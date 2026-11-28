package app.filmmerkezi.tv.updates

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.NativeMainActivity
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** All discovered options for the four changed providers; no unrelated site matrix. */
class PilotCatalogRulesDeviceTest {
    @Test fun updatedMetadataRunsAcrossSignedWorker()=runBlocking {PilotWorkerTest().withSet{client->
        val ins=InstrumentationRegistry.getInstrumentation();val results=JSONArray()
        for(id in listOf("p002","p003","filmcibaba","p005")){
            val raw=ins.context.assets.open("pilot-test/$id/search.json").bufferedReader().use{it.readText()}
            val card=Contracts.readCard(JSONObject(raw).getJSONArray("cards").getJSONObject(0))
            val detail=PluginWire.detail(client.call(id,"site","detail",JSONObject().put("site",PluginWire.site(Sites.get(id))).put("card",Contracts.card(card))))
            when(id){"p002"->{assertEquals(2018,detail.card.year);assertTrue(detail.card.genres.isNotEmpty())}
                "p003"->{assertEquals(listOf("DRAM"),detail.card.genres);assertTrue(detail.description.isNotBlank());assertTrue(detail.cast.size>=2)}
                "filmcibaba"->{assertEquals(2025,detail.card.year);assertTrue(detail.card.genres.none{it.startsWith("2025")})}
                "p005"->{assertTrue(detail.cast.any{it.contains("Şaşmaz")});assertTrue(detail.cast.none{it=="Erkan Aldur"})}}
            results.put(JSONObject().put("site",id).put("year",detail.card.year?:JSONObject.NULL).put("genres",JSONArray(detail.card.genres)).put("cast",JSONArray(detail.cast)).put("episodes",detail.episodes.size).put("passed",true))
        }
        File(ins.targetContext.getExternalFilesDir(null),"catalog-rules-worker-metadata.json").writeText(results.toString(2))
    }}
    @Test fun everyDiscoveredSourceRendersAndAdvances()=runBlocking {PilotWorkerTest().withSet{client->
        val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext;val rows=JSONArray();var failures=0
        val selected=InstrumentationRegistry.getArguments().getString("pilotSite")
        for(id in listOf("p002","p003","filmcibaba","p005").filter{selected==null||it==selected}){
            val row=JSONObject().put("site",id);val sources=JSONArray();row.put("sources",sources)
            try{
                val sample=if(id=="filmcibaba"&&InstrumentationRegistry.getArguments().getString("pilotCase")=="series")"filmcibaba-series"else id
                val raw=ins.context.assets.open("pilot-test/$sample/search.json").bufferedReader().use{it.readText()}
                val card=Contracts.readCard(JSONObject(raw).getJSONArray("cards").getJSONObject(0))
                fun input()=JSONObject().put("site",PluginWire.site(Sites.get(id)))
                val detail=PluginWire.detail(client.call(id,"site","detail",input().put("card",Contracts.card(card))))
                row.put("content",detail.card.title).put("kind",detail.card.ref.kind.name).put("genres",JSONArray(detail.card.genres)).put("year",detail.card.year?:JSONObject.NULL)
                    .put("imdb",detail.card.imdb?:JSONObject.NULL).put("imdbId",detail.card.imdbId).put("episodeCount",detail.episodes.size).put("seasonCount",detail.seasons.size)
                val playable=if(detail.episodes.isNotEmpty())PluginWire.detail(client.call(id,"site","detail",input().put("card",Contracts.card(detail.episodes.first().card))))else detail
                row.put("sourceCount",playable.options.size);check(playable.options.isNotEmpty()){"No source options"}
                for(option in playable.options){
                    val sourceRow=JSONObject().put("id",option.id).put("label",option.label).put("state",option.state.name);sources.put(sourceRow)
                    if(option.state!=OptionState.AVAILABLE){sourceRow.put("supported",false).put("reason",option.reason);continue}
                    var activity:NativeMainActivity?=null
                    try{
                        val resolved=client.call(id,"resolver","resolve",input().put("option",Contracts.option(option)))
                        sourceRow.put("resolved",true).put("type",resolved.getString("type"))
                        activity=ins.startActivitySync(Intent(context,NativeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as NativeMainActivity
                        val current=activity;val requests=java.util.concurrent.CopyOnWriteArrayList<Pair<String?,String?>>()
                        val http=okhttp3.OkHttpClient.Builder().addNetworkInterceptor{chain->val r=chain.request();requests.add(r.header("Referer") to r.header("User-Agent"));chain.proceed(r)}.build()
                        ins.runOnMainSync{val method=current.javaClass.declaredMethods.single{it.name.startsWith("startSourcesWithClientForTest")};method.isAccessible=true;method.invoke(current,listOf(resolved),http)}
                        val began=SystemClock.elapsedRealtime();var snapshot=JSONObject()
                        while(SystemClock.elapsedRealtime()-began<50000){delay(250);ins.runOnMainSync{snapshot=current.playbackSnapshotForTest()}
                            if(!snapshot.isNull("error")||snapshot.optBoolean("firstFrame")&&snapshot.optLong("positionMs")>=15000)break}
                        sourceRow.put("playback",snapshot).put("elapsedMs",SystemClock.elapsedRealtime()-began).put("mediaRequests",requests.size)
                        assertTrue(snapshot.toString(),snapshot.optBoolean("firstFrame")&&snapshot.optLong("positionMs")>=15000&&snapshot.isNull("error"));assertEquals(1,snapshot.getInt("engineInstancesUsed"))
                        assertTrue(requests.isNotEmpty());assertTrue(requests.all{it.first==resolved.optString("referer")&&it.second==resolved.optString("userAgent")})
                        sourceRow.put("passed",true)
                    }catch(e:Throwable){failures++;sourceRow.put("passed",false).put("error",e.toString())}
                    finally{activity?.let{a->ins.runOnMainSync{a.finish()}}}
                }
                row.put("passed",(0 until sources.length()).all{sources.getJSONObject(it).optBoolean("passed")||sources.getJSONObject(it).optBoolean("supported",true)==false})
            }catch(e:Throwable){failures++;row.put("passed",false).put("error",e.toString())}
            rows.put(row);File(context.getExternalFilesDir(null),"pilot-catalog-rules-sources.json").writeText(rows.toString(2))
        }
        assertEquals(rows.toString(),0,failures)
    }}
}
