package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class YoutubeIntegrationTest {
    private val content=ContentRef("ddizi","https://www.ddizi.tel/izle/91916/uzak-sehir-68-bolum-izle-hd1.htm",ContentKind.EPISODE)
    private val url="https://www.youtube.com/embed/5SAwvBpQuIw"
    @Test fun youtubeFormsRequireExactHostAndVideoId(){
        for(link in listOf(url,"https://www.youtube-nocookie.com/embed/5SAwvBpQuIw?rel=0","https://youtu.be/5SAwvBpQuIw","https://www.youtube.com/watch?v=5SAwvBpQuIw")){
            assertEquals("5SAwvBpQuIw",YoutubeLinks.videoId(link));assertTrue(SourceMenus.knownFrame(link))
        }
        for(link in listOf("https://notyoutube.com/embed/5SAwvBpQuIw","https://youtube.com.evil.test/embed/5SAwvBpQuIw","http://youtube.com/watch?v=5SAwvBpQuIw","https://www.youtube.com/embed/123")){
            assertNull(YoutubeLinks.videoId(link));assertFalse(SourceMenus.knownFrame(link))
        }
    }
    @Test fun ddiziInventoriesYoutubeAndOtherSourcesWithoutFetching(){
        val html="""<div class="video-g"><iframe src="$url"></iframe></div>
            <div class="parts"><a href="/izle/91916/other.htm">HLS</a></div>
            <aside><iframe src="https://www.youtube.com/embed/abcdefghijk"></iframe></aside>"""
        val options=SourceMenus.parse(Sites.get("ddizi"),html,content)
        val yt=options.single{it.extractor=="youtube"}
        assertEquals(url,yt.url);assertEquals(OptionState.AVAILABLE,yt.state);assertEquals(content,yt.content)
        assertTrue(options.any{it.extractor=="page"})
        val wrapped="""<div class="video-g"><iframe src="/player/telif/index.php?id=https://www.youtube.com/watch?v=5SAwvBpQuIw"></iframe></div>"""
        val actual=SourceMenus.parse(Sites.get("ddizi"),wrapped,content).single{it.extractor=="youtube"}
        assertEquals("5SAwvBpQuIw",YoutubeLinks.videoId(actual.url))
        assertNull(YoutubeLinks.frameUrl("https://unrelated.test/player/telif/index.php?id=$url",content.pageUrl))
    }
    @Test fun resolverUsesYoutubeAdapterFromDirectAndNestedIframe()=runBlocking {
        var calls=0;val pages=mutableListOf<String>()
        val transport=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{pages+=request.url;return WebResponse(request.url,"""<div class="video-g"><iframe src="$url"></iframe></div>""")}}
        val adapter=YoutubeResolver{option,link->calls++;assertEquals(url,link);ResolvedSource(option.id,option.label,"https://media.example.test/a.mp4",StreamType.MP4)}
        val resolver=SourceResolver(transport,adapter);val o=SourceOption("ddizi|yt",content,"YouTube",url)
        assertEquals(o.id,resolver.resolve(o).id);assertTrue(pages.isEmpty())
        assertEquals(o.id,resolver.resolve(o.copy(url=content.pageUrl)).id);assertEquals(2,calls);assertEquals(1,pages.size)
    }
    @Test fun streamsKeepHighResHighFpsAndCodecFamiliesWithCombinedFallback(){
        fun v(id:String,h:Int,a:Boolean=false,fps:Int=25,codec:String="avc1.4D401F")=MediaVariant(id,"https://video.example.test/$id?expire=2000000000",h,1280,
            if(a)listOf(MediaAudio("https://video.example.test/a","tr","Türkçe"))else emptyList(),codec,fps)
        val o=SourceOption("ddizi|yt",content,"YouTube",url)
        val s=YoutubeStreams.assemble(o,listOf(v("720",720,true),v("360-separate",360,true),v("360-combined",360),v("high",1080),v("60fps",720,true,60),v("4k",2160,true,60,"vp09").copy(mimeType="video/webm"),v("8k",4320,true,60,"av01")),emptyList())
        assertEquals("8k",s.variants.first().id);assertEquals(7,s.variants.size)
        assertTrue(s.variants.any{it.id=="360-combined"&&it.audio.isEmpty()});assertEquals(2000000000000,s.expiresAt)
        val json=Contracts.source(s);assertEquals(7,json.getJSONArray("variants").length());assertEquals("tr",json.getJSONArray("audioStreams").getJSONObject(0).getString("language"))
    }
    @Test fun cancellingNewpipeCancelsOnlyItsOwnRequest()=runBlocking {
        val entered=CompletableDeferred<Unit>();val cancelled=CompletableDeferred<Unit>()
        val network=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
            entered.complete(Unit);try{delay(60000);error("unreachable")}finally{cancelled.complete(Unit)}
        }}
        val job=launch{NewPipeYoutube(network).resolve(SourceOption("ddizi|yt",content,"YouTube",url),url)}
        withTimeout(6000){entered.await()};job.cancel();withTimeout(3000){job.join();cancelled.await()}
        assertTrue(isActive)
    }
    @Test fun usesReportedHlsWhenNoEligibleVideoVariantRemains() {
        val option=SourceOption("ddizi|yt",content,"YouTube",url)
        val hls="https://manifest.googlevideo.com/sample/index.m3u8"
        val incomplete=MediaVariant("360","https://video.example.test/360.mp4",0,0)
        val subtitles=listOf(Subtitle("https://video.example.test/tr.vtt","Türkçe","tr","vtt"))
        val fallback=YoutubeStreams.assemble(option,listOf(incomplete),subtitles,hls)
        assertEquals(StreamType.HLS,fallback.type)
        assertEquals(option.id,fallback.id)
        assertEquals(hls,fallback.url)
        assertEquals(subtitles,fallback.subtitles)
        assertEquals(setOf("youtube"),fallback.playbackRequirements)
        val valid=incomplete.copy(height=360,width=640)
        assertEquals(StreamType.MP4,YoutubeStreams.assemble(option,listOf(valid),subtitles,hls).type)
        for(bad in listOf(null,"", "http://manifest.googlevideo.com/sample/index.m3u8", "https://localhost/sample/index.m3u8")) {
            val error=assertThrows(DataFailure::class.java){YoutubeStreams.assemble(option,listOf(incomplete),subtitles,bad)}
            assertEquals(FailureKind.UNSUPPORTED,error.kind)
        }
    }
    @Test fun explicitHlsOptionKeepsTheEpisodeAndRequiresAnActualManifest() {
        val option = SourceOption("ddizi|yt", content, "YouTube", url, "youtube")
        val other = SourceOption("ddizi|other", content, "Diğer", content.pageUrl)
        val options = YoutubeStreams.withHlsOptions(listOf(option, other))
        val alternative = options.last()
        assertEquals(listOf(option, other), options.dropLast(1))
        assertEquals(option.content, alternative.content)
        assertEquals(option.url, alternative.url)
        assertNotEquals(option.id, alternative.id)
        assertEquals(options, YoutubeStreams.withHlsOptions(options))
        assertEquals(listOf(option.copy(state=OptionState.ACCESS_REQUIRED)),
            YoutubeStreams.withHlsOptions(listOf(option.copy(state=OptionState.ACCESS_REQUIRED))))
        val mp4 = MediaVariant("360", "https://video.example.test/360.mp4", 360, 640)
        val manifest = "https://manifest.googlevideo.com/sample/index.m3u8"
        assertEquals(StreamType.HLS,YoutubeStreams.assemble(alternative,listOf(mp4),emptyList(),manifest).type)
        assertThrows(DataFailure::class.java) { YoutubeStreams.assemble(alternative,listOf(mp4),emptyList()) }
    }
}
