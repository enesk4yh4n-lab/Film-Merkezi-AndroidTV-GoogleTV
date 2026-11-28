package app.filmmerkezi.tv.updates

import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.NativeMainActivity
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Runs published-format pilot DEX against the installed shrunk host, restoring its package set. */
class PilotWorkerTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val context=ins.targetContext
    private val manager=DistributionManager.get(context)
    private fun asset(name:String,root:String="pilot-test")=ins.context.assets.open("$root/$name").use{it.readBytes()}
    internal suspend fun withSet(root:String="pilot-test",folder:File?=null,block:suspend(PluginWorkerClient)->Unit) {
        fun staged(name:String)=folder?.resolve(name)?.readBytes()?:asset(name,root)
        ModuleRuntime.stop();val original=manager.store.snapshot()
        // GC tests may unlink package objects. Pin existing artifacts without copying user data.
        val objects=File(manager.store.directory,"objects").apply{mkdirs()}
        val pins=File(objects,".pilot-pins-"+java.util.UUID.randomUUID()).apply{mkdirs()}
        val hashes=listOf("packages","pending","previous").flatMap{name->original.optJSONObject(name)?.let{s->s.keys().asSequence().map{s.getJSONObject(it).getString("artifact")}.toList()}.orEmpty()}.distinct()
        fun pin(source:File,target:File){try{android.system.Os.link(source.path,target.path)}catch(e:android.system.ErrnoException){
            // Some Android SELinux profiles forbid hard links even within this app's own data.
            if(e.errno !in listOf(android.system.OsConstants.EACCES,android.system.OsConstants.EPERM))throw e
            source.inputStream().use{input->target.outputStream().use{out->check(target.setReadOnly());input.copyTo(out)}}
        }}
        hashes.forEach{hash->val file=File(objects,"$hash.jar");if(file.exists())pin(file,File(pins,"$hash.jar"))}
        val raw=staged(UpdateProtocol.INDEX);val index=UpdateProtocol.index(raw)
        val signature=staged(UpdateProtocol.INDEX.replace(".json",".sig")).toString(Charsets.US_ASCII)
        UpdateProtocol.verify(raw,signature,index.publicKey)
        val fp=UpdateProtocol.fingerprint(index.publicKey);val repoKey="$fp/${index.id}";val packages=JSONObject()
        val repository=JSONObject().put("key",repoKey).put("id",index.id).put("fingerprint",fp).put("publicKey",index.publicKey).put("name",index.name).put("enabled",true).put("url","https://example.com/${UpdateProtocol.INDEX}").put("revision",index.revision).put("indexHash",UpdateProtocol.sha(raw)).put("index",raw.toString(Charsets.UTF_8)).put("signature",signature)
        val currentEntries=index.packages.groupBy{it.id}.values.map{versions->
            versions.maxWithOrNull{a,b->UpdateProtocol.compare(a.version,b.version)}!!
        }
        for(entry in currentEntries.reversed()) {
            val manifest=staged(entry.manifest);assertEquals(entry.hash,UpdateProtocol.sha(manifest))
            val m=UpdateProtocol.module(manifest);val file=File(manager.store.directory,"objects/${m.hash}.jar");file.parentFile!!.mkdirs()
            if(!file.exists()) file.outputStream().use { out->assertTrue(file.setReadOnly());out.write(staged(m.artifact)) }
            assertEquals(m.hash,UpdateProtocol.sha(file.readBytes()))
            packages.put("$fp/${m.id}",JSONObject().put("repo",repoKey).put("manifest",manifest.toString(Charsets.UTF_8)).put("signature",staged(entry.manifest.replace(".json",".sig")).toString(Charsets.US_ASCII)).put("artifact",m.hash).put("enabled",true))
        }
        manager.store.commit{it.put("repositories",JSONObject().put(repoKey,repository)).put("packages",packages).put("bootPending",false).remove("pending")}
        val client=PluginWorkerClient(context,manager)
        try {PackageSets.validate(manager.store.snapshot(),packages,manager.installedCode,android.os.Build.VERSION.SDK_INT);ModuleRuntime.register(manager.store.snapshot());block(client)}
        finally {client.stop();pins.listFiles().orEmpty().forEach{saved->val target=File(objects,saved.name);if(!target.exists())pin(saved,target);saved.delete()};pins.delete()
            manager.store.commit{next->next.keys().asSequence().toList().forEach(next::remove);original.keys().forEach{next.put(it,original.get(it))}};ModuleRuntime.register(original)}
    }
    private fun payload(id:String):JSONObject = JSONObject().put("site",PluginWire.site(Sites.get(id)))
    @Test fun localSignedPackagesChooseSmallPosterInWorker()=runBlocking {
        val folder=File(context.getExternalFilesDir(null),"poster-repo-pilot")
        org.junit.Assume.assumeTrue(File(folder,UpdateProtocol.INDEX).isFile)
        withSet(folder=folder,block=::checkPosterSize)
    }
    fun productionPosterSetStartsInTheWorker()=runBlocking {
        val folder=File(context.getExternalFilesDir(null),"poster-release-test")
        withSet(folder=folder){client->
            val set=manager.store.snapshot().getJSONObject("packages")
            assertEquals(34,set.length())
            val modules=set.keys().asSequence().associateWith{key->
                UpdateProtocol.module(set.getJSONObject(key).getString("manifest").toByteArray())
            }
            val newSites=modules.values.filter{it.minApp==manager.installedCode}
            assertEquals(15,newSites.size)
            assertTrue(newSites.all{it.type=="site"})
            for((key,module) in modules){
                val siteId=modules.values.single{it.type=="site"&&it.id.drop(1)==module.id.drop(1)}.site!!.getString("id")
                assertEquals(1,client.probe(key,payload(siteId)).getInt("sdk"))
            }
            checkPosterSize(client)
        }
    }
    private suspend fun checkPosterSize(client:PluginWorkerClient) {
        val id="filmcibaba"
        val page=Sites.get(id).base
        val html="""<article><a class="v100-card" href="/poster-fixture/">
            <img src="/poster-1280.jpg" srcset="/poster-180.jpg 180w, /poster-320.jpg 320w, /poster-1280.jpg 1280w">
            <h2 class="v100-title">Poster fixture</h2></a></article>"""
        val result=PluginWire.page(client.call(id,"site","parseList",payload(id).put("body",html).put("url",page)))
        assertEquals("$page/poster-320.jpg",result.cards.single().poster)
    }
    fun signedCatalogUpdatesReadRecordedPages()=runBlocking {
        val folder=File(context.getExternalFilesDir(null),"catalog-release-test")
        withSet(folder=folder){client->
            val entries=PluginInventory.modules(manager.store.snapshot().getJSONObject("packages")).values.associateBy{it.module.id}
            val fixtures=mapOf(
                "p004" to listOf("p004-animation" to "/Kategori/tur/animasyon-filmleri/","p004-anime" to "/Kategori/anime-izle/",
                    "p004-short" to "/Kategori/k-drama-reels-asya-dizileri/"),
                "p007" to listOf("p007-drama" to "/asya-dizileri/")
            )
            for((id,pages) in fixtures){
                val entry=entries.getValue(id)
                val module=entry.module
                val site=module.site!!
                val siteId=site.getString("id")
                assertEquals(manager.installedCode,module.minApp)
                assertTrue(site.has("catalogSections"))
                val resolver=entries.getValue("r"+id.drop(1))
                assertEquals(1,client.probe(resolver.key,payload(siteId)).getInt("sdk"))
                for((name,path) in pages){
                    val input=payload(siteId).put("body",File(folder,"$name.html").readText()).put("url",site.getString("base")+path)
                    val result=PluginWire.page(client.call(siteId,"site","parseList",input))
                    assertTrue(name,result.cards.isNotEmpty())
                    assertTrue(name,result.cards.all{it.ref.siteId==siteId})
                }
            }
        }
    }
    fun publishedSetStartsInTheWorker()=runBlocking {withSet("release-test"){client->
        val set=manager.store.snapshot().getJSONObject("packages")
        val prepared=Sites.all.map{it.id}.toSet()
        assertEquals(17,prepared.size)
        assertEquals(34,set.length())
        val ownerByCode=linkedMapOf<String,SiteSpec>()
        for(key in set.keys()){
            val module=UpdateProtocol.module(set.getJSONObject(key).getString("manifest").toByteArray())
            assertEquals(39,module.minApp)
            if(module.type=="site")ownerByCode[module.id.drop(1)]=Sites.get(module.site!!.getString("id"))
        }
        assertEquals(prepared,ownerByCode.values.map{it.id}.toSet())
        for(key in set.keys()){
            val spec=ownerByCode.getValue(key.substringAfterLast('/').drop(1))
            assertEquals(1,client.probe(key,payload(spec.id)).getInt("sdk"))
        }
    }}

    @Test fun signaturesDependenciesAndFourCatalogsLoadInRealWorker()=runBlocking {withSet{ client->
        val set=manager.store.snapshot().getJSONObject("packages");assertEquals(8,set.length())
        assertEquals(listOf("p002","p003","filmcibaba","p005"),Sites.all.map{it.id})
        for(key in set.keys()) {
            val m=UpdateProtocol.module(set.getJSONObject(key).getString("manifest").toByteArray());val id=when(m.id.substring(1)){"002"->"p002";"003"->"p003";"004"->"filmcibaba";else->"p005"}
            assertEquals(1,client.probe(key,payload(id)).getInt("sdk"))
        }
        val rows=JSONArray()
        for(id in listOf("p002","p003","filmcibaba","p005")) {
            val actual=PluginWire.page(client.call(id,"site","parseList",payload(id).put("body",asset("$id/catalog.html").toString(Charsets.UTF_8)).put("url",asset("$id/catalog-url.txt").toString(Charsets.UTF_8))))
            assertTrue(id,actual.cards.isNotEmpty());assertTrue(actual.cards.all{it.ref.siteId==id})
            rows.put(JSONObject().put("id",id).put("cards",actual.cards.size))
        }
        File(context.getExternalFilesDir(null),"pilot-worker.json").writeText(JSONObject().put("api",android.os.Build.VERSION.SDK_INT).put("entries",8).put("catalogs",rows).toString(2))
    }}
    @Test fun fourLiveSourcesRenderAndAdvanceTwentySeconds()=runBlocking {withSet{client->
        val results=JSONArray();var failures=0
        val selected=InstrumentationRegistry.getArguments().getString("pilotSite")
        for(id in listOf("p002","p003","filmcibaba","p005").filter{selected==null||it==selected}) {
            val row=JSONObject().put("id",id);var activity:NativeMainActivity?=null
            try {
                val card=Contracts.readCard(JSONObject(asset("$id/search.json").toString(Charsets.UTF_8)).getJSONArray("cards").getJSONObject(0))
                var detail=PluginWire.detail(client.call(id,"site","detail",payload(id).put("card",Contracts.card(card))))
                if(detail.episodes.isNotEmpty()) detail=PluginWire.detail(client.call(id,"site","detail",payload(id).put("card",Contracts.card(detail.episodes.first().card))))
                val option=detail.options.first{it.state==OptionState.AVAILABLE}
                val source=client.call(id,"resolver","resolve",payload(id).put("option",Contracts.option(option)))
                activity=ins.startActivitySync(Intent(context,NativeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as NativeMainActivity
                val current=activity;val requests=java.util.concurrent.CopyOnWriteArrayList<JSONObject>()
                val http=okhttp3.OkHttpClient.Builder().addNetworkInterceptor{chain->val r=chain.request();requests.add(JSONObject().put("origin",r.url.scheme+"://"+r.url.host).put("referer",r.header("Referer")).put("ua",r.header("User-Agent")));chain.proceed(r)}.build()
                ins.runOnMainSync {val method=current.javaClass.declaredMethods.single{it.name.startsWith("startSourcesWithClientForTest")};method.isAccessible=true;method.invoke(current,listOf(source),http)}
                val start=SystemClock.elapsedRealtime();var state=JSONObject()
                while(SystemClock.elapsedRealtime()-start<60000) {
                    delay(500);ins.runOnMainSync{state=current.playbackSnapshotForTest()}
                    if(!state.isNull("error")||state.optBoolean("firstFrame")&&state.optLong("positionMs")>=20000) break
                }
                row.put("playback",state).put("elapsedMs",SystemClock.elapsedRealtime()-start).put("mediaRequests",requests.size)
                assertTrue(id+state,state.optBoolean("firstFrame")&&state.optLong("positionMs")>=20000&&state.isNull("error"))
                assertEquals(1,state.getInt("engineInstancesUsed"))
                assertTrue(requests.isNotEmpty());assertTrue(requests.all{it.optString("referer")==source.getString("referer")&&it.optString("ua")==source.getString("userAgent")})
                row.put("passed",true)
            } catch(e:Throwable) {failures++;row.put("passed",false).put("error",e.toString())}
            finally {
                activity?.let{a->ins.runOnMainSync{a.finish()}};results.put(row)
                val file=File(context.getExternalFilesDir(null),"pilot-live-playback.json")
                val combined=JSONArray()
                if(selected!=null&&file.exists()){val old=JSONArray(file.readText());for(i in 0 until old.length())if(old.getJSONObject(i).getString("id")!=selected)combined.put(old.getJSONObject(i))}
                for(i in 0 until results.length())combined.put(results.getJSONObject(i))
                file.writeText(combined.toString(2))
            }
        }
        assertEquals(results.toString(),0,failures)
    }}
}
