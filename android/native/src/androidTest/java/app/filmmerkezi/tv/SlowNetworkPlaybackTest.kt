package app.filmmerkezi.tv

import android.content.Intent
import android.os.Debug
import android.os.SystemClock
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.settings.AppSettings
import app.filmmerkezi.tv.tv.RemoteAuditData
import app.filmmerkezi.tv.tv.Settings110Fixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class SlowNetworkPlaybackTest {
    @Test fun firstCatalogCardAndWarmTabReturn()=runBlocking{
        val fixture=Settings110Fixture()
        val args=InstrumentationRegistry.getArguments()
        assumeNotNull(args.getString("fixturePort"))
        val profile=args.getString("profile")?:"normal"
        val port=args.getString("fixturePort")?.toInt()?:8784
        val base=RemoteAuditData().apply{includeRecords=false}
        val scope=Sites.all
        Sites.installed(Sites.defaults.filter{it.id=="filmcibaba"})
        val calls=AtomicInteger()
        var requestedAt=0L
        var receivedBytes=0
        var coldMs=0L
        var warmMs=0L
        val data=object:app.filmmerkezi.tv.tv.TvData by base {
            override fun browseSite(siteId:String,kind:ContentKind,cursors:Map<String,PageCursor>)=flow{
                val items=if(kind==ContentKind.MOVIE){
                    calls.incrementAndGet()
                    requestedAt=SystemClock.elapsedRealtime()
                    withContext(Dispatchers.IO){
                        val raw=URL("http://10.0.2.2:$port/$profile/catalog.json").readBytes()
                        receivedBytes=raw.size
                        val cards=JSONObject(raw.toString(Charsets.UTF_8)).getJSONArray("cards")
                        (0 until cards.length()).map{i->
                            val id=cards.getJSONObject(i).getInt("id")
                            base.item(Card(ContentRef("filmcibaba","https://filmizle.ink/network-$id/",ContentKind.MOVIE),
                                cards.getJSONObject(i).getString("title"),workId="network:$id"))
                        }
                    }
                }else emptyList()
                emit(UnifiedPage(items,emptyMap(),emptyList(),completedSites=1,requestedSites=1))
            }
        }
        try{
            fixture.start(AppSettings(showPosters=false),data)
            fixture.waitFor{requestedAt>0&&fixture.tag("network:1")!=null}
            coldMs=SystemClock.elapsedRealtime()-requestedAt
            val switchedAt=SystemClock.elapsedRealtime()
            fixture.main{fixture.activity.findViewById<View>(R.id.tv_nav_movies).performClick()}
            fixture.waitFor{fixture.tag("network:1")!=null}
            warmMs=SystemClock.elapsedRealtime()-switchedAt
            assertEquals(1,calls.get())
        }finally{
            File(fixture.context.getExternalFilesDir(null),"catalog-network-$profile.json").writeText(JSONObject()
                .put("profile",profile).put("firstCardMs",coldMs).put("warmTabMs",warmMs)
                .put("bytes",receivedBytes).put("metadataRequests",calls.get()).put("pssKiB",Debug.getPss()).toString(2))
            fixture.close()
            Sites.installed(scope)
        }
    }

    @Test fun adaptiveVideoStartsAndAdvancesOnShapedConnection()=runBlocking{
        val ins=InstrumentationRegistry.getInstrumentation()
        val args=InstrumentationRegistry.getArguments()
        assumeNotNull(args.getString("fixturePort"))
        val profile=args.getString("profile")?:"normal"
        val rate=args.getString("profileRate")?:"unlimited"
        val activity=ins.startActivitySync(Intent(ins.targetContext,NativeMainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as NativeMainActivity
        val port=args.getString("fixturePort")?.toInt()?:8784
        val base="http://10.0.2.2:$port/$profile"
        val run="shaped-$profile-${SystemClock.elapsedRealtime()}"
        val path="$base/case/$run"
        val source=JSONObject().put("id",run).put("label","Local adaptive fixture")
            .put("type","hls").put("url","$path/master.m3u8?mode=selections")
        val log=JSONObject().put("profile",profile).put("configuredRate",rate)
        val heights=linkedSetOf<Int>()
        val start=SystemClock.elapsedRealtime()
        var pss=0L
        var triggered=false
        var failedRequests=0
        var frameTime=0L
        var last=JSONObject()
        try{
            ins.runOnMainSync{activity.startSourcesForTest(listOf(source))}
            while(SystemClock.elapsedRealtime()-start<62000){
                ins.runOnMainSync{last=activity.playbackSnapshotForTest()}
                pss=maxOf(pss,Debug.getPss())
                val position=last.optLong("positionMs")
                if(last.optBoolean("firstFrame")&&frameTime==0L)frameTime=SystemClock.elapsedRealtime()-start
                last.optInt("height").takeIf{it>0}?.let(heights::add)
                if(profile=="outage"&&!triggered&&position>=6500){
                    triggered=true
                    withContext(Dispatchers.IO){URL("$path/control?outageMs=6000").readText()}
                }
                if(frameTime>0&&position>=20000&&last.optBoolean("isPlaying")){
                    if(profile!="outage")break
                    failedRequests=withContext(Dispatchers.IO){URL("$path/fault-count").readText().toIntOrNull()?:0}
                    if(failedRequests>0)break
                }
                delay(250)
            }
            assertTrue("No first frame for $profile",frameTime>0)
            assertTrue("Playback did not advance for $profile",last.optLong("positionMs")>=20000)
            assertEquals(1,last.getInt("engineInstancesUsed"))
            assertTrue(profile!="outage"||triggered&&failedRequests>0)
        }finally{
            log.put("firstFrameMs",frameTime).put("sampledPssKiB",pss).put("heights",JSONArray(heights.toList()))
                .put("outageTriggered",triggered).put("failedRequests",failedRequests).put("playback",last)
            File(ins.targetContext.getExternalFilesDir(null),"network-$profile.json").writeText(log.toString(2))
            ins.runOnMainSync{activity.stopSourcesForTest();activity.finish()}
        }
    }
}
