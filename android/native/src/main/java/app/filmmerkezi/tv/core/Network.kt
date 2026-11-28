package app.filmmerkezi.tv.core

import app.filmmerkezi.tv.LegacyTls
import app.filmmerkezi.tv.SiteHttpPolicy
import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class WebRequest(val url: String, val referer: String = "", val method: String = "GET",
    val fields: Map<String, String> = emptyMap(), val body: String? = null, val headers: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 12000, val byteLimit: Int = 2 * 1024 * 1024)
data class WebResponse(val url: String, val body: String, val code: Int = 200, val headers: Map<String, String> = emptyMap())
interface WebTransport { suspend fun request(request: WebRequest): WebResponse }

internal fun providerHttpFailure(code:Int, mitigation:String?):DataFailure? {
    if(mitigation.equals("challenge",ignoreCase=true))
        return DataFailure(FailureKind.ACCESS,"Site tarayıcı doğrulaması istiyor","browser-challenge",code.takeUnless{it in 200..299})
    if(code in 200..299)return null
    val kind=if(code in listOf(401,403,429))FailureKind.ACCESS else FailureKind.HTTP
    return DataFailure(kind,"HTTP $code","http",code)
}

/** Application-owned metadata transport. Cancellation affects only the caller's Call. */
class ProviderNetwork(context: Context,private val originPolicy:((HttpUrl)->Unit)?=null,sharedPool:ConnectionPool?=null,private val cookies:MutableList<Cookie> = mutableListOf()) : WebTransport {
    private val slots = Semaphore(2)
    private class CatalogTarget(val site:SiteSpec,val route:SectionRoute,val number:Int)
    val client: OkHttpClient = LegacyTls.configure(context.applicationContext, OkHttpClient.Builder()
        .connectionPool(sharedPool?:ConnectionPool(3,1,TimeUnit.MINUTES))
        .addNetworkInterceptor{chain->try{originPolicy?.invoke(chain.request().url)}catch(e:Exception){throw IOException(e.message,e)};chain.proceed(chain.request())}
        .dns(object:Dns{override fun lookup(hostname:String)=Dns.SYSTEM.lookup(hostname).also{addresses->if(originPolicy!=null&&addresses.any{it.isAnyLocalAddress||it.isLoopbackAddress||it.isSiteLocalAddress||it.isLinkLocalAddress||it.isMulticastAddress||it.address.size==16&&(it.address[0].toInt()and 0xfe)==0xfc})throw IOException("Yerel ağ adresine paket isteği reddedildi")}})
        .connectTimeout(8,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val r=chain.request()
            chain.proceed(if(r.header("User-Agent")==null) r.newBuilder().header("User-Agent",SiteAddresses.userAgent(r.url)).build() else r)
        }.cookieJar(object: CookieJar {
            override fun saveFromResponse(url:HttpUrl, values:List<Cookie>) = synchronized(cookies) {
                values.forEach { c -> cookies.removeAll { it.name==c.name && it.domain==c.domain && it.path==c.path }; cookies.add(c) }
                cookies.removeAll { it.expiresAt<System.currentTimeMillis() }
                while(cookies.size>64) cookies.removeAt(0)
            }
            override fun loadForRequest(url:HttpUrl):List<Cookie> = synchronized(cookies) { cookies.filter { it.expiresAt>System.currentTimeMillis() && it.matches(url) } }
        })).build()
    private val categoryClient by lazy {
        client.newBuilder().followRedirects(false).followSslRedirects(false).addInterceptor{chain->
            var request=chain.request()
            val target=checkNotNull(request.tag(CatalogTarget::class.java))
            for(hop in 0..4){
                val response=chain.proceed(request)
                if(response.code !in setOf(301,302,303,307,308))return@addInterceptor response
                val redirected=response.use{it.header("Location")?.let{location->target.site.catalogUrl(location,request.url.toString())}}
                if(hop==4||redirected==null||sectionPageNumber(target.site,target.route,redirected)!=target.number)
                    throw IOException("Kategori yönlendirmesi istenen sayfaya ait değil")
                request=request.newBuilder().url(redirected).build()
            }
            error("Unreachable redirect limit")
        }.build()
    }

    override suspend fun request(request: WebRequest): WebResponse = request(request,null)
    internal suspend fun requestCategory(request:WebRequest,site:SiteSpec,route:SectionRoute,number:Int):WebResponse =
        request(request,CatalogTarget(site,route,number))

    private suspend fun request(request:WebRequest,target:CatalogTarget?):WebResponse = slots.withPermit {
        require(request.timeoutMs in 1..60000 && request.byteLimit in 1..4*1024*1024)
        val address=if(target==null)SiteAddresses.rebaseKnown(request.url)else target.site.catalogUrl(request.url).orEmpty()
        val url = publicUrl(address) ?: throw DataFailure(FailureKind.PARSE,"Geçersiz ağ adresi","request")
        val builder=Request.Builder().url(url).header("Accept","*/*")
        if(request.referer.isNotBlank()) builder.header("Referer", SiteAddresses.rebaseKnown(request.referer))
        request.headers.forEach { (key,value) -> builder.header(key,value) }
        if(request.method=="POST") {
            val body=if(request.fields.isNotEmpty()) FormBody.Builder().apply { request.fields.forEach { (k,v)-> add(k,v) } }.build()
                else (request.body?:"").toRequestBody(null)
            builder.post(body)
        }
        val http=if(target==null)client else categoryClient.also{builder.tag(CatalogTarget::class.java,target)}
        val call=http.newCall(builder.build()); call.timeout().timeout(request.timeoutMs,TimeUnit.MILLISECONDS)
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException) {
                    if(continuation.isActive) continuation.resumeWithException(DataFailure(if(e is java.io.InterruptedIOException) FailureKind.TIMEOUT else FailureKind.NETWORK,e.message?:"Ağ hatası","http",cause=e))
                }
                override fun onResponse(call:Call,response:Response) {
                    try { response.use { r ->
                        providerHttpFailure(r.code,r.header("cf-mitigated"))?.let{throw it}
                        val output=ByteArrayOutputStream()
                        r.body.byteStream().use { input ->
                            val buffer=ByteArray(8192)
                            while(true) { val count=input.read(buffer); if(count<0)break
                                if(output.size()+count>request.byteLimit)throw DataFailure(FailureKind.LIMIT,"Yanıt boyut sınırını aştı","http")
                                output.write(buffer,0,count)
                            }
                        }
                        val result=WebResponse(r.request.url.toString(),output.toString(r.body.contentType()?.charset(Charsets.UTF_8)?.name()?: "UTF-8"),r.code,r.headers.toMap())
                        if(continuation.isActive) continuation.resume(result)
                    } } catch(e:Exception) { if(continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
    }
}

fun publicUrl(value:String, base:String?=null):String? = runCatching {
    val clean=value.trim().replace("\\/","/")
    if(clean.isBlank() || clean.startsWith("#"))return null
    val u=if(base==null) clean.toHttpUrl() else base.toHttpUrl().resolve(clean) ?: return null
    if(u.username.isNotEmpty() || u.password.isNotEmpty()) return null
    val h=u.host.lowercase()
    if(h=="localhost" || h=="::1" || h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.") ||
        Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(h)) return null
    u.toString()
}.getOrNull()
