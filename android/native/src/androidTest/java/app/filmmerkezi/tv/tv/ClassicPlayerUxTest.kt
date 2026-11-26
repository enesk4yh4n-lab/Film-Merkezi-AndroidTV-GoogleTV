package app.filmmerkezi.tv.tv

import android.content.Intent
import android.os.SystemClock
import android.view.*
import android.widget.*
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.json.*
import java.io.File

/** Only changed Classic search/windows/player interactions. Network is local fixture media. */
class ClassicPlayerUxTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity:TvActivity
    private lateinit var fake:Fake
    private var saved=emptyMap<String,Map<String,*>>()
    private fun main(block:()->Unit)=ins.runOnMainSync(block)
    private fun waitFor(timeout:Long=20000,test:()->Boolean){val until=SystemClock.elapsedRealtime()+timeout
        while(SystemClock.elapsedRealtime()<until){var yes=false;main{yes=test()};if(yes)return;SystemClock.sleep(80)};error("Classic UX timeout: "+snap())}
    private fun snap():JSONObject{var result=JSONObject();main{result=activity.snapshotForUiTest()};return result}
    private fun click(id:Int){waitFor{activity.findViewById<View>(id)!=null};main{activity.findViewById<View>(id).performClick()};ins.waitForIdleSync()}
    private fun key(key:String,player:Boolean=false){waitFor{(if(player)activity.playerOverlayForTest()else activity.classicOverlayForTest())?.showing==true};main{checkNotNull(if(player)activity.playerOverlayForTest()else activity.classicOverlayForTest()).select(key)};ins.waitForIdleSync()}
    private fun tile(key:String){waitFor{activity.window.decorView.findViewWithTag<View>(key)!=null};main{activity.window.decorView.findViewWithTag<View>(key).performClick()};ins.waitForIdleSync()}
    private fun texts(v:View):List<String> = buildList{if(v is TextView)add(v.text.toString());if(v is ViewGroup)for(i in 0 until v.childCount)addAll(texts(v.getChildAt(i)))}
    private fun capture(name:String){ins.waitForIdleSync();SystemClock.sleep(250);val bmp=checkNotNull(ins.uiAutomation.takeScreenshot());File(ins.targetContext.getExternalFilesDir(null),"ux-"+name+".png").outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()}
    private fun movie(){click(R.id.tv_nav_movies);tile(ContentIdentity.key(fake.movie));waitFor{activity.findViewById<View>(R.id.tv_play_action)!=null}}
    private fun startPlayback(){click(R.id.tv_play_action);waitFor(35000){activity.snapshotForUiTest().optBoolean("firstFrame")||activity.classicOverlayForTest()?.showing==true}
        if(!snap().optBoolean("firstFrame"))key("restart");waitFor(35000){activity.snapshotForUiTest().optBoolean("firstFrame")}}
    @Before fun prepare(){
        ins.setInTouchMode(false)
        saved=listOf("player-preferences","catalog-preferences","interface-preferences").associateWith{ins.targetContext.getSharedPreferences(it,0).all.toMap()}
        ins.targetContext.getSharedPreferences("player-preferences",0).edit().clear().commit()
        ins.targetContext.getSharedPreferences("catalog-preferences",0).edit().clear().commit()
        ins.targetContext.getSharedPreferences("interface-preferences",0).edit().putBoolean("configured",true).putString("style","classic").commit()
        fake=Fake();TvActivity.dataFactoryForTests={fake}
        activity=ins.startActivitySync(Intent(ins.targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
    }
    @After fun finish(){
        if(::activity.isInitialized)main{activity.finish()};TvActivity.dataFactoryForTests=null
        saved.forEach{(name,values)->val e=ins.targetContext.getSharedPreferences(name,0).edit().clear();values.forEach{(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v)}};e.commit()}
        ins.waitForIdleSync();SystemClock.sleep(300);if(::fake.isInitialized)fake.close()
    }
    @Test fun searchTypesCountsLateMergePaginationAndClassicSites(){
        click(R.id.tv_site_picker);waitFor{activity.classicOverlayForTest()?.rowKeys()?.size==Sites.all.size+1};capture("catalog-sites")
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitFor{activity.classicOverlayForTest()==null};main{assertTrue(activity.findViewById<View>(R.id.tv_site_picker).hasFocus())}
        main{activity.findViewById<EditText>(R.id.tv_query).setText("Deney");activity.findViewById<EditText>(R.id.tv_query).onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH)}
        waitFor{texts(activity.window.decorView).contains("Diziler (1)")}
        capture("search-all")
        main{activity.window.decorView.findViewWithTag<View>("search-type:SERIES").performClick()}
        tile(ContentIdentity.key(fake.series));waitFor{activity.findViewById<View>(R.id.tv_play_action)!=null}
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitFor{activity.window.decorView.findViewWithTag<View>("search-type:SERIES")?.isSelected==true}
        main{assertEquals(1,fake.searchCalls)}
        main{activity.window.decorView.findViewWithTag<View>("search-type:COLLECTIONS").performClick()}
        waitFor{activity.window.decorView.findViewWithTag<View>("feed:more")!=null};tile("feed:more")
        waitFor{texts(activity.window.decorView).contains("Koleksiyonlar (1)")}
        main{assertEquals(2,fake.searchCalls)}
        click(R.id.tv_nav_movies);tile(ContentIdentity.key(fake.movie));waitFor{activity.findViewById<View>(R.id.fm_detail_sites)!=null}
        click(R.id.fm_detail_sites);waitFor{activity.classicOverlayForTest()?.rowKeys()?.size==2};capture("detail-sites")
        main{assertTrue(activity.classicOverlayForTest()!!.titles().any{it.contains("Filmci")||it.contains("Film")})}
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitFor{activity.classicOverlayForTest()==null};main{assertTrue(activity.findViewById<View>(R.id.fm_detail_sites).hasFocus())}
    }
    @Test fun entryExitDecisionsAndDiscardAfterAutosave(){
        movie();click(R.id.tv_play_action);capture("start-new")
        main{assertEquals(0,fake.resolves);assertEquals("restart",activity.classicOverlayForTest()?.focusedKey())};key("exit")
        waitFor{!activity.snapshotForUiTest().optBoolean("entry")};main{assertEquals(0,fake.resolves)}
        runBlocking{fake.update(fake.movie,position=25000,duration=600000)}
        click(R.id.tv_play_action);waitFor{activity.classicOverlayForTest()?.focusedKey()=="continue"};capture("start-resume");key("continue")
        waitFor(35000){activity.snapshotForUiTest().optBoolean("firstFrame")&&activity.snapshotForUiTest().optLong("positionMs")>=19000}
        click(R.id.fm_player_exit);waitFor{activity.snapshotForUiTest().optBoolean("exit")};capture("exit")
        val paused=snap().getLong("positionMs");SystemClock.sleep(500);assertTrue(kotlin.math.abs(snap().getLong("positionMs")-paused)<200)
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitFor{!activity.snapshotForUiTest().optBoolean("exit")&&activity.snapshotForUiTest().optBoolean("isPlaying")}
        click(R.id.fm_player_exit);key("restart");waitFor{activity.snapshotForUiTest().optLong("positionMs")<3000&&activity.snapshotForUiTest().optBoolean("isPlaying")}
        main{activity.saveForTest()};SystemClock.sleep(250)
        click(R.id.fm_player_exit);key("discard");waitFor{activity.findViewById<View>(R.id.tv_play_action)?.isShown==true&&!activity.snapshotForUiTest().optBoolean("exit")}
        assertEquals(25000L,runBlocking{fake.records().single().positionMs})
        startPlayback();click(R.id.fm_player_exit);key("continue");waitFor{activity.snapshotForUiTest().optBoolean("isPlaying")}
        fake.failFinish=true;click(R.id.fm_player_exit);key("save")
        waitFor{activity.classicOverlayForTest()?.focusedKey()=="continue"&&activity.snapshotForUiTest().optBoolean("exit")}
        main{assertFalse(activity.snapshotForUiTest().optBoolean("isPlaying"))};key("save")
        waitFor{activity.findViewById<View>(R.id.tv_play_action)?.isShown==true}
        assertTrue(runBlocking{fake.records().single().positionMs}<25000)
    }
    @Test fun panelsKeepEngineTracksRemoteAndTwentySecondsProgress(){
        movie();startPlayback();val engine=snap().getInt("engineIdentity")
        val origin=snap().getLong("positionMs")
        click(R.id.fm_player_sources);waitFor{activity.playerOverlayForTest()?.rowKeys()?.any{it.startsWith("header:")}==true};capture("sources")
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitFor{activity.findViewById<View>(R.id.fm_player_sources).hasFocus()}
        fun chooseTrack(button:Int,match:String){click(button);waitFor{activity.playerOverlayForTest()?.titles()?.any{it.contains(match)}==true};capture(if(button==R.id.fm_player_audio)"audio"else if(button==R.id.fm_player_subtitles)"subtitles"else "quality")
            main{val window=activity.playerOverlayForTest()!!;val i=window.titles().indexOfFirst{it.contains(match)};window.select(window.rowKeys()[i])};ins.waitForIdleSync()}
        chooseTrack(R.id.fm_player_audio,"İngilizce");chooseTrack(R.id.fm_player_subtitles,"İngilizce");chooseTrack(R.id.fm_player_quality,"360p")
        waitFor{activity.snapshotForUiTest().optInt("height")==360}
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE);waitFor{!activity.snapshotForUiTest().optBoolean("isPlaying")}
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY);waitFor{activity.snapshotForUiTest().optBoolean("isPlaying")}
        waitFor(30000){activity.snapshotForUiTest().optLong("positionMs")>=origin+20000}
        assertEquals(engine,snap().getInt("engineIdentity"));capture("controls")
        val before=snap().getLong("positionMs");click(R.id.fm_player_sources)
        main{val panel=activity.playerOverlayForTest()!!;panel.select(panel.rowKeys().first{it.endsWith("|backup")})}
        waitFor{activity.snapshotForUiTest().optString("sourceId").endsWith("|backup")&&activity.snapshotForUiTest().optBoolean("firstFrame")}
        assertEquals(engine,snap().getInt("engineIdentity"));assertTrue(snap().getLong("positionMs")>=before-1500)
        File(ins.targetContext.getExternalFilesDir(null),"ux-playback.json").writeText(snap().put("fixture",true).toString(2))
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_STOP);waitFor{activity.snapshotForUiTest().optBoolean("exit")};key("discard")
    }
    @Test fun manualEpisodeNavigationKeepsEngineAndPreviousCheckpoint(){
        click(R.id.tv_nav_series);tile(ContentIdentity.key(fake.series));waitFor{activity.findViewById<View>(R.id.tv_play_action)!=null}
        startPlayback();waitFor{activity.findViewById<View>(R.id.fm_player_next).isEnabled}
        click(R.id.fm_player_settings);key("next",true);ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        val engine=snap().getInt("engineIdentity")
        main{assertFalse(activity.findViewById<View>(R.id.fm_player_previous).isEnabled)}
        click(R.id.fm_player_next);waitFor(35000){activity.snapshotForUiTest().optString("playingTitle")=="Bölüm 2"&&activity.snapshotForUiTest().optBoolean("firstFrame")}
        assertEquals(engine,snap().getInt("engineIdentity"));waitFor{activity.findViewById<View>(R.id.fm_player_previous).isEnabled}
        main{assertFalse(activity.findViewById<View>(R.id.fm_player_next).isEnabled)}
        click(R.id.fm_player_exit);key("discard");waitFor{!activity.snapshotForUiTest().optBoolean("exit")}
        val rows=runBlocking{fake.records()};assertTrue(rows.any{it.card.episodeNumber?.number==1});assertFalse(rows.any{it.card.episodeNumber?.number==2})
    }
    @Test fun manualSourcePickerAndFailedOrCancelledEpisodeKeepPlayback(){
        movie();startPlayback();click(R.id.fm_player_settings);key("auto",true);ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        click(R.id.fm_player_exit);key("save");waitFor{activity.findViewById<View>(R.id.tv_play_action)?.isShown==true}
        click(R.id.tv_play_action);key("restart");waitFor{activity.classicOverlayForTest()?.rowKeys()?.any{it.endsWith("|backup")}==true}
        val before=fake.resolves;SystemClock.sleep(250);assertEquals(before,fake.resolves);capture("manual-sources")
        main{val panel=activity.classicOverlayForTest()!!;panel.select(panel.rowKeys().first{it.endsWith("|backup")})}
        waitFor{activity.snapshotForUiTest().optBoolean("firstFrame")&&activity.snapshotForUiTest().optString("sourceId").endsWith("|backup")}
        click(R.id.fm_player_exit);key("save");waitFor{activity.findViewById<View>(R.id.tv_play_action)?.isShown==true}
        click(R.id.tv_nav_series);tile(ContentIdentity.key(fake.series));click(R.id.tv_play_action)
        waitFor{activity.classicOverlayForTest()?.rowKeys()?.any{it.endsWith("|main")}==true}
        main{val panel=activity.classicOverlayForTest()!!;panel.select(panel.rowKeys().first{it.endsWith("|main")})}
        waitFor{activity.snapshotForUiTest().optBoolean("firstFrame")&&activity.findViewById<View>(R.id.fm_player_next).isEnabled}
        val engine=snap().getInt("engineIdentity");val old=snap().getString("playingTitle")
        click(R.id.fm_player_next);waitFor{activity.classicOverlayForTest()?.showing==true};ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitFor{activity.snapshotForUiTest().optBoolean("isPlaying")};assertEquals(old,snap().getString("playingTitle"));assertEquals(engine,snap().getInt("engineIdentity"))
        fake.failNext=true;click(R.id.fm_player_next);waitFor{activity.classicOverlayForTest()?.showing==true}
        main{val panel=activity.classicOverlayForTest()!!;panel.select(panel.rowKeys().first{it.endsWith("|main")})}
        waitFor{activity.snapshotForUiTest().optBoolean("isPlaying")&&activity.classicOverlayForTest()==null}
        assertEquals(old,snap().getString("playingTitle"));assertEquals(engine,snap().getInt("engineIdentity"))
        click(R.id.fm_player_exit);key("discard")
    }
    @Test fun naturalEndWaitsForNeighbourListAndKeepsEngine(){
        fake.shortEpisodes=true;fake.delayedNeighbours=true
        click(R.id.tv_nav_series);tile(ContentIdentity.key(fake.series));waitFor{activity.findViewById<View>(R.id.tv_play_action)!=null}
        startPlayback();val engine=snap().getInt("engineIdentity")
        waitFor(20000){activity.snapshotForUiTest().optString("playingTitle")=="Bölüm 2"&&activity.snapshotForUiTest().optBoolean("firstFrame")}
        assertEquals(engine,snap().getInt("engineIdentity"));click(R.id.fm_player_exit);key("discard")
    }
    @Test fun controlSymbolsAndAccessibleDescriptions(){
        movie();startPlayback()
        main{
            for(id in listOf(R.id.fm_player_previous,R.id.fm_player_rewind,androidx.media3.ui.R.id.exo_play_pause,R.id.fm_player_forward,R.id.fm_player_next,R.id.fm_player_audio,R.id.fm_player_subtitles,R.id.fm_player_quality,R.id.fm_player_sources,R.id.fm_player_settings,R.id.fm_player_exit)){
                val icon=activity.findViewById<ImageButton>(id);assertNotNull(icon.drawable);assertFalse(icon.contentDescription.isNullOrBlank());assertTrue(icon.isShown)
            }
            assertFalse(activity.findViewById<View>(R.id.fm_player_previous).isEnabled);assertFalse(activity.findViewById<View>(R.id.fm_player_next).isEnabled)
        }
        capture("controls");click(R.id.fm_player_sources);waitFor{activity.playerOverlayForTest()?.rowKeys()?.count{!it.startsWith("header:")}==2};ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);click(R.id.fm_player_exit);key("discard")
    }
    @Test fun lateSiteSourcePreservesFocusedRowAndPlayingSource(){
        fake.lateSources=true;movie();startPlayback();val before=snap();click(R.id.fm_player_sources)
        var focused="";main{val panel=activity.playerOverlayForTest()!!;focused=panel.rowKeys().first{it.endsWith("|backup")};panel.focus(focused)}
        waitFor{activity.playerOverlayForTest()?.focusedKey()==focused};fake.lateGate.complete(Unit)
        waitFor{activity.playerOverlayForTest()?.rowKeys()?.any{it.startsWith("lovefilmizle|")}==true}
        main{assertEquals(focused,activity.playerOverlayForTest()?.focusedKey())}
        assertEquals(before.getString("sourceId"),snap().getString("sourceId"));assertEquals(before.getInt("engineIdentity"),snap().getInt("engineIdentity"));capture("sources-live")
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);click(R.id.fm_player_exit);key("discard")
    }
    @Test fun dpadSeekStartsAtTenSecondsAndAcceleratesWhileHeld(){
        movie();startPlayback();ins.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
        waitFor{!activity.snapshotForUiTest().optBoolean("isPlaying")}
        val positions=mutableListOf<Long>();val clock=SystemClock.uptimeMillis()
        var bar:androidx.media3.ui.DefaultTimeBar?=null
        main{
            bar=activity.findViewById(androidx.media3.ui.R.id.exo_progress);bar!!.requestFocus()
            bar!!.addListener(object:androidx.media3.ui.TimeBar.OnScrubListener{
                override fun onScrubStart(timeBar:androidx.media3.ui.TimeBar,position:Long){positions.add(position)}
                override fun onScrubMove(timeBar:androidx.media3.ui.TimeBar,position:Long){positions.add(position)}
                override fun onScrubStop(timeBar:androidx.media3.ui.TimeBar,position:Long,canceled:Boolean){}
            })
        }
        fun down(key:Int,held:Long,repeat:Int){main{bar!!.dispatchKeyEvent(KeyEvent(clock,clock+held,KeyEvent.ACTION_DOWN,key,repeat))}}
        down(KeyEvent.KEYCODE_DPAD_RIGHT,0,0)
        var first=0L;main{first=positions.last();assertTrue(first in 10000L..12000L)}
        down(KeyEvent.KEYCODE_DPAD_RIGHT,100,1);main{assertEquals(first,positions.last())}
        down(KeyEvent.KEYCODE_DPAD_RIGHT,300,2);main{assertEquals(first+10000,positions.last())}
        down(KeyEvent.KEYCODE_DPAD_RIGHT,1500,3);main{assertEquals(first+30000,positions.last())}
        down(KeyEvent.KEYCODE_DPAD_RIGHT,3000,4);main{assertEquals(first+60000,positions.last())}
        down(KeyEvent.KEYCODE_DPAD_RIGHT,5000,5);main{assertEquals(first+120000,positions.last())}
        down(KeyEvent.KEYCODE_DPAD_RIGHT,7000,6);main{assertEquals(first+210000,positions.last())}
        down(KeyEvent.KEYCODE_DPAD_RIGHT,9000,7);main{assertEquals(first+330000,positions.last())}
        down(KeyEvent.KEYCODE_DPAD_LEFT,9300,8);main{assertEquals(first+320000,positions.last())}
        main{bar!!.dispatchKeyEvent(KeyEvent(clock,clock+9350,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_LEFT,0))}
        // Media3 commits a keyboard scrub after its existing one-second timeout.
        waitFor{activity.snapshotForUiTest().optLong("positionMs")>=first+319000}
        val before=snap().getLong("positionMs");down(KeyEvent.KEYCODE_DPAD_LEFT,9400,0)
        var target=0L;main{target=positions.last();assertTrue(before-target in 9000L..11000L)}
        main{bar!!.dispatchKeyEvent(KeyEvent(clock,clock+9450,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_LEFT,0))}
        File(ins.targetContext.getExternalFilesDir(null),"ux-seek.json").writeText(JSONObject().put("fixture",true).put("firstPreviewMs",first).put("heldStepsMs",JSONArray(listOf(10000,10000,20000,30000,60000,90000,120000))).put("repeatThrottleMs",300).put("newPressTargetMs",target).toString(2))
        click(R.id.fm_player_exit);key("discard")
    }
    @Test fun leftRightSeekDirectlyWithoutOpeningOrNavigatingController(){
        movie();startPlayback();ins.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
        waitFor{!activity.snapshotForUiTest().optBoolean("isPlaying")}
        if(snap().optBoolean("controllerVisible"))ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        val initial=snap();val engine=initial.getInt("engineIdentity");val base=initial.getLong("positionMs")
        // Hidden controls use direct seek; visible controls navigate their focused buttons.
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        waitFor{val s=activity.snapshotForUiTest();s.optLong("positionMs")>=base+9000&&!s.optBoolean("controllerVisible")}
        assertTrue(snap().getLong("positionMs")<=base+11000)
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
        waitFor{activity.snapshotForUiTest().optLong("positionMs")<=base+1000}
        assertFalse(snap().getBoolean("controllerVisible"))
        val clock=SystemClock.uptimeMillis();val heldBase=snap().getLong("positionMs")
        fun down(held:Long,repeat:Int){main{activity.dispatchKeyEvent(KeyEvent(clock,clock+held,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,repeat))}}
        down(0,0);down(1500,1);down(3000,2);down(5000,3);down(7000,4);down(9000,5)
        main{activity.dispatchKeyEvent(KeyEvent(clock,clock+9100,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT,0))}
        waitFor{activity.snapshotForUiTest().optLong("positionMs")>=heldBase+329000}
        assertFalse(snap().getBoolean("controllerVisible"));assertEquals(engine,snap().getInt("engineIdentity"))
        val position=snap().getLong("positionMs")
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        waitFor{activity.snapshotForUiTest().optBoolean("controllerVisible")}
        var focus=0;main{focus=activity.currentFocus?.id?:0}
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        main{assertNotEquals(focus,activity.currentFocus?.id?:0)}
        assertTrue(kotlin.math.abs(snap().getLong("positionMs")-position)<1000)
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        waitFor{!activity.snapshotForUiTest().optBoolean("controllerVisible")}
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
        waitFor{activity.snapshotForUiTest().optLong("positionMs")<=position-9000}
        assertFalse(snap().getBoolean("controllerVisible"))
        File(ins.targetContext.getExternalFilesDir(null),"ux-direct-seek.json").writeText(snap().put("fixture",true).put("heldTotalMs",330000).put("directSeekWithoutController",true).put("explicitDownNavigation",true).toString(2))
        // Exit still works while the controller is hidden.
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitFor{activity.snapshotForUiTest().optBoolean("exit")};key("discard")
    }
    @Test fun directSeekShowsTimeTotalRemainingAndProgressWithoutMenu(){
        movie();startPlayback();ins.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE)
        waitFor{!activity.snapshotForUiTest().optBoolean("isPlaying")}
        if(snap().optBoolean("controllerVisible"))ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        val engine=snap().getInt("engineIdentity")
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        waitFor{activity.findViewById<View>(R.id.fm_seek_feedback)?.isShown==true}
        main{
            assertFalse(activity.snapshotForUiTest().getBoolean("controllerVisible"))
            assertFalse(activity.findViewById<View>(R.id.fm_seek_feedback).hasFocus())
            assertTrue(activity.findViewById<TextView>(R.id.fm_seek_delta).text.toString().contains("+00:10"))
            val position=activity.snapshotForUiTest().getLong("positionMs")
            val target=String.format(java.util.Locale.ROOT,"%02d:%02d",position/60000,position/1000%60)
            assertTrue(activity.findViewById<TextView>(R.id.fm_seek_time).text.toString().startsWith(target+" / "))
            assertTrue(activity.findViewById<TextView>(R.id.fm_seek_remaining).text.toString().contains("kaldı"))
            assertTrue(activity.findViewById<ProgressBar>(R.id.fm_seek_progress).progress>0)
        }
        capture("seek-first")
        val clock=SystemClock.uptimeMillis()
        for((repeat,held)in listOf(0L,1500L,3000L,5000L,7000L,9000L).withIndex())main{
            activity.dispatchKeyEvent(KeyEvent(clock,clock+held,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,repeat))
        }
        main{activity.dispatchKeyEvent(KeyEvent(clock,clock+9100,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT,0))}
        waitFor{activity.snapshotForUiTest().getLong("positionMs")>=339000}
        main{assertTrue(activity.findViewById<TextView>(R.id.fm_seek_delta).text.toString().contains("+02:00"));assertTrue(activity.findViewById<TextView>(R.id.fm_seek_time).text.toString().startsWith("05:4"))}
        capture("seek-held")
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
        waitFor{activity.findViewById<TextView>(R.id.fm_seek_delta).text.toString().startsWith("Geri")}
        main{assertTrue(activity.findViewById<TextView>(R.id.fm_seek_delta).text.toString().contains("00:10"))}
        waitFor(5000){activity.findViewById<View>(R.id.fm_seek_feedback).visibility==View.GONE}
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        waitFor{activity.findViewById<View>(R.id.fm_seek_feedback).isShown}
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        waitFor{activity.snapshotForUiTest().getBoolean("controllerVisible")}
        main{assertEquals(View.GONE,activity.findViewById<View>(R.id.fm_seek_feedback).visibility)}
        assertEquals(engine,snap().getInt("engineIdentity"))
        File(ins.targetContext.getExternalFilesDir(null),"ux-seek-feedback.json").writeText(snap().put("fixture",true).put("seekHudChecked",true).toString(2))
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitFor{activity.snapshotForUiTest().getBoolean("exit")};key("discard")
    }
    @Test fun seekFeedbackHandlesHoursUnknownDurationAndStartBoundary(){
        main{
            val player=androidx.media3.ui.PlayerView(activity);val hint=TvSeekFeedback(player)
            try{
                hint.show(3800000,7200000,90000,1)
                assertEquals("1:03:20 / 2:00:00",player.findViewById<TextView>(R.id.fm_seek_time).text.toString())
                assertEquals("56:40 kaldı",player.findViewById<TextView>(R.id.fm_seek_remaining).text.toString())
                hint.show(5000,androidx.media3.common.C.TIME_UNSET,-10000,-1)
                assertEquals("00:05 / --:--",player.findViewById<TextView>(R.id.fm_seek_time).text.toString())
                assertEquals(View.GONE,player.findViewById<View>(R.id.fm_seek_progress).visibility)
                hint.show(0,100000,0,-1)
                assertEquals("Geri · −00:00",player.findViewById<TextView>(R.id.fm_seek_delta).text.toString())
                hint.show(200000,100000,10000,1)
                assertEquals("01:40 / 01:40",player.findViewById<TextView>(R.id.fm_seek_time).text.toString())
                assertEquals(10000,player.findViewById<ProgressBar>(R.id.fm_seek_progress).progress)
            }finally{hint.close()}
        }
    }
    @Test fun searchRemoteZonesAndLiveUpdatesKeepCardAndViewport(){
        fake.stressUpdates=true
        main{activity.findViewById<EditText>(R.id.tv_query).setText("Deney");activity.findViewById<EditText>(R.id.tv_query).onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH)}
        waitFor{texts(activity.window.decorView).contains("Diziler (1)")}
        click(R.id.classic_sort);main{activity.siteDialogForTest()!!.choose(ClassicSort.RATING.ordinal)};ins.waitForIdleSync()
        fun remote(key:Int){ins.sendKeyDownUpSync(key);ins.waitForIdleSync()}
        remote(KeyEvent.KEYCODE_DPAD_DOWN);main{assertEquals(R.id.tv_nav_home,activity.currentFocus?.id)}
        remote(KeyEvent.KEYCODE_DPAD_DOWN);main{assertEquals("search-type:ALL",activity.currentFocus?.tag)}
        for(type in listOf("MOVIES","SERIES","COLLECTIONS")){remote(KeyEvent.KEYCODE_DPAD_RIGHT);main{assertEquals("search-type:$type",activity.currentFocus?.tag)}}
        remote(KeyEvent.KEYCODE_DPAD_RIGHT);main{assertEquals(R.id.classic_filter,activity.currentFocus?.id)}
        remote(KeyEvent.KEYCODE_DPAD_RIGHT);main{assertEquals(R.id.classic_sort,activity.currentFocus?.id)}
        remote(KeyEvent.KEYCODE_DPAD_UP);main{assertEquals("catalog:responses",activity.currentFocus?.tag)}
        remote(KeyEvent.KEYCODE_DPAD_UP);main{assertEquals(R.id.tv_site_picker,activity.currentFocus?.id)}
        remote(KeyEvent.KEYCODE_DPAD_UP);main{assertEquals(R.id.tv_nav_about,activity.currentFocus?.id)}
        remote(KeyEvent.KEYCODE_DPAD_LEFT);main{assertEquals(R.id.classic_search_recent,activity.currentFocus?.id)}
        remote(KeyEvent.KEYCODE_DPAD_LEFT);remote(KeyEvent.KEYCODE_DPAD_LEFT);main{assertEquals(R.id.tv_search_submit,activity.currentFocus?.id)}
        remote(KeyEvent.KEYCODE_DPAD_LEFT);remote(KeyEvent.KEYCODE_DPAD_DOWN);remote(KeyEvent.KEYCODE_DPAD_DOWN)
        remote(KeyEvent.KEYCODE_DPAD_DOWN);remote(KeyEvent.KEYCODE_DPAD_DOWN);remote(KeyEvent.KEYCODE_DPAD_DOWN)
        var selected="";var top=0
        main{val focus=activity.currentFocus!!;selected=focus.tag.toString();val xy=IntArray(2);focus.getLocationOnScreen(xy);top=xy[1]}
        fake.updateGate.complete(Unit)
        val until=SystemClock.elapsedRealtime()+8000
        while(!fake.updatesDone&&SystemClock.elapsedRealtime()<until){SystemClock.sleep(50);main{val focus=activity.currentFocus!!;assertEquals(selected,focus.tag.toString());val xy=IntArray(2);focus.getLocationOnScreen(xy);assertTrue("Focused card moved ${xy[1]-top}px",kotlin.math.abs(xy[1]-top)<=2)}}
        assertTrue(fake.updatesDone);capture("search-stable")
        repeat(2){remote(KeyEvent.KEYCODE_DPAD_UP)};remote(KeyEvent.KEYCODE_DPAD_UP)
        main{assertEquals("search-type:ALL",activity.currentFocus?.tag)}
        remote(KeyEvent.KEYCODE_DPAD_UP);main{assertEquals(R.id.tv_nav_home,activity.currentFocus?.id)}
        repeat(5){remote(KeyEvent.KEYCODE_DPAD_RIGHT)};main{assertEquals(R.id.tv_nav_history,activity.currentFocus?.id)}
        remote(KeyEvent.KEYCODE_DPAD_RIGHT);main{assertEquals(R.id.tv_site_picker,activity.currentFocus?.id)}
    }
    @Test fun realCollectionHasFiftyMembersAndReachablePages(){
        fake.realCollections=true;click(R.id.tv_nav_discover);click(R.id.fm_collections)
        waitFor{activity.siteDialogForTest()?.choiceCount==1}
        main{activity.siteDialogForTest()!!.choose(0)}
        waitFor{activity.window.decorView.findViewWithTag<View>("collection:0")!=null}
        main{assertFalse(texts(activity.window.decorView).contains(ins.targetContext.getString(R.string.tv_retry)))}
        main{activity.findViewById<View>(R.id.tv_site_picker).requestFocus()}
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        main{assertEquals(ins.targetContext.getString(R.string.classic_back),(activity.currentFocus as? TextView)?.text.toString())}
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        capture("collection-top50")
        fun named(value:String)=texts(activity.window.decorView).contains(value)
        fun clickText(value:String){fun find(v:View):View?{if(v is Button&&v.text.toString()==value)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null};main{checkNotNull(find(activity.window.decorView)).apply{requestFocus();performClick()}};ins.waitForIdleSync()}
        clickText(ins.targetContext.getString(R.string.tv_add));waitFor{named(ins.targetContext.getString(R.string.tv_remove))}
        main{assertEquals(ins.targetContext.getString(R.string.tv_remove),(activity.currentFocus as? TextView)?.text.toString())}
        clickText(ins.targetContext.getString(R.string.tv_more));waitFor{activity.window.decorView.findViewWithTag<View>("collection:24")!=null}
        clickText(ins.targetContext.getString(R.string.tv_more));waitFor{activity.window.decorView.findViewWithTag<View>("collection:48")!=null}
        clickText(ins.targetContext.getString(R.string.tv_previous));waitFor{activity.window.decorView.findViewWithTag<View>("collection:24")!=null}
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        main{assertNotNull(activity.currentFocus);assertTrue(activity.currentFocus!!.isShown)}
    }
    private inner class Fake:TvData,AutoCloseable {
        val movie=Card(ContentRef("filmcibaba","https://filmizle.ink/ux-movie/"),"Film Merkezi deneyi",year=2026,language="Dublaj & Altyazılı")
        val series=Card(ContentRef("filmcibaba","https://filmizle.ink/ux-series/",ContentKind.SERIES),"Dizi deneyi",year=2026)
        private val other=movie.copy(ref=ContentRef("lovefilmizle","https://lovefilmizle.net/ux-movie/"))
        private val libName="ux-ui-library.db";private val seriesName="ux-ui-series.db"
        private val lib:LibraryStore;private val tracking:SeriesTrackingStore;private val ledger:PlaybackLedger
        var resolves=0;var searchCalls=0;var failNext=false;var failFinish=false;var shortEpisodes=false;var delayedNeighbours=false;var stressUpdates=false;var updatesDone=false;val updateGate=CompletableDeferred<Unit>();var realCollections=false;var lateSources=false;val lateGate=CompletableDeferred<Unit>()
        private val base="http://10.0.2.2:8768/case/ux-"+SystemClock.elapsedRealtime()
        init{ins.targetContext.deleteDatabase(libName);ins.targetContext.deleteDatabase(seriesName);lib=LibraryStore(ins.targetContext,libName);tracking=SeriesTrackingStore(ins.targetContext,seriesName);ledger=PlaybackLedger(lib,tracking,parent={series})}
        override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(ContentIdentity.merge(listOf(if(kind==ContentKind.SERIES)series else movie)),emptyMap(),emptyList()))
        override fun search(query:String,cursors:Map<String,PageCursor>)=flow{searchCalls++
            if(cursors.isNotEmpty()){val c=movie.copy(ref=movie.ref.copy(pageUrl="https://filmizle.ink/ux-collection/",kind=ContentKind.COLLECTION),title="Deney koleksiyonu");emit(UnifiedPage(ContentIdentity.merge(listOf(c)),emptyMap(),emptyList()));return@flow}
            val cards=(1..125).map{n->movie.copy(ref=movie.ref.copy(pageUrl="https://filmizle.ink/search-$n/"),title="Deney filmi $n",imdbId="tt${1000000+n}")}
            val next=mapOf("filmcibaba" to PageCursor("filmcibaba","https://filmizle.ink/page/2/"))
            emit(UnifiedPage(ContentIdentity.merge(cards),next,emptyList()));delay(350)
            val initial=ContentIdentity.merge(cards+series);emit(UnifiedPage(initial,next,emptyList()))
            if(stressUpdates){updateGate.await();repeat(8){round->delay(100);emit(UnifiedPage(initial.reversed().mapIndexed{i,item->item.copy(card=item.card.copy(imdb=6.0+(i+round)%10/10.0))},next,emptyList()))};updatesDone=true}
        }
        override fun sourcePool(card:Card,initial:List<SourceOption>,crossSite:Boolean,gate:suspend()->Unit)=flow{
            val options=initial.map{it.scoped()};emit(SourcePool(options,lateSources&&crossSite))
            if(lateSources&&crossSite){lateGate.await();emit(SourcePool(options+SourceOption("other",other.ref,"Diğer site kaynağı","https://hotstream.club/fixture").scoped(),false))}
        }
        override suspend fun work(card:Card)=UnifiedItem(ContentIdentity.key(card),card,if(card.ref.kind==ContentKind.MOVIE)listOf(card,other)else listOf(card))
        override suspend fun collections()=if(realCollections)listOf(Card(ContentRef("filmcibaba","https://filmizle.ink/haber/imdb-top-50-filmi-sinema-dunyasinin-zirvesine-yolculuk/",ContentKind.COLLECTION),"IMDB Top 50"))else emptyList()
        override suspend fun detail(card:Card):Detail{delay(40);if(card.ref.kind==ContentKind.COLLECTION&&realCollections)return CatalogParser(Sites.get("filmcibaba")).detail(ins.context.assets.open("collections/imdb-top50.html").bufferedReader().use{it.readText()},card);return Detail(card,"Yerel deneme içeriği. Gerçek site oynatımı değildir.",episodes=if(card.ref.kind==ContentKind.SERIES)episodes(card)else emptyList(),options=if(card.ref.kind==ContentKind.SERIES)emptyList()else listOf(SourceOption("main",card.ref,"Ana kaynak","https://hotstream.club/fixture"),SourceOption("backup",card.ref,"Yedek kaynak","https://hotstream.club/fixture")))}
        override suspend fun episodes(card:Card):List<Episode>{if(delayedNeighbours)delay(5000);return listOf(1,2).map{n->val number=EpisodeNumber(1,n);val c=series.copy(ref=series.ref.copy(pageUrl="https://filmizle.ink/episode-$n/",kind=ContentKind.EPISODE),title="Bölüm $n",seriesIdentity=ContentIdentity.key(series),seriesRef=series.ref,episodeNumber=number);Episode(c,series.ref,number)}}
        override suspend fun resolve(option:SourceOption):ResolvedSource{resolves++;delay(50);if(failNext&&option.content.pageUrl.contains("episode-2"))error("Controlled episode resolve failure");if(shortEpisodes&&option.content.kind==ContentKind.EPISODE)return ResolvedSource(option.id,option.label,base+"/clip.mp4",StreamType.MP4);return ResolvedSource(option.id,option.label,base+"-"+option.id.substringAfterLast('|')+"/master.m3u8?mode=selections",StreamType.HLS,"https://fixture.example.test/","FM-UX",subtitles=listOf(Subtitle(base+"/tr.vtt","Türkçe","tr"),Subtitle(base+"/en.srt","English","en","srt")))}
        override suspend fun records()=lib.records()
        override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?)=lib.update(card,listed,position,duration)
        override suspend fun beginPlayback(card:Card)=ledger.begin(card)
        override suspend fun abandonPlayback(cp:PlaybackCheckpoint)=ledger.abandon(cp)
        override suspend fun savePlayback(cp:PlaybackCheckpoint,position:Long,duration:Long)=ledger.save(cp,position,duration)
        override suspend fun finishPlayback(cp:PlaybackCheckpoint,mode:PlaybackFinish,position:Long,duration:Long){if(failFinish){failFinish=false;error("Controlled storage write failure")};ledger.finish(cp,mode,position,duration)}
        override suspend fun cancelPlaybackFinish(cp:PlaybackCheckpoint)=ledger.cancelFinish(cp)
        override suspend fun remember(query:String){};override suspend fun searches()=emptyList<String>()
        override fun close(){lib.close();tracking.close();ins.targetContext.deleteDatabase(libName);ins.targetContext.deleteDatabase(seriesName)}
    }
}
