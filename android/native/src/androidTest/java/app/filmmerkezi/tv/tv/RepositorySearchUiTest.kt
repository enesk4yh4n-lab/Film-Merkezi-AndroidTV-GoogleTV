package app.filmmerkezi.tv.tv

import android.os.SystemClock
import android.view.*
import android.widget.EditText
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.settings.AppSettings
import app.filmmerkezi.tv.updates.DistributionManager
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Explicit live probe of the real native Options flow; no site or playback traffic. */
class RepositorySearchUiTest {
    @Test fun setupSearchFocusesVerifiedRepositoryAndCanSkipByRemote()=runBlocking<Unit> {
        val f=Settings110Fixture()
        val expected="updates:enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV-Eklentiler"
        fun press(code:Int)=f.ins.sendKeyDownUpSync(code)
        val store=DistributionManager.get(f.context).store
        val repositoryCount=store.repositoryCount
        try {
            check(UiPreferences(f.context).step(2))
            TvActivity.forceSetupForTests=true
            f.start(AppSettings(),UpdateTestData.empty(),configured=false)
            f.waitFor(35000){f.activity.currentFocus?.tag==expected}
            f.screenshot("setup-repository-results.png")
            press(KeyEvent.KEYCODE_DPAD_LEFT)
            f.waitFor{f.activity.currentFocus?.tag==expected}
            press(KeyEvent.KEYCODE_DPAD_UP)
            f.waitFor{f.tag("updates:back")?.hasFocus()==true}
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            f.waitFor{f.activity.currentFocus?.tag==expected}
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            f.waitFor{f.tag("updates:edit")?.hasFocus()==true}
            repeat(4){press(KeyEvent.KEYCODE_DPAD_RIGHT)}
            f.waitFor{f.tag("updates:setup-skip")?.hasFocus()==true}
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            f.waitFor{UiPreferences(f.context).step==3}
            assertEquals(repositoryCount,store.repositoryCount)
        }finally{
            TvActivity.forceSetupForTests=false
            f.close()
        }
    }

    @Test fun defaultSearchVerifiesAndroidRepositoryAndCanBeCancelled()=runBlocking<Unit> {
        val f=Settings110Fixture();val result=JSONObject();val manager=DistributionManager.get(f.context)
        val packagesBefore=manager.store.snapshot().getJSONObject("packages").toString()
        val repositoriesBefore=manager.store.repositoryCount
        val expected="enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV-Eklentiler"
        val choices=listOf("search","search-user","address")
        fun center()=checkNotNull(f.activity.updateCenterForTest())
        fun press(code:Int)=f.ins.sendKeyDownUpSync(code)
        try {
            f.start(AppSettings(),UpdateTestData.empty())
            f.main{f.activity.findViewById<View>(R.id.tv_nav_about).performClick()}
            f.waitFor{f.tag("settings-category:updates")!=null}
            f.main{f.tag("settings-category:updates").requestFocus()}
            f.waitFor{f.activity.updateCenterForTest()?.keys()?.contains("add")==true}
            f.main{center().select("add")}
            f.waitFor{center().keys()==choices}
            f.main{center().select("search");assertEquals(View.VISIBLE,f.tag("updates:loading").visibility);assertNull(f.activity.settingsDialogForTest())}
            // Back cancels pending network work and cannot be overwritten by a late reply.
            press(KeyEvent.KEYCODE_BACK)
            f.waitFor{center().keys()==choices}
            delay(800)
            f.main{assertEquals(choices,center().keys());assertEquals(View.GONE,f.tag("updates:loading").visibility)}
            result.put("cancelledSearchKeepsParent",true)
            val started=SystemClock.elapsedRealtime()
            f.main{center().select("search")}
            f.waitFor(35000){center().keys().contains(expected)}
            result.put("liveSearchMs",SystemClock.elapsedRealtime()-started).put("verifiedResults",center().keys().filter{it.contains('/')})
            f.main{assertEquals(View.GONE,f.tag("updates:loading").visibility);assertFalse(center().keys().any{it.contains("Tizen")});center().requestKey(expected)}
            f.waitFor{f.tag("updates:$expected")?.hasFocus()==true}
            f.screenshot("repository-search-results.png")
            f.main{center().select("edit")}
            f.waitFor{f.activity.settingsDialogForTest()?.isShowing==true}
            f.main{val input=f.activity.settingsDialogForTest()!!.findViewById<View>(android.R.id.content).findViewWithTag<EditText>("updates:input");assertEquals("film-merkezi",input.text.toString())}
            press(KeyEvent.KEYCODE_BACK)
            f.main{center().select(expected)}
            f.waitFor{f.activity.settingsDialogForTest()?.isShowing==true}
            f.screenshot("repository-search-trust.png")
            press(KeyEvent.KEYCODE_BACK)
            f.waitFor{f.activity.settingsDialogForTest()?.isShowing!=true&&center().keys().contains(expected)}
            assertEquals(packagesBefore,manager.store.snapshot().getJSONObject("packages").toString())
            assertEquals(repositoriesBefore,manager.store.repositoryCount)
            result.put("trustRequiresConfirmation",true)
            f.main{f.tag("updates:back").performClick()}
            f.waitFor{center().keys()==choices}
            f.main{center().select("search")}
            f.waitFor(3000){center().keys().contains(expected)}
            result.put("cachedSearchWorks",true)
            f.main{center().select("refresh");f.tag("updates:back").performClick()}
            f.waitFor{center().keys()==choices}
            delay(300)
            f.main{assertEquals(choices,center().keys())}
            result.put("refreshCanBeCancelled",true).put("passed",true)
        }finally{File(f.context.getExternalFilesDir(null),"repository-search-ui.json").writeText(result.toString(2));f.close()}
    }
}
