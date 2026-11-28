package app.filmmerkezi.tv.tv

import android.view.*
import androidx.media3.ui.R as MediaR
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.settings.*
import app.filmmerkezi.tv.PlaybackSettings
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RemoteBoundaryAuditTest {
    @Test fun settingsAndPlayerBoundaries()=runBlocking {
        val f=Settings110Fixture(true);val report=JSONArray()
        fun key(code:Int){f.ins.sendKeyDownUpSync(code);f.ins.waitForIdleSync()}
        suspend fun check(name:String,block:suspend()->Unit){try{block();report.put(JSONObject().put("case",name).put("passed",true))}catch(e:Throwable){report.put(JSONObject().put("case",name).put("passed",false).put("error",e.toString()))}}
        try {
            f.start(AppSettings(entry="RESTART",player=PlaybackSettings(controlsSeconds=0)))
            f.play();key(KeyEvent.KEYCODE_MEDIA_PAUSE)
            f.waitFor{f.activity.snapshotForUiTest().optBoolean("controllerVisible")}
            check("player_button_up_reaches_timeline") {
                f.main{f.activity.findViewById<View>(MediaR.id.exo_play_pause).requestFocus()};key(KeyEvent.KEYCODE_DPAD_UP)
                delay(100);f.main{assertEquals(MediaR.id.exo_progress,f.activity.currentFocus?.id)}
            }
            f.main{f.activity.settingsForTest()};f.waitFor{f.tag("settings-category:general")?.hasFocus()==true}
            check("settings_right_is_not_consumed_by_player") {
                key(KeyEvent.KEYCODE_DPAD_RIGHT);delay(100);f.main{assertTrue(f.activity.currentFocus?.tag.toString().startsWith("setting:"))}
            }
            check("settings_arrows_never_seek_hidden_video") {
                f.main{f.tag("settings-category:general").requestFocus()};val before=f.snap().getLong("positionMs")
                key(KeyEvent.KEYCODE_DPAD_RIGHT);delay(100);assertTrue(kotlin.math.abs(f.snap().getLong("positionMs")-before)<250)
                key(KeyEvent.KEYCODE_DPAD_LEFT);delay(100);assertTrue(kotlin.math.abs(f.snap().getLong("positionMs")-before)<250)
            }
            check("settings_category_roundtrip_keeps_selected_option") {
                f.main{f.tag("settings-category:audio").requestFocus()};key(KeyEvent.KEYCODE_DPAD_RIGHT)
                repeat(3){key(KeyEvent.KEYCODE_DPAD_DOWN)};val target=f.activity.currentFocus?.tag
                assertTrue(target.toString().startsWith("setting:"))
                key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_RIGHT);delay(100)
                f.main{assertNotEquals("setting:source-audio",target);assertEquals(target,f.activity.currentFocus?.tag)}
            }
        }finally{File(f.context.getExternalFilesDir(null),"remote-boundaries.json").writeText(report.toString(2));f.close()}
        assertTrue(report.toString(2),(0 until report.length()).all{report.getJSONObject(it).getBoolean("passed")})
    }
}
