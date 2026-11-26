package app.filmmerkezi.tv

import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class YoutubeLegacyTransportTest {
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
