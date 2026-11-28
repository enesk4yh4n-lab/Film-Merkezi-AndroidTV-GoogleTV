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
import org.json.JSONObject
import java.io.File

/** Isolated metadata and local media; restores the user's settings in finally. */
internal class Settings110Fixture(private val continuous:Boolean=false,private val resume:Boolean=false) {
    val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext
    val repo=SettingsRepository.get(context);private val original=repo.value
    private val ui=context.getSharedPreferences("interface-preferences",0);private val oldUi=ui.all.toMap()
    lateinit var activity:TvActivity
    val card=Card(ContentRef("filmcibaba","https://filmizle.ink/settings110-1-sezon-1-bolum/",ContentKind.EPISODE),"Ayarlar yerel oynatma deneyi",workId="work:settings110",episodeNumber=EpisodeNumber(1,1))
    fun main(block:()->Unit){var failure:Throwable?=null;ins.runOnMainSync{try{block()}catch(e:Throwable){failure=e}};failure?.let{throw it}}
    fun snap():JSONObject{var v=JSONObject();main{v=activity.snapshotForUiTest()};return v}
    suspend fun waitFor(timeout:Long=40000,check:()->Boolean){val end=SystemClock.elapsedRealtime()+timeout
        while(SystemClock.elapsedRealtime()<end){var ok=false;main{ok=check()};if(ok)return;delay(100)}
        screenshot("settings110-timeout.png");var focus="";main{focus=activity.currentFocus?.tag.toString()};error("Settings110 timeout, focus=$focus: "+snap())}
    fun tag(value:String)=activity.window.decorView.findViewWithTag<View>(value)
    suspend fun start(settings:AppSettings=AppSettings(entry="RESTART"),customData:TvData?=null,configured:Boolean=true){
        repo.update{settings};ui.edit().putString("style","classic").putBoolean("configured",configured).commit()
        TvActivity.dataFactoryForTests={customData?:object:TvData{
            private fun option()=SourceOption(card.ref.id,card.ref,"Yerel HLS",card.ref.pageUrl)
            override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(listOf(UnifiedItem(card.workId,card,listOf(card))),emptyMap(),emptyList()))
            override fun search(query:String,cursors:Map<String,PageCursor>)=browse(ContentKind.SERIES,cursors)
            override suspend fun detail(card:Card)=Detail(card,options=listOf(option()))
            override suspend fun episodes(card:Card)=emptyList<Episode>()
            override fun episodeSourcePool(item:UnifiedItem,crossSite:Boolean,gate:suspend()->Unit)=flowOf(SourcePool(listOf(option().scoped()),false))
            override suspend fun resolve(option:SourceOption)=ResolvedSource(option.id,option.label,"http://10.0.2.2:8770/case/settings110-${SystemClock.elapsedRealtime()}/"+(if(continuous)"long.m3u8"else "master.m3u8"),StreamType.HLS,
                referer="https://fixture.example.test/",userAgent="FM-Settings110",subtitles=listOf(Subtitle("http://10.0.2.2:8770/case/settings110-captions/"+(if(continuous)"captions.vtt"else "tr.vtt"),"Türkçe","tr", "vtt")))
            override suspend fun records()=if(resume)listOf(LibraryRecord(card.workId,card,false,25000,600000,1))else emptyList()
            override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){}
            override suspend fun remember(query:String){};override suspend fun searches()=emptyList<String>()
        }}
        ins.uiAutomation.executeShellCommand("input keyevent 224").close();ins.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
        ins.setInTouchMode(false)
        activity=ins.startActivitySync(Intent(context,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
    }
    suspend fun play(){
        main{activity.findViewById<View>(R.id.tv_nav_series).performClick()};waitFor{tag(card.workId)!=null}
        main{tag(card.workId).performClick()};waitFor{activity.snapshotForUiTest().optBoolean("firstFrame")&&activity.snapshotForUiTest().optBoolean("isPlaying")}
    }
    suspend fun stop(){main{activity.onBackPressed()};waitFor{activity.classicOverlayForTest()?.showing==true}
        main{activity.classicOverlayForTest()!!.select("save")};waitFor{activity.snapshotForUiTest().isNull("playingTitle")}}
    fun screenshot(name:String){ins.waitForIdleSync();SystemClock.sleep(400);ins.uiAutomation.takeScreenshot()?.let{bmp->File(context.getExternalFilesDir(null),name).outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()}}
    suspend fun close(){if(::activity.isInitialized)main{activity.finish()};TvActivity.dataFactoryForTests=null;repo.update{original}
        val e=ui.edit().clear();oldUi.forEach{(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v)}};e.commit()}
}
