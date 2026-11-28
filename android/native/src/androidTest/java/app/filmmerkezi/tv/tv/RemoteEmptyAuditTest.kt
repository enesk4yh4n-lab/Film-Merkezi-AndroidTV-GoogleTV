package app.filmmerkezi.tv.tv

import android.view.*
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.settings.AppSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RemoteEmptyAuditTest {
    @Test fun setupEmptyErrorLoadingAndLateResultsKeepRemoteUsable()=runBlocking {
        val results=JSONArray()
        for(mode in listOf("setup","empty","error","late","refresh","disabled")){
            val f=Settings110Fixture();val base=RemoteAuditData()
            var updated=false
            fun key(k:Int){f.ins.sendKeyDownUpSync(k);f.ins.waitForIdleSync()}
            val data=object:TvData by base {
                override fun filteredBrowse(siteId:String,query:CatalogQuery,cursors:Map<String,PageCursor>)=flow {
                    emit(UnifiedPage(if(mode=="refresh")base.movies.take(12).map(base::item)else emptyList(),emptyMap(),emptyList()))
                    if(mode=="error")throw java.io.IOException("Local remote test error")
                    if(mode=="late"){delay(1300);emit(UnifiedPage(base.movies.map(base::item),emptyMap(),emptyList()))}
                    if(mode=="refresh"){delay(1800);emit(UnifiedPage(base.movies.reversed().map(base::item),emptyMap(),emptyList()));updated=true}
                }
            }
            try{
                TvActivity.forceSetupForTests=mode=="setup"
                f.start(AppSettings(disabledCatalog=if(mode=="disabled")Sites.all.map{it.id}.toSet()else emptySet()),data,configured=mode!="setup")
                if(mode=="setup"){
                    for(key in listOf(KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_UP)){
                        key(key);f.main{assertTrue(f.activity.currentFocus?.isShown==true)}
                    }
                    key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{UiPreferences(f.context).step==1}
                }else{
                    f.main{f.activity.findViewById<View>(R.id.tv_nav_movies).requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN)
                    f.waitFor(4000){f.activity.findViewById<View>(R.id.tv_content)?.hasFocus()==true}
                    key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_LEFT);f.main{assertNotNull(f.activity.currentFocus)}
                    if(mode=="refresh"){
                        key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_RIGHT);f.waitFor{f.activity.currentFocus?.tag==base.movies[2].workId}
                        val before=android.graphics.Rect();f.main{f.activity.currentFocus!!.getGlobalVisibleRect(before)}
                        f.waitFor(5000){updated};f.main{assertEquals(base.movies[2].workId,f.activity.currentFocus?.tag);val after=android.graphics.Rect();f.activity.currentFocus!!.getGlobalVisibleRect(after);assertTrue(kotlin.math.abs(after.top-before.top)<5)}
                    }
                    if(mode=="late"){
                        f.waitFor(5000){f.tag(base.movies.first().workId)!=null};key(KeyEvent.KEYCODE_DPAD_RIGHT);f.waitFor(4000){f.activity.currentFocus?.tag==base.movies[1].workId}
                        key(KeyEvent.KEYCODE_DPAD_UP);f.main{assertNotNull(f.activity.currentFocus)}
                    }
                    key(KeyEvent.KEYCODE_DPAD_UP);f.main{assertNotNull(f.activity.currentFocus)};key(KeyEvent.KEYCODE_BACK);f.waitFor{f.activity.findViewById<View>(R.id.tv_hero_action)?.isShown==true}
                }
                results.put(JSONObject().put("case",mode).put("passed",true))
            }catch(e:Throwable){results.put(JSONObject().put("case",mode).put("passed",false).put("error",e.toString()))}
            finally{TvActivity.forceSetupForTests=false;f.close()}
        }
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null),"remote-empty-audit.json").writeText(results.toString(2))
        assertTrue(results.toString(2),(0 until results.length()).all{results.getJSONObject(it).getBoolean("passed")})
    }
}
