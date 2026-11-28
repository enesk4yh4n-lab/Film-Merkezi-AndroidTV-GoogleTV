package app.filmmerkezi.tv.tv

import android.content.DialogInterface
import android.view.View
import app.filmmerkezi.tv.updates.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdateRemovalUiTest {
    @Test fun removesOnlyApprovedModulesAndKeepsRollbackObjects()=runBlocking{PilotWorkerTest().withSet{
        val f=Settings110Fixture();val manager=DistributionManager.get(f.context);val installer=PackageInstaller(manager)
        val original=manager.store.snapshot().getJSONObject("packages").toString()
        try{
            f.start(customData=UpdateTestData.empty())
            f.main{f.activity.settingsForTest();f.tag("settings-category:updates").requestFocus()}
            f.waitFor{f.activity.updateCenterForTest()?.keys()?.contains("installed")==true}
            val center=checkNotNull(f.activity.updateCenterForTest())
            f.main{center.select("installed")};f.waitFor{center.keys().size==4}
            val key=center.keys().single{it.endsWith("/p002")}
            f.main{center.select(key);center.select("remove")}
            f.waitFor{f.activity.settingsDialogForTest()?.isShowing==true}
            f.screenshot("updates-remove-confirm.png")
            f.main{f.activity.settingsDialogForTest()!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()}
            f.waitFor{manager.store.snapshot().optJSONObject("pending")?.length()==6}
            val state=manager.store.snapshot();assertEquals(original,state.getJSONObject("packages").toString())
            assertFalse(state.getJSONObject("pending").has(key));assertFalse(state.getJSONObject("pending").has(key.substringBefore('/')+"/r002"))
            val objects=File(manager.store.directory,"objects")
            val active=PluginInventory.modules(state.getJSONObject("packages"));val artifacts=active.values.map{File(objects,it.module.hash+".jar")}
            val junk=File(objects,"download-${"f".repeat(64)}.jar");junk.writeBytes(ByteArray(27))
            withContext(Dispatchers.IO){installer.cleanup()};assertFalse(junk.exists());assertTrue(artifacts.all{it.exists()})
            File(f.context.getExternalFilesDir(null),"updates-removal-ui.json").writeText(JSONObject().put("approvedRemoval",true).put("pendingPackages",6).put("activeUnchanged",true).put("rollbackObjectsPreserved",true).put("unreferencedObjectRemoved",true).toString(2))
        }finally{f.close()}
    }}
}
