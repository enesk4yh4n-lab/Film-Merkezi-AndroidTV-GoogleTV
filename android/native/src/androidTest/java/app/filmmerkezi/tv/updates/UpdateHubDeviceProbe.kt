package app.filmmerkezi.tv.updates

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

/** Explicit emulator-only setup for testing the real restart outside instrumentation. */
internal object UpdateHubDeviceProbe {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val restoreFile get()=File(context.cacheDir,"update-hub-test-restore.json")
    fun prepare()=runBlocking {
        val manager=DistributionManager.get(context);val original=manager.store.snapshot();val set=original.getJSONObject("packages")
        check(!restoreFile.exists()){"An unfinished update-hub test must be restored first"}
        check(!original.has("pending")&&!manager.store.restartRequired){"Finish the current installation first"}
        val identity=set.keys().asSequence().single{it.endsWith("/p005")}
        val plan=PluginRemovals.plan(set,identity);val next=PluginRemovals.apply(set,plan,true)
        PackageSets.validate(original,next,manager.installedCode,android.os.Build.VERSION.SDK_INT)
        restoreFile.writeText(JSONObject().put("distribution",original).put("notices",manager.notices.value.encode()).toString())
        ModuleRuntime.stop();manager.notices.update{NoticeState()}
        manager.store.commit{it.put("packages",next).put("bootPending",false);it.remove("needsBoot")}
        check(manager.catalog().fresh.any{it.entry.id=="p005"})
    }
    fun restore()=runBlocking {
        if(!restoreFile.exists())return@runBlocking
        val saved=JSONObject(restoreFile.readText());val original=saved.getJSONObject("distribution");val manager=DistributionManager.get(context)
        ModuleRuntime.stop();manager.store.commit{current->current.keys().asSequence().toList().forEach(current::remove);original.keys().forEach{current.put(it,original.get(it))}}
        manager.notices.update{NoticeState()};manager.notices.update{NoticeState.decode(saved.getString("notices"))}
        ModuleRuntime.register(original);check(restoreFile.delete())
    }
}
