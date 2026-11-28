package app.filmmerkezi.tv.tv

import android.os.SystemClock
import android.view.View
import androidx.media3.common.C
import app.filmmerkezi.tv.PlaybackPreferencePolicy
import app.filmmerkezi.tv.PlaybackSettings
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.settings.AppSettings
import app.filmmerkezi.tv.settings.SourceScanProfile
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlayerRecoveryUiTest {
    @Test fun autoSwitchKeepsPositionSelectedAudioAndSubtitles()=runBlocking{exercise(true)}
    @Test fun manualFailureExplainsProblemAndWaitsForChoice()=runBlocking{exercise(false)}
    @Test fun sourceErrorsExplainKnownCausesWithoutLeakingRawDetails(){
        val context=Settings110Fixture().context
        val cases=listOf(403 to R.string.player_failure_access,404 to R.string.player_failure_missing,
            410 to R.string.player_failure_missing,429 to R.string.player_failure_rate,503 to R.string.player_failure_server)
        for((code,summary) in cases){
            assertEquals(context.getString(R.string.player_failure_with_code,context.getString(summary),code),
                sourceFailureText(context,DataFailure(FailureKind.HTTP,"https://example.test/private","resolve",code)))
        }
        assertEquals(context.getString(R.string.player_failure_timeout),
            sourceFailureText(context,DataFailure(FailureKind.TIMEOUT,"transport exception")))
        assertEquals(context.getString(R.string.player_failure_unsupported_source),
            sourceFailureText(context,DataFailure(FailureKind.UNSUPPORTED,"resolver detail")))
    }

    private suspend fun exercise(automatic:Boolean){
        val fixture=Settings110Fixture()
        val installed=Sites.all
        val card=fixture.card
        val id="recovery-${if(automatic)"auto" else "manual"}-${SystemClock.elapsedRealtime()}"
        val base="http://10.0.2.2:8776/case/$id"
        val primary=SourceOption("filmcibaba|fixture|primary",card.ref,"Yerel ana kaynak",card.ref.pageUrl)
        val backup=SourceOption("filmcibaba|fixture|backup",card.ref,"Yerel yedek kaynak",card.ref.pageUrl)
        val backupResolutions=AtomicInteger()
        val savedProgress=AtomicReference<Pair<Long,Long>?>()
        val data=object:TvData by RemoteAuditData(){
            override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(listOf(UnifiedItem(card.workId,card,listOf(card))),emptyMap(),emptyList()))
            override fun filteredBrowse(siteId:String,query:CatalogQuery,cursors:Map<String,PageCursor>)=browse(query.kind,cursors)
            override suspend fun records()=emptyList<LibraryRecord>()
            override suspend fun savePlayback(cp:PlaybackCheckpoint,position:Long,duration:Long){savedProgress.set(position to duration)}
            override fun episodeSourcePool(item:UnifiedItem,crossSite:Boolean,gate:suspend()->Unit)=flowOf(SourcePool(listOf(primary,backup),false))
            override suspend fun resolve(option:SourceOption):ResolvedSource {
                val isBackup=option.id==backup.id
                if(isBackup){backupResolutions.incrementAndGet();delay(500)}
                val address="$base-${if(isBackup)"b" else "a"}"
                return ResolvedSource(option.id,option.label,"$address/master.m3u8?mode=selections",StreamType.HLS,
                    referer="https://fixture.example.test/",userAgent="FM-PlayerRecovery",
                    subtitles=listOf(Subtitle("$address/tr.vtt","Türkçe","tr","vtt"),Subtitle("$address/en.srt","English","en","srt")))
            }
        }
        val evidence=JSONObject().put("automatic",automatic)
        val name=if(automatic)"auto" else "manual"
        fun selection(type:Int)=fixture.activity.playbackSessionForTest().trackChoices(type).firstOrNull{it.selected}?.format?.language
        try{
            fixture.start(AppSettings(entry="RESTART",automaticSources=automatic,rememberTracks=false,sourceScan=SourceScanProfile.SELECTED,
                player=PlaybackSettings(controlsSeconds=0)),customData=data)
            fixture.main{fixture.activity.findViewById<View>(R.id.tv_nav_series).performClick()}
            fixture.waitFor{fixture.tag(card.workId)!=null}
            fixture.main{fixture.tag(card.workId).performClick()}
            if(!automatic){
                fixture.waitFor{fixture.activity.classicOverlayForTest()?.rowKeys()?.contains(primary.id)==true}
                fixture.main{fixture.activity.classicOverlayForTest()!!.select(primary.id)}
            }
            fixture.waitFor{val s=fixture.activity.snapshotForUiTest();s.optString("sourceId")==primary.id&&s.optBoolean("firstFrame")&&s.optBoolean("isPlaying")}
            val engine=fixture.snap().getInt("engineIdentity")
            fixture.waitFor{fixture.activity.playbackSessionForTest().trackChoices(C.TRACK_TYPE_TEXT).size>=2}
            fixture.main{
                val player=fixture.activity.playbackSessionForTest()
                for(type in listOf(C.TRACK_TYPE_AUDIO,C.TRACK_TYPE_TEXT)){
                    val english=player.trackChoices(type).first{PlaybackPreferencePolicy.matches(it.format.language,"en")}
                    assertTrue(player.chooseTrack(type,english))
                }
                player.player!!.seekTo(45000)
            }
            fixture.waitFor{fixture.activity.snapshotForUiTest().optLong("positionMs")>=45000&&
                PlaybackPreferencePolicy.matches(selection(C.TRACK_TYPE_AUDIO),"en")&&PlaybackPreferencePolicy.matches(selection(C.TRACK_TYPE_TEXT),"en")}
            val before=fixture.snap().getLong("positionMs")
            evidence.put("cutRequestedMs",before).put("engineBefore",engine)
            withContext(Dispatchers.IO){
                val connection=URL("$base-a/control?expire=true").openConnection() as HttpURLConnection
                try{connection.connectTimeout=4000;connection.readTimeout=4000;assertEquals(200,connection.responseCode)}finally{connection.disconnect()}
            }
            if(!automatic){
                fixture.waitFor(50000){fixture.activity.playerPanelForTest()?.showing==true}
                val failed=fixture.snap()
                evidence.put("failedPositionMs",failed.getLong("positionMs"))
                fixture.main{
                    val panel=fixture.activity.playerPanelForTest()!!
                    val message=fixture.activity.getString(R.string.player_failure_with_code,
                        fixture.activity.getString(R.string.player_failure_access),403)
                    assertEquals(message,panel.messageForTest())
                    assertEquals(message,fixture.activity.playbackSessionForTest().sourceEntries().first{it.id==primary.id}.reason)
                }
                fixture.screenshot("classic-player-source-error.png")
                delay(1000)
                assertEquals(0,backupResolutions.get())
                assertEquals(primary.id,fixture.snap().getString("sourceId"))
                assertTrue(failed.getLong("positionMs")>=before-1000)
                fixture.main{fixture.activity.saveForTest()}
                fixture.waitFor{savedProgress.get()?.let{(position,duration)->position==failed.getLong("positionMs")&&duration>position}==true}
                evidence.put("savedWhileStoppedMs",savedProgress.get()?.first)
                fixture.main{fixture.activity.playerPanelForTest()!!.select(backup.id)}
            }
            fixture.waitFor(50000){val s=fixture.activity.snapshotForUiTest();s.optString("sourceId")==backup.id&&s.optBoolean("firstFrame")&&s.optBoolean("isPlaying")}
            val resumed=fixture.snap()
            fixture.main {
                val expected=if(automatic)R.string.player_switch_success else R.string.player_source
                assertEquals(fixture.activity.getString(expected,backup.label),fixture.activity.playerNoticeForTest())
            }
            if(automatic)fixture.screenshot("classic-player-source-fallback.png")
            evidence.put("resumedPositionMs",resumed.getLong("positionMs")).put("engineAfter",resumed.getInt("engineIdentity"))
                .put("replacementFirstFrameMs",resumed.optLong("firstFrameMs")).put("sameEngine",engine==resumed.getInt("engineIdentity"))
            val failedAt=resumed.getLong("lastFailurePositionMs")
            evidence.put("failureAnchorMs",failedAt)
            assertEquals(engine,resumed.getInt("engineIdentity"))
            assertTrue(resumed.getLong("positionMs")>=before-1000)
            assertTrue(kotlin.math.abs(resumed.getLong("positionMs")-failedAt)<=1250L)
            assertEquals(if(automatic)1 else 0,resumed.getInt("sourceSwitches"))
            fixture.waitFor{val s=fixture.activity.snapshotForUiTest();s.optLong("positionMs")>=resumed.optLong("positionMs")+1500&&
                s.optInt("audioRenderedBuffers")>0&&PlaybackPreferencePolicy.matches(selection(C.TRACK_TYPE_AUDIO),"en")&&
                PlaybackPreferencePolicy.matches(selection(C.TRACK_TYPE_TEXT),"en")&&s.optString("subtitleText").contains("English subtitle fixture")}
            val after=fixture.snap()
            evidence.put("advancedToMs",after.getLong("positionMs")).put("audioBuffers",after.getInt("audioRenderedBuffers"))
                .put("subtitle",after.getString("subtitleText")).put("passed",true)
        }finally{
            File(fixture.context.getExternalFilesDir(null),"player-recovery-$name.json").writeText(evidence.toString(2))
            fixture.close();Sites.installed(installed)
        }
    }
}
