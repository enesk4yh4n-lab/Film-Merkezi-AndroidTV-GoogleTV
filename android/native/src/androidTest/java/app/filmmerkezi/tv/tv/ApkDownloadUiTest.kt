package app.filmmerkezi.tv.tv

import android.content.pm.PackageManager
import android.widget.ProgressBar
import android.widget.TextView
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.updates.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import okio.source
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow

class ApkDownloadUiTest {
    @Suppress("DEPRECATION","UNCHECKED_CAST")
    @Test fun delayedTransferSurvivesNavigationAndOnlyVerifiedApkOffersInstall()=runBlocking {
        val fixture=Settings110Fixture()
        val context=fixture.context
        val binary=File(context.applicationInfo.sourceDir)
        val size=binary.length()
        val digest=MessageDigest.getInstance("SHA-256")
        binary.inputStream().use{input->
            val buffer=ByteArray(32768)
            while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}
        }
        val sha=digest.digest().joinToString(""){"%02x".format(it.toInt()and 255)}
        val certificate=UpdateProtocol.sha(context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNATURES).signatures!!.first().toByteArray())
        var firstByte=CountDownLatch(1)
        var finish=CountDownLatch(1)
        var mode="normal"
        val client=OkHttpClient.Builder().addInterceptor{chain->
            val opener=firstByte
            val closer=finish
            val attempt=mode
            if(attempt=="break")throw IOException("fixture network cut")
            check(opener.await(15,TimeUnit.SECONDS))
            val body=object:ResponseBody(){
                override fun contentType()=null
                override fun contentLength()=size
                private val stream=object:ForwardingSource(binary.source()){
                    private var readOnce=false
                    override fun read(sink:Buffer,byteCount:Long):Long{
                        if(readOnce){
                            while(!closer.await(50,TimeUnit.MILLISECONDS)){if(chain.call().isCanceled())throw IOException("fixture cancelled")}
                        }
                        readOnce=true
                        return super.read(sink,byteCount)
                    }
                }.buffer()
                override fun source():BufferedSource=stream
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        }.build()
        val manager=DistributionManager(context,UpdateTransport(context,client))
        val release=UpdateProtocol.Release(manager.installedCode,"Deneme",23,"https://fixture.example.test/update.apk",size.toInt(),sha,certificate,"")
        val state=DistributionManager::class.java.getDeclaredField("state").apply{isAccessible=true}.get(manager) as MutableStateFlow<DistributionStatus>
        val singleton=DistributionManager::class.java.getDeclaredField("singleton").apply{isAccessible=true}
        val original=singleton.get(null)
        singleton.set(null,manager)
        fun offered(value:UpdateProtocol.Release)=DistributionStatus(release=value,appCheck=CheckState.AVAILABLE)
        fun center()=checkNotNull(fixture.activity.updateCenterForTest())
        fun apkPage(){fixture.main{center().select("application")}}
        fun has(id:Int)= (fixture.tag("updates:status") as? TextView)?.text?.contains(context.getString(id))==true
        try{
            state.value=offered(release)
            fixture.start(customData=UpdateTestData.empty())
            fixture.main{fixture.activity.settingsForTest();fixture.tag("settings-category:updates").requestFocus()}
            fixture.waitFor{center().keys().contains("updates")}
            fixture.main{center().select("updates")}
            fixture.waitFor{center().keys().contains("application")}
            apkPage()
            fixture.waitFor{fixture.tag("updates:update-application")?.isEnabled==true}
            fixture.main{center().select("update-application")}
            val job=manager.startApkDownload()
            assertSame(job,manager.startApkDownload())
            fixture.waitFor{has(R.string.apk_download_starting)}
            firstByte.countDown()
            fixture.waitFor{(fixture.tag("updates:loading") as? ProgressBar)?.let{it.isShown&&!it.isIndeterminate}==true}
            fixture.main{assertFalse(fixture.tag("updates:install-apk")?.isEnabled==true)}
            fixture.screenshot("apk-download-progress.png")
            fixture.ins.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            fixture.waitFor{center().keys().contains("application")}
            apkPage()
            fixture.waitFor{(fixture.tag("updates:loading") as? ProgressBar)?.let{it.isShown&&!it.isIndeterminate}==true}
            fixture.main{center().select("cancel-apk")}
            finish.countDown()
            fixture.waitFor{has(R.string.apk_download_cancelled)}
            assertNull(manager.readyApk())
            assertFalse(manager.store.directory.listFiles().orEmpty().any{it.name.endsWith(".partial.apk")})

            mode="break"
            fixture.main{center().select("update-application")}
            fixture.waitFor{manager.apkDownload.value.phase==ApkDownloadPhase.FAILED}
            assertEquals(UpdateProblem.NETWORK,manager.apkDownload.value.failure)
            mode="normal"
            state.value=offered(release.copy(hash="0".repeat(64)))
            fixture.main{center().select("update-application")}
            fixture.waitFor{manager.apkDownload.value.phase==ApkDownloadPhase.FAILED&&manager.apkDownload.value.failure==UpdateProblem.INTEGRITY}
            assertNull(manager.readyApk())

            state.value=offered(release)
            fixture.main{center().select("update-application")}
            fixture.waitFor{fixture.tag("updates:install-apk")?.isEnabled==true}
            assertEquals(size,manager.readyApk()?.length())
            fixture.main{assertTrue(fixture.activity.hasWindowFocus());assertTrue(center().keys().contains("install-apk"))}
            fixture.screenshot("apk-download-ready.png")
        }finally{
            firstByte.countDown();finish.countDown();manager.cancelApkDownload()
            manager.readyApk()?.delete();fixture.close();singleton.set(null,original)
        }
    }
}
