package app.filmmerkezi.tv.tv

import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import java.io.File

/** Deterministic provider failures and real Media3 playback, without touching user history. */
class EpisodePlayback102UiTest {
    companion object { private val results=JSONArray() }
    @get:Rule val name=TestName()
    private val ins=InstrumentationRegistry.getInstrumentation();private val context=ins.targetContext
    private lateinit var activity:TvActivity;private lateinit var fake:Data
    private var saved=emptyMap<String,Map<String,Any?>>()
    private var passed=false;private val evidence=JSONObject()
    private fun main(block:()->Unit)=ins.runOnMainSync(block)
    private fun snap():JSONObject{var r=JSONObject();main{r=activity.snapshotForUiTest()};return r}
    private fun waitFor(ms:Long=12000,test:()->Boolean){val end=SystemClock.elapsedRealtime()+ms
        while(SystemClock.elapsedRealtime()<end){var ok=false;main{ok=test()};if(ok)return;SystemClock.sleep(70)};error("Episode UI timeout: "+snap())}
    private fun click(id:Int){waitFor{activity.findViewById<View>(id)!=null};main{activity.findViewById<View>(id).performClick()}}
    private fun row(number:Int=1){val key=fake.episode(number).card.ref.id;waitFor{activity.window.decorView.findViewWithTag<View>(key)!=null}
        main{activity.window.decorView.findViewWithTag<View>(key).performClick()}}
    private fun openSeries(){
        TvActivity.dataFactoryForTests={fake}
        activity=ins.startActivitySync(Intent(context,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
        click(R.id.tv_nav_series);waitFor{activity.window.decorView.findViewWithTag<View>(fake.seriesItem.identity)!=null}
        main{activity.window.decorView.findViewWithTag<View>(fake.seriesItem.identity).performClick()}
        waitFor{activity.window.decorView.findViewWithTag<View>(fake.episode(1).card.ref.id)!=null}
    }
    @Before fun prepare(){
        saved=listOf("interface-preferences","player-preferences","catalog-preferences").associateWith{context.getSharedPreferences(it,0).all.toMap()}
        context.getSharedPreferences("interface-preferences",0).edit().putBoolean("configured",true).putString("style","classic").commit()
        context.getSharedPreferences("catalog-preferences",0).edit().putString("siteId","").commit()
        context.getSharedPreferences("player-preferences",0).edit().putBoolean("automaticSources",true).putBoolean("crossSiteSources",true).putInt("height",0).commit()
        ins.setInTouchMode(false);fake=Data()
    }
    @After fun finish(){
        evidence.put("test",name.methodName).put("passed",passed)
        if(::activity.isInitialized){evidence.put("state",snap());main{activity.finish()};ins.waitForIdleSync()}
        evidence.put("metadataSites",JSONArray(fake.loaded.toList())).put("resolveCount",fake.resolved)
        results.put(evidence);File(context.getExternalFilesDir(null),"episodes0102-ui.json").writeText(JSONObject().put("fixture",true).put("cases",results).toString(2))
        TvActivity.dataFactoryForTests=null
        saved.forEach{(name,values)->val e=context.getSharedPreferences(name,0).edit().clear();values.forEach{(k,v)->when(v){
            is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v);is Float->e.putFloat(k,v)
            is Set<*>->e.putStringSet(k,v.filterIsInstance<String>().toSet())
        }};e.commit()}
    }
    @Test fun automaticEpisodeSkipsDetailAndPlaysOtherSiteFor20Seconds(){
        fake.late=true;openSeries();row()
        waitFor{activity.snapshotForUiTest().optBoolean("firstFrame")}
        val first=snap();assertEquals("SERIES",first.getString("detailKind"));assertFalse(first.getBoolean("classicWindow"))
        assertTrue(first.getString("sourceId").startsWith("dizibox|"))
        val at=SystemClock.elapsedRealtime();val position=first.getLong("positionMs")
        waitFor(30000){val s=activity.snapshotForUiTest();SystemClock.elapsedRealtime()-at>=20000&&s.optLong("positionMs")-position>=19000&&s.optBoolean("isPlaying")}
        val state=snap();assertEquals(first.getInt("engineIdentity"),state.getInt("engineIdentity"));assertEquals(first.getString("sourceId"),state.getString("sourceId"))
        assertTrue(state.getInt("sourceCount")>=2);assertTrue(state.getInt("audioRenderedBuffers")>0);assertTrue(state.getInt("poolErrorCount")>=1)
        evidence.put("wallMs",SystemClock.elapsedRealtime()-at).put("advanceMs",state.getLong("positionMs")-position)
        ins.uiAutomation.takeScreenshot()?.let{bmp->File(context.getExternalFilesDir(null),"episodes0102-player.png").outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()}
        passed=true
    }
    @Test fun resumePromptPrecedesAnyMetadataAndContinuesFiveSecondsEarlier(){
        fake.resume=25000;openSeries();row()
        waitFor{activity.classicOverlayForTest()?.showing==true}
        assertTrue(fake.loaded.isEmpty());assertEquals(0,fake.resolved)
        main{activity.classicOverlayForTest()!!.select("continue")}
        waitFor{activity.snapshotForUiTest().optBoolean("firstFrame")}
        assertTrue(snap().getLong("positionMs")>=20000);assertEquals("SERIES",snap().getString("detailKind"));passed=true
    }
    @Test fun manualModeListsSourcesBeforeStartingPlayback(){
        context.getSharedPreferences("player-preferences",0).edit().putBoolean("automaticSources",false).commit()
        openSeries();row()
        waitFor{activity.classicOverlayForTest()?.rowKeys()?.any{it.startsWith("dizibox|")}==true}
        assertEquals(0,fake.resolved);assertFalse(snap().optBoolean("firstFrame"))
        main{val window=activity.classicOverlayForTest()!!;window.select(window.rowKeys().first{it.startsWith("dizibox|")})}
        waitFor{activity.snapshotForUiTest().optBoolean("firstFrame")};assertEquals(1,fake.resolved);passed=true
    }
    @Test fun disabledOtherSitesShowsFailureInSourcePanelAndBackReturnsToSeries(){
        context.getSharedPreferences("player-preferences",0).edit().putBoolean("crossSiteSources",false).commit()
        openSeries();row()
        waitFor{activity.classicOverlayForTest()?.rowKeys()?.any{it.startsWith("error:")}==true&&!activity.snapshotForUiTest().optBoolean("poolPending")}
        assertEquals(listOf("dizipal:1"),fake.loaded.toList());assertEquals(0,fake.resolved)
        main{activity.onBackPressed()}
        waitFor{!activity.snapshotForUiTest().optBoolean("entry")&&activity.findViewById<View>(R.id.tv_season)!=null}
        assertEquals("SERIES",snap().getString("detailKind"));passed=true
    }
    @Test fun cancelledCheckpointCannotOpenLatePlayback(){
        fake.beginDelay=300;openSeries();row()
        waitFor{activity.snapshotForUiTest().optBoolean("preparing")}
        main{activity.onBackPressed()}
        waitFor{fake.discarded>0};SystemClock.sleep(200)
        assertEquals(0,fake.resolved);assertTrue(fake.loaded.isEmpty());assertFalse(snap().optBoolean("entry"));assertFalse(snap().optBoolean("firstFrame"));passed=true
    }
    @Test fun nextEpisodeUsesPoolAndFailedNextKeepsCurrentEpisode(){
        openSeries();row();waitFor{activity.snapshotForUiTest().optBoolean("firstFrame")}
        click(R.id.fm_player_next)
        waitFor{val s=activity.snapshotForUiTest();s.optBoolean("firstFrame")&&s.optString("playingTitle")==fake.episode(2).card.title}
        assertEquals(1,fake.saved);val position=snap().getLong("positionMs")
        fake.failedEpisode=3;click(R.id.fm_player_next)
        waitFor{fake.loaded.contains("dizibox:3")&&activity.snapshotForUiTest().optBoolean("isPlaying")}
        assertEquals(fake.episode(2).card.title,snap().getString("playingTitle"));assertTrue(snap().getLong("positionMs")>=position);passed=true
    }
    @Test fun moviesKeepTheirDetailAndStartChoices(){
        TvActivity.dataFactoryForTests={fake}
        activity=ins.startActivitySync(Intent(context,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
        click(R.id.tv_nav_movies);waitFor{activity.window.decorView.findViewWithTag<View>(fake.movieItem.identity)!=null}
        main{activity.window.decorView.findViewWithTag<View>(fake.movieItem.identity).performClick()}
        click(R.id.tv_play_action);waitFor{activity.classicOverlayForTest()?.showing==true}
        assertEquals("MOVIE",snap().getString("detailKind"));assertEquals(0,fake.resolved)
        main{activity.classicOverlayForTest()!!.select("exit")};waitFor{!activity.snapshotForUiTest().optBoolean("entry")};passed=true
    }
    private inner class Data:TvData {
        val series=Card(ContentRef("dizipal","https://dizipalorjinal19.com/diziler/dragon",ContentKind.SERIES),"Dragon")
        val seriesItem=UnifiedItem(ContentIdentity.key(series),series,listOf(series))
        val movie=Card(ContentRef("filmcibaba","https://filmizle.ink/test-movie/"),"Test Movie")
        val movieItem=UnifiedItem(ContentIdentity.key(movie),movie,listOf(movie))
        val loaded=java.util.Collections.synchronizedList(mutableListOf<String>())
        var resolved=0;var late=false;var resume=0L;var beginDelay=0L;var discarded=0;var saved=0;var token=0;var failedEpisode=0
        private val base="http://10.0.2.2:8766/case/episodes102-${SystemClock.elapsedRealtime()}"
        fun episode(n:Int):Episode {
            val c=Card(ContentRef("dizipal","https://dizipalorjinal19.com/bolumler/dragon-s1b$n",ContentKind.EPISODE),"Dragon · 1x$n",seriesIdentity=seriesItem.identity,episodeNumber=EpisodeNumber(1,n),seriesRef=series.ref)
            val other=c.copy(ref=ContentRef("dizibox","https://www.dizibox.live/dragon-1-sezon-$n-bolum-izle/",ContentKind.EPISODE),seriesRef=ContentRef("dizibox","https://www.dizibox.live/diziler/dragon/",ContentKind.SERIES))
            return Episode(c,series.ref,EpisodeNumber(1,n),alternatives=listOf(c,other))
        }
        private fun option(c:Card)=SourceOption("source-${c.episodeNumber?.number?:0}",c.ref,"Çalışan kaynak","$base/master.m3u8")
        override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(listOf(if(kind==ContentKind.SERIES)seriesItem else movieItem),emptyMap(),emptyList()))
        override fun search(query:String,cursors:Map<String,PageCursor>)=browse(ContentKind.SERIES,cursors)
        override suspend fun detail(card:Card):Detail {
            if(card.ref.kind==ContentKind.SERIES)return Detail(series,episodes=(1..3).map(::episode))
            if(card.ref.kind==ContentKind.EPISODE){val n=card.episodeNumber!!.number;loaded+="${card.ref.siteId}:$n"
                if(card.ref.siteId=="dizipal"||n==failedEpisode)throw DataFailure(FailureKind.HTTP,"HTTP 404",httpCode=404)}
            return Detail(card,options=listOf(option(card)))
        }
        override suspend fun episodes(card:Card)=(1..3).map(::episode)
        override suspend fun work(card:Card)=if(card.ref.kind==ContentKind.SERIES)seriesItem else movieItem
        override fun sourcePool(card:Card,initial:List<SourceOption>,crossSite:Boolean,gate:suspend()->Unit)=flow {
            if(late){emit(SourcePool(emptyList(),true));delay(800)
                val other=card.copy(ref=ContentRef("ddizi","https://www.ddizi.tel/izle/1/dragon-1-bolum.htm",ContentKind.EPISODE))
                emit(SourcePool(listOf(option(other).scoped()),false))
            }else emit(SourcePool(initial.map{it.scoped()},false))
        }
        override suspend fun resolve(option:SourceOption):ResolvedSource {resolved++;return ResolvedSource(option.id,option.label,option.url,StreamType.HLS)}
        override suspend fun beginPlayback(card:Card):PlaybackCheckpoint {val id=++token;val delayMs=beginDelay;if(delayMs>0)delay(delayMs);return PlaybackCheckpoint("fixture-$id",card,resume,180000)}
        override suspend fun abandonPlayback(cp:PlaybackCheckpoint){discarded++}
        override suspend fun finishPlayback(cp:PlaybackCheckpoint,mode:PlaybackFinish,position:Long,duration:Long){if(mode==PlaybackFinish.DISCARD)discarded++ else saved++}
        override suspend fun records()=emptyList<LibraryRecord>()
        override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){}
        override suspend fun remember(query:String){}
        override suspend fun searches()=emptyList<String>()
    }
}
