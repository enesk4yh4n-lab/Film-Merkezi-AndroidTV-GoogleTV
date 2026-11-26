package app.filmmerkezi.tv.tv

import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.media3.common.C
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Live test is opt-in; catalog UI is seeded with one card, site/extractor/media are real. */
class Youtube0100UiTest {
    @Test fun ddiziYoutubePlaysInClassicWithQualityAndSubtitlePanels()=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("youtubeLive")=="true")
        val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext
        val report=JSONObject().put("fixtureCatalog",true).put("fixtureMedia",false).put("api",android.os.Build.VERSION.SDK_INT)
        val file=File(context.getExternalFilesDir(null),"youtube0100-ui.json")
        val stores=listOf("interface-preferences","player-preferences").associateWith{context.getSharedPreferences(it,0).all.toMap()}
        context.getSharedPreferences("interface-preferences",0).edit().putString("style","classic").putBoolean("configured",true).commit()
        context.getSharedPreferences("player-preferences",0).edit().putBoolean("automaticSources",true).putInt("height",0).commit()
        var activity:TvActivity?=null
        fun main(block:()->Unit)=ins.runOnMainSync(block)
        fun snap():JSONObject{var r=JSONObject();main{r=activity!!.snapshotForUiTest()};return r}
        suspend fun waitFor(ms:Long=20000,check:()->Boolean){val end=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<end){var ok=false;main{ok=check()};if(ok)return;delay(100)};error("UI/player timeout: "+snap())}
        try{
            val network=ProviderNetwork(context);val resolver=SourceResolver(network)
            val card=Card(ContentRef("ddizi","https://www.ddizi.tel/izle/91916/uzak-sehir-68-bolum-izle-hd1.htm",ContentKind.EPISODE),"Uzak Şehir 68. Bölüm")
            val body=network.request(WebRequest(card.ref.pageUrl,"https://www.ddizi.tel/")).body
            val options=SourceMenus.parse(Sites.get("ddizi"),body,card.ref).filter{it.extractor=="youtube"}
            assertTrue("Live page must provide a YouTube option",options.isNotEmpty());report.put("youtubeOptions",options.size)
            val item=UnifiedItem(ContentIdentity.key(card),card,listOf(card))
            TvActivity.dataFactoryForTests={object:TvData{
                override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(listOf(item),emptyMap(),emptyList()))
                override fun search(query:String,cursors:Map<String,PageCursor>)=browse(ContentKind.MOVIE,cursors)
                override suspend fun detail(card:Card)=Detail(card,options=options)
                override suspend fun episodes(card:Card)=emptyList<Episode>()
                override suspend fun resolve(option:SourceOption)=resolver.resolve(option)
                override suspend fun records()=emptyList<LibraryRecord>()
                override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){}
                override suspend fun remember(query:String){}
                override suspend fun searches()=emptyList<String>()
            }}
            activity=ins.startActivitySync(Intent(context,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
            main{activity!!.findViewById<View>(R.id.tv_nav_movies).performClick()}
            waitFor{activity!!.window.decorView.findViewWithTag<View>(item.identity)!=null}
            main{activity!!.window.decorView.findViewWithTag<View>(item.identity).performClick()}
            waitFor(60000){val s=activity!!.snapshotForUiTest();s.optBoolean("firstFrame")&&s.optBoolean("isPlaying")}
            val first=snap();val identity=first.getInt("engineIdentity");val start=SystemClock.elapsedRealtime();val pos=first.getLong("positionMs")
            waitFor(40000){val s=activity!!.snapshotForUiTest();SystemClock.elapsedRealtime()-start>=20000&&s.optLong("positionMs")-pos>=19000&&s.optBoolean("isPlaying")}
            val state=snap();assertTrue(state.getInt("audioRenderedBuffers")>0);report.put("player20s",state).put("observedWallMs",SystemClock.elapsedRealtime()-start).put("positionAdvanceMs",state.getLong("positionMs")-pos)
            main{activity!!.findViewById<View>(R.id.fm_player_quality).performClick()}
            waitFor{activity!!.playerOverlayForTest()?.rowKeys()?.count{it.startsWith("track:")}?:0>1}
            val overlay=activity!!.playerOverlayForTest()!!;report.put("qualityRows",JSONArray(overlay.titles()))
            val key=overlay.rowKeys().last{it.startsWith("track:")}
            main{overlay.select(key)}
            waitFor(20000){val s=activity!!.snapshotForUiTest();s.optBoolean("firstFrame")&&s.optBoolean("isPlaying")&&s.optLong("positionMs")>state.getLong("positionMs")}
            assertEquals(identity,snap().getInt("engineIdentity"))
            main{activity!!.findViewById<View>(R.id.fm_player_subtitles).performClick()}
            waitFor{activity!!.playerOverlayForTest()?.showing==true}
            report.put("subtitleRows",JSONArray(activity!!.playerOverlayForTest()!!.titles()))
            main{activity!!.playerOverlayForTest()!!.close()}
            report.put("status","PASS").put("final",snap())
            ins.uiAutomation.takeScreenshot()?.let{bmp->File(context.getExternalFilesDir(null),"youtube0100-playing.png").outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()}
        }catch(e:Throwable){report.put("status","FAIL").put("error",e.toString().replace(Regex("https?://\\S+"),"<url>"));throw e}
        finally{
            file.writeText(report.toString(2));activity?.let{a->main{a.finish()}};TvActivity.dataFactoryForTests=null
            stores.forEach{(name,values)->val e=context.getSharedPreferences(name,0).edit().clear();values.forEach{(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v);is Float->e.putFloat(k,v)}};e.commit()}
        }
        Unit
    }
}
