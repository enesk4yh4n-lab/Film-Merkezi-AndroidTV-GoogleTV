package app.filmmerkezi.tv.core

import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

class KernelTest {
    @Test fun identitiesKeepProviderAlternativesWithoutMergingUnknownRemakes() {
        val a=Card(ContentRef("filmcibaba","https://filmizle.ink/isik/"),"Işık",year=2024)
        val b=a.copy(ref=ContentRef("lovefilmizle","https://lovefilmizle.net/light/"),title="IŞIK")
        val c=a.copy(ref=ContentRef("lovefilmizle","https://lovefilmizle.net/isik-2004/"),year=2004)
        val unified=ContentIdentity.merge(listOf(a,b,c))
        assertEquals(2,unified.size);assertEquals(2,unified.first().alternatives.size)
        assertNotEquals(ContentIdentity.key(a.copy(year=null)),ContentIdentity.key(b.copy(year=null)))
        assertNotEquals(ContentIdentity.key(a),ContentIdentity.key(a.copy(ref=a.ref.copy(kind=ContentKind.SERIES))))
    }
    @Test fun imdbEvidenceAndJsonSurviveRoundTrip() {
        val c=Card(ContentRef("hdfilmdelisi","https://hdfilmdelisi.one/film/backrooms"),"Backrooms",year=2026,imdb=6.1,imdbId="tt22084616",aliases=listOf("Işık"))
        assertEquals(c,Contracts.readCard(Contracts.card(c)))
        assertTrue(ContentIdentity.key(c).contains("tt22084616"))
    }
    @Test fun episodesWithTheSameLabelDoNotMergeAcrossDifferentShows() {
        val ref=ContentRef("hdfilmdelisi","https://hdfilmdelisi.one/dizi/a/sezon-1/bolum-1",ContentKind.EPISODE)
        val a=Card(ref,"Pilot",year=2026,seriesIdentity="SERIES:2026:show a",episodeNumber=EpisodeNumber(1,1))
        val b=a.copy(ref=ref.copy(pageUrl="https://hdfilmdelisi.one/dizi/b/sezon-1/bolum-1"),seriesIdentity="SERIES:2026:show b")
        assertEquals(2,ContentIdentity.merge(listOf(a,b)).size)
        assertNotEquals(ContentIdentity.key(a),ContentIdentity.key(b))
    }
    @Test fun newlyEnrichedImdbEvidenceMergesButContradictingIdsDoNot() {
        val a=Card(ContentRef("filmcibaba","https://filmizle.ink/a/"),"Işık",year=2024)
        val b=a.copy(ref=ContentRef("lovefilmizle","https://lovefilmizle.net/b/"),imdbId="tt1234567")
        assertEquals(1,ContentIdentity.merge(listOf(a,b)).size)
        val c=b.copy(ref=ContentRef("filmizlebabam","https://www.filmizlebabam.com/c/"),imdbId="tt7654321")
        assertEquals(3,ContentIdentity.merge(listOf(a,b,c)).size)
    }
    @Test fun literalReaderRejectsCodeAndInvalidEncoding() {
        for(s in listOf("alert('x')","(()=>{})()","{file:fetch('https://bad.test/')}","'\\xzz'","'\\u000x'")) {
            assertThrows(Exception::class.java){Literals.read(s,0)}
        }
        assertThrows(Exception::class.java){Literals.base64("abc!!!")}
        assertEquals("Işık",Literals.b64("ScWfxLFr"))
        assertNull(publicUrl(""));assertNull(publicUrl("", "https://filmizle.ink/"))
        assertNull(publicUrl("file:///etc/passwd"));assertNull(publicUrl("https://user:pass@example.test/"))
        assertNull(publicUrl("http://127.0.0.1/"))
    }
    @Test fun numberedOptionsRemainOneFilmAndDoNotIncludeUnrelatedPages() {
        val site=Sites.get("filmcibaba");val ref=ContentRef(site.id,site.base+"/the-gorge-2025-izle/")
        val html="""<div class="p-v12-ep-list"><a class="post-page-numbers" href="/the-gorge-2025-izle/">1</a><a class="post-page-numbers" href="/the-gorge-2025-izle/2/">2</a><a class="post-page-numbers" href="/other-film/2/">wrong</a></div>"""
        val options=SourceMenus.parse(site,html,ref,site.base+"/the-gorge-2025-izle/2/")
        assertEquals(2,options.size);assertTrue(options.all{it.content==ref})
        assertEquals(2,options.map{it.url}.distinct().size)
    }
    @Test fun resolverCancellationCancelsOnlyItsOwnMetadataWork()=runBlocking {
        val entered=CompletableDeferred<Unit>();val cancelled=AtomicBoolean()
        val network=object:WebTransport {
            override suspend fun request(request:WebRequest):WebResponse {entered.complete(Unit);try{delay(60000);error("unreachable")}finally{cancelled.set(true)}}
        }
        val option=SourceOption("slow",ContentRef("filmcibaba","https://filmizle.ink/slow/"),"Slow","https://hotstream.club/embed/slow")
        val task=launch{SourceResolver(network).resolve(option)}
        entered.await();task.cancelAndJoin();assertTrue(cancelled.get())
        assertTrue(isActive)
    }
    @Test fun unavailableOptionsStillFailBeforeAnyRequest()=runBlocking {
        var requests=0
        val network=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{requests++;error("unexpected")}}
        val option=SourceOption("restricted",ContentRef("ddizi","https://www.ddizi.tel/izle/1/x.htm"),"Kaynak","https://www.youtube.com/embed/5SAwvBpQuIw",state=OptionState.ACCESS_REQUIRED,reason="Üye girişi gerekli")
        try{SourceResolver(network).resolve(option);fail("Expected access failure")}catch(e:DataFailure){assertEquals(FailureKind.ACCESS,e.kind)}
        assertEquals(0,requests)
    }
    @Test fun ddiziPostUsesRealAramaFieldAndDiziboxIndexFetchesOnce()=runBlocking {
        val requests=mutableListOf<WebRequest>()
        val network=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{requests.add(request);return WebResponse(request.url,if(request.url.contains("dizibox"))"""<ul class="alphabetical-category-list"><a href="/diziler/dark/">Dark</a></ul>""" else """<div class="dizi-boxpost-cat"><a href="/diziler/1815/dark-hd"><h2>Dark</h2></a></div>""")}}
        NativeSiteProvider(Sites.get("ddizi"),network,Dispatchers.Default).search("Dark")
        assertEquals("POST",requests[0].method);assertEquals(mapOf("arama" to "Dark"),requests[0].fields)
        requests.clear()
        assertEquals(1,NativeSiteProvider(Sites.get("dizibox"),network,Dispatchers.Default).search("Dark").cards.size)
        assertEquals(1,requests.size)
    }
    @Test fun sinefilmBrowserUserAgentDoesNotLeakIntoOkRu()=runBlocking {
        val metadata=JSONObject().put("movie",JSONObject().put("movieId","123").put("status","OK"))
            .put("ondemandHls","https://video.example.test/master.m3u8")
        val options=JSONObject().put("flashvars",JSONObject().put("metadata",metadata.toString()))
        val network=object:WebTransport{override suspend fun request(request:WebRequest)=WebResponse(request.url,"<div data-options='"+options+"'></div>")}
        val source=SourceResolver(network).resolve(SourceOption("ok",ContentRef("filmifullizle","https://www.filmifullizle.news/film/test.html"),"OK","https://ok.ru/videoembed/123"))
        assertEquals("Mozilla/5.0",source.userAgent)
    }
}
