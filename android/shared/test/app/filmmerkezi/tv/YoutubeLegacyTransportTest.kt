package app.filmmerkezi.tv

import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class YoutubeLegacyTransportTest {
    @Test fun tinyHeapsUseBoundedProgressiveTransportAndBufferTargets() {
        val base=OkHttpClient()
        val tinyHeap=16L*1024*1024
        val media=source("https://media.example/video.mp4",youtube=false).put("type","mp4")
        val transport=PlaybackHttpPolicy.mediaTransport(media,base,29,tinyHeap)
        assertEquals(listOf(Protocol.HTTP_1_1),transport.protocols)
        assertSame(base.connectionPool,transport.connectionPool)
        assertEquals(listOf(Protocol.HTTP_1_1),PlaybackHttpPolicy.mediaTransport(media,base,29,tinyHeap*2).protocols)
        assertSame(base,PlaybackHttpPolicy.mediaTransport(media,base,29,tinyHeap*2+1))
        assertSame(base,PlaybackHttpPolicy.mediaTransport(media,base,29,tinyHeap*4))
        assertSame(base,PlaybackHttpPolicy.mediaTransport(media.put("type","hls"),base,29,tinyHeap))
        assertEquals(4*1024*1024,BufferProfile.AUTO.targetBytes(tinyHeap))
        assertEquals(8*1024*1024,BufferProfile.AUTO.targetBytes(tinyHeap*2))
        assertEquals(16*1024*1024,BufferProfile.LONG.targetBytes(tinyHeap*4))
        assertEquals(32*1024*1024,BufferProfile.LONG.targetBytes(tinyHeap*8))
    }
    private fun source(url:String="https://rr1.googlevideo.com/videoplayback",youtube:Boolean=true)=JSONObject().put("url",url).put("playbackRequirements",JSONArray(if(youtube)listOf("youtube")else emptyList<String>()))
    @Test fun legacyYoutubeTransportKeepsSessionWhileUsingHttp1(){
        val base=OkHttpClient()
        for(api in listOf(23,24,25)){
            val result=PlaybackHttpPolicy.mediaTransport(source(),base,api)
            assertEquals(listOf(Protocol.HTTP_1_1),result.protocols)
            assertSame(base.cookieJar,result.cookieJar);assertSame(base.connectionPool,result.connectionPool);assertSame(base.dispatcher,result.dispatcher)
        }
    }
    @Test fun modernAndroidAndUnrelatedSourcesRetainTheirOriginalTransport(){
        val base=OkHttpClient()
        for(api in listOf(26,29,36))assertSame(base,PlaybackHttpPolicy.mediaTransport(source(),base,api))
        assertSame(base,PlaybackHttpPolicy.mediaTransport(source(youtube=false),base,24))
        assertSame(base,PlaybackHttpPolicy.mediaTransport(source("https://media.example/video.mp4"),base,24))
        assertSame(base,PlaybackHttpPolicy.mediaTransport(source("https://googlevideo.com.unrelated.invalid/videoplayback"),base,24))
    }
}
