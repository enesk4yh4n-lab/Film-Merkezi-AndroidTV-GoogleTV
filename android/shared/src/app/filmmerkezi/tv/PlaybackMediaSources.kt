package app.filmmerkezi.tv

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Format
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** Uses the same HTTP factory and ExoPlayer for video, separate audio and subtitles. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal object PlaybackMediaSources {
    fun create(source:JSONObject,factory:DefaultMediaSourceFactory,item:MediaItem):MediaSource {
        val video=factory.createMediaSource(item)
        val audio=source.optJSONArray("audioStreams")?:return video
        if(audio.length()==0)return video
        val parts=mutableListOf(video)
        for(i in 0 until minOf(audio.length(),2)){
            val a=audio.getJSONObject(i)
            val url=a.getString("url").toHttpUrlOrNull()?:error("Invalid audio URL")
            require(url.scheme in listOf("http","https"))
            parts.add(factory.createMediaSource(MediaItem.Builder().setMediaId("${item.mediaId}-audio-$i")
                .setUri(url.toString()).setMimeType(a.optString("mimeType",MimeTypes.AUDIO_MP4)).build()))
        }
        return MergingMediaSource(true,false,*parts.toTypedArray())
    }
}

/** URLs remain session-only. Normal HLS/MP4/DASH sources have no variants. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal object PlaybackVariants {
    fun all(source:JSONObject):List<JSONObject> = source.optJSONArray("variants")?.let{a->
        (0 until a.length()).mapNotNull{a.optJSONObject(it)}.filter{it.optInt("height")>0&&it.optString("url").toHttpUrlOrNull()!=null}
    }.orEmpty()
    fun apply(source:JSONObject,variant:JSONObject):JSONObject=JSONObject(source.toString()).apply{
        put("variantId",variant.getString("id"));put("url",variant.getString("url"));put("type",if(variant.optString("mimeType")=="video/webm")"webm"else"mp4")
        put("audioStreams",variant.optJSONArray("audioStreams")?:org.json.JSONArray())
        put("mediaProfile",JSONObject().put("videoCodec",variant.optString("codec")).put("height",variant.optInt("height")).put("width",variant.optInt("width")))
    }
    fun format(v:JSONObject):Format=Format.Builder().setId(v.optString("id"))
        .setSampleMimeType(MimeTypes.getVideoMediaMimeType(v.optString("codec"))?:if(v.optString("mimeType")=="video/webm")MimeTypes.VIDEO_VP9 else MimeTypes.VIDEO_H264)
        .setAverageBitrate(v.optInt("bitrate").takeIf{it>0}?:Format.NO_VALUE)
        .setCodecs(v.optString("codec").takeIf{it.isNotBlank()}).setHeight(v.optInt("height"))
        .setWidth(v.optInt("width").takeIf{it>0}?:Format.NO_VALUE).setFrameRate(v.optInt("fps").takeIf{it>0}?.toFloat()?:Format.NO_VALUE.toFloat()).build()
    fun prepare(source:JSONObject,height:Int,allowed:(JSONObject)->Boolean={true}):JSONObject {
        val variants=all(source).filter(allowed);if(variants.isEmpty())return source
        val ordered=variants.sortedWith(compareByDescending<JSONObject>{it.optInt("height")}.thenByDescending{it.optInt("fps")}.thenBy{it.optJSONArray("audioStreams")?.length()!=0})
        val selected=variants.firstOrNull{it.optString("id")==source.optString("variantId")}
            ?:(if(height>0)ordered.firstOrNull{it.optInt("height")<=height}?:ordered.last() else ordered.first())
        return apply(source,selected)
    }
    fun fallback(source:JSONObject,tried:Set<String>,allowed:(JSONObject)->Boolean={true}):JSONObject? = all(source).filter(allowed)
        .filter{it.optJSONArray("audioStreams")?.length()!=null&&it.getJSONArray("audioStreams").length()==0
            &&it.optString("id")!=source.optString("variantId")&&it.optString("id") !in tried}
        .maxByOrNull{it.optInt("height")}?.let{apply(source,it)}

    fun lowerBitrate(source:JSONObject,tried:Set<String>,budget:Long,allowed:(JSONObject)->Boolean):JSONObject? {
        val rows=all(source)
        val bitrate=rows.firstOrNull{it.optString("id")==source.optString("variantId")}?.optInt("bitrate")?:0
        if(bitrate<=0)return null
        val lower=rows.filter{it.optInt("bitrate") in 1 until bitrate&&it.optString("id") !in tried&&allowed(it)}
        return (lower.filter{it.optInt("bitrate")<=budget}.maxByOrNull{it.optInt("bitrate")}
            ?:lower.minByOrNull{it.optInt("bitrate")})?.let{apply(source,it)}
    }
}
