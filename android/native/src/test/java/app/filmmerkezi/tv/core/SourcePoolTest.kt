package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.Assert.*

class SourcePoolTest {
    private fun option(id:String)=SourceOption(id,ContentRef("filmcibaba","https://filmizle.ink/test/"),id,"https://hotstream.club/$id")
    private fun resolved(o:SourceOption)=ResolvedSource(o.id,o.label,"https://cdn.example/${o.id}.m3u8",StreamType.HLS)
    @Test fun firstReadyDoesNotWaitForSlowest()=runBlocking{val state=MutableStateFlow(SourcePool(listOf(option("slow"),option("fast")),false));val first=FirstReadySource.await(state){if(it.id=="slow")delay(10000)else delay(10);resolved(it)};assertEquals("fast",first.first.id)}
    @Test fun pendingPoolAcceptsLateAlternative()=runBlocking{val state=MutableStateFlow(SourcePool(listOf(option("bad")),true));val job=launch{delay(30);state.value=SourcePool(listOf(option("bad"),option("good")),false)};val first=FirstReadySource.await(state){if(it.id=="bad")error("404");resolved(it)};assertEquals("good",first.first.id);job.join()}
    @Test fun failureIsTerminalOnlyAfterDiscoveryEnds()=runBlocking{val state=MutableStateFlow(SourcePool(emptyList(),true));launch{delay(20);state.value=SourcePool(emptyList(),false)};try{FirstReadySource.await(state){resolved(it)};fail()}catch(e:DataFailure){assertEquals(FailureKind.UNSUPPORTED,e.kind)}}
    @Test fun cancellationStopsOutstandingResolvers()=runBlocking{var cancelled=false;val state=MutableStateFlow(SourcePool(listOf(option("slow")),true));val task=launch{FirstReadySource.await(state){try{delay(10000);resolved(it)}finally{cancelled=true}}};delay(20);task.cancelAndJoin();assertTrue(cancelled)}

    @Test fun timedOutSourcesFreeSlotsForADiscoveredAlternative()=runBlocking {
        val blocked=listOf(option("timeout-a"),option("timeout-b"))
        val alternate=option("alternate")
        val state=MutableStateFlow(SourcePool(blocked,true))
        val attempted=mutableSetOf<String>()
        val failures=mutableMapOf<String,FailureKind?>()
        val selected=withTimeout(2000){
            FirstReadySource.await(state,onFailure={source,error->
                failures[source.id]=(error as? DataFailure)?.kind
                if(failures.size==2)state.value=SourcePool(blocked+alternate,false)
            }){source->
                attempted.add(source.id)
                if(source in blocked)withTimeout(30){awaitCancellation()}
                resolved(source)
            }
        }
        assertEquals(blocked.map{it.id}.toSet()+alternate.id,attempted)
        assertEquals(blocked.associate{it.id to FailureKind.TIMEOUT},failures)
        assertEquals(alternate,selected.first)
    }

    @Test fun maximumTwoResolversAndAllCandidatesVisited()=runBlocking{var active=0;var peak=0;var total=0;val state=MutableStateFlow(SourcePool((1..20).map{option("$it")},false));val first=FirstReadySource.await(state){active++;peak=maxOf(peak,active);total++;try{delay(2);if(it.id!="20")error("broken");resolved(it)}finally{active--}};assertEquals("20",first.first.id);assertEquals(2,peak);assertEquals(20,total)}
    @Test fun scopedIdsDistinguishSitesAndPages(){val a=option("1").scoped();assertEquals(a,a.scoped());assertNotEquals(a.id,option("1").copy(content=option("1").content.copy(pageUrl="https://filmizle.ink/other/")).scoped().id)}

    @Test fun sourceFailureRemainsVisibleWhenADifferentSourceStarts()=runBlocking {
        val denied=option("denied")
        val ready=option("ready")
        val state=MutableStateFlow(SourcePool(listOf(denied,ready),false))
        val failures=mutableMapOf<String,String>()
        val selected=FirstReadySource.await(state,onFailure={option,error->failures[option.id]=error.message.orEmpty()}){
            if(it==denied)throw DataFailure(FailureKind.ACCESS,"Site tarayıcı doğrulaması istiyor","browser-challenge",403)
            delay(10)
            resolved(it)
        }
        val refreshed=state.value.withResolutionFailures(failures)
        assertEquals(ready,selected.first)
        assertEquals("Site tarayıcı doğrulaması istiyor",refreshed.options.first().reason)
        assertEquals(OptionState.AVAILABLE,refreshed.options.first().state)
        assertEquals("",refreshed.options.last().reason)
    }

    @Test fun onlySiteFailureIsPreservedWhenThereAreNoSourceOptions()=runBlocking {
        val state=MutableStateFlow(SourcePool(emptyList(),false,errors=listOf(
            SiteFailure("filmcibaba",FailureKind.ACCESS,"Site tarayıcı doğrulaması istiyor",403)
        )))
        val failure=runCatching{FirstReadySource.await(state){error("Unexpected resolver call")}}
            .exceptionOrNull() as DataFailure
        assertEquals(FailureKind.ACCESS,failure.kind)
        assertEquals("Site tarayıcı doğrulaması istiyor",failure.message)
        assertEquals(403,failure.httpCode)
    }
}
