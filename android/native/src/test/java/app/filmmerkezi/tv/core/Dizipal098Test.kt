package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import okhttp3.HttpUrl.Companion.toHttpUrl

class Dizipal098Test {
    private val site=Sites.get("dizipal")
    private val page=site.base+"/diziler/house-of-the-dragon"
    private val episode=site.base+"/bolumler/house-of-the-dragon-s1b3"
    private fun fixture(name:String)=checkNotNull(javaClass.getResource("/dizipal098/$name")).readText()
    private fun seed()=Card(ContentRef(site.id,page,ContentKind.SERIES),"House of the Dragon")
    @Test fun searchAndBothArchivesAreNativeAndPaginated() {
        val parser=CatalogParser(site);val search=parser.list(fixture("search.json"),site.base+site.search+"House")
        assertEquals(4,search.cards.size);val house=search.cards.single{it.title=="House of the Dragon"}
        assertEquals(ContentKind.SERIES,house.ref.kind);assertEquals(2022,house.year)
        for((file,kind)in listOf("series-list.html" to ContentKind.SERIES,"movies-list.html" to ContentKind.MOVIE)){
            val result=parser.list(fixture(file),site.base+if(kind==ContentKind.SERIES)"/diziler"else"/filmler")
            assertEquals(if(kind==ContentKind.SERIES)18 else 24,result.cards.size);assertTrue(result.cards.all{it.ref.kind==kind&&it.title.isNotBlank()&&it.poster.startsWith("https://")})
            assertEquals(2,result.next!!.page);assertTrue(result.next!!.url.endsWith("?page=2"))
        }
    }
    @Test fun primarySeriesHasAllSeasonsAndVerifiedReleaseYear(){
        val seed=seed();val html=fixture("series.html");val detail=PageIdentity.attach(html,CatalogParser(site).detail(html,seed),seed)
        assertEquals(listOf(1,2,3),detail.seasons);assertEquals(listOf(10,8,8),detail.episodes.groupBy{it.number.season}.values.map{it.size})
        assertEquals(2022,detail.card.year);assertTrue(detail.card.identityEvidence!!.yearVerified)
        assertEquals("House of the Dragon",detail.card.originalTitle);assertTrue(detail.options.isEmpty())
        assertTrue(detail.episodes.all{it.series==seed.ref&&it.card.seriesRef==seed.ref})
    }
    @Test fun episodeHasParentAndOnlyItsActiveSource(){
        val raw=Card(ContentRef(site.id,episode,site.kind(episode)),"House")
        val h=fixture("episode.html");val d=PageIdentity.attach(h,CatalogParser(site).detail(h,raw),raw)
        assertEquals(ContentKind.EPISODE,d.card.ref.kind);assertEquals(EpisodeNumber(1,3),d.card.episodeNumber)
        assertEquals(page,d.card.seriesRef!!.pageUrl);assertEquals(1,d.options.size)
        assertEquals("https://streamcorecdn.com/dizi/94997/1/3",d.options.single().url);assertEquals(OptionState.AVAILABLE,d.options.single().state)
    }
    @Test fun houseMergesWithVerifiedExistingSiteButDifferentEpisodeCannot(){
        val html=fixture("series.html");val checked=PageIdentity.attach(html,CatalogParser(site).detail(html,seed()),seed()).card
        val otherSeed=Card(ContentRef("hdfilmdelisi","https://hdfilmdelisi.one/dizi/house-of-the-dragon-izle",ContentKind.SERIES),"House of the Dragon")
        val otherHtml=checkNotNull(javaClass.getResource("/series097/delisi-house.html")).readText()
        val other=PageIdentity.attach(otherHtml,CatalogParser(Sites.get("hdfilmdelisi")).detail(otherHtml,otherSeed),otherSeed).card
        assertEquals(1,ContentIdentity.merge(listOf(checked,other)).size)
        val eps=CatalogParser(site).detail(html,seed()).episodes
        assertFalse(ContentIdentity.equivalent(eps[0].card,eps[1].card))
    }
    @Test fun recommendationsAndForeignEpisodeLinksCannotContaminateSeasons(){
        val html=fixture("series.html").replace("</body>","<a href='/bolumler/another-show-s9b99'>9. Sezon 99. Bölüm</a></body>")
        assertEquals(26,CatalogParser(site).detail(html,seed()).episodes.size)
        try{CatalogParser(site).detail(html,seed().copy(ref=seed().ref.copy(pageUrl=site.base+"/diziler/other")));fail("Mismatched primary accepted")}catch(e:DataFailure){assertEquals(FailureKind.PARSE,e.kind)}
    }
    @Test fun inactiveAndUnknownSourcesHaveHonestStates(){
        val rows=JSONArray().put(JSONObject().put("id",1).put("is_active",false).put("embed_url","https://streamcorecdn.com/dizi/1/1/1"))
            .put(JSONObject().put("id",2).put("is_active",true).put("embed_url","https://unknown.invalid/embed/1"))
        val html="<script>self.__next_f.push("+JSONArray().put(1).put("1:"+JSONObject().put("videoSources",rows)+"\n")+")</script>"
        val out=SourceMenus.parse(site,html,ContentRef(site.id,episode,ContentKind.EPISODE));assertEquals(1,out.size);assertEquals(OptionState.UNSUPPORTED,out.single().state)
    }
    @Test fun streamcoreReadsMainManifestInsteadOfEarlierAdvertisement(){
        val embed="https://streamcorecdn.com/dizi/94997/1/3";val body=fixture("streamcore-episode.html")
        val config=StreamCore.parse(body,embed);val url=config.url.toHttpUrl()
        assertEquals("streamcorecdn.com",url.host);assertEquals("2718",url.queryParameter("id"));assertEquals("master",url.queryParameter("m"))
        val sub=config.subtitles.single();assertEquals("tur",sub.language);assertEquals("vtt",sub.format)
        val subtitle=sub.url.toHttpUrl();assertNull(subtitle.queryParameter("m"));assertEquals("tracks/sub_1/subtitles.vtt",subtitle.queryParameter("p"));assertEquals(url.queryParameter("token"),subtitle.queryParameter("token"))
    }
    @Test fun streamcoreRejectsWrongIdentityAndOffsiteMaster(){
        val body=fixture("streamcore-episode.html");val page="https://streamcorecdn.com/dizi/94997/1/3"
        for(bad in listOf(body.replace("const videoId = 2718","const videoId = 9999"),body.replace("/play.m3u8?id=2718&m=master","https://wrong.invalid/play.m3u8?id=2718&m=master"))){
            try{StreamCore.parse(bad,page);fail("Invalid configuration accepted")}catch(e:DataFailure){assertEquals(FailureKind.PARSE,e.kind)}
        }
    }
    @Test fun providerUsesRealSearchApiAndKeepsPaginationOnOwnHost()=runBlocking{
        val calls=mutableListOf<WebRequest>();val transport=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{calls.add(request);return WebResponse(request.url,fixture(if(request.url.contains("/api/"))"search.json"else"series-list.html"))}}
        val provider=NativeSiteProvider(site,transport,Dispatchers.Default)
        provider.search("House of the Dragon");assertEquals("/api/v1/search",calls.single().url.toHttpUrl().encodedPath);assertEquals("House of the Dragon",calls.single().url.toHttpUrl().queryParameter("q"))
        val first=provider.browse(ContentKind.SERIES);provider.browse(ContentKind.SERIES,first.next);assertTrue(calls.last().url.endsWith("/diziler?page=2"))
        assertNull(site.catalogUrl("https://another.invalid/diziler/house-of-the-dragon"))
    }
    @Test fun resolverPreservesEmbedRefererAndNativeSubtitleMetadata()=runBlocking{
        val embed="https://streamcorecdn.com/dizi/94997/1/3";val calls=mutableListOf<WebRequest>()
        val resolver=SourceResolver(object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{calls.add(request);return WebResponse(embed,fixture("streamcore-episode.html"))}})
        val source=resolver.resolve(SourceOption("dizipal-test",ContentRef(site.id,episode,ContentKind.EPISODE),"StreamCore",embed))
        assertEquals(episode,calls.single().referer);assertEquals(embed,source.referer);assertTrue(source.userAgent.isNotBlank());assertNotNull(source.expiresAt);assertEquals(StreamType.HLS,source.type)
        assertEquals(1,source.subtitles.size)
    }
    @Test fun nonstandardEpisodeSlugRequiresExplicitParentEvidence(){
        val body=fixture("series.html");val detail=CatalogParser(site).detail(body,seed())
        assertTrue(detail.episodes.single{it.number==EpisodeNumber(3,2)}.card.ref.pageUrl.endsWith("/bolumler/s3b2-2-bolum"))
        // Strip this row's explicit parent identity, keeping the DOM link. It may
        // not be accepted based on the season label alone.
        val escaped=body.replace("\\\"slug\\\":\\\"house-of-the-dragon\\\"","\\\"slug\\\":\\\"another-show\\\"")
        val eps=CatalogParser(site).episodes(HtmlData.document(escaped,page),seed())
        assertFalse(eps.any{it.number==EpisodeNumber(3,2)})
    }
    @Test fun movieMetadataAndSecondSeasonInventoryRecognizeVideoPlays(){
        val url=site.base+"/filmler/ninho-le-prix-a-payer";val raw=Card(ContentRef(site.id,url),"Ninho")
        val html=fixture("movie.html");val movie=PageIdentity.attach(html,CatalogParser(site).detail(html,raw),raw)
        assertEquals(ContentKind.MOVIE,movie.card.ref.kind);assertEquals(2026,movie.card.year);assertTrue(movie.card.identityEvidence!!.yearVerified)
        assertTrue(movie.options.single().label.startsWith("VideoPlays"));assertEquals(OptionState.AVAILABLE,movie.options.single().state)
        val options=SourceMenus.parse(site,fixture("episode-s2b1.html"),ContentRef(site.id,site.base+"/bolumler/house-of-the-dragon-s2b1",ContentKind.EPISODE))
        assertEquals("https://videoplays.cfd/player.html?video=151",options.single().url)
    }
    private fun videoPlaysTransport(badIdentity:Boolean=false)=object:WebTransport{
        val requests=mutableListOf<WebRequest>()
        override suspend fun request(request:WebRequest):WebResponse {
            requests.add(request);val path=request.url.toHttpUrl().encodedPath
            val file=when{path=="/player.html"->"videoplays-s2b1.html";path=="/api/videos/151"->"video151.json";path=="/api/videos/151/token"->"video151-token.json";else->error("Unexpected request")}
            val body=fixture(file).let{if(badIdentity&&file=="video151-token.json")JSONObject(it).put("video_id",999).toString()else it}
            return WebResponse(request.url,body)
        }
    }
    @Test fun videoPlaysUsesPublicTokenWithActualParentAndBothSubtitleLanguages()=runBlocking {
        val net=videoPlaysTransport();val page=site.base+"/bolumler/house-of-the-dragon-s2b1";val embed="https://videoplays.cfd/player.html?video=151"
        val source=SourceResolver(net).resolve(SourceOption("vp",ContentRef(site.id,page,ContentKind.EPISODE),"VideoPlays",embed))
        assertEquals("storagedomain.cfd",source.url.toHttpUrl().host);assertEquals(listOf("tr","en"),source.subtitles.map{it.language});assertEquals(embed,source.referer);assertNotNull(source.expiresAt)
        assertEquals(page,net.requests.last().url.toHttpUrl().queryParameter("embed_referrer"));assertEquals(3,net.requests.size)
    }
    @Test fun videoPlaysRejectsTokenForAnotherVideo()=runBlocking {
        try{SourceResolver(videoPlaysTransport(true)).resolve(SourceOption("vp",ContentRef(site.id,episode,ContentKind.EPISODE),"VideoPlays","https://videoplays.cfd/player.html?video=151"));fail("Wrong token accepted")}
        catch(e:DataFailure){assertEquals(FailureKind.PARSE,e.kind)}
    }
    @Test fun realSecondSeasonFourthEpisodeHasNoSourceAndMustNotInventOne(){
        val ref=ContentRef(site.id,site.base+"/bolumler/house-of-the-dragon-s2b4",ContentKind.EPISODE)
        val detail=CatalogParser(site).detail(fixture("missing-source.html"),Card(ref,"House of the Dragon"))
        assertEquals(EpisodeNumber(2,4),detail.card.episodeNumber);assertTrue(detail.options.isEmpty())
    }
}
