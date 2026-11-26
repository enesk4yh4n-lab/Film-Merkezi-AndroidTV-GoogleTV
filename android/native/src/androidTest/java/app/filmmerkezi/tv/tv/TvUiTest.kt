package app.filmmerkezi.tv.tv

import android.content.Intent
import android.os.SystemClock
import android.view.*
import android.widget.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

@RunWith(AndroidJUnit4::class)
class TvUiTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity:TvActivity
    private lateinit var fake:Fake
    private val checks=JSONArray()
    private fun main(block:()->Unit)=ins.runOnMainSync(block)
    private fun waitFor(timeout:Long=10000,condition:()->Boolean) {
        val until=SystemClock.elapsedRealtime()+timeout
        while(SystemClock.elapsedRealtime()<until){var yes=false;main{yes=condition()};if(yes)return;SystemClock.sleep(50)}
        fail("UI condition timed out; page="+fake.pages+"; visible="+run { var value="";main{value=texts(activity.window.decorView).take(30).joinToString("|")};value })
    }
    private fun click(id:Int){main{if(id==R.id.tv_nav_search)activity.findViewById<View>(R.id.tv_query).requestFocus()else activity.findViewById<View>(id).performClick()};ins.waitForIdleSync()
        if(id==R.id.tv_nav_about){main{activity.siteDialogForTest()!!.choose(2)};ins.waitForIdleSync()}}
    private fun texts(root:View):List<String> = buildList{
        if(root is TextView)add(root.text.toString())
        if(root is ViewGroup)for(i in 0 until root.childCount)addAll(texts(root.getChildAt(i)))
    }
    private fun visibleTag(tag:String):View?=activity.window.decorView.findViewWithTag(tag)
    private fun check(name:String,block:()->Unit) {
        try{block();checks.put(JSONObject().put("name",name).put("passed",true))}
        catch(e:Throwable){checks.put(JSONObject().put("name",name).put("passed",false).put("error",e.message));throw e}
        finally{File(ins.targetContext.getExternalFilesDir(null),"stage2-ui-"+checks.optJSONObject(0).optString("name").take(8).replace(" ","_")+".json").writeText(JSONObject().put("api",android.os.Build.VERSION.SDK_INT).put("fixture",true).put("checks",checks).toString(2))}
    }
    @Before fun create(){
        fake=Fake();TvActivity.dataFactoryForTests={fake}
        activity=ins.startActivitySync(Intent(ins.targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
        ins.waitForIdleSync()
    }
    @After fun close(){if(::activity.isInitialized)main{activity.finish()};TvActivity.dataFactoryForTests=null}
    @Test fun navigationDetailSearchSeasonsAndFocus() {
        check("Home shelves and native hero"){
            waitFor{activity.findViewById<View>(R.id.tv_hero_action)!=null&&texts(activity.window.decorView).contains("Film 0")}
            main{assertTrue(activity.supportFragmentManager.findFragmentById(R.id.tv_content)is TvRowsFragment)}
        }
        check("DPAD keeps focus through header hero and rows"){
            main{activity.findViewById<View>(R.id.tv_nav_home).requestFocus()}
            for(key in listOf(20,20,22,22,19,19,22,22,21,20,19)){ins.sendKeyDownUpSync(key);SystemClock.sleep(70);main{assertNotNull(activity.currentFocus);assertTrue(activity.currentFocus!!.isShown)}}
        }
        check("Movie grid paginates and stays bounded"){
            click(R.id.tv_nav_movies);waitFor{visibleTag("MOVIE:2025:film 0")!=null}
            repeat(4){
                main{(activity.supportFragmentManager.findFragmentById(R.id.tv_content)as TvGridFragment).restore("feed:more")}
                waitFor{visibleTag("feed:more")!=null}
                main{visibleTag("feed:more")!!.performClick()}
                waitFor{fake.pages>=it+2};SystemClock.sleep(350)
                ins.waitForIdleSync()
            }
            main{val f=activity.supportFragmentManager.findFragmentById(R.id.tv_content)as TvGridFragment;assertTrue(f.adapter!!.size()<=TvPolicy.GRID_CARDS+1)}
        }
        check("Oversized provider page keeps unseen cards without another request"){
            fake.pageSize=190
            click(R.id.tv_nav_movies);waitFor{visibleTag("MOVIE:2025:film 0")!=null}
            main{(activity.supportFragmentManager.findFragmentById(R.id.tv_content)as TvGridFragment).restore("feed:more")}
            waitFor{visibleTag("feed:more")!=null};SystemClock.sleep(350)
            main{visibleTag("feed:more")!!.performClick()}
            ins.waitForIdleSync();SystemClock.sleep(350)
            main{(activity.supportFragmentManager.findFragmentById(R.id.tv_content)as TvGridFragment).restore("MOVIE:2025:film 189")}
            waitFor{visibleTag("MOVIE:2025:film 189")!=null}
            main{assertEquals(1,fake.pages);activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)}
            fake.pageSize=40
        }
        check("Detail save and Back restore the selected movie"){
            click(R.id.tv_nav_movies);waitFor{visibleTag("MOVIE:2025:film 0")!=null}
            main{visibleTag("MOVIE:2025:film 0")!!.performClick()}
            waitFor{activity.findViewById<View>(R.id.tv_list_action)!=null}
            click(R.id.tv_list_action)
            waitFor{fake.saved.any{it.card.title=="Film 0"&&it.listed}}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            waitFor{activity.supportFragmentManager.findFragmentById(R.id.tv_content)is TvGridFragment}
            main{assertNotNull(activity.currentFocus)}
        }
        check("Search keeps query after detail Back and empty results recover"){
            click(R.id.tv_nav_search)
            main{activity.findViewById<EditText>(R.id.tv_query).setText("Işık")}
            click(R.id.tv_search_submit);waitFor{visibleTag("MOVIE:2025:aranan film")!=null}
            main{visibleTag("MOVIE:2025:aranan film")!!.performClick()}
            waitFor{activity.findViewById<View>(R.id.tv_play_action)!=null}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            waitFor{activity.findViewById<EditText>(R.id.tv_query)?.text?.toString()=="Işık"}
            main{activity.findViewById<EditText>(R.id.tv_query).setText("empty")}
            click(R.id.tv_search_submit);waitFor{texts(activity.window.decorView).contains(activity.getString(R.string.tv_no_results))}
            main{assertNotNull(activity.currentFocus)}
        }
        check("Series season selection and 24-episode window"){
            click(R.id.tv_nav_series);waitFor{visibleTag("SERIES:2025:dizi 0")!=null}
            main{visibleTag("SERIES:2025:dizi 0")!!.performClick()}
            waitFor{activity.findViewById<SeasonButtons>(R.id.tv_season)?.count==3}
            main{activity.findViewById<SeasonButtons>(R.id.tv_season).selectSeason(2)}
            waitFor{texts(activity.window.decorView).any{it.contains("2. Sezon")}}
            main{(activity.supportFragmentManager.findFragmentById(R.id.tv_content)as ClassicEpisodeGridFragment).focusContent()}
            waitFor{
                val node=visibleTag(ContentRef("filmcibaba","https://filmizle.ink/ui-2-sezon-1-bolum/",ContentKind.EPISODE).id)
                val rect=android.graphics.Rect()
                node!=null&&node.getGlobalVisibleRect(rect)&&rect.height()==node.height
            }
            main{assertNotNull(activity.findViewById<View>(R.id.tv_content))}
        }
        check("Library and About remain reachable with DPAD"){
            click(R.id.tv_nav_library);waitFor{visibleTag("MOVIE:2025:film 0")!=null}
            click(R.id.tv_nav_about);waitFor{texts(activity.window.decorView).any{it.contains("Lisanslar")}}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);main{assertNotNull(activity.currentFocus)}
            click(R.id.tv_nav_home);ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            waitFor{activity.snapshotForUiTest().optBoolean("uiDialog")}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            waitFor{activity.findViewById<View>(R.id.tv_nav_home)?.hasFocus()==true}
        }
    }
    @Test fun selectedSourcePlaysAndBackReturnsToDetail() {
        check("Leanback detail -> lazy source -> Media3 20 seconds -> Back"){
            click(R.id.tv_nav_movies);waitFor{visibleTag("MOVIE:2025:film 0")!=null}
            main{visibleTag("MOVIE:2025:film 0")!!.performClick()}
            waitFor{activity.findViewById<View>(R.id.tv_play_action)!=null}
            click(R.id.tv_play_action)
            waitFor{activity.classicOverlayForTest()?.showing==true};main{activity.classicOverlayForTest()!!.select("restart")}
            waitFor(45000){val s=activity.snapshotForUiTest();s.optBoolean("firstFrame")&&s.optLong("positionMs")>=20000}
            main{assertEquals(1,fake.resolved);activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            waitFor{activity.classicOverlayForTest()?.showing==true};main{activity.classicOverlayForTest()!!.select("save")}
            waitFor{activity.findViewById<View>(R.id.tv_play_action)?.isShown==true}
            main{assertNotNull(activity.currentFocus)}
        }
    }
    @Test fun repeatedNavigationRecyclesAndHandlesTrim() {
        fun cycle(){
            click(R.id.tv_nav_movies);waitFor{visibleTag("MOVIE:2025:film 0")!=null}
            main{visibleTag("MOVIE:2025:film 0")!!.requestFocus();visibleTag("MOVIE:2025:film 0")!!.performClick()}
            waitFor{activity.findViewById<View>(R.id.tv_play_action)!=null}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            waitFor{activity.supportFragmentManager.findFragmentById(R.id.tv_content)is TvGridFragment}
            waitFor{activity.currentFocus?.tag=="MOVIE:2025:film 0"}
        }
        fun pss():Int {val info=android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(info);return info.totalPss}
        repeat(5){cycle()};Runtime.getRuntime().gc();SystemClock.sleep(700)
        val before=pss();var peak=before
        repeat(20){cycle();peak=maxOf(peak,pss())}
        main{activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)}
        Runtime.getRuntime().gc();SystemClock.sleep(700)
        val after=pss()
        File(ins.targetContext.getExternalFilesDir(null),"stage2-ui-memory.json").writeText(JSONObject()
            .put("fixtureMetadata",true).put("api",android.os.Build.VERSION.SDK_INT).put("warmup",5).put("cycles",20)
            .put("beforePssKiB",before).put("afterPssKiB",after).put("sampledPeakPssKiB",peak).toString(2))
        assertTrue("Navigation PSS grew by more than 10 MiB: "+(after-before),after-before<=10240)
    }
    private class Fake:TvData {
        var pages=0;var resolved=0;var pageSize=40
        val saved=mutableListOf<LibraryRecord>()
        private fun card(i:Int,kind:ContentKind=ContentKind.MOVIE)=Card(ContentRef("filmcibaba","https://filmizle.ink/ui-"+kind.name.lowercase()+"-$i/",kind),
            (if(kind==ContentKind.MOVIE)"Film "else "Dizi ")+i,year=2025)
        override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flow {
            val page=cursors.values.firstOrNull()?.page?:1;delay(30)
            if(kind==ContentKind.MOVIE)pages=page
            val items=(0 until pageSize).map{card((page-1)*pageSize+it,kind)}.let(ContentIdentity::merge)
            emit(UnifiedPage(items,mapOf("filmcibaba" to PageCursor("filmcibaba","https://filmizle.ink/page/"+(page+1)+"/",page+1)),emptyList()))
        }
        override fun search(query:String,cursors:Map<String,PageCursor>)=flow {
            delay(30)
            emit(UnifiedPage(if(query=="empty")emptyList()else ContentIdentity.merge(listOf(card(999).copy(title="Aranan Film"))),emptyMap(),emptyList()))
        }
        override suspend fun detail(card:Card):Detail {
            delay(30)
            return Detail(card,"Kayıtlı arayüz testi özeti.",genres=listOf("Dram"),episodes=if(card.ref.kind==ContentKind.SERIES)episodes(card)else emptyList(),
                options=if(card.ref.kind==ContentKind.SERIES)emptyList()else listOf(SourceOption("fixture",card.ref,"Referans video","https://hotstream.club/embed/fixture")))
        }
        override suspend fun episodes(card:Card):List<Episode> =(1..3).flatMap{s->(1..50).map{n->
            val ref=ContentRef("filmcibaba","https://filmizle.ink/ui-$s-sezon-$n-bolum/",ContentKind.EPISODE)
            Episode(Card(ref,"Bölüm $n",year=2025,seriesIdentity=ContentIdentity.key(card),episodeNumber=EpisodeNumber(s,n)),card.ref,EpisodeNumber(s,n))
        }}
        override suspend fun resolve(option:SourceOption):ResolvedSource {resolved++;delay(50);return ResolvedSource("fixture","Referans video","http://10.0.2.2:8766/case/stage2-ui/master.m3u8",StreamType.HLS,"https://fixture.example.test/","FM-TV-UI")}
        override suspend fun records()=saved.toList()
        override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){
            val key=ContentIdentity.key(card);val old=saved.firstOrNull{it.identity==key};saved.removeAll{it.identity==key}
            saved.add(LibraryRecord(key,card,listed?:old?.listed?:false,position?:old?.positionMs?:0,duration?:old?.durationMs?:0,System.currentTimeMillis()))
        }
        override suspend fun remember(query:String)=Unit
        override suspend fun searches()=listOf("Işık")
    }
}
