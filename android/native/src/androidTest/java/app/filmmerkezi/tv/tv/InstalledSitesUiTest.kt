package app.filmmerkezi.tv.tv

import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.updates.*
import android.view.View
import android.view.KeyEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class InstalledSitesUiTest {
    @Test fun classicPickersOnlyShowInstalledPluginsAndKeepBackFocus()=runBlocking {PilotWorkerTest().withSet {
        val f=Settings110Fixture();val report=JSONObject();val ghost=SiteSpec("retired-ui","Kurulu olmayan sağlayıcı","https://retired-ui.example","/","/","a","h2")
        val manager=DistributionManager.get(f.context)
        manager.store.commit{state->state.put("knownSites",(state.optJSONArray("knownSites")?:JSONArray()).put(PluginWire.site(ghost)))}
        ModuleRuntime.register(manager.store.snapshot())
        val expected=Sites.all.map{it.name};val expectedIds=Sites.all.map{it.id};assertEquals(4,expected.size)
        val fake=object:TvData {
            override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(emptyList(),emptyMap(),emptyList()))
            override fun search(query:String,cursors:Map<String,PageCursor>)=browse(ContentKind.MOVIE,cursors)
            override suspend fun detail(card:Card):Detail=error("No detail expected")
            override suspend fun episodes(card:Card)=emptyList<Episode>()
            override suspend fun resolve(option:SourceOption):ResolvedSource=error("No media expected")
            override suspend fun records()=emptyList<LibraryRecord>()
            override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){}
            override suspend fun remember(query:String){}
            override suspend fun searches()=emptyList<String>()
        }
        try {
            report.put("stage","picker")
            f.start(f.repo.value.copy(siteOrder=listOf(ghost.id)+Sites.all.map{it.id}),fake)
            f.main{f.activity.findViewById<View>(R.id.tv_site_picker).performClick()}
            f.waitFor{f.activity.classicOverlayForTest()?.showing==true}
            f.main {val window=f.activity.classicOverlayForTest()!!;assertEquals(5,window.rowKeys().size);assertEquals(expected,window.titles().drop(1));assertFalse(window.titles().contains(ghost.name))}
            f.screenshot("installed-sites-picker.png")
            f.ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);f.waitFor{f.activity.findViewById<View>(R.id.tv_site_picker).hasFocus()}
            f.main{f.activity.settingsForTest();f.tag("settings-category:catalog").performClick()}
            report.put("stage","management")
            f.waitFor{f.tag("setting:site-management")!=null}
            f.main{f.tag("setting:site-management").requestFocus();f.tag("setting:site-management").performClick()}
            f.waitFor{f.activity.settingsDialogForTest()?.choiceCount==4}
            f.screenshot("installed-sites-management.png")
            f.ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);f.waitFor{f.tag("setting:site-management").hasFocus()}
            Sites.installed(emptyList());Sites.remember(listOf(ghost))
            report.put("stage","empty")
            f.waitFor{expectedIds.none{f.tag("setting:site:$it")!=null}}
            f.main{f.tag("setting:site-management").requestFocus();f.tag("setting:site-management").performClick()}
            f.waitFor{f.activity.settingsDialogForTest()?.isShowing==true}
            f.main{assertEquals(0,f.activity.settingsDialogForTest()!!.choiceCount)}
            f.screenshot("installed-sites-empty.png")
            report.put("passed",true).put("sitePickerEntries",5).put("managedSites",4).put("emptySites",0).put("backFocus",true)
        } catch(e:Throwable){report.put("passed",false).put("error",e.toString());throw e}
        finally{f.close();File(f.context.getExternalFilesDir(null),"installed-sites-ui.json").writeText(report.toString(2))}
    }}
}
