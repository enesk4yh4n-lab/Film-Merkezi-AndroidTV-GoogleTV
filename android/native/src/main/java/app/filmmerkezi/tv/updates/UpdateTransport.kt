package app.filmmerkezi.tv.updates

import android.content.Context
import app.filmmerkezi.tv.LegacyTls
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** No browser/WebView, app credentials or site cookies are used for distribution. */
class UpdateTransport(context:Context) {
    private val slots=Semaphore(2)
    private val client=LegacyTls.configure(context,OkHttpClient.Builder()).followRedirects(false).followSslRedirects(false)
        .cache(Cache(File(context.cacheDir,"distribution-http"),2L*1024*1024))
        .connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(120,TimeUnit.SECONDS)
        .dns(object:Dns{override fun lookup(hostname:String):List<InetAddress> = Dns.SYSTEM.lookup(hostname).also{values->
            require(values.isNotEmpty()&&values.none{it.isAnyLocalAddress||it.isLoopbackAddress||it.isLinkLocalAddress||it.isSiteLocalAddress||it.isMulticastAddress||it.address.firstOrNull()?.toInt()?.and(0xfe)==0xfc}){"Yerel ağ depo adresi kullanılamaz"}
        }}).build()
    companion object {
        fun https(value:String):String {
            val u=value.toHttpUrl();require(u.scheme=="https"&&u.username.isEmpty()&&u.password.isEmpty()&&u.port==443&&u.fragment==null){"Dağıtım adresi HTTPS olmalı"}
            require(u.host!="localhost"&&!u.host.endsWith(".local")&&u.host.contains('.')){"Yerel depo adresi kullanılamaz"};return u.toString()
        }
        fun repository(value:String):String {
            var u=https(value.trim()).toHttpUrl();require(u.query==null)
            if(u.host=="github.com"){
                val p=u.pathSegments.filter{it.isNotBlank()}
                u=when{p.size==2->"https://raw.githubusercontent.com/${p[0]}/${p[1]}/main/${UpdateProtocol.INDEX}".toHttpUrl()
                    p.size>=5&&p[2]=="blob"->("https://raw.githubusercontent.com/"+p.take(2).joinToString("/")+"/"+p.drop(3).joinToString("/")).toHttpUrl()
                    else->error("GitHub depo veya indeks adresi girin")}
            }
            require(u.encodedPath.endsWith("/"+UpdateProtocol.INDEX)){"Android eklenti indeksi seçilmeli"}
            return https(u.toString())
        }
    }
    suspend fun bytes(url:String,limit:Int=65536,timeoutMs:Long=30000):ByteArray = transfer(url,limit,null,null,timeoutMs){}
    suspend fun download(url:String,target:File,size:Int,hash:String,progress:(Long)->Unit={}) {
        transfer(url,size,target,hash,120000,progress);if(target.length()!=size.toLong()){target.delete();error("İndirilen dosyanın boyutu eşleşmiyor")}
    }
    private suspend fun transfer(url:String,limit:Int,target:File?,hash:String?,timeoutMs:Long,progress:(Long)->Unit):ByteArray=slots.withPermit {
        require(timeoutMs in 1..120000)
        suspendCancellableCoroutine { cont ->
            var active:Call?=null;var closed:Response?=null
            cont.invokeOnCancellation{active?.cancel();closed?.close()}
            fun request(address:String,redirects:Int){
                if(!cont.isActive)return
                try{
                    val call=client.newCall(Request.Builder().url(https(address)).header("User-Agent","FilmMerkezi-Android-Distribution/1").header("Accept",if(address.startsWith("https://api.github.com/"))"application/vnd.github+json"else"*/*").header("Cache-Control","no-cache").build());call.timeout().timeout(timeoutMs,TimeUnit.MILLISECONDS);active=call
                    if(!cont.isActive){call.cancel();return}
                    call.enqueue(object:Callback{
                        override fun onFailure(call:Call,e:IOException){if(cont.isActive)cont.resumeWithException(e)}
                        override fun onResponse(call:Call,response:Response){
                            closed=response
                            try{response.use{r->
                                if(r.code in 300..399){require(redirects<5){"Yönlendirme sınırı"};val next=r.header("Location")?.let{r.request.url.resolve(it)}?:error("Yönlendirme adresi yok");request(https(next.toString()),redirects+1);return}
                                if(!r.isSuccessful)throw UpdateHttpFailure.response(r.code,r.request.url.host,r.header("X-RateLimit-Remaining"),r.header("Retry-After"),r.header("X-RateLimit-Reset"))
                                require(r.body.contentLength()<=limit){"Dosya boyut sınırını aştı"}
                                val digest=MessageDigest.getInstance("SHA-256");val memory=if(target==null)ByteArrayOutputStream()else null
                                target?.parentFile?.mkdirs();val output=target?.outputStream()?.also{if(target.extension=="jar")require(target.setReadOnly()){"Paket salt okunur yapılamadı"}}?:memory!!
                                var count=0L;var reported=0L
                                output.use{out->r.body.byteStream().use{input->val buffer=ByteArray(32768);while(true){
                                    if(!cont.isActive)throw IOException("İndirme iptal edildi")
                                    val n=input.read(buffer);if(n<0)break;count+=n;require(count<=limit){"Dosya boyut sınırını aştı"};digest.update(buffer,0,n);out.write(buffer,0,n)
                                    if(count-reported>=262144){progress(count);reported=count}
                                }}}
                                if(hash!=null)require(digest.digest().joinToString(""){"%02x".format(it.toInt()and 255)}==hash){"Dosya özeti eşleşmiyor"}
                                progress(count);if(cont.isActive)cont.resume(memory?.toByteArray()?:ByteArray(0))
                            }}catch(e:Exception){target?.delete();if(cont.isActive)cont.resumeWithException(e)}
                        }
                    })
                }catch(e:Exception){if(cont.isActive)cont.resumeWithException(e)}
            }
            request(url,0)
        }
    }
}
