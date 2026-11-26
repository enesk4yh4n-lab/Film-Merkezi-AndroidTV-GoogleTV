package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.HttpUrl.Companion.toHttpUrl

class RepositoryFlowTest {
    private class Network:WebTransport {
        val active=AtomicInteger();val peak=AtomicInteger();var cancelled=0
        private val index=JSONArray(checkNotNull(javaClass.getResource("/catalog/index.json")).readText()).values().filterIsInstance<JSONObject>()
        private val bodies=Sites.all.associate{site->
            if(site.id=="dizipal")return@associate site.id to """<main><a class="group block" href="/filmler/fixture"><h3>Fixture</h3></a></main>"""
            val row=index.firstOrNull{it.string("site")==site.id&&it.string("method")!="detail"&&!it.getJSONObject("reference").has("error")}
            site.id to row?.let{checkNotNull(javaClass.getResource("/catalog/"+it.string("file"))).readText()}.orEmpty()
        }
        override suspend fun request(request:WebRequest):WebResponse {
            val running=active.incrementAndGet();peak.updateAndGet{maxOf(it,running)}
            try{
                delay(60)
                val site=Sites.all.first{it.base.toHttpUrl().host==request.url.toHttpUrl().host}
                if(site.id=="hdfilmcehennemi")throw DataFailure(FailureKind.HTTP,"HTTP 503","http",503)
                return WebResponse(request.url,bodies.getValue(site.id))
            }catch(e:CancellationException){cancelled++;throw e}
            finally{active.decrementAndGet()}
        }
    }
    @Test fun slowOrFailedSiteDoesNotHoldAllResultsAndConcurrencyIsBounded()=runBlocking {
        val network=Network();val repository=CatalogRepository(network)
        val pages=repository.observeBrowse().toList()
        assertTrue(pages.size>3)
        assertTrue(pages.dropLast(1).any{it.items.isNotEmpty()})
        assertTrue(pages.last().items.isNotEmpty())
        assertEquals(listOf("hdfilmcehennemi"),pages.last().failures.map{it.siteId})
        assertTrue(network.peak.get()<=2);assertEquals(0,network.active.get())
    }
    @Test fun cancelledScreenDoesNotPoisonTheNextQuery()=runBlocking {
        val network=Network();val repository=CatalogRepository(network)
        repository.observeBrowse().take(3).toList()
        assertEquals(0,network.active.get())
        assertTrue(repository.browse().items.isNotEmpty())
        assertTrue(network.peak.get()<=2)
    }
}
