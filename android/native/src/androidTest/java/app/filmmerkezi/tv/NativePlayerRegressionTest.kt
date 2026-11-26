package app.filmmerkezi.tv

import android.content.Intent
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.net.ssl.SSLContext

/** Real Media3 + deterministic local HTTP faults. Never counted as live-site playback. */
@RunWith(AndroidJUnit4::class)
class NativePlayerRegressionTest {
    @Test fun stability() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue(args.getString("playerStability") == "true")
        val context = instrumentation.targetContext
        val report = objectOf("fixture" to true, "api" to android.os.Build.VERSION.SDK_INT, "abi" to android.os.Build.SUPPORTED_ABIS[0], "surface" to (args.getString("surface") ?: "surface"),
            "checks" to JSONArray(), "cases" to JSONArray(), "at" to System.currentTimeMillis())
        val file = File(context.getExternalFilesDir(null), "stage3-regression.json")
        val activity = instrumentation.startActivitySync(Intent(context, NativeMainActivity::class.java).putExtra("live", true).putExtra("surface", args.getString("surface")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as NativeMainActivity
        val base = "http://10.0.2.2:8768"
        val prefix = "run-" + SystemClock.elapsedRealtime()
        fun save() { file.writeText(report.toString(2)) }
        fun snapshot(): JSONObject { var value = JSONObject(); instrumentation.runOnMainSync { value = activity.playbackSnapshotForTest() }; return value }
        fun pss(): Int { val m = Debug.MemoryInfo(); Debug.getMemoryInfo(m); return m.totalPss }
        fun memory(): JSONObject {
            val m = Debug.MemoryInfo(); Debug.getMemoryInfo(m)
            return objectOf("pssKiB" to m.totalPss, "javaBytes" to Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
                "nativeHeapBytes" to Debug.getNativeHeapAllocatedSize(), "javaPss" to m.getMemoryStat("summary.java-heap"),
                "nativePss" to m.getMemoryStat("summary.native-heap"), "graphicsPss" to m.getMemoryStat("summary.graphics"), "otherPss" to m.getMemoryStat("summary.private-other"))
        }
        fun source(id: String, path: String = "master.m3u8", mode: String = "healthy", type: String = "hls") =
            objectOf("id" to id, "label" to id, "url" to "$base/case/$prefix-$id/$path?mode=$mode", "type" to type,
                "referer" to "https://fixture.example.test/", "userAgent" to "FM-Player-Fixture", "headers" to objectOf("X-FM-Playback" to "yes"))
        fun start(vararg sources: JSONObject) { instrumentation.runOnMainSync { activity.startSourcesForTest(sources.toList()) } }
        suspend fun awaitState(timeoutMs: Long = 30000, predicate: (JSONObject) -> Boolean): JSONObject {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            var state: JSONObject
            do { delay(250); state = snapshot(); if (predicate(state)) return state } while (SystemClock.elapsedRealtime() < deadline)
            error("Player condition timed out: $state")
        }
        suspend fun playing(ms: Long = 2000): JSONObject = awaitState { it.optBoolean("firstFrame") && it.optBoolean("isPlaying") && it.optLong("positionMs") >= ms && it.isNull("error") }
        suspend fun checkCase(name: String, block: suspend (JSONObject) -> Unit) {
            val filter = args.getString("caseFilter")
            if (!filter.isNullOrEmpty() && !name.contains(filter)) return
            val row = objectOf("name" to name); report.getJSONArray("cases").put(row)
            try { block(row); report.getJSONArray("checks").put(objectOf("name" to name, "passed" to true)) }
            catch (e: Throwable) { row.put("failure", e.stackTraceToString()); report.getJSONArray("checks").put(objectOf("name" to name, "passed" to false, "error" to e.message)) }
            finally { row.put("last", snapshot()); instrumentation.runOnMainSync { activity.stopSourcesForTest() }; save() }
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "PLAYER $name: " + if (row.has("failure")) "FAIL\n" else "PASS\n") })
        }
        fun requireState(value: Boolean, message: String) { check(value) { message } }
        try {
            checkCase("403 -> next source; same engine") { row ->
                start(source("denied", "denied.m3u8"), source("fallback"))
                val s = playing(); row.put("playing", s)
                requireState(s.getInt("sourceSwitches") == 1 && s.getInt("engineInstancesUsed") == 1, "Fallback recreated or failed to switch engine")
            }
            checkCase("all sources fail once; terminal message") { row ->
                start(source("missing", "not-found.m3u8"), source("html", "html.m3u8"))
                val s = awaitState { !it.isNull("error") }; row.put("terminal", s)
                delay(1500)
                requireState(snapshot().getInt("sourceSwitches") == 1 && snapshot().getInt("engineInstancesUsed") == 1, "Terminal failure looped")
            }
            checkCase("transient HTTP 503 recovers") { row ->
                start(source("transient", mode = "503-once")); val s = playing(22000); row.put("playing", s)
                val log = JSONArray(URL("$base/log").readText())
                requireState(log.objects().any { it.optString("path").contains("$prefix-transient/") && it.optInt("status") == 503 }, "503 fixture not exercised")
                requireState(s.optInt("sourceSwitches") == 0 && s.isNull("error"), "Transient error exhausted source")
            }
            checkCase("truncated segment resumes with byte range") { row ->
                start(source("truncated", mode = "drop-once")); val s = playing(22000); row.put("playing", s)
                val log = JSONArray(URL("$base/log").readText())
                val calls = log.objects().filter { it.optString("path").contains("$prefix-truncated/clip-4.ts") }
                row.put("requests", JSONArray(calls))
                requireState(calls.any { it.optBoolean("truncated") } && calls.size >= 2, "Truncated segment was not retried")
                requireState(s.isNull("error"), "Truncated transfer did not recover")
            }
            checkCase("startup watchdog bounds an unresponsive source") { row ->
                val began = SystemClock.elapsedRealtime()
                start(source("startup-stall", mode = "prepare-stall"), source("after-startup-stall"))
                val s = awaitState(28000) { it.optInt("sourceIndex") == 1 && it.optBoolean("isPlaying") }
                row.put("elapsedMs", SystemClock.elapsedRealtime() - began).put("playing", s)
                requireState(s.optString("lastFailure") == "watchdog:STARTUP" && s.optInt("engineInstancesUsed") == 1, "Unresponsive startup escaped watchdog")
            }
            checkCase("mid-play outage switches source and keeps position") { row ->
                start(source("controlled-outage"), source("after-outage")); val before = playing(4000)
                URL("$base/case/$prefix-controlled-outage/control?outageMs=60000").readText()
                val s = awaitState(50000) { it.optInt("sourceIndex") == 1 && it.optBoolean("isPlaying") }
                row.put("before", before).put("after", s)
                requireState(s.optLong("positionMs") >= before.optLong("positionMs") - 1000 && s.optInt("engineInstancesUsed") == 1 && s.isNull("error"), "Recovery lost position or recreated engine")
            }
            checkCase("wrong Hi10 declaration cannot hang forever") { row ->
                start(source("hi10", mode = "hi10"), source("after-hi10"))
                val s = awaitState(30000) { it.optInt("sourceIndex") == 1 && it.optBoolean("isPlaying") && it.optLong("positionMs") >= 2000 }
                row.put("playing", s)
                requireState(s.optInt("sourceSwitches") == 1 && s.optInt("engineInstancesUsed") == 1 && s.isNull("error"), "Hi10 fallback failed")
            }
            checkCase("overstated manifest profile does not reject supported video") { row ->
                start(source("overdeclared", mode = "overdeclared")); val s = playing(3000); row.put("playing", s)
                requireState(s.optInt("sourceSwitches") == 0 && s.isNull("error") && s.optString("videoCodec").startsWith("avc1.42", true), "Supported actual Baseline input was rejected by a High manifest label")
            }
            checkCase("single 5 Mbps rendition is not rejected by a bitrate label") { row ->
                start(source("single-5mbps", mode = "single-5mbps")); val s = playing(3000); row.put("playing", s)
                requireState(s.optInt("sourceSwitches") == 0 && s.isNull("error"), "A generic 5 Mbps master label rejected playable video")
            }
            checkCase("bounded AVC level probe keeps the actual profile") { row ->
                start(source("overlevel", mode = "overlevel")); val s = playing(3000); row.put("playing", s)
                requireState(s.optString("videoCapabilityProbe") == "avc-level" && s.optString("videoCodec").endsWith("3D", true) && s.optInt("sourceSwitches") == 0, "Overstated AVC level was rejected or hidden")
            }
            checkCase("DTS rejected; supported AAC selected") { row ->
                start(source("dts-aac", mode = "dts-aac")); val s = playing(3000); row.put("playing", s)
                requireState(s.optString("audioMime") == "audio/mp4a-latm" && s.optInt("audioRenderedBuffers") > 0, "Supported AAC was not decoded")
                requireState(s.getJSONArray("tracks").objects().none { it.optBoolean("selected") && it.optString("mime").contains("dts") }, "Unsupported DTS selected")
            }
            checkCase("DTS-only never silently continues video") { row ->
                start(source("dts-only", mode = "dts-only"), source("after-dts"))
                val s = awaitState { it.optInt("sourceIndex") == 1 && it.optBoolean("isPlaying") && it.optInt("audioRenderedBuffers") > 0 }
                row.put("playing", s); requireState(s.optInt("sourceSwitches") == 1, "DTS-only source remained silent")
            }
            checkCase("seek, pause and media resume") { row ->
                start(source("seek")); playing()
                instrumentation.runOnMainSync { activity.seekPlaybackForTest(35000) }; playing(36000)
                instrumentation.runOnMainSync { activity.seekPlaybackForTest(5000); activity.pausePlaybackForTest() }
                val paused = awaitState { !it.optBoolean("isPlaying") }; delay(22000)
                val later = snapshot(); row.put("paused", paused).put("later", later)
                requireState(later.isNull("error") && later.optInt("sourceSwitches") == 0 && !later.optBoolean("isPlaying"), "Pause triggered watchdog recovery")
                instrumentation.runOnMainSync { activity.resumePlaybackForTest() }; row.put("resumed", playing(7000))
            }
            checkCase("Home and return preserve position and pause") { row ->
                start(source("lifecycle")); playing(4000)
                val states = JSONArray()
                repeat(3) { n ->
                    if (n == 1) instrumentation.runOnMainSync { activity.pausePlaybackForTest() }
                    val before = snapshot()
                    ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME")).use { it.readBytes() }
                    awaitState { it.optBoolean("suspended") }
                    context.startActivity(Intent(context, NativeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    val after = awaitState { !it.optBoolean("suspended") && it.optBoolean("firstFrame") }
                    requireState(after.optLong("positionMs") >= before.optLong("positionMs") - 1000, "Resume position lost")
                    if (n == 1) requireState(!after.optBoolean("playWhenReady"), "User pause was lost")
                    states.put(objectOf("before" to before, "after" to after))
                    instrumentation.runOnMainSync { activity.resumePlaybackForTest() }; playing()
                }; row.put("cycles", states)
            }
            checkCase("MP4 progressive seek and natural end") { row ->
                start(source("mp4", "clip.mp4", type = "mp4")); playing(1000)
                instrumentation.runOnMainSync { activity.seekPlaybackForTest(3000) }
                val s = awaitState { it.optInt("state") == 4 }; row.put("ended", s)
                requireState(s.isNull("error") && s.optInt("sourceSwitches") == 0, "Natural end was treated as failure")
            }
            checkCase("20 foreground playback open/close cycles") { row ->
                val memory = JSONArray()
                val identities = mutableSetOf<Int>()
                repeat(20) { n ->
                    start(source("cycle-$n")); identities.add(playing(500).getInt("engineIdentity"))
                    instrumentation.runOnMainSync { activity.stopSourcesForTest() }
                    if (n in listOf(4, 9, 19)) {
                        System.gc(); delay(500)
                        memory.put(memory().put("cycle", n + 1).put("threads", File("/proc/self/task").list()?.size).put("fds", File("/proc/self/fd").list()?.size))
                        if (n != 9) File("/proc/self/smaps").copyTo(File(context.getExternalFilesDir(null), "player-smaps-${n + 1}.txt"), overwrite = true)
                    }
                }
                row.put("memory", memory).put("engineIdentities", JSONArray(identities.toList()))
                requireState(identities.size == 1, "Foreground playback recreated its engine")
                val growth = memory.getJSONObject(2).getInt("pssKiB") - memory.getJSONObject(0).getInt("pssKiB")
                row.put("growthKiB", growth); requireState(growth <= 10240, "20 cycles exceeded 10 MiB growth budget: $growth KiB")
            }
            val longMs = args.getString("longMs")?.toLongOrNull() ?: 0L
            if (longMs > 0) checkCase("long synthetic playback ${longMs / 60000} minutes") { row ->
                start(source("long", "long.m3u8")); playing()
                val samples = JSONArray(); var peak = 0; val start = SystemClock.elapsedRealtime()
                var lastSampleAt = start; var previouslyPlaying = true; var observedPlayingMs = 0L
                while (SystemClock.elapsedRealtime() - start < longMs + 30000) {
                    delay(5000); val state = snapshot().put("elapsedMs", SystemClock.elapsedRealtime() - start).put("pssKiB", pss())
                    val now = SystemClock.elapsedRealtime()
                    if (previouslyPlaying && state.optBoolean("isPlaying")) observedPlayingMs += now - lastSampleAt
                    lastSampleAt = now; previouslyPlaying = state.optBoolean("isPlaying"); state.put("observedPlayingMs", observedPlayingMs)
                    peak = maxOf(peak, state.getInt("pssKiB")); samples.put(state); row.put("samples", samples).put("peakPssKiB", peak); save()
                    requireState(state.isNull("error") && state.optInt("sourceSwitches") == 0, "Long playback failed")
                    if (observedPlayingMs >= longMs && state.optLong("positionMs") >= longMs) break
                }
                val last = snapshot(); row.put("elapsedMs", SystemClock.elapsedRealtime() - start).put("observedPlayingMs", observedPlayingMs)
                requireState(last.optBoolean("firstFrame") && last.optLong("positionMs") >= longMs, "Long playback did not advance")
                requireState(observedPlayingMs >= longMs, "Long playback spent excessive time stopped/buffering")
                requireState(peak <= 220 * 1024, "Long playback exceeded 220 MiB PSS budget: $peak KiB")
            }
        } finally { instrumentation.runOnMainSync { activity.finish() }; save() }
        val failed = report.getJSONArray("checks").objects().filter { !it.getBoolean("passed") }
        check(report.getJSONArray("checks").length() > 0 && failed.isEmpty()) { "${failed.size} player checks failed; see stage3-regression.json" }
    }
}
