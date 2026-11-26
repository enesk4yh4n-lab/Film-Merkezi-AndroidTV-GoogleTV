package app.filmmerkezi.tv.core

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class ResolverQuality101Test {
    private val ref=ContentRef("lovefilmizle","https://lovefilmizle.net/test/")
    @Test fun mailDoesNotPrefer720OverHigherAvailableQuality()=runBlocking {
        val url="https://my.mail.ru/video/123";val meta="https://my.mail.ru/video/meta/123"
        val videos=JSONArray(listOf(720,2160,1080).map{JSONObject().put("key","${it}p").put("url","https://cdn.example.test/$it.mp4")})
        val network=object:WebTransport{override suspend fun request(request:WebRequest)=WebResponse(request.url,
            if(request.url==url)JSONObject().put("metadataUrl",meta).toString()else JSONObject().put("videos",videos).toString())}
        val source=SourceResolver(network).resolve(SourceOption("mail",ref,"Mail",url))
        assertEquals("https://cdn.example.test/2160.mp4",source.url);assertEquals(setOf(720,1080,2160),source.variants.map{it.height}.toSet())
    }
    @Test fun okDirectFallbackDoesNotPreferHdOverFullQuality()=runBlocking {
        val metadata=JSONObject().put("movie",JSONObject().put("movieId","123"))
            .put("videos",JSONArray(listOf("hd","full","sd").map{JSONObject().put("name",it).put("url","https://cdn.example.test/$it.mp4")}))
        val config=JSONObject().put("flashvars",JSONObject().put("metadata",metadata)).toString()
        val network=object:WebTransport{override suspend fun request(request:WebRequest)=WebResponse(request.url,"<div data-options='$config'></div>")}
        val source=SourceResolver(network).resolve(SourceOption("ok",ref,"OK","https://ok.ru/videoembed/123"))
        assertEquals("https://cdn.example.test/full.mp4",source.url);assertEquals(setOf(480,720,1080),source.variants.map{it.height}.toSet())
    }
}
