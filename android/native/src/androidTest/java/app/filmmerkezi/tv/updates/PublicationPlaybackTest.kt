package app.filmmerkezi.tv.updates

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.NativeMainActivity
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.*
import okhttp3.OkHttpClient

/** 20-second local HTTPS Media3 check including worker overhead and domain-scoped cookies. */
class PublicationPlaybackTest {
    @Test fun signedModulesAndMedia3PreserveCookieHeadersAndProgress()=runBlocking{PluginWorkerTest().withSet{client->
        val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext
        val cert=ins.context.assets.open("player-tls-ca.der").use{CertificateFactory.getInstance("X.509").generateCertificate(it)}
        val keys=KeyStore.getInstance(KeyStore.getDefaultType()).apply{load(null);setCertificateEntry("fixture",cert)}
        val tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply{init(keys)}.trustManagers.filterIsInstance<X509TrustManager>().single()
        val ssl=SSLContext.getInstance("TLS").apply{init(null,arrayOf(tm),null)}
        val requests=java.util.concurrent.CopyOnWriteArrayList<JSONObject>()
        val http=OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory,tm).hostnameVerifier{host,_->host=="10.0.2.2"}.addNetworkInterceptor{chain->val r=chain.request();requests.add(JSONObject().put("url",r.url.toString()).put("cookie",r.header("Cookie")).put("referer",r.header("Referer")).put("ua",r.header("User-Agent")));chain.proceed(r)}.build()
        val activity=ins.startActivitySync(Intent(context,NativeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as NativeMainActivity
        val source=Contracts.source(ResolvedSource("publication-local","Local HTTPS fixture","https://10.0.2.2:8767/master.m3u8",StreamType.HLS,referer="https://fixture.example.test/",userAgent="FM-Publication",sessionCookies=listOf(SessionCookie("fm","fixture","10.0.2.2","/",System.currentTimeMillis()+600000,true,true,true))))
        fun snapshot():JSONObject{var result=JSONObject();ins.runOnMainSync{result=activity.playbackSnapshotForTest()};return result}
        val begin=SystemClock.elapsedRealtime();var peak=0;val samples=JSONArray();val am=context.getSystemService(android.content.Context.ACTIVITY_SERVICE)as android.app.ActivityManager
        try{
            ins.runOnMainSync{val method=activity.javaClass.declaredMethods.single{it.name.startsWith("startSourcesWithClientForTest")};method.isAccessible=true;method.invoke(activity,listOf(source),http)}
            val site=Sites.get("filmcibaba");val all=DistributionManager.get(context).store.snapshot().getJSONObject("packages")
            val key=all.keys().asSequence().first{it.endsWith("/site-filmcibaba")}
            val background=launch(Dispatchers.IO){repeat(12){client.probe(key,JSONObject().put("site",PluginWire.site(site)));delay(500)}}
            while(SystemClock.elapsedRealtime()-begin<60000){
                delay(500);val state=snapshot()
                val pids=am.runningAppProcesses.filter{it.processName==context.packageName||it.processName==context.packageName+":plugins"}.map{it.pid}.toIntArray()
                val pss=am.getProcessMemoryInfo(pids).sumOf{it.totalPss};peak=maxOf(peak,pss);samples.put(JSONObject().put("atMs",SystemClock.elapsedRealtime()-begin).put("pssKiB",pss))
                if(state.optBoolean("firstFrame")&&state.optBoolean("isPlaying")&&state.optLong("positionMs")>=20000)break
                assertTrue(state.toString(),state.isNull("error"))
            }
            background.join();val result=snapshot().put("fixture",true).put("combinedPeakPssKiB",peak).put("samples",samples)
            File(context.getExternalFilesDir(null),"modular-playback.json").writeText(result.toString(2))
            assertTrue(result.toString(),result.optBoolean("firstFrame")&&result.optLong("positionMs")>=20000)
            assertEquals(1,result.getInt("engineInstancesUsed"));assertEquals(0,result.getInt("sourceSwitches"))
            assertTrue(requests.isNotEmpty());for(r in requests){assertEquals("fm=fixture",r.getString("cookie"));assertEquals("https://fixture.example.test/",r.getString("referer"));assertEquals("FM-Publication",r.getString("ua"))}
            assertTrue("Combined host + worker PSS exceeded 220 MiB: $peak KiB",peak<220*1024)
        }finally{ins.runOnMainSync{activity.finish()}}
    }}
}
