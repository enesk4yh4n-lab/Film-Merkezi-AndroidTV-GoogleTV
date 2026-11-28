package app.filmmerkezi.tv.tv

import android.view.KeyEvent
import kotlinx.coroutines.*
import org.junit.Test
import java.io.File

class UpdateDpadUiTest {
    @Test fun changingLongRowsKeepsFocusAndFooterAndCategoryRemainReachable()=runBlocking {
        val f=Settings110Fixture();var stage="start"
        suspend fun wait(key:String){try{f.waitFor(5000){f.tag("updates:$key")?.hasFocus()==true}}catch(e:Exception){throw IllegalStateException("Dpad stage=$stage wanted=$key",e)}}
        fun press(key:Int)=f.ins.sendKeyDownUpSync(key)
        try{
            f.start(customData=UpdateTestData.empty());f.main{f.activity.settingsForTest();f.tag("settings-category:updates").requestFocus()}
            f.waitFor{f.activity.updateCenterForTest()?.keys()?.contains("add")==true}
            val center=checkNotNull(f.activity.updateCenterForTest());val rows=(0 until 100).map{ClassicRow("long:$it","Deneme $it")};val actions=listOf(ClassicAction("test-action","Yeniden dene"){})
            f.main{center.select("add")};f.waitFor{center.keys().isNotEmpty()}
            f.main{center.update(rows,actions);center.requestKey("long:50")};stage="initial50";wait("long:50")
            f.main{center.update(rows.map{it.copy(description="Güncellenen durum")},actions)};stage="updated50";delay(200);wait("long:50")
            press(KeyEvent.KEYCODE_DPAD_LEFT);f.waitFor(5000){f.tag("settings-category:updates").hasFocus()}
            press(KeyEvent.KEYCODE_DPAD_RIGHT);stage="category50";wait("long:50")
            f.main{center.requestKey("long:99")};stage="last99";wait("long:99")
            press(KeyEvent.KEYCODE_DPAD_DOWN);stage="footer";wait("test-action")
            press(KeyEvent.KEYCODE_DPAD_UP);stage="footerUp99";wait("long:99")
            f.main{center.update(emptyList(),actions)};stage="emptyFooter";wait("test-action")
            press(KeyEvent.KEYCODE_BACK);stage="parentAdd";wait("add")
            File(f.context.getExternalFilesDir(null),"updates-long-dpad.txt").writeText("PASS: 100 rows, resized rows, category resume, footer, empty list, parent focus")
        }finally{f.close()}
    }
}
