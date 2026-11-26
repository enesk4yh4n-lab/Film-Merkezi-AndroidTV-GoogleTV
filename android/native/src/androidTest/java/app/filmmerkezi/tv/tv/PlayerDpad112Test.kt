package app.filmmerkezi.tv.tv

import android.view.KeyEvent
import android.view.View
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.PlaybackSettings
import app.filmmerkezi.tv.settings.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Only the visible-controls / direct-seek boundary; isolated metadata and local video. */
class PlayerDpad112Test {
    @Test fun visibleButtonsNavigateImmediatelyAndHiddenControlsSeek()=runBlocking {
        val f=Settings110Fixture();val result=JSONObject().put("fixture",true)
        fun key(code:Int){f.ins.sendKeyDownUpSync(code);f.ins.waitForIdleSync()}
        fun position()=f.snap().getLong("positionMs")
        fun unchanged(expected:Long){assertTrue("Unexpected seek: ${position()} vs $expected",kotlin.math.abs(position()-expected)<250)}
        try{
            f.start(AppSettings(entry="RESTART",player=PlaybackSettings(controlsSeconds=0)))
            f.play();key(KeyEvent.KEYCODE_MEDIA_PAUSE);f.waitFor{!f.activity.snapshotForUiTest().optBoolean("isPlaying")}
            f.waitFor{f.activity.snapshotForUiTest().optBoolean("controllerVisible")}
            val start=f.snap();val baseline=position();val engine=start.getInt("engineIdentity")
            result.put("start",start).put("baselineMs",baseline)
            // No preceding Down: the first Right/Left must move focus, never seek.
            key(KeyEvent.KEYCODE_DPAD_RIGHT);f.waitFor{f.activity.currentFocus?.id==R.id.fm_player_forward};unchanged(baseline)
            key(KeyEvent.KEYCODE_DPAD_LEFT);f.waitFor{f.activity.currentFocus?.id==androidx.media3.ui.R.id.exo_play_pause};unchanged(baseline)
            result.put("initialVisibleNavigation",true)
            // Panels return to visible controls, also without needing Down.
            f.main{f.activity.findViewById<View>(R.id.fm_player_quality).performClick()}
            f.waitFor{f.activity.playerOverlayForTest()?.showing==true};key(KeyEvent.KEYCODE_BACK)
            f.waitFor{f.activity.currentFocus?.id==R.id.fm_player_quality}
            key(KeyEvent.KEYCODE_DPAD_RIGHT);f.waitFor{f.activity.currentFocus?.id==R.id.fm_player_audio};unchanged(baseline)
            result.put("panelReturnNavigation",true)
            key(KeyEvent.KEYCODE_DPAD_UP);f.waitFor(5000){!f.activity.snapshotForUiTest().optBoolean("controllerVisible")}
            result.put("afterHide",f.snap())
            key(KeyEvent.KEYCODE_DPAD_RIGHT);result.put("afterHiddenRight",f.snap());f.waitFor(5000){f.activity.snapshotForUiTest().optLong("positionMs")>=baseline+9900}
            unchanged(baseline+10000);assertFalse(f.snap().optBoolean("controllerVisible"))
            key(KeyEvent.KEYCODE_DPAD_LEFT);f.waitFor{f.activity.snapshotForUiTest().optLong("positionMs")<=baseline+100};unchanged(baseline)
            result.put("hiddenDirectSeek",true)
            key(KeyEvent.KEYCODE_DPAD_DOWN);f.waitFor{f.activity.snapshotForUiTest().optBoolean("controllerVisible")}
            key(KeyEvent.KEYCODE_DPAD_RIGHT);f.waitFor{f.activity.currentFocus?.id==R.id.fm_player_forward};unchanged(baseline)
            result.put("downStillOpensControls",true)
            assertEquals(engine,f.snap().getInt("engineIdentity"));result.put("start",start).put("end",f.snap()).put("status","PASS")
            f.screenshot("player-dpad112.png");f.stop()
        }catch(e:Throwable){result.put("status","FAIL").put("error",e.toString()).put("snapshot",f.snap());throw e}
        finally{File(f.context.getExternalFilesDir(null),"player-dpad112.json").writeText(result.toString(2));f.close()}
        Unit
    }
}
