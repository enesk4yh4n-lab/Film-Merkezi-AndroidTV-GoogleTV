package app.filmmerkezi.tv.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogSnapshotsTest {
    @Test fun newRepositoryShowsSavedCatalogBeforeRefreshAndKeepsItOnFailure()=runBlocking{
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"catalog-test-${UUID.randomUUID()}")
        val installed=Sites.all
        val remote=PluginGateway.remote
        val site=Sites.defaults.first{it.id=="diziboxum"}
        val card=Card(ContentRef(site.id,"${site.base}/dizi/test/",ContentKind.SERIES),"Kayıtlı katalog")
        var parsed=listOf(card)
        var responseCode=200
        var time=1_000L
        var calls=0
        val transport=object:WebTransport{
            override suspend fun request(request:WebRequest):WebResponse{
                calls++
                return WebResponse(request.url,"<main></main>",responseCode)
            }
        }
        val store=CatalogSnapshots(dir){time}
        fun repository()=CatalogRepository(transport,snapshots=CatalogSnapshots(dir){time})
        fun results(repo:CatalogRepository)=repo.observeSection(CatalogSection.DRAMA_SHORT)
        try{
            Sites.installed(listOf(site))
            PluginGateway.remote={_,_,operation,_->
                assertEquals("parseList",operation)
                PluginWire.page(CatalogPage(parsed))
            }
            assertFalse(results(repository()).last().cached)
            assertTrue(store.bytes()>0)
            responseCode=503
            val offline=results(repository()).toList()
            assertTrue(offline.first().cached)
            assertEquals(0,offline.first().completedSites)
            assertEquals(listOf(card.title),offline.first().items.map{it.card.title})
            assertTrue(offline.last().cached)
            assertEquals(503,offline.last().failures.single().code)
            assertTrue(offline.last().next.isEmpty())
            assertEquals(2,calls)

            Sites.installed(listOf(site),mapOf(site.id to CatalogPluginMetadata("p007@updated",null)))
            assertFalse(results(repository()).toList().any{it.cached})
            Sites.installed(listOf(site))
            time+=25*60*60*1000L
            assertFalse(results(repository()).toList().any{it.cached})

            time=1_000L;responseCode=200;parsed=emptyList()
            val empty=results(repository()).toList()
            assertTrue(empty.first().cached)
            assertFalse(empty.last().cached)
            assertTrue(empty.last().items.isEmpty())
            assertEquals(0L,store.bytes())
        }finally{PluginGateway.remote=remote;Sites.installed(installed);store.clear();dir.delete()}
    }

    @Test fun cacheIsBoundedAndAClearInvalidatesPendingWrites()=runBlocking{
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"catalog-test-${UUID.randomUUID()}")
        val store=CatalogSnapshots(dir)
        val card=Card(ContentRef("diziboxum","https://diziboxum.com/dizi/test/",ContentKind.SERIES),"Fixture")
        val items=listOf(UnifiedItem("fixture",card,listOf(card)))
        val oldGeneration=store.generation
        try{
            repeat(10){store.write(it.toString(),items,oldGeneration)}
            assertEquals(8,dir.listFiles()!!.count{it.extension=="json"})
            assertTrue(store.bytes() in 1..(3*1024*1024L))
            store.clear()
            store.write("pending",items,oldGeneration)
            assertEquals(0L,store.bytes())
        }finally{store.clear();dir.delete()}
    }
}
