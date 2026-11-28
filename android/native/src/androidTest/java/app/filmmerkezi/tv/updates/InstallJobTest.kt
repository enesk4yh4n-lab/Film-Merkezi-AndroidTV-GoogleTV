package app.filmmerkezi.tv.updates

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class InstallJobTest {
    @Test fun onlyVerifiedDependencyClosedRootsCanBeStaged()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".qa"))
        val manager=DistributionManager.get(context)
        val original=manager.store.snapshot()
        val keys=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair()
        val publicKey=Base64.getEncoder().encodeToString(keys.public.encoded)
        val publisher=UpdateProtocol.fingerprint(publicKey)
        val repoKey="$publisher/local-parity"
        val repo=JSONObject().put("key",repoKey).put("fingerprint",publisher).put("publicKey",publicKey)
            .put("name","Local QA").put("url","https://example.test/index.json")
        val objects=File(manager.store.directory,"objects").apply{mkdirs()}
        val files=mutableListOf<File>()
        fun candidate(id:String,resolver:String?,cached:Boolean):PackageInstaller.Candidate {
            val out=ByteArrayOutputStream()
            ZipOutputStream(out).use{zip->
                zip.putNextEntry(ZipEntry("classes.dex"));zip.write(id.toByteArray());zip.closeEntry()
            }
            val bytes=out.toByteArray()
            val hash=UpdateProtocol.sha(bytes)
            if(cached)File(objects,"$hash.jar").also{it.writeBytes(bytes);files.add(it)}
            val version="1.0.0"
            val raw=JSONObject().put("schemaVersion",1).put("platform","android").put("runtime","kotlin-dex-v1")
                .put("sdkMin",1).put("sdkMax",2).put("id",id).put("name",id).put("version",version)
                .put("type",if(resolver==null)"resolver" else "site").put("entryClass","app.filmmerkezi.addon.QaEntry")
                .put("minAppVersionCode",39).put("minSdk",23).put("origins",JSONArray(listOf("https://example.test")))
                .put("artifact",JSONObject().put("sha256",hash).put("path","packages/$id/$version/plugin.jar").put("size",bytes.size))
                .apply{if(resolver!=null){
                    put("site",JSONObject().put("id",id).put("name",id).put("base","https://example.test"))
                    put("dependencies",JSONArray(listOf(JSONObject().put("publisher",publisher).put("id",resolver)
                        .put("minVersion","1.0.0").put("maxVersion","2.0.0"))))
                }}.toString().toByteArray()
            val signature=Signature.getInstance("SHA256withRSA").run{initSign(keys.private);update(raw);Base64.getEncoder().encodeToString(sign())}
            return PackageInstaller.Candidate(repo,UpdateProtocol.module(raw),raw,signature)
        }
        try{
            val one=candidate("r090",null,true)
            val two=candidate("p090","r090",true)
            val three=candidate("r091",null,false)
            val four=candidate("p091","r091",true)
            manager.store.commit{state->state.getJSONObject("repositories").put(repoKey,repo)}
            val installer=PackageInstaller(manager)
            val plan=listOf(one,two,three,four)
            try{installer.beginJob(plan.map{if(it===two)it.copy(signature="bad")else it});fail("Invalid signature accepted")}
            catch(_:Exception){assertTrue(installer.job().isEmpty())}
            installer.beginJob(plan)
            manager.store.commit{state->
                val job=state.getJSONObject("installJob")
                for(item in plan)job.getJSONObject(item.identity).put("status",if(item===three)"failed" else "ready")
            }
            val reopened=PackageInstaller(manager)
            assertEquals(4,reopened.job().size)
            reopened.stageReady()
            val staged=manager.store.snapshot()
            assertEquals(original.getJSONObject("packages").toString(),staged.getJSONObject("packages").toString())
            val pending=staged.getJSONObject("pending")
            assertTrue(pending.has(one.identity)&&pending.has(two.identity))
            assertFalse(pending.has(three.identity)||pending.has(four.identity))
            assertEquals(setOf(three.identity,four.identity),reopened.job().map{it.key}.toSet())
        }finally{
            manager.store.commit{state->state.keys().asSequence().toList().forEach(state::remove);original.keys().forEach{state.put(it,original.get(it))}}
            files.forEach{it.delete()}
        }
    }
}
