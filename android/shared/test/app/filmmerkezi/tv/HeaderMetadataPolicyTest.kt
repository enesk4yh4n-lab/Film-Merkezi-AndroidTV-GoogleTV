package app.filmmerkezi.tv

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.*
import org.junit.Assert.*
import org.junit.Test

class HeaderMetadataPolicyTest {
    private val media="https://media.example/video.m3u8"
    private val original="https://provider.example/watch/"
    private fun source(declared:Boolean=true,subtitle:String="https://alternate.example/captions.vtt")=JSONObject()
        .put("id","plugin-source").put("url",media).put("referer",original)
        .put("playbackRequirements",JSONArray(if(declared)listOf("http-referer-from-subtitles")else emptyList<String>()))
        .put("subtitles",JSONArray().put(JSONObject().put("url",subtitle)))
    @Test fun declaredFallbackUsesActualMetadataAndStaysOnTheMediaHost() {
        val requests=mutableListOf<Request>();val accepted=mutableListOf<String>()
        val client=PlaybackHttpPolicy.client(source(),OkHttpClient(),accepted::add).newBuilder().addInterceptor{chain->
            val r=chain.request();requests.add(r);Response.Builder().request(r).protocol(Protocol.HTTP_1_1)
                .code(if(requests.size==1)403 else 200).message("fixture").body("fixture".toResponseBody()).build()
        }.build()
        client.newCall(Request.Builder().url(media).header("Referer",original).build()).execute().use{assertEquals(200,it.code)}
        assertEquals(listOf(original,"https://alternate.example/"),requests.map{it.header("Referer")})
        client.newCall(Request.Builder().url("https://unrelated.example/subtitle.vtt").header("Referer",original).build()).execute().close()
        assertEquals(original,requests.last().header("Referer"));assertEquals(listOf("https://alternate.example/"),accepted)
    }
    @Test fun genericSourcesAndUnsafeAlternateOriginsDoNotGetHeaderTrials() {
        for(s in listOf(source(false),source(subtitle="http://alternate.example/a.vtt"),source(subtitle="https://media.example/a.vtt"),source(subtitle="https://provider.example/a.vtt"))) {
            var calls=0
            val client=PlaybackHttpPolicy.client(s,OkHttpClient()){fail("No header selection expected")}.newBuilder().addInterceptor{chain->
                calls++;Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(404).message("fixture").body("".toResponseBody()).build()
            }.build()
            client.newCall(Request.Builder().url(media).header("Referer",original).build()).execute().close();assertEquals(1,calls)
        }
    }
}
