package app.filmmerkezi.tv.tv

import android.view.*
import android.content.DialogInterface
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Read-only update/transfer navigation. Does not search, publish, install, delete or export. */
class RemoteUpdateFlowTest {
    @Test fun updateSubpagesFormsAndTransferRestoreFocus()=runBlocking {
        val f=Settings110Fixture();val results=JSONArray()
        fun key(k:Int){f.ins.sendKeyDownUpSync(k);f.ins.waitForIdleSync()}
        suspend fun target(k:String){f.main{f.activity.updateCenterForTest()!!.requestKey(k)};f.waitFor(5000){f.tag("updates:$k")?.hasFocus()==true}}
        suspend fun check(name:String,block:suspend()->Unit){try{block();results.put(JSONObject().put("case",name).put("passed",true))}catch(e:Throwable){results.put(JSONObject().put("case",name).put("passed",false).put("error",e.toString()))}}
        try {
            f.start(customData=UpdateTestData.empty());f.main{f.activity.settingsForTest();f.tag("settings-category:updates").requestFocus()};f.waitFor{f.activity.updateCenterForTest()?.keys()?.contains("add")==true};key(KeyEvent.KEYCODE_DPAD_RIGHT)
            check("application_without_enabled_actions_keeps_back_reachable") {
                target("application");key(KeyEvent.KEYCODE_DPAD_CENTER);delay(200);f.waitFor{f.tag("updates:back")?.hasFocus()==true}
                key(KeyEvent.KEYCODE_DPAD_RIGHT);f.main{assertEquals("updates:back",f.activity.currentFocus?.tag)};key(KeyEvent.KEYCODE_DPAD_DOWN);f.main{assertEquals("updates:back",f.activity.currentFocus?.tag)}
                key(KeyEvent.KEYCODE_BACK);f.waitFor{f.tag("updates:application")?.hasFocus()==true}
            }
            check("installed_list_and_parent_focus") {
                target("installed");key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor(5000){f.activity.updateCenterForTest()?.keys()?.let{it.isNotEmpty()&&"check" !in it}==true}
                key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_UP);key(KeyEvent.KEYCODE_BACK);f.waitFor{f.tag("updates:installed")?.hasFocus()==true}
            }
            check("repository_address_edit_form_direction_and_cancel") {
                target("add");key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.tag("updates:address")!=null};target("address");key(KeyEvent.KEYCODE_DPAD_CENTER)
                f.waitFor{f.activity.settingsDialogForTest()?.isShowing==true};val d=f.activity.settingsDialogForTest()!!;val input=d.window!!.decorView.findViewWithTag<View>("updates:input")
                f.main{input.requestFocus()};key(KeyEvent.KEYCODE_DPAD_DOWN);f.main{assertSame(d.getButton(DialogInterface.BUTTON_POSITIVE),d.currentFocus)};key(KeyEvent.KEYCODE_DPAD_RIGHT);f.main{assertSame(d.getButton(DialogInterface.BUTTON_NEGATIVE),d.currentFocus)}
                key(KeyEvent.KEYCODE_DPAD_UP);f.main{assertSame(input,d.currentFocus)};key(KeyEvent.KEYCODE_BACK);f.waitFor{f.tag("updates:address")?.hasFocus()==true};key(KeyEvent.KEYCODE_BACK);f.waitFor{f.tag("updates:add")?.hasFocus()==true}
            }
            check("technical_confirmation_cancel_does_not_clean") {
                target("technical");key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.tag("updates:cleanup")!=null};target("cleanup");key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.activity.settingsDialogForTest()?.isShowing==true}
                val d=f.activity.settingsDialogForTest()!!;f.main{d.getButton(DialogInterface.BUTTON_NEGATIVE).requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.tag("updates:cleanup")?.hasFocus()==true};key(KeyEvent.KEYCODE_BACK)
            }
            key(KeyEvent.KEYCODE_BACK);delay(100);f.main{f.activity.settingsForTest();f.tag("settings-category:about").requestFocus()}
            f.waitFor(5000){f.activity.hasWindowFocus()&&f.tag("settings-category:about").hasFocus()};key(KeyEvent.KEYCODE_DPAD_RIGHT)
            check("transfer_panel_arrows_back_restores_setting") {
                var stage="enter-options"
                try{
                    f.waitFor(5000){f.tag("setting:transfer")?.hasFocus()==true};stage="open-panel";key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor(5000){f.activity.transferOverlayForTest()?.focusedKey()=="export"}
                    stage="rows";key(KeyEvent.KEYCODE_DPAD_DOWN);assertEquals("import",f.activity.transferOverlayForTest()?.focusedKey());key(KeyEvent.KEYCODE_DPAD_DOWN);assertEquals("local",f.activity.transferOverlayForTest()?.focusedKey());key(KeyEvent.KEYCODE_DPAD_DOWN);assertEquals("local",f.activity.transferOverlayForTest()?.focusedKey());stage="return-origin";key(KeyEvent.KEYCODE_BACK);f.waitFor(5000){f.tag("setting:transfer")?.hasFocus()==true}
                }catch(e:Throwable){throw AssertionError("Transfer stage=$stage",e)}
            }
        }finally{File(f.context.getExternalFilesDir(null),"remote-update-flow.json").writeText(results.toString(2));f.close()}
        assertTrue(results.toString(2),(0 until results.length()).all{results.getJSONObject(it).getBoolean("passed")})
    }
}
