package app.filmmerkezi.tv.tv

import android.view.*
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.settings.AppSettings
import app.filmmerkezi.tv.updates.DistributionManager
import app.filmmerkezi.tv.updates.ModuleRuntime
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Reproduces the reported empty-install, right-column and Update Center header paths. */
class ReportedNavigationTest {
    @Test fun reportedRoutesFollowTheVisibleLayout()=runBlocking {
        val results=JSONArray()
        suspend fun check(name:String,block:suspend()->Unit){try{block();results.put(JSONObject().put("case",name).put("passed",true))}catch(e:Throwable){results.put(JSONObject().put("case",name).put("passed",false).put("error",e.toString()))}}
        val empty=Settings110Fixture();val manager=DistributionManager.get(empty.context);val original=manager.store.snapshot()
        fun restore(target:JSONObject,source:JSONObject){target.keys().asSequence().toList().forEach(target::remove);source.keys().forEach{target.put(it,source.get(it))}}
        fun key(f:Settings110Fixture,k:Int){f.ins.sendKeyDownUpSync(k);f.ins.waitForIdleSync()}
        suspend fun id(f:Settings110Fixture,value:Int){f.main{f.activity.findViewById<View>(value).requestFocus()}}
        try{
            ModuleRuntime.stop();manager.store.commit{restore(it,JSONObject().put("schemaVersion",1).put("repositories",JSONObject()).put("packages",JSONObject()))};ModuleRuntime.register(manager.store.snapshot())
            empty.start(customData=UpdateTestData.empty());empty.waitFor{empty.activity.hasWindowFocus()}
            // The activity keeps its local data fixture; render the actual production empty-install branch.
            empty.main{TvActivity.dataFactoryForTests=null;empty.activity.findViewById<View>(R.id.tv_nav_movies).performClick();empty.activity.findViewById<View>(R.id.tv_nav_home).performClick()}
            val cta=empty.activity.window.decorView.getFocusables(View.FOCUS_FORWARD).single{it is android.widget.Button&&it.text.toString()==empty.context.getString(R.string.updates_add_repository)}
            check("no_repository_nav_down_reaches_add_repository") {id(empty,R.id.tv_nav_home);key(empty,KeyEvent.KEYCODE_DPAD_DOWN);empty.main{assertSame(cta,empty.activity.currentFocus)}}
            check("no_repository_right_column_reaches_add_repository") {id(empty,R.id.tv_site_picker);key(empty,KeyEvent.KEYCODE_DPAD_DOWN);empty.main{assertSame(cta,empty.activity.currentFocus)}}
            check("no_repository_action_opens_update_center") {
                empty.main{cta.requestFocus()};key(empty,KeyEvent.KEYCODE_DPAD_CENTER);empty.waitFor(6000){empty.activity.updateCenterForTest()?.keys()==listOf("search","search-user","address")};key(empty,KeyEvent.KEYCODE_BACK);key(empty,KeyEvent.KEYCODE_BACK)
                empty.waitFor(6000){empty.activity.hasWindowFocus()&&empty.tag("catalog:add-repository")?.isShown==true}
                id(empty,R.id.tv_nav_home);key(empty,KeyEvent.KEYCODE_DPAD_DOWN);empty.main{assertSame(empty.tag("catalog:add-repository"),empty.activity.currentFocus)}
            }
            empty.screenshot("reported-empty-install.png")
        }finally{empty.close();manager.store.commit{restore(it,original)};ModuleRuntime.register(original)}
        val f=Settings110Fixture()
        try {
            f.start(AppSettings(),RemoteAuditData());f.waitFor{f.activity.hasWindowFocus()}
            check("home_tools_match_their_visual_order") {
                var top=0;var bottom=0;f.main{val sort=f.activity.findViewById<View>(R.id.classic_sort);val response=f.tag("catalog:responses");val a=IntArray(2);val b=IntArray(2);sort.getLocationOnScreen(a);response.getLocationOnScreen(b);top=a[1];bottom=b[1]};assertTrue(top<bottom)
                id(f,R.id.tv_site_picker);key(f,KeyEvent.KEYCODE_DPAD_DOWN);f.main{assertEquals(R.id.classic_sort,f.activity.currentFocus?.id)}
                key(f,KeyEvent.KEYCODE_DPAD_DOWN);f.main{assertSame(f.tag("catalog:responses"),f.activity.currentFocus)}
                key(f,KeyEvent.KEYCODE_DPAD_UP);f.main{assertEquals(R.id.classic_sort,f.activity.currentFocus?.id)}
                key(f,KeyEvent.KEYCODE_DPAD_UP);f.main{assertEquals(R.id.tv_site_picker,f.activity.currentFocus?.id)}
            }
            check("home_right_column_down_then_up_returns_to_responses") {
                f.main{f.tag("catalog:responses").requestFocus()};key(f,KeyEvent.KEYCODE_DPAD_DOWN);f.main{assertEquals(R.id.tv_hero_action,f.activity.currentFocus?.id)}
                key(f,KeyEvent.KEYCODE_DPAD_UP);f.main{assertSame(f.tag("catalog:responses"),f.activity.currentFocus)}
            }
            check("catalog_right_column_keeps_its_different_visual_order") {
                id(f,R.id.tv_nav_movies);key(f,KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.activity.findViewById<View>(R.id.classic_sort)!=null}
                id(f,R.id.tv_site_picker);key(f,KeyEvent.KEYCODE_DPAD_DOWN);f.main{assertSame(f.tag("catalog:responses"),f.activity.currentFocus)}
                key(f,KeyEvent.KEYCODE_DPAD_DOWN);f.main{assertEquals(R.id.classic_sort,f.activity.currentFocus?.id)}
                key(f,KeyEvent.KEYCODE_DPAD_RIGHT);f.main{assertEquals(R.id.classic_sort,f.activity.currentFocus?.id)}
                key(f,KeyEvent.KEYCODE_DPAD_UP);f.main{assertSame(f.tag("catalog:responses"),f.activity.currentFocus)}
            }
            f.main{f.activity.settingsForTest();f.tag("settings-category:updates").requestFocus()};f.waitFor{f.activity.hasWindowFocus()&&f.activity.updateCenterForTest()?.keys()?.contains("add")==true}
            check("update_center_root_up_reaches_settings_back") {
                key(f,KeyEvent.KEYCODE_DPAD_RIGHT);f.waitFor{f.tag("updates:add")?.hasFocus()==true};key(f,KeyEvent.KEYCODE_DPAD_UP);f.main{assertSame(f.tag("settings:back"),f.activity.currentFocus)}
                key(f,KeyEvent.KEYCODE_DPAD_DOWN);f.waitFor{f.tag("updates:add")?.hasFocus()==true}
            }
            f.main{f.activity.updateCenterForTest()!!.requestKey("technical")};f.waitFor{f.tag("updates:technical")?.hasFocus()==true};key(f,KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.tag("updates:diagnostics")!=null}
            check("nested_update_back_reaches_outer_back_and_returns") {
                f.main{f.activity.updateCenterForTest()!!.focusFirst()};key(f,KeyEvent.KEYCODE_DPAD_UP);f.waitFor{f.tag("updates:back")?.hasFocus()==true}
                key(f,KeyEvent.KEYCODE_DPAD_UP);f.main{assertSame(f.tag("settings:back"),f.activity.currentFocus)}
                key(f,KeyEvent.KEYCODE_DPAD_DOWN);f.waitFor{f.tag("updates:back")?.hasFocus()==true}
            }
        }finally{File(f.context.getExternalFilesDir(null),"reported-navigation.json").writeText(results.toString(2));f.close()}
        assertTrue(results.toString(2),(0 until results.length()).all{results.getJSONObject(it).getBoolean("passed")})
    }
}
