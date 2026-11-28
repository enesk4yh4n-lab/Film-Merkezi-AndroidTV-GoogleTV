package app.filmmerkezi.tv.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DdiziEpisodeRecoveryTest {
    private val site=Sites.get("ddizi")
    private val archive=site.base+"/diziler/42/ornek-dizi"
    private val old=site.base+"/izle/100/ornek-dizi-68-bolum-izle-hd1.htm"
    private val fresh=site.base+"/izle/200/ornek-dizi-68-bolum-izle-hd2.htm"
    private val other=site.base+"/izle/201/ornek-dizi-69-bolum-izle-hd2.htm"
    private val parent=ContentRef(site.id,archive,ContentKind.SERIES)
    private val seed=Card(ContentRef(site.id,old,ContentKind.EPISODE),"Örnek Dizi 68. Bölüm",
        episodeNumber=EpisodeNumber(1,68),seriesRef=parent)

    private fun archiveHtml(vararg links:Pair<String,String>)= """
        <link rel="canonical" href="$archive">
        <div class="content_">
        ${links.joinToString(""){(url,label)->"<div class='dizi-boxpost-cat'><a href='$url'>$label</a></div>"}}
        </div>
    """.trimIndent()
    private fun episodeHtml(label:String)= """
        <link rel="canonical" href="$fresh">
        <h1>$label</h1>
        <script type="application/ld+json">{"@type":"TVEpisode","name":"$label","url":"$fresh"}</script>
        <div class="video-g"><iframe src="https://www.youtube.com/embed/abcdefghijk"></iframe></div>
    """.trimIndent()
    private fun provider(reply:suspend (WebRequest)->WebResponse)=NativeSiteProvider(site,object:WebTransport {
        override suspend fun request(request:WebRequest)=reply(request)
    },Dispatchers.Default)

    @Test fun recoveredArchiveLinkPreservesOldIdentityAndUsesCurrentSource()=runBlocking {
        val requests=mutableListOf<WebRequest>()
        val transport=provider {
            requests.add(it)
            when(it.url) {
                old->throw DataFailure(FailureKind.HTTP,"HTTP 404","http",404)
                archive->WebResponse(archive,archiveHtml(other to "Örnek Dizi 69. Bölüm",fresh to seed.title))
                fresh->WebResponse(fresh,episodeHtml(seed.title))
                else->error("Unexpected request ${it.url}")
            }
        }
        val detail=transport.detail(seed)
        assertEquals(seed.ref,detail.card.ref)
        assertEquals(parent,detail.card.seriesRef)
        assertEquals(listOf("YouTube", "YouTube · HLS"), detail.options.map { it.label })
        assertTrue(detail.options.all { it.content == seed.ref && it.fields["sourcePage"] == fresh })
        assertTrue(detail.options.all { it.url == "https://www.youtube.com/embed/abcdefghijk" })
        assertEquals(listOf(old,archive,fresh),requests.map{it.url})
        assertEquals(archive,requests.last().referer)
    }

    @Test fun staleEntryRejectsDifferentEpisodeAndAmbiguousReplacement()=runBlocking {
        for (body in listOf(
            archiveHtml(other to seed.title),
            archiveHtml(fresh to seed.title,fresh.replace("/200/","/202/") to seed.title),
        )) {
            val source=provider {
                when(it.url) {
                    old->throw DataFailure(FailureKind.HTTP,"HTTP 404","http",404)
                    archive->WebResponse(archive,body)
                    else->error("Unverified episode requested ${it.url}")
                }
            }
            val error=runCatching{source.detail(seed)}.exceptionOrNull() as DataFailure
            assertEquals(404,error.httpCode)
        }
    }

    @Test fun forbiddenEpisodeNeverLooksForReplacement()=runBlocking {
        val source=provider {
            assertEquals(old,it.url)
            throw DataFailure(FailureKind.ACCESS,"HTTP 403","http",403)
        }
        val error=runCatching{source.detail(seed)}.exceptionOrNull() as DataFailure
        assertEquals(403,error.httpCode)
    }

    @Test fun resolverUsesTheRecoveredPageAsRefererForTheSameEpisode()=runBlocking {
        val frame=site.base+"/player/oynat/abcdef1234567890abcdef1234567890"
        val requests=mutableListOf<WebRequest>()
        val network=object:WebTransport {
            override suspend fun request(request:WebRequest):WebResponse {
                requests.add(request)
                assertEquals(frame,request.url)
                return WebResponse(frame,"<video src='https://media.example/episode.mp4'></video>")
            }
        }
        val resolver=SourceResolver(network)
        val option=SourceOption("fixture",seed.ref,"Kaynak",frame,fields=mapOf("sourcePage" to fresh))
        assertEquals(StreamType.MP4,resolver.resolve(option).type)
        resolver.resolve(option.copy(fields=mapOf("sourcePage" to other)))
        assertEquals(listOf(fresh,old),requests.map{it.referer})
    }
}
