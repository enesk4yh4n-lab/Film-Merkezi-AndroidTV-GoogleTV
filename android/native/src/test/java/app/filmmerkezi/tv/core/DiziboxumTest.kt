package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.json.*
import app.filmmerkezi.tv.settings.SettingsCodec

class DiziboxumTest {
    private val site=Sites.get("diziboxum")
    private val seriesUrl=site.base+"/dizi/tulsa-king-val2h-1046/"
    private val episodeUrl=site.base+"/tulsa-king-1-sezon-2-bolum-9b0sw-16021/"
    private val embed="https://ksdpictures.site/dzembed.php?id=731b18d9-6a7a-4df4-bb33-8d0d858acde0"
    private fun fixture(name:String)=checkNotNull(javaClass.getResource("/diziboxum/$name.html")).readText()
    private fun series()=DiziboxumParser.detail(site,fixture("series"),Card(ContentRef(site.id,seriesUrl,ContentKind.SERIES),"Tulsa King"))
    @Test fun homeArchiveSearchAndRealPagination(){
        val home=DiziboxumParser.list(site,fixture("home"),site.base+"/")
        val archive=DiziboxumParser.list(site,fixture("archive"),site.base+"/diziler/")
        val search=DiziboxumParser.list(site,fixture("search"),site.base+"/?s=tulsa+king")
        assertTrue(home.cards.isNotEmpty());assertTrue(archive.cards.isNotEmpty());assertTrue(home.cards.all{it.ref.kind==ContentKind.SERIES})
        val c=search.cards.single();assertEquals("Tulsa King",c.title);assertEquals(2022,c.year);assertEquals(7.9,c.imdb!!,0.001)
        assertEquals(site.base+"/diziler/?sayfa=2",archive.next!!.url)
        println("Diziboxum: home=${home.cards.size}, archive=${archive.cards.size}, search=${search.cards.size}")
    }
    @Test fun allDeclaredSeasonsAndParentIdentityAreKeptWithoutPlayingTheTrailer(){
        val d=series();assertEquals(33,d.episodes.size);assertEquals(listOf(1,2,3,4),d.seasons);assertEquals(2022,d.card.year)
        assertTrue(d.options.isEmpty());assertEquals("Tulsa King",d.card.title)
        assertTrue(d.episodes.all{it.card.seriesRef==d.card.ref&&it.card.seriesIdentity==ContentIdentity.key(d.card)})
        assertEquals(episodeUrl,d.episodes.single{it.number==EpisodeNumber(1,2)}.card.ref.pageUrl)
        val verified=PageIdentity.attach(fixture("series"),d).card
        assertTrue(verified.identityEvidence!!.typeVerified);assertTrue(verified.identityEvidence!!.yearVerified)
        assertTrue(ContentIdentity.equivalent(verified,Card(ContentRef("hdfilmdelisi","https://hdfilmdelisi.one/dizi/tulsa-king",ContentKind.SERIES),"Tulsa King",year=2022)))
    }
    @Test fun episodeUsesItsDeclaredShowAndNumberAndIgnoresPublicationYear(){
        val d=DiziboxumParser.detail(site,fixture("episode"),Card(ContentRef(site.id,episodeUrl,ContentKind.EPISODE),"Seed"))
        assertEquals(ContentKind.EPISODE,d.card.ref.kind);assertEquals(EpisodeNumber(1,2),d.card.episodeNumber)
        assertEquals(seriesUrl,d.card.seriesRef!!.pageUrl);assertEquals("Tulsa King",d.card.title);assertNull(d.card.year)
        assertEquals(1,d.options.size);assertEquals(embed,d.options.single().url)
        try{DiziboxumParser.detail(site,fixture("episode"),d.card.copy(episodeNumber=EpisodeNumber(2,2)));fail("Wrong season must be rejected")}catch(_:DataFailure){}
    }
    @Test fun metadataInventoryAndLazyResolutionExecuteNoRemoteScriptAndNoMediaFetch()=runBlocking {
        val requests=mutableListOf<WebRequest>()
        val transport=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
            requests.add(request);return when(request.url){episodeUrl->WebResponse(request.url,fixture("episode"));embed->WebResponse(request.url,fixture("katre"));else->error("Unexpected network: "+request.url)}
        }}
        val provider=NativeSiteProvider(site,transport,Dispatchers.Default)
        val d=provider.detail(Card(ContentRef(site.id,episodeUrl,ContentKind.EPISODE),"Tulsa King"))
        assertEquals(2,requests.size);assertEquals(1,d.options.size);assertNotNull(d.options.single().fields["katreStream"])
        val source=SourceResolver(transport).resolve(d.options.single())
        assertEquals(3,requests.size);assertEquals(embed,requests.last().url);assertEquals(episodeUrl,requests.last().referer)
        assertEquals(StreamType.HLS,source.type);assertEquals(embed,source.referer);assertTrue(source.url.endsWith("/master.m3u8"))
        assertEquals(setOf("tr","en"),source.subtitles.map{it.language}.toSet())
        val before=requests.size;assertTrue(provider.browse(ContentKind.MOVIE).cards.isEmpty());assertEquals(before,requests.size)
        Unit
    }
    @Test fun everyDeclaredKatreStreamGetsAnIndependentStableSourceOption(){
        val config=Literals.after(fixture("katre"),"\\bwindow\\.NP_CONFIG\\s*=\\s*")as JSONObject
        val rows=config.getJSONArray("sources");val alt=JSONObject(rows.getJSONObject(0).toString()).put("title","Alternatif").put("kind","alternate")
        alt.put("master",alt.getString("master").replace("master.m3u8","alternate.m3u8"));rows.put(alt)
        val body="window.NP_CONFIG = "+config+";";val parsed=KatrePlayer.parse(body,embed)
        val base=SourceOption("sample",ContentRef(site.id,episodeUrl,ContentKind.EPISODE),"Katre Player",embed)
        val options=KatrePlayer.options(base,parsed);assertEquals(2,options.size);assertEquals(2,options.map{it.id}.distinct().size)
        assertTrue(KatrePlayer.resolve(options[1],body,embed).url.endsWith("alternate.m3u8"))
        try{KatrePlayer.parse(body.replace("embed-731b18d9","embed-11111111"),embed);fail("Mismatched video must be rejected")}catch(_:DataFailure){}
    }
    @Test fun sourceMenuKeepsAllOwnedPlayersAndPartsButNotUnrelatedLinks(){
        val ref=ContentRef(site.id,episodeUrl,ContentKind.EPISODE)
        val html="<div class='r-ep-kaynak'>Katre Player</div><div class='video-container'><iframe src='$embed'></iframe><iframe src='https://vidmoly.biz/embed-abc.html'></iframe><iframe src='https://unknown.example/player'></iframe></div>"+
            "<div class='page-links'><a href='${episodeUrl}2/'>İkinci kaynak</a><a href='/other-1-sezon-2-bolum/'>Unrelated</a></div><iframe src='https://www.youtube.com/embed/trailer'></iframe>"
        val options=SourceMenus.parse(site,html,ref);assertEquals(4,options.size);assertEquals(1,options.count{it.state==OptionState.UNSUPPORTED})
        assertTrue(options.none{it.url.contains("youtube")||it.url.contains("/other-")});assertTrue(options.any{it.url==episodeUrl+"2/"})
    }
    @Test fun addingTheSitePreservesExistingSettingsAndEnablesItsIndependentSwitches(){
        val oldOrder=Sites.all.filter{it.id!=site.id}.joinToString("|"){it.id}
        val read=SettingsCodec.read(mapOf("siteOrder" to oldOrder,"disabledCatalog" to setOf("ddizi"),"disabledSources" to setOf("dizibox")))
        assertEquals(site.id,read.siteOrder.last());assertTrue(site.id in read.catalog().sites);assertTrue(read.sourceAllowed(site.id))
        assertTrue("ddizi" in read.disabledCatalog);assertFalse(read.sourceAllowed("dizibox"))
    }
}
