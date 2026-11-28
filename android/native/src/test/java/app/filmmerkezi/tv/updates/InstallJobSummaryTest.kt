package app.filmmerkezi.tv.updates

import org.junit.Assert.*
import org.junit.Test

class InstallJobSummaryTest {
    @Test fun completedProviderCountMakesFifteenOfThirtyFiftyPercent(){
        val sites=(1..30).map{InstallJobItem("p$it","Provider $it",if(it<=15)"ready"else"waiting",true,listOf("resolver"))}
        val resolver=InstallJobItem("resolver","Shared resolver","ready",false,emptyList())
        val summary=InstallJobSummary(sites+resolver)
        assertEquals(30,summary.total)
        assertEquals(15,summary.completed)
        assertEquals(50,summary.percent)
        assertFalse(summary.complete)
    }

    @Test fun readyProviderWaitsForItsRequiredResolver(){
        val provider=InstallJobItem("p004","Provider","ready",true,listOf("r004"))
        val resolver=InstallJobItem("r004","Resolver","failed",false,emptyList())
        assertEquals(0,InstallJobSummary(listOf(provider,resolver)).percent)
        assertFalse(InstallJobSummary(listOf(provider)).complete)
        assertTrue(InstallJobSummary(listOf(provider),setOf("r004")).complete)
        assertEquals(100,InstallJobSummary(listOf(provider,resolver.copy(status="ready"))).percent)
    }
}
