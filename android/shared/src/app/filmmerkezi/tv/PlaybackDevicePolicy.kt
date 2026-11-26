package app.filmmerkezi.tv

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

internal object PlaybackHdrPolicy { fun allows(hdr:Boolean,hdrDisplay:Boolean)=!hdr||hdrDisplay }

internal object PlaybackTrackRole {
    fun normal(roleFlags:Int)=roleFlags and C.ROLE_FLAG_TRICK_PLAY==0
}

/** Hardware declarations plus the renderer's support, never a software decoder extension. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal class PlaybackDevicePolicy(private val context:Context) {
    private val types=runCatching{MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter{!it.isEncoder}.flatMap{it.supportedTypes.toList()}.toSet()}.getOrDefault(emptySet())
    @Suppress("DEPRECATION")
    private val hdrTypes=if(Build.VERSION.SDK_INT>=24)runCatching{
        (context.getSystemService(Context.WINDOW_SERVICE)as WindowManager).defaultDisplay.hdrCapabilities?.supportedHdrTypes?.toSet()?:emptySet()
    }.getOrDefault(emptySet())else emptySet()
    fun allows(format:Format,rendererSupported:Boolean,actual:Boolean=false):Boolean {
        // I-frame/trick-play renditions are not normal video or user quality choices.
        if(!PlaybackTrackRole.normal(format.roleFlags))return false
        val mime=format.sampleMimeType?:return rendererSupported
        if(mime.startsWith("video/")){
            val transfer=format.colorInfo?.colorTransfer
            val hdr=transfer==C.COLOR_TRANSFER_ST2084||transfer==C.COLOR_TRANSFER_HLG||mime==MimeTypes.VIDEO_DOLBY_VISION
            val displayOk=if(Build.VERSION.SDK_INT<24)false else when{
                mime==MimeTypes.VIDEO_DOLBY_VISION->Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in hdrTypes
                transfer==C.COLOR_TRANSFER_HLG->Display.HdrCapabilities.HDR_TYPE_HLG in hdrTypes
                else->Display.HdrCapabilities.HDR_TYPE_HDR10 in hdrTypes
            }
            if(!PlaybackHdrPolicy.allows(hdr,displayOk)||mime !in types)return false
            return runCatching{
                val decoders=MediaCodecUtil.getDecoderInfos(mime,false,false).filter{
                    format.width<=0||format.height<=0||it.isVideoSizeAndRateSupported(format.width,format.height,format.frameRate.toDouble())}
                // Tolerate overstated AVC labels only within an actual decoder's size/rate support.
                if(!actual&&mime==MimeTypes.VIDEO_H264)return@runCatching decoders.isNotEmpty()
                decoders.any{it.isFormatSupported(context,format)} ||
                    (if(mime==MimeTypes.VIDEO_H264)AvcLevelProbe.boundedCodec(format.codecs,format.width,format.height,format.frameRate)else null)
                        ?.let{bounded->decoders.any{it.isFormatSupported(context,format.buildUpon().setCodecs(bounded).build())}}==true ||
                    (mime==MimeTypes.VIDEO_H264&&decoders.isNotEmpty()&&
                        Regex("(?i)^avc[13]\\.([0-9a-f]{2})[0-9a-f]{4}$").find(format.codecs.orEmpty())
                            ?.groupValues?.get(1)?.toIntOrNull(16) in setOf(66,77,100))
            }.getOrDefault(false)
        }
        if(mime.startsWith("audio/"))return rendererSupported&&(mime==MimeTypes.AUDIO_RAW||mime in types)
        return rendererSupported
    }
    fun declaredTypes()=types.sorted()
}
