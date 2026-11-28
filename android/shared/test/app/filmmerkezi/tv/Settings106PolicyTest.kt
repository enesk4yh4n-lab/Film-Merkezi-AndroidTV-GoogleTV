package app.filmmerkezi.tv

import org.junit.Test
import org.junit.Assert.*

class Settings106PolicyTest {
    @Test fun automaticLimitsDoNotConstrainAnExplicitManualChoice(){
        val p=PlaybackSettings(autoMaxHeight=720,autoMaxFps=30)
        assertTrue(PlaybackPreferencePolicy.autoVideoAllowed(720,29.97f,p))
        assertFalse(PlaybackPreferencePolicy.autoVideoAllowed(1080,24f,p))
        assertFalse(PlaybackPreferencePolicy.autoVideoAllowed(720,60f,p))
        assertTrue(PlaybackPreferencePolicy.autoVideoAllowed(1080,60f,p.copy(preferredHeight=1080)))
        assertTrue(PlaybackPreferencePolicy.autoVideoAllowed(4320,120f,PlaybackSettings()))
    }
    @Test fun automaticTextDependsOnKnownAudioAndRetainsForcedCues(){
        assertFalse(PlaybackPreferencePolicy.subtitleAllowed(SubtitlePolicy.AUTO,"tur",false))
        assertTrue(PlaybackPreferencePolicy.subtitleAllowed(SubtitlePolicy.AUTO,"tr",true))
        assertTrue(PlaybackPreferencePolicy.subtitleAllowed(SubtitlePolicy.AUTO,"en",false))
        assertTrue(PlaybackPreferencePolicy.subtitleAllowed(SubtitlePolicy.AUTO,null,false))
        assertFalse(PlaybackPreferencePolicy.subtitleAllowed(SubtitlePolicy.OFF,"en",true))
        assertTrue(PlaybackPreferencePolicy.subtitleAllowed(SubtitlePolicy.ON,"tr",false))
    }
    @Test fun profilesStayWithinTheNormalPlaybackBufferBudget(){
        val mib = 1024 * 1024
        val smallHeap = 32L * mib
        val largeHeap = 256L * mib
        assertEquals(32 * mib, BufferProfile.AUTO.targetBytes(largeHeap))
        assertEquals(32 * mib, BufferProfile.LONG.targetBytes(largeHeap))
        assertEquals(8 * mib, BufferProfile.LOW.targetBytes(largeHeap))
        for(p in BufferProfile.entries){
            assertTrue(p.maxMs>=p.minMs)
            assertTrue(p.minMs>=p.restartMs)
            assertTrue(p.minMs>=p.startMs)
            assertEquals(8 * mib, p.targetBytes(smallHeap))
        }
    }
}
