package app.filmmerkezi.tv.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PackedSeededSourceTest {
    @Test fun missingNamedConfigDoesNotPickAnUnrelatedLaterObject()=runBlocking {
        val page="https://rapid.filmmakinesi.to/embed-test/"
        val body="<div id='player'></div><script>jwplayer('player').setup(missing); var advertisement={file:'https://cdn.example.test/advertisement.m3u8'};</script>"
        val resolver=SourceResolver(object:WebTransport{override suspend fun request(request:WebRequest)=WebResponse(page,body)})
        try{resolver.resolve(SourceOption("missing",ContentRef("filmmakinesi","https://filmmakinesi.to/film/test/"),"Rapid",page));fail("Unrelated object must not be selected")}
        catch(e:DataFailure){assertEquals(FailureKind.UNSUPPORTED,e.kind)}
        Unit
    }
    @Test fun activeNamedConfigurationDecodesPackedMediaAndKeepsCaptions()=runBlocking {
        val page="https://rapid.filmmakinesi.to/embed-rsyi6h14btcs/"
        val body=checkNotNull(javaClass.getResource("/repairs/filmmakinesi-rapid.html")).readText()
        var calls=0
        val resolver=SourceResolver(object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
            assertEquals(page,request.url);calls++;return WebResponse(page,body)
        }})
        val source=resolver.resolve(SourceOption("rapid",ContentRef("filmmakinesi","https://filmmakinesi.to/film/orumcek-adam-yepyeni-bir-gun-2026-fmfxps/"),"DUAL Rapid",page))
        assertTrue(source.url.contains("master.txt")||source.url.contains("master.m3u8"))
        assertEquals(page,source.referer);assertTrue(source.subtitles.any{it.language=="en"})
        assertEquals(1,calls)
        Unit
    }
}
