package app.filmmerkezi.tv.tv

import android.content.Intent
import android.graphics.Bitmap
import android.os.Debug
import android.os.SystemClock
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import com.bumptech.glide.signature.ObjectKey
import kotlinx.coroutines.flow.flowOf
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Stage4MemoryTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity:TvActivity
    private fun main(block:()->Unit)=ins.runOnMainSync(block)
    @Test fun lowMemoryEvictsCachesAndRepeatedMenusStayBounded(){
        val context=ins.targetContext;val fake=Fake();TvActivity.dataFactoryForTests={fake}
        val samples=JSONArray();val report=JSONObject().put("fixture",true).put("api",android.os.Build.VERSION.SDK_INT)
            .put("scope","debug native menu lifecycle; no network, no video; PSS sampled after GC").put("samples",samples)
        fun pss():Int {Runtime.getRuntime().gc();SystemClock.sleep(150);return Debug.MemoryInfo().also{Debug.getMemoryInfo(it)}.totalPss}
        fun cycle(){
            for(id in listOf(R.id.tv_nav_movies,R.id.tv_nav_series,R.id.tv_nav_search,R.id.tv_nav_library,R.id.tv_nav_home)){
                main{if(id==R.id.tv_nav_search)activity.findViewById<android.widget.EditText>(R.id.tv_query).apply{setText("memory");onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH)}else activity.findViewById<View>(id).performClick()};ins.waitForIdleSync()
            }
        }
        try{
            activity=ins.startActivitySync(Intent(context,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
            repeat(5){cycle()};val before=pss();samples.put(before)
            repeat(50){i->cycle();if(i%10==9)samples.put(pss())}
            val after=pss();samples.put(after)
            main{
                val memory=Posters::class.java.getDeclaredField("memory").apply{isAccessible=true}.get(Posters)as LruResourceCache
                val pool=Glide.get(context).bitmapPool
                pool.put(Bitmap.createBitmap(64,64,Bitmap.Config.RGB_565))
                memory.put(ObjectKey("stage4-trim"),checkNotNull(BitmapResource.obtain(Bitmap.createBitmap(64,64,Bitmap.Config.RGB_565),pool)))
                val seeded=Posters.memoryForTest();assertTrue(seeded[0]>0);assertTrue(seeded[1]>0)
                activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
                assertArrayEquals(longArrayOf(0,0),Posters.memoryForTest())
                assertNotNull(activity.currentFocus);assertTrue(activity.currentFocus!!.isShown)
                activity.onLowMemory();assertFalse(activity.snapshotForUiTest().optBoolean("isPlaying"))
            }
            cycle();main{assertNotNull(activity.currentFocus)}
            report.put("beforePssKiB",before).put("afterPssKiB",after).put("growthKiB",after-before)
                .put("growthBudgetKiB",10240).put("cycles",50).put("trimClearedImageCacheAndPool",true)
            assertTrue("Menu PSS growth exceeded 10 MiB: "+(after-before),after-before<=10240)
            report.put("passed",true)
        }catch(e:Throwable){report.put("passed",false).put("error",e.stackTraceToString());throw e}
        finally{
            File(context.getExternalFilesDir(null),"stage4-memory.json").writeText(report.toString(2))
            if(::activity.isInitialized)main{activity.finish()};TvActivity.dataFactoryForTests=null
        }
    }
    private class Fake:TvData {
        private fun page(kind:ContentKind)=UnifiedPage((0 until 40).map{n->
            val c=Card(ContentRef("filmcibaba","https://filmizle.ink/memory-$n/",kind),"Bellek $n",year=2025)
            UnifiedItem(ContentIdentity.key(c),c,listOf(c))
        },emptyMap(),emptyList())
        override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(page(kind))
        override fun search(query:String,cursors:Map<String,PageCursor>)=flowOf(page(ContentKind.MOVIE))
        override suspend fun detail(card:Card)=Detail(card)
        override suspend fun episodes(card:Card)=emptyList<Episode>()
        override suspend fun resolve(option:SourceOption):ResolvedSource=error("No media in memory test")
        override suspend fun records()=emptyList<LibraryRecord>()
        override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){}
        override suspend fun remember(query:String){}
        override suspend fun searches()=emptyList<String>()
    }
}
