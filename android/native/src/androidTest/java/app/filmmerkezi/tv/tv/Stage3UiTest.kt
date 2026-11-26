package app.filmmerkezi.tv.tv
import android.content.Intent
import android.os.SystemClock
import android.view.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class Stage3UiTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity:TvActivity
    private lateinit var fake:Fake
    private var savedPrefs=emptyMap<String,Any?>()
    private val checks=JSONArray()
    private fun main(block:()->Unit)=ins.runOnMainSync(block)
    private fun snap():JSONObject {var value=JSONObject();main{value=activity.snapshotForUiTest()};return value}
    private fun waitFor(timeout:Long=35000,test:(JSONObject)->Boolean):JSONObject{
        val until=SystemClock.elapsedRealtime()+timeout
        while(SystemClock.elapsedRealtime()<until){val s=snap();if(test(s))return s;SystemClock.sleep(100)}
        error("UI player timeout: "+snap())
    }
    private fun click(id:Int){main{checkNotNull(activity.findViewById<View>(id)).performClick()};ins.waitForIdleSync()}
    private fun tile(key:String){var node:View?=null;val until=SystemClock.elapsedRealtime()+10000
        while(SystemClock.elapsedRealtime()<until){main{node=activity.window.decorView.findViewWithTag(key)};if(node!=null)break;SystemClock.sleep(100)}
        main{checkNotNull(node).performClick()};ins.waitForIdleSync()
    }
    private fun choose(containing:String){
        main{
            val panel=checkNotNull(activity.playerOverlayForTest())
            val i=panel.titles().indexOfFirst{it.contains(containing)};check(i>=0);panel.select(panel.rowKeys()[i])
        };ins.waitForIdleSync()
    }
    private fun check(name:String,block:()->Unit){
        val r=JSONObject().put("name",name);checks.put(r)
        try{block();r.put("passed",true)}catch(e:Throwable){r.put("passed",false).put("error",e.stackTraceToString());throw e}
        finally{r.put("state",snap());File(ins.targetContext.getExternalFilesDir(null),"stage3-ui.json").writeText(JSONObject()
            .put("fixture",true).put("api",android.os.Build.VERSION.SDK_INT).put("checks",checks).toString(2))}
    }
    @Before fun start(){
        ins.setInTouchMode(false)
        val prefs=ins.targetContext.getSharedPreferences("player-preferences",0)
        savedPrefs=prefs.all.toMap();prefs.edit().clear().commit()
        fake=Fake();TvActivity.dataFactoryForTests={fake}
        activity=ins.startActivitySync(Intent(ins.targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
    }
    @After fun finish(){
        main{activity.finish()};TvActivity.dataFactoryForTests=null
        val edit=ins.targetContext.getSharedPreferences("player-preferences",0).edit().clear()
        savedPrefs.forEach{(k,v)->when(v){is Boolean->edit.putBoolean(k,v);is Int->edit.putInt(k,v);is String->edit.putString(k,v)}}
        edit.commit()
    }
    @Test fun menusKeysSourceAndNextEpisode(){
        check("Audio/subtitle/quality menus and remote keys"){
            click(R.id.tv_nav_movies);tile(ContentIdentity.key(fake.movie))
            val until=SystemClock.elapsedRealtime()+10000
            while(SystemClock.elapsedRealtime()<until){var yes=false;main{yes=activity.findViewById<View>(R.id.tv_play_action)!=null};if(yes)break;SystemClock.sleep(100)}
            click(R.id.tv_play_action)
            waitFor{it.optBoolean("entry")}
            main{activity.classicOverlayForTest()!!.select("restart")}
            waitFor{it.optBoolean("firstFrame")&&it.optLong("positionMs")>=2000}
            waitFor{it.optString("subtitleText").contains("Türkçe")}
            click(R.id.fm_player_audio);choose("İngilizce")
            waitFor{selected(it,1,"en")}
            click(R.id.fm_player_subtitles);choose("İngilizce")
            waitFor{selected(it,3,"en")}
            waitFor{it.optString("subtitleText").contains("English")}
            click(R.id.fm_player_subtitles);choose("Kapalı")
            waitFor{!selected(it,3,"en")&&!selected(it,3,"tr")}
            waitFor{it.optString("subtitleText").isEmpty()}
            click(R.id.fm_player_quality);choose("360p")
            waitFor{it.optInt("height")==360&&it.optBoolean("isPlaying")}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE);waitFor{!it.optBoolean("isPlaying")&&!it.optBoolean("playWhenReady")}
            val paused=snap().getLong("positionMs");SystemClock.sleep(1200);val pausedAfter=snap();assertTrue("Paused position changed: $paused -> $pausedAfter",kotlin.math.abs(pausedAfter.getLong("positionMs")-paused)<300)
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            waitFor{it.optLong("positionMs")>=paused+9000}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY);waitFor{it.optBoolean("isPlaying")}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE);waitFor{!it.optBoolean("isPlaying")&&!it.optBoolean("playWhenReady")}
            android.os.ParcelFileDescriptor.AutoCloseInputStream(ins.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME")).use{it.readBytes()}
            waitFor{it.optBoolean("suspended")}
            ins.targetContext.startActivity(Intent(ins.targetContext,TvActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            waitFor{!it.optBoolean("suspended")&&it.optBoolean("firstFrame")&&it.optInt("height")==360}
            assertFalse(snap().getBoolean("playWhenReady"))
            waitFor{selected(it,1,"en")}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY);waitFor{it.optBoolean("isPlaying")}
            for(key in listOf(19,20,21,22,22,21)){ins.sendKeyDownUpSync(key);main{assertNotNull(activity.currentFocus)}}
            val before=snap()
            click(R.id.fm_player_sources);choose("Yedek")
            val after=waitFor{it.optInt("sourceIndex")==1&&it.optBoolean("firstFrame")&&it.optBoolean("isPlaying")}
            assertEquals(before.getInt("engineIdentity"),after.getInt("engineIdentity"))
            assertTrue(after.getLong("positionMs")>=before.getLong("positionMs")-1500)
            click(R.id.fm_player_settings)
            main{activity.playerOverlayForTest()!!.select("diagnostics");activity.playerOverlayForTest()!!.close()}
            waitFor{it.optBoolean("diagnosticsEnabled")}
            waitFor{var focused=false;main{focused=activity.hasWindowFocus()};focused}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_STOP)
            main{activity.classicOverlayForTest()!!.select("save")}
            val end=SystemClock.elapsedRealtime()+10000
            while(SystemClock.elapsedRealtime()<end){var yes=false;main{yes=activity.findViewById<View>(R.id.tv_play_action)?.isShown==true};if(yes)break;SystemClock.sleep(100)}
            main{assertTrue(activity.findViewById<View>(R.id.tv_play_action).isShown)}
        }
        check("Natural episode end opens next episode on the same foreground engine"){
            click(R.id.tv_nav_series);tile(ContentIdentity.key(fake.series))
            val end=SystemClock.elapsedRealtime()+10000
            while(SystemClock.elapsedRealtime()<end){var yes=false;main{yes=activity.findViewById<View>(R.id.tv_play_action)!=null};if(yes)break;SystemClock.sleep(100)}
            click(R.id.tv_play_action)
            waitFor{it.optBoolean("firstFrame")||it.optBoolean("entry")}
            main{activity.classicOverlayForTest()?.select("restart")}
            waitFor{it.optString("playingTitle")=="Bölüm 1"&&it.optBoolean("firstFrame")}
            val identity=snap().getInt("engineIdentity")
            val next=waitFor{it.optString("playingTitle")=="Bölüm 2"&&it.optBoolean("firstFrame")}
            assertEquals(identity,next.getInt("engineIdentity"))
            assertTrue(fake.resolves.contains("episode-2"))
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        }
    }
    private fun selected(s:JSONObject,type:Int,lang:String):Boolean{
        val tracks=s.optJSONArray("tracks")?:return false
        return(0 until tracks.length()).map{tracks.getJSONObject(it)}.any{it.optInt("type")==type&&it.optBoolean("selected")&&it.optString("language")==lang}
    }
    private class Fake:TvData {
        val movie=Card(ContentRef("filmcibaba","https://filmizle.ink/player-fixture/"),"Kaynak deneyi",year=2026)
        val series=Card(ContentRef("filmcibaba","https://filmizle.ink/player-series/",ContentKind.SERIES),"Dizi deneyi",year=2026)
        val resolves=java.util.Collections.synchronizedList(mutableListOf<String>())
        private val stored=ConcurrentHashMap<String,LibraryRecord>()
        private val base="http://10.0.2.2:8768/case/ui3-"+SystemClock.elapsedRealtime()
        override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(ContentIdentity.merge(listOf(if(kind==ContentKind.SERIES)series else movie)),emptyMap(),emptyList()))
        override fun search(query:String,cursors:Map<String,PageCursor>)=browse(ContentKind.MOVIE,cursors)
        override suspend fun detail(card:Card):Detail {
            delay(80)
            return Detail(card,"Player UI fixture",episodes=if(card.ref.kind==ContentKind.SERIES)episodes(card)else emptyList(),
                options=if(card.ref.kind==ContentKind.SERIES)emptyList()else if(card.ref.kind==ContentKind.EPISODE)
                    listOf(SourceOption("episode-"+card.episodeNumber!!.number,card.ref,"Bölüm","https://hotstream.club/fixture"))
                else listOf(SourceOption("main",card.ref,"Ana","https://hotstream.club/fixture"),SourceOption("backup",card.ref,"Yedek","https://hotstream.club/backup")))
        }
        override suspend fun episodes(card:Card)=listOf(1,2).map{n->
            val c=Card(ContentRef("filmcibaba","https://filmizle.ink/bolum/fixture-$n/",ContentKind.EPISODE),"Bölüm $n",year=2026,
                seriesIdentity=ContentIdentity.key(series),episodeNumber=EpisodeNumber(1,n),seriesRef=series.ref)
            Episode(c,series.ref,EpisodeNumber(1,n))
        }
        override suspend fun resolve(option:SourceOption):ResolvedSource{
            val local=option.id.substringAfterLast('|');resolves.add(local);delay(80)
            if(local.startsWith("episode"))return ResolvedSource(option.id,option.label,base+"-"+local+"/clip.mp4",StreamType.MP4)
            return ResolvedSource(option.id,option.label,base+"-"+local+"/master.m3u8?mode=selections",StreamType.HLS,
                "https://fixture.example.test/","FM-UI3",subtitles=listOf(Subtitle(base+"-"+local+"/tr.vtt","Türkçe","tr"),Subtitle(base+"-"+local+"/en.srt","English","en","srt")))
        }
        override suspend fun records()=stored.values.toList()
        override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){
            val key=ContentIdentity.key(card)
            stored.compute(key){_,old->LibraryRecord(key,card,listed?:old?.listed?:false,position?:old?.positionMs?:0,duration?:old?.durationMs?:0,System.currentTimeMillis())}
        }
        override suspend fun remember(query:String)=Unit
        override suspend fun searches()=emptyList<String>()
    }
}
