package app.filmmerkezi.tv

import org.junit.Assert.*
import org.junit.Test

class PlaybackRecoveryTest {
    @Test fun permanentFailureMovesForwardWithoutLooping() {
        val r = PlaybackRecovery(3)
        assertEquals(PlaybackRecovery.Action.NEXT, r.failed(PlaybackRecovery.Failure.HTTP, false))
        assertEquals(1, r.index)
        assertEquals(PlaybackRecovery.Action.NEXT, r.failed(PlaybackRecovery.Failure.DECODER, true))
        assertEquals(PlaybackRecovery.Action.STOP, r.failed(PlaybackRecovery.Failure.AUDIO, true))
    }
    @Test fun connectionCanRestartOnlyOnceAfterProgress() {
        val r = PlaybackRecovery(2)
        assertEquals(PlaybackRecovery.Action.RETRY, r.failed(PlaybackRecovery.Failure.NETWORK, true))
        assertEquals(PlaybackRecovery.Action.NEXT, r.failed(PlaybackRecovery.Failure.NETWORK, true))
        assertEquals(PlaybackRecovery.Action.RETRY, r.failed(PlaybackRecovery.Failure.STALL, true))
        assertEquals(PlaybackRecovery.Action.STOP, r.failed(PlaybackRecovery.Failure.STALL, true))
    }
    @Test fun aDeadStartupUrlNeverRestartsTheSameSession() {
        val r = PlaybackRecovery(1)
        assertEquals(PlaybackRecovery.Action.STOP, r.failed(PlaybackRecovery.Failure.NETWORK, false))
        assertEquals(0, r.retryCount)
    }
    @Test fun startupUsesElapsedTimeRatherThanSampleCount() {
        val g = PlaybackGuard(20000, 30000); g.reset(1000)
        assertNull(g.observe(20999, true, false, 0, 0))
        assertEquals(PlaybackRecovery.Failure.STARTUP, g.observe(21000, true, false, 0, 0))
    }
    @Test fun pauseAndAudioFocusLossDoNotConsumeStartupBudget() {
        val g = PlaybackGuard(20000, 30000); g.reset(0)
        assertNull(g.observe(90000, false, false, 0, 0))
        assertNull(g.observe(109999, true, false, 0, 0))
    }
    @Test fun advancingAudioDoesNotHideFrozenVideo() {
        val g = PlaybackGuard(20000, 30000); g.reset(0, 1000, 10)
        assertNull(g.observe(29000, true, true, 30000, 10))
        assertEquals(PlaybackRecovery.Failure.STALL, g.observe(30000, true, true, 31000, 10))
    }
    @Test fun realFramesAndPositionResetTheStallClock() {
        val g = PlaybackGuard(20000, 30000); g.reset(0)
        assertNull(g.observe(29000, true, true, 28000, 700))
        assertNull(g.observe(58000, true, true, 57000, 1400))
        assertEquals(PlaybackRecovery.Failure.STALL, g.observe(88000, true, true, 57000, 1400))
    }
    @Test fun seekAndPausedPlaybackResetTheReferencePoint() {
        val g = PlaybackGuard(); g.reset(1000, 60000, 1200)
        g.reset(29000, 10000, 1200)
        assertNull(g.observe(30000, true, true, 11000, 1220))
        assertNull(g.observe(90000, false, true, 11000, 1220))
        assertNull(g.observe(91000, true, true, 12000, 1240))
    }
}
