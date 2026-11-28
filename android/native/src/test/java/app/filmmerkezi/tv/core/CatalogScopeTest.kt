package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class CatalogScopeTest {
    private fun card(site:String,n:Int)=Card(ContentRef(site,if(site=="hdfilmdelisi")"https://hdfilmdelisi.one/film/scope-$n"else Sites.get(site).base+"/scope-$n/"),"$site film $n",year=2025)
    @Test fun allConfiguredProvidersAreRepresentedBeforeOneProviderFillsTheShelf(){
        val rows=Sites.all.reversed().associate{it.id to CatalogPage((0 until 40).map{n->card(it.id,n)})}
        val merged=CatalogMerge.pages(rows)
        assertEquals(Sites.all.size*40,merged.size)
        assertEquals(Sites.all.map{it.id}.toSet(),merged.take(Sites.all.size).map{it.card.ref.siteId}.toSet())
        assertEquals(Sites.all.first().id,merged.first().card.ref.siteId)
    }
    @Test fun duplicatesRetainEverySiteAndLateBilingualEvidenceJoinsEarlierGroups(){
        val a=card("filmcibaba",0).copy(title="Yerel Başlık")
        val b=card("hdfilmdelisi",0).copy(title="Original Title")
        val bridge=card("sinemacc",0).copy(title="Yerel Başlık",originalTitle="Original Title",imdbId="tt1234567")
        val merged=ContentIdentity.merge(listOf(a,b,bridge))
        assertEquals(1,merged.size);assertEquals(3,merged.single().alternatives.size)
        assertEquals("tt1234567",merged.single().card.imdbId)
        val conflicting=b.copy(imdbId="tt7654321")
        assertEquals(3,ContentIdentity.merge(listOf(a.copy(imdbId="tt1234567"),conflicting,bridge.copy(imdbId=""))).size)
    }
    @Test fun selectedSiteDoesNotRequestOtherSitesAndUsesItsOwnCache()=runBlocking {
        val requests=CopyOnWriteArrayList<WebRequest>()
        val catalogs=mapOf("https://hdfilmdelisi.one/" to recordedCatalog("hdfilmdelisi"),
            "https://sinemacc.com/" to recordedCatalog("sinemacc"))
        val repository=CatalogRepository(object:WebTransport{
            override suspend fun request(request:WebRequest):WebResponse {
                requests.add(request)
                return WebResponse(request.url,catalogs.entries.single{request.url.startsWith(it.key)}.value)
            }
        })
        val result=repository.observeBrowse(siteId="hdfilmdelisi").last()
        assertEquals(1,requests.size);assertTrue(requests.all{it.url.startsWith("https://hdfilmdelisi.one/")})
        assertEquals(1,result.requestedSites);assertEquals(1,result.completedSites)
        assertTrue(result.failures.isEmpty());assertTrue(result.items.isNotEmpty())
        repository.observeBrowse(siteId="hdfilmdelisi").last();assertEquals(1,requests.size)
        repository.observeBrowse(siteId="sinemacc").last();assertEquals(2,requests.size)
        repository.observeSearch("Deneme",siteId="sinemacc").last();assertEquals(3,requests.size)
        assertTrue(requests.last().url.startsWith("https://sinemacc.com/"))
        Unit
    }
    @Test fun selectedSiteRejectsForeignPaginationBeforeAnyRequest()=runBlocking {
        var calls=0
        val repository=CatalogRepository(object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{calls++;error("No request expected")}})
        try{repository.observeBrowse(cursors=mapOf("sinemacc" to PageCursor("sinemacc","https://sinemacc.com/",2)),siteId="hdfilmdelisi").last();fail("Cross-site cursor")}
        catch(e:DataFailure){assertEquals(FailureKind.PARSE,e.kind)}
        assertEquals(0,calls)
        Unit
    }
}
