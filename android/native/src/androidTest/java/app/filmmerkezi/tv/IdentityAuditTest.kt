package app.filmmerkezi.tv

import app.filmmerkezi.tv.core.*
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import android.database.sqlite.SQLiteDatabase

/** Opt-in, catalog-only capture. Production data is read, never cleared or rewritten. */
class IdentityAuditTest {
    @Test fun captureSearchEvidence()=runBlocking {
        val ins=InstrumentationRegistry.getInstrumentation();val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("identityAudit")=="true")
        val context=ins.targetContext
        val label=args.getString("auditLabel")?:"baseline-070"
        require(label.matches(Regex("[a-zA-Z0-9_-]+")))
        val destination=File(context.getExternalFilesDir(null),"identity-audit-$label").apply{mkdirs()}
        val raw=File(destination,"responses").apply{mkdirs()};val count=AtomicInteger()
        val network=ProviderNetwork(context)
        val replay=args.getString("auditReplay")=="true"
        val replayFile=args.getString("auditReplayFile")?:"merge-corpus.json.gz"
        require(replayFile.matches(Regex("[a-zA-Z0-9_-]+\\.json\\.gz")))
        val reference=if(replay)java.util.zip.GZIPInputStream(File(context.getExternalFilesDir(null),replayFile).inputStream()).bufferedReader().use{JSONObject(it.readText())}else null
        val recorded=reference?.getJSONArray("responses")?.values()?.filterIsInstance<JSONObject>()?.associateBy{
            it.getString("method")+it.getString("url")+it.getJSONObject("fields").let{o->o.keys().asSequence().associateWith{o.getString(it)}.toSortedMap()}
        }.orEmpty()
        val transport=object:WebTransport{
            override suspend fun request(request:WebRequest):WebResponse{
                val fixture=recorded[request.method+request.url+request.fields.toSortedMap()]
                val r=if(!replay)network.request(request)else fixture?.let{WebResponse(it.optString("finalUrl").ifBlank{request.url},it.getString("body"),it.getInt("code"))}
                    ?:throw DataFailure(FailureKind.NETWORK,"FIXTURE EKSİK: "+request.url,"fixture")
                val key=TextKey.hash(request.method+request.url+request.fields.toSortedMap())
                File(raw,"$key.json").writeText(JSONObject().put("url",request.url).put("finalUrl",r.url).put("method",request.method)
                    .put("fields",JSONObject(request.fields)).put("body",r.body).put("code",r.code).toString())
                count.incrementAndGet();return r
            }
        }
        val production=context.getDatabasePath("film-works.db")
        val stored=JSONArray()
        if(production.exists())SQLiteDatabase.openDatabase(production.path,null,SQLiteDatabase.OPEN_READONLY).use{db->
            db.rawQuery("SELECT work,card FROM sources WHERE lower(card) LIKE '%matrix%'",null).use{c->while(c.moveToNext())stored.put(JSONObject().put("work",c.getString(0)).put("card",JSONObject(c.getString(1))))}
        }
        File(destination,"existing-matrix.json").writeText(stored.toString(2))
        val queries=(args.getString("queries")?:"matrix").split('|').map{it.trim()}.filter{it.isNotBlank()}
        val report=JSONArray()
        for(query in queries){
            val database="identity-audit-$label-"+TextKey.hash(query)+".db"
            context.deleteDatabase(database) // This capture's isolated disposable DB only.
            try{WorkStore(context,database).use{works->
                val repository=CatalogRepository(transport,works);val started=android.os.SystemClock.elapsedRealtime()
                var latest=UnifiedPage(emptyList(),emptyMap(),emptyList())
                val snapshots=JSONArray()
                withTimeoutOrNull(180000){repository.observeSearch(query).collect{page->latest=page
                    snapshots.put(JSONObject().put("ms",android.os.SystemClock.elapsedRealtime()-started).put("groups",page.items.size).put("done",page.completedSites))
                    File(destination,"current.json").writeText(Contracts.unified(page).put("query",query).put("snapshots",snapshots).toString(2))
                }}
                val entry=Contracts.unified(latest).put("query",query).put("snapshots",snapshots).put("requests",count.get()).put("fixture",replay)
                entry.put("facts",JSONArray(latest.items.flatMap{it.alternatives}.map{c->val f=ContentIdentity.facts(c)
                    JSONObject().put("card",Contracts.card(c)).put("names",JSONObject(f.names)).put("kind",f.kind.name).put("year",f.year?:JSONObject.NULL).put("imdb",f.imdb).put("checked",f.checked).put("typeVerified",f.typeVerified)
                }))
                report.put(entry)
                File(destination,"searches.json").writeText(report.toString(2))
                if(replay&&args.getString("auditCheckReviewed")=="true"){
                    val expected=checkNotNull(reference).getJSONArray("expectations").values().filterIsInstance<JSONObject>().filter{it.getString("query")==query}
                    for(e in expected){val pages=e.getJSONArray("pages").values().map{it.toString()}.toSet()
                        val groups=latest.items.filter{g->g.alternatives.any{it.ref.pageUrl in pages}}
                        check(groups.size==1){e.getString("label")+" split into "+groups.size+" groups"}
                        check(groups.single().alternatives.map{it.ref.pageUrl}.containsAll(pages)){"Missing captured pages for "+e.getString("label")}
                    }
                }
                if(args.getString("auditDetails")=="true"){
                    val checks=JSONArray();val sem=kotlinx.coroutines.sync.Semaphore(2)
                    coroutineScope{latest.items.flatMap{it.alternatives}.distinctBy{it.ref.id}.take(60).forEach{c->launch{
                        sem.acquire();try{
                            val result=try{Contracts.detail(repository.provider(c.ref.siteId).detail(c))}catch(e:CancellationException){throw e}catch(e:Exception){JSONObject().put("error",e.message)}
                            synchronized(checks){checks.put(JSONObject().put("seed",Contracts.card(c)).put("detail",result));File(destination,"details.json").writeText(checks.toString(2))}
                        }finally{sem.release()}
                    }}}
                }
            }}finally{context.deleteDatabase(database)}
        }
    }
}
