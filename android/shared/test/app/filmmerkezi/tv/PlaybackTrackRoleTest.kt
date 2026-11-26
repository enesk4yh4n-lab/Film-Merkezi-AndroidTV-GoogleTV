package app.filmmerkezi.tv

import androidx.media3.common.C
import org.junit.Test
import org.junit.Assert.*

class PlaybackTrackRoleTest {
    @Test fun trickPlayIsNotNormalVideoEvenWhenItsResolutionAndCodecMatch(){
        assertTrue(PlaybackTrackRole.normal(0))
        assertTrue(PlaybackTrackRole.normal(C.ROLE_FLAG_MAIN))
        assertTrue(PlaybackTrackRole.normal(C.ROLE_FLAG_SUBTITLE))
        assertFalse(PlaybackTrackRole.normal(C.ROLE_FLAG_TRICK_PLAY))
        assertFalse(PlaybackTrackRole.normal(C.ROLE_FLAG_MAIN or C.ROLE_FLAG_TRICK_PLAY))
    }
}
