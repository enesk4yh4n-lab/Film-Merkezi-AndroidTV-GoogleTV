package app.filmmerkezi.tv

import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.C
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TrickPlayRegressionTest {
    @Test fun helperIFrameRenditionCannotBecomeNormalPlaybackOrQuality()=runBlocking {
        val ins=InstrumentationRegistry.getInstrumentation()
        val activity=ins.startActivitySync(Intent(ins.targetContext,NativeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as NativeMainActivity
        fun main(block:()->Unit)=ins.runOnMainSync(block)
        fun snap():JSONObject{var s=JSONObject();main{s=activity.playbackSnapshotForTest()};return s}
        val source=JSONObject().put("id","trick-play").put("label","Role fixture").put("type","hls")
            .put("url","http://10.0.2.2:8769/case/role-"+SystemClock.elapsedRealtime()+"/master.m3u8?mode=trick-play")
        val report=JSONObject().put("fixture",true).put("api",android.os.Build.VERSION.SDK_INT)
        try{
            main{activity.startSourcesForTest(listOf(source))}
            val end=SystemClock.elapsedRealtime()+35000
            while(SystemClock.elapsedRealtime()<end){val s=snap();if(!s.isNull("error"))error(s.toString());if(s.optLong("positionMs")>=8000)break;delay(100)}
            val s=snap();assertTrue(s.optBoolean("firstFrame"));assertTrue(s.optLong("positionMs")>=8000)
            main{val choices=activity.playbackSessionForTest().trackChoices(C.TRACK_TYPE_VIDEO)
                assertTrue(choices.isNotEmpty());assertTrue(choices.none{it.format.roleFlags and C.ROLE_FLAG_TRICK_PLAY!=0})}
            report.put("passed",true).put("state",s)
        }catch(e:Throwable){report.put("passed",false).put("error",e.stackTraceToString());throw e}
        finally{
            File(ins.targetContext.getExternalFilesDir(null),"trick-play-regression.json").writeText(report.toString(2))
            main{activity.stopSourcesForTest();activity.finish()}
        }
        Unit
    }
}
