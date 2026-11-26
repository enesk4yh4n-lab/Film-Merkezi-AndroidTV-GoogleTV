package app.filmmerkezi.tv.core

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EncryptedWrapper104Test {
    private val ref=JSONObject(checkNotNull(javaClass.getResource("/stream104/reference.json")).readText())
    private fun option(url:String)=SourceOption("dbx",ContentRef("dizibox","https://www.dizibox.live/visionquest-1-sezon-1-bolum-izle/",ContentKind.EPISODE),"DBX Pro",url)
    private fun resolver(html:String)=SourceResolver(object:WebTransport{override suspend fun request(request:WebRequest)=WebResponse(request.url,html)})
    @Test fun capturedEncryptedMolystreamResolvesBoundHlsWithoutExecutingItsScripts()=runBlocking {
        val body=checkNotNull(javaClass.getResource("/stream104/molystream.html")).readText()
        val source=resolver(body).resolve(option(ref.getString("page")))
        assertEquals(ref.getString("media"),source.url);assertEquals(ref.getString("page"),source.referer)
        assertEquals(StreamType.HLS,source.type);assertTrue(source.subtitles.isEmpty())
    }
    @Test fun anotherMoviesEndpointCannotBeImportedFromTheWrapper()=runBlocking {
        val body="""<script>jwplayer('sheplayer').setup({file:'https://dbx.molystream.org/embed/sheila/different-video',autostart:playControl});</script>"""
        try{resolver(body).resolve(option(ref.getString("page")));fail("Wrong video accepted")}catch(e:DataFailure){assertEquals(FailureKind.PARSE,e.kind)}
    }
    @Test fun explicitEmbeddingDenialIsAnAccessFailure()=runBlocking {
        try{resolver("<title>Access Blocked</title><h1>Erisim Engeli</h1>").resolve(option("https://vidmoly.net/embed-denied.html"));fail("Access denial ignored")}
        catch(e:DataFailure){assertEquals(FailureKind.ACCESS,e.kind)}
    }
}
