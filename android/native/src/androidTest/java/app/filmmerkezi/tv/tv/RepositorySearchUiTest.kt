package app.filmmerkezi.tv.tv

import android.content.DialogInterface
import android.os.SystemClock
import android.view.*
import android.widget.EditText
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.settings.AppSettings
import app.filmmerkezi.tv.updates.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Explicit live probe of the real native Options flow; no site or playback traffic. */
class RepositorySearchUiTest {
    @Test fun defaultSearchVerifiesAndroidRepositoryAndCanBeCancelled()=runBlocking {
        val f=Settings110Fixture();val result=JSONObject();val manager=DistributionManager.get(f.context)
        val packagesBefore=manager.store.snapshot().getJSONObject("packages").toString()
        val expected="enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV-Eklentiler"
        fun center()=checkNotNull(f.activity.updateCenterForTest())
        fun press(code:Int)=f.ins.sendKeyDownUpSync(code)
        try {
            f.start(AppSettings(),UpdateTestData.empty())
            f.main{f.activity.findViewById<View>(R.id.tv_nav_about).performClick()}
            f.waitFor{f.tag("settings-category:updates")!=null}
            f.main{f.tag("settings-category:updates").requestFocus()}
            f.waitFor{f.activity.updateCenterForTest()?.keys()?.contains("add")==true}
            f.main{center().select("add");center().select("search");assertEquals(View.VISIBLE,f.tag("updates:loading").visibility);assertNull(f.activity.settingsDialogForTest())}
            // Back cancels pending network work and cannot be overwritten by a late reply.
            press(KeyEvent.KEYCODE_BACK)
            f.waitFor{center().keys()==listOf("search","search-user","address")}
            delay(800)
            f.main{assertEquals(listOf("search","search-user","address"),center().keys());assertEquals(View.GONE,f.tag("updates:loading").visibility)}
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
            f.main{f.activity.settingsDialogForTest()!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()}
            f.waitFor{manager.repositories().isNotEmpty()&&center().keys().contains(manager.repositories().single{it.getString("name").contains("Android")}.getString("key"))}
            val repo=manager.repositories().single{it.getString("url").contains(expected)}
            result.put("repositoryTrusted",true).put("revision",repo.getInt("revision"))
            f.main{center().select(repo.getString("key"))}
            f.waitFor{center().keys().contains("package:p002")}
            f.main{assertTrue(center().keys().containsAll(listOf("package:p002","package:p003","package:p004","package:p005")));assertFalse(center().keys().any{it.startsWith("package:r")})}
            assertEquals(packagesBefore,manager.store.snapshot().getJSONObject("packages").toString())
            f.screenshot("repository-search-packages.png")
            result.put("sitesVisible",4).put("resolversListedAsSites",false).put("searchDoesNotInstallPackages",true)
            val revision=manager.store.revision.value
            f.main{center().select("refresh")}
            f.waitFor{manager.store.revision.value>revision&&manager.status.value.repositoryChecks[repo.getString("key")]?.state==CheckState.CURRENT}
            result.put("manualRefreshUpdatesStatus",true)
            press(KeyEvent.KEYCODE_BACK);press(KeyEvent.KEYCODE_BACK)
            f.waitFor{center().keys().contains("add")}
            f.main{center().select("add");center().select("search")}
            f.waitFor(3000){center().keys().contains(expected)}
            result.put("cachedSearchWorks",true)
            f.main{center().select("refresh")};press(KeyEvent.KEYCODE_BACK)
            f.waitFor{center().keys()==listOf("search","search-user","address")}
            delay(300)
            f.main{assertEquals(listOf("search","search-user","address"),center().keys())}
            result.put("refreshCanBeCancelled",true).put("passed",true)
        }finally{File(f.context.getExternalFilesDir(null),"repository-search-ui.json").writeText(result.toString(2));f.close()}
    }
}
