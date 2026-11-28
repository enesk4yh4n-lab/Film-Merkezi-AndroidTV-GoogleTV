package app.filmmerkezi.tv.core

import app.filmmerkezi.tv.SiteHttpPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class SourceRepairTest {
    private val ddizi=Sites.get("ddizi")
    private val original=ddizi.base+"/izle/12345/ornek-1-bolum-hd2.htm"
    private val actual=ddizi.base+"/izle/12345/ornek-1-bolum-hd1.htm"
    private val player=ddizi.base+"/player/oynat/123456789012345678901234567890ab"
    private val wrong=ddizi.base+"/izle/12346/ornek-2-bolum-hd1.htm"
    private val seed=Card(ContentRef(ddizi.id,original,ContentKind.EPISODE),"Örnek")
    private fun ddiziHtml(canonical:String)= """
        <link rel="canonical" href="$canonical">
        <h1>Örnek 1. Bölüm</h1>
        <script type="application/ld+json">{"@type":"TVSeries","url":"${ddizi.base}/diziler/123/ornek"}</script>
        <script type="application/ld+json">{"@type":"TVEpisode","name":"Örnek 1. Bölüm","url":"$canonical"}</script>
        <iframe src="https://www.youtube.com/embed/abcdefghijk"></iframe>
        <div class="video-g"><iframe src="$player"></iframe></div>
        <div class="parts"><a href="$wrong">Başka bölüm</a></div>
    """.trimIndent()

    @Test fun atomKeepsTheCatalogProfileThroughEmbedAndMediaRequests() = runBlocking {
        val owner = Sites.get("fullhdfilmizlesene")
        val film = ContentRef(owner.id, owner.base + "/film/ornek/", ContentKind.MOVIE)
        val hls = "https://media.example.test/main.m3u8"
        fun atomLiteral(value: String): String {
            val inner = Base64.getEncoder().encodeToString(value.toByteArray())
            val salt = "K9L"
            val shifted = inner.mapIndexed { index, ch -> (ch.code + salt[index % 3].code % 5 + 1).toChar() }.joinToString("")
            return Base64.getEncoder().encodeToString(shifted.toByteArray(Charsets.ISO_8859_1)).reversed()
        }
        val inlineData = JSONObject().put("m", "atom-player").put("cm", hls)
        val fixtures = mapOf(
            "https://rapidvid.org/embed/example" to """<div id="atom-player"></div>
                <script>window._p8=${JSONObject.quote(atomLiteral(inlineData.toString()))};</script>""",
            "https://rapidvid.net/embed/example" to """<script>jwSetup.sources=[{"file":av(${JSONObject.quote(atomLiteral(hls))})}];</script>""",
        )
        val expectedAgent = SiteHttpPolicy.forProvider(owner.id)
        for ((embed, fixture) in fixtures) {
            val transport = object : WebTransport {
                override suspend fun request(request: WebRequest): WebResponse {
                    assertEquals(embed, request.url)
                    assertEquals(film.pageUrl, request.referer)
                    assertEquals(expectedAgent, request.headers["User-Agent"])
                    return WebResponse(embed, fixture)
                }
            }
            val option = SourceOption("fixture", film, "Atom", embed)
            val resolved = SourceResolver(transport).resolve(option)
            assertEquals(hls, resolved.url)
            assertEquals(StreamType.HLS, resolved.type)
            assertEquals(expectedAgent, resolved.userAgent)
            assertEquals(embed, resolved.referer)
        }
    }

    @Test fun ddiziRetainsTheEpisodeIdentityAndUsesTheActivePlayerAndDeclaredMp4Type()=runBlocking {
        val requests=mutableListOf<WebRequest>()
        val transport=object:WebTransport {
            override suspend fun request(request:WebRequest):WebResponse {
                requests.add(request)
                return when(request.url) {
                    original->WebResponse(actual,ddiziHtml(original.replace("https:","http:")))
                    player->WebResponse(player,"""
                        <meta name="referrer" content="no-referrer"><div id="main-player"></div>
                        <script>jwplayer("main-player").setup({
                          sources:[{file:"https://media.example/videoplayback?id=1",label:"360p",type:"mp4"}]
                        });</script>
                    """.trimIndent())
                    else->error("Unexpected request ${request.url}")
                }
            }
        }
        val detail=NativeSiteProvider(ddizi,transport,Dispatchers.Default).detail(seed)
        assertEquals(seed.ref,detail.card.ref)
        assertEquals(seed.ref,detail.options.single().content)
        assertEquals(player,detail.options.single().url)
        val source=SourceResolver(transport).resolve(detail.options.single())
        assertEquals(StreamType.MP4,source.type)
        assertEquals("",source.referer)
        assertEquals(actual,requests.last().referer)
    }

    @Test fun ddiziRejectsAnUnrelatedCanonicalOrHttpRedirect()=runBlocking {
        assertThrows(DataFailure::class.java){CatalogParser(ddizi).detail(ddiziHtml(wrong),seed)}
        val transport=object:WebTransport {
            override suspend fun request(request:WebRequest)=WebResponse(wrong,ddiziHtml(wrong))
        }
        val failure=runCatching{NativeSiteProvider(ddizi,transport,Dispatchers.Default).detail(seed)}.exceptionOrNull()
        assertTrue(failure is DataFailure)
    }

    @Test fun brokenMailRuLiteralKeepsBothLanguageOptionsInsideTheirOwnHandlers() {
        val site=Sites.get("filmifullizle")
        val ref=ContentRef(site.id,site.base+"/film/ornek.html",ContentKind.MOVIE)
        val body="""
            <div class="sources"><button id="source2" dil="trd">MailRu</button><button id="source6" dil="tra">MailRu</button>
                <button id="source7" dil="tra">MailRu</button></div>
            <script>
              $("#source2").click(function(){ $(".video-container").html('<!--baslik:MailRu,trd--><iframe src='https://my.mail.ru/video/embed/12345'></iframe>'); });
              $("#source6").click(function(){ $(".video-container").html('<!--baslik:MailRu,tra--><iframe src='https://my.mail.ru/video/embed/12346'></iframe>'); });
              $("#source7").click(function(){ $(".video-container").html('<!--baslik:MailRu,tra--><iframe src='https://other.example/player'></iframe>'); });
            </script>
        """.trimIndent()
        val options=SourceMenus.parse(site,body,ref)
        assertEquals(listOf("MailRu · Türkçe Dublaj","MailRu · Türkçe Altyazılı"),options.map{it.label})
        assertEquals(listOf("12345","12346"),options.map{it.url.substringAfterLast('/')})
    }

    @Test fun trDiziKeepsTheEpisodeAcrossVariantSuffixesAndInventoriesItsActiveYoutubeFrame() = runBlocking {
        val site=SiteSpec("p012","TRDiziİzle","https://www.trdiziizle.tv","/?s=","/","article","h2")
        val old=site.base+"/ornek-194-bolum-izle-full-tek-parca/"
        val current=site.base+"/ornek-194-bolum-izle-1/"
        assertEquals(EpisodePages.episodePageKey(site,old),EpisodePages.episodePageKey(site,current))
        assertNotEquals(EpisodePages.episodePageKey(site,old),EpisodePages.episodePageKey(site,current.replace("194","193")))
        val ref=ContentRef(site.id,current,ContentKind.EPISODE)
        val body="""
            <link rel="canonical" href="$current">
            <div id="content">
                <iframe src="https://www.youtube.com/embed/Trailer1234"></iframe>
                <div class="dizialani"><div class="video-container">
                    <iframe src="https://www.youtube.com/embed/abcdefghijk"></iframe>
                </div></div>
            </div>
        """.trimIndent()
        val detail=CatalogParser(site).detail(body,Card(ref,"Örnek"))
        assertEquals("https://www.youtube.com/embed/abcdefghijk",detail.options.single().url)
        val transport = object : WebTransport {
            override suspend fun request(request: WebRequest): WebResponse {
                assertEquals(current, request.url)
                return WebResponse(current, body)
            }
        }
        val androidDetail = NativeSiteProvider(site, transport, Dispatchers.Default).detail(Card(ref, "Örnek"))
        assertEquals(listOf("YouTube", "YouTube · HLS"), androidDetail.options.map { it.label })
        assertTrue(androidDetail.options.all { it.content == ref && it.url == detail.options.single().url })
    }

    @Test fun providerJwHlsKeepsTheDeclaredTypeAndNoReferrerPolicy() = runBlocking {
        for (siteId in listOf("ddizi", "p012")) {
            val owner = Sites.get(siteId)
            val episodePage = if (siteId == "ddizi") owner.base + "/izle/12345/ornek-1-bolum.htm"
                else owner.base + "/ornek-1-bolum/"
            val frame = owner.base + "/player/oynat/123456789012345678901234567890ab"
            val manifest = "https://eu1.tabii.com/fixture/entry"
            val requests = mutableListOf<WebRequest>()
            val expectedAgent = if (siteId == "p012") SiteHttpPolicy.BROWSER_USER_AGENT else SiteHttpPolicy.forProvider(siteId)
            val network = object : WebTransport {
                override suspend fun request(request: WebRequest): WebResponse {
                    requests.add(request)
                    assertEquals(frame, request.url)
                    assertEquals(expectedAgent, request.headers["User-Agent"])
                    return WebResponse(frame, """
                        <meta name="referrer" content="no-referrer"><div id="active"></div>
                        <script>jwplayer("active").setup({sources:[{file:"$manifest",type:"hls"}]});</script>
                    """.trimIndent())
                }
            }
            val option = SourceOption("fixture", ContentRef(siteId, episodePage, ContentKind.EPISODE), "JW", frame)
            val result = SourceResolver(network).resolve(option)
            assertEquals(StreamType.HLS, result.type)
            assertEquals(manifest, result.url)
            assertEquals("", result.referer)
            assertEquals(expectedAgent, result.userAgent)
            assertEquals(episodePage, requests.single().referer)
        }
    }

    @Test fun psourceUsesDeferredPartsBoundToTheActiveVideoAndHonorsNoReferrer() {
        val site=Sites.get("diziboxum")
        val ref=ContentRef(site.id,site.base+"/ornek-1-sezon-1-bolum/",ContentKind.EPISODE)
        val id="00000000-0000-0000-0000-000000000001"
        val frame="https://yabancidizim.com/psource/$id"
        val option=SourceMenus.parse(site,"<div class='video-container'><iframe data-frame-src='$frame'></iframe></div>",ref).single()
        val body="""
            <meta name="referrer" content="no-referrer"><div id="np"></div><script>
            window.NP_CONFIG={"id":"psource-$id","deferSource":true};
            window.PSOURCE_CONFIG={"id":"psource-$id","parentOrigin":"https://diziboxum.com","parts":[
                {"id":1,"label":"Twitter","url":"https://video.twimg.com/sample.m3u8"},
                {"id":2,"label":"Message","url":"https://video.twimg.com/message.m3u8"},
                {"id":3,"label":"Dış adres","url":"https://unknown.example/sample.m3u8"}
            ]};
            </script>
        """.trimIndent()
        assertEquals("Psource",option.label)
        val selected=KatrePlayer.options(option,KatrePlayer.parse(body,frame)).single()
        val media=KatrePlayer.resolve(selected,body,frame)
        assertEquals("https://video.twimg.com/sample.m3u8",media.url)
        assertEquals("",media.referer)
        assertThrows(DataFailure::class.java){KatrePlayer.parse(body.replace("https://diziboxum.com","https://other.example"),frame)}
    }

    @Test fun dzenFindsTheMatchingVideoDataAfterEnvironmentAndUnrelatedObjects()=runBlocking {
        val site=Sites.get("filmizlebabam")
        val id="Video12345"
        val frame="https://dzen.ru/embed/$id"
        val page=site.base+"/ornek/"
        val stream="https://strm.yandex.ru/test/master.m3u8"
        val configuration="""
            <meta property="og:vh-uuid" content="$id">
            <script>var _params = ({"environment":"fixture"});</script>
            <script>var _params = ({"ssrData":{"exportResponse":{"content":{"content_id":"Other1234","streams":[]}}}});</script>
            <script>var _params = ({"ssrData":{"exportResponse":{"content":{"content_id":"$id","streams":[{"type":"hls","url":"$stream"}]}}}});</script>
        """.trimIndent()
        val transport=object:WebTransport {
            override suspend fun request(request:WebRequest):WebResponse {
                assertEquals(frame,request.url)
                return WebResponse(frame,configuration)
            }
        }
        val option=SourceOption("dzen",ContentRef(site.id,page),"Dzen",frame)
        val source=SourceResolver(transport).resolve(option)
        assertEquals(stream,source.url)
        assertEquals(StreamType.HLS,source.type)
    }
}
