package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class RepositoryTrimTest {
    @Test fun inFlightUnifiedRequestCannotRefillTrimmedCache()=runBlocking {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val calls=AtomicInteger()
        val catalog=recordedCatalog("hdfilmdelisi")
        val network=object:WebTransport {
            override suspend fun request(request:WebRequest):WebResponse {
                calls.incrementAndGet();entered.complete(Unit);release.await()
                return WebResponse(request.url,catalog)
            }
        }
        val repository=CatalogRepository(network,requestOptions={CatalogRequestOptions(sites=listOf("hdfilmdelisi"))})
        val pending=async{repository.browse()};entered.await();repository.trim();release.complete(Unit);pending.await()
        val before=calls.get();assertTrue(repository.browse().items.isNotEmpty());assertTrue(calls.get()>before)
        val cached=calls.get();repository.browse();assertEquals(cached,calls.get())
        repository.trim();repository.browse();assertTrue(calls.get()>cached)
        Unit
    }
    @Test fun inFlightDetailCannotRefillTrimmedCache()=runBlocking {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val calls=AtomicInteger()
        val network=object:WebTransport {
            override suspend fun request(request:WebRequest):WebResponse {
                calls.incrementAndGet();entered.complete(Unit);release.await()
                return WebResponse(request.url,"<html><h1>Trim fixture</h1></html>")
            }
        }
        val provider=NativeSiteProvider(Sites.get("hdfilmdelisi"),network,Dispatchers.Default)
        val card=Card(ContentRef("hdfilmdelisi","https://hdfilmdelisi.one/film/trim-fixture"),"Trim fixture")
        val pending=async{provider.detail(card)};entered.await();provider.trim();release.complete(Unit);pending.await()
        provider.detail(card);assertEquals(2,calls.get())
        provider.detail(card);assertEquals(2,calls.get())
        provider.trim();provider.detail(card);assertEquals(3,calls.get())
        Unit
    }
}
