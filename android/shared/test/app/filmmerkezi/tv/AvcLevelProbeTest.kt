package app.filmmerkezi.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvcLevelProbeTest {
    @Test fun preservesHighProfileWhenOnlyLevelIsOverstated() {
        assertEquals("avc1.640028", AvcLevelProbe.boundedCodec("avc1.64003D", 1920, 1080, 30f))
    }
    @Test fun preservesBaselineProfileWithin720pBudget() {
        assertEquals("avc1.42C01f", AvcLevelProbe.boundedCodec("avc1.42C03D", 1280, 720, 24f))
    }
    @Test fun neverDemotesUnsupportedChromaOrBitDepthProfiles() {
        for (codec in listOf("avc1.6E003D", "avc1.7A003D", "avc1.F4003D", "hvc1.1.6.L153")) assertNull(AvcLevelProbe.boundedCodec(codec, 1280, 720, 24f))
    }
    @Test fun refusesUnknownOrOversizedInputAndUnchangedLevel() {
        for (fps in listOf(-1f, 0f, 60f, Float.NaN)) assertNull(AvcLevelProbe.boundedCodec("avc1.64003D", 1920, 1080, fps))
        assertNull(AvcLevelProbe.boundedCodec("avc1.64003D", 3840, 2160, 24f))
        assertNull(AvcLevelProbe.boundedCodec("avc1.64003D", -1, 720, 24f))
        assertNull(AvcLevelProbe.boundedCodec("avc1.640028", 1920, 1080, 24f))
    }
}
