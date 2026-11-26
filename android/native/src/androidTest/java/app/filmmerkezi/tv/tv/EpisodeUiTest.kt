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
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.*
import org.junit.Assert.*

/** Actual provider/parser -> native series screen. Only transport/storage are fixtures. */
class EpisodeUiTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity:TvActivity
    private var prefs=emptyMap<String,Any?>()
    private fun main(block:()->Unit)=ins.runOnMainSync(block)
    private fun texts(v:View):List<String> = buildList{if(v is TextView)add(v.text.toString());if(v is ViewGroup)for(i in 0 until v.childCount)addAll(texts(v.getChildAt(i)))}
    private fun waitFor(test:()->Boolean){val end=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<end){var ok=false;main{ok=test()};if(ok)return;SystemClock.sleep(60)};error("Episode UI timeout")}
    private fun click(id:Int){main{checkNotNull(activity.findViewById<View>(id)).performClick()};ins.waitForIdleSync()}
    @Before fun prepare(){val p=ins.targetContext.getSharedPreferences("interface-preferences",0);prefs=p.all.toMap();p.edit().putString("style","classic").putBoolean("configured",true).commit();ins.setInTouchMode(false)}
    @After fun finish(){if(::activity.isInitialized)main{activity.finish()};TvActivity.dataFactoryForTests=null
        val e=ins.targetContext.getSharedPreferences("interface-preferences",0).edit().clear();prefs.forEach{(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v)}};e.commit()}
    private fun open(site:String,path:String,title:String):Card{
        val card=Card(ContentRef(site,Sites.get(site).base+path,ContentKind.SERIES),title)
        TvActivity.dataFactoryForTests={Fixture(card)}
        activity=ins.startActivitySync(Intent(ins.targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
        click(R.id.tv_nav_series)
        waitFor{activity.window.decorView.findViewWithTag<View>(ContentIdentity.key(card))!=null}
        main{activity.window.decorView.findViewWithTag<View>(ContentIdentity.key(card)).performClick()}
        return card
    }
    private fun complete(count:Int,seasons:Int){waitFor{texts(activity.window.decorView).contains("0/$count bölüm izlendi")&&(activity.findViewById<SeasonButtons>(R.id.tv_season)?.count?:0)==seasons}
        waitFor{texts(activity.window.decorView).contains("1. Sezon 1. Bölüm")}
        main{assertEquals(1,activity.findViewById<SeasonButtons>(R.id.tv_season).selectedSeason)}}
    private fun capture(name:String){ins.waitForIdleSync();SystemClock.sleep(300);ins.waitForIdleSync();val bitmap=checkNotNull(ins.uiAutomation.takeScreenshot());java.io.File(ins.targetContext.getExternalFilesDir(null),"episodes-$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    @Test fun vladimirShowsEightEpisodesInsteadOfMovieDetail(){open("filmcibaba","/vladimir-i2/","Vladimir 1.Sezon");complete(8,1);capture("vladimir")}
    @Test fun ddiziLateArchiveReturnsToFirstSeasonAndFirstEpisode(){open("ddizi","/diziler/1815/dark-hd1","Dark");complete(26,3);capture("ddizi")}
    @Test fun diziboxAllFourSeasonsAreReachable(){open("dizibox","/diziler/12-monkeys-izle-1/","12 Monkeys");complete(47,4)
        main{activity.findViewById<SeasonButtons>(R.id.tv_season).selectSeason(4)}
        waitFor{texts(activity.window.decorView).contains("4. Sezon 1. Bölüm")};capture("dizibox-season4")}
    @Test fun delisiFlightDataShowsSevenSeasonsAnd151Episodes(){open("hdfilmdelisi","/dizi/the-mentalist","The Mentalist");complete(151,7)
        main{activity.findViewById<SeasonButtons>(R.id.tv_season).selectSeason(7)}
        waitFor{texts(activity.window.decorView).contains("7. Sezon 1. Bölüm")};capture("delisi-season7")}
    private inner class Fixture(val card:Card):TvData {
        val item=UnifiedItem(ContentIdentity.key(card),card,listOf(card))
        val repository=CatalogRepository(object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
            // DDizi deliberately returns its newest page before delayed older pages.
            delay(if(request.url.contains("sayfa-"))150 else 20)
            val file=when(card.ref.siteId){
                "filmcibaba"->"vladimir"
                "ddizi"->when{request.url.endsWith("sayfa-1")->"ddizi-1";request.url.endsWith("sayfa-2")->"ddizi-2";request.url==card.ref.pageUrl->"ddizi";else->error("Foreign archive")}
                "dizibox"->Regex("/([1-4])-sezon-12-monkeys/").find(request.url)?.groupValues?.get(1)?.let{"dizibox-season-$it"}?:"dizibox-multi"
                else->"delisi-multi"
            }
            return WebResponse(request.url,ins.context.assets.open("episodes/$file.html").bufferedReader().use{it.readText()})
        }})
        override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(if(kind==ContentKind.SERIES)listOf(item)else emptyList(),emptyMap(),emptyList()))
        override fun search(query:String,cursors:Map<String,PageCursor>)=browse(ContentKind.SERIES,cursors)
        override suspend fun detail(card:Card)=repository.detail(card)
        override suspend fun episodes(card:Card)=repository.episodes(card)
        override suspend fun work(card:Card)=item.copy(card=card)
        override suspend fun resolve(option:SourceOption):ResolvedSource=error("No player requests in episode UI tests")
        override suspend fun records()=emptyList<LibraryRecord>()
        override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){}
        override suspend fun remember(query:String){}
        override suspend fun searches()=emptyList<String>()
    }
}
