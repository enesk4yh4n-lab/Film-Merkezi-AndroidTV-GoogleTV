package app.filmmerkezi.tv

import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.C
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class Stage3PlayerTest {
    @Test fun nativeTransportSelectionAndRefresh()=runBlocking {
        val ins=InstrumentationRegistry.getInstrumentation()
        val activity=ins.startActivitySync(Intent(ins.targetContext,NativeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as NativeMainActivity
        val base="http://10.0.2.2:8768"
        val prefix="s3-"+SystemClock.elapsedRealtime()
        val jobs=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val checks=JSONArray()
        fun main(block:()->Unit)=ins.runOnMainSync(block)
        fun snap():JSONObject {var value=JSONObject();main{value=activity.playbackSnapshotForTest()};return value}
        suspend fun await(timeout:Long=40000,predicate:(JSONObject)->Boolean):JSONObject {
            val end=SystemClock.elapsedRealtime()+timeout
            while(SystemClock.elapsedRealtime()<end){val v=snap();if(predicate(v))return v;delay(150)}
            error("Stage 3 timed out: "+snap())
        }
        suspend fun check(name:String,block:suspend ()->Unit){
            val row=JSONObject().put("name",name);checks.put(row)
            try{block();row.put("passed",true)}catch(e:Throwable){row.put("passed",false).put("error",e.stackTraceToString());throw e}
            finally{
                row.put("state",snap())
                File(ins.targetContext.getExternalFilesDir(null),"stage3-player.json").writeText(JSONObject()
                    .put("api",android.os.Build.VERSION.SDK_INT).put("fixture",true).put("checks",checks).toString(2))
                main{activity.stopSourcesForTest()}
            }
        }
        fun source(id:String,route:String="master.m3u8",mode:String="healthy",type:String="hls")=
            JSONObject().put("id",id).put("label",id).put("type",type).put("url","$base/case/$prefix-$id/$route?mode=$mode")
                .put("referer","https://fixture.example.test/").put("userAgent","FM-Stage3-Media")
        try{
            check("Actual 8-bit AVC High decodes 20 seconds without profile rewriting"){
                main{activity.startSourcesForTest(listOf(source("high8","high8.mpd",type="dash")))}
                val s=await{it.optBoolean("firstFrame")&&it.optLong("positionMs")>=20000&&it.optBoolean("isPlaying")}
                assertTrue(s.getString("videoCodec").startsWith("avc1.64",true))
                assertEquals(0,s.getInt("sourceSwitches"))
            }
            check("DASH fMP4 plays through the same Media3 engine"){
                main{activity.startSourcesForTest(listOf(source("dash","manifest.mpd",type="dash")))}
                await{it.optBoolean("firstFrame")&&it.optLong("positionMs")>=1000&&it.optBoolean("isPlaying")}
            }
            check("Pause during asynchronous resolution is preserved"){
                val lazy=JSONObject().put("id","pause").put("label","Pause").put("lazyOptionId","pause")
                val lookup=PlaybackSession.SourceLookup{_,done->
                    val job=jobs.launch{delay(800);done(source("paused-resolution"),null)}
                    AutoCloseable{job.cancel()}
                }
                main{activity.startSourcesForTest(listOf(lazy),lookup);activity.playbackSessionForTest().setPlaying(false)}
                val paused=await{it.optInt("state")==3&&!it.optBoolean("resolving")}
                assertFalse(paused.getBoolean("playWhenReady"));delay(1200);assertFalse(snap().getBoolean("isPlaying"))
                main{activity.playbackSessionForTest().setPlaying(true)}
                await{it.optBoolean("isPlaying")&&it.optLong("positionMs")>=1000}
            }
            check("AES-128 key / fake JPG HLS / segment headers"){
                val s=source("headers","master.jpg","headers").put("cookie","fm=fixture").put("headers",JSONObject().put("X-FM-Stage3","yes"))
                val jar=object:okhttp3.CookieJar{
                    override fun loadForRequest(url:okhttp3.HttpUrl)=listOf(okhttp3.Cookie.Builder().name("fm").value("jar").hostOnlyDomain(url.host).build())
                    override fun saveFromResponse(url:okhttp3.HttpUrl,cookies:List<okhttp3.Cookie>)=Unit
                }
                val http=okhttp3.OkHttpClient.Builder().cookieJar(jar).build()
                main{activity.startSourcesForTest(listOf(s),http=http)}
                await{it.optBoolean("firstFrame")&&it.optLong("positionMs")>=20000&&it.optBoolean("isPlaying")}
                val log=JSONArray(URL("$base/log").readText())
                val rows=(0 until log.length()).map{log.getJSONObject(it)}.filter{it.optString("path").contains("$prefix-headers/")}
                assertTrue(rows.any{it.getString("path").endsWith("/key")})
                assertTrue(rows.any{it.getString("path").endsWith(".ts")})
                assertTrue(rows.all{it.optString("referer")=="https://fixture.example.test/"&&it.optString("agent")=="FM-Stage3-Media"&&it.optString("cookie")=="fm=fixture"&&it.optInt("status")==200})
            }
            check("Signed URL 403 refresh keeps position and one engine"){
                val count=AtomicInteger()
                val lazy=JSONObject().put("id","renew").put("label","Renew").put("lazyOptionId","renew")
                val lookup=PlaybackSession.SourceLookup{_,done->
                    val n=count.incrementAndGet()
                    val job=jobs.launch{delay(80);done(source(if(n==1)"old"else "renewed"),null)}
                    AutoCloseable{job.cancel()}
                }
                main{activity.startSourcesForTest(listOf(lazy),lookup)}
                val before=await{it.optBoolean("firstFrame")&&it.optLong("positionMs")>=2000}
                URL("$base/case/$prefix-old/control?expire=true").readText()
                val after=await{it.optInt("sourceRefreshes")==1&&it.optBoolean("firstFrame")&&it.optString("sourceId")=="renewed"&&it.optBoolean("isPlaying")}
                assertEquals(2,count.get());assertEquals(1,after.getInt("engineInstancesUsed"))
                assertTrue(after.getLong("positionMs")>=before.getLong("positionMs")-1000)
            }
            check("Repeated expired resolution terminates with fallback"){
                val count=AtomicInteger()
                val lazy=JSONObject().put("id","dead").put("label","Dead").put("lazyOptionId","dead")
                val lookup=PlaybackSession.SourceLookup{_,done->
                    count.incrementAndGet();val job=jobs.launch{done(source("dead","denied.m3u8"),null)}
                    AutoCloseable{job.cancel()}
                }
                main{activity.startSourcesForTest(listOf(lazy,source("fallback")),lookup)}
                val state=await{it.optInt("sourceIndex")==1&&it.optBoolean("firstFrame")&&it.optLong("positionMs")>=1000}
                assertEquals(2,count.get());assertEquals(1,state.getInt("sourceRefreshes"));assertEquals(1,state.getInt("engineInstancesUsed"))
            }
        }finally{jobs.cancel();main{activity.finish()}}
        Unit
    }
}
