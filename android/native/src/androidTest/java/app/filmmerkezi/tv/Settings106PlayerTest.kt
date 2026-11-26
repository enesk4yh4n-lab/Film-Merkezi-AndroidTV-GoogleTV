package app.filmmerkezi.tv

import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.C
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Settings106PlayerTest {
    @Test fun autoCapManualOverrideAudioDrivenCaptionsAndSourceSwitchKeepThePlayer()=runBlocking {
        val ins=InstrumentationRegistry.getInstrumentation();val c=ins.targetContext
        val a=ins.startActivitySync(Intent(c,NativeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as NativeMainActivity
        val report=JSONObject().put("fixture",true).put("api",android.os.Build.VERSION.SDK_INT)
        fun main(block:()->Unit)=ins.runOnMainSync(block)
        fun snap():JSONObject {var v=JSONObject();main{v=a.playbackSnapshotForTest()};return v}
        suspend fun waitFor(check:(JSONObject)->Boolean):JSONObject {val end=SystemClock.elapsedRealtime()+40000;while(SystemClock.elapsedRealtime()<end){val v=snap();if(check(v))return v;delay(100)};error("Settings playback timeout: "+snap())}
        val base="http://10.0.2.2:8770/case/settings106-"+SystemClock.elapsedRealtime()
        fun source(id:String)=JSONObject().put("id",id).put("label",id).put("type","hls").put("url","$base-$id/master.m3u8?mode=selections")
            .put("referer","https://fixture.example.test/").put("userAgent","FM-Settings106")
            .put("subtitles",JSONArray().put(JSONObject().put("url","$base-$id/tr.vtt").put("language","tr").put("label","Turkce").put("format","vtt")))
        var session:PlaybackSession?=null
        fun selected(type:Int,lang:String):Boolean{var ok=false;main{ok=session!!.trackChoices(type).any{it.selected&&PlaybackPreferencePolicy.matches(it.format.language,lang)}};return ok}
        try{
            main{session=a.playbackSessionForTest();session!!.configure(PlaybackSettings(autoMaxHeight=360,autoMaxFps=30));a.startSourcesForTest(listOf(source("first")))}
            val first=waitFor{it.optBoolean("firstFrame")&&it.optLong("positionMs")>1500&&it.optInt("height")==360}
            val engine=first.getInt("engineIdentity");report.put("automatic",first)
            assertTrue(selected(C.TRACK_TYPE_AUDIO,"tr"));assertFalse(selected(C.TRACK_TYPE_TEXT,"tr"))
            main{val english=session!!.trackChoices(C.TRACK_TYPE_AUDIO).first{PlaybackPreferencePolicy.matches(it.format.language,"en")};assertTrue(session!!.chooseTrack(C.TRACK_TYPE_AUDIO,english))}
            waitFor{selected(C.TRACK_TYPE_AUDIO,"en")&&selected(C.TRACK_TYPE_TEXT,"tr")}
            main{val high=session!!.trackChoices(C.TRACK_TYPE_VIDEO).first{it.format.height==720};assertTrue(session!!.chooseTrack(C.TRACK_TYPE_VIDEO,high))}
            val high=waitFor{it.optInt("height")==720&&it.optBoolean("isPlaying")};report.put("manual",high)
            main{session!!.configure(PlaybackSettings(audio="en",preferredHeight=720,autoMaxHeight=360,autoMaxFps=30));session!!.appendCandidates(listOf(source("second")),false);assertTrue(session!!.chooseSource("second"))}
            val start=waitFor{it.optBoolean("firstFrame")&&it.optInt("height")==720&&it.optBoolean("isPlaying")}
            val end=waitFor{it.optLong("positionMs")>=start.getLong("positionMs")+15000&&it.optBoolean("isPlaying")}
            assertEquals(engine,end.getInt("engineIdentity"));assertEquals(1,end.getInt("engineInstancesUsed"));assertTrue(selected(C.TRACK_TYPE_AUDIO,"en"))
            report.put("after15s",end).put("status","PASS")
        }catch(e:Throwable){report.put("status","FAIL").put("error",e.toString());throw e}
        finally{File(c.getExternalFilesDir(null),"settings106-player.json").writeText(report.toString(2));main{a.stopSourcesForTest();a.finish()}}
        Unit
    }
}
