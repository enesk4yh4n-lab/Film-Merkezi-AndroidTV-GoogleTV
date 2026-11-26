package app.filmmerkezi.tv

import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest

/** Explicit, local-only audit of every configured site's catalog and series contract. */
class SeriesSiteAuditTest {
    @Test fun auditConfiguredSites()=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("seriesAudit")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.getExternalFilesDir(null),"series-audit").apply{mkdirs()}
        val calls=JSONArray();val sites=JSONArray();val http=ProviderNetwork(context)
        val repository=CatalogRepository(object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
            val row=JSONObject().put("url",request.url).put("method",request.method).put("fields",JSONObject(request.fields))
            try{val response=http.request(request);val bytes=response.body.toByteArray(Charsets.UTF_8)
                val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt()and 255)}
                File(folder,"$hash.html").writeBytes(bytes)
                row.put("finalUrl",response.url).put("file","$hash.html").put("bytes",bytes.size).put("status",response.code)
                return response
            }catch(e:Exception){row.put("error",e.message);throw e}finally{calls.put(row);File(folder,"requests.json").writeText(calls.toString(2))}
        }})
        suspend fun stage(out:JSONObject,key:String,body:suspend()->JSONObject){
            try{out.put(key,body())}catch(e:CancellationException){throw e}catch(e:Exception){out.put(key,JSONObject().put("status","ERROR").put("kind",(e as? DataFailure)?.kind).put("error",e.message))}
            File(folder,"sites.json").writeText(sites.toString(2))
        }
        for(p in repository.providers.values){
            val row=JSONObject().put("site",p.site.id).put("seriesConfigured",p.site.seriesPath!=null);sites.put(row)
            var search=emptyList<Card>();var browse=emptyList<Card>();var sample:Card?=null
            stage(row,"search"){
                val page=p.search("House of the Dragon");search=page.cards
                JSONObject().put("status",if(page.cards.isEmpty())"EMPTY"else"OK").put("cards",JSONArray(page.cards.map{Contracts.card(it)})).put("next",page.next?.let{Contracts.cursor(it)})
            }
            stage(row,"browse"){
                val page=p.browse(if(p.site.seriesPath!=null)ContentKind.SERIES else ContentKind.MOVIE);browse=page.cards;sample=page.cards.firstOrNull()
                JSONObject().put("status",if(page.cards.isEmpty())"EMPTY"else"OK").put("count",page.cards.size).put("cards",JSONArray(page.cards.take(6).map{Contracts.card(it)})).put("next",page.next?.let{Contracts.cursor(it)})
            }
            val target=(search.filter{ContentIdentity.relevant(it,"House of the Dragon")}.take(8)+if(p.site.id=="hdfilmdelisi")browse.take(5)else emptyList()).distinctBy{it.ref.id}.ifEmpty{listOfNotNull(sample)}
            val details=JSONArray();row.put("details",details)
            for(c in target){val drow=JSONObject().put("seed",Contracts.card(c));details.put(drow)
                stage(drow,"detail"){
                    val d=repository.detail(c)
                    val episodes=if(d.card.ref.kind==ContentKind.SERIES)p.episodes(d.card)else d.episodes
                    val wrong=episodes.filter{it.card.episodeNumber!=it.number||p.site.id=="hdfilmdelisi"&&!it.card.ref.pageUrl.startsWith(d.card.ref.pageUrl.trimEnd('/')+"/sezon-")}
                    assertTrue("Episodes from another series: ${p.site.id}",wrong.isEmpty())
                    JSONObject().put("status","OK").put("card",Contracts.card(d.card)).put("options",d.options.size)
                        .put("seasons",JSONObject(episodes.groupBy{it.number.season.toString()}.mapValues{it.value.size}))
                        .put("episodes",JSONArray(episodes.map{e->JSONObject().put("season",e.number.season).put("episode",e.number.number).put("url",e.card.ref.pageUrl).put("title",e.card.title)}))
                }
            }
        }
        assertEquals(Sites.all.size,sites.length())
        val cards=sites.values().filterIsInstance<JSONObject>().flatMap{it.optJSONArray("details")?.values()?.filterIsInstance<JSONObject>().orEmpty()}
            .mapNotNull{it.optJSONObject("detail")?.optJSONObject("card")?.let{Contracts.readCard(it)}}
        val groups=ContentIdentity.merge(cards)
        File(folder,"groups.json").writeText(JSONArray(groups.map{g->JSONObject().put("identity",g.identity).put("card",Contracts.card(g.card)).put("alternatives",JSONArray(g.alternatives.map{Contracts.card(it)}))}).toString(2))
    }
}
