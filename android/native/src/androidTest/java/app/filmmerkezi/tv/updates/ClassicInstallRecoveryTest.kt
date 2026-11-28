package app.filmmerkezi.tv.updates

import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.SocketException
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** The host force-stops the isolated QA package between the three phases. */
class ClassicInstallRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val directory = File(context.filesDir, "classic-install-recovery")
    private val original = File(directory, "original.json")
    private val host = "classic-install-recovery.example"
    private val stable = "p092"
    private val first = "p093"
    private val broken = "p094"
    private val version = "1.0.0"
    private val updatedVersion = "1.1.0"
    private val manager: DistributionManager
        get() {
            check(context.packageName.endsWith(".qa"))
            return DistributionManager.get(context)
        }

    @Before fun requireRestartHarness() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("classicInstallRecovery") == "true")
    }

    @Test fun downloadStopsAfterNetworkLoss() = runBlocking {
        val store = manager.store
        check(!original.exists()) { "Restore the preceding QA run before seeding another one." }
        val snapshot = store.snapshot()
        check(!store.hasInstallJob && !snapshot.has("pending") && !store.restartRequired)
        directory.mkdirs()
        original.writeText(snapshot.toString())
        val repo = createRepository()
        manager.trust(repo)
        val installer = PackageInstaller(manager)
        val key = "${repo.fingerprint}/${repo.index.id}"
        val requests = withTransport(failOn = broken) {
            installer.install(installer.plan(key, stable))
            installer.activate()
            ModuleRuntime.prepare(context)
            withTimeout(20_000) { while (store.restartRequired) delay(50) }
            assertTrue(store.snapshot().getJSONObject("packages").has("${repo.fingerprint}/$stable"))

            val plan = listOf(first, broken).flatMap { installer.plan(key, it) }
            manager.startPluginInstall(plan)
            withTimeout(20_000) { manager.pluginInstall.first { !it.busy } }
            val summary = installer.jobSummary()
            assertEquals(50, summary.percent)
            assertEquals("failed", summary.items.single { it.key.endsWith("/$broken") }.status)
            assertEquals(UpdateProblem.NETWORK, summary.items.single { it.key.endsWith("/$broken") }.problem)
        }
        assertEquals(listOf(stable, first, broken), requests)
        File(directory, "stable.json").writeText(store.snapshot().getJSONObject("packages").toString())
        File(directory, "pid").writeText(android.os.Process.myPid().toString())
    }

    @Test fun resumeAfterProcessDeathDownloadsOnlyRemainingPackage() = runBlocking {
        assertFreshProcess()
        val store = manager.store
        val installer = PackageInstaller(manager)
        val stablePackages = File(directory, "stable.json").readText()
        assertEquals(stablePackages, store.snapshot().getJSONObject("packages").toString())
        assertEquals(50, installer.jobSummary().percent)
        val requests = withTransport {
            manager.startPluginInstall()
            withTimeout(20_000) { manager.pluginInstall.first { !it.busy } }
            assertEquals(100, installer.jobSummary().percent)
        }
        assertEquals(listOf(broken), requests)
        assertEquals(stablePackages, store.snapshot().getJSONObject("packages").toString())
        installer.stageReady()
        assertEquals(stablePackages, store.snapshot().getJSONObject("packages").toString())
        installer.activate()
        assertTrue(store.snapshot().optBoolean("needsBoot"))
        assertTrue(store.snapshot().getJSONObject("packages").keys().asSequence().any { it.endsWith("/$broken") })
        assertFalse(store.hasInstallJob)
        File(directory, "pid").writeText(android.os.Process.myPid().toString())
    }

    @Test fun realStartupRestoresWorkingSetAfterEntryClassFailure() = runBlocking {
        assertFreshProcess()
        val store = manager.store
        withTimeout(30_000) { while (store.restartRequired) delay(50) }
        assertTrue(store.snapshot().optBoolean("recovered"))
        assertEquals(File(directory, "stable.json").readText(), store.snapshot().getJSONObject("packages").toString())
        val identity = store.snapshot().getJSONObject("packages").keys().asSequence().single { it.endsWith("/$stable") }
        assertWorkingEntry(identity, version)
    }

    @Test fun downloadAndStageSignedVersionUpdate() = runBlocking {
        val raw = File(directory, "updated-index.json").readBytes()
        val probe = RepositoryProbe("https://$host/${UpdateProtocol.INDEX}", raw, File(directory, "updated-index.sig").readText(), UpdateProtocol.index(raw))
        manager.trust(probe)
        val update = manager.catalog().updates.single { it.entry.id == stable }
        assertEquals(updatedVersion, update.entry.version)
        assertEquals(version, update.installed!!.module.version)
        val installer = PackageInstaller(manager)
        val requests = withTransport {
            manager.startPluginInstall(installer.plan(update.repository, stable))
            withTimeout(20_000) { manager.pluginInstall.first { !it.busy } }
            assertEquals(100, installer.jobSummary().percent)
        }
        assertEquals(listOf(stable), requests)
        installer.stageReady()
        val staged = manager.store.snapshot()
        assertEquals(version, PluginInventory.modules(staged.getJSONObject("packages")).getValue(update.key).module.version)
        assertEquals(updatedVersion, PluginInventory.modules(staged.getJSONObject("pending")).getValue(update.key).module.version)
        installer.activate()
        File(directory, "pid").writeText(android.os.Process.myPid().toString())
    }

    @Test fun realStartupAcceptsSignedVersionUpdate() = runBlocking {
        assertFreshProcess()
        val store = manager.store
        withTimeout(30_000) { while (store.restartRequired) delay(50) }
        val identity = store.snapshot().getJSONObject("packages").keys().asSequence().single { it.endsWith("/$stable") }
        assertWorkingEntry(identity, updatedVersion)
    }

    private suspend fun assertWorkingEntry(identity: String, expectedVersion: String) {
        val client = PluginWorkerClient(context, manager)
        try {
            val manifest = PluginInventory.modules(manager.store.snapshot().getJSONObject("packages")).getValue(identity).module
            assertEquals(expectedVersion, manifest.version)
            assertTrue(client.probe(identity, JSONObject().put("site", manifest.site)).getBoolean("independent"))
        } finally {
            client.stop()
        }
    }

    @Test fun restoreQaDistribution() = runBlocking {
        check(context.packageName.endsWith(".qa"))
        if (!original.isFile) return@runBlocking
        val snapshot = JSONObject(original.readText())
        ModuleRuntime.stop()
        manager.store.commit { state ->
            state.keys().asSequence().toList().forEach(state::remove)
            snapshot.keys().forEach { state.put(it, snapshot.get(it)) }
        }
        PackageInstaller(manager).cleanup()
        check(directory.listFiles().orEmpty().all { it.delete() })
        check(directory.delete())
    }

    private fun assertFreshProcess() {
        assertNotEquals(File(directory, "pid").readText().toInt(), android.os.Process.myPid())
    }

    private fun createRepository(): RepositoryProbe {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)
        fun sign(bytes: ByteArray) = Base64.getEncoder().encodeToString(Signature.getInstance("SHA256withRSA").run {
            initSign(keys.private)
            update(bytes)
            sign()
        })
        val fixtures = instrumentation.context.assets
        val pathPrefix = "independent-test/packages/independent-example/1.0.0"
        val workingEntry = fixtures.open("$pathPrefix/manifest.json").use { UpdateProtocol.module(it.readBytes()).entry }
        val dex = fixtures.open("$pathPrefix/plugin.jar").use { raw ->
            ZipInputStream(raw).use { zip ->
                while (zip.nextEntry?.name != "classes.dex") check(zip.available() > 0)
                zip.readBytes()
            }
        }
        val entries = JSONArray()
        val packages = listOf(stable to version, first to version, broken to version, stable to updatedVersion)
        for ((id, packageVersion) in packages) {
            val jar = ByteArrayOutputStream().apply {
                ZipOutputStream(this).use { zip ->
                    zip.putNextEntry(ZipEntry("classes.dex"))
                    zip.write(dex)
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("fixture.txt"))
                    zip.write((publicKey + id + packageVersion).toByteArray())
                    zip.closeEntry()
                }
            }.toByteArray()
            val path = "packages/$id/$packageVersion"
            val minApp = manager.installedCode
            val manifest = JSONObject().put("schemaVersion", 1).put("platform", "android").put("runtime", UpdateProtocol.RUNTIME)
                .put("sdkMin", 1).put("sdkMax", 2).put("type", "site").put("id", id).put("name", id.uppercase())
                .put("version", packageVersion).put("minAppVersionCode", minApp).put("minSdk", 23)
                .put("entryClass", if (id == broken) "app.filmmerkezi.addon.MissingQaEntry" else workingEntry)
                .put("site", JSONObject().put("id", id).put("name", id.uppercase()).put("base", "https://$host"))
                .put("origins", JSONArray(listOf("https://$host")))
                .put("artifact", JSONObject().put("path", "$path/plugin.jar").put("sha256", UpdateProtocol.sha(jar)).put("size", jar.size))
                .toString().toByteArray()
            val prefix = "$id-$packageVersion"
            File(directory, "$prefix-manifest.json").writeBytes(manifest)
            File(directory, "$prefix-manifest.sig").writeText(sign(manifest))
            File(directory, "$prefix-plugin.jar").writeBytes(jar)
            entries.put(JSONObject().put("type", "site").put("id", id).put("name", id.uppercase()).put("version", packageVersion)
                .put("manifest", "$path/manifest.json").put("manifestSha256", UpdateProtocol.sha(manifest)).put("minAppVersionCode", minApp))
        }
        val index = JSONObject().put("schemaVersion", 1).put("platform", "android").put("runtime", UpdateProtocol.RUNTIME)
            .put("id", "classic-install-recovery").put("name", "Classic install recovery QA").put("revision", 1)
            .put("publicKey", publicKey).put("packages", JSONArray((0 until packages.lastIndex).map(entries::getJSONObject)))
        val raw = index.toString().toByteArray()
        val updated = index.put("revision", 2).put("packages", entries).toString().toByteArray()
        File(directory, "updated-index.json").writeBytes(updated)
        File(directory, "updated-index.sig").writeText(sign(updated))
        return RepositoryProbe("https://$host/${UpdateProtocol.INDEX}", raw, sign(raw), UpdateProtocol.index(raw))
    }

    private suspend fun withTransport(failOn: String? = null, block: suspend () -> Unit): List<String> {
        val requests = mutableListOf<String>()
        val field = UpdateTransport::class.java.getDeclaredField("client").apply { isAccessible = true }
        val previous = field.get(manager.transport) as OkHttpClient
        val client = previous.newBuilder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host != host) return@addInterceptor chain.proceed(request)
            val id = request.url.pathSegments[1]
            val packageVersion = request.url.pathSegments[2]
            val file = request.url.pathSegments.last()
            if (file == "plugin.jar") {
                requests.add(id)
                if (id == failOn) throw SocketException("QA network disconnected")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("QA fixture")
                .body(File(directory, "$id-$packageVersion-$file").readBytes().toResponseBody()).build()
        }.build()
        field.set(manager.transport, client)
        try {
            block()
            return requests.toList()
        } finally {
            field.set(manager.transport, previous)
        }
    }
}
