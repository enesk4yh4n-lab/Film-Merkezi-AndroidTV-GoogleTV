package app.filmmerkezi.tv.tv

import android.content.DialogInterface
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.settings.AppSettings
import app.filmmerkezi.tv.updates.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class InstallProgressUiTest {
    @Test fun showsFeedbackBeforeNetworkAndResumesProgressAfterLeavingOptions()=runBlocking{
        val fixture=Settings110Fixture()
        check(fixture.context.packageName.endsWith(".qa"))
        val manager=DistributionManager.get(fixture.context)
        val original=manager.store.snapshot()
        val signer=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair()
        val publicKey=Base64.getEncoder().encodeToString(signer.public.encoded)
        val fingerprint=UpdateProtocol.fingerprint(publicKey)
        val host="install-progress.example"
        val beforeManifest=CountDownLatch(1)
        val beforeFailure=CountDownLatch(1)
        val beforeRetry=CountDownLatch(1)
        val firstDownloads=AtomicInteger()
        val secondDownloads=AtomicInteger()
        val payloads=mutableMapOf<String,ByteArray>()
        val hashes=mutableListOf<String>()
        val indexEntries=JSONArray()
        val dex=fixture.ins.context.assets.open("independent-test/packages/independent-example/1.0.0/plugin.jar").use{raw->
            ZipInputStream(raw).use{zip->while(zip.nextEntry?.name!="classes.dex")check(zip.available()>0);zip.readBytes()}
        }
        fun sign(bytes:ByteArray)=Base64.getEncoder().encodeToString(Signature.getInstance("SHA256withRSA").run{
            initSign(signer.private);update(bytes);sign()
        })
        for(id in listOf("p090","p091")){
            val path="packages/$id/1.0.0"
            val name=id.uppercase()+" - Fixture Site"
            val jar=ByteArrayOutputStream().also{output->ZipOutputStream(output).use{zip->
                zip.putNextEntry(ZipEntry("classes.dex"));zip.write(dex);zip.closeEntry()
                zip.putNextEntry(ZipEntry("fixture.txt"));zip.write(fingerprint.plus(id).toByteArray());zip.closeEntry()
            }}.toByteArray()
            val hash=UpdateProtocol.sha(jar);hashes.add(hash)
            val raw=JSONObject().put("schemaVersion",1).put("platform","android").put("runtime",UpdateProtocol.RUNTIME)
                .put("sdkMin",1).put("sdkMax",2).put("id",id).put("name",name).put("type","site").put("version","1.0.0")
                .put("entryClass","app.filmmerkezi.addon.Example").put("minAppVersionCode",39).put("minSdk",23)
                .put("site",JSONObject().put("id",id).put("name",name).put("base","https://$host"))
                .put("origins",JSONArray().put("https://$host"))
                .put("artifact",JSONObject().put("path","$path/plugin.jar").put("size",jar.size).put("sha256",hash)).toString().toByteArray()
            payloads["/$path/manifest.json"]=raw;payloads["/$path/manifest.sig"]=sign(raw).toByteArray();payloads["/$path/plugin.jar"]=jar
            indexEntries.put(JSONObject().put("id",id).put("version","1.0.0").put("type","site").put("name",name)
                .put("manifest","$path/manifest.json").put("manifestSha256",UpdateProtocol.sha(raw)).put("minAppVersionCode",39))
        }
        val index=JSONObject().put("schemaVersion",1).put("platform","android").put("runtime",UpdateProtocol.RUNTIME)
            .put("id","install-progress-qa").put("name","Install progress QA").put("publicKey",publicKey).put("revision",1)
            .put("packages",indexEntries).toString().toByteArray()
        val client=OkHttpClient.Builder().addInterceptor{chain->
            val request=chain.request()
            check(request.url.host==host)
            val path=request.url.encodedPath
            var code=200
            when(path){
                "/packages/p090/1.0.0/manifest.json"->check(beforeManifest.await(16,TimeUnit.SECONDS))
                "/packages/p090/1.0.0/plugin.jar"->firstDownloads.incrementAndGet()
                "/packages/p091/1.0.0/plugin.jar"->if(secondDownloads.incrementAndGet()==1){
                    check(beforeFailure.await(20,TimeUnit.SECONDS));code=503
                }else check(beforeRetry.await(16,TimeUnit.SECONDS))
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("QA fixture")
                .body((if(code==200)payloads.getValue(path)else "unavailable".toByteArray()).toResponseBody()).build()
        }.build()
        val field=UpdateTransport::class.java.getDeclaredField("client").apply{isAccessible=true}
        val prior=field.get(manager.transport)
        fun center()=checkNotNull(fixture.activity.updateCenterForTest())
        fun select(key:String)=fixture.main{center().select(key)}
        fun pressBack()=fixture.ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        fun percent()=(fixture.tag("updates:loading") as? ProgressBar)?.takeIf{it.isShown&&!it.isIndeterminate}?.progress
        val firstKey="catalog:$fingerprint/p090"
        val secondKey="catalog:$fingerprint/p091"
        try{
            manager.store.commit{it.remove("installJob");it.remove("pending");it.remove("needsBoot");it.remove("bootPending")}
            manager.trust(RepositoryProbe("https://$host/${UpdateProtocol.INDEX}",index,sign(index),UpdateProtocol.index(index)))
            field.set(manager.transport,client)
            fixture.start(AppSettings(),UpdateTestData.empty())
            fixture.main{fixture.activity.settingsForTest();fixture.tag("settings-category:updates").requestFocus()}
            fixture.waitFor(10000){fixture.activity.updateCenterForTest()?.keys()?.contains("add")==true}
            select("add");fixture.waitFor(10000){center().keys().containsAll(listOf(firstKey,secondKey))}
            fixture.main{
                assertEquals("P090",center().row(firstKey).title)
                assertEquals("P091",center().row(secondKey).title)
            }
            select(firstKey);fixture.waitFor(10000){center().keys().contains(secondKey)&&center().row(firstKey).selected}
            select(secondKey);fixture.waitFor(10000){center().keys().contains(secondKey)&&center().row(secondKey).selected}
            fixture.main{
                center().select("install-selected")
                assertEquals(fixture.activity.getString(R.string.install_preparing),(fixture.tag("updates:status") as TextView).text.toString())
                assertTrue((fixture.tag("updates:loading") as ProgressBar).isIndeterminate)
            }
            beforeManifest.countDown()
            fixture.waitFor(10000){fixture.activity.settingsDialogForTest()?.isShowing==true}
            fixture.main{fixture.activity.settingsDialogForTest()!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()}
            fixture.waitFor(10000){percent()==50&&center().actionKeys().contains("background")}
            fixture.main{
                assertEquals("P090",center().row("$fingerprint/p090").title)
                assertEquals("P091",center().row("$fingerprint/p091").title)
                assertTrue((fixture.tag("updates:status") as TextView).text.toString().endsWith("P091"))
            }
            fixture.screenshot("install-progress-50.png")

            pressBack();fixture.waitFor(10000){center().keys().contains("install-job")}
            fixture.main{assertTrue(center().row("install-job").description.contains("50"))}
            select("install-job");fixture.waitFor(10000){percent()==50&&center().actionKeys().contains("background")}
            select("background");fixture.waitFor(10000){center().keys().contains("install-job")}
            fixture.main{fixture.tag("settings-category:general").requestFocus()}
            fixture.waitFor(10000){fixture.activity.updateCenterForTest()==null&&manager.pluginInstall.value.busy}
            beforeFailure.countDown()
            withTimeout(12000){manager.pluginInstall.first{!it.busy}}
            fixture.main{fixture.tag("settings-category:updates").requestFocus()}
            fixture.waitFor(10000){fixture.activity.updateCenterForTest()?.keys()?.contains("install-job")==true}
            select("install-job")
            fixture.waitFor(10000){percent()==50&&center().actionKeys().contains("retry")}
            fixture.main{assertEquals(fixture.activity.getString(R.string.install_http_error,503),center().row("$fingerprint/p091").description)}
            select("retry")
            fixture.waitFor(10000){percent()==50&&center().actionKeys().contains("background")}
            beforeRetry.countDown()
            fixture.waitFor(10000){percent()==100&&fixture.tag("updates:continue")!=null}
            fixture.main{assertEquals(fixture.activity.getString(R.string.install_apply_restart),(fixture.tag("updates:continue") as TextView).text.toString())}
            assertEquals(1,firstDownloads.get());assertEquals(2,secondDownloads.get())
            assertEquals(original.getJSONObject("packages").toString(),manager.store.snapshot().getJSONObject("packages").toString())
            fixture.screenshot("install-progress-100.png")
        }finally{
            beforeManifest.countDown();beforeFailure.countDown();beforeRetry.countDown()
            withTimeout(20000){manager.pluginInstall.first{!it.busy}}
            fixture.close()
            field.set(manager.transport,prior)
            client.dispatcher.executorService.shutdown()
            manager.store.commit{state->state.keys().asSequence().toList().forEach(state::remove);original.keys().forEach{state.put(it,original.get(it))}}
            hashes.forEach{File(manager.store.directory,"objects/$it.jar").delete()}
        }
    }
}
