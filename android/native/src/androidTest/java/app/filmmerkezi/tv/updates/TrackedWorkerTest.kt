package app.filmmerkezi.tv.updates

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.NativeMainActivity
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.TimeUnit

/** Checks the signed tracked repository on the existing shrunk QA host. */
class TrackedWorkerTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val context = ins.targetContext
    private fun asset(name: String) = ins.context.assets.open("tracked-test/$name").bufferedReader().use { it.readText() }
    private fun payload(id: String) = JSONObject().put("site", PluginWire.site(Sites.get(id)))
    private fun artifact(name: String, json: Any) = File(context.getExternalFilesDir(null), name).writeText(json.toString())

    fun catalogs() = runBlocking {
        PilotWorkerTest().withSet("tracked-test") { client ->
            val tests = JSONArray(asset("cases.json"))
            val snapshot = DistributionManager.get(context).store.snapshot().getJSONObject("packages")
            assertEquals(26, snapshot.length())
            assertEquals(13, Sites.all.size)
            val rows = JSONArray()
            for (index in 0 until tests.length()) {
                val item = tests.getJSONObject(index)
                val id = item.getString("id")
                val code = item.getString("code")
                for (prefix in listOf("p", "r")) {
                    val key = snapshot.keys().asSequence().single { it.endsWith("/$prefix$code") }
                    assertEquals(1, client.probe(key, payload(id)).getInt("sdk"))
                }
                val fixture = item.optJSONObject("catalog") ?: continue
                val page = PluginWire.page(client.call(id, "site", "parseList", payload(id)
                    .put("url", fixture.getString("url")).put("body", asset(fixture.getString("file")))))
                assertEquals(id, fixture.getInt("count"), page.cards.size)
                assertTrue(page.cards.all { it.ref.siteId == id })
                rows.put(JSONObject().put("id", id).put("cards", page.cards.size))
            }
            artifact("tracked-worker.json", JSONObject().put("packages", snapshot.length()).put("catalogs", rows))
        }
    }

    fun livePlayback() = runBlocking {
        PilotWorkerTest().withSet("tracked-test") { client ->
            val tests = JSONArray(asset("cases.json"))
            val rows = JSONArray()
            val selected = InstrumentationRegistry.getArguments().getString("trackedCode")?.split(',')?.toSet()
            for (index in 0 until tests.length()) {
                val item = tests.getJSONObject(index)
                val id = item.getString("id")
                val code = item.getString("code")
                if (selected != null && code !in selected) continue
                val samples = item.getJSONArray("samples")
                val row = JSONObject().put("code", code).put("samples", JSONArray())
                rows.put(row)
                if (samples.length() == 0) row.put("status", "NO_CAPTURED_PLAYABLE_SAMPLE")
                for (n in 0 until samples.length()) {
                    val seed = Contracts.readCard(samples.getJSONObject(n))
                    val results = JSONArray()
                    val sample = JSONObject().put("title", seed.title).put("sources", results)
                    row.getJSONArray("samples").put(sample)
                    try {
                        val detail = PluginWire.detail(client.call(id, "site", "detail", payload(id).put("card", Contracts.card(seed))))
                        if (detail.options.isEmpty()) sample.put("status", "NO_OPTIONS")
                        for (option in detail.options) {
                            val source = JSONObject().put("label", option.label).put("state", option.state.name)
                            results.put(source)
                            if (option.state != OptionState.AVAILABLE) source.put("reason", option.reason)
                            else try {
                                val resolved = withTimeout(38000) {
                                    client.call(id, "resolver", "resolve", payload(id).put("option", Contracts.option(option)))
                                }
                                source.put("resolved", true).put("type", resolved.getString("type"))
                                play(resolved, source)
                            } catch (failure: Exception) {
                                source.put("error", safe(failure))
                            }
                            artifact("tracked-live-playback.json", rows)
                        }
                    } catch (failure: Exception) {
                        sample.put("error", safe(failure))
                    }
                }
                artifact("tracked-live-playback.json", rows)
            }
        }
    }

    fun untestedPlayback() = runBlocking {
        PilotWorkerTest().withSet("tracked-test") { client ->
            val targetFile = InstrumentationRegistry.getArguments().getString("trackedTargets") ?: "untested.json"
            require(targetFile.matches(Regex("[a-z0-9-]+\\.json")))
            val targets = JSONArray(asset(targetFile))
            val only = InstrumentationRegistry.getArguments().getString("trackedCode")?.split(',')?.toSet()
            val youtubeHls = InstrumentationRegistry.getArguments().getString("trackedYoutubeHls") == "true"
            val rows = JSONArray()
            for (i in 0 until targets.length()) {
                val target = targets.getJSONObject(i)
                if (only != null && target.getString("code") !in only) continue
                val id = target.getString("site")
                val title = target.getString("title")
                val series = target.optString("seriesPage").takeIf(String::isNotBlank)
                    ?.let { ContentRef(id, it, ContentKind.SERIES) }
                val seed = Card(ContentRef(id, target.getString("page"), ContentKind.valueOf(target.getString("kind"))), title,
                    episodeNumber = series?.let { EpisodeNumber(target.getInt("season"), target.getInt("episode")) },
                    seriesRef = series)
                val matched = JSONArray()
                val row = JSONObject().put("code", target.getString("code")).put("family", target.getString("family"))
                    .put("title", title).put("sources", matched)
                rows.put(row)
                try {
                    val detail = withTimeout(16000) {
                        if (youtubeHls) NativeSiteProvider(Sites.get(id), ProviderNetwork(context), PluginGateway.parsing).detail(seed)
                        else PluginWire.detail(client.call(id, "site", "detail", payload(id).put("card", Contracts.card(seed))))
                    }
                    if (series != null) {
                        assertEquals(seed.ref, detail.card.ref)
                        assertTrue(detail.options.all { it.content == seed.ref })
                        row.put("episodeRefPreserved", true)
                    }
                    val labels = Regex(if (youtubeHls) "^YouTube · HLS$" else target.getString("label"), RegexOption.IGNORE_CASE)
                    val host = target.optString("optionHost").takeIf(String::isNotBlank)?.let(::Regex)
                    val options = detail.options.filter { option ->
                        labels.containsMatchIn(option.label) &&
                            (host == null || host.matches(java.net.URI(option.url).host.orEmpty()))
                    }
                    row.put("observedOptions", JSONArray(detail.options.map { it.label }))
                    if (options.isEmpty()) row.put("status", "NO_TARGET_OPTION")
                    for (option in options.take(3)) {
                        val source = JSONObject().put("label", option.label).put("state", option.state.name)
                        matched.put(source)
                        if (option.state != OptionState.AVAILABLE) {
                            source.put("reason", option.reason)
                            continue
                        }
                        try {
                            val resolved = withTimeout(21000) {
                                client.call(id, "resolver", "resolve", payload(id).put("option", Contracts.option(option)))
                            }
                            val mediaHost = java.net.URI(resolved.getString("url")).host.orEmpty()
                            val format = resolved.getString("type")
                            source.put("resolved", true).put("type", format).put("mediaHost", mediaHost)
                            val expectedFormat = if (youtubeHls) "hls" else target.optString("type")
                            val expectedHost = target.optString("mediaHost")
                            if ((expectedFormat.isNotBlank() && !expectedFormat.equals(format, true)) ||
                                (expectedHost.isNotBlank() && !Regex(expectedHost).matches(mediaHost))) {
                                source.put("status", "DIFFERENT_SOURCE")
                            } else {
                                play(resolved, source, testBudget = target.optLong("testBudgetMs", 24000).coerceIn(24000, 50000))
                            }
                        } catch (failure: Exception) {
                            source.put("error", safe(failure))
                        }
                    }
                } catch (failure: Exception) {
                    row.put("error", safe(failure))
                } finally {
                    artifact("tracked-untested-playback.json", rows)
                }
            }
            check(rows.length() > 0) { "No playback target matched the requested codes" }
        }
    }

    private fun safe(error: Exception) = JSONObject()
        .put("kind", (error as? DataFailure)?.kind?.name ?: error.javaClass.simpleName)
        .put("stage", (error as? DataFailure)?.stage.orEmpty())
        .put("http", (error as? DataFailure)?.httpCode ?: JSONObject.NULL)
        .put("message", Regex("""https?://\S+""").replace(error.message.orEmpty(), "[adres]").take(220))

    private suspend fun play(source: JSONObject, result: JSONObject, testBudget: Long = 45000) {
        val activity = ins.startActivitySync(Intent(context, NativeMainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as NativeMainActivity
        try {
            val requests = java.util.concurrent.CopyOnWriteArrayList<Boolean>()
            val http = okhttp3.OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).addNetworkInterceptor { chain ->
                val request = chain.request()
                requests.add(request.header("Referer").orEmpty() == source.optString("referer") &&
                    request.header("User-Agent") == source.optString("userAgent"))
                chain.proceed(request)
            }.build()
            ins.runOnMainSync {
                val start = activity.javaClass.declaredMethods.single { it.name.startsWith("startSourcesWithClientForTest") }
                start.isAccessible = true
                start.invoke(activity, listOf(source), http)
            }
            var state = JSONObject()
            val began = SystemClock.elapsedRealtime()
            do {
                delay(250)
                ins.runOnMainSync { state = activity.playbackSnapshotForTest() }
            } while (SystemClock.elapsedRealtime() - began < testBudget && state.isNull("error") &&
                !(state.optBoolean("firstFrame") && state.optLong("positionMs") >= 15000))
            val rendered = state.optBoolean("firstFrame")
            val position = state.optLong("positionMs")
            result.put("firstFrame", rendered).put("positionMs", position)
                .put("heapLimitBytes", Runtime.getRuntime().maxMemory())
                .put("bufferTargetBytes", state.optInt("bufferTargetBytes"))
                .put("engineInstances", state.optInt("engineInstancesUsed"))
                .put("headersMatch", requests.isNotEmpty() && requests.all { it })
                .put("passed", rendered && position >= 15000 && state.isNull("error") && state.optInt("engineInstancesUsed") == 1)
                .put("playbackError", if (state.isNull("error")) JSONObject.NULL else
                    Regex("""https?://\S+""").replace(state.optString("error", ""), "[adres]").take(220))
        } finally {
            ins.runOnMainSync { activity.finish() }
        }
    }
}
