package app.filmmerkezi.tv.updates

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
            assertTrue(probe.index.packages.any{it.id=="p004"});assertTrue(probe.index.packages.any{it.id=="r004"});assertEquals("d31d23cccc49043e3846a9b799e1cea9464f8cd29e04daa55dd5d05c1a96fc1f",probe.fingerprint)
            manager.trust(probe);val key=probe.fingerprint+"/"+probe.index.id;val installer=PackageInstaller(manager)
            manager.setRepositoryEnabled(key,false)
            try{installer.plan(key,"p004");fail()}catch(_:IllegalArgumentException){}
            manager.setRepositoryEnabled(key,true)
            val plan=installer.plan(key,"p004");assertEquals(2,plan.size)
            installer.install(plan);assertEquals(original.getJSONObject("packages").toString(),manager.store.snapshot().getJSONObject("packages").toString())
            val expected=original.getJSONObject("packages").keys().asSequence().toSet()-plan.flatMap{it.replaces}.toSet()+plan.map{it.identity}
            installer.activate();ModuleRuntime.register(manager.store.snapshot())
            assertEquals(expected,manager.store.snapshot().getJSONObject("packages").keys().asSequence().toSet())
            val site=plan.single{it.manifest.type=="site"}
            assertEquals(1,client.probe(site.identity,JSONObject().put("site",site.manifest.site)).getInt("sdk"))
            try{installer.remove(plan.single{it.manifest.type=="resolver"}.identity);fail()}catch(_:IllegalArgumentException){}
            installer.remove(site.identity);installer.remove(plan.single{it.manifest.type=="resolver"}.identity)
            assertEquals(expected-plan.map{it.identity}.toSet(),manager.store.snapshot().getJSONObject("pending").keys().asSequence().toSet())
            installer.clearPending();installer.rollback();installer.activate()
            assertEquals(original.getJSONObject("packages").toString(),manager.store.snapshot().getJSONObject("packages").toString())
        }finally{client.stop();manager.store.commit{next->next.keys().asSequence().toList().forEach(next::remove);original.keys().forEach{next.put(it,original.get(it))}};ModuleRuntime.register(original)}
    }
}
