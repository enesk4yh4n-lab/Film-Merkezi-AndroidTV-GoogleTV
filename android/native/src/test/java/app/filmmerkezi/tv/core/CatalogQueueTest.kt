package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogQueueTest {
    @Test fun nextFreeSlotGoesToTheNewlyVisibleScreen()=runBlocking{
        val queue=CatalogQueue()
        val home=CatalogVisit(true)
        val movies=CatalogVisit(false)
        val release=CompletableDeferred<Unit>()
        val order=mutableListOf<String>()
        fun request(name:String,visit:CatalogVisit,hold:Boolean=false)=launch(visit,start=CoroutineStart.UNDISPATCHED){
            queue.withSlot {order.add(name);if(hold)release.await()}
        }
        val active=listOf(request("home 1",home,true),request("home 2",home,true))
        val nextHome=request("home 3",home)
        val nextMovies=request("movies",movies)
        val cancelled=request("cancelled",movies)
        home.visible=false;movies.visible=true
        cancelled.cancelAndJoin()
        assertEquals(listOf("home 1","home 2"),order)
        release.complete(Unit)
        withTimeout(3000){(active+listOf(nextHome,nextMovies)).joinAll()}
        assertEquals(listOf("home 1","home 2","movies","home 3"),order)
    }
}
