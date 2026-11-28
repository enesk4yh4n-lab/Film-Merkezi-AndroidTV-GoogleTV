package app.filmmerkezi.tv.updates

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DistributionChecksTest {
    private val release=UpdateProtocol.Release(38,"0.12.2",23,"https://example.test/app.apk",10,"a".repeat(64),"b".repeat(64),"Notes")
    @Test fun repositoryFailureCannotHideCurrentAppOrOtherRepository()=runBlocking {
        var latest=DistributionStatus()
        DistributionChecks.run(latest,38,listOf(RepositoryCheck("a","A"),RepositoryCheck("b","B")),{release},{if(it=="a")error("offline")},{2},now={123},emit={latest=it})
        assertEquals(CheckState.CURRENT,latest.appCheck);assertEquals(CheckState.FAILED,latest.repositoryChecks.getValue("a").state)
        assertEquals(CheckState.CURRENT,latest.repositoryChecks.getValue("b").state);assertEquals(2,latest.moduleUpdates);assertFalse(latest.busy);assertEquals(123,latest.checkedAt)
    }
    @Test fun appFailureStillChecksRepositoriesAndRetainsVerifiedOffer()=runBlocking {
        var latest=DistributionStatus(release=release)
        DistributionChecks.run(latest,37,listOf(RepositoryCheck("a","A")),{error("signature")},{},{1},emit={latest=it})
        assertEquals(CheckState.FAILED,latest.appCheck);assertEquals("signature",latest.appError);assertEquals(release,latest.release)
        assertEquals(CheckState.CURRENT,latest.repositoryChecks.getValue("a").state)
    }
    @Test fun availableAndCurrentHaveDifferentStates()=runBlocking {
        var latest=DistributionStatus()
        DistributionChecks.run(latest,37,emptyList(),{release},{},{0},emit={latest=it});assertEquals(CheckState.AVAILABLE,latest.appCheck);assertEquals(release,latest.release)
        DistributionChecks.run(latest,38,emptyList(),{release},{},{0},emit={latest=it});assertEquals(CheckState.CURRENT,latest.appCheck);assertNull(latest.release)
    }
    @Test fun cancellationClearsBusyAndOnlyMarksRunningCheckCancelled()=runBlocking {
        var latest=DistributionStatus();val entered=CompletableDeferred<Unit>()
        val job=launch{DistributionChecks.run(latest,38,listOf(RepositoryCheck("a","A")),{release},{entered.complete(Unit);awaitCancellation()},{0},emit={latest=it})}
        entered.await();job.cancelAndJoin();assertFalse(latest.busy);assertEquals(CheckState.CURRENT,latest.appCheck);assertEquals(CheckState.CANCELLED,latest.repositoryChecks.getValue("a").state)
    }
    @Test fun corruptPackageCountCannotCrashOrChangeSuccessfulChecks()=runBlocking {
        var latest=DistributionStatus(moduleUpdates=3)
        DistributionChecks.run(latest,38,emptyList(),{release},{},{error("bad stored package")},emit={latest=it})
        assertEquals(CheckState.CURRENT,latest.appCheck);assertFalse(latest.busy);assertEquals(3,latest.moduleUpdates);assertEquals("bad stored package",latest.pluginError)
    }
}
