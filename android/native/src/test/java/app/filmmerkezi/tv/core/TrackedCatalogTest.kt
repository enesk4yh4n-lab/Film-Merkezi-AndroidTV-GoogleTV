package app.filmmerkezi.tv.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class TrackedCatalogTest {
    @Test fun recordedCatalogsAndDetails() {
        val directory = System.getenv("FM_TRACKED_HOME")
        assumeTrue("Use the captured catalogue directory", !directory.isNullOrBlank())
        val dir = File(checkNotNull(directory))
        val sites = JSONArray(File(dir, "sites.json").readText()).values().filterIsInstance<JSONObject>()
        val responses = JSONArray(File(dir, "http.json").readText()).values().filterIsInstance<JSONObject>()
        val reports = JSONArray()
        val examples = mutableListOf<Card>()
        for (entry in sites) {
            val code = entry.getString("code")
            val site = PluginWire.site(entry.getJSONObject("site"))
            Sites.remember(listOf(site))
            val parser = CatalogParser(site)
            val cards = mutableListOf<Card>()
            val siteResponses = responses.filter { it.string("code") == code }.sortedBy {
                when (it.string("scenario")) { "collection", "collection-curated" -> 2; "episode" -> 3; else -> 1 }
            }
            for (response in siteResponses) {
                val scenario = response.string("scenario")
                val row = JSONObject().put("code", code).put("scenario", scenario).put("http", response.optInt("status"))
                reports.put(row)
                val file = response.string("file")
                if (file.isBlank()) {
                    row.put("captureError", response.string("error"))
                    continue
                }
                val body = File(dir, file).readText()
                val url = response.string("url")
                try {
                    if (scenario.endsWith("detail") || scenario in setOf("collection", "collection-curated", "episode")) {
                        val fallback = when {
                            scenario == "episode" && site.id == "p012" -> Card(ContentRef(site.id, url, ContentKind.EPISODE), entry.string("probe"))
                            scenario == "movie-detail" && site.id == "p016" -> Card(ContentRef(site.id, url), entry.string("probe"))
                            else -> null
                        }
                        val seed = cards.firstOrNull {
                            it.ref.pageUrl.trimEnd('/') == url.trimEnd('/') ||
                                it.ref.kind == ContentKind.EPISODE && site.id == "diziboxum" &&
                                it.ref.pageUrl.trimEnd('/').substringAfterLast('-') == url.trimEnd('/').substringAfterLast('-')
                        }?.let { it.copy(ref = it.ref.copy(pageUrl = url)) } ?: fallback
                            ?: throw DataFailure(FailureKind.PARSE, "No catalogue seed for recorded detail", "fixture")
                        val parsed = parser.detail(body, seed)
                        val primary = if (site.id.startsWith("p")) parsed else PageIdentity.attach(body, parsed, seed)
                        val detail = AdultContent.detail(body, ContentMetadata.enrich(primary, body))
                        cards.addAll(detail.episodes.map { it.card })
                        row.put("detail", evidence(detail.card)).put("seed", Contracts.card(seed)).put("seasons", JSONArray(detail.seasons))
                            .put("episodes", detail.episodes.size).put("collectionItems", detail.collectionItems.size)
                            .put("options", JSONArray(detail.options.map {
                                JSONObject().put("label", it.label).put("host", java.net.URI(it.url).host).put("state", it.state.name)
                            }))
                    } else {
                        val result = parser.list(body, url)
                        cards.addAll(result.cards)
                        if (scenario == "search") examples.addAll(result.cards)
                        row.put("count", result.cards.size).put("kinds", JSONObject(result.cards.groupingBy { it.ref.kind.name }.eachCount()))
                            .put("withYear", result.cards.count { it.year != null })
                            .put("withOriginal", result.cards.count { it.originalTitle.isNotBlank() })
                            .put("withRating", result.cards.count { it.imdb != null })
                            .put("withGenres", result.cards.count { it.genres.isNotEmpty() })
                        if (scenario == "search") row.put("examples", JSONArray(result.cards.filter {
                            TextKey.fold(it.title).contains(TextKey.fold(entry.string("probe")))
                        }.take(6).map(::evidence)))
                        if (entry.has("site") && site.id.startsWith("p")) {
                            assertFalse("Episodes must be grouped", result.cards.any { it.ref.kind == ContentKind.EPISODE })
                        }
                    }
                } catch (error: Exception) {
                    row.put("parserError", error.message)
                }
            }
        }
        File(dir, "parser-results.json").writeText(reports.toString(2))
        val result = reports.values().filterIsInstance<JSONObject>()
        fun row(code: String, scenario: String) = result.single { it.string("code") == code && it.string("scenario") == scenario }
        assertEquals(sites.size, result.map { it.getString("code") }.distinct().size)
        assertEquals(2003, row("018", "detail").getJSONObject("detail").getInt("year"))
        assertTrue(row("016", "collection").optInt("collectionItems") >= 20)
        assertEquals(20, row("016", "collection-curated").optInt("collectionItems"))
        assertTrue(row("017", "detail").optInt("episodes") > 0)
        assertTrue(row("008", "series-detail").optInt("episodes") > 0)
        assertEquals(12, row("009", "detail").optInt("episodes"))
        assertEquals(1, row("017", "episode").getJSONArray("options").length())
        assertTrue(row("019", "detail").optJSONArray("options")!!.length() >= 2)
        assertTrue(row("015", "detail").getJSONArray("options").values().filterIsInstance<JSONObject>()
            .filter { it.string("label").startsWith("Sinefilm") }.all { it.string("state") == "UNSUPPORTED" })
        writeIdentityExamples(dir, examples)
    }

    private fun writeIdentityExamples(dir: File, cards: List<Card>) {
        fun find(id: String, title: String) = cards.first { it.ref.siteId == id && TextKey.fold(it.title) == TextKey.fold(title) }
        val comparisons = listOf(
            find("filmizlehell", "Matrix Revolutions") to find("p019", "Matrix 3: Revolutions"),
            find("filmizlehell", "Matrix Reloaded") to find("p019", "Matrix 2: Reloaded"),
            find("filmizlehell", "Matrix Revolutions") to find("p019", "Matrix 2: Reloaded"),
            find("p008", "Genç Sherlock") to find("p008", "Young Sherlock"),
            find("p016", "Animatrix") to find("p019", "The Animatrix"),
        )
        val result = comparisons.map { (a, b) ->
            val decision = ContentIdentity.decide(a, b)
            JSONObject().put("left", evidence(a)).put("right", evidence(b))
                .put("decision", decision.state.name).put("reason", decision.reason)
        }
        File(dir, "identity-examples.json").writeText(JSONArray(result).toString(2))
    }

    @Test fun episodeFeedUsesRealParentsAndAvsarChecksTheSeasonPanel() {
        val tr = SiteSpec("p012", "TRDiziİzle", "https://www.trdiziizle.tv", "/?s=", "/tum-bolumler/",
            ".listepisodes", "[id=dizi-ismi]", seriesPath = "/tum-bolumler/")
        val trBody = """
            <a href="/diziler/ornek/">Örnek</a>
            <div id="content">
              <article class="listepisodes"><a href="/ornek-12-bolum/"><span id="dizi-ismi">Örnek</span></a></article>
              <article class="listepisodes"><a href="/ornek-13-bolum/"><span id="dizi-ismi">Örnek</span></a></article>
              <article class="listepisodes"><a href="/diger-1-bolum/"><span id="dizi-ismi">Belirsiz</span></a></article>
            </div>
        """.trimIndent()
        val card = CatalogParser(tr).list(trBody, tr.base + tr.list).cards.single()
        assertEquals(ContentKind.SERIES, card.ref.kind)
        assertEquals(tr.base + "/diziler/ornek/", card.ref.pageUrl)
        val avsar = SiteSpec("p017", "Avşar Film", "https://www.avsarfilm.com.tr", "/ara?search=", "/film",
            ".movie-item", "h2", episodeSelector = "[data-season-panel] a.av-episode[href]")
        val parent = Card(ContentRef(avsar.id, avsar.base + "/watch/ornek", ContentKind.SERIES), "Örnek")
        val body = """
            <div data-season-panel="2">
                <a class="av-episode" href="/watch/ornek/s2e3">Doğru</a>
                <a class="av-episode" href="/watch/farkli/s2e3">Başka dizi</a>
                <a class="av-episode" href="/watch/ornek/s1e4">Yanlış panel</a>
            </div>
        """.trimIndent()
        val episode = CatalogParser(avsar).episodes(HtmlData.document(body, parent.ref.pageUrl), parent).single()
        assertEquals(EpisodeNumber(2, 3), episode.number)
        assertEquals(parent.ref, episode.card.seriesRef)
    }

    private fun evidence(c: Card) = JSONObject().put("title", c.title)
        .put("kind", c.ref.kind.name).put("url", c.ref.pageUrl).put("year", c.year ?: JSONObject.NULL)
        .put("yearHint", c.yearHint ?: JSONObject.NULL).put("original", c.originalTitle).put("aliases", JSONArray(c.aliases))
        .put("rating", c.imdb ?: JSONObject.NULL).put("imdbId", c.imdbId).put("genres", JSONArray(c.genres))
}
