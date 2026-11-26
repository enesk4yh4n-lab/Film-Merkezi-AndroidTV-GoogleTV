package app.filmmerkezi.tv.tv

import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File

class Settings106UiTest {
    @Test fun automaticResumeAndSleepAtEpisodeEndSaveWithoutStartingTheNextEpisode()=runBlocking {
        val ins=InstrumentationRegistry.getInstrumentation();val c=ins.targetContext;val repo=SettingsRepository.get(c);val original=repo.value
        val prefs=c.getSharedPreferences("interface-preferences",0);val oldUi=prefs.all.toMap();var a:TvActivity?=null
        val parent=ContentRef("filmcibaba","https://filmizle.ink/settings-series/",ContentKind.SERIES)
        val first=Card(ContentRef("filmcibaba","https://filmizle.ink/settings-1-sezon-1-bolum/",ContentKind.EPISODE),"Settings fixture",seriesIdentity="work:settings-series",episodeNumber=EpisodeNumber(1,1),seriesRef=parent,workId="work:settings-episode")
        val second=first.copy(ref=first.ref.copy(pageUrl="https://filmizle.ink/settings-1-sezon-2-bolum/"),episodeNumber=EpisodeNumber(1,2),workId="work:settings-second")
        var resolved=0;var saved=0;var episodeLists=0;var lastPosition=0L
        val report=JSONObject().put("fixture",true)
        fun main(block:()->Unit)=ins.runOnMainSync(block)
        suspend fun waitFor(check:()->Boolean){val end=SystemClock.elapsedRealtime()+40000;while(SystemClock.elapsedRealtime()<end){var yes=false;main{yes=check()};if(yes)return;delay(100)};error("Settings UI player timeout")}
        try{
            repo.update{AppSettings(entry="RESUME",resumeRewind=10)};prefs.edit().putString("style","classic").putBoolean("configured",true).commit()
            TvActivity.dataFactoryForTests={object:TvData{
                private fun option(ref:ContentRef)=SourceOption(ref.id,ref,"Local",ref.pageUrl)
                override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(listOf(UnifiedItem(first.workId,first,listOf(first))),emptyMap(),emptyList()))
                override fun search(query:String,cursors:Map<String,PageCursor>)=browse(ContentKind.SERIES,cursors)
                override suspend fun detail(card:Card)=Detail(card,options=listOf(option(card.ref)))
                override suspend fun episodes(card:Card):List<Episode>{episodeLists++;return listOf(Episode(first,parent,EpisodeNumber(1,1)),Episode(second,parent,EpisodeNumber(1,2)))}
                override suspend fun episodeCatalog(card:Card):SeriesEpisodes{episodeLists++;return SeriesEpisodes(listOf(Episode(first,parent,EpisodeNumber(1,1)),Episode(second,parent,EpisodeNumber(1,2))))}
                override fun episodeSourcePool(item:UnifiedItem,crossSite:Boolean,gate:suspend()->Unit)=flowOf(SourcePool(listOf(option(item.card.ref).scoped()),false))
                override suspend fun resolve(option:SourceOption):ResolvedSource{resolved++;return ResolvedSource(option.id,option.label,"http://10.0.2.2:8770/case/settings106-ui/master.m3u8",StreamType.HLS,referer="https://fixture.example.test/",userAgent="FM-Settings106-UI")}
                override suspend fun records()=emptyList<LibraryRecord>()
                override suspend fun beginPlayback(card:Card)=PlaybackCheckpoint("settings-ui-"+card.ref.id,card,30000,120000)
                override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){saved++;lastPosition=position?:0}
                override suspend fun remember(query:String){};override suspend fun searches()=emptyList<String>()
            }}
            a=ins.startActivitySync(Intent(c,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
            main{a!!.findViewById<View>(R.id.tv_nav_series).performClick()}
            waitFor{a!!.window.decorView.findViewWithTag<View>(first.workId)!=null}
            main{a!!.window.decorView.findViewWithTag<View>(first.workId).performClick()}
            waitFor{a!!.snapshotForUiTest().optBoolean("firstFrame")&&a!!.snapshotForUiTest().optLong("positionMs")>=20000&&episodeLists>0}
            main{assertFalse(a!!.snapshotForUiTest().optBoolean("entry"));report.put("resumed",a!!.snapshotForUiTest());a!!.sleepForTest(1)}
            delay(30);main{a!!.endForTest()}
            waitFor{a!!.snapshotForUiTest().isNull("playingTitle")&&saved>0}
            assertEquals(1,resolved);assertTrue(lastPosition>0)
            report.put("status","PASS").put("resolved",resolved).put("saved",saved).put("lastPosition",lastPosition)
        }catch(e:Throwable){report.put("status","FAIL").put("error",e.toString());throw e}
        finally{File(c.getExternalFilesDir(null),"settings106-ui.json").writeText(report.toString(2));a?.let{main{it.finish()}};TvActivity.dataFactoryForTests=null;repo.update{original}
            val e=prefs.edit().clear();oldUi.forEach{(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v)}};e.commit()}
        Unit
    }
}
