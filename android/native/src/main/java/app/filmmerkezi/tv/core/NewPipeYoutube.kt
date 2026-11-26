package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException

/** Bounded metadata only. The existing transport owns cookies, slots and per-call cancellation. */
class NewPipeYoutube(private val network:WebTransport):YoutubeResolver {
    companion object {
        private val gate=Mutex() // NewPipe.init installs a process-global Downloader.
        private val idle=object:Downloader(){override fun execute(request:Request):Response=throw IOException("YouTube çözümleme oturumu kapalı")}
    }
    override suspend fun resolve(option:SourceOption,url:String):ResolvedSource=withContext(Dispatchers.IO) {
        val id=YoutubeLinks.videoId(url)?:throw DataFailure(FailureKind.PARSE,"YouTube video kimliği geçersiz","youtube")
        gate.withLock {
            val owner=currentCoroutineContext().job
            var requests=0
            val started=System.nanoTime()
            val downloader=object:Downloader(){
                override fun execute(request:Request):Response {
                    owner.ensureActive()
                    if(++requests>32||System.nanoTime()-started>35_000_000_000L)throw IOException("YouTube çözümleme sınırı")
                    val u=request.url().toHttpUrl()
                    if(u.scheme!="https"||listOf("youtube.com","youtube-nocookie.com","ytimg.com","googlevideo.com","googleapis.com","google.com","youtu.be").none{u.host==it||u.host.endsWith(".$it")})throw IOException("YouTube kaynak alanı geçersiz")
                    val headers=request.headers().mapValues{it.value.joinToString(", ")}.toMutableMap()
                    if(headers.keys.none{it.equals("User-Agent",true)})headers["User-Agent"]=YoutubeStreams.USER_AGENT
                    val r=runBlocking(owner){network.request(WebRequest(u.toString(),method=request.httpMethod(),
                        body=request.dataToSend()?.toString(Charsets.UTF_8),headers=headers,timeoutMs=12000,byteLimit=4*1024*1024))}
                    return Response(r.code,"",r.headers.mapValues{listOf(it.value)},r.body,r.url)
                }
            }
            try {
                runInterruptible {
                    NewPipe.init(downloader,Localization("tr","TR"),ContentCountry("TR"))
                    val extractor=ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$id")
                    val info=StreamInfo.getInfo(extractor)
                    if(info.ageLimit!=0)throw DataFailure(FailureKind.ACCESS,"YouTube: yaş kısıtlı içerik açılamıyor","youtube")
                    val audio=info.audioStreams.filter{it.isUrl&&it.format in listOf(MediaFormat.M4A,MediaFormat.WEBMA,MediaFormat.WEBMA_OPUS,MediaFormat.OPUS)&&it.content.startsWith("https://")}
                        .sortedWith(compareBy<org.schabi.newpipe.extractor.stream.AudioStream>{when(it.audioLocale?.language){"tr","tur"->0;"en","eng"->1;else->2}}
                            .thenBy{if(it.format==MediaFormat.M4A)0 else 1}.thenByDescending{it.averageBitrate})
                        .distinctBy{it.audioLocale?.language.orEmpty()+it.audioTrackId.orEmpty()}.take(2)
                        .map{MediaAudio(it.content,it.audioLocale?.language.orEmpty(),it.audioTrackName.orEmpty(),it.format?.mimeType?:"audio/mp4")}
                    val variants=(info.videoOnlyStreams+info.videoStreams).filter{it.isUrl&&it.format in listOf(MediaFormat.MPEG_4,MediaFormat.WEBM)&&(!it.isVideoOnly||audio.isNotEmpty())}
                        .map{MediaVariant(it.itag.toString(),it.content,it.height,it.width,if(it.isVideoOnly)audio else emptyList(),it.codec.orEmpty(),it.fps,it.format?.mimeType?:"video/mp4")}
                    val subtitles=runCatching{extractor.getSubtitles(MediaFormat.VTT)}.getOrDefault(emptyList()).filter{it.isUrl&&publicUrl(it.content)?.startsWith("https://")==true}
                        .map{Subtitle(it.content,it.displayLanguageName,it.languageTag,"vtt")}
                    owner.ensureActive()
                    if(variants.isEmpty()&&info.hlsUrl?.startsWith("https://")==true)ResolvedSource(option.id,option.label,info.hlsUrl,StreamType.HLS,
                        "https://www.youtube.com/",YoutubeStreams.USER_AGENT,subtitles=subtitles.take(8),playbackRequirements=setOf("youtube"))
                    else YoutubeStreams.assemble(option,variants,subtitles)
                }
            }catch(e:CancellationException){throw e}
            catch(e:DataFailure){throw e}
            catch(e:Exception){owner.ensureActive();throw DataFailure(FailureKind.UNSUPPORTED,"YouTube kaynağı açılamadı; yeniden deneyin","youtube",cause=e)}
            finally{NewPipe.init(idle,Localization("tr","TR"),ContentCountry("TR"))}
        }
    }
}
