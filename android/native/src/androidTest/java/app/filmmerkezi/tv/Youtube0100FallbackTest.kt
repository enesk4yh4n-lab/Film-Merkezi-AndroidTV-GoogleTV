package app.filmmerkezi.tv

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class Youtube0100FallbackTest {
    @Test fun combinedFallbackKeepsPlayerPositionAndManualModeDoesNotSwitch()=runBlocking {
        val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext
        val activity=ins.startActivitySync(Intent(context,NativeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as NativeMainActivity
        val base="http://10.0.2.2:8766/case/yt0100-${SystemClock.elapsedRealtime()}"
        val checks=JSONArray()
        fun main(block:()->Unit)=ins.runOnMainSync(block)
        fun snap():JSONObject{var v=JSONObject();main{v=activity.playbackSnapshotForTest()};return v}
        suspend fun waitFor(test:(JSONObject)->Boolean):JSONObject{val end=SystemClock.elapsedRealtime()+25000;while(SystemClock.elapsedRealtime()<end){val s=snap();if(test(s))return s;delay(100)};error(snap().toString())}
        val bad=JSONObject().put("id","separate").put("url","$base/not-found.mp4").put("height",720).put("width",1280)
            .put("audioStreams",JSONArray().put(JSONObject().put("url","$base/clip.mp4")))
        val good=JSONObject().put("id","combined").put("url","$base/clip.mp4").put("height",360).put("width",640).put("audioStreams",JSONArray())
        val source=JSONObject().put("id","ddizi|youtube-test").put("label","YouTube fixture").put("type","mp4")
            .put("url",bad.getString("url")).put("variants",JSONArray().put(bad).put(good))
        try{
            var identity=0
            main{activity.startSourcesForTest(listOf(source));identity=System.identityHashCode(activity.playbackSessionForTest().player)}
            val s=waitFor{it.optBoolean("firstFrame")&&it.optLong("positionMs")>1200}
            assertEquals(identity,s.getInt("engineIdentity"));assertEquals(0,s.getInt("sourceSwitches"));assertTrue(s.getInt("sourceRestarts")>=1)
            checks.put(JSONObject().put("name","automatic combined fallback / same engine").put("passed",true).put("state",s))
            main{activity.stopSourcesForTest();activity.playbackSessionForTest().automaticSources=false;activity.startSourcesForTest(listOf(source))}
            val manual=waitFor{!it.isNull("error")}
            assertFalse(manual.getBoolean("firstFrame"));assertEquals(0,manual.getInt("sourceRestarts"))
            checks.put(JSONObject().put("name","manual mode keeps failure explicit").put("passed",true).put("state",manual))
        }finally{File(context.getExternalFilesDir(null),"youtube0100-fallback.json").writeText(JSONObject().put("fixture",true).put("checks",checks).toString(2));main{activity.stopSourcesForTest();activity.finish()}}
        Unit
    }
}
