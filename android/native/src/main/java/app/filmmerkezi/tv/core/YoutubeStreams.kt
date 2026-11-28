package app.filmmerkezi.tv.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Shared recognition for menu inventory and resolution; no network or extractor initialization. */
object YoutubeLinks {
    fun isHost(host:String)=host in setOf("youtube.com","www.youtube.com","m.youtube.com","youtube-nocookie.com","www.youtube-nocookie.com","youtu.be")
    fun videoId(value:String):String? {
        val u=value.toHttpUrlOrNull()?:return null
        if(u.scheme!="https"||!isHost(u.host)||u.username.isNotEmpty()||u.password.isNotEmpty())return null
        val parts=u.pathSegments.filter{it.isNotEmpty()}
        val id=when {
            u.host=="youtu.be"&&parts.size==1->parts.single()
            u.encodedPath=="/watch"->u.queryParameter("v")
            parts.size==2&&parts[0] in listOf("embed","shorts","live")->parts[1]
            else->null
        }
        return id?.takeIf{it.matches(Regex("[A-Za-z0-9_-]{11}"))}
    }
    fun frameUrl(value:String,page:String):String? {
        val u=publicUrl(value,page)?.toHttpUrlOrNull()?:return null
        if(videoId(u.toString())!=null)return u.toString()
        // A provider-owned wrapper may carry the public YouTube URL as inert data.
        val owner=page.toHttpUrlOrNull()
        if(owner!=null&&u.scheme==owner.scheme&&u.host==owner.host&&u.port==owner.port&&u.encodedPath=="/player/telif/index.php"){
            val id=videoId(u.queryParameter("id").orEmpty())?:return null
            return "https://www.youtube.com/watch?v=$id"
        }
        return null
    }
}

fun interface YoutubeResolver { suspend fun resolve(option:SourceOption,url:String):ResolvedSource }

object YoutubeStreams {
    const val USER_AGENT="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val HLS_OPTION = "youtubeFormat"

    internal fun withHlsOptions(options: List<SourceOption>): List<SourceOption> {
        val alternatives = options.mapNotNull { option ->
            if (option.extractor != "youtube" || option.state != OptionState.AVAILABLE ||
                option.fields.containsKey(HLS_OPTION) || YoutubeLinks.videoId(option.url) == null) return@mapNotNull null
            val hlsId = option.id + "-hls"
            if (options.any { it.id == hlsId }) return@mapNotNull null
            option.copy(id = hlsId, label = option.label + " · HLS", fields = option.fields + (HLS_OPTION to "hls"))
        }
        return options + alternatives
    }

    @JvmOverloads
    fun assemble(option:SourceOption,variants:List<MediaVariant>,subtitles:List<Subtitle>,hlsUrl:String?=null):ResolvedSource {
        val hlsOnly = option.fields[HLS_OPTION] == "hls"
        val valid=variants.filter{it.height>0&&it.fps>=0&&it.mimeType in setOf("video/mp4","video/webm")
            &&publicUrl(it.url)?.startsWith("https://")==true&&it.audio.all{a->publicUrl(a.url)?.startsWith("https://")==true}}
            .distinctBy{it.id}.sortedWith(compareByDescending<MediaVariant>{it.height}.thenByDescending{it.fps})
        // Retain every resolution/codec family, including combined fallbacks. Device support is checked by the player.
        val choices=valid.distinctBy{listOf(it.height,it.fps,it.codec,it.mimeType,it.audio.isEmpty())}
        val best=choices.firstOrNull()
        if(hlsOnly || best==null) {
            val manifest=hlsUrl?.let{publicUrl(it)}?.takeIf{it.startsWith("https://")}
                ?:throw DataFailure(FailureKind.UNSUPPORTED,
                    if (hlsOnly) "YouTube bu video için HLS manifesti sunmadı" else "YouTube: uygun video kaynağı bulunamadı", "youtube")
            return ResolvedSource(option.id,option.label,manifest,StreamType.HLS,"https://www.youtube.com/",USER_AGENT,
                subtitles=subtitles.take(8),playbackRequirements=setOf("youtube"))
        }
        val expires=choices.mapNotNull{it.url.toHttpUrlOrNull()?.queryParameter("expire")?.toLongOrNull()?.times(1000)}.minOrNull()
        return ResolvedSource(option.id,option.label,best.url,if(best.mimeType=="video/webm")StreamType.WEBM else StreamType.MP4,"https://www.youtube.com/",USER_AGENT,
            subtitles=subtitles.take(8),playbackRequirements=setOf("youtube"),mediaProfile=MediaProfile(best.codec,null,best.width,best.height),
            expiresAt=expires,audioStreams=best.audio,variants=choices)
    }
}
