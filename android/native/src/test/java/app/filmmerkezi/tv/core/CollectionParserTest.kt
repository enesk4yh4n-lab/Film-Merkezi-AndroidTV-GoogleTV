package app.filmmerkezi.tv.core

import org.junit.Test
import org.junit.Assert.*

class CollectionParserTest {
    @Test fun articleOrderAndMeaningfulNumbersArePreserved(){
        val items=CollectionParser.entries("<h1>Seri</h1><article class='news-content article-content'><h2>2. Matrix 2 – Reloaded (2003)</h2><h3>1. The Matrix (1999)</h3><p>3. Advertisement (2026)</p></article>","https://filmizle.ink/haber/seri/")
        assertEquals(listOf(2,1),items.map{it.ordinal});assertEquals("Matrix 2 – Reloaded",items.first().title);assertEquals(listOf("Matrix 2","Reloaded"),items.first().aliases)
    }
    @Test fun unrelatedArticleIsNotPlayableCollection(){try{CollectionParser.entries("<article class='news-content article-content'><h2>News</h2></article>","https://filmizle.ink/haber/seri/");fail()}catch(e:DataFailure){assertEquals(FailureKind.PARSE,e.kind)}}
    @Test fun liveTop50DetailSupportsOriginalTitlesAndDashYears(){
        val body=javaClass.getResource("/collections/imdb-top50.html")!!.readText()
        val card=Card(ContentRef("filmcibaba","https://filmizle.ink/haber/imdb-top-50-filmi-sinema-dunyasinin-zirvesine-yolculuk/",ContentKind.COLLECTION),"IMDB Top 50")
        val detail=CatalogParser(Sites.get("filmcibaba")).detail(body,card)
        assertEquals(ContentKind.COLLECTION,detail.card.ref.kind);assertEquals(50,detail.collectionItems.size)
        assertEquals((1..50).toList(),detail.collectionItems.map{it.ordinal})
        assertEquals(1994,detail.collectionItems.first().year);assertTrue(detail.collectionItems.first().aliases.contains("The Shawshank Redemption"))
        assertTrue(detail.collectionItems.first().aliases.contains("Esaretin Bedeli"));assertTrue(detail.options.isEmpty())
    }
    @Test fun collectionIndexFallsBackToSiteSearchWhenHomeHasNoArticles()=kotlinx.coroutines.runBlocking {
        val requests=mutableListOf<String>()
        val transport=object:WebTransport {override suspend fun request(request:WebRequest):WebResponse{
            requests.add(request.url)
            val fixture=if(request.url=="https://filmizle.ink/")"home"else "search"
            return WebResponse(request.url,javaClass.getResource("/collections/$fixture.html")!!.readText())
        }}
        val found=CatalogRepository(transport,requestOptions={CatalogRequestOptions(sites=listOf("filmcibaba"))}).collections()
        assertTrue(found.any{it.ref.pageUrl.contains("imdb-top-50")});assertTrue(found.all{it.ref.kind==ContentKind.COLLECTION})
        assertEquals(3,requests.size);assertTrue(requests.last().contains("s=top"))
    }
    @Test fun collectionCannotMergeWithMovieEvenWithSameImdb(){val c=Card(ContentRef("filmcibaba","https://filmizle.ink/haber/seri/",ContentKind.COLLECTION),"Matrix",year=1999,imdbId="tt0133093");assertEquals(IdentityState.DIFFERENT,ContentIdentity.decide(c,c.copy(ref=c.ref.copy(kind=ContentKind.MOVIE))).state)}
    @Test fun memberMatchingNeedsYearAndTitle(){val e=CollectionEntry(1,"Matrix",1999);val c=Card(ContentRef("sinemacc","https://sinemacc.com/film/matrix/"),"Matrix",year=1999);assertTrue(e.matches(c));assertFalse(e.matches(c.copy(year=2024)));assertFalse(e.matches(c.copy(title="Matrix Reloaded")))}
}
