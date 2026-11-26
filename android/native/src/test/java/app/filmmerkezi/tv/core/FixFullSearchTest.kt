package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.last
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class FixFullSearchTest {
    private val site=Sites.get("fixfullfilmizle")
    private fun body(name:String)=checkNotNull(javaClass.getResource("/fixfull114/$name.html")).readText()
    private fun transport(calls:MutableList<WebRequest>)=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
        calls.add(request);val u=request.url.toHttpUrl();val path=u.encodedPath
        val name=when{
            u.queryParameter("s")?.lowercase()=="black mirror"->if(path.contains("/page/2/"))"black-search-2"else"black-search"
            u.queryParameter("s")?.lowercase()=="breaking bad"->"breaking-search"
            u.queryParameter("s")?.lowercase()=="house"->"house"
            u.queryParameter("s")?.lowercase()=="reacher"->"reacher"
            path.startsWith("/diziler/black-mirror/")->if(path.contains("/page/2/"))"black-series-2"else"black-series"
            path.startsWith("/diziler/breaking-bad/")->Regex("/page/(\\d+)/").find(path)?.groupValues?.get(1)?.let{"breaking-series-$it"}?:"breaking-series"
            else->error("Unexpected request: "+request.url)
        }
        return WebResponse(request.url,body(name))
    }}
    @Test fun recordedEpisodeSearchBecomesOneDeclaredSeriesWithoutExtraRequests()=runBlocking {
        val calls=mutableListOf<WebRequest>();val provider=NativeSiteProvider(site,transport(calls),Dispatchers.Default)
        for((query,fixture,title,slug)in listOf(listOf("Black Mirror","black-search","Black Mirror","black-mirror"),listOf("Breaking Bad","breaking-search","Breaking Bad","breaking-bad"))){
            val raw=CatalogParser(site).list(body(fixture),site.base+"/?s="+query.replace(' ','+'))
            assertEquals(12,raw.cards.size);assertTrue(raw.cards.all{it.ref.kind==ContentKind.EPISODE})
            val before=calls.size;val projected=provider.search(query);assertEquals(before+1,calls.size)
            val card=projected.cards.single();assertEquals(ContentKind.SERIES,card.ref.kind);assertEquals(title,card.title)
            assertEquals(site.base+"/diziler/$slug/",card.ref.pageUrl);assertNull(card.episodeNumber);assertNull(card.year);assertNull(card.imdb)
            assertTrue(card.poster.isNotBlank());assertNotNull(projected.next)
        }
        Unit
    }
    @Test fun laterSearchPagesKeepOneSeriesAndTheRealNextCursor()=runBlocking {
        val calls=mutableListOf<WebRequest>();val provider=NativeSiteProvider(site,transport(calls),Dispatchers.Default)
        val first=provider.search("Black Mirror");val second=provider.search("Black Mirror",first.next)
        assertEquals(first.cards.single().ref,second.cards.single().ref);assertEquals(2,calls.size)
        assertEquals("black mirror",second.next!!.url.toHttpUrl().queryParameter("s")?.lowercase());assertEquals(3,second.next.page)
    }
    @Test fun movieResultsRemainWhileAnIncidentalEpisodeJoinsItsOwnSeries()=runBlocking {
        val p=NativeSiteProvider(site,transport(mutableListOf()),Dispatchers.Default)
        val films=p.search("house");val expected=CatalogParser(site).list(body("house"),site.base+"/?s=house")
        assertEquals(expected.cards.filter{it.ref.kind==ContentKind.MOVIE},films.cards.filter{it.ref.kind==ContentKind.MOVIE})
        assertTrue(films.cards.none{it.ref.kind==ContentKind.EPISODE});assertTrue(films.cards.any{it.title=="Black Mirror"&&it.ref.kind==ContentKind.SERIES})
        assertTrue(p.search("reacher").cards.isEmpty())
    }
    @Test fun seriesSearchWorksWithUnifiedCardsDisabled()=runBlocking {
        val repo=CatalogRepository(transport(mutableListOf()),requestOptions={CatalogRequestOptions(merge=false,sites=listOf(site.id))})
        val result=repo.observeSearch("Black Mirror").last();assertEquals(1,result.items.size)
        assertEquals(ContentKind.SERIES,result.items.single().card.ref.kind);assertEquals(1,result.siteCounts[site.id])
    }
    @Test fun parentDetailsKeepTheirTitleAndAllRecordedEpisodes()=runBlocking {
        val p=NativeSiteProvider(site,transport(mutableListOf()),Dispatchers.Default)
        for(query in listOf("Black Mirror","Breaking Bad")){
            val card=p.search(query).cards.single();val detail=p.detail(card);assertEquals(query,detail.card.title);assertEquals(card.poster,detail.card.poster)
            val episodes=p.episodes(card);assertTrue(episodes.isNotEmpty());assertEquals(episodes.size,episodes.map{it.number}.distinct().size)
            val expected=if(query=="Breaking Bad")62 else 33
            assertEquals(query,expected,episodes.size)
            assertEquals(if(query=="Breaking Bad")(1..5).toList()else(1..7).toList(),episodes.map{it.number.season}.distinct())
            assertTrue(episodes.all{it.series==card.ref&&it.card.ref.kind==ContentKind.EPISODE})
            println("$query: episodes=${episodes.size}, seasons=${episodes.map{it.number.season}.distinct()}")
        }
        Unit
    }
    @Test fun ambiguousNamesAndUnrelatedArchivesCannotManufactureAParent(){
        val page=site.base+"/?s=target";val ref=ContentRef(site.id,site.base+"/target-1-sezon-1-bolum-izle/",ContentKind.EPISODE)
        val raw=CatalogPage(listOf(Card(ref,"Target 1. Sezon 1. Bölüm",year=2025,imdb=9.0)))
        val mismatch="<select><option value='/diziler/other/'>Other</option></select>"
        assertTrue(FixFullSearch.project(site,mismatch,page,"target",raw).cards.isEmpty())
        val ambiguous="<select><option value='/diziler/old-target/'>Target</option><option value='/diziler/new-target/'>Target</option></select>"
        assertTrue(FixFullSearch.project(site,ambiguous,page,"1 sezon",raw).cards.isEmpty())
        try{FixFullSearch.project(site,"<div></div>",page,"target",raw);fail()}catch(e:DataFailure){assertEquals(FailureKind.PARSE,e.kind)}
    }
    @Test fun matchingIndexAddsARealSeriesEvenWhenSearchPageIsFullOfMovies(){
        val movie=Card(ContentRef(site.id,site.base+"/black-movie/"),"Black Movie")
        val html="<select><option value='/diziler/black-mirror/'>Black Mirror</option><option value='https://evil.example/diziler/black/'>Black</option><option value='/diziler/black-mirror/black-mirror-7-sezon/'>Black Mirror 7. Sezon</option></select>"
        val result=FixFullSearch.project(site,html,site.base+"/?s=black","black",CatalogPage(listOf(movie)))
        assertEquals(2,result.cards.size);assertEquals(movie,result.cards.first());assertEquals("Black Mirror",result.cards.last().title)
        assertEquals(site.base+"/diziler/black-mirror/",result.cards.last().ref.pageUrl)
    }
}
