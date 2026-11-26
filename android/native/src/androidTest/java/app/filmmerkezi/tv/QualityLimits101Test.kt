package app.filmmerkezi.tv

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import app.filmmerkezi.tv.core.*

class QualityLimits101Test {
    @Test fun liveYoutubeInventoryKeepsHighResolutionAndFrameRate()=runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("youtubeLive")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val result=SourceResolver(ProviderNetwork(context)).resolve(SourceOption("ddizi|inventory",ContentRef("ddizi","https://www.ddizi.tel/izle/91916/uzak-sehir-68-bolum-izle-hd1.htm"),"YouTube","https://www.youtube.com/watch?v=aqz-KE-bpKQ"))
        File(context.getExternalFilesDir(null),"quality101-youtube-inventory.json").writeText(JSONObject().put("fixture",false).put("playbackTest",false)
            .put("variants",JSONArray(result.variants.map{v->JSONObject().put("height",v.height).put("fps",v.fps).put("codec",v.codec).put("mimeType",v.mimeType).put("combined",v.audio.isEmpty())})).toString(2))
        assertTrue(result.variants.any{it.height>720});assertTrue(result.variants.any{it.fps>30});assertTrue(result.variants.any{it.mimeType=="video/webm"})
    }
    @Test fun uncappedSelectorPlaysHlsAnd60fpsWebmWithSeparateOpus()=runBlocking {
        val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext
        val activity=ins.startActivitySync(Intent(context,NativeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as NativeMainActivity
        val root="http://10.0.2.2:8766/case/quality101-${SystemClock.elapsedRealtime()}"
        val cases=JSONArray()
        fun main(block:()->Unit)=ins.runOnMainSync(block)
        fun snap():JSONObject{var s=JSONObject();main{s=activity.playbackSnapshotForTest()};return s}
        suspend fun await(ms:Long=25000,predicate:(JSONObject)->Boolean):JSONObject{
            val deadline=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<deadline){val s=snap();if(predicate(s))return s;delay(100)};error(snap().toString())
        }
        try{
            val sources=listOf(
                JSONObject().put("id","hls").put("label","HLS control").put("type","hls").put("url","$root/master.m3u8"),
                JSONObject().put("id","webm").put("label","60fps VP9 + Opus").put("type","webm").put("url","$root/quality101-video.webm")
                    .put("audioStreams",JSONArray().put(JSONObject().put("url","$root/quality101-audio.webm").put("language","tr").put("mimeType","audio/webm"))))
            for(source in sources){
                main{activity.startSourcesForTest(listOf(source));activity.playbackSessionForTest().preferences("tr","tr",true,0)}
                val first=await{it.optBoolean("firstFrame")&&it.optBoolean("isPlaying")}
                val limits=first.getJSONObject("videoLimits")
                for(key in listOf("width","height","fps","bitrate","viewportWidth","viewportHeight"))assertEquals("$key must have no software ceiling",Int.MAX_VALUE,limits.getInt(key))
                val start=SystemClock.elapsedRealtime();val position=first.getLong("positionMs")
                val played=await{SystemClock.elapsedRealtime()-start>=15000&&it.optLong("positionMs")-position>=14000&&it.optBoolean("isPlaying")}
                assertTrue(played.getInt("audioRenderedBuffers")>0);assertTrue(played.isNull("error"))
                cases.put(JSONObject().put("source",source.getString("id")).put("wallMs",SystemClock.elapsedRealtime()-start).put("state",played).put("passed",false))
                if(source.getString("id")=="webm"){
                    // Matroska can leave Format.frameRate unknown; the fixture is ffprobe-verified 60 fps.
                    val tracks=played.getJSONArray("tracks");assertTrue((0 until tracks.length()).any{tracks.getJSONObject(it).optString("mime")=="video/x-vnd.on2.vp9"})
                    assertEquals("audio/opus",played.getString("audioMime"))
                }
                cases.getJSONObject(cases.length()-1).put("passed",true)
                main{activity.stopSourcesForTest()}
            }
        }finally{File(context.getExternalFilesDir(null),"quality101-player.json").writeText(JSONObject().put("fixture",true).put("cases",cases).toString(2));main{activity.stopSourcesForTest();activity.finish()}}
        Unit
    }
}
