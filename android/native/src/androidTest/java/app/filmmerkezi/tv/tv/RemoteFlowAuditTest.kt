package app.filmmerkezi.tv.tv

import android.content.DialogInterface
import android.view.*
import android.widget.*
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.settings.AppSettings
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RemoteFlowAuditTest {
    @Test fun homeCollectionsSiteWindowsAndSettingsForms()=runBlocking {
        val f=Settings110Fixture();val data=RemoteAuditData();val results=JSONArray()
        fun key(k:Int){f.ins.sendKeyDownUpSync(k);f.ins.waitForIdleSync()}
        suspend fun check(name:String,block:suspend()->Unit){try{block();results.put(JSONObject().put("case",name).put("passed",true))}catch(e:Throwable){results.put(JSONObject().put("case",name).put("passed",false).put("error",e.toString()))}}
        suspend fun focus(id:Int){f.waitFor(4000){f.activity.findViewById<View>(id)?.isShown==true};f.main{f.activity.findViewById<View>(id).requestFocus()}}
        suspend fun nav(id:Int){focus(id);key(KeyEvent.KEYCODE_DPAD_CENTER);delay(150)}
        suspend fun setting(category:String,row:String){f.main{f.tag("settings-category:$category").requestFocus()};key(KeyEvent.KEYCODE_DPAD_RIGHT)
            repeat(30){if(f.activity.currentFocus?.tag=="setting:$row")return;key(KeyEvent.KEYCODE_DPAD_DOWN)};error("Missing setting $category/$row")}
        suspend fun settingsDialog():ClassicDialog {f.waitFor(5000){f.activity.settingsDialogForTest()?.isShowing==true};return f.activity.settingsDialogForTest()!!}
        fun texts(root:View):List<View> = if(root is ViewGroup)(0 until root.childCount).flatMap{texts(root.getChildAt(it))}else if(root is TextView)listOf(root)else emptyList()
        fun findScroll(v:View):ScrollView?=if(v is ScrollView)v else if(v is ViewGroup)(0 until v.childCount).firstNotNullOfOrNull{findScroll(v.getChildAt(it))}else null
        try {
            f.start(AppSettings(),data)
            check("header_horizontal_and_options_sites_vertical") {
                focus(R.id.tv_query);f.main{f.activity.findViewById<EditText>(R.id.tv_query).setSelection(0)};key(KeyEvent.KEYCODE_DPAD_LEFT);f.main{assertEquals(R.id.tv_query,f.activity.currentFocus?.id)}
                focus(R.id.tv_search_submit)
                for(id in listOf(R.id.classic_search_clear,R.id.classic_search_recent,R.id.tv_nav_about)){key(KeyEvent.KEYCODE_DPAD_RIGHT);f.main{assertEquals(id,f.activity.currentFocus?.id)}}
                key(KeyEvent.KEYCODE_DPAD_DOWN);f.main{assertEquals(R.id.tv_site_picker,f.activity.currentFocus?.id)};key(KeyEvent.KEYCODE_DPAD_UP);f.main{assertEquals(R.id.tv_nav_about,f.activity.currentFocus?.id)}
            }
            check("hero_horizontal_edges") {
                focus(R.id.tv_hero_action);key(KeyEvent.KEYCODE_DPAD_LEFT);f.main{assertEquals(R.id.tv_hero_action,f.activity.currentFocus?.id)}
                key(KeyEvent.KEYCODE_DPAD_RIGHT);f.main{assertEquals(R.id.classic_recommend,f.activity.currentFocus?.id)}
                key(KeyEvent.KEYCODE_DPAD_RIGHT);f.main{assertEquals(R.id.classic_recommend,f.activity.currentFocus?.id)}
            }
            check("home_card_left_edge_does_not_jump_up_or_right") {
                focus(R.id.tv_hero_action);key(KeyEvent.KEYCODE_DPAD_DOWN);f.waitFor(4000){f.activity.currentFocus?.tag==data.movies.first().workId}
                key(KeyEvent.KEYCODE_DPAD_LEFT);f.main{assertEquals(data.movies.first().workId,f.activity.currentFocus?.tag)}
                key(KeyEvent.KEYCODE_DPAD_RIGHT);f.waitFor(4000){f.activity.currentFocus?.tag==data.movies[1].workId}
            }
            check("home_row_arrows_reachable_and_down_returns_card") {
                key(KeyEvent.KEYCODE_DPAD_UP);f.waitFor(4000){f.activity.currentFocus?.tag.toString().let{it.startsWith("home-row:")&&it.endsWith(":previous")}}
                val next=f.activity.currentFocus?.tag.toString().removeSuffix(":previous")+":next"
                key(KeyEvent.KEYCODE_DPAD_RIGHT);f.waitFor(4000){f.activity.currentFocus?.tag==next}
                key(KeyEvent.KEYCODE_DPAD_DOWN);f.waitFor(4000){f.activity.currentFocus?.tag==data.movies[1].workId}
            }
            check("sites_popup_back_restores_header_button") {focus(R.id.tv_site_picker);key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor(4000){f.activity.classicOverlayForTest()?.focusedKey()?.startsWith("site:")==true};key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_BACK);f.waitFor(4000){f.activity.currentFocus?.id==R.id.tv_site_picker}}
            check("search_history_list_and_back_focus") {focus(R.id.classic_search_recent);key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor(4000){f.activity.siteDialogForTest()?.isShowing==true};key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_BACK);f.waitFor(4000){f.activity.currentFocus?.id==R.id.classic_search_recent}}
            check("recommendation_criteria_results_and_back") {
                focus(R.id.classic_recommend);key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor(4000){f.activity.siteDialogForTest()?.isShowing==true}
                key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN)
                val d=f.activity.siteDialogForTest()!!;f.main{d.getButton(DialogInterface.BUTTON_POSITIVE).requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER)
                f.waitFor(4000){f.activity.siteDialogForTest()?.choiceCount==3};key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_BACK);f.waitFor(4000){f.activity.currentFocus?.id==R.id.classic_recommend}
            }
            nav(R.id.tv_nav_discover);focus(R.id.fm_collections);key(KeyEvent.KEYCODE_DPAD_CENTER)
            f.waitFor(4000){f.activity.siteDialogForTest()?.isShowing==true};key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor(5000){f.tag("collection:0")!=null}
            check("collection_two_column_arrows_and_detail_back") {
                f.main{f.tag("collection:0").requestFocus()};key(KeyEvent.KEYCODE_DPAD_RIGHT);f.waitFor(4000){f.tag("collection:1").hasFocus()};key(KeyEvent.KEYCODE_DPAD_DOWN);f.waitFor(4000){f.tag("collection:3").hasFocus()};key(KeyEvent.KEYCODE_DPAD_LEFT);f.waitFor(4000){f.tag("collection:2").hasFocus()};key(KeyEvent.KEYCODE_DPAD_UP);f.waitFor(4000){f.tag("collection:0").hasFocus()}
                key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor(4000){f.activity.findViewById<View>(R.id.tv_play_action)!=null};key(KeyEvent.KEYCODE_BACK);f.waitFor(4000){f.tag("collection:0")?.hasFocus()==true}
            }
            nav(R.id.tv_nav_movies);f.waitFor{f.tag(data.movies[0].workId)!=null};f.main{f.tag(data.movies[0].workId).requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.activity.findViewById<View>(R.id.tv_play_action)!=null}
            check("detail_sites_popup_and_back") {focus(R.id.fm_detail_sites);key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.activity.classicOverlayForTest()?.showing==true};key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_BACK);f.waitFor{f.activity.currentFocus?.id==R.id.fm_detail_sites}}
            check("entry_without_resume_skips_disabled_continue_and_cancels") {
                data.includeRecords=false;focus(R.id.tv_play_action);key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.activity.classicOverlayForTest()?.focusedKey()=="restart"}
                key(KeyEvent.KEYCODE_DPAD_LEFT);assertEquals("restart",f.activity.classicOverlayForTest()?.focusedKey());key(KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals("exit",f.activity.classicOverlayForTest()?.focusedKey())
                key(KeyEvent.KEYCODE_BACK);f.waitFor{f.activity.currentFocus?.id==R.id.tv_play_action};assertEquals(0,f.snap().optInt("enginesCreated"));data.includeRecords=true
            }
            check("detail_long_text_scroll_and_back_focus") {
                val more=texts(f.activity.window.decorView).first{it is Button&&it.text.toString()==f.context.getString(R.string.classic_read_more)}
                f.main{more.requestFocus()};key(KeyEvent.KEYCODE_DPAD_LEFT);f.main{assertSame(more,f.activity.currentFocus)};key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.activity.siteDialogForTest()?.isShowing==true}
                val d=f.activity.siteDialogForTest()!!;val scroll=findScroll(d.window!!.decorView)!!
                f.waitFor(4000){scroll.height>0&&scroll.getChildAt(0).height>scroll.height&&scroll.hasFocus()};delay(100)
                key(KeyEvent.KEYCODE_DPAD_DOWN);f.main{assertTrue("Text focus=${d.currentFocus?.javaClass?.simpleName} height=${scroll.height} content=${scroll.getChildAt(0).height}",scroll.scrollY>0)};key(KeyEvent.KEYCODE_BACK);f.waitFor{more.hasFocus()}
            }
            nav(R.id.tv_nav_home);f.main{f.activity.settingsForTest()}
            for((cat,row) in listOf("general" to "start","general" to "rows","video" to "quality","audio" to "audio","remote" to "seek","storage" to "cache","storage" to "history","storage" to "reset"))check("settings_popup_${cat}_$row") {
                setting(cat,row);key(KeyEvent.KEYCODE_DPAD_CENTER);val d=settingsDialog();delay(120)
                val original=d.currentFocus;assertNotNull(original);key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_UP);key(KeyEvent.KEYCODE_BACK)
                f.waitFor(4000){f.tag("setting:$row")?.hasFocus()==true}
            }
            check("site_management_name_url_and_horizontal_actions") {
                setting("catalog","site-management");key(KeyEvent.KEYCODE_DPAD_CENTER);settingsDialog();key(KeyEvent.KEYCODE_DPAD_CENTER)
                f.waitFor(4000){f.activity.settingsDialogForTest()?.window?.decorView?.findViewWithTag<View>("site-manager-name")!=null}
                val d=settingsDialog();val root=d.window!!.decorView;val name=root.findViewWithTag<View>("site-manager-name");val address=root.findViewWithTag<View>("site-manager-address")
                f.main{name.requestFocus()};key(KeyEvent.KEYCODE_DPAD_DOWN);f.main{assertSame(address,d.currentFocus)};key(KeyEvent.KEYCODE_DPAD_DOWN);f.main{assertTrue(d.currentFocus?.tag in listOf("site-manager-test","site-manager-reset"))}
                f.main{root.findViewWithTag<View>("site-manager-test").requestFocus()};key(KeyEvent.KEYCODE_DPAD_RIGHT);f.main{assertEquals("site-manager-reset",d.currentFocus?.tag)}
                key(KeyEvent.KEYCODE_DPAD_RIGHT);f.main{assertEquals("site-manager-reset",d.currentFocus?.tag)};key(KeyEvent.KEYCODE_DPAD_UP);f.main{assertSame(address,d.currentFocus)}
                key(KeyEvent.KEYCODE_BACK);settingsDialog();key(KeyEvent.KEYCODE_BACK);delay(150)
            }
            repeat(3){if(f.activity.settingsDialogForTest()?.isShowing==true){key(KeyEvent.KEYCODE_BACK);delay(100)}}
            assertFalse(f.activity.settingsDialogForTest()?.isShowing==true)
            key(KeyEvent.KEYCODE_BACK);f.waitFor(4000){f.tag("settings-category:catalog")==null}
            nav(R.id.tv_nav_home);key(KeyEvent.KEYCODE_BACK);f.waitFor(4000){f.activity.siteDialogForTest()?.isShowing==true}
            check("application_exit_confirmation_buttons") {val d=f.activity.siteDialogForTest()!!;f.main{d.getButton(DialogInterface.BUTTON_POSITIVE).requestFocus()};key(KeyEvent.KEYCODE_DPAD_RIGHT);f.main{assertSame(d.getButton(DialogInterface.BUTTON_NEGATIVE),d.currentFocus)};key(KeyEvent.KEYCODE_BACK);f.waitFor(4000){f.activity.currentFocus?.id==R.id.tv_nav_home}}
        }finally{File(f.context.getExternalFilesDir(null),"remote-flow-audit.json").writeText(results.toString(2));f.close()}
        assertTrue(results.toString(2),(0 until results.length()).all{results.getJSONObject(it).getBoolean("passed")})
    }
}
