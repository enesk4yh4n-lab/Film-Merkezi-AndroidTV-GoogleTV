package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Test
import org.junit.Assert.*

class EpisodeSourcePoolTest {
    private fun card(site:String="dizipal",number:Int=1,series:String="work:dragon")=Card(
        ContentRef(site,"https://example.test/$site/season-1-episode-$number",ContentKind.EPISODE),"Dragon",seriesIdentity=series,
        episodeNumber=EpisodeNumber(1,number),seriesRef=ContentRef(site,"https://example.test/$site/dragon",ContentKind.SERIES))
    private fun item(a:Card=card(),b:Card=card("dizibox"))=UnifiedItem("episode",a,listOf(a,b))
    private fun detail(c:Card)=Detail(c,options=listOf(SourceOption(c.ref.siteId,c.ref,"Source","https://cdn.example.test/master.m3u8")))
    private val none={_:Set<String>->flowOf(SourcePool(emptyList(),false))}
    @Test fun brokenPreferredPageKeepsOtherVerifiedSite()=runBlocking {
        val rows=EpisodeSourcePool.observe(item(),true,{c->if(c.ref.siteId=="dizipal")throw DataFailure(FailureKind.HTTP,"HTTP 404",httpCode=404);detail(c)},none).toList()
        assertEquals("dizibox",rows.last().options.single().content.siteId);assertFalse(rows.last().pending)
        assertEquals(404,rows.last().errors.single().code)
    }
    @Test fun slowPreferredPageDoesNotBlockFastKnownAlternative()=runBlocking {
        var slowCancelled=false
        val first=withTimeout(2000){EpisodeSourcePool.observe(item(),true,{c->
            if(c.ref.siteId=="dizipal")try{delay(60000)}finally{slowCancelled=true}
            detail(c)
        },none).first{it.options.isNotEmpty()}}
        assertEquals("dizibox",first.options.single().content.siteId);assertTrue(slowCancelled)
    }
    @Test fun crossSiteOffLoadsOnlyPreferredSiteAndNeverDiscovers()=runBlocking {
        val loaded=mutableListOf<String>();var searched=false
        val last=EpisodeSourcePool.observe(item(),false,{c->loaded+=c.ref.siteId;detail(c)},{searched=true;flowOf(SourcePool(emptyList(),false))}).last()
        assertEquals(listOf("dizipal"),loaded);assertFalse(searched);assertFalse(last.pending)
    }
    @Test fun differentEpisodeOrSeriesNeverBecomesAnAlternative(){
        val a=card();val values=listOf(a,card("dizibox",2),card("ddizi",1,"other-series"),card("hdfilmdelisi").copy(ref=card("hdfilmdelisi").ref.copy(kind=ContentKind.MOVIE)))
        assertEquals(listOf(a),EpisodeSourcePool.seeds(UnifiedItem("e",a,values),true))
    }
    @Test fun conflictingDetailEpisodeNumberIsRejected()=runBlocking {
        val c=card();val last=EpisodeSourcePool.observe(item(c,c),false,{detail(it.copy(episodeNumber=EpisodeNumber(1,2)))},none).last()
        assertTrue(last.options.isEmpty());assertEquals(FailureKind.PARSE,last.errors.single().kind)
    }
    @Test fun discoveryErrorDoesNotCancelPreferredPage()=runBlocking {
        val c=card();val last=EpisodeSourcePool.observe(item(c,c),true,{delay(20);detail(it)},{flow{throw DataFailure(FailureKind.NETWORK,"Offline")}}).last()
        assertEquals(1,last.options.size);assertTrue(last.errors.any{it.kind==FailureKind.NETWORK})
    }
    @Test fun cancellationStopsSeedAndDiscoveryRequests()=runBlocking {
        val entered=CompletableDeferred<Unit>();var localStopped=false;var remoteStopped=false
        val task=launch{EpisodeSourcePool.observe(item(card(),card()),true,{entered.complete(Unit);try{delay(60000);detail(it)}finally{localStopped=true}},
            {flow{try{emit(SourcePool(emptyList(),true));delay(60000)}finally{remoteStopped=true}}}).collect{}}
        entered.await();delay(20);task.cancelAndJoin();assertTrue(localStopped);assertTrue(remoteStopped)
    }
    @Test fun lateDiscoveryKeepsPoolPendingUntilAlternativeArrives()=runBlocking {
        val c=card();val other=card("dizibox")
        val state=EpisodeSourcePool.observe(item(c,c),true,{throw DataFailure(FailureKind.HTTP,"404")},{flow{
            emit(SourcePool(emptyList(),true));delay(40);emit(SourcePool(detail(other).options.map{it.scoped()},false))
        }}).stateIn(this,SharingStarted.Eagerly,SourcePool(emptyList(),true))
        try{val first=withTimeout(2000){FirstReadySource.await(state){ResolvedSource(it.id,it.label,it.url,StreamType.HLS)}};assertEquals("dizibox",first.first.content.siteId)}
        finally{coroutineContext.cancelChildren()}
    }
}
