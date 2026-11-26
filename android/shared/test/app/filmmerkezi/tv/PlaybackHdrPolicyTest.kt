package app.filmmerkezi.tv

import org.junit.Test
import org.junit.Assert.*

class PlaybackHdrPolicyTest {
    @Test fun hdrRequiresHdrDisplay(){
        assertFalse(PlaybackHdrPolicy.allows(true,false))
        assertTrue(PlaybackHdrPolicy.allows(true,true))
    }
    @Test fun sdrDoesNotRequireHdrDisplay(){
        assertTrue(PlaybackHdrPolicy.allows(false,false))
    }
}
