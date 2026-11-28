package app.filmmerkezi.tv.tv

import android.content.DialogInterface
import android.view.*
import android.widget.*
import app.filmmerkezi.tv.updates.DistributionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdateDiagnosticsUiTest {
    private fun texts(v:View):String=if(v is ViewGroup)(0 until v.childCount).joinToString("\n"){texts(v.getChildAt(it))}else if(v is TextView)v.text.toString()else""
    @Test fun ordinaryErrorGivesActionAndTechnicalDetailsStayBehindDetailsButton()=runBlocking {
        val f=Settings110Fixture();val manager=DistributionManager.get(f.context);val oldIssue=manager.lastIssue
        try {
            f.start(customData=UpdateTestData.empty());f.main{f.activity.settingsForTest();f.tag("settings-category:updates").requestFocus()}
            f.waitFor{f.activity.updateCenterForTest()?.keys()?.contains("add")==true}
            val center=checkNotNull(f.activity.updateCenterForTest())
            f.main{center.select("add");center.select("address")}
            f.waitFor{f.activity.settingsDialogForTest()?.isShowing==true}
            f.main{val d=f.activity.settingsDialogForTest()!!;d.window!!.decorView.findViewWithTag<EditText>("updates:input").setText("PRIVATE_INTERNAL_VALUE_WITHOUT_SCHEME");d.getButton(DialogInterface.BUTTON_POSITIVE).performClick()}
            f.waitFor{f.activity.settingsDialogForTest()?.isShowing==true&&texts(f.activity.settingsDialogForTest()!!.window!!.decorView).contains("Depo adresini kontrol")}
            f.main{assertFalse(texts(f.activity.settingsDialogForTest()!!.window!!.decorView).contains("PRIVATE_INTERNAL"))}
            f.screenshot("updates-friendly-error.png")
            f.main{f.activity.settingsDialogForTest()!!.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()}
            f.waitFor{texts(f.activity.settingsDialogForTest()!!.window!!.decorView).contains("Expected URL scheme")}
            File(f.context.getExternalFilesDir(null),"updates-diagnostics.txt").writeText("PASS: invalid address rejected before network; action shown; raw cause only in details")
        }finally{manager.recordIssue(IllegalStateException(oldIssue));f.close()}
    }
}
