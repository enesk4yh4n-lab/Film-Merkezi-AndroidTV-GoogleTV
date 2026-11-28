package app.filmmerkezi.tv.tv

import android.view.KeyEvent
import android.view.View
import app.filmmerkezi.tv.PlaybackSettings
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.SubtitlePolicy
import app.filmmerkezi.tv.settings.AdultPin
import app.filmmerkezi.tv.settings.AppSettings
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ParityPlaybackTest {
    @Test fun lockedPinSurvivesPreferenceReopen(){
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".qa"))
        val prefs=context.getSharedPreferences("adult-pin",0)
        try{
            var time=5_000L
            val pin=AdultPin(context){time}
            pin.replace("2648")
            repeat(4){assertEquals(AdultPin.Result.WRONG,pin.verify("0000"))}
            assertEquals(AdultPin.Result.LOCKED,pin.verify("0000"))
            val reopened=AdultPin(context){time}
            assertEquals(AdultPin.Result.LOCKED,reopened.verify("2648"))
            time+=300_001L
            assertEquals(AdultPin.Result.ACCEPTED,reopened.verify("2648"))
        }finally{prefs.edit().clear().commit()}
    }

    @Test fun subtitleOffsetAndSpeedKeepTheEngineAndPlaybackPosition()=runBlocking{
        val fixture=Settings110Fixture(continuous=true)
        try{
            fixture.start(AppSettings(entry="RESTART",player=PlaybackSettings(subtitles=SubtitlePolicy.ON,controlsSeconds=0)))
            fixture.play()
            val first=fixture.snap()
            delay(16_000)
            fixture.waitFor(8000){fixture.activity.snapshotForUiTest().getLong("positionMs")-first.getLong("positionMs")>=15_000L}
            fixture.ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            fixture.main{fixture.activity.findViewById<View>(R.id.fm_player_subtitles).performClick()}
            fixture.waitFor(9000){fixture.activity.playerPanelForTest()?.navigationRootForTest()?.findViewWithTag<View>("subtitle-sync")!=null}
            fixture.main{val panel=fixture.activity.playerPanelForTest()!!;panel.select("subtitle-sync");panel.select("later")}
            fixture.waitFor(9000){fixture.activity.subtitleOffsetForTest()==500}
            fixture.main{fixture.activity.playerPanelForTest()!!.close();fixture.activity.findViewById<View>(R.id.fm_player_settings).performClick()
                val panel=fixture.activity.playerPanelForTest()!!;panel.select("speed");panel.select("1.25");panel.close()}
            val last=fixture.snap()
            assertEquals(first.getInt("engineIdentity"),last.getInt("engineIdentity"))
            assertTrue(last.getLong("positionMs")>=first.getLong("positionMs")+15_000L)
            assertTrue(last.getBoolean("isPlaying"))
            fixture.main{assertEquals(1.25f,fixture.activity.speedForTest()?:0f,0.001f)}
            java.io.File(fixture.context.getExternalFilesDir(null),"parity-playback.json").writeText(JSONObject()
                .put("elapsedPlaybackMs",last.getLong("positionMs")-first.getLong("positionMs"))
                .put("firstFrameMs",first.getLong("firstFrameMs")).put("rebufferCount",last.getInt("rebufferCount"))
                .put("engineUnchanged",true).put("subtitleOffsetMs",500).put("speed",1.25).toString(2))
            fixture.screenshot("parity-player.png")
            fixture.stop()
        }finally{fixture.close()}
    }

    @Test fun playNextCountdownAndEndSleepUseTheSameEpisodeSession()=runBlocking {
        val fixture=Settings110Fixture()
        val base=RemoteAuditData()
        val stamp=android.os.SystemClock.elapsedRealtime()
        val data=object:TvData by base {
            fun option(card:Card)=SourceOption(card.ref.id,card.ref,"Local",card.ref.pageUrl).scoped()
            override fun episodeSourcePool(item:UnifiedItem,crossSite:Boolean,gate:suspend()->Unit)=flowOf(SourcePool(listOf(option(item.card)),false))
            override suspend fun resolve(option:SourceOption)=ResolvedSource(option.id,option.label,
                "http://10.0.2.2:8770/case/parity-next-$stamp/clip.mp4",StreamType.MP4)
        }
        try{
            fixture.start(AppSettings(entry="RESTART",autoNext=true),data)
            fixture.main{fixture.activity.findViewById<View>(R.id.tv_nav_series).performClick()}
            fixture.waitFor{fixture.tag(base.series.workId)!=null}
            fixture.main{fixture.tag(base.series.workId).performClick()}
            fixture.waitFor{fixture.tag(base.eps[0].card.ref.id)!=null}
            fixture.main{(fixture.tag(base.eps[0].card.ref.id) as android.view.ViewGroup).getChildAt(0).performClick()}
            fixture.waitFor(9000){fixture.activity.snapshotForUiTest().optBoolean("firstFrame")}
            fixture.main{fixture.activity.endForTest()}
            fixture.waitFor(7000){fixture.activity.classicOverlayForTest()?.navigationRootForTest()?.findViewWithTag<View>("play")!=null}
            fixture.main{fixture.activity.classicOverlayForTest()!!.select("play")}
            fixture.waitFor(9000){fixture.activity.snapshotForUiTest().optString("playingTitle")==base.eps[1].card.title&&
                fixture.activity.snapshotForUiTest().optBoolean("firstFrame")}
            fixture.main{fixture.activity.sleepAtEndForTest();fixture.activity.endForTest()}
            fixture.waitFor(7000){fixture.activity.snapshotForUiTest().isNull("playingTitle")}
        }finally{fixture.close()}
    }
}
