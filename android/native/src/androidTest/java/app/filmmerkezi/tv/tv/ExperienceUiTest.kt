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
import org.json.JSONObject

class ExperienceUiTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity:TvActivity
    private lateinit var fake:Fake
    private var prefs=emptyMap<String,Any?>()
    private fun main(action:()->Unit)=ins.runOnMainSync(action)
    private fun waitFor(condition:()->Boolean){val end=SystemClock.elapsedRealtime()+12000;while(SystemClock.elapsedRealtime()<end){var ok=false;main{ok=condition()};if(ok)return;SystemClock.sleep(50)};error("experience UI timeout")}
    private fun click(id:Int){waitFor{activity.findViewById<View>(id)!=null};main{activity.findViewById<View>(id).performClick()};ins.waitForIdleSync()}
    private fun tile(id:String){waitFor{activity.window.decorView.findViewWithTag<View>(id)!=null};main{activity.window.decorView.findViewWithTag<View>(id).performClick()};ins.waitForIdleSync()}
    private fun texts(v:View):List<String> = buildList{if(v is TextView)add(v.text.toString());if(v is android.view.ViewGroup)for(i in 0 until v.childCount)addAll(texts(v.getChildAt(i)))}
    private fun begin(){waitFor{activity.classicOverlayForTest()?.showing==true};main{activity.classicOverlayForTest()!!.select("restart")};ins.waitForIdleSync()}
    private fun exitPlayer(){main{activity.onBackPressed()};waitFor{activity.snapshotForUiTest().optBoolean("exit")};main{activity.classicOverlayForTest()!!.select("save")};waitFor{!activity.snapshotForUiTest().optBoolean("exit")}}
    private fun pick(index:Int){main{val d=checkNotNull(activity.siteDialogForTest());d.choose(index)};ins.waitForIdleSync()}
    @Before fun setup(){
        prefs=ins.targetContext.getSharedPreferences("player-preferences",0).all.toMap();ins.targetContext.getSharedPreferences("player-preferences",0).edit().clear().commit()
        fake=Fake();TvActivity.dataFactoryForTests={fake};ins.setInTouchMode(false)
        activity=ins.startActivitySync(Intent(ins.targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
    }
    @After fun close(){if(::activity.isInitialized)main{activity.finish()};TvActivity.dataFactoryForTests=null
        val e=ins.targetContext.getSharedPreferences("player-preferences",0).edit().clear();prefs.forEach{(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v)}};e.commit()}
    @Test fun discoveryTrackingRecommendationsAndCollections(){
        tile(fake.movie.identity);click(R.id.fm_find_sites)
        waitFor{activity.findViewById<Button>(R.id.fm_detail_sites)?.text=="Siteler (3)"}
        click(R.id.tv_nav_series);tile(fake.show.identity);click(R.id.fm_follow_series)
        waitFor{fake.state.following};assertEquals(2,fake.state.known.size)
        tile(fake.episodes.first().card.ref.id+":options");pick(2)
        waitFor{fake.state.marks["1:1"]==true}
        click(R.id.tv_nav_discover);click(R.id.fm_collections);pick(0)
        waitFor{texts(activity.window.decorView).contains(fake.collection.title)};tile("collection:0")
        waitFor{texts(activity.window.decorView).contains(ins.targetContext.getString(R.string.fm_next_member))}
        click(R.id.tv_nav_home)
        fun find(v:View):View?{if(v is Button&&v.text.toString()=="Ne izlesem?")return v;if(v is android.view.ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null}
        waitFor{find(activity.window.decorView)!=null};main{find(activity.window.decorView)!!.performClick()};ins.waitForIdleSync()
        main{activity.siteDialogForTest()!!.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()}
        waitFor{activity.siteDialogForTest()?.choiceCount==1};assertTrue(fake.recommended)
    }
    @Test fun automaticFirstReadyAndOtherSiteSourcesReachPlayer(){
        tile(fake.movie.identity);click(R.id.tv_play_action);begin()
        val end=SystemClock.elapsedRealtime()+30000;var result=JSONObject()
        while(SystemClock.elapsedRealtime()<end){main{result=activity.snapshotForUiTest()};if(result.optBoolean("firstFrame")&&result.optLong("positionMs")>=15000)break;SystemClock.sleep(100)}
        assertTrue(result.toString(),result.optBoolean("firstFrame"));assertTrue(result.optLong("positionMs")>=15000)
        assertTrue(result.optString("sourceId").startsWith("sinemacc|"));assertEquals(3,result.optInt("sourceCount"));assertEquals(1,result.optInt("enginesCreated"))
        click(R.id.fm_player_sources);main{assertEquals(3,activity.playerOverlayForTest()!!.rowKeys().count{!it.startsWith("header:")});activity.playerOverlayForTest()!!.close()}
        exitPlayer()
    }
    @Test fun backingOutBeforeDecisionCannotStartAnotherScreen(){
        fake.slowOnly=true;tile(fake.movie.identity);click(R.id.tv_play_action);SystemClock.sleep(100);main{activity.onBackPressed()};SystemClock.sleep(500)
        main{assertFalse(activity.snapshotForUiTest().optBoolean("isPlaying"));assertTrue(activity.findViewById<View>(R.id.tv_play_action)?.isShown==true)}
        assertFalse(fake.cancelled)
    }
    @Test fun sourceSettingsSupportAllFourCombinations(){
        fun named(v:View,label:String):View?{if(v is Button&&v.text.toString()==label)return v;if(v is android.view.ViewGroup)for(i in 0 until v.childCount)named(v.getChildAt(i),label)?.let{return it};return null}
        for(automatic in listOf(true,false))for(cross in listOf(true,false)){
            tile(fake.movie.identity)
            main{checkNotNull(named(activity.window.decorView,ins.targetContext.getString(R.string.classic_options))).performClick()};pick(1)
            main{val d=checkNotNull(activity.siteDialogForTest());for((i,wanted)in listOf(automatic,cross).withIndex())if(d.isChoiceChecked(i)!=wanted)d.choose(i);d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()}
            click(R.id.tv_play_action);begin()
            if(!automatic){waitFor{activity.classicOverlayForTest()?.rowKeys()?.any{!it.startsWith("header:")}==true};main{val panel=activity.classicOverlayForTest()!!;panel.select(panel.rowKeys().first{!it.startsWith("header:")})}}
            val until=SystemClock.elapsedRealtime()+15000;var state=JSONObject()
            while(SystemClock.elapsedRealtime()<until){main{state=activity.snapshotForUiTest()};if(state.optBoolean("firstFrame")&&state.optInt("sourceCount")==if(cross)3 else 1)break;SystemClock.sleep(100)}
            assertTrue("mode automatic=$automatic cross=$cross: $state",state.optBoolean("firstFrame")&&state.optInt("sourceCount")==if(cross)3 else 1)
            main{assertEquals(if(cross)3 else 1,activity.snapshotForUiTest().optInt("sourceCount"))};exitPlayer()
            click(R.id.tv_nav_home)
        }
    }
    private class Fake:TvData {
        private val c=Card(ContentRef("filmcibaba","https://filmizle.ink/experience/"),"Experience Film",year=2024,imdb=8.0)
        val movie=UnifiedItem(ContentIdentity.key(c),c,listOf(c,c.copy(ref=c.ref.copy(siteId="sinemacc"))))
        private val showCard=Card(ContentRef("dizibox","https://www.dizibox.live/diziler/experience/",ContentKind.SERIES),"Experience Series",year=2024)
        val show=UnifiedItem(ContentIdentity.key(showCard),showCard,listOf(showCard))
        val episodes=(1..2).map{n->val number=EpisodeNumber(1,n);Episode(Card(ContentRef("dizibox","https://www.dizibox.live/experience-1-sezon-$n-bolum-izle/",ContentKind.EPISODE),"Episode $n",seriesIdentity=show.identity,episodeNumber=number,seriesRef=showCard.ref),showCard.ref,number)}
        val collection=Card(ContentRef("filmcibaba","https://filmizle.ink/haber/experience/",ContentKind.COLLECTION),"Experience Collection")
        var state=SeriesTracking(showCard);var recommended=false;var cancelled=false;var slowOnly=false
        private fun option(site:String,id:String)=SourceOption(id,c.ref.copy(siteId=site),id,"https://host.example/$id")
        override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(listOf(if(kind==ContentKind.SERIES)show else movie),emptyMap(),emptyList()))
        override fun search(query:String,cursors:Map<String,PageCursor>)=browse(ContentKind.MOVIE,cursors)
        override suspend fun work(card:Card)=when(card.ref.kind){ContentKind.SERIES->show;ContentKind.COLLECTION->UnifiedItem(ContentIdentity.key(card),card,listOf(card));else->movie}
        override suspend fun detail(card:Card)=Detail(card,genres=listOf("Aksiyon"),episodes=if(card.ref.kind==ContentKind.SERIES)episodes else emptyList(),options=if(card.ref.kind==ContentKind.MOVIE)listOf(option("filmcibaba","slow"))else emptyList(),collectionItems=if(card.ref.kind==ContentKind.COLLECTION)listOf(CollectionEntry(1,c.title,2024),CollectionEntry(2,"Second Film",2024))else emptyList())
        override suspend fun episodes(card:Card)=episodes
        override fun discoverAlternatives(card:Card,force:Boolean)=flowOf(DiscoverySnapshot(movie.copy(alternatives=movie.alternatives+c.copy(ref=c.ref.copy(siteId="lovefilmizle"))),14,14,running=false))
        override fun sourcePool(card:Card,initial:List<SourceOption>,crossSite:Boolean,gate:suspend()->Unit)=flow{
            emit(SourcePool((initial+if(crossSite&&!slowOnly)listOf(option("sinemacc","fast"))else emptyList()).map{it.scoped()},crossSite))
            delay(100);emit(SourcePool((initial+if(crossSite&&!slowOnly)listOf(option("sinemacc","fast"),option("filmcibaba","other"))else emptyList()).map{it.scoped()},false))
        }
        override suspend fun resolve(option:SourceOption):ResolvedSource{try{delay(if(option.id.endsWith("fast"))10 else if(slowOnly)5000 else 1000);return ResolvedSource(option.id,option.label,"http://10.0.2.2:8768/case/ui-experience/master.m3u8?mode=healthy",StreamType.HLS,referer="https://fixture.example.test/",userAgent="FM-Experience-UI")}catch(e:CancellationException){cancelled=true;throw e}}
        override suspend fun records()=emptyList<LibraryRecord>()
        override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){}
        override suspend fun remember(query:String){}
        override suspend fun searches()=emptyList<String>()
        override suspend fun tracking(card:Card)=state
        override suspend fun follow(card:Card,episodes:List<Episode>,enabled:Boolean){state=state.copy(following=enabled,known=episodes.map{it.number.trackingKey()}.toSet())}
        override suspend fun markEpisodes(card:Card,numbers:List<EpisodeNumber>,watched:Boolean){state=state.copy(marks=state.marks+numbers.associate{it.trackingKey() to watched})}
        override suspend fun collections()=listOf(collection)
        override suspend fun collectionMatches(entry:CollectionEntry,search:Boolean)=listOf(movie)
        override fun recommendations(criteria:CatalogQuery,siteId:String)=flow{recommended=true;emit(RecommendationResult(listOf(movie),1,1,1,true))}
    }
}
