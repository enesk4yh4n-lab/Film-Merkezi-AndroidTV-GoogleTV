package app.filmmerkezi.tv.tv

import android.view.*
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.PlaybackSettings
import app.filmmerkezi.tv.settings.AppSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class RemotePlaybackSelectionTest {
    @Test fun automaticSelectionPlaysAlternativeAfterTwoResolutionTimeouts()=runBlocking {
        val fixture=Settings110Fixture()
        val base=RemoteAuditData()
        val movie=base.movies.first()
        val candidates=listOf("timeout-a","timeout-b","late").map{key->
            SourceOption(key,movie.ref,key,movie.ref.pageUrl).scoped()
        }
        val attempts=CopyOnWriteArrayList<String>()
        val finishedTimeouts=CopyOnWriteArrayList<String>()
        val bothTimedOut=CompletableDeferred<Unit>()
        val data=object:TvData by base {
            override fun sourcePool(card:Card,initial:List<SourceOption>,crossSite:Boolean,gate:suspend()->Unit)=flow {
                emit(SourcePool(candidates.take(2),true))
                bothTimedOut.await()
                emit(SourcePool(candidates,false))
            }
            override suspend fun resolve(option:SourceOption):ResolvedSource {
                attempts.add(option.id)
                if(option!=candidates.last())try {
                    withTimeout(100){awaitCancellation()}
                }finally {
                    finishedTimeouts.add(option.id)
                    if(finishedTimeouts.size==2)bothTimedOut.complete(Unit)
                }
                return ResolvedSource(option.id,option.label,
                    "http://10.0.2.2:8770/case/source-slots-ui/master.m3u8",StreamType.HLS)
            }
        }
        try {
            fixture.start(AppSettings(entry="RESTART",automaticSources=true),data)
            fixture.main{fixture.activity.findViewById<View>(R.id.tv_nav_movies).performClick()}
            fixture.waitFor{fixture.tag(movie.workId)!=null}
            fixture.main{fixture.tag(movie.workId).performClick()}
            fixture.waitFor{fixture.activity.findViewById<View>(R.id.tv_play_action)!=null}
            fixture.main{fixture.activity.findViewById<View>(R.id.tv_play_action).performClick()}
            fixture.waitFor(30000){
                val state=fixture.activity.snapshotForUiTest()
                state.optBoolean("firstFrame")&&state.optLong("positionMs")>=15000&&state.isNull("error")
            }
            val state=fixture.snap()
            assertEquals(candidates.map{it.id}.toSet(),attempts.toSet())
            assertEquals(3,attempts.size)
            assertEquals(candidates.last().id,state.getString("sourceId"))
            assertEquals(1,state.getInt("engineInstancesUsed"))
            File(fixture.context.getExternalFilesDir(null),"source-slots-ui.json").writeText(state.toString(2))
        }finally {
            fixture.close()
        }
    }

    @Test fun retriesTheOriginalMovieSourceWithoutCrossSiteSearch() = runBlocking {
        val fixture = Settings110Fixture()
        val base = RemoteAuditData().apply { includeRecords = false }
        val movie = base.movies.first()
        val attempts = CopyOnWriteArrayList<SourceOption>()
        val data = object : TvData by base {
            override fun sourcePool(card: Card, initial: List<SourceOption>, crossSite: Boolean, gate: suspend () -> Unit): Flow<SourcePool> {
                assertFalse(crossSite)
                return flowOf(SourcePool(initial.map { it.scoped() }, false))
            }

            override suspend fun resolve(option: SourceOption): ResolvedSource {
                attempts.add(option)
                if (attempts.size == 1) throw DataFailure(FailureKind.ACCESS, "Oynatıcı HTTP 403 döndürdü", "embed", 403)
                return ResolvedSource(option.id, option.label,
                    "http://10.0.2.2:8770/case/source-retry-ui/master.m3u8", StreamType.HLS)
            }
        }
        try {
            fixture.start(AppSettings(entry = "RESTART", crossSiteSources = false), data)
            fixture.main { fixture.activity.findViewById<View>(R.id.tv_nav_movies).performClick() }
            fixture.waitFor { fixture.tag(movie.workId) != null }
            fixture.main { fixture.tag(movie.workId).performClick() }
            fixture.waitFor { fixture.activity.findViewById<View>(R.id.tv_play_action) != null }
            fixture.main { fixture.activity.findViewById<View>(R.id.tv_play_action).performClick() }
            fixture.waitFor(10000) {
                fixture.activity.classicOverlayForTest()?.navigationRootForTest()?.findViewWithTag<View>("retry") != null
            }
            assertEquals(1, attempts.size)
            fixture.main { fixture.activity.classicOverlayForTest()!!.select("retry") }
            fixture.waitFor(7000) { attempts.size == 2 }
            assertEquals(attempts.first(), attempts.last())
            assertEquals(movie.ref, attempts.last().content)
            fixture.waitFor(30000) {
                val state = fixture.activity.snapshotForUiTest()
                state.optBoolean("firstFrame") && state.optLong("positionMs") >= 15000 && state.isNull("error")
            }
            assertEquals(1, fixture.snap().getInt("engineInstancesUsed"))
        } finally {
            fixture.close()
        }
    }

    @Test fun automaticSelectionShowsTheRejectedSourceAlongsideThePlayingSource()=runBlocking {
        val fixture=Settings110Fixture()
        val base=RemoteAuditData()
        val blocked="Site tarayıcı doğrulaması istiyor"
        fun options(card:Card)=listOf(
            SourceOption("blocked",card.ref,"Erişim isteyen kaynak",card.ref.pageUrl),
            SourceOption("ready",card.ref,"Çalışan kaynak",card.ref.pageUrl)
        )
        val data=object:TvData by base {
            override fun episodeSourcePool(item:UnifiedItem,crossSite:Boolean,gate:suspend()->Unit)=
                flowOf(SourcePool(options(item.card).map{it.scoped()},false))
            override suspend fun resolve(option:SourceOption):ResolvedSource {
                if(option.id.endsWith("|blocked"))
                    throw DataFailure(FailureKind.ACCESS,blocked,"browser-challenge",403)
                delay(100)
                return ResolvedSource(option.id,option.label,
                    "http://10.0.2.2:8770/case/source-recovery-ui/master.m3u8",StreamType.HLS)
            }
        }
        fun containsMessage(view:View):Boolean =
            view is android.widget.TextView&&view.text.toString()==blocked||
                view is ViewGroup&&(0 until view.childCount).any{containsMessage(view.getChildAt(it))}
        try {
            fixture.start(AppSettings(entry="RESTART",automaticSources=true,player=PlaybackSettings(controlsSeconds=0)),data)
            fixture.main{fixture.activity.findViewById<View>(R.id.tv_nav_series).performClick()}
            fixture.waitFor{fixture.tag(base.series.workId)!=null}
            fixture.main{fixture.tag(base.series.workId).performClick()}
            val episode=base.eps.first().card.ref.id
            fixture.waitFor{fixture.tag(episode)!=null}
            fixture.main{(fixture.tag(episode) as ViewGroup).getChildAt(0).performClick()}
            fixture.waitFor(15000){
                val playback=fixture.activity.snapshotForUiTest()
                playback.optBoolean("firstFrame")&&playback.optString("sourceId").endsWith("|ready")
            }
            val engine=fixture.snap().getInt("engineIdentity")
            fixture.main{fixture.activity.findViewById<View>(R.id.fm_player_sources).performClick()}
            fixture.waitFor(5000){
                fixture.activity.playerOverlayForTest()?.navigationRootForTest()?.let(::containsMessage)==true
            }
            assertEquals(engine,fixture.snap().getInt("engineIdentity"))
            fixture.screenshot("source-recovery-ui.png")
            fixture.main{fixture.activity.playerOverlayForTest()?.close()}
            fixture.waitFor{fixture.activity.findViewById<View>(R.id.fm_player_next).isEnabled}
            fixture.main{fixture.activity.findViewById<View>(R.id.fm_player_next).performClick()}
            fixture.waitFor(15000){
                val playback=fixture.activity.snapshotForUiTest()
                playback.optBoolean("firstFrame")&&playback.optString("playingTitle")==base.eps[1].card.title
            }
            fixture.main{fixture.activity.findViewById<View>(R.id.fm_player_sources).performClick()}
            fixture.waitFor(5000){
                fixture.activity.playerOverlayForTest()?.navigationRootForTest()?.let(::containsMessage)==true
            }
        } finally {
            fixture.close()
        }
    }

    @Test fun remoteSelectsSourceTracksAndNeighbourEpisodes()=runBlocking {
        val f=Settings110Fixture();val base=RemoteAuditData();val results=JSONArray()
        fun options(card:Card)=listOf("a","b").map{SourceOption(card.ref.id+it,card.ref,"Yerel $it",card.ref.pageUrl)}
        val stamp=android.os.SystemClock.elapsedRealtime()
        val data=object:TvData by base {
            override suspend fun detail(card:Card)=base.detail(card).copy(options=options(card))
            override fun episodeSourcePool(item:UnifiedItem,crossSite:Boolean,gate:suspend()->Unit)=flowOf(SourcePool(options(item.card).map{it.scoped()},false))
            override suspend fun resolve(option:SourceOption)=ResolvedSource(option.id,option.label,"http://10.0.2.2:8770/case/remote-selection-$stamp/master.m3u8?mode=selections",StreamType.HLS,
                referer="https://fixture.example.test/",userAgent="FM-Remote-Audit",subtitles=listOf(Subtitle("http://10.0.2.2:8770/case/remote-text-$stamp/tr.vtt","Türkçe","tr","vtt"),Subtitle("http://10.0.2.2:8770/case/remote-text-$stamp/en.srt","English","en","srt")))
        }
        fun key(k:Int){f.ins.sendKeyDownUpSync(k);f.ins.waitForIdleSync()}
        suspend fun check(name:String,block:suspend()->Unit){try{block();results.put(JSONObject().put("case",name).put("passed",true))}catch(e:Throwable){results.put(JSONObject().put("case",name).put("passed",false).put("error",e.toString()));f.main{f.activity.playerOverlayForTest()?.close()};delay(100)}}
        suspend fun button(id:Int){f.main{f.activity.findViewById<View>(id).requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER)}
        suspend fun choose(id:Int,predicate:(String)->Boolean){
            button(id);f.waitFor(5000){f.activity.playerOverlayForTest()?.showing==true&&f.activity.playerOverlayForTest()?.focusedKey()!=null};val panel=f.activity.playerOverlayForTest()!!
            val target=panel.rowKeys().zip(panel.titles()).first{predicate(it.second)}.first
            // Start at the top via remote, then walk to the desired stable row.
            repeat(panel.rowKeys().size+1){key(KeyEvent.KEYCODE_DPAD_UP)};key(KeyEvent.KEYCODE_DPAD_DOWN)
            repeat(panel.rowKeys().size+1){if(panel.focusedKey()==target)return@repeat;key(KeyEvent.KEYCODE_DPAD_DOWN)}
            assertEquals(target,panel.focusedKey());key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor(5000){!panel.showing}
        }
        try {
            f.start(AppSettings(entry="RESTART",autoNext=false,player=PlaybackSettings(controlsSeconds=0)),data)
            f.main{f.activity.findViewById<View>(R.id.tv_nav_series).requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.tag(base.series.workId)!=null}
            f.main{f.tag(base.series.workId).requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.tag(base.eps.first().card.ref.id)!=null}
            f.main{(f.tag(base.eps.first().card.ref.id)as ViewGroup).getChildAt(0).requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.activity.snapshotForUiTest().optBoolean("firstFrame")}
            check("remote_source_selection") {val engine=f.snap().getInt("engineIdentity");choose(R.id.fm_player_sources){it.endsWith("Yerel b")};f.waitFor{f.activity.snapshotForUiTest().optBoolean("firstFrame")&&f.activity.snapshotForUiTest().optString("sourceId").endsWith("b")};assertEquals(1,f.snap().getInt("sourceIndex"));assertEquals(engine,f.snap().getInt("engineIdentity"))}
            val engine=f.snap().getInt("engineIdentity")
            for((id,label)in listOf(R.id.fm_player_quality to "360p",R.id.fm_player_audio to "İngilizce",R.id.fm_player_subtitles to "Türkçe"))check("remote_track_$label") {choose(id){it.contains(label,ignoreCase=true)};assertEquals(engine,f.snap().getInt("engineIdentity"))}
            check("next_button_enabled_and_enters_next_episode") {
                f.waitFor{f.activity.findViewById<View>(R.id.fm_player_next)?.isEnabled==true}
                button(R.id.fm_player_next);f.waitFor{f.activity.snapshotForUiTest().optString("playingTitle")==base.eps[1].card.title&&f.activity.snapshotForUiTest().optBoolean("firstFrame")}
                assertTrue(f.snap().getLong("positionMs")<5000)
            }
            check("previous_button_returns_previous_episode") {
                f.waitFor{f.activity.findViewById<View>(R.id.fm_player_previous)?.isEnabled==true};button(R.id.fm_player_previous)
                f.waitFor{f.activity.snapshotForUiTest().optString("playingTitle")==base.eps[0].card.title&&f.activity.snapshotForUiTest().optBoolean("firstFrame")}
            }
            check("media_next_previous_work_with_auto_next_off") {
                key(KeyEvent.KEYCODE_MEDIA_NEXT);f.waitFor{f.activity.snapshotForUiTest().optString("playingTitle")==base.eps[1].card.title&&f.activity.snapshotForUiTest().optBoolean("firstFrame")}
                key(KeyEvent.KEYCODE_MEDIA_PREVIOUS);f.waitFor{f.activity.snapshotForUiTest().optString("playingTitle")==base.eps[0].card.title&&f.activity.snapshotForUiTest().optBoolean("firstFrame")}
            }
            f.stop()
        }finally{File(f.context.getExternalFilesDir(null),"remote-playback-selection.json").writeText(results.toString(2));f.close()}
        assertTrue(results.toString(2),(0 until results.length()).all{results.getJSONObject(it).getBoolean("passed")})
    }
}
