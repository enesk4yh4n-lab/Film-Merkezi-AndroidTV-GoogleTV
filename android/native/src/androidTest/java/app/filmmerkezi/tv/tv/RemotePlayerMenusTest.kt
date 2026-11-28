package app.filmmerkezi.tv.tv

import android.view.*
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.PlaybackSettings
import app.filmmerkezi.tv.settings.AppSettings
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RemotePlayerMenusTest {
    @Test fun realPlayerButtonsPanelsAndExitUseRemoteWithoutReplacingEngine()=runBlocking {
        val f=Settings110Fixture(continuous=true,resume=true);val results=JSONArray()
        fun key(code:Int){f.ins.sendKeyDownUpSync(code);f.ins.waitForIdleSync()}
        suspend fun check(name:String,block:suspend()->Unit){try{block();results.put(JSONObject().put("case",name).put("passed",true))}catch(e:Throwable){results.put(JSONObject().put("case",name).put("passed",false).put("error",e.toString()))}}
        suspend fun button(id:Int){f.main{f.activity.findViewById<View>(id).requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER)}
        try {
            f.start(AppSettings(entry="ASK",player=PlaybackSettings(controlsSeconds=0)))
            f.main{f.activity.findViewById<View>(R.id.tv_nav_series).requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.tag(f.card.workId)!=null}
            f.main{f.tag(f.card.workId).requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER)
            f.waitFor{f.activity.classicOverlayForTest()?.focusedKey()=="continue"}
            check("entry_actions_direction_and_no_early_engine") {
                assertEquals(0,f.snap().optInt("enginesCreated"));key(KeyEvent.KEYCODE_DPAD_LEFT);assertEquals("continue",f.activity.classicOverlayForTest()?.focusedKey())
                key(KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals("restart",f.activity.classicOverlayForTest()?.focusedKey());key(KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals("exit",f.activity.classicOverlayForTest()?.focusedKey())
                key(KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals("exit",f.activity.classicOverlayForTest()?.focusedKey());key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_CENTER)
            }
            f.waitFor{f.activity.snapshotForUiTest().optBoolean("firstFrame")};val engine=f.snap().getInt("engineIdentity")
            check("local_playback_twenty_seconds") {f.waitFor(30000){f.activity.snapshotForUiTest().optLong("positionMs")>=20000};assertEquals(engine,f.snap().getInt("engineIdentity"))}
            key(KeyEvent.KEYCODE_MEDIA_PAUSE);f.waitFor{!f.activity.snapshotForUiTest().optBoolean("isPlaying")}
            val position=f.snap().getLong("positionMs")
            check("button_order_edges_skip_unavailable_episodes") {
                val ids=listOf(R.id.fm_player_rewind,androidx.media3.ui.R.id.exo_play_pause,R.id.fm_player_forward,R.id.fm_player_sources,R.id.fm_player_quality,R.id.fm_player_audio,R.id.fm_player_subtitles,R.id.fm_player_settings,R.id.fm_player_exit)
                f.main{f.activity.findViewById<View>(ids.first()).requestFocus()};key(KeyEvent.KEYCODE_DPAD_LEFT);f.main{assertEquals(ids.first(),f.activity.currentFocus?.id)}
                for(id in ids.drop(1)){key(KeyEvent.KEYCODE_DPAD_RIGHT);f.main{assertEquals(id,f.activity.currentFocus?.id)}}
                key(KeyEvent.KEYCODE_DPAD_RIGHT);f.main{assertEquals(ids.last(),f.activity.currentFocus?.id)}
                for(id in ids.dropLast(1).reversed()){key(KeyEvent.KEYCODE_DPAD_LEFT);f.main{assertEquals(id,f.activity.currentFocus?.id)}}
                assertTrue(kotlin.math.abs(f.snap().getLong("positionMs")-position)<250)
            }
            for(id in listOf(R.id.fm_player_sources,R.id.fm_player_quality,R.id.fm_player_audio,R.id.fm_player_subtitles,R.id.fm_player_settings))check("actual_panel_$id") {
                button(id);f.waitFor{f.activity.playerOverlayForTest()?.showing==true};val panel=f.activity.playerOverlayForTest()!!
                val selected=panel.focusedKey();assertNotNull(selected)
                key(KeyEvent.KEYCODE_DPAD_LEFT);assertEquals(selected,panel.focusedKey());key(KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals(selected,panel.focusedKey())
                key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_UP);delay(80);assertNotNull(panel.focusedKey())
                key(KeyEvent.KEYCODE_BACK);f.waitFor{f.activity.currentFocus?.id==id};assertEquals(engine,f.snap().getInt("engineIdentity"))
            }
            check("timebar_up_and_down_restore_opening_button") {
                f.main{f.activity.findViewById<View>(R.id.fm_player_audio).requestFocus()};key(KeyEvent.KEYCODE_DPAD_UP);f.main{assertEquals(androidx.media3.ui.R.id.exo_progress,f.activity.currentFocus?.id)}
                key(KeyEvent.KEYCODE_DPAD_DOWN);f.main{assertEquals(R.id.fm_player_audio,f.activity.currentFocus?.id)}
            }
            check("media_keys_seek_and_stop_opens_exit") {
                key(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);f.waitFor{f.activity.snapshotForUiTest().optLong("positionMs")>=position+9900};key(KeyEvent.KEYCODE_MEDIA_REWIND)
                key(KeyEvent.KEYCODE_MEDIA_STOP);f.waitFor{f.activity.classicOverlayForTest()?.showing==true};assertEquals("continue",f.activity.classicOverlayForTest()?.focusedKey())
            }
            check("exit_four_actions_and_cancel_restores_paused") {
                for(name in listOf("restart","save","discard")){key(KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals(name,f.activity.classicOverlayForTest()?.focusedKey())}
                key(KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals("discard",f.activity.classicOverlayForTest()?.focusedKey());key(KeyEvent.KEYCODE_BACK);f.waitFor{!f.activity.snapshotForUiTest().optBoolean("exit")};assertFalse(f.snap().getBoolean("isPlaying"))
            }
            check("exit_button_continue_and_restart_keep_engine") {
                button(R.id.fm_player_exit);f.waitFor{f.activity.classicOverlayForTest()?.showing==true};key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.activity.snapshotForUiTest().optBoolean("isPlaying")}
                button(R.id.fm_player_exit);f.waitFor{f.activity.classicOverlayForTest()?.showing==true};key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.activity.snapshotForUiTest().optLong("positionMs")<1500};assertEquals(engine,f.snap().getInt("engineIdentity"))
            }
            f.screenshot("remote-player-menus.png");f.stop()
        }finally{File(f.context.getExternalFilesDir(null),"remote-player-menus.json").writeText(results.toString(2));f.close()}
        assertTrue(results.toString(2),(0 until results.length()).all{results.getJSONObject(it).getBoolean("passed")})
    }
}
