package app.filmmerkezi.tv
import android.media.MediaCodecList
import android.view.WindowManager
import androidx.media3.common.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.*
import java.io.File

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
@RunWith(AndroidJUnit4::class)
class Stage3CapabilitiesTest {
    @Test fun actualDeviceDeclarationsConstrainTracks(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val policy=PlaybackDevicePolicy(context)
        val fourK=Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(3840).setHeight(2160).setFrameRate(24f).build()
        val fourKSize=MediaCodecUtil.getDecoderInfos(MimeTypes.VIDEO_H264,false,false).any{it.isVideoSizeAndRateSupported(3840,2160,24.0)}
        assertEquals(fourKSize,policy.allows(fourK,true))
        val audio=Format.Builder().setSampleMimeType(MimeTypes.AUDIO_DTS).build()
        if(MimeTypes.AUDIO_DTS !in policy.declaredTypes())assertFalse(policy.allows(audio,true))
        @Suppress("DEPRECATION")
        val hdr=(context.getSystemService(android.content.Context.WINDOW_SERVICE)as WindowManager).defaultDisplay.hdrCapabilities?.supportedHdrTypes?.toList().orEmpty()
        val pq=Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(1280).setHeight(720)
            .setColorInfo(ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_ST2084).build()).build()
        if(hdr.isEmpty())assertFalse(policy.allows(pq,true))
        val hevc=Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).setWidth(1280).setHeight(720).build()
        if(MimeTypes.VIDEO_H265 !in policy.declaredTypes())assertFalse(policy.allows(hevc,true))
        File(context.getExternalFilesDir(null),"stage3-capabilities.json").writeText(JSONObject().put("api",android.os.Build.VERSION.SDK_INT)
            .put("decoderMimeTypes",JSONArray(policy.declaredTypes())).put("hdrDisplayTypes",JSONArray(hdr)).put("fourKSizeSupportedByDevice",fourKSize)
            .put("passed",true).toString(2))
    }
}
