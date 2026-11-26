package app.filmmerkezi.tv.tv

import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class FixFullSearchUiTest {
    @Test fun searchShowsOneSeriesAndOpensItsSeasonsAndEpisodes()=runBlocking {
        val f=Settings110Fixture();val report=JSONObject().put("fixture",true)
        val result=AtomicReference<CatalogPage>();var searches=0
        val provider=NativeSiteProvider(Sites.get("fixfullfilmizle"),object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
            val u=request.url.toHttpUrl();val key=when{
                u.queryParameter("s")!=null->if(u.encodedPath.contains("/page/2/"))"black-search-2"else"black-search"
                u.encodedPath.contains("/page/2/")->"black-series-2"
                else->"black-series"
            }
            return WebResponse(request.url,f.ins.context.assets.open("fixfull114/$key.html").bufferedReader().use{it.readText()})
        }},Dispatchers.Default.limitedParallelism(1))
        val data=object:TvData{
            override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(emptyList(),emptyMap(),emptyList()))
            override fun search(query:String,cursors:Map<String,PageCursor>)=flow{
                searches++;val p=provider.search(query,cursors[provider.site.id]);result.set(p)
                emit(UnifiedPage(ContentIdentity.merge(p.cards),p.next?.let{mapOf(provider.site.id to it)}?:emptyMap(),emptyList()))
            }.flowOn(Dispatchers.IO)
            override suspend fun detail(card:Card)=provider.detail(card)
            override suspend fun episodes(card:Card)=provider.episodes(card)
            override suspend fun resolve(option:SourceOption):ResolvedSource=error("Playback is outside this search test")
            override suspend fun records()=emptyList<LibraryRecord>()
            override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){}
            override suspend fun remember(query:String){};override suspend fun searches()=emptyList<String>()
        }
        try{
            f.start(customData=data)
            f.main{f.activity.findViewById<EditText>(R.id.tv_query).apply{setText("Black Mirror");onEditorAction(EditorInfo.IME_ACTION_SEARCH)}}
            f.waitFor{(f.tag("search-type:SERIES")as? TextView)?.text?.contains("Diziler (1)")==true}
            f.main{assertEquals("Tümü (1)",(f.tag("search-type:ALL")as TextView).text.toString());f.tag("search-type:SERIES").performClick()}
            assertEquals(1,searches);assertEquals(1,result.get().cards.size)
            f.main{f.tag("feed:more").performClick()}
            f.waitFor{searches==2&&(f.tag("search-type:SERIES")as? TextView)?.text?.contains("Diziler (1)")==true}
            f.screenshot("fixfull-search-series.png")
            val card=result.get().cards.single();assertEquals(ContentKind.SERIES,card.ref.kind)
            f.main{f.tag(ContentIdentity.key(card)).performClick()}
            f.waitFor{f.tag("season:1")!=null&&f.tag("season:7")!=null}
            f.main{f.tag("season:1").performClick()}
            f.waitFor{f.activity.window.decorView.findViewWithTag<View>("season:1")?.isSelected==true}
            f.screenshot("fixfull-series-detail.png")
            report.put("status","PASS").put("searchCalls",searches).put("seriesCards",1).put("seasons",7).put("parentUrl",card.ref.pageUrl)
        }catch(e:Throwable){report.put("status","FAIL").put("error",e.toString());throw e}
        finally{File(f.context.getExternalFilesDir(null),"fixfull-search-ui.json").writeText(report.toString(2));f.close()}
        Unit
    }
}
