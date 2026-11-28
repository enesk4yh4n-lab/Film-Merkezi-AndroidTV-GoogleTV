package app.filmmerkezi.tv.core

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class PlayerAvailabilityTest {
    private val installedSites = Sites.all.toList()
    private val knownSites = Sites.known.toList()

    @After fun restoreSites() {
        Sites.installed(installedSites)
        Sites.remember(knownSites)
    }

    @Test fun providerChallengeIsAnAccessFailureAndPreservesTheHttpStatus() {
        val challenge=providerHttpFailure(403,"challenge")!!
        assertEquals(FailureKind.ACCESS,challenge.kind)
        assertEquals("browser-challenge",challenge.stage)
        assertEquals(403,challenge.httpCode)
        assertEquals("http",providerHttpFailure(403,null)?.stage)
        assertNull(providerHttpFailure(200,null))
        assertNull(providerHttpFailure(200,"challenge")?.httpCode)
    }

    private fun option(site:String,base:String,frame:String,extractor:String=""):SourceOption {
        Sites.remember(listOf(SiteSpec(site,site,base,"/?s=","/","article","h2")))
        return SourceOption("sample",ContentRef(site,base+"/ornek/"),site,frame,extractor)
    }

    private fun transport(vararg responses:Pair<String,WebResponse>)=object:WebTransport {
        override suspend fun request(request:WebRequest):WebResponse=
            responses.toMap()[request.url]?:error("Unexpected request ${request.url}")
    }

    @Test fun spidyBrowserGateIsReportedBeforeLookingForMedia()=runBlocking {
        val frame="https://spidypro.com/embed/example"
        val source=option("p008","https://www.dizigom.news",frame,"spidypro")
        val body="""
            <title>Redirecting...</title><div id="loading" role="status"></div>
            <script>const probe = "ad-overlay"; getComputedStyle(probe); document.referrer;</script>
        """.trimIndent()
        val error=runCatching{SourceResolver(transport(frame to WebResponse(frame,body))).resolve(source)}
            .exceptionOrNull() as DataFailure
        assertEquals(FailureKind.ACCESS,error.kind)
        assertEquals("embed",error.stage)
    }

    @Test fun nadirReportsVerificationWithoutRequestingTheExternalPage()=runBlocking {
        val frame="https://player.nadirfilm.com/?tmdb=603"
        val api="https://player.nadirfilm.com/vs_src.php?type=movie&id=603"
        val source=option("p016","https://www.nadirfilm.com",frame)
        val server=transport(
            frame to WebResponse(frame,"<iframe id='player_iframe' data-api='$api'></iframe>"),
            api to WebResponse(api,"""{"src":"https://stellarconductornexus.com/embed/fixture"}"""),
        )
        val error=runCatching{SourceResolver(server).resolve(source)}.exceptionOrNull() as DataFailure
        assertEquals(FailureKind.ACCESS,error.kind)
        assertTrue(error.message.orEmpty().contains("tarayıcı doğrulaması"))
    }

    @Test fun playerJsUsesPublicMediaFromTheActivePlayerOnly()=runBlocking {
        val frame="https://play.liderfilm.cc/embed-abcd1234.html"
        val source=option("p018","https://liderfilmizle.vip",frame,"lider-playerjs")
        val html="""
            <div id="video"></div>
            <script>new Playerjs({id:"missing",file:"/trailer.mp4"});
            new Playerjs({id:"video",file:"/video/episode.m3u8"});</script>
        """.trimIndent()
        val result=SourceResolver(transport(frame to WebResponse(frame,html))).resolve(source)
        assertEquals("https://play.liderfilm.cc/video/episode.m3u8",result.url)
        assertEquals(StreamType.HLS,result.type)
        val outside=html.replace("/video/episode.m3u8","https://unrelated.example/episode.m3u8")
        val error=runCatching{SourceResolver(transport(frame to WebResponse(frame,outside))).resolve(source)}
            .exceptionOrNull() as DataFailure
        assertEquals(FailureKind.UNSUPPORTED,error.kind)
    }

    @Test fun liderReadsTheDeclaredNestedPlayerAndPreservesEpisodeContext() = runBlocking {
        val frame = "https://play.liderfilm.cc/embed-abcd1234.html"
        val nested = "https://x.ag2m5.cfd/embed-abcd1234.html"
        val endpoint = "https://x.ag2m5.cfd/dl?op=get_stream&view_id=12&hash=sample"
        val hls = "https://qp21.uk-traffic-076.com/hls2/test/master.m3u8"
        val source = option("p018", "https://liderfilmizle.vip", frame, "lider-playerjs")
        val literalBody = """
           <div id="active-player"></div>
           <script>new Playerjs({id:"active-player",file:""});
           fetch('/dl?op=get_stream&view_id=12&hash=sample');</script>
        """.trimIndent()
        val callbackBody = """
           <div id="active-player"></div>
           <script>fetch('/dl?op=get_stream&view_id=12&hash=sample')
           .then(function(r){return r.json();})
           .then(function(d){
             if(d.url){player=new Playerjs({id:"active-player",ready:"loaded",file:d.url});}
           });</script>
        """.trimIndent()
        for ((attribute, nestedBody) in listOf("data-frame-src" to literalBody, "src" to callbackBody)) {
            val requests = mutableListOf<WebRequest>()
            val network = object : WebTransport {
                override suspend fun request(request: WebRequest): WebResponse {
                    requests.add(request)
                    return when (request.url) {
                        frame -> WebResponse(frame, "<iframe $attribute='$nested'></iframe>")
                        nested -> WebResponse(nested, nestedBody)
                        endpoint -> WebResponse(endpoint, """{"url":"$hls"}""")
                        else -> error("Unexpected player request")
                    }
                }
            }
            val result = SourceResolver(network).resolve(source)
            assertEquals(listOf(frame, nested, endpoint), requests.map { it.url })
            assertEquals(listOf(source.content.pageUrl, frame, nested), requests.map { it.referer })
            assertEquals(source.id, result.id)
            assertEquals(hls, result.url)
            assertEquals(StreamType.HLS, result.type)
            assertEquals("https://x.ag2m5.cfd/", result.referer)
        }
        val inactivePlayer = transport(
            frame to WebResponse(frame, "<iframe src='$nested'></iframe>"),
            nested to WebResponse(nested, callbackBody.replace("<div id=\"active-player\"></div>", "")),
        )
        val failure = runCatching { SourceResolver(inactivePlayer).resolve(source) }.exceptionOrNull() as DataFailure
        assertEquals(FailureKind.UNSUPPORTED, failure.kind)
    }

    @Test fun liderRejectsAnotherPlayerIdentityAndPreservesAccessDenied() = runBlocking {
        val frame = "https://play.liderfilm.cc/embed-abcd1234.html"
        val nested = "https://x.ag2m5.cfd/embed-abcd1234.html"
        val endpoint = "https://x.ag2m5.cfd/dl?op=get_stream&view_id=12&hash=sample"
        val source = option("p018", "https://liderfilmizle.vip", frame, "lider-playerjs")
        val wrong = "<iframe src='https://x.ag2m5.cfd/embed-zxcv1234.html'></iframe>"
        val mismatch = runCatching { SourceResolver(transport(frame to WebResponse(frame, wrong))).resolve(source) }.exceptionOrNull() as DataFailure
        assertEquals(FailureKind.UNSUPPORTED, mismatch.kind)
        val player = """
            <div id="active"></div><script>new Playerjs({id:"active",file:""});
            fetch('/dl?op=get_stream&view_id=12&hash=sample');</script>
        """.trimIndent()
        val deniedResponses = transport(
            frame to WebResponse(frame, "<iframe data-frame-src='$nested'></iframe>"),
            nested to WebResponse(nested, player),
            endpoint to WebResponse(endpoint, """{"error":"unauthorized"}"""),
        )
        val denied = runCatching { SourceResolver(deniedResponses).resolve(source) }.exceptionOrNull() as DataFailure
        assertEquals(FailureKind.ACCESS, denied.kind)
        val firstHopDenied = object : WebTransport {
            override suspend fun request(request: WebRequest): WebResponse {
                assertEquals(frame, request.url)
                throw DataFailure(FailureKind.ACCESS,"HTTP 403","http",403)
            }
        }
        val forbidden = runCatching { SourceResolver(firstHopDenied).resolve(source) }.exceptionOrNull() as DataFailure
        assertEquals(403, forbidden.httpCode)
    }

    @Test fun staleDdiziGoogleVideoLinkShowsExpiryBeforeMediaIsRequested()=runBlocking {
        val frame="https://www.ddizi.tel/player/oynat/abcd0123456789abcd0123456789abcd"
        val source=option("ddizi","https://www.ddizi.tel",frame)
        fun response(expires:Long)=WebResponse(frame,"""
            <div id="video"></div>
            <script>jwplayer("video").setup({sources:[
                {file:"https://redirector.googlevideo.com/videoplayback?expire=$expires",type:"mp4"}
            ]});</script>
        """.trimIndent())
        val error=runCatching{SourceResolver(transport(frame to response(1))).resolve(source)}
            .exceptionOrNull() as DataFailure
        assertEquals("media-expiry",error.stage)
        val expiry=System.currentTimeMillis()/1000+3600
        val fresh=SourceResolver(transport(frame to response(expiry))).resolve(source)
        assertEquals(StreamType.MP4,fresh.type)
        assertEquals(expiry*1000,fresh.expiresAt)
    }
}
