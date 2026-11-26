package app.filmmerkezi.tv

import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Explicit live metadata check for only the affected provider. No player or user-library writes. */
class FixFullSearchLiveTest {
    @Test fun realSearchAndEpisodeArchives()=runBlocking {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val network=ProviderNetwork(c);val provider=NativeSiteProvider(Sites.get("fixfullfilmizle"),network,Dispatchers.Default.limitedParallelism(1))
        val report=JSONObject().put("live",true);val rows=JSONArray()
        try{
            for(query in listOf("Black Mirror","Breaking Bad")){
                val p=provider.search(query);val card=p.cards.single();assertEquals(ContentKind.SERIES,card.ref.kind);assertEquals(query,card.title)
                val d=provider.detail(card);assertEquals(query,d.card.title)
                val episodes=provider.episodes(card);assertTrue(episodes.isNotEmpty())
                rows.put(JSONObject().put("query",query).put("cards",p.cards.size).put("type",card.ref.kind.name).put("page",card.ref.pageUrl)
                    .put("episodes",episodes.size).put("seasons",JSONArray(episodes.map{it.number.season}.distinct())))
            }
            report.put("status","PASS")
        }catch(e:Throwable){report.put("status","FAIL").put("error",e.toString());throw e}
        finally{report.put("results",rows);File(c.getExternalFilesDir(null),"fixfull-search-live.json").writeText(report.toString(2));network.client.dispatcher.executorService.shutdown();network.client.connectionPool.evictAll()}
        Unit
    }
}
