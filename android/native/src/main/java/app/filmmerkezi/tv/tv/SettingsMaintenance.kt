package app.filmmerkezi.tv.tv

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import app.filmmerkezi.tv.core.Sites
import app.filmmerkezi.tv.settings.AppSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** User-triggered maintenance only. Nothing here runs at application startup. */
internal class SettingsMaintenance(private val context:Context,private val data:TvData,private val client:OkHttpClient,
    val playing:()->Boolean,val playbackLog:()->String={""},private val network:app.filmmerkezi.tv.core.WebTransport?=null) {
    suspend fun checkSite(site:app.filmmerkezi.tv.core.SiteSpec,base:String):String {
        val transport=network?:throw IllegalStateException("Ağ bağlantısı hazır değil")
        val test=app.filmmerkezi.tv.core.SiteAddresses.apply(Sites.default(site.id),app.filmmerkezi.tv.core.SiteAddress(base,site.name,app.filmmerkezi.tv.core.SiteAddresses.hosts(site)))
        val path=if(site.id in setOf("dizibox","diziboxum","ddizi"))site.seriesPath?:site.list else site.list
        val response=transport.request(app.filmmerkezi.tv.core.WebRequest(base+path,base+"/",headers=mapOf("User-Agent" to app.filmmerkezi.tv.core.SiteAddresses.userAgent(site.base.toHttpUrl()))))
        val count=app.filmmerkezi.tv.core.NativeSiteProvider(test,transport,Dispatchers.Default).parseList(response.body,base+path).cards.size
        if(count==0)throw IllegalStateException(context.getString(app.filmmerkezi.tv.R.string.site_manager_no_cards))
        return context.getString(app.filmmerkezi.tv.R.string.site_manager_test_ok,count)
    }
    suspend fun cacheBytes()=Posters.diskBytes(context)
    suspend fun run(key:String){when(key){
        "cache"->Posters.clearCache(context)
        "search"->data.clearSearches()
        "history"->{check(!playing());data.clearPlaybackHistory()}
        "overrides"->{check(!playing());data.clearPlaybackPreferences()}
        else->error("Unknown maintenance action")
    }}
    suspend fun licenses()=withContext(Dispatchers.IO){context.assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use{it.readText()}}
    suspend fun deviceInfo():String=withContext(Dispatchers.IO){
        val manager=context.getSystemService(Context.ACTIVITY_SERVICE)as ActivityManager
        val mem=ActivityManager.MemoryInfo().also{manager.getMemoryInfo(it)}
        val codecs=MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter{!it.isEncoder}.map{codec->
            codec.name+"\n  "+codec.supportedTypes.sorted().joinToString()
        }
        "${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\n"+
            Build.SUPPORTED_ABIS.joinToString()+"\nRAM: ${mem.totalMem/(1024*1024)} MiB · Java: ${manager.memoryClass} MiB\n\n"+codecs.joinToString("\n\n")
    }
    suspend fun connections(settings:AppSettings):String=coroutineScope{
        val sites=settings.siteOrder.filter{it !in settings.disabledCatalog||settings.sourceAllowed(it)}
        if(sites.isEmpty())return@coroutineScope context.getString(app.filmmerkezi.tv.R.string.settings_disabled_sites)
        val http=client.newBuilder().callTimeout(10,TimeUnit.SECONDS).build();val permits=Semaphore(2)
        sites.map{id->async{permits.withPermit{
            val site=Sites.get(id)
            val status=try{
                val call=http.newCall(Request.Builder().url(site.base).head().build())
                suspendCancellableCoroutine<Int>{continuation->
                    continuation.invokeOnCancellation{call.cancel()}
                    call.enqueue(object:Callback{
                        override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resumeWithException(e)}
                        override fun onResponse(call:Call,response:Response){response.use{if(continuation.isActive)continuation.resume(it.code)}}
                    })
                }.let{"HTTP $it"}
            }catch(e:CancellationException){throw e}catch(_:IOException){context.getString(app.filmmerkezi.tv.R.string.settings_connection_failed)}
            site.name+": "+status
        }}}.awaitAll().joinToString("\n")
    }
}
