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
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Explicit live test: only Diziboxum and its declared media host, with an isolated library. */
class DiziboxumLiveTest {
    @Test fun declaredSourcesAndRealMedia3Playback()=runBlocking {
        val ins=InstrumentationRegistry.getInstrumentation();val c=ins.targetContext
        val settings=SettingsRepository.get(c);val original=settings.value
        val ui=c.getSharedPreferences("interface-preferences",0);val oldUi=ui.all.toMap()
        val network=ProviderNetwork(c);val provider=NativeSiteProvider(Sites.get("diziboxum"),network,Dispatchers.Default.limitedParallelism(1))
        val resolver=SourceResolver(network);var activity:TvActivity?=null
        val report=JSONObject().put("live",true).put("fixture",false)
        fun main(block:()->Unit)=ins.runOnMainSync(block)
        fun snap():JSONObject{var result=JSONObject();main{result=activity!!.snapshotForUiTest()};return result}
        suspend fun waitFor(timeout:Long=60000,check:()->Boolean){val end=SystemClock.elapsedRealtime()+timeout
            while(SystemClock.elapsedRealtime()<end){var ok=false;main{ok=check()};if(ok)return;delay(100)}
            error("Live wait timed out: "+snap())}
        fun screenshot(name:String){ins.waitForIdleSync();ins.uiAutomation.takeScreenshot()?.let{bmp->File(c.getExternalFilesDir(null),name).outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()}}
        try{
            val found=provider.search("Tulsa King");assertTrue(found.cards.any{it.title=="Tulsa King"})
            report.put("searchResults",found.cards.size)
            val series=provider.detail(found.cards.first{it.title=="Tulsa King"})
            val first=series.episodes.single{it.number==EpisodeNumber(1,2)}.card
            report.put("episodeCount",series.episodes.size).put("seasons",JSONArray(series.seasons))
            val sampleUrls=listOf(first.ref.pageUrl,
                "https://diziboxum.com/tulsa-king-4-sezon-4-bolum-c1qso-83875/",
                "https://diziboxum.com/furia-2-sezon-5-bolum/",
                "https://diziboxum.com/the-black-dagger-brotherhood-1-sezon-1-bolum/")
            val samples=JSONArray();var firstDetail:Detail?=null
            for(url in sampleUrls){
                val detail=provider.detail(if(url==first.ref.pageUrl)first else Card(ContentRef("diziboxum",url,ContentKind.EPISODE),""))
                if(firstDetail==null)firstDetail=detail
                val sources=JSONArray()
                for(option in detail.options){
                    val row=JSONObject().put("label",option.label).put("state",option.state.name)
                    if(option.state==OptionState.AVAILABLE){val source=resolver.resolve(option)
                        val manifest=network.request(WebRequest(source.url,source.referer))
                        assertTrue(manifest.body.startsWith("#EXTM3U"));assertEquals(200,manifest.code)
                        row.put("type",source.type.name).put("http",manifest.code).put("referer",source.referer)
                            .put("subtitles",JSONArray(source.subtitles.map{it.language})).put("master",manifest.body)
                    }
                    sources.put(row)
                }
                assertTrue(detail.options.isNotEmpty());samples.put(JSONObject().put("page",url).put("sources",sources))
            }
            report.put("samples",samples)
            settings.update{AppSettings(entry="RESTART",crossSiteSources=false)}
            ui.edit().putString("style","classic").putBoolean("configured",true).commit()
            val detail=checkNotNull(firstDetail);val item=UnifiedItem(ContentIdentity.key(first),first,listOf(first))
            TvActivity.dataFactoryForTests={object:TvData{
                override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(if(kind==ContentKind.SERIES)listOf(item)else emptyList(),emptyMap(),emptyList()))
                override fun search(query:String,cursors:Map<String,PageCursor>)=browse(ContentKind.SERIES,cursors)
                override suspend fun detail(card:Card)=if(card.ref.id==first.ref.id)detail else provider.detail(card)
                override suspend fun episodes(card:Card)=series.episodes
                override fun episodeSourcePool(item:UnifiedItem,crossSite:Boolean,gate:suspend()->Unit)=flowOf(SourcePool(detail.options.map{it.scoped()},false))
                override suspend fun resolve(option:SourceOption)=resolver.resolve(option)
                override suspend fun records()=emptyList<LibraryRecord>()
                override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){}
                override suspend fun remember(query:String){};override suspend fun searches()=emptyList<String>()
            }}
            ins.setInTouchMode(false)
            activity=ins.startActivitySync(Intent(c,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
            main{activity!!.findViewById<View>(R.id.tv_nav_series).performClick()}
            waitFor{activity!!.window.decorView.findViewWithTag<View>(item.identity)!=null}
            main{activity!!.window.decorView.findViewWithTag<View>(item.identity).performClick()}
            waitFor{val s=activity!!.snapshotForUiTest();s.optBoolean("firstFrame")&&s.optBoolean("isPlaying")}
            val start=snap();report.put("start",start)
            waitFor{activity!!.snapshotForUiTest().optLong("positionMs")>=start.optLong("positionMs")+20000}
            val played=snap();report.put("after20seconds",played)
            assertTrue(played.optLong("renderedBuffers")>start.optLong("renderedBuffers"))
            assertTrue(played.optLong("audioRenderedBuffers")>start.optLong("audioRenderedBuffers"))
            assertEquals(start.optInt("engineIdentity"),played.optInt("engineIdentity"));assertTrue(played.isNull("error")||played.optString("error").isBlank())
            val types=played.getJSONArray("tracks");report.put("tracks",types)
            main{activity!!.findViewById<View>(R.id.fm_player_subtitles).performClick()}
            waitFor{activity!!.playerOverlayForTest()?.titles()?.let{rows->rows.any{it.contains("İngilizce")}&&rows.any{it.contains("Türkçe")}}==true}
            main{val panel=activity!!.playerOverlayForTest()!!;report.put("subtitleMenu",JSONArray(panel.titles()))
                val english=panel.titles().indexOfFirst{it.contains("İngilizce")};assertTrue(english>=0);panel.select(panel.rowKeys()[english])}
            waitFor{val rows=activity!!.snapshotForUiTest().getJSONArray("tracks");(0 until rows.length()).any{rows.getJSONObject(it).let{r->r.optInt("type")==3&&r.optString("language")=="en"&&r.optBoolean("selected")}}}
            report.put("afterSubtitleSelection",snap())
            main{activity!!.findViewById<View>(R.id.fm_player_quality).performClick()}
            waitFor{activity!!.playerOverlayForTest()?.rowKeys()?.any{it.startsWith("track:")}==true}
            main{report.put("qualityMenu",JSONArray(activity!!.playerOverlayForTest()!!.titles()))}
            screenshot("diziboxum-quality.png")
            main{val panel=activity!!.playerOverlayForTest()!!;panel.select(panel.rowKeys().first{it.startsWith("track:")})}
            waitFor{!activity!!.snapshotForUiTest().optBoolean("automaticQuality")}
            main{activity!!.findViewById<View>(R.id.fm_player_audio).performClick()}
            waitFor{activity!!.playerOverlayForTest()?.rowKeys()?.count{it.startsWith("track:")}==2}
            main{val panel=activity!!.playerOverlayForTest()!!;report.put("audioMenu",JSONArray(panel.titles()));panel.select(panel.rowKeys().last{it.startsWith("track:")})}
            delay(2000)
            report.put("afterAudioSelection",snap());assertEquals(start.optInt("engineIdentity"),snap().optInt("engineIdentity"))
            screenshot("diziboxum-playing.png");report.put("status","PASS")
        }catch(e:Throwable){report.put("status","FAIL").put("error",e.toString());if(activity!=null){report.put("snapshot",snap());screenshot("diziboxum-failure.png")};throw e}
        finally{
            File(c.getExternalFilesDir(null),"diziboxum-live.json").writeText(report.toString(2))
            activity?.let{main{it.finish()}};TvActivity.dataFactoryForTests=null;settings.update{original}
            val editor=ui.edit().clear();oldUi.forEach{(k,v)->when(v){is String->editor.putString(k,v);is Boolean->editor.putBoolean(k,v)}};editor.commit()
            network.client.dispatcher.executorService.shutdown();network.client.connectionPool.evictAll()
        }
        Unit
    }
}
