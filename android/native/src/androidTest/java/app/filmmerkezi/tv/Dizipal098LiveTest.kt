package app.filmmerkezi.tv

import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Opt-in, short public metadata audit. Playback is NativeLiveTest's filtered target. */
class Dizipal098LiveTest {
    @Test fun realSearchBrowseSeasonsAndSource()=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeLive")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val native=ProviderNetwork(context)
        val calls=JSONArray();val transport=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
            val start=System.nanoTime();val r=native.request(request);calls.put(JSONObject().put("url",request.url).put("status",r.code).put("ms",(System.nanoTime()-start)/1000000));return r
        }}
        val p=NativeSiteProvider(Sites.get("dizipal"),transport,Dispatchers.Default)
        val out=JSONObject().put("fixture",false).put("requests",calls)
        try{
            val search=p.search("House of the Dragon");val house=search.cards.single{it.ref.pageUrl.endsWith("/diziler/house-of-the-dragon")}
            out.put("searchCards",search.cards.size)
            for(kind in listOf(ContentKind.MOVIE,ContentKind.SERIES)){val first=p.browse(kind);val second=p.browse(kind,checkNotNull(first.next));assertTrue(first.cards.isNotEmpty()&&second.cards.isNotEmpty());out.put(kind.name,JSONObject().put("page1",first.cards.size).put("page2",second.cards.size))}
            val detail=p.detail(house);val eps=p.episodes(house)
            out.put("year",detail.card.year).put("episodes",eps.size).put("seasons",JSONObject(eps.groupBy{it.number.season}.mapKeys{it.key.toString()}.mapValues{it.value.size}))
            assertEquals(listOf(10,8,8),eps.groupBy{it.number.season}.values.map{it.size})
            val ep=p.detail(eps.single{it.number==EpisodeNumber(1,3)}.card);assertEquals(1,ep.options.size)
            val source=SourceResolver(transport).resolve(ep.options.single());out.put("source",Contracts.source(source));out.put("status","PASS")
            assertEquals("https://streamcorecdn.com/dizi/94997/1/3",source.referer);assertEquals("tur",source.subtitles.single().language)
        }finally{File(context.getExternalFilesDir(null),"dizipal098-live-metadata.json").writeText(out.toString(2))}
        Unit
    }
}
