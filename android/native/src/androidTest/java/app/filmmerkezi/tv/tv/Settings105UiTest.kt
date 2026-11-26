package app.filmmerkezi.tv.tv

import android.content.Intent
import android.os.SystemClock
import android.view.*
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.settings.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Settings105UiTest {
    @Test fun classicCategoriesDpadAndDurableToggles()=runBlocking {
        val ins=InstrumentationRegistry.getInstrumentation();val c=ins.targetContext;val repo=SettingsRepository.get(c);val original=repo.value
        val ui=c.getSharedPreferences("interface-preferences",0);val oldUi=ui.all.toMap();var a:TvActivity?=null
        fun main(block:()->Unit)=ins.runOnMainSync(block)
        fun waitFor(check:()->Boolean){val end=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<end){var ok=false;main{ok=check()};if(ok)return;SystemClock.sleep(50)};error("Settings UI timeout")}
        fun tag(k:String)=a!!.window.decorView.findViewWithTag<View>(k)
        try{
            ins.uiAutomation.executeShellCommand("input keyevent 224").close()
            ins.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
            repo.update{AppSettings()};ui.edit().putString("style","classic").putBoolean("configured",true).commit()
            TvActivity.dataFactoryForTests={object:TvData{
                override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(emptyList(),emptyMap(),emptyList()))
                override fun search(query:String,cursors:Map<String,PageCursor>)=browse(ContentKind.MOVIE,cursors)
                override suspend fun detail(card:Card)=Detail(card);override suspend fun episodes(card:Card)=emptyList<Episode>()
                override suspend fun resolve(option:SourceOption):ResolvedSource=error("No media in settings UI test")
                override suspend fun records()=emptyList<LibraryRecord>();override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){}
                override suspend fun remember(query:String){};override suspend fun searches()=emptyList<String>()
            }}
            ins.setInTouchMode(false);a=ins.startActivitySync(Intent(c,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
            main{a!!.settingsForTest()};waitFor{tag("settings-category:general")?.hasFocus()==true}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);waitFor{tag("setting:start")?.hasFocus()==true}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);waitFor{tag("settings-category:general")?.hasFocus()==true}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitFor{tag("settings-category:catalog")?.hasFocus()==true}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);waitFor{tag("setting:merge")?.hasFocus()==true}
            main{tag("setting:merge").performClick()};waitFor{!repo.value.merge}
            assertTrue(tag("setting:merge").hasFocus());assertFalse(SettingsRepository(c).value.merge)
            main{tag("setting:adult").performClick()};waitFor{!repo.value.hideAdult}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            ins.waitForIdleSync();SystemClock.sleep(500);ins.waitForIdleSync()
            ins.uiAutomation.takeScreenshot()?.let{bmp->File(c.getExternalFilesDir(null),"settings105-catalog.png").outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()}
            main{a!!.onBackPressed()};waitFor{tag("settings-category:catalog")==null}
        }finally{a?.let{main{it.finish()}};TvActivity.dataFactoryForTests=null;repo.update{original}
            val e=ui.edit().clear();oldUi.forEach{(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v)}};e.commit()
        }
    }
}
