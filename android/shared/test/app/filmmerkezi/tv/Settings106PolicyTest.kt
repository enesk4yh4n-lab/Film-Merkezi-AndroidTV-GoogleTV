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
    @Test fun profilesAreBoundedAndDefaultRetainsTheProvenBuffer(){
        assertEquals(16*1024*1024,BufferProfile.AUTO.bytes)
        for(p in BufferProfile.entries){assertTrue(p.maxMs>=p.minMs);assertTrue(p.minMs>=p.restartMs);assertTrue(p.minMs>=p.startMs);assertTrue(p.bytes<=24*1024*1024)}
    }
}
