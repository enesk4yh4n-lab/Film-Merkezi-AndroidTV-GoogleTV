package app.filmmerkezi.tv

import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Explicit opt-in: real native HTTP and parser, metadata only, no media. */
class EpisodeLiveTest {
    @Test fun liveSeriesArchiveChecks()=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveEpisodes")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repository=CatalogRepository(ProviderNetwork(context));val rows=JSONArray();val wrong=mutableListOf<String>()
        for((site,path,title,count) in listOf(
            listOf("filmcibaba","/vladimir-i2/","Vladimir 1.Sezon","8"),
            listOf("dizibox","/diziler/12-monkeys-izle-1/","12 Monkeys","47"),
            listOf("hdfilmdelisi","/dizi/breaking-bad","Breaking Bad","62"),
            listOf("hdfilmdelisi","/dizi/the-mentalist","The Mentalist","151"),
            listOf("ddizi","/diziler/1815/dark-hd1","Dark","26"),
            listOf("hdfilmcehennemi","/dizi/black-mirror-izle-3/","Black Mirror","33"),
            listOf("fixfullfilmizle","/diziler/after-life/","After Life","18"),
            listOf("filmizlebabam","/dizi/the-mentalist/","The Mentalist","151")
        )){
            val url=Sites.get(site).base+path;val row=JSONObject().put("site",site).put("url",url);val started=System.nanoTime()
            try{
                val d=repository.detail(Card(ContentRef(site,url,ContentKind.SERIES),title));val episodes=repository.episodes(d.card)
                val valid=d.card.ref.kind==ContentKind.SERIES&&episodes.size==count.toInt()&&episodes.firstOrNull()?.number==EpisodeNumber(1,1)
                row.put("status",if(valid)"PASS"else"FAIL").put("kind",d.card.ref.kind).put("count",episodes.size)
                    .put("seasons",JSONObject(episodes.groupBy{it.number.season.toString()}.mapValues{it.value.size}))
                if(!valid)wrong.add(site+" "+title)
            }catch(e:CancellationException){throw e}catch(e:Exception){row.put("status","UNAVAILABLE").put("error",e.message).put("kind",(e as? DataFailure)?.kind)}
            row.put("seconds",(System.nanoTime()-started)/1e9);rows.put(row)
        }
        File(context.getExternalFilesDir(null),"episodes-live.json").writeText(rows.toString(2))
        assertTrue("Accessible site returned wrong episode archive: $wrong",wrong.isEmpty())
        assertTrue("No live site could be validated",rows.values().filterIsInstance<JSONObject>().any{it.string("status")=="PASS"})
    }
}
