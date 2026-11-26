package app.filmmerkezi.tv.updates

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DistributionSmokeTest {
    @Test fun githubRepositoryDownloadsAndStagesSignedDependencySet()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val manager=DistributionManager.get(context);val original=manager.store.snapshot();val client=PluginWorkerClient(context,manager)
        try{
            val probe=manager.probe("https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV-Eklentiler")
            assertEquals(32,probe.index.packages.size);assertEquals("d31d23cccc49043e3846a9b799e1cea9464f8cd29e04daa55dd5d05c1a96fc1f",probe.fingerprint)
            manager.trust(probe);val key=probe.fingerprint+"/"+probe.index.id;val installer=PackageInstaller(manager)
            manager.setRepositoryEnabled(key,false)
            try{installer.plan(key,"site-filmcibaba");fail()}catch(_:IllegalArgumentException){}
            manager.setRepositoryEnabled(key,true)
            val plan=installer.plan(key,"site-filmcibaba");assertEquals(2,plan.size)
            installer.install(plan);assertEquals(original.getJSONObject("packages").toString(),manager.store.snapshot().getJSONObject("packages").toString())
            installer.activate();ModuleRuntime.register(manager.store.snapshot())
            assertEquals(2,manager.store.snapshot().getJSONObject("packages").length())
            val site=plan.single{it.manifest.type=="site"}
            assertEquals(1,client.probe(site.identity,JSONObject().put("site",site.manifest.site)).getInt("sdk"))
            try{installer.remove(plan.single{it.manifest.type=="resolver"}.identity);fail()}catch(_:IllegalArgumentException){}
            installer.remove(site.identity);installer.remove(plan.single{it.manifest.type=="resolver"}.identity)
            assertEquals(0,manager.store.snapshot().getJSONObject("pending").length())
            installer.clearPending();installer.rollback();installer.activate()
            assertEquals(original.getJSONObject("packages").toString(),manager.store.snapshot().getJSONObject("packages").toString())
        }finally{client.stop();manager.store.commit{next->next.keys().asSequence().toList().forEach(next::remove);original.keys().forEach{next.put(it,original.get(it))}};ModuleRuntime.register(original)}
    }
    @Test fun emptyHomeOpensClassicNativeUpdatePanelAndBackReturnsFocus()=runBlocking{
        val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext
        val prefs=context.getSharedPreferences("interface-preferences",0);val before=prefs.all
        prefs.edit().putString("style","classic").putBoolean("configured",true).commit()
        val activity=ins.startActivitySync(Intent(context,app.filmmerkezi.tv.tv.TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun find(view:View):View?{if(view is TextView&&view.text.toString()=="Eklenti deposu ekle")return view;if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i))?.let{return it};return null}
        try{
            ins.waitForIdleSync();var origin:View?=null
            ins.runOnMainSync{origin=find(activity.window.decorView);assertNotNull(origin);origin!!.requestFocus();origin!!.performClick()}
            val deadline=SystemClock.elapsedRealtime()+5000
            while(SystemClock.elapsedRealtime()<deadline&&ins.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Güncelleme Merkezi").isNullOrEmpty())delay(100)
            assertTrue(ins.uiAutomation.rootInActiveWindow!!.findAccessibilityNodeInfosByText("Güncelleme Merkezi").isNotEmpty())
            ins.uiAutomation.takeScreenshot()?.let{bmp->java.io.File(context.getExternalFilesDir(null),"modular-update-center.png").outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()}
            ins.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);ins.waitForIdleSync()
            ins.runOnMainSync{assertTrue(origin!!.hasFocus())}
        }finally{ins.runOnMainSync{activity.finish()};val e=prefs.edit();for(k in listOf("style","configured"))when(val v=before[k]){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);else->e.remove(k)};e.commit()}
    }
}
