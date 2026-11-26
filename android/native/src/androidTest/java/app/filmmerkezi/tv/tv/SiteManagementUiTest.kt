package app.filmmerkezi.tv.tv

import android.content.DialogInterface
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SiteManagementUiTest {
    @Test fun classicEditorValidatesChecksSavesCancelsAndResets()=runBlocking {
        val f=Settings110Fixture();val report=JSONObject().put("fixture",true);val calls=mutableListOf<WebRequest>();var href="/dizi/test/"
        fun dialog()=checkNotNull(f.activity.settingsDialogForTest())
        fun view(key:String)=dialog().window!!.decorView.findViewWithTag<View>(key)
        fun edit(key:String,value:String){f.main{(view(key)as EditText).setText(value)}}
        fun apply(){f.main{dialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()}}
        suspend fun editor(){f.main{dialog().choose(f.repo.value.siteOrder.indexOf("diziboxum"))};f.waitFor{view("site-manager-address")!=null}}
        try{
            f.start()
            TvActivity.siteCheckTransportForTests=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
                calls.add(request);return WebResponse(request.url,"<main><a class='r-card' href='$href'><span class='r-cap-t'>Deneme dizisi</span><span class='r-cap-s'>2026</span></a></main>")
            }}
            f.main{f.activity.settingsForTest();f.tag("settings-category:catalog").performClick()}
            f.waitFor{f.tag("setting:site-management")!=null}
            f.main{f.tag("setting:site-management").performClick()};f.waitFor{dialog().choiceCount==Sites.all.size};editor()
            val original=f.repo.value;val base=Sites.default("diziboxum").base
            edit("site-manager-address","http://bad.example");apply()
            f.main{assertTrue(dialog().isShowing);assertEquals(original,f.repo.value)}
            edit("site-manager-address","https://series-updated.example/");edit("site-manager-name","Diziboxum Deneme")
            f.main{view("site-manager-test").performClick()}
            f.waitFor{(view("site-manager-status")as? TextView)?.text?.contains("1 içerik ayrıştırıldı")==true}
            assertEquals(1,calls.size);assertEquals("https://series-updated.example/diziler/",calls.single().url);assertEquals("https://series-updated.example/",calls.single().referer)
            assertEquals(original,f.repo.value) // A successful probe does not save.
            f.screenshot("site-management-editor.png");apply()
            f.waitFor{Sites.get("diziboxum").base=="https://series-updated.example"&&dialog().choiceCount==Sites.all.size}
            assertEquals("Diziboxum Deneme",Sites.get("diziboxum").name);assertEquals(original.disabledSources,f.repo.value.disabledSources)
            editor();edit("site-manager-address","https://cancelled.example")
            href="https://series-updated.example/dizi/test/";f.main{view("site-manager-test").performClick()}
            f.waitFor{(view("site-manager-status")as? TextView)?.text?.contains("1 içerik ayrıştırıldı")==true}
            f.main{dialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()};f.waitFor{dialog().choiceCount==Sites.all.size}
            assertEquals("https://series-updated.example",Sites.get("diziboxum").base)
            editor();f.main{view("site-manager-reset").performClick()};assertEquals("https://series-updated.example",Sites.get("diziboxum").base)
            apply();f.waitFor{Sites.get("diziboxum").base==base&&dialog().choiceCount==Sites.all.size}
            assertEquals(base+"/dizi/test/",Sites.get("diziboxum").catalogUrl("https://series-updated.example/dizi/test/"))
            f.ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);f.waitFor{f.tag("setting:site-management")?.hasFocus()==true}
            report.put("status","PASS").put("probeRequests",calls.size).put("savedAndCancelled",true).put("restoredDefault",true).put("backFocus",true)
        }catch(e:Throwable){report.put("status","FAIL").put("error",e.toString());throw e}
        finally{TvActivity.siteCheckTransportForTests=null;File(f.context.getExternalFilesDir(null),"site-management-ui.json").writeText(report.toString(2));f.close()}
        Unit
    }
}
