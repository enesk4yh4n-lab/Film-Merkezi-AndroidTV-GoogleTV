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

class SeriesCatalog097UiTest {
    private val ins=InstrumentationRegistry.getInstrumentation();private val context=ins.targetContext
    private lateinit var activity:TvActivity;private lateinit var works:WorkStore
    private var prefs=emptyMap<String,Any?>();private val dbName="series-catalog-ui097.db"
    private fun html(n:String)=ins.context.assets.open("series097/$n.html").bufferedReader().use{it.readText()}
    private fun main(block:()->Unit)=ins.runOnMainSync(block)
    private fun texts(v:View):List<String> = buildList{if(v is TextView)add(v.text.toString());if(v is ViewGroup)for(i in 0 until v.childCount)addAll(texts(v.getChildAt(i)))}
    private fun waitFor(test:()->Boolean){val end=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<end){var ok=false;main{ok=test()};if(ok)return;SystemClock.sleep(60)};error("Unified series UI timeout")}
    @Before fun setup(){val p=context.getSharedPreferences("interface-preferences",0);prefs=p.all.toMap();p.edit().putString("style","classic").putBoolean("configured",true).commit();context.deleteDatabase(dbName);works=WorkStore(context,dbName);ins.setInTouchMode(false)}
    @After fun finish(){if(::activity.isInitialized)main{activity.finish()};TvActivity.dataFactoryForTests=null;works.close();context.deleteDatabase(dbName)
        val e=context.getSharedPreferences("interface-preferences",0).edit().clear();prefs.forEach{(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v)}};e.commit()}
    @Test fun sameHouseCardAndFullSeasonsSurviveChoosingTheThirdSeasonSite()=runBlocking{scenario(false)}
    @Test fun findingOtherSitesUpdatesSeasonsWithoutClosingDetail()=runBlocking{scenario(true)}
    private suspend fun scenario(discoverLate:Boolean){
        val body=html("baba-search");val parser=CatalogParser(Sites.get("filmcibaba"))
        val seasons=parser.list(body,"https://filmizle.ink/?s=House+of+the+Dragon").cards.mapIndexed{i,c->PageIdentity.attach(html("baba-house-${i+1}"),parser.detail(html("baba-house-${i+1}"),c),c).card}
        val full=Card(ContentRef("hdfilmdelisi","https://hdfilmdelisi.one/dizi/house-of-the-dragon-izle",ContentKind.SERIES),"House of the Dragon")
        val checked=PageIdentity.attach(html("delisi-house"),CatalogParser(Sites.get(full.ref.siteId)).detail(html("delisi-house"),full),full).card
        val item=works.observe(if(discoverLate)seasons else seasons+checked).single()
        val repository=CatalogRepository(object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
            val n=when{request.url.contains("?s=")->"baba-search";request.url.contains("3-sezon-hbo")->"baba-house-1";request.url.contains("2-sezon-izlett")->"baba-house-2";request.url.contains("1-sezon-izlet")->"baba-house-3";else->"delisi-house"};return WebResponse(request.url,html(n))
        }},works)
        val fake=object:TvData{
            override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(if(kind==ContentKind.SERIES)listOf(item)else emptyList(),emptyMap(),emptyList()))
            override fun search(query:String,cursors:Map<String,PageCursor>)=browse(ContentKind.SERIES,cursors)
            override suspend fun detail(card:Card)=repository.detail(card)
            override suspend fun episodes(card:Card)=repository.episodes(card)
            override suspend fun episodeCatalog(card:Card)=repository.episodeCatalog(card)
            override suspend fun work(card:Card)=works.item(card)
            override fun discoverAlternatives(card:Card,force:Boolean)=flow{works.observe(listOf(checked));emit(DiscoverySnapshot(works.item(card),14,14,running=false))}
            override suspend fun resolve(option:SourceOption):ResolvedSource=error("No media")
            override suspend fun records()=emptyList<LibraryRecord>()
            override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){}
            override suspend fun remember(query:String){}
            override suspend fun searches()=emptyList<String>()
        }
        TvActivity.dataFactoryForTests={fake}
        activity=ins.startActivitySync(Intent(context,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
        main{activity.findViewById<View>(R.id.tv_nav_series).performClick()}
        waitFor{activity.window.decorView.findViewWithTag<View>(item.identity)!=null}
        main{activity.window.decorView.findViewWithTag<View>(item.identity).performClick()}
        fun complete(){waitFor{texts(activity.window.decorView).contains("0/26 bölüm izlendi")&&activity.findViewById<SeasonButtons>(R.id.tv_season)?.count==3}}
        if(discoverLate){waitFor{texts(activity.window.decorView).contains("0/14 bölüm izlendi")};main{activity.findViewById<View>(R.id.fm_find_sites).performClick()}}
        complete();main{activity.findViewById<SeasonButtons>(R.id.tv_season).selectSeason(3)}
        main{assertTrue(activity.findViewById<TextView>(R.id.fm_detail_progress).text.toString().contains("6 site sayfası"))}
        waitFor{texts(activity.window.decorView).contains("3. Sezon 1. Bölüm")}
        main{activity.findViewById<View>(R.id.fm_detail_sites).performClick()}
        waitFor{activity.classicOverlayForTest()?.rowKeys()?.size==4}
        main{activity.classicOverlayForTest()!!.select(seasons.first().ref.id)}
        complete();main{activity.findViewById<SeasonButtons>(R.id.tv_season).selectSeason(1)}
        waitFor{texts(activity.window.decorView).contains("1. Sezon 1. Bölüm")}
        ins.waitForIdleSync();SystemClock.sleep(300);val bmp=checkNotNull(ins.uiAutomation.takeScreenshot())
        java.io.File(context.getExternalFilesDir(null),if(discoverLate)"series097-house-discovered.png"else"series097-house-unified.png").outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()
        assertEquals(item.identity,works.item(seasons.first()).identity)
    }
}
