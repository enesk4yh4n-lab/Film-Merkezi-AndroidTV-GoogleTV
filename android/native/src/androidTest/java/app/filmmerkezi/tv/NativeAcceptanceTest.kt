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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class NativeAcceptanceTest {
    @Test fun lazyFallbackCancelsAndPlaysOnOneEngine()=runBlocking {
        val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext
        val activity=ins.startActivitySync(Intent(context,NativeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as NativeMainActivity
        val report=JSONObject().put("api",android.os.Build.VERSION.SDK_INT).put("fixture",true).put("checks",JSONArray())
        var peakPssKiB=0;var lastSample=0L
        fun snap():JSONObject {var s=JSONObject();ins.runOnMainSync{s=activity.playbackSnapshotForTest()};return s}
        suspend fun await(predicate:(JSONObject)->Boolean):JSONObject {
            val deadline=SystemClock.elapsedRealtime()+45000
            do{
                delay(200)
                if(SystemClock.elapsedRealtime()-lastSample>=2000){
                    lastSample=SystemClock.elapsedRealtime()
                    val memory=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(memory)
                    peakPssKiB=maxOf(peakPssKiB,memory.totalPss)
                }
                val s=snap();if(predicate(s))return s
            }while(SystemClock.elapsedRealtime()<deadline)
            error("Player timeout: "+snap())
        }
        val ready=JSONObject().put("id","native-good").put("label","Native fixture").put("type","hls")
            .put("url","http://10.0.2.2:8766/case/native-stage1/master.m3u8")
            .put("referer","https://fixture.example.test/").put("userAgent","FM-Native-Core")
        val requested=mutableListOf<String>();val jobs=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val lookup=PlaybackSession.SourceLookup{candidate,done->
            val id=candidate.getString("lazyOptionId");synchronized(requested){requested.add(id)}
            val job=jobs.launch{delay(250);if(id=="bad")done(null,DataFailure(FailureKind.HTTP,"fixture resolver 404"))else done(ready,null)}
            AutoCloseable{job.cancel()}
        }
        try{
            ins.runOnMainSync{activity.startSourcesForTest(listOf(JSONObject().put("id","bad").put("label","bad").put("lazyOptionId","bad"),
                JSONObject().put("id","good").put("label","good").put("lazyOptionId","good")),lookup)}
            val state=await{it.optBoolean("firstFrame")&&it.optLong("positionMs")>=20000&&it.optBoolean("isPlaying")}
            assertEquals(listOf("bad","good"),requested)
            assertEquals(1,state.getInt("sourceSwitches"));assertEquals(1,state.getInt("engineInstancesUsed"))
            report.getJSONArray("checks").put(JSONObject().put("name","lazy resolve failure -> next -> 20s HLS; one engine").put("passed",true).put("state",state))
            val cancelled=AtomicBoolean();val late=AtomicBoolean()
            val pending=PlaybackSession.SourceLookup{_,done->
                val job=jobs.launch{try{delay(30000);late.set(true);done(ready,null)}finally{cancelled.set(true)}}
                AutoCloseable{job.cancel()}
            }
            ins.runOnMainSync{activity.startSourcesForTest(listOf(JSONObject().put("id","pending").put("label","pending").put("lazyOptionId","pending")),pending)}
            await{it.optBoolean("resolving")}
            ins.runOnMainSync{activity.stopSourcesForTest()}
            delay(500)
            assertTrue(cancelled.get());assertFalse(late.get());assertFalse(snap().optBoolean("isPlaying"))
            report.getJSONArray("checks").put(JSONObject().put("name","stop cancels metadata; no late playback").put("passed",true))
        }finally{
            report.put("sampledPeakPssKiB",peakPssKiB).put("sampleIntervalMs",2000).put("debugApk",true)
            File(context.getExternalFilesDir(null),"native-acceptance.json").writeText(report.toString(2))
            jobs.cancel();ins.runOnMainSync{activity.finish()}
        }
        Unit
    }
    @Test fun libraryReopensAndKeepsCorruptInput()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="native-test-library.db";context.deleteDatabase(name)
        val card=Card(ContentRef("filmcibaba","https://filmizle.ink/test-record/"),"Kayıt",year=2024)
        try{
            withContext(Dispatchers.IO){
                LibraryStore(context,name).use{s->s.update(card,listed=true,positionMs=23456,durationMs=120000);s.rememberSearch("Işık")}
                LibraryStore(context,name).use{s->
                    s.update(card.copy(imdbId="tt1234567"))
                    val r=s.records().single();assertEquals(23456,r.positionMs);assertTrue(r.listed);assertEquals(listOf("Işık"),s.searches())
                    assertEquals("tt1234567",r.card.imdbId)
                }
                LibraryStore(context,name).use{s->
                    repeat(72){i->s.update(card.copy(ref=card.ref.copy(pageUrl="https://filmizle.ink/paged-$i/"),title="Paged $i",imdbId="tt"+(9000000+i)),listed=true,positionMs=if(i%2==0)20000 else 0)}
                    val first=s.records(true,60,0);val rest=s.records(true,60,60)
                    assertEquals(60,first.size);assertEquals(13,rest.size)
                    assertTrue(first.map{it.identity}.intersect(rest.map{it.identity}.toSet()).isEmpty())
                    assertEquals(37,s.records(historyOnly=true).size)
                }
                context.deleteDatabase(name)
                val file=context.getDatabasePath(name);file.parentFile?.mkdirs()
                val bytes="explicit-corrupt-native-test-data".toByteArray();file.writeBytes(bytes)
                try{LibraryStore(context,name).use{it.records()};fail("Corrupt data must be surfaced")}catch(e:DataFailure){assertEquals(FailureKind.STORAGE,e.kind)}
                assertArrayEquals(bytes,file.readBytes())
            }
        }finally{context.deleteDatabase(name)}
        Unit
    }
}
