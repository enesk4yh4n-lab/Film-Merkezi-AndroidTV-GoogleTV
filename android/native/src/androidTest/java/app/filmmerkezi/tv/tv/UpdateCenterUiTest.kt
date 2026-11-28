package app.filmmerkezi.tv.tv

import android.view.KeyEvent
import android.view.View
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.updates.*
import app.filmmerkezi.tv.settings.AppSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdateCenterUiTest {
    @Test fun updatePagesStayInsideOptionsAndPreserveDpadFocus()=runBlocking {PilotWorkerTest().withSet{
        val f=Settings110Fixture();val results=JSONArray()
        val manager=DistributionManager.get(f.context)
        val field=DistributionManager::class.java.getDeclaredField("state").apply{isAccessible=true}
        @Suppress("UNCHECKED_CAST") val status=field.get(manager) as MutableStateFlow<DistributionStatus>
        val previous=status.value
        val large=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("largeText")=="true"
        val width=f.context.resources.displayMetrics.widthPixels
        fun center()=checkNotNull(f.activity.updateCenterForTest())
        suspend fun waitKey(key:String){f.waitFor{f.tag("updates:$key")?.hasFocus()==true}}
        fun press(code:Int)=f.ins.sendKeyDownUpSync(code)
        try {
            status.value=DistributionStatus()
            f.start(AppSettings(largeText=large),UpdateTestData.empty())
            f.main{val options=f.activity.findViewById<View>(R.id.tv_nav_about);options.requestFocus();options.performClick()}
            f.waitFor{f.tag("settings-category:updates")!=null}
            f.main{assertNull(f.activity.settingsDialogForTest());f.tag("settings-category:updates").requestFocus()}
            f.waitFor{f.activity.hasWindowFocus()&&f.activity.updateCenterForTest()?.keys()?.contains("add")==true}
            assertTrue(center().keys().containsAll(listOf("updates","installed","add","repositories","technical")))
            press(KeyEvent.KEYCODE_DPAD_RIGHT);waitKey("add")
            press(KeyEvent.KEYCODE_DPAD_DOWN);waitKey("installed")
            f.main{status.value=status.value.copy(message="Yerel durum güncellemesi")}
            delay(250);waitKey("installed")
            press(KeyEvent.KEYCODE_DPAD_LEFT);f.waitFor{f.tag("settings-category:updates").hasFocus()}
            f.main{status.value=status.value.copy(message="Yeni durum")};delay(200)
            f.main{assertTrue(f.tag("settings-category:updates").hasFocus())}
            press(KeyEvent.KEYCODE_DPAD_RIGHT);waitKey("installed")
            f.main{center().select("installed")};f.waitFor{center().keys().size==4}
            f.main{center().focusFirst()};f.waitFor{center().hasFocus()}
            press(KeyEvent.KEYCODE_BACK);waitKey("installed")
            f.main{center().select("repositories")};f.waitFor{center().keys().size==1}
            val repositoryKey=center().keys().single()
            f.main{center().select(repositoryKey)};f.waitFor{center().keys().contains("package:p002")}
            f.main{center().focusFirst()};f.waitFor{center().hasFocus()}
            press(KeyEvent.KEYCODE_BACK);waitKey(repositoryKey)
            press(KeyEvent.KEYCODE_BACK);waitKey("repositories")
            f.main{center().select("add")};f.waitFor{center().keys().count{it.startsWith("catalog:")}==4}
            f.main{center().focusFirst()};press(KeyEvent.KEYCODE_BACK);waitKey("add")
            f.main{center().select("technical")};f.waitFor{f.tag("updates:back")?.visibility==View.VISIBLE}
            press(KeyEvent.KEYCODE_BACK);waitKey("technical")
            f.screenshot("options-update-center-${width}-${if(large)"large"else"normal"}.png")
            f.main{f.tag("settings-category:about").requestFocus()};f.waitFor{f.tag("setting:about")!=null}
            f.main{assertNull(f.activity.updateCenterForTest());assertNull(f.tag("setting:updates"))}
            f.main{f.tag("settings-category:updates").requestFocus()};f.waitFor{f.activity.updateCenterForTest()!=null}
            f.main{center().focusFirst()};press(KeyEvent.KEYCODE_BACK)
            f.waitFor{f.activity.findViewById<View>(R.id.tv_nav_about).hasFocus()}
            results.put(JSONObject().put("width",width).put("largeText",large).put("homeRows",5).put("installedSites",4).put("backFocus",true).put("statusKeepsFocus",true))
            f.main{f.activity.settingsForTest();f.tag("settings-category:updates").requestFocus()}
            f.waitFor{f.activity.updateCenterForTest()!=null}
            manager.store.commit{it.put("packages",JSONObject()).put("repositories",JSONObject()).remove("pending");it.remove("previous")};ModuleRuntime.register(manager.store.snapshot())
            f.main{f.tag("settings-category:about").requestFocus();f.tag("settings-category:updates").requestFocus()}
            f.waitFor{f.activity.updateCenterForTest()!=null}
            f.main{center().select("installed")};f.waitFor{center().keys().isEmpty()}
            press(KeyEvent.KEYCODE_BACK);waitKey("installed")
            f.main{center().select("repositories")};f.waitFor{center().keys().isEmpty()}
            f.main{center().select("add")};f.waitFor{center().keys()==listOf("search","search-user","address")}
            press(KeyEvent.KEYCODE_BACK);waitKey("search");press(KeyEvent.KEYCODE_BACK);waitKey("add")
            results.getJSONObject(0).put("emptyStates",true)
            f.main{center().select("add")};f.waitFor{center().keys()==listOf("search","search-user","address")}
            val longRows=(0 until 100).map{ClassicRow("long:$it","Deneme $it")}
            val actions=listOf(ClassicAction("test-action","Yeniden dene"){})
            f.main{center().update(longRows,actions);center().requestKey("long:50")};waitKey("long:50")
            f.main{center().update(longRows.map{it.copy(description="Güncellenen durum")},actions)};delay(150);waitKey("long:50")
            press(KeyEvent.KEYCODE_DPAD_LEFT);f.waitFor{f.tag("settings-category:updates").hasFocus()}
            press(KeyEvent.KEYCODE_DPAD_RIGHT);waitKey("long:50")
            f.main{center().requestKey("long:99")};waitKey("long:99")
            press(KeyEvent.KEYCODE_DPAD_DOWN);waitKey("test-action")
            press(KeyEvent.KEYCODE_DPAD_UP);waitKey("long:99")
            f.main{center().update(emptyList(),actions)};waitKey("test-action")
            press(KeyEvent.KEYCODE_BACK);waitKey("add")
            results.getJSONObject(0).put("longListFocus",true).put("footerDpad",true).put("categoryResume",true)
        } finally {status.value=previous;File(f.context.getExternalFilesDir(null),"options-update-center-ui.json").writeText(results.toString(2));f.close()}
    }}
}
