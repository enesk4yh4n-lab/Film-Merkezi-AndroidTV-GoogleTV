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

class Dizipal098UiTest {
    private val ins=InstrumentationRegistry.getInstrumentation();private val context=ins.targetContext
    private lateinit var activity:TvActivity
    private var prefs=emptyMap<String,Any?>()
    private fun main(block:()->Unit)=ins.runOnMainSync(block)
    private fun texts(v:View):List<String> = buildList{if(v is TextView)add(v.text.toString());if(v is ViewGroup)for(i in 0 until v.childCount)addAll(texts(v.getChildAt(i)))}
    private fun waitFor(check:()->Boolean){val end=SystemClock.elapsedRealtime()+12000;while(SystemClock.elapsedRealtime()<end){var ok=false;main{ok=check()};if(ok)return;SystemClock.sleep(70)};error("Dizipal UI timeout")}
    private fun key(code:Int){ins.sendKeyDownUpSync(code);ins.waitForIdleSync()}
    @Before fun setup(){val p=context.getSharedPreferences("interface-preferences",0);prefs=p.all.toMap();p.edit().putString("style","classic").putBoolean("configured",true).commit();ins.setInTouchMode(false)}
    @After fun finish(){if(::activity.isInitialized)main{activity.finish()};TvActivity.dataFactoryForTests=null
        val e=context.getSharedPreferences("interface-preferences",0).edit().clear();prefs.forEach{(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v)}};e.commit()}
    private fun open(){
        val card=Card(ContentRef("dizipal","https://dizipalorjinal19.com/diziler/house-of-the-dragon",ContentKind.SERIES),"House of the Dragon")
        val repo=CatalogRepository(object:WebTransport{override suspend fun request(request:WebRequest)=WebResponse(request.url,ins.context.assets.open("dizipal098/series.html").bufferedReader().use{it.readText()})})
        val item=UnifiedItem(ContentIdentity.key(card),card,listOf(card))
        TvActivity.dataFactoryForTests={object:TvData {
            override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(if(kind==ContentKind.SERIES)listOf(item)else emptyList(),emptyMap(),emptyList()))
            override fun search(query:String,cursors:Map<String,PageCursor>)=browse(ContentKind.SERIES,cursors)
            override suspend fun detail(card:Card)=repo.detail(card)
            override suspend fun episodes(card:Card)=repo.episodes(card)
            override suspend fun episodeCatalog(card:Card)=repo.episodeCatalog(card)
            override suspend fun work(card:Card)=item
            override suspend fun resolve(option:SourceOption):ResolvedSource=error("No playback in UI test")
            override suspend fun records()=emptyList<LibraryRecord>()
            override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){}
            override suspend fun remember(query:String){}
            override suspend fun searches()=emptyList<String>()
        }}
        activity=ins.startActivitySync(Intent(context,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
        main{activity.findViewById<View>(R.id.tv_nav_series).performClick()};waitFor{activity.window.decorView.findViewWithTag<View>(item.identity)!=null}
        main{activity.window.decorView.findViewWithTag<View>(item.identity).performClick()}
        waitFor{activity.findViewById<SeasonButtons>(R.id.tv_season)?.count==3&&texts(activity.window.decorView).contains("0/26 bölüm izlendi")}
    }
    @Test fun horizontalSeasonsAndEpisodesAreReachableByDpad(){
        open();main{activity.findViewById<View>(R.id.tv_season).requestFocus()}
        assertEquals("season:1",activity.currentFocus?.tag)
        key(KeyEvent.KEYCODE_DPAD_RIGHT);assertEquals("season:2",activity.currentFocus?.tag)
        key(KeyEvent.KEYCODE_DPAD_CENTER);waitFor{texts(activity.window.decorView).contains("2. Sezon 1. Bölüm")}
        key(KeyEvent.KEYCODE_DPAD_DOWN);waitFor{activity.findViewById<View>(R.id.tv_content).hasFocus()}
        key(KeyEvent.KEYCODE_DPAD_UP);waitFor{activity.currentFocus?.tag=="season:2"}
        key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);waitFor{texts(activity.window.decorView).contains("3. Sezon 1. Bölüm")}
        main{activity.findViewById<SeasonButtons>(R.id.tv_season).selectSeason(1)}
        waitFor{texts(activity.window.decorView).contains("1. Sezon 1. Bölüm")}
        val bmp=checkNotNull(ins.uiAutomation.takeScreenshot());java.io.File(context.getExternalFilesDir(null),"dizipal098-seasons.png").outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()
    }
    @Test fun lateSeasonUpdatesKeepFocusedButtonAndSelection(){
        open();main{
            val strip=activity.findViewById<SeasonButtons>(R.id.tv_season);strip.selectSeason(2)
            val button=strip.findViewWithTag<View>("season:2");button.requestFocus()
            strip.bind((1..12).toList(),2)
            assertSame(button,activity.currentFocus);assertSame(button,strip.findViewWithTag<View>("season:2"));assertEquals(2,strip.selectedSeason)
        }
        repeat(10){key(KeyEvent.KEYCODE_DPAD_RIGHT)}
        assertEquals("season:12",activity.currentFocus?.tag)
        key(KeyEvent.KEYCODE_DPAD_LEFT);assertEquals("season:11",activity.currentFocus?.tag)
    }
    @Test fun episodeRowGoldOutlineFollowsDpadAndOptions(){
        open();main{activity.findViewById<View>(R.id.tv_season).requestFocus()}
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        fun focusedRow():ViewGroup {
            var row:ViewGroup?=null
            main{val focused=activity.currentFocus;assertTrue("DPAD must focus a real action",focused is Button)
                row=focused?.parent as? LinearLayout;assertTrue("Episode row must inherit its child's focus",row?.drawableState?.contains(android.R.attr.state_focused)==true)}
            return checkNotNull(row)
        }
        fun assertGold():ViewGroup{
            // Leanback scrolls a newly focused row into view over several frames.
            // Sample after that short movement, not at mismatched frame coordinates.
            ins.waitForIdleSync();SystemClock.sleep(300);ins.waitForIdleSync()
            val row=focusedRow() // Leanback may hand focus to a recycled holder after layout.
            val bitmap=checkNotNull(ins.uiAutomation.takeScreenshot());val point=IntArray(2);main{row.getLocationOnScreen(point)}
            val color=bitmap.getPixel(point[0]+ClassicUi.u(context,1f).coerceAtLeast(1),point[1]+row.height/2)
            java.io.File(context.getExternalFilesDir(null),"episode099-focus-check.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            assertTrue("Visible yellow row outline: "+Integer.toHexString(color)+" at "+point.toList()+" size "+listOf(row.width,row.height),android.graphics.Color.red(color)>220&&android.graphics.Color.green(color)>160&&android.graphics.Color.blue(color)<150)
            bitmap.recycle()
            return row
        }
        val first=assertGold();val firstKey=first.tag
        key(KeyEvent.KEYCODE_DPAD_DOWN);val second=assertGold();assertNotEquals(firstKey,second.tag)
        main{assertFalse(first.drawableState.contains(android.R.attr.state_focused))}
        key(KeyEvent.KEYCODE_DPAD_RIGHT);main{assertTrue(activity.currentFocus?.tag?.toString()?.endsWith(":options")==true)};assertGold()
        key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_UP);assertEquals(firstKey,assertGold().tag)
        val image=checkNotNull(ins.uiAutomation.takeScreenshot());java.io.File(context.getExternalFilesDir(null),"episode099-focus.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};image.recycle()
        key(KeyEvent.KEYCODE_DPAD_UP);waitFor{activity.currentFocus?.tag=="season:1"}
        main{assertFalse(first.drawableState.contains(android.R.attr.state_focused))}
    }
}
