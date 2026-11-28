package app.filmmerkezi.tv.tv

import android.view.*
import app.filmmerkezi.tv.settings.AppSettings
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RemoteWindowAuditTest {
    @Test fun sidePanelsAndAllSettingsCategoriesUseRemote()=runBlocking {
        val f=Settings110Fixture();val results=JSONArray();var panel:ClassicOverlay?=null
        fun key(code:Int){f.ins.sendKeyDownUpSync(code);f.ins.waitForIdleSync()}
        suspend fun check(name:String,block:suspend()->Unit){try{block();results.put(JSONObject().put("case",name).put("passed",true))}catch(e:Throwable){results.put(JSONObject().put("case",name).put("passed",false).put("error",e.toString()))}}
        suspend fun selected(key:String){f.waitFor(4000){panel!!.focusedKey()==key}}
        try {
            val large=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("large")=="true"
            f.start(AppSettings(largeText=large,player=app.filmmerkezi.tv.PlaybackSettings(customSubtitles=true)),RemoteAuditData())
            f.main{f.activity.settingsForTest()}
            for(category in listOf("general","catalog","playback","video","audio","updates","remote","storage","about")){
                check("settings_${category}_all_rows_and_edges"){
                    f.main{f.tag("settings-category:$category").requestFocus()};delay(150);key(KeyEvent.KEYCODE_DPAD_RIGHT)
                    f.waitFor(5000){f.activity.currentFocus?.tag?.toString()?.let{it.startsWith("setting:")||it.startsWith("updates:")}==true}
                    val visited=mutableSetOf<String>();var last=""
                    repeat(45){
                        val focus=f.activity.currentFocus?.tag.toString();assertTrue("$category focus=$focus",focus.startsWith("setting:")||focus.startsWith("updates:"));visited.add(focus)
                        key(KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals(focus,f.activity.currentFocus?.tag)
                        if(focus!=last){last=focus;key(KeyEvent.KEYCODE_DPAD_DOWN);delay(30)}
                    }
                    assertTrue(visited.isNotEmpty());key(KeyEvent.KEYCODE_DPAD_LEFT);f.waitFor(3000){f.tag("settings-category:$category").hasFocus()}
                    key(KeyEvent.KEYCODE_DPAD_RIGHT);f.waitFor(3000){f.activity.currentFocus?.tag.toString() in visited}
                }
            }
            key(KeyEvent.KEYCODE_BACK);delay(120)
            val rows=listOf(ClassicRow("heading","Site",header=true))+(0 until 65).map{ClassicRow("row:$it","Kaynak $it",selected=it==0,enabled=it!=2)}
            val actions=listOf(ClassicAction("retry","Yeniden dene"){},ClassicAction("close","Kapat"){})
            f.main{panel=ClassicOverlay(f.activity,"Kumanda uzun panel deneyi",true,f.activity.currentFocus);panel!!.update(rows,actions)}
            selected("row:0")
            check("panel_rows_horizontal_boundaries") {key(KeyEvent.KEYCODE_DPAD_RIGHT);selected("row:0");key(KeyEvent.KEYCODE_DPAD_LEFT);selected("row:0")}
            check("panel_up_reaches_back_and_down_returns_row") {key(KeyEvent.KEYCODE_DPAD_UP);f.main{assertEquals("classic-panel:back",panel!!.focusedKey())};key(KeyEvent.KEYCODE_DPAD_DOWN);selected("row:0")}
            check("panel_back_survives_live_status_refresh") {key(KeyEvent.KEYCODE_DPAD_UP);f.main{panel!!.update(rows,actions,"Yeni durum")};delay(150);selected("classic-panel:back");key(KeyEvent.KEYCODE_DPAD_DOWN);selected("row:0")}
            check("panel_scroll_skips_disabled_and_keeps_direction") {f.main{panel!!.focus("row:0")};delay(80);repeat(38){key(KeyEvent.KEYCODE_DPAD_DOWN);delay(20)};selected("row:39")}
            check("panel_live_resize_preserves_row_and_scroll") {f.main{panel!!.update(rows.map{it.copy(description="Güncellenen açıklama ".repeat(3))},actions)};delay(200);selected("row:39");key(KeyEvent.KEYCODE_DPAD_DOWN);selected("row:40")}
            check("panel_footer_and_back_to_last_row") {repeat(30){key(KeyEvent.KEYCODE_DPAD_DOWN);delay(15)};selected("retry");key(KeyEvent.KEYCODE_DPAD_RIGHT);selected("close");key(KeyEvent.KEYCODE_DPAD_RIGHT);selected("close");key(KeyEvent.KEYCODE_DPAD_UP);selected("row:64")}
            check("panel_empty_keeps_action_accessible") {f.main{panel!!.update(emptyList(),actions)};selected("retry");key(KeyEvent.KEYCODE_DPAD_LEFT);selected("retry")}
            key(KeyEvent.KEYCODE_BACK);f.waitFor(3000){panel?.showing==false};f.main{assertNotNull(f.activity.currentFocus)}
        }finally{f.main{panel?.close()};File(f.context.getExternalFilesDir(null),"remote-window-audit.json").writeText(results.toString(2));f.close()}
        assertTrue(results.toString(2),(0 until results.length()).all{results.getJSONObject(it).getBoolean("passed")})
    }
}
