package app.filmmerkezi.tv.core

import app.filmmerkezi.tv.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.last
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SiteAddressesTest {
    @After fun restore(){Sites.configure(emptyMap())}
    private fun fixture(name:String)=checkNotNull(javaClass.getResource("/diziboxum/$name.html")).readText()
    private fun move(id:String="diziboxum",url:String="https://new-series.example",name:String="Yeni Dizi")=
        SiteAddresses.change(emptyMap(),id,url,name).also(Sites::configure)
    @Test fun onlyPublicHttpsOriginsAreAccepted(){
        assertEquals("https://example.com",SiteAddresses.normalize("  EXAMPLE.com/  "))
        assertEquals("https://www.example.com",SiteAddresses.normalize("https://www.example.com:443/"))
        for(value in listOf("","http://example.com","https://user@example.com","https://127.0.0.1","localhost","https://x.local","https://example.com/film/","https://example.com/?token=a","https://example.com/#part","https://example.com:444")){
            try{SiteAddresses.normalize(value);fail(value)}catch(_:IllegalArgumentException){}
        }
    }
    @Test fun historyAndSettingsSurviveRepeatedChangesAndReset(){
        val first=move();val next=SiteAddresses.change(first,"diziboxum","second.example","İkinci ad");Sites.configure(next)
        assertEquals("https://second.example/dizi/test/?s=a",Sites.get("diziboxum").catalogUrl("https://new-series.example/dizi/test/?s=a"))
        val s=AppSettings(siteAddresses=next,automaticSources=false,disabledCatalog=setOf("ddizi"))
        assertEquals(s,SettingsCodec.read(SettingsCodec.fields(s)))
        val reset=SiteAddresses.reset(next,"diziboxum");Sites.configure(reset)
        assertEquals("Diziboxum",Sites.get("diziboxum").name)
        assertEquals("https://diziboxum.com/dizi/test/",Sites.get("diziboxum").catalogUrl("https://second.example/dizi/test/"))
    }
    @Test fun everySiteKeepsSavedIdentityParentAndPosterAfterMoving(){
        val saved=Sites.defaults.associate{site->site.id to Card(ContentRef(site.id,site.base+"/test-1-sezon-2-bolum/",ContentKind.EPISODE),"Deneme",poster=site.base+"/poster.jpg",workId="work:${site.id}",seriesIdentity="series:${site.id}",seriesRef=ContentRef(site.id,site.base+"/dizi/test/",ContentKind.SERIES),episodeNumber=EpisodeNumber(1,2))}
        val maps=Sites.defaults.fold(emptyMap<String,SiteAddress>()){all,s->SiteAddresses.change(all,s.id,"https://${s.id}.example",s.name)};Sites.configure(maps)
        for((id,c)in saved){val read=Contracts.readCard(Contracts.card(c));val base="https://$id.example"
            assertEquals(c.ref.id,read.ref.id);assertEquals(c.workId,read.workId);assertEquals(c.seriesIdentity,read.seriesIdentity);assertEquals(c.episodeNumber,read.episodeNumber)
            assertTrue(read.ref.pageUrl.startsWith(base));assertTrue(read.seriesRef!!.pageUrl.startsWith(base));assertEquals(base+"/poster.jpg",read.poster)
            assertNull(Sites.get(id).catalogUrl("https://$id.example.evil.test/test/"))
        }
    }
    @Test fun anotherProvidersAddressCannotBeAssigned(){
        try{SiteAddresses.change(emptyMap(),"diziboxum",Sites.get("dizibox").base,"Yanlış");fail()}catch(_:IllegalArgumentException){}
        try{SiteAddresses.change(emptyMap(),"diziboxum","https://www.dizibox.live/","Yanlış");fail()}catch(_:IllegalArgumentException){}
        try{SiteAddresses.change(emptyMap(),"diziboxum","https://new.example/","\n");fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun anExistingProviderRefreshesDetailPaginationAndSourceContext()=runBlocking {
        val original=Sites.get("diziboxum");val calls=mutableListOf<WebRequest>()
        val transport=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
            calls.add(request);val kind=when{KatrePlayer.accepts(request.url)->"katre";"/dizi/" in request.url->"series";"-bolum-" in request.url->"episode";else->"archive"}
            return WebResponse(request.url,fixture(kind))
        }}
        val provider=NativeSiteProvider(original,transport,Dispatchers.Default)
        val seed=Card(ContentRef(original.id,original.base+"/tulsa-king-1-sezon-2-bolum-9b0sw-16021/",ContentKind.EPISODE),"Tulsa King")
        val old=provider.detail(seed);val before=calls.size;move()
        val fresh=provider.detail(seed);assertTrue(calls.size>before);assertEquals(seed.ref.id,fresh.card.ref.id)
        assertTrue(fresh.card.ref.pageUrl.startsWith("https://new-series.example/"))
        assertTrue(calls[before].url.startsWith("https://new-series.example/"));assertEquals("https://new-series.example/",calls[before].referer)
        val cursor=PageCursor(original.id,original.base+"/diziler/?sayfa=2")
        provider.browse(ContentKind.SERIES,cursor);assertTrue(calls.last().url.startsWith("https://new-series.example/"))
        SourceResolver(transport).resolve(old.options.single());assertTrue(calls.last().referer.startsWith("https://new-series.example/"))
        Unit
    }
    @Test fun allProvidersRebaseLazySourcesAndRetainTheCorrectUserAgent()=runBlocking {
        for(original in Sites.defaults){
            Sites.configure(SiteAddresses.change(emptyMap(),original.id,"https://${original.id}.example",original.name))
            val calls=mutableListOf<WebRequest>();val network=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{calls.add(request);return WebResponse(request.url,"",headers=mapOf("Content-Type" to "video/mp4"))}}
            val ref=ContentRef(original.id,original.base+"/test/");val source=SourceResolver(network).resolve(SourceOption("test",ref,"Video",original.base+"/clip.mp4"))
            assertEquals("https://${original.id}.example/clip.mp4",source.url);assertEquals("https://${original.id}.example/test/",source.referer)
            assertEquals(app.filmmerkezi.tv.SiteHttpPolicy.forProvider(original.id),source.userAgent)
            assertEquals(source.url,calls.single().url);assertEquals(source.referer,calls.single().referer)
        }
        Unit
    }
    @Test fun repositoryDoesNotReuseOldCatalogCacheAfterAddressChange()=runBlocking {
        var requests=0;val transport=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{requests++;return WebResponse(request.url,fixture("search"))}}
        val repo=CatalogRepository(transport,requestOptions={CatalogRequestOptions(sites=listOf("diziboxum"))})
        val old=repo.observeSearch("Tulsa").last();assertEquals(1,requests)
        repo.observeSearch("Tulsa").last();assertEquals(1,requests)
        move();val fresh=repo.observeSearch("Tulsa").last();assertEquals(2,requests)
        assertEquals(old.items.single().identity,fresh.items.single().identity)
        assertTrue(fresh.items.single().card.ref.pageUrl.startsWith("https://new-series.example/"))
    }
    @Test fun malformedSavedOverridesFallBackWithoutChangingOtherSettings(){
        val read=SettingsCodec.read(mapOf("schema" to 2,"siteAddresses" to "{bad","merge" to false,"selectedSite" to "ddizi"))
        assertTrue(read.siteAddresses.isEmpty());assertFalse(read.merge);assertEquals("ddizi",read.selectedSite)
    }
}
