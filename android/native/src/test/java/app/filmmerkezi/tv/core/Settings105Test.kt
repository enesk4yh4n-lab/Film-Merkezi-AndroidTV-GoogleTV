package app.filmmerkezi.tv.core

import app.filmmerkezi.tv.settings.*
import app.filmmerkezi.tv.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.last
import org.junit.Assert.*
import org.junit.Test

class Settings105Test {
    @Test fun legacyPreferencesAndInvalidTypesHaveBoundedNonDestructiveDefaults(){
        val old=mapOf("audio" to "de","textEnabled" to false,"height" to 1080,"automaticSources" to false,"diagnostics" to true)
        val s=SettingsCodec.read(emptyMap(),old,mapOf("siteId" to "dizibox"))
        assertEquals("de",s.player.audio);assertEquals(SubtitlePolicy.OFF,s.player.subtitles);assertEquals(1080,s.player.preferredHeight)
        assertEquals(0,s.player.autoMaxHeight);assertEquals(0,s.player.autoMaxFps);assertFalse(s.automaticSources);assertTrue(s.diagnostics)
        assertEquals("dizibox",s.selectedSite);assertTrue(s.hideAdult);assertTrue(s.merge)
        val bad=SettingsCodec.read(mapOf("merge" to "false","buffer" to "invalid","autoMaxFps" to 1000,"audio" to "not a language","siteOrder" to "unknown|dizibox|dizibox"))
        assertTrue(bad.merge);assertEquals(0,bad.player.autoMaxFps);assertEquals("tr",bad.player.audio)
        assertEquals(Sites.all.size,bad.siteOrder.size);assertEquals("dizibox",bad.siteOrder.first())
    }
    @Test fun allTypedPreferencesRoundTrip(){
        val s=AppSettings(largeText=true,hiddenRows=setOf("movies"),merge=false,disabledCatalog=setOf("ddizi"),disabledSources=setOf("dizibox"),
            player=PlaybackSettings(autoMaxFps=30,autoMaxHeight=720,customSubtitles=true,buffer=BufferProfile.LONG,seekSeconds=15))
        assertEquals(s,SettingsCodec.read(SettingsCodec.fields(s)))
    }
    @Test fun separateCardKeysDoNotChangeWorkOrRemoveOtherSites(){
        val a=Card(ContentRef("filmcibaba","https://filmizle.ink/test/"),"Film",year=2025,workId="work:stable")
        val b=a.copy(ref=ContentRef("filmmakinesi","https://filmmakinesi.to/film/test/"))
        val group=UnifiedItem("work:stable",a,listOf(a,b));val policy=CatalogRequestOptions(merge=false)
        val split=CatalogPresentation.project(listOf(group),policy)
        assertEquals(2,split.size);assertEquals(2,split.map{it.displayKey}.distinct().size)
        assertTrue(split.all{it.identity=="work:stable"&&it.card.workId=="work:stable"})
        assertEquals(1,CatalogPresentation.project(listOf(group),policy.copy(merge=true)).size)
        assertEquals(1,CatalogPresentation.project(listOf(group),policy.copy(sites=listOf("filmcibaba"))).size)
        assertEquals(split.map{it.displayKey},app.filmmerkezi.tv.tv.stableContentOrder(split,split.reversed()).map{it.displayKey})
    }
    @Test fun eroticEvidenceIsPrimaryAndDoesNotUseSeoAgeOrCss(){
        val c=Card(ContentRef("filmmakinesi","https://filmmakinesi.to/film/fixture/"),"Fixture")
        val noise="<title>Erotik Film izle</title><style>.adult-blur-card{opacity:0}</style><nav><a>Erotik</a></nav>"
        val basic=noise+"<h1>Fixture</h1><script type='application/ld+json'>{\"@type\":\"Movie\",\"name\":\"Fixture\",\"contentRating\":\"18+\",\"genre\":\"Korku\"}</script>"
        assertFalse(AdultContent.blocked(AdultContent.detail(basic,Detail(c)).card))
        val adult=AdultContent.detail(basic+"<div id='info--box'><div class='type'><a>Erotik Filmler</a></div></div>",Detail(c)).card
        assertTrue(AdultContent.blocked(adult));assertEquals(c.ref.pageUrl,adult.adultEvidence!!.page)
        val group=UnifiedItem("work:a",c,listOf(c,adult))
        assertTrue(CatalogPresentation.project(listOf(group),CatalogRequestOptions(hideAdult=true)).isEmpty())
        assertEquals(1,CatalogPresentation.project(listOf(group),CatalogRequestOptions(hideAdult=false)).size)
        assertTrue(Contracts.readCard(Contracts.card(adult)).adultEvidence!!.confirmed)
    }
    @Test fun disabledSitesMakeNoRequestAndSettingsArePartOfCacheKey()=runBlocking {
        var calls=0;var options=CatalogRequestOptions(sites=emptyList())
        val catalog=recordedCatalog("filmmakinesi")
        val transport=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{calls++;return WebResponse(request.url,catalog)}}
        val repo=CatalogRepository(transport,requestOptions={options})
        assertEquals(0,repo.observeBrowse().last().requestedSites);assertEquals(0,calls)
        options=options.copy(sites=listOf("filmmakinesi"));assertTrue(repo.observeBrowse().last().items.isNotEmpty());assertEquals(1,calls)
        repo.observeBrowse().last();assertEquals(1,calls)
        options=options.copy(merge=false);repo.observeBrowse().last();assertEquals(2,calls)
    }
}
