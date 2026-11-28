package app.filmmerkezi.tv.tv

import android.content.Intent
import android.os.SystemClock
import android.view.*
import android.widget.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClassicUiTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity:TvActivity
    private lateinit var fake:Fake
    private var original=emptyMap<String,Any?>()
    private var catalogOriginal=emptyMap<String,Any?>()
    private fun main(block:()->Unit)=ins.runOnMainSync(block)
    private fun texts(v:View):List<String> = buildList{if(v is TextView)add(v.text.toString());if(v is ViewGroup)for(i in 0 until v.childCount)addAll(texts(v.getChildAt(i)))}
    private fun waitFor(test:()->Boolean){val until=SystemClock.elapsedRealtime()+10000
        while(SystemClock.elapsedRealtime()<until){var yes=false;main{yes=test()};if(yes)return;SystemClock.sleep(50)};error("Classic UI timeout")}
    private fun click(id:Int){main{checkNotNull(activity.findViewById<View>(id)).performClick()};ins.waitForIdleSync()}
    private fun tile(key:String){waitFor{activity.window.decorView.findViewWithTag<View>(key)!=null};main{activity.window.decorView.findViewWithTag<View>(key).performClick()};ins.waitForIdleSync()}
    private fun start(){activity=ins.startActivitySync(Intent(ins.targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity;ins.waitForIdleSync()}
    private fun capture(name:String){ins.waitForIdleSync();SystemClock.sleep(400);ins.waitForIdleSync();val image=checkNotNull(ins.uiAutomation.takeScreenshot())
        java.io.File(ins.targetContext.getExternalFilesDir(null),"classic-"+name+".png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
    @Before fun prepare(){
        val store=ins.targetContext.getSharedPreferences("interface-preferences",0);original=store.all.toMap()
        store.edit().clear().putString("style","classic").putBoolean("configured",true).commit()
        val catalog=ins.targetContext.getSharedPreferences("catalog-preferences",0);catalogOriginal=catalog.all.toMap();catalog.edit().clear().commit()
        fake=Fake();TvActivity.dataFactoryForTests={fake};ins.setInTouchMode(false)
    }
    @After fun finish(){
        if(::activity.isInitialized)main{activity.finish()};TvActivity.dataFactoryForTests=null;TvActivity.forceSetupForTests=false
        val e=ins.targetContext.getSharedPreferences("interface-preferences",0).edit().clear()
        original.forEach{(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v)}};e.commit()
        val catalog=ins.targetContext.getSharedPreferences("catalog-preferences",0).edit().clear()
        catalogOriginal.forEach{(k,v)->when(v){is String->catalog.putString(k,v);is Boolean->catalog.putBoolean(k,v);is Int->catalog.putInt(k,v)}};catalog.commit()
    }
    @Test fun firstRunChoicePersistsClassicAndShowsRealBrandVersion(){
        ins.targetContext.getSharedPreferences("interface-preferences",0).edit().clear().putString("configured","bad type").commit()
        TvActivity.forceSetupForTests=true;start()
        main{
            assertTrue(texts(activity.window.decorView).contains("Türkçe"))
            assertTrue(texts(activity.window.decorView).contains("English"))
            assertNotNull(activity.currentFocus)
        }
        for(key in listOf(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_CENTER))ins.sendKeyDownUpSync(key)
        waitFor{UiPreferences(ins.targetContext).step==1}
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME)
        ins.targetContext.startActivity(Intent(ins.targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        waitFor{activity.hasWindowFocus()&&UiPreferences(ins.targetContext).step==1}
        assertFalse(UiPreferences(ins.targetContext).configured)
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor{UiPreferences(ins.targetContext).step==2}
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor{activity.setupConfirmationForTest()?.isShowing==true}
        main{activity.setupConfirmationForTest()!!.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()}
        waitFor{UiPreferences(ins.targetContext).step==3}
        for(key in listOf(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_CENTER))ins.sendKeyDownUpSync(key)
        waitFor{activity.findViewById<View>(R.id.classic_setup_start)!=null}
        click(R.id.classic_setup_start);waitFor{activity.findViewById<View>(R.id.tv_query)!=null}
        main{val all=texts(activity.window.decorView);assertTrue(all.contains("Film Merkezi"));assertTrue(all.contains("SİNEMA EVİNİZDE"))
            val version=ins.targetContext.packageManager.getPackageInfo(ins.targetContext.packageName,0).versionName
            assertTrue(all.contains("v"+version));assertTrue(UiPreferences(ins.targetContext).configured)
            assertEquals(2, fake.movie.alternatives.size)}
    }
    @Test fun nativeClassicNavigationFiltersMovieAndSeriesDetail(){
        start();waitFor{activity.findViewById<View>(R.id.tv_hero_action)!=null}
        main{assertTrue(texts(activity.window.decorView).contains("BUGÜNÜN VİTRİNİ"));assertNotNull(activity.findViewById<View>(R.id.tv_site_picker))}
        capture("fixture-home")
        for(key in listOf(20,20,20,20,19,19,22,19)){ins.sendKeyDownUpSync(key);main{assertNotNull(activity.currentFocus);assertTrue(activity.currentFocus!!.isShown)}}
        click(R.id.tv_nav_discover);waitFor{activity.findViewById<View>(R.id.classic_filter)!=null}
        click(R.id.classic_filter);main{assertTrue(activity.siteDialogForTest()?.isShowing==true);activity.siteDialogForTest()?.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.performClick()};ins.waitForIdleSync()
        click(R.id.classic_sort);main{val d=checkNotNull(activity.siteDialogForTest());d.choose(ClassicSort.TITLE.ordinal)};ins.waitForIdleSync()
        capture("fixture-discover")
        tile(fake.movie.identity);waitFor{activity.findViewById<View>(R.id.tv_play_action)!=null}
        main{val all=texts(activity.window.decorView);assertTrue(all.contains("▶ İzle"));assertTrue(all.contains("Siteler (2)"));assertTrue(all.contains("Devamını oku"))}
        capture("fixture-movie")
        click(R.id.tv_list_action);waitFor{fake.saved.any{it.listed}}
        click(R.id.tv_nav_library);waitFor{activity.window.decorView.findViewWithTag<View>(fake.movie.identity)!=null}
        click(R.id.tv_nav_history);main{assertTrue(texts(activity.window.decorView).contains("Geçmiş"))}
        click(R.id.tv_nav_series);tile(fake.series.identity);waitFor{activity.supportFragmentManager.findFragmentById(R.id.tv_content)is ClassicEpisodeGridFragment}
        waitFor{texts(activity.window.decorView).contains("0/8 bölüm izlendi")}
        capture("fixture-series")
        val key=fake.episode(1).card.ref.id
        tile(key+":options");main{assertTrue(activity.siteDialogForTest()?.isShowing==true);activity.siteDialogForTest()?.dismiss()}
        click(R.id.tv_site_picker);main{assertEquals(Sites.all.size+1,activity.classicOverlayForTest()!!.rowKeys().size);activity.classicOverlayForTest()?.close()}
    }
    @Test fun formsHistoryOptionsAndExitUseClassicWindows(){
        start();click(R.id.tv_nav_discover);click(R.id.classic_filter)
        fun fields(v:View):List<ClassicChoice> = buildList{if(v is ClassicChoice)add(v);if(v is ViewGroup)for(i in 0 until v.childCount)addAll(fields(v.getChildAt(i)))}
        val form=checkNotNull(activity.siteDialogForTest())
        main{assertNotNull(form.window!!.decorView.findViewWithTag<View>("classic-dialog"))}
        val selectors=fields(form.window!!.decorView);assertEquals(4,selectors.size)
        capture("classic-filter-form")
        for(field in selectors){
            main{field.getChildAt(0).requestFocus();field.getChildAt(0).performClick()};ins.waitForIdleSync()
            main{val popup=checkNotNull(field.popupForTest());assertTrue(popup.isShowing);assertNotNull(popup.window!!.decorView.findViewWithTag<View>("classic-dialog"));assertTrue(popup.choiceCount>0)}
            ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);ins.waitForIdleSync()
            waitFor{form.currentFocus===field.getChildAt(0)}
        }
        main{form.dismiss()};click(R.id.classic_search_recent);waitFor{activity.siteDialogForTest()?.choiceCount==1}
        main{assertNotNull(activity.siteDialogForTest()!!.window!!.decorView.findViewWithTag<View>("classic-dialog"));activity.siteDialogForTest()!!.dismiss()}
        click(R.id.tv_nav_about);main{assertEquals(4,activity.siteDialogForTest()!!.choiceCount);activity.siteDialogForTest()!!.dismiss()}
        click(R.id.tv_nav_home);main{activity.onBackPressed()};ins.waitForIdleSync()
        main{assertNotNull(activity.siteDialogForTest()!!.window!!.decorView.findViewWithTag<View>("classic-dialog"));activity.siteDialogForTest()!!.dismiss()}
    }
    @Test fun longClassicMenuRecyclesRowsAndKeepsCheckedStateSeparateFromFocus(){
        start();lateinit var popup:ClassicDialog
        main{popup=ClassicDialog.Builder(activity).setTitle("Film Merkezi Klasik").setMultiChoiceItems(Array(200){"Seçenek $it"},BooleanArray(200)){_,_,_->}.setPositiveButton(R.string.tv_back,null).show()}
        ins.waitForIdleSync()
        main{popup.choose(0);assertTrue(popup.isChoiceChecked(0))}
        repeat(30){ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)};ins.waitForIdleSync()
        main{assertNotNull(popup.currentFocus);assertTrue(popup.isChoiceChecked(0));assertFalse(popup.isChoiceChecked(30));assertEquals(200,popup.choiceCount);popup.dismiss()}
    }
    private class Fake:TvData {
        private val card=Card(ContentRef("filmcibaba","https://filmizle.ink/classic-film/"),"Charlie Harper",year=2026,imdb=6.6,language="Dublaj & Altyazılı")
        val movie=UnifiedItem(ContentIdentity.key(card),card,listOf(card,card.copy(ref=ContentRef("lovefilmizle","https://lovefilmizle.net/classic-film/"))))
        private val show=Card(ContentRef("dizibox","https://www.dizibox.live/diziler/visionquest/",ContentKind.SERIES),"VisionQuest",year=2026,imdb=7.8)
        val series=UnifiedItem(ContentIdentity.key(show),show,listOf(show))
        val saved=mutableListOf<LibraryRecord>()
        fun episode(n:Int):Episode {val number=EpisodeNumber(1,n);val c=Card(ContentRef("dizibox","https://www.dizibox.live/visionquest-1-sezon-$n-bolum-izle/",ContentKind.EPISODE),"VisionQuest $n",seriesIdentity=series.identity,episodeNumber=number,seriesRef=show.ref)
            return Episode(c,show.ref,number)}
        override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(listOf(if(kind==ContentKind.SERIES)series else movie),emptyMap(),emptyList(),mapOf("filmcibaba" to 1),1,1))
        override fun search(query:String,cursors:Map<String,PageCursor>)=browse(ContentKind.MOVIE,cursors)
        override suspend fun detail(card:Card)=Detail(card,"Klasik detay özeti. ".repeat(50),genres=listOf("Aksiyon","Dram"),cast=listOf("Oyuncu 1","Oyuncu 2"),episodes=if(card.ref.kind==ContentKind.SERIES)(1..8).map(::episode)else emptyList())
        override suspend fun episodes(card:Card)=(1..8).map(::episode)
        override suspend fun work(card:Card)=if(card.ref.kind==ContentKind.SERIES)series else movie
        override suspend fun resolve(option:SourceOption):ResolvedSource=error("No media in UI-only tests")
        override suspend fun records()=saved.toList()
        override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){saved.removeAll{it.identity==ContentIdentity.key(card)};saved.add(LibraryRecord(ContentIdentity.key(card),card,listed?:false,position?:0,duration?:0,0))}
        override suspend fun remember(query:String){}
        override suspend fun searches()=listOf("Charlie")
    }
}
