package app.filmmerkezi.tv

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL

@RunWith(AndroidJUnit4::class)
class NativeE2ETest {
    @Test fun capturedSearchDetailResolveAndDeclaredLocalMedia()=runBlocking {
        val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext
        val replay=JSONObject(ins.context.assets.open("native-e2e.json").bufferedReader().use{it.readText()})
        val calls=mutableListOf<String>()
        val network=object:WebTransport {
            override suspend fun request(request:WebRequest):WebResponse {
                calls.add(request.url)
                val body=when{
                    request.url.startsWith("https://filmizle.ink/?s=")->replay.getString("searchBody")
                    request.url==replay.getJSONObject("card").getString("pageUrl")->replay.getString("detailBody")
                    request.url.startsWith("https://hotstream.club/embed/")->replay.getString("embedBody")
                    else->error("No fixture for "+request.url)
                }
                return WebResponse(request.url,body)
            }
        }
        val started=SystemClock.elapsedRealtime()
        val provider=NativeSiteProvider(Sites.get("filmcibaba"),network,Dispatchers.Default)
        val cards=provider.search("Sultana").cards
        assertEquals(1,cards.size)
        val detail=provider.detail(cards.single())
        assertTrue(detail.card.title.contains("Sultana"))
        assertTrue(detail.options.isNotEmpty())
        assertEquals(2,calls.size) // Listing did not pre-resolve the player.
        val source=SourceResolver(network).resolve(detail.options.first())
        assertTrue(source.url.startsWith("https://hotstream.club/list/"))
        val metadataMs=SystemClock.elapsedRealtime()-started
        val playerSource=Contracts.source(source).put("url","http://10.0.2.2:8766/case/native-e2e/master.m3u8")
        val activity=ins.startActivitySync(Intent(context,NativeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as NativeMainActivity
        var state=JSONObject()
        try{
            ins.runOnMainSync{activity.startSourcesForTest(listOf(playerSource))}
            val deadline=SystemClock.elapsedRealtime()+40000
            do{delay(250);ins.runOnMainSync{state=activity.playbackSnapshotForTest()}
                if(state.optLong("positionMs")>=20000||!state.isNull("error"))break
            }while(SystemClock.elapsedRealtime()<deadline)
            assertTrue(state.toString(),state.optBoolean("firstFrame")&&state.optLong("positionMs")>=20000&&state.isNull("error"))
            val log=JSONArray(URL("http://10.0.2.2:8766/log").readText()).values().filterIsInstance<JSONObject>().filter{it.string("path").startsWith("/case/native-e2e/")}
            assertTrue(log.isNotEmpty());assertTrue(log.all{it.string("referer")==source.referer&&it.string("agent")==source.userAgent})
            assertTrue(log.all{it.string("accept")=="*/*"})
        }finally{
            File(context.getExternalFilesDir(null),"native-e2e.json").writeText(JSONObject()
                .put("fixture",true).put("mediaReplacement","Declared local 720p reference; NOT live Sultana playback")
                .put("metadataMs",metadataMs).put("card",Contracts.card(detail.card)).put("originalResolvedSource",Contracts.source(source))
                .put("player",state).put("requests",JSONArray(calls)).toString(2))
            ins.runOnMainSync{activity.finish()}
        }
        Unit
    }
}
