package app.filmmerkezi.tv.updates

import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real signed DEX, real Binder pipes and a separate Android process; no live site dependency. */
class PluginWorkerTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val manager=DistributionManager.get(context)
    private fun asset(name:String)=instrumentation.context.assets.open("distribution-test/$name").use{it.readBytes()}
    internal suspend fun withSet(block:suspend(PluginWorkerClient)->Unit){
        ModuleRuntime.stop()
        val original=manager.store.snapshot()
        val raw=asset(UpdateProtocol.INDEX);val index=UpdateProtocol.index(raw);val signature=asset("filmmerkezi-android-repository.sig").toString(Charsets.US_ASCII)
        UpdateProtocol.verify(raw,signature,index.publicKey)
        val fp=UpdateProtocol.fingerprint(index.publicKey);val repoKey="$fp/${index.id}"
        val repository=JSONObject().put("key",repoKey).put("id",index.id).put("fingerprint",fp).put("publicKey",index.publicKey).put("name",index.name).put("enabled",true).put("url","https://example.com/${UpdateProtocol.INDEX}").put("revision",index.revision).put("indexHash",UpdateProtocol.sha(raw)).put("index",raw.toString(Charsets.UTF_8)).put("signature",signature)
        val packages=JSONObject()
        for(entry in index.packages){
            val manifest=asset(entry.manifest);val m=UpdateProtocol.module(manifest)
            val file=File(manager.store.directory,"objects/${m.hash}.jar");file.parentFile!!.mkdirs()
            if(!file.exists()){file.outputStream().use{output->assertTrue(file.setReadOnly());output.write(asset("plugin.jar"))}}
            assertEquals(m.hash,UpdateProtocol.sha(file.readBytes()))
            packages.put("$fp/${m.id}",JSONObject().put("repo",repoKey).put("manifest",manifest.toString(Charsets.UTF_8)).put("signature",asset(entry.manifest.removeSuffix(".json")+".sig").toString(Charsets.US_ASCII)).put("artifact",m.hash).put("enabled",true))
        }
        manager.store.commit{it.put("repositories",JSONObject().put(repoKey,repository)).put("packages",packages).put("bootPending",false)}
        ModuleRuntime.register(manager.store.snapshot())
        val client=PluginWorkerClient(context,manager)
        try{block(client)}finally{client.stop();manager.store.commit{next->next.keys().asSequence().toList().forEach(next::remove);original.keys().forEach{next.put(it,original.get(it))}};ModuleRuntime.register(original)}
    }
    @Test fun allThirtyTwoSignedEntriesStartInWorker()=runBlocking{withSet{client->
        val all=manager.store.snapshot().getJSONObject("packages")
        assertEquals(32,all.length())
        for(key in all.keys()){
            val m=UpdateProtocol.module(all.getJSONObject(key).getString("manifest").toByteArray())
            val site=m.site?:PluginWire.site(Sites.all.first())
            assertEquals(key,1,client.probe(key,JSONObject().put("site",site)).getInt("sdk"))
        }
    }}
    @Test fun parserRunsAcrossPipeAndMatchesJvmSchema()=runBlocking{withSet{client->
        val site=Sites.get("filmcibaba")!!
        val html="<html><body><article><a class='v100-card' href='https://filmizle.ink/worker-film/' title='Worker Film'><img src='https://filmizle.ink/poster.jpg'/><h2 class='v100-title'>Worker Film</h2></a></article></body></html>"
        val payload=JSONObject().put("site",PluginWire.site(site)).put("url",site.base+"/").put("body",html)
        val actual=client.call(site.id,"site","parseList",payload)
        assertEquals(1,actual.getJSONArray("cards").length());assertTrue(actual.has("next"))
        assertTrue("A helper process must exist",android.app.ActivityManager::class.java.cast(context.getSystemService(android.content.Context.ACTIVITY_SERVICE)).runningAppProcesses.any{it.processName==context.packageName+":plugins"&&it.pid!=android.os.Process.myPid()})
    }}
    @Test fun independentPublisherTypedErrorAndUncooperativeCancellation()=runBlocking{withSet{client->
        client.stop();delay(200)
        fun read(path:String)=instrumentation.context.assets.open("independent-test/$path").use{it.readBytes()}
        val raw=read(UpdateProtocol.INDEX);val index=UpdateProtocol.index(raw);val signature=read("filmmerkezi-android-repository.sig").toString(Charsets.US_ASCII)
        val probe=RepositoryProbe("https://example.com/${UpdateProtocol.INDEX}",raw,signature,index)
        manager.trust(probe)
        val e=index.packages.single();val manifest=read(e.manifest);val m=UpdateProtocol.module(manifest);val key=probe.fingerprint+"/"+m.id
        val file=File(manager.store.directory,"objects/${m.hash}.jar")
        if(!file.exists())file.outputStream().use{out->assertTrue(file.setReadOnly());out.write(read(m.artifact))}
        manager.store.commit{it.getJSONObject("packages").put(key,JSONObject().put("repo",probe.fingerprint+"/"+index.id).put("manifest",manifest.toString(Charsets.UTF_8)).put("signature",read(e.manifest.removeSuffix(".json")+".sig").toString(Charsets.US_ASCII)).put("artifact",m.hash))}
        ModuleRuntime.register(manager.store.snapshot())
        assertTrue(client.probe(key,JSONObject().put("site",m.site)).getBoolean("independent"))
        try{client.probe(key,JSONObject().put("site",m.site).put("typedFailure",true));fail()}catch(e:DataFailure){assertEquals(FailureKind.ACCESS,e.kind);assertEquals(403,e.httpCode);assertEquals("publisher",e.stage)}
        val start=android.os.SystemClock.elapsedRealtime()
        try{withTimeout(1200){client.probe(key,JSONObject().put("site",m.site).put("spin",true))};fail()}catch(_:Exception){}
        assertTrue("CPU cancellation must terminate only the helper process",android.os.SystemClock.elapsedRealtime()-start<6000)
        delay(250)
        assertTrue(client.probe(key,JSONObject().put("site",m.site)).getBoolean("independent"))
        assertTrue(android.os.Process.myPid()>0)
        // Exercise the actual 10-second native watchdog, without a caller timeout.
        try{client.probe(key,JSONObject().put("site",m.site).put("spin",true));fail()}catch(e:DataFailure){assertEquals(FailureKind.TIMEOUT,e.kind);assertEquals("plugin",e.stage)}
        assertTrue(client.probe(key,JSONObject().put("site",m.site)).getBoolean("independent"))
    }}
    @Test fun setValidationRejectsMissingDependencyAndRecoverRestoresWholeSet()=runBlocking{withSet{_->
        val before=manager.store.snapshot();val original=before.getJSONObject("packages")
        val broken=JSONObject(original.toString());val resolver=broken.keys().asSequence().first{it.substringAfter('/').startsWith("resolver-")};broken.remove(resolver)
        try{PackageSets.validate(before,broken,36,android.os.Build.VERSION.SDK_INT);fail()}catch(_:IllegalArgumentException){}
        val installer=PackageInstaller(manager)
        manager.store.commit{it.put("pending",JSONObject())};installer.activate()
        assertEquals(0,manager.store.snapshot().getJSONObject("packages").length())
        manager.store.commit{it.put("bootPending",true)};installer.recover()
        assertEquals(original.toString(),manager.store.snapshot().getJSONObject("packages").toString())
        assertFalse(manager.store.snapshot().optBoolean("bootPending"))
        assertTrue(manager.store.snapshot().getJSONArray("knownSites").length()>=16)
    }}
}
