package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BabamDomainTest {
    private val site=Sites.get("filmizlebabam")
    @Test fun currentCapturedHomeSearchAndSeriesDetailStillParse(){
        fun body(name:String)=checkNotNull(javaClass.getResource("/babam-icu/$name.html")).readText()
        val parser=CatalogParser(site);val home=parser.list(body("home"),site.base+"/")
        val search=parser.list(body("search"),site.base+"/ara?q=reacher")
        assertTrue(home.cards.isNotEmpty());assertTrue(search.cards.any{it.title.contains("Reacher",true)})
        assertTrue((home.cards+search.cards).all{it.ref.pageUrl.startsWith(site.base+"/")})
        val detail=parser.detail(body("detail"),Card(ContentRef(site.id,site.base+"/dizi/reacher/",ContentKind.SERIES),"Reacher"))
        assertEquals(ContentKind.SERIES,detail.card.ref.kind);assertTrue(detail.episodes.isNotEmpty())
        println("Babam ICU capture: home=${home.cards.size}, search=${search.cards.size}, episodes=${detail.episodes.size}, seasons=${detail.seasons}")
    }
    @Test fun knownOldHostsRebaseWithoutChangingContentIdentityOrAllowingLookalikes(){
        assertEquals("https://filmizlebabam.icu",site.base)
        val old=ContentRef(site.id,"https://www.filmizlebabam.com/dizi/reacher/",ContentKind.SERIES)
        val current=old.copy(pageUrl=site.catalogUrl(old.pageUrl)!!)
        assertEquals("https://filmizlebabam.icu/dizi/reacher/",current.pageUrl);assertEquals(old.id,current.id)
        for(host in site.catalogAliases)assertEquals("https://filmizlebabam.icu/ara?q=reacher",site.catalogUrl("https://$host/ara?q=reacher"))
        assertNull(site.catalogUrl("https://filmizlebabam.com.evil.example/dizi/reacher/"));assertNull(site.catalogUrl("https://filmizlebabam.icu:444/dizi/reacher/"))
        assertNull(site.catalogUrl("https://user@filmizlebabam.icu/dizi/reacher/"))
    }
    @Test fun oldSavedEpisodeAndParentReadAtTheNewHostWhileWorkIdAndNumberSurvive(){
        val parent=ContentRef(site.id,"https://www.filmizlebabam.com/dizi/reacher/",ContentKind.SERIES)
        val card=Card(ContentRef(site.id,"https://www.filmizlebabam.com/bolum/reacher-1-sezon-1-bolum/",ContentKind.EPISODE),"Reacher",seriesRef=parent,episodeNumber=EpisodeNumber(1,1),seriesIdentity="work:reacher",workId="work:reacher-1-1")
        val read=Contracts.readCard(Contracts.card(card));assertEquals(card.workId,read.workId);assertEquals(card.ref.id,read.ref.id);assertEquals(card.episodeNumber,read.episodeNumber)
        assertTrue(read.ref.pageUrl.startsWith(site.base));assertTrue(read.seriesRef!!.pageUrl.startsWith(site.base))
    }
    @Test fun oldDetailAndPaginationRequestsUseNewHostAndReferer()=runBlocking {
        val calls=mutableListOf<WebRequest>();val provider=NativeSiteProvider(site,object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
            calls.add(request);return WebResponse(request.url,if("/ara" in request.url)"<a class='group block' href='/dizi/reacher/'><h3>Reacher</h3></a>"else "<h1>Reacher</h1><script type='application/ld+json'>{\"@type\":\"TVSeries\",\"name\":\"Reacher\",\"url\":\"https://filmizlebabam.icu/dizi/reacher/\"}</script>")
        }},Dispatchers.Default)
        val detail=provider.detail(Card(ContentRef(site.id,"https://www.filmizlebabam.com/dizi/reacher/",ContentKind.SERIES),"Reacher",workId="work:reacher"))
        assertEquals(site.base+"/dizi/reacher/",calls.single().url);assertEquals(site.base+"/",calls.single().referer);assertEquals(site.base+"/dizi/reacher/",detail.card.ref.pageUrl)
        provider.search("Reacher",PageCursor(site.id,"https://filmizlebabam.com/ara?q=reacher&page=2",2))
        assertEquals(site.base+"/ara?q=reacher&page=2",calls.last().url)
        Unit
    }
    @Test fun legacySourceUsesNewSiteRefererWithoutRewritingExternalMedia()=runBlocking {
        val ref=ContentRef(site.id,"https://www.filmizlebabam.com/reacher/")
        val media="https://cdn.example.test/stream.mp4"
        val resolver=SourceResolver(object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
            assertEquals(media,request.url);assertEquals(site.base+"/reacher/",request.referer)
            return WebResponse(request.url,"",headers=mapOf("Content-Type" to "video/mp4"))
        }})
        val source=resolver.resolve(SourceOption("legacy",ref,"Fixture",media))
        assertEquals(media,source.url);assertEquals(site.base+"/reacher/",source.referer)
        Unit
    }
}
