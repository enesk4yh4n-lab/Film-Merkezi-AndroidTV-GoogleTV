package app.filmmerkezi.tv.tv

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.filmmerkezi.tv.PlaybackSession
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import com.bumptech.glide.Glide
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

/** Bounded Leanback screens; one attached playback surface across navigation. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class TvActivity:FragmentActivity() {
    companion object {
        @Volatile internal var dataFactoryForTests:((Context)->TvData)?=null
        @Volatile internal var forceSetupForTests=false
        @Volatile internal var siteCheckTransportForTests:WebTransport?=null
        private val canvasColor=ClassicUi.background
        private val muted=ClassicUi.muted
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val work=mutableListOf<Job>()
    private lateinit var services:TvServices
    private lateinit var data:TvData
    private lateinit var settings:app.filmmerkezi.tv.settings.SettingsRepository
    private var settingsScreen:SettingsScreen?=null
    private var transferPanel:DataTransferPanel?=null
    internal fun openDataTransfer(){transferPanel=DataTransferPanel(this,scope,{::playback.isInitialized&&playback.active}).also{it.open()}}
    @Deprecated("Framework file picker bridge")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:android.content.Intent?){if(transferPanel?.result(requestCode,resultCode,data)==true)return;super.onActivityResult(requestCode,resultCode,data)}
    private var playbackOverrides=app.filmmerkezi.tv.settings.PlaybackOverrides()
    private var preferenceRevision=0
    private var adultBlocked=false
    private var sleepMinutes=0
    private var sleepDeadline=0L
    private lateinit var host:FrameLayout
    private lateinit var ui:LinearLayout
    private var episodeGrid:ClassicEpisodeGridFragment?=null
    private var classicSort=ClassicSort.DEFAULT
    private var classicFilter=ClassicFilter()
    private var setupShowing=false
    private lateinit var body:LinearLayout
    private lateinit var status:TextView
    private val navigation=linkedMapOf<TvScreen,Button>()
    private var rows:TvRowsFragment?=null
    private var grid:TvGridFragment?=null
    private var current=TvNav()
    private val stack=ArrayDeque<TvNav>()
    private val focuses=mutableMapOf<TvScreen,Pair<Long,String>>()
    private var generation=0
    private var foreground=false
    private var started=false
    private var movies=UnifiedPage(emptyList(),emptyMap(),emptyList())
    private var series=movies
    private var library=emptyList<LibraryRecord>()
    private val feeds=mutableMapOf<TvScreen,UnifiedPage>()
    // At most one provider page awaits presentation; unseen cards are never dropped.
    private val pendingFeeds=mutableMapOf<TvScreen,List<UnifiedItem>>()
    private var feedBusy=false
    private var renderedCatalog=emptyList<UnifiedItem>()
    private var detailValue:Detail?=null
    private var episodeValues=emptyList<Episode>()
    private var declaredSeasons=emptyList<Int>()
    private var applySeriesCatalog:((SeriesEpisodes)->Unit)?=null
    private var episodeBusy=false
    private var seriesWatch:SeriesTracking?=null
    private var following=emptyList<SeriesTracking>()
    private var collectionContext:Pair<Detail,Int>?=null
    private var collectionPage=0
    private var heroCard:Card?=null
    private var heroImage:ImageView?=null
    private var detailImage:ImageView?=null
    private var firstContent:View?=null
    private var queryView:EditText?=null
    private var heroButton:Button?=null
    private var homeHero:View?=null
    private var homeStatus:View?=null
    private lateinit var playback:PlaybackSession
    private var playerView:PlayerView?=null
    private var playerNotice:TextView?=null
    private var playingCard:Card?=null
    private var checkpoint:PlaybackCheckpoint?=null
    private var pendingCheckpoint:PlaybackCheckpoint?=null
    private var exitOpen=false
    private var exitWasPlaying=false
    private var finishBusy=false
    private var entryBusy=false
    private var entryJob:Job?=null
    private var entryRevision=0
    private var sourceEpisode:UnifiedItem?=null
    private var returnEpisodeKey:String?=null
    private var initialSourceCard:Card?=null
    private var initialSourceResume=0L
    private var neighbours=emptyList<Episode>()
    private var neighbourJob:Job?=null
    private var dialog:ClassicDialog?=null
    private var classicOverlay:ClassicOverlay?=null
    private val searchTabs=linkedMapOf<SearchType,Button>()
    private fun overlay(title:String,side:Boolean=false,origin:View?=currentFocus,back:()->Unit={}):ClassicOverlay {
        classicOverlay?.close()
        return ClassicOverlay(this,title,side,origin,back).also{window->classicOverlay=window;window.onClosed={if(classicOverlay===window)classicOverlay=null}}
    }
    private lateinit var playerPreferences:PlayerPreferences
    private lateinit var catalogPreferences:CatalogPreferences
    private var playerControls:TvPlayerControls?=null
    private var subtitleAppearance:SubtitleAppearance?=null
    private var nextEpisodeJob:Job?=null
    private var sourcePoolJob:Job?=null
    private var sourceStartJob:Job?=null
    private var sourceDeadlineJob:Job?=null
    private var sourceRevision=0
    private val sourceState=MutableStateFlow(SourcePool(emptyList(),false))
    private val sourceOptions=linkedMapOf<String,SourceOption>()
    private val primedSources=mutableMapOf<String,ResolvedSource>()
    private fun candidate(o:SourceOption)=JSONObject().put("id",o.id).put("label",o.label).put("lazyOptionId",o.id)
    private fun cancelSources(){sourceRevision++;sourcePoolJob?.cancel();sourceStartJob?.cancel();sourceDeadlineJob?.cancel();if(::playback.isInitialized&&playback.active)playback.appendCandidates(emptyList(),false)}
    private lateinit var mediaButtons:TvMediaButtons

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        if(dataFactoryForTests==null)try{app.filmmerkezi.tv.updates.DataTransferStore(this).applyPending()?.let{Toast.makeText(this,it,Toast.LENGTH_LONG).show()}}catch(e:Exception){Toast.makeText(this,e.message,Toast.LENGTH_LONG).show()}
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        services=TvServices.get(this);data=dataFactoryForTests?.invoke(this)?:services;settings=services.settings;settings.reload()
        scope.launch{try{settings.migrate()}catch(_:Exception){Toast.makeText(this@TvActivity,R.string.settings_saved_error,Toast.LENGTH_LONG).show()}}
        playerPreferences=PlayerPreferences(this)
        catalogPreferences=CatalogPreferences(this)
        Posters.initialize(this,services)
        host=FrameLayout(this);setContentView(host)
        mediaButtons=TvMediaButtons(this,{if(!exitOpen&&!finishBusy&&!adultBlocked)playback.setPlaying(it)},{if(::playback.isInitialized&&playback.active)onBackPressed()},::changeEpisode,{settings.value.player.seekSeconds})
        playback=PlaybackSession(this,object:PlaybackSession.Listener{
            override fun onEngineChanged(player:ExoPlayer?){playerView?.player=player;subtitleAppearance?.bind(player);mediaButtons.bind(player)}
            override fun onMessage(resource:Int,label:String){playerNotice?.text=getString(resource,label)}
            override fun onEnded(){nextEpisode()}
            override fun onSourcesExhausted(message:String){playerNotice?.text=message;if(foreground)playerControls?.openSources()}
        })
        val restored=savedInstanceState?.let{state->
            val screen=runCatching{TvScreen.valueOf(state.getString("screen").orEmpty())}.getOrDefault(TvScreen.HOME)
            val card=state.getBundle("card")?.let(::readCard)
            TvNav(if(screen==TvScreen.DETAIL&&card==null)TvScreen.HOME else screen,card?.let{UnifiedItem(ContentIdentity.key(it),it,listOf(it))},
                state.getString("query").orEmpty(),state.getInt("season"),state.getInt("episodePage"),state.getBoolean("history"),state.getInt("libraryPage"),runCatching{SearchType.valueOf(state.getString("searchType").orEmpty())}.getOrDefault(SearchType.ALL),state.getInt("searchPage"))
        }?:TvNav(if(dataFactoryForTests==null)runCatching{TvScreen.valueOf(settings.value.startScreen)}.getOrDefault(TvScreen.HOME)else TvScreen.HOME)
        if(restored.screen!=TvScreen.HOME)stack.addLast(TvNav())
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        if(savedInstanceState?.getBoolean("interfaceSetup")==true||!UiPreferences(this).configured&&(dataFactoryForTests==null||forceSetupForTests))showInterfaceSetup()else render(restored)
        scope.launch{while(isActive){delay(15000);if(foreground&&playback.active)saveProgress()}}
        scope.launch{while(isActive){delay(1000);if(sleepExpired()&&playback.active&&!finishBusy&&!exitOpen)finishForSleep()}}
        if(dataFactoryForTests==null){
            val distribution=app.filmmerkezi.tv.updates.DistributionManager.get(this)
            scope.launch{while(isActive){if(foreground)distribution.foreground(playback.active);delay(60000)}}
            scope.launch{distribution.status.collect{v->
                if(v.release!=null||v.moduleUpdates>0)findViewById<Button>(R.id.tv_nav_about)?.apply{text=getString(R.string.updates_new_badge);setTextColor(ClassicUi.gold)}
            }}
        }
        scope.launch{var previous=settings.value;settings.changes.collect{v->
            if(playback.active){applyPlayerSettings()
                if(v.disabledSources!=previous.disabledSources||v.crossSiteSources!=previous.crossSiteSources||v.automaticSources!=previous.automaticSources)sourceSettingsChanged()
                if(v.hideAdult&&!previous.hideAdult)playingCard?.let{card->try{if(AdultContent.blocked(data.work(card)))blockAdultPlayback()}catch(_:Exception){}}
                if(!v.hideAdult&&adultBlocked){adultBlocked=false;classicOverlay?.close()}
            };previous=v
        }}
    }
    private fun dp(v:Int)=ClassicUi.u(this,v.toFloat())
    private fun text(value:String,size:Float=16f,color:Int=ClassicUi.foreground)=ClassicUi.text(this,value,size,color)
    private fun label(id:Int,size:Float=16f)=text(getString(id),size)
    private fun button(title:String,id:Int=View.generateViewId(),action:()->Unit)=ClassicUi.button(this,title,id,action=action)

    private fun active(token:Int)=token==generation&&!isFinishing&&!isDestroyed
    private fun launchScreen(block:suspend(Int)->Unit):Job {
        val token=generation
        return scope.launch{try{block(token);if(active(token)&&data.storageWarning)status.setText(R.string.tv_storage_damaged)}
            catch(e:CancellationException){throw e}catch(e:Exception){if(active(token)){if((e as? DataFailure)?.stage=="adult-filter"){
                status.setText(R.string.settings_filtered);grid?.show(listOf(statusTile(R.string.settings_filtered,"settings")))
            }else showError()}}}.also{work.add(it)}
    }
    internal suspend fun quiesceForDistribution(){
        check(!playback.active)
        val pending=work.toList();cancelWork();pending.joinAll()
        val sources=listOfNotNull(sourcePoolJob,sourceStartJob,sourceDeadlineJob,neighbourJob,entryJob)
        sources.forEach{it.cancel()};sources.joinAll()
    }
    private fun cancelWork(){generation++;work.forEach{it.cancel()};work.clear()}
    private fun navigate(next:TvNav,push:Boolean=false) {
        if(next.screen!=TvScreen.DETAIL)collectionContext=null
        if(push){stack.addLast(current);while(stack.size>12)stack.removeFirst()}
        else {stack.clear();if(next.screen!=TvScreen.HOME)stack.addLast(TvNav())}
        render(next)
    }
    private fun render(state:TvNav) {
        if(!playback.active)cancelSources()
        setupShowing=false;cancelWork();applySeriesCatalog=null;classicOverlay?.close();dialog?.dismiss();dialog=null;feedBusy=false;renderedCatalog=emptyList();searchTabs.clear()
        heroImage?.let{Posters.clear(it)};detailImage?.let{Posters.clear(it)};detailImage=null
        current=if(state.screen==TvScreen.HISTORY)state.copy(history=true)else state
        rows=null;grid=null;episodeGrid=null;firstContent=null;queryView=null;heroButton=null;heroCard=null;heroImage=null;homeHero=null;homeStatus=null
        supportFragmentManager.findFragmentById(R.id.tv_content)?.let{supportFragmentManager.beginTransaction().remove(it).commitNow()}
        if(::ui.isInitialized)host.removeView(ui)
        ui=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(canvasColor)}
        host.addView(ui,FrameLayout.LayoutParams(-1,-1))
        val shell=ClassicUi.shell(this,current.screen,current.query,scopeName(),
            {screen->!(screen==TvScreen.MOVIES&&!supports(ContentKind.MOVIE)||screen==TvScreen.SERIES&&!supports(ContentKind.SERIES))},
            {screen->feeds.remove(screen);pendingFeeds.remove(screen);navigate(TvNav(screen,history=screen==TvScreen.HISTORY))},
            ::submitSearch,::recentSearches,::chooseSite,::classicOptions){
                if(queryView?.hasFocus()==true)(navigation[current.screen]?:navigation[TvScreen.HOME])?.requestFocus()else focusAboveContent()
            }
        navigation.clear();navigation.putAll(shell.navigation);queryView=shell.query
        if(dataFactoryForTests==null){val update=app.filmmerkezi.tv.updates.DistributionManager.get(this).status.value;if(update.release!=null||update.moduleUpdates>0)shell.root.findViewById<Button>(R.id.tv_nav_about)?.apply{text=getString(R.string.updates_new_badge);setTextColor(ClassicUi.gold)}}
        ui.addView(shell.root,LinearLayout.LayoutParams(-1,dp(88)))
        body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(37),0,dp(37),0)}
        ui.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        val statusPanel=FrameLayout(this)
        homeStatus=statusPanel
        statusPanel.visibility=if(state.screen==TvScreen.DETAIL)View.GONE else View.VISIBLE
        status=ClassicUi.text(this,"",9.5f,muted).apply{id=R.id.tv_site_status;gravity=Gravity.CENTER_VERTICAL}
        statusPanel.addView(status,FrameLayout.LayoutParams(-1,dp(19),Gravity.BOTTOM).apply{leftMargin=dp(6);rightMargin=dp(90)})
        val response=button(getString(R.string.classic_site_responses)){showSiteStatus()}.apply{tag="catalog:responses";setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,dp(8).toFloat());setPadding(dp(5),0,dp(5),0)}
        statusPanel.addView(response,FrameLayout.LayoutParams(dp(69),dp(19),Gravity.RIGHT or Gravity.BOTTOM).apply{rightMargin=dp(6)})
        if(state.screen==TvScreen.HOME){
            statusPanel.addView(button(getString(R.string.classic_sort,sortLabels()[classicSort.ordinal]),R.id.classic_sort){chooseSort()},
                FrameLayout.LayoutParams(dp(146),dp(24),Gravity.RIGHT or Gravity.TOP).apply{topMargin=dp(16);rightMargin=dp(8)})
        }
        body.addView(statusPanel,LinearLayout.LayoutParams(-1,if(state.screen==TvScreen.DETAIL)0 else dp(if(state.screen==TvScreen.HOME)70 else 28)))
        val footer=FrameLayout(this).apply{setPadding(dp(37),0,dp(37),0)}
        footer.addView(View(this).apply{setBackgroundColor(0xFF263040.toInt())},FrameLayout.LayoutParams(-1,1))
        footer.addView(ClassicUi.text(this,getString(R.string.classic_footer),10f,muted).apply{gravity=Gravity.CENTER_VERTICAL},FrameLayout.LayoutParams(-2,dp(22),Gravity.LEFT or Gravity.BOTTOM))
        footer.addView(ClassicUi.text(this,getString(R.string.classic_enjoy),10f,0xFFA69370.toInt()).apply{gravity=Gravity.CENTER_VERTICAL},FrameLayout.LayoutParams(-2,dp(22),Gravity.RIGHT or Gravity.BOTTOM))
        ui.addView(footer,LinearLayout.LayoutParams(-1,dp(26)))
        playerView?.apply{useController=false;keepScreenOn=false;isFocusable=false;descendantFocusability=android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS}
        val needsRepository=dataFactoryForTests==null&&!app.filmmerkezi.tv.BuildConfig.MIGRATION&&Sites.all.isEmpty()&&state.screen in setOf(TvScreen.HOME,TvScreen.MOVIES,TvScreen.SERIES,TvScreen.DISCOVER,TvScreen.SEARCH)
        if(needsRepository){
            body.addView(label(R.string.updates_no_sites,18f));body.addView(label(R.string.updates_no_sites_help,12f))
            body.addView(button(getString(R.string.updates_add_repository)){DistributionPanel(this,scope,{playback.active}).open()},LinearLayout.LayoutParams(dp(260),dp(38)))
        }else when(state.screen){
            TvScreen.HOME->home()
            TvScreen.MOVIES,TvScreen.SERIES,TvScreen.DISCOVER->catalog()
            TvScreen.SEARCH->search()
            TvScreen.LIBRARY,TvScreen.HISTORY->library()
            TvScreen.DETAIL->detail()
            TvScreen.ABOUT->about()
        }
        if(state.screen==TvScreen.HOME||state.screen==TvScreen.SEARCH)queryView?.requestFocus()
        else if(state.screen==TvScreen.DETAIL)firstContent?.requestFocus()
        else navigation[state.screen]?.requestFocus()
    }

    private fun rowsContainer(compact:Boolean=false):TvRowsFragment {
        body.addView(FrameLayout(this).apply{id=R.id.tv_content},LinearLayout.LayoutParams(-1,0,1f))
        return TvRowsFragment().also{f->
            f.arguments=Bundle().apply{putBoolean("compact",compact)}
            rows=f;f.click=::tileClick
            f.selection={row,tile->focuses[current.screen]=row to tile.key
                if(current.screen==TvScreen.HOME){val visibility=if(f.selectedPosition>0)View.GONE else View.VISIBLE
                    homeHero?.visibility=visibility;homeStatus?.visibility=visibility}
            }
            supportFragmentManager.beginTransaction().replace(R.id.tv_content,f).commitNow()
        }
    }
    private fun gridContainer(collection:Boolean=false):TvGridFragment {
        body.addView(FrameLayout(this).apply{id=R.id.tv_content},LinearLayout.LayoutParams(-1,0,1f))
        return TvGridFragment().also{f->
            f.arguments=Bundle().apply{putBoolean("collection",collection)}
            grid=f;f.click=::tileClick;f.selection={focuses[current.screen]=0L to it.key}
            supportFragmentManager.beginTransaction().replace(R.id.tv_content,f).commitNow()
        }
    }
    private fun statusTile(message:Int,action:String="retry")=TvTile("status:$action",getString(message),action=action)
    private fun shown(items:List<UnifiedItem>)=CatalogPresentation.project(items,settings.value.catalog())
    private fun tiles(items:List<UnifiedItem>)=items.map{TvTile(it.displayKey.ifBlank{it.identity},ContentIdentity.displayTitle(it.card)+(if(it.displayKey.isNotBlank())" · "+Sites.get(it.card.ref.siteId).name else ""),it)}
    private fun libraryTile(record:LibraryRecord)=TvPolicy.tile(record.card,record.positionMs,record.durationMs)
    private fun home() {
        val hero=FrameLayout(this).apply{background=ClassicUi.shape(this@TvActivity,0xFF192A3C.toInt(),7f);clipToOutline=true}
        homeHero=hero
        heroImage=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
        hero.addView(heroImage,FrameLayout.LayoutParams(dp(400),-1,Gravity.RIGHT))
        hero.addView(View(this).apply{background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(0xFF192A3C.toInt(),0xFF172638.toInt(),0x00172638))},FrameLayout.LayoutParams(-1,-1))
        val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(10),0,0)}
        info.addView(ClassicUi.text(this,getString(R.string.classic_showcase),9f,ClassicUi.gold).apply{letterSpacing=0.12f})
        val title=ClassicUi.text(this,getString(R.string.native_app_name),17f,bold=true).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        info.addView(title,LinearLayout.LayoutParams(dp(500),dp(25)))
        val meta=ClassicUi.text(this,"",9f,muted);info.addView(meta,LinearLayout.LayoutParams(dp(500),dp(14)))
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        heroButton=ClassicUi.button(this,"▶ "+getString(R.string.tv_explore),R.id.tv_hero_action,goldFill=true){
            heroCard?.let{c->open((movies.items+series.items).firstOrNull{it.card.ref.id==c.ref.id}?:UnifiedItem(ContentIdentity.key(c),c,listOf(c)))}
        }
        actions.addView(heroButton,LinearLayout.LayoutParams(dp(64),dp(23)))
        actions.addView(ClassicUi.button(this,getString(R.string.classic_recommend),R.id.classic_recommend,goldFill=true){showRecommendations()},LinearLayout.LayoutParams(dp(88),dp(23)).apply{marginStart=dp(8)})
        info.addView(actions);hero.addView(info)
        body.addView(hero,LinearLayout.LayoutParams(-1,dp(104)).apply{bottomMargin=dp(8)});firstContent=heroButton
        rowsContainer()
        fun update(){
            showHomeRows()
            val c=shown(movies.items+series.items).firstOrNull{it.card.ref.kind!=ContentKind.COLLECTION}?.card
            if(c!=null&&heroCard?.ref?.id!=c.ref.id){heroCard=c;title.text=ContentIdentity.displayTitle(c)
                meta.text=listOfNotNull(c.year?.toString(),c.language.takeIf{it.isNotBlank()},c.imdb?.let{"IMDb "+it}).joinToString(" · ")
                Posters.load(heroImage!!,c,dp(400),dp(104))}
        }
        update();launchScreen{t->library=data.records();following=data.followed();if(active(t))update();if(!playback.active){following=data.refreshFollowing();if(active(t))update()}}
        if(supports(ContentKind.MOVIE))launchScreen{t->data.browseSite(catalogPreferences.siteId,ContentKind.MOVIE).collect{if(active(t)){movies=it;update()}}}
        if(supports(ContentKind.SERIES))launchScreen{t->data.browseSite(catalogPreferences.siteId,ContentKind.SERIES).collect{if(active(t)){series=it;update()}}}
    }

    private fun showHomeRows(){
        if(settings.value.catalog().sites.isEmpty()){
            rows?.show(listOf(TvSection(0,getString(R.string.settings_catalog),listOf(statusTile(R.string.settings_disabled_sites,"settings")))))
            status.setText(R.string.settings_disabled_sites);return
        }
        fun shelf(values:List<TvTile>,more:String)=if(values.isEmpty())listOf(statusTile(R.string.tv_loading,more))else values.take(TvPolicy.ROW_CARDS)+TvTile("more:$more",getString(R.string.tv_more),action=more)
        val sections=buildList{
            if(supports(ContentKind.MOVIE))add(TvSection(2,getString(R.string.classic_new_movies),shelf(tiles(ClassicCatalogPolicy.apply(shown(movies.items),classicFilter,classicSort)),"movies")))
            val continuing=TvPolicy.continuing(library).take(TvPolicy.ROW_CARDS).map(::libraryTile)
            if(continuing.isNotEmpty())add(TvSection(1,getString(R.string.tv_continue),shelf(continuing,"library")))
            if(supports(ContentKind.SERIES))add(TvSection(4,getString(R.string.classic_new_series),shelf(tiles(shown(series.items)),"series")))
            if(following.isNotEmpty())add(TvSection(6,getString(R.string.fm_following),following.take(TvPolicy.ROW_CARDS).map{r->val item=UnifiedItem(ContentIdentity.key(r.card),r.card,listOf(r.card));TvTile(item.identity,ContentIdentity.displayTitle(r.card)+if(r.pending.isNotEmpty())" · "+getString(R.string.fm_new_episodes,r.pending.size)else "",item)}))
            val saved=library.filter{it.listed}.take(TvPolicy.ROW_CARDS).map(::libraryTile)
            if(saved.isNotEmpty())add(TvSection(5,getString(R.string.tv_my_list),shelf(saved,"library")))
        }
        val keys=mapOf(1L to "continue",2L to "movies",4L to "series",5L to "library",6L to "following")
        rows?.show(sections.filter{keys[it.id] !in settings.value.hiddenRows}.sortedBy{settings.value.rowOrder.indexOf(keys[it.id])})
        status.text=pageStatus(if(supports(ContentKind.MOVIE))movies else series,movies.items.size+series.items.size)
    }

    private fun catalog() {
        classicHeading(getString(if(current.screen==TvScreen.SERIES)R.string.tv_series else if(current.screen==TvScreen.DISCOVER)R.string.classic_discover else R.string.tv_movies))
        gridContainer()
        val screen=current.screen
        feeds[screen]?.let{showFeed(it)}
        if(feeds[screen]==null)grid?.show(listOf(statusTile(R.string.tv_loading)))
        if(feeds[screen]==null)loadFeed(false)
    }
    private fun loadFeed(more:Boolean) {
        val state=current;val previous=feeds[state.screen]
        if(feedBusy)return
        if(more&&state.screen==TvScreen.SEARCH&&previous!=null){
            val count=ClassicCatalogPolicy.apply(previous.items,classicFilter,classicSort).count{state.searchType.accepts(it)}
            if((state.searchPage+1)*TvPolicy.GRID_CARDS<count){current=current.copy(searchPage=state.searchPage+1);grid?.resetSelection();showFeed(previous);return}
        }
        val pending=pendingFeeds[state.screen].orEmpty()
        if(more&&pending.isNotEmpty()&&previous!=null){
            val bounded=previous.copy(items=(previous.items+pending.take(TvPolicy.GRID_CARDS)).takeLast(TvPolicy.GRID_CARDS))
            pendingFeeds[state.screen]=pending.drop(TvPolicy.GRID_CARDS)
            feeds[state.screen]=bounded;showFeed(bounded);return
        }
        if(more&&previous?.next.isNullOrEmpty())return
        val cursors=if(more)previous!!.next else emptyMap()
        val old=if(more)previous!!.items else emptyList()
        feedBusy=true;status.setText(R.string.tv_loading)
        launchScreen{token->
          try{
            var finalPage:UnifiedPage?=null
            val flow=if(state.screen==TvScreen.SEARCH)data.searchSite(catalogPreferences.siteId,state.query,cursors)
                else data.filteredBrowse(catalogPreferences.siteId,catalogQuery(if(state.screen==TvScreen.SERIES)ContentKind.SERIES else ContentKind.MOVIE),cursors)
            flow.collect{page->
                finalPage=page
                if(page.items.isEmpty()&&page.failures.isEmpty()&&old.isEmpty())return@collect
                val incoming=if(state.screen==TvScreen.SEARCH)page.items else page.items.take(TvPolicy.GRID_CARDS)
                val combined=if(old.isEmpty())page.copy(items=incoming) else withContext(Dispatchers.Default){page.copy(items=if(settings.value.merge)ContentIdentity.merge((old+incoming).flatMap{it.alternatives})else(old+incoming).distinctBy{it.displayKey.ifBlank{it.card.ref.id}})}
                if(active(token)){
                    // The visible window is bounded; older pages are not retained indefinitely.
                    pendingFeeds[state.screen]=if(state.screen==TvScreen.SEARCH)emptyList()else page.items.drop(TvPolicy.GRID_CARDS)
                    val bounded=if(state.screen==TvScreen.SEARCH)combined else combined.copy(items=combined.items.takeLast(TvPolicy.GRID_CARDS))
                    val shown=if(grid?.hasContentFocus()==true)bounded.copy(items=stableContentOrder(feeds[state.screen]?.items.orEmpty(),bounded.items))else bounded
                    feeds[state.screen]=shown;showFeed(shown)
                }
            }
            if(active(token)&&finalPage?.items?.isEmpty()==true&&old.isEmpty()){
                feeds[state.screen]=finalPage!!;showFeed(finalPage!!)
            }
          }finally{
              if(active(token)){feedBusy=false;feeds[state.screen]?.let(::showFeed)}
          }
        }
    }
    private fun catalogQuery(kind:ContentKind=ContentKind.MOVIE)=CatalogQuery(kind,classicFilter.genre,classicFilter.yearFrom.takeIf{it>0},classicFilter.language,classicFilter.minimumRating,CatalogOrder.entries[classicSort.ordinal])
    private fun showFeed(page:UnifiedPage){
        if(settings.value.catalog().sites.isEmpty()){grid?.show(listOf(statusTile(R.string.settings_disabled_sites,"settings")));status.setText(R.string.settings_disabled_sites);return}
        val ordered=ClassicCatalogPolicy.apply(shown(page.items),if(current.screen==TvScreen.SEARCH)classicFilter else ClassicFilter(),classicSort)
        val all=if(grid?.hasContentFocus()==true)stableContentOrder(renderedCatalog,ordered)else ordered
        renderedCatalog=all
        if(current.screen==TvScreen.SEARCH)updateSearchTabs(all)
        val selected=if(current.screen==TvScreen.SEARCH)all.filter{current.searchType.accepts(it)}else all
        if(current.screen==TvScreen.SEARCH)current=current.copy(searchPage=current.searchPage.coerceAtMost(((selected.size-1).coerceAtLeast(0))/TvPolicy.GRID_CARDS))
        val offset=if(current.screen==TvScreen.SEARCH)current.searchPage*TvPolicy.GRID_CARDS else 0
        val localMore=selected.size>offset+TvPolicy.GRID_CARDS
        val values=tiles(selected.drop(offset).take(TvPolicy.GRID_CARDS))
        val more=localMore||page.next.isNotEmpty()||!pendingFeeds[current.screen].isNullOrEmpty()
        grid?.show((if(current.screen==TvScreen.SEARCH&&current.searchPage>0)listOf(TvTile("feed:previous",getString(R.string.tv_previous),action="feed:previous"))else emptyList())+if(values.isEmpty())listOf(statusTile(if(page.failures.isEmpty())R.string.tv_no_results else R.string.tv_network_error))+if(page.next.isNotEmpty()||!pendingFeeds[current.screen].isNullOrEmpty())listOf(TvTile("feed:more",getString(R.string.tv_more),action="feed:more"))else emptyList()
            else values+if(feedBusy)listOf(statusTile(R.string.tv_loading,"feed:wait"))
            else if(more)listOf(TvTile("feed:more",getString(R.string.tv_more),action="feed:more"))else emptyList())
        status.text=pageStatus(page,values.size)+if(page.coverage.any{it.partial})" · "+getString(R.string.fm_partial_scope,page.coverage.count{it.partial})else ""
    }
    private fun sortLabels()=arrayOf(getString(R.string.classic_default_order),getString(R.string.classic_newest_order),getString(R.string.classic_rating_order),getString(R.string.classic_title_order))
    private fun classicHeading(title:String,filters:Boolean=true){
        val row=FrameLayout(this)
        row.addView(ClassicUi.text(this,title,22f,bold=true),FrameLayout.LayoutParams(dp(340),dp(32)).apply{topMargin=dp(9)})
        if(filters)row.addView(button(getString(R.string.classic_filters),R.id.classic_filter){showFilters()},FrameLayout.LayoutParams(dp(81),dp(32)).apply{leftMargin=dp(351);topMargin=dp(8)})
        row.addView(button(getString(R.string.classic_sort,sortLabels()[classicSort.ordinal]),R.id.classic_sort){chooseSort()},FrameLayout.LayoutParams(dp(146),dp(31),Gravity.RIGHT).apply{rightMargin=dp(8);topMargin=dp(8)})
        body.addView(row,LinearLayout.LayoutParams(-1,dp(49)))
        if(filters&&current.screen==TvScreen.SEARCH){
            val tabs=LinearLayout(this).apply{setPadding(0,dp(2),0,0)}
            SearchType.entries.forEach{type->val b=button(""){current=current.copy(searchType=type,searchPage=0);grid?.resetSelection();feeds[TvScreen.SEARCH]?.let(::showFeed)}
                b.tag="search-type:$type";searchTabs[type]=b;tabs.addView(b,LinearLayout.LayoutParams(-2,dp(29)).apply{marginEnd=dp(8)})}
            body.addView(tabs,LinearLayout.LayoutParams(-1,dp(45)));updateSearchTabs(emptyList())
        }else if(filters){
            val tabs=LinearLayout(this).apply{setPadding(0,dp(2),0,0)}
            tabs.addView(button(getString(R.string.tv_new)){classicFilter=ClassicFilter();reloadFiltered()}.apply{tag="catalog:new";firstContent=this;isSelected=true;setTextColor(ClassicUi.gold)},LinearLayout.LayoutParams(dp(105),dp(28)))
            if(current.screen==TvScreen.DISCOVER)tabs.addView(button(getString(R.string.fm_collections),R.id.fm_collections){showCollections()},LinearLayout.LayoutParams(-2,dp(28)).apply{marginStart=dp(8)})
            body.addView(tabs,LinearLayout.LayoutParams(-1,dp(45)))
        }
    }
    private fun updateSearchTabs(items:List<UnifiedItem>){
        val labels=listOf(R.string.ux_all_count,R.string.ux_movies_count,R.string.ux_series_count,R.string.ux_collections_count)
        SearchType.entries.forEachIndexed{i,t->searchTabs[t]?.apply{text=getString(labels[i],items.count{t.accepts(it)});isSelected=t==current.searchType;setTextColor(if(isSelected)ClassicUi.gold else ClassicUi.foreground)}}
    }
    private fun reloadFiltered(){feeds.remove(current.screen);pendingFeeds.remove(current.screen);feedBusy=false;render(current)}
    private fun refreshClassicResults(){
        findViewById<Button>(R.id.classic_sort)?.text=getString(R.string.classic_sort,sortLabels()[classicSort.ordinal])
        if(current.screen==TvScreen.HOME)render(current)
        else feeds[current.screen]?.let(::showFeed)?:if(current.screen==TvScreen.LIBRARY||current.screen==TvScreen.HISTORY)showLibrary()else render(current)
    }
    private fun chooseSort(){dialog=ClassicDialog.Builder(this).setTitle(R.string.classic_sort_title).setSingleChoiceItems(sortLabels(),classicSort.ordinal){d,i->
        classicSort=ClassicSort.entries[i];d.dismiss();reloadFiltered()
    }.setNegativeButton(R.string.tv_cancel,null).create().also{it.show()}}
    private fun showFilters(after:(()->Unit)?=null){
        val form=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(10))}
        form.addView(ClassicUi.text(this,if(current.screen==TvScreen.SEARCH)getString(R.string.classic_filter_scope)else getString(R.string.fm_filter_scope),11f,muted))
        fun field(title:String,values:Array<String>,selected:Int):ClassicChoice{
            form.addView(ClassicUi.text(this,title,12f,ClassicUi.gold).apply{setPadding(0,dp(10),0,dp(4))})
            return ClassicChoice(this).apply{contentDescription=title;setOptions(values.toList());setSelection(selected.coerceAtLeast(0));form.addView(this,LinearLayout.LayoutParams(-1,dp(31)))}
        }
        val years=listOf(0)+(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) downTo 1920).toList();val ratings=listOf(0.0,6.0,7.0,8.0);val languages=listOf("","dublaj","altyazi")
        val year=field(getString(R.string.classic_filter_year,""),years.map{if(it==0)getString(R.string.classic_any)else "$it"}.toTypedArray(),years.indexOf(classicFilter.yearFrom))
        val rating=field(getString(R.string.classic_filter_rating,""),ratings.map{if(it==0.0)getString(R.string.classic_any)else it.toString()}.toTypedArray(),ratings.indexOf(classicFilter.minimumRating))
        val language=field(getString(R.string.classic_filter_language,""),arrayOf(getString(R.string.classic_any),getString(R.string.classic_dubbed),getString(R.string.classic_subtitled)),languages.indexOf(classicFilter.language))
        val genreNames=mutableListOf(getString(R.string.classic_any))
        val genre=field(getString(R.string.fm_genre),genreNames.toTypedArray(),0)
        scope.launch{val options=data.filterOptions(catalogPreferences.siteId);genreNames.addAll(options.values["genre"].orEmpty().map{it.label}.distinctBy{GenreKey.key(it)}.sorted());if(genre.isAttachedToWindow){genre.setOptions(genreNames);genre.setSelection(genreNames.indexOf(classicFilter.genre).coerceAtLeast(0))}}
        dialog=ClassicDialog.Builder(this).setTitle(R.string.classic_filters).setView(form).setPositiveButton(R.string.classic_filter_apply){_,_->
            classicFilter=ClassicFilter(years[year.selectedItemPosition],ratings[rating.selectedItemPosition],languages[language.selectedItemPosition],genreNames.getOrNull(genre.selectedItemPosition)?.takeUnless{genre.selectedItemPosition==0}.orEmpty());if(after!=null)after()else reloadFiltered()
        }.setNeutralButton(R.string.classic_filter_clear){_,_->classicFilter=ClassicFilter();if(after!=null)after()else reloadFiltered()}
            .setNegativeButton(R.string.tv_cancel,null).create().also{it.show()}
    }
    private fun showRecommendations(){
        val form=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(12))}
        form.addView(ClassicUi.text(this,getString(R.string.fm_recommend_scope),12f))
        val genres=mutableListOf(getString(R.string.classic_any));if(classicFilter.genre.isNotBlank())genres.add(classicFilter.genre)
        val genre=ClassicChoice(this).apply{contentDescription=getString(R.string.fm_genre);setOptions(genres);form.addView(this,LinearLayout.LayoutParams(-1,dp(34)))}
        val ratings=listOf(0.0,6.0,7.0,8.0)
        val rating=ClassicChoice(this).apply{contentDescription=getString(R.string.classic_filter_rating,"");setOptions(ratings.map{if(it==0.0)getString(R.string.classic_any)else "IMDb $it+"});setSelection(ratings.indexOf(classicFilter.minimumRating).coerceAtLeast(0));form.addView(this,LinearLayout.LayoutParams(-1,dp(34)))}
        scope.launch{val options=data.filterOptions(catalogPreferences.siteId);val more=options.values["genre"].orEmpty().map{it.label};genres.addAll(more.filter{g->genres.none{GenreKey.key(it)==GenreKey.key(g)}});if(genre.isAttachedToWindow)genre.setOptions(genres)}
        dialog=ClassicDialog.Builder(this).setTitle(R.string.classic_recommend).setView(form).setPositiveButton(R.string.classic_recommend){_,_->
            val query=CatalogQuery(genre=genres.getOrNull(genre.selectedItemPosition)?.takeUnless{genre.selectedItemPosition==0}.orEmpty(),minRating=ratings[rating.selectedItemPosition])
            launchScreen{token->
                val progress=ClassicUi.text(this@TvActivity,getString(R.string.tv_loading),13f).apply{setPadding(dp(20),dp(15),dp(20),dp(15))}
                dialog=ClassicDialog.Builder(this@TvActivity).setTitle(R.string.classic_recommend).setView(progress).setNegativeButton(R.string.tv_cancel){_,_->cancelWork()}.create().also{it.setOnCancelListener{cancelWork()};it.show()}
                data.recommendations(query,catalogPreferences.siteId).collect{result->if(active(token)){
                    progress.text=getString(R.string.fm_recommend_progress,result.completedSites,result.totalSites,result.checked)
                    if(result.finished){
                        dialog?.dismiss()
                        val builder=ClassicDialog.Builder(this@TvActivity).setTitle(R.string.classic_recommend)
                        if(result.items.isEmpty())builder.setMessage(getString(R.string.fm_recommend_count,0))
                        else builder.setItems(result.items.map{it.card.title+" · "+(it.card.year?:"")+" · IMDb "+(it.card.imdb?:"—")}.toTypedArray()){_,i->open(result.items[i])}
                        dialog=builder.setNeutralButton(R.string.pool_retry){_,_->showRecommendations()}.setNegativeButton(R.string.tv_cancel,null).create().also{it.show()}
                    }
                }}
            }
        }.setNegativeButton(R.string.tv_cancel,null).create().also{it.show()}
    }
    private fun showCollections(){launchScreen{token->
        status.setText(R.string.tv_loading)
        val cards=data.collections()
        if(active(token))dialog=ClassicDialog.Builder(this@TvActivity).setTitle(R.string.fm_collections).apply{
            if(cards.isEmpty())setMessage(R.string.tv_no_results)else setItems(cards.map{it.title}.toTypedArray()){_,i->open(UnifiedItem(ContentIdentity.key(cards[i]),cards[i],listOf(cards[i])))}
        }.setNegativeButton(R.string.tv_cancel,null).create().also{it.show()}
    }}
    private fun showCollectionDetail(detail:Detail){
        supportFragmentManager.findFragmentById(R.id.tv_content)?.let{supportFragmentManager.beginTransaction().remove(it).commitNow()}
        while(body.childCount>1)body.removeViewAt(1)
        val actions=LinearLayout(this)
        actions.addView(button(getString(R.string.classic_back)){onBackPressed()}.also{firstContent=it},LinearLayout.LayoutParams(-2,dp(31)))
        var listed=library.any{it.identity==ContentIdentity.key(detail.card)&&it.listed}
        lateinit var listButton:Button
        listButton=button(getString(if(listed)R.string.tv_remove else R.string.tv_add)){
            val heldFocus=listButton.hasFocus()
            // Disabling a focused TV button moves focus elsewhere before the write completes.
            listButton.isClickable=false;launchScreen{t->try{data.update(detail.card,listed=!listed);library=data.records();if(active(t)){listed=!listed;listButton.setText(if(listed)R.string.tv_remove else R.string.tv_add)}}finally{
                listButton.isClickable=true;if(heldFocus&&listButton.isAttachedToWindow&&(currentFocus==null||currentFocus===listButton))listButton.requestFocus()
            }}
        }
        actions.addView(listButton,LinearLayout.LayoutParams(-2,dp(31)))
        if(collectionPage>0)actions.addView(button(getString(R.string.tv_previous)){collectionPage--;showCollectionDetail(detail)},LinearLayout.LayoutParams(-2,dp(31)))
        if((collectionPage+1)*24<detail.collectionItems.size)actions.addView(button(getString(R.string.tv_more)){collectionPage++;showCollectionDetail(detail)},LinearLayout.LayoutParams(-2,dp(31)))
        body.addView(actions,LinearLayout.LayoutParams(-1,dp(42)))
        body.addView(ClassicUi.text(this,detail.card.title,20f,bold=true))
        body.addView(ClassicUi.text(this,getString(R.string.fm_collection_scope),11f,muted))
        val fragment=gridContainer(true);val entries=detail.collectionItems.drop(collectionPage*24).take(24)
        launchScreen{token->val visible=entries.mapIndexedNotNull{i,e->if(data.hiddenCollectionEntry(e))null else TvTile("collection:"+(collectionPage*24+i),getString(R.string.fm_collection_member,e.ordinal,e.title,e.year))};if(active(token))fragment.show(visible)}
        fragment.click={tile->tile.key.substringAfter("collection:").toIntOrNull()?.let{openCollectionMember(detail,it)}}
        fragment.focusContent()
    }
    private fun openCollectionMember(collection:Detail,index:Int){
        val entry=collection.collectionItems.getOrNull(index)?:return
        launchScreen{token->status.setText(R.string.tv_loading);val matches=data.collectionMatches(entry,true)
            fun openMatch(item:UnifiedItem){collectionContext=collection to index;if(current.item?.card?.ref?.kind==ContentKind.COLLECTION)open(item)else render(TvNav(TvScreen.DETAIL,item))}
            if(active(token))when(matches.size){0->status.setText(R.string.tv_no_results);1->openMatch(matches.single());else->dialog=ClassicDialog.Builder(this@TvActivity).setTitle(entry.title).setItems(matches.map{it.card.title+" · "+it.card.year}.toTypedArray()){_,i->openMatch(matches[i])}.setNegativeButton(R.string.tv_cancel,null).create().also{it.show()}}
        }
    }
    private fun recentSearches(){launchScreen{token->val values=data.searches();if(active(token))dialog=ClassicDialog.Builder(this@TvActivity).setTitle(R.string.tv_search_history)
        .setItems(values.toTypedArray()){_,i->submitSearch(values[i])}.setNegativeButton(R.string.tv_cancel,null).create().also{it.show()}}}
    private fun classicOptions(){dialog=ClassicDialog.Builder(this).setTitle(R.string.classic_options).setItems(arrayOf(getString(R.string.settings_title),getString(R.string.pool_settings),getString(R.string.updates_title),getString(R.string.tv_about),getString(R.string.tv_exit))){_,i->
        when(i){0->showSettings();1->sourceSettings();2->DistributionPanel(this,scope,{playback.active}).open();3->navigate(TvNav(TvScreen.ABOUT));4->askAppExit()}
    }.setNegativeButton(R.string.tv_cancel,null).create().also{it.show()}}
    private fun showInterfaceSetup(){
        setupShowing=true;cancelWork();if(::ui.isInitialized)host.removeView(ui)
        supportFragmentManager.findFragmentById(R.id.tv_content)?.let{supportFragmentManager.beginTransaction().remove(it).commitNow()}
        heroImage?.let{Posters.clear(it)};detailImage?.let{Posters.clear(it)}
        ui=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setBackgroundColor(canvasColor);setPadding(dp(100),dp(50),dp(100),dp(50))}
        host.addView(ui,FrameLayout.LayoutParams(-1,-1))
        ui.addView(ImageView(this).apply{setImageResource(R.drawable.classic_logo)},LinearLayout.LayoutParams(dp(68),dp(68)))
        ui.addView(ClassicUi.text(this,getString(R.string.native_app_name),26f,bold=true).apply{gravity=Gravity.CENTER})
        ui.addView(ClassicUi.text(this,"v"+packageManager.getPackageInfo(packageName,0).versionName.orEmpty(),10f,ClassicUi.gold).apply{gravity=Gravity.CENTER})
        ui.addView(ClassicUi.text(this,getString(R.string.classic_setup_title),18f,ClassicUi.gold).apply{gravity=Gravity.CENTER;setPadding(0,dp(15),0,dp(8))})
        ui.addView(ClassicUi.text(this,getString(R.string.classic_setup_description),12f,muted),LinearLayout.LayoutParams(dp(540),dp(50)))
        ui.addView(ClassicUi.text(this,getString(R.string.classic_name),16f,ClassicUi.gold),LinearLayout.LayoutParams(dp(540),dp(32)))
        ui.addView(ClassicUi.text(this,getString(R.string.classic_modern),14f,muted).apply{alpha=0.65f},LinearLayout.LayoutParams(dp(540),dp(34)))
        val start=ClassicUi.button(this,getString(R.string.classic_setup_start),R.id.classic_setup_start,goldFill=true){UiPreferences(this).classic();render(TvNav())}
        ui.addView(start,LinearLayout.LayoutParams(dp(260),dp(36)).apply{topMargin=dp(16)});start.requestFocus()
    }
    private fun showSettings(){
        if(settingsScreen!=null)return
        dialog?.dismiss();classicOverlay?.close();cancelWork()
        val before=settings.value;val savedFocus=focuses[current.screen];val wasPlaying=playback.player?.playWhenReady==true
        if(playback.active)playback.setPlaying(false)
        ui.visibility=View.GONE
        lateinit var screen:SettingsScreen
        screen=SettingsScreen(this,scope,settings,{
            host.removeView(screen);settingsScreen=null;ui.visibility=View.VISIBLE
            if(before.catalog()!=settings.value.catalog()||before.siteAddresses!=settings.value.siteAddresses){
                feeds.clear();pendingFeeds.clear();movies=UnifiedPage(emptyList(),emptyMap(),emptyList());series=movies;services.trim()
            }
            if(playback.active){ui.visibility=View.GONE;applyPlayerSettings();if(adultBlocked)blockAdultPlayback()else{playback.setPlaying(wasPlaying);playerControls?.show()}}
            else{render(current);savedFocus?.let{(row,key)->body.post{rows?.restore(row,key);grid?.restore(key)}};findViewById<View>(R.id.tv_nav_about)?.requestFocus()}
        },SettingsMaintenance(this,data,services.network.client,{playback.active||pendingCheckpoint!=null||entryBusy},::localPlaybackLog,siteCheckTransportForTests?:services.network), {sleepMinutes},::setSleep)
        settingsScreen=screen;host.addView(screen,FrameLayout.LayoutParams(-1,-1))
    }
    internal fun settingsForTest(){showSettings()}
    internal fun settingsDialogForTest()=settingsScreen?.dialogForTest()
    private fun localPlaybackLog():String {
        if(!settings.value.diagnostics)return getString(R.string.settings_log_disabled)
        val s=playback.snapshot();val events=s.optJSONArray("events")?:org.json.JSONArray()
        return getString(R.string.settings_log_summary,s.optInt("rebufferCount"),s.optLong("rebufferMs"),s.optLong("averageDownloadBps")/1000)+"\n\n"+
            (0 until events.length()).map{events.getJSONObject(it)}.joinToString("\n"){"${it.optLong("atMs")} ms · ${it.optString("kind")}"}
    }
    private fun scopeName()=Sites.all.firstOrNull{it.id==catalogPreferences.siteId}?.name?:getString(R.string.tv_all_sites)
    private fun supports(kind:ContentKind):Boolean {
        val site=Sites.all.firstOrNull{it.id==catalogPreferences.siteId}?:return true
        return if(kind==ContentKind.SERIES)site.seriesPath!=null else site.id !in setOf("dizibox","ddizi","diziboxum")
    }
    private fun pageStatus(page:UnifiedPage,count:Int)=if(page.requestedSites>0)
        if(page.completedSites>=page.requestedSites)getString(R.string.classic_sites_done,page.requestedSites)else getString(R.string.classic_sites_loading,page.completedSites,page.requestedSites)
        else if(page.failures.isEmpty())getString(R.string.tv_result_count,count)else getString(R.string.tv_partial,count)
    private fun chooseSite(){
        val enabled=settings.value.catalog().sites.map(Sites::get);val ids=listOf("")+enabled.map{it.id};val names=listOf(getString(R.string.tv_all_sites))+enabled.map{it.name}
        val window=overlay(getString(R.string.tv_sites),origin=findViewById(R.id.tv_site_picker))
        window.update(ids.mapIndexed{i,id->ClassicRow("site:$id",names[i],selected=id==catalogPreferences.siteId)}){key->window.close();selectSite(key.removePrefix("site:"))}
    }
    private fun selectSite(id:String){
        cancelWork();scope.launch{try{catalogPreferences.select(id);feeds.clear();pendingFeeds.clear();focuses.clear();stack.clear()
            movies=UnifiedPage(emptyList(),emptyMap(),emptyList());series=movies;detailValue=null;episodeValues=emptyList();navigate(TvNav())
        }catch(_:Exception){Toast.makeText(this@TvActivity,R.string.settings_saved_error,Toast.LENGTH_LONG).show()}}
    }
    private fun showSiteStatus(){
        val pages=if(current.screen==TvScreen.HOME)listOf(movies,series)else listOfNotNull(feeds[current.screen])
        val sites=settings.value.catalog().sites.map(Sites::get).filter{catalogPreferences.siteId.isEmpty()||it.id==catalogPreferences.siteId}
        val labels=sites.map{s->
            val failure=pages.flatMap{it.failures}.firstOrNull{it.siteId==s.id}
            val count=pages.sumOf{it.siteCounts[s.id]?:0}
            val message=if(count>0)"$count"+(failure?.let{" · "+it.message}?:"")else failure?.let{it.message}?:getString(if(pages.any{it.siteCounts.containsKey(s.id)})R.string.tv_site_no_results else R.string.tv_site_loading)
            s.name+" · "+message
        }
        val window=overlay(getString(R.string.tv_sites_status))
        window.update(sites.mapIndexed{i,s->ClassicRow(s.id,labels[i])}){id->window.close();selectSite(id)}
    }
    private fun search() {
        classicHeading(if(current.query.isBlank())getString(R.string.tv_search)else getString(R.string.ux_search_title,current.query));gridContainer()
        if(current.query.isNotBlank()){
            feeds[TvScreen.SEARCH]?.let{showFeed(it)}?:loadFeed(false)
        }else{
            status.setText(R.string.tv_search_prompt)
            grid?.show(listOf(statusTile(R.string.tv_search_prompt,"search:focus")))
            launchScreen{t->val history=data.searches();if(active(t)&&history.isNotEmpty())grid?.show(history.take(20).map{TvTile("query:$it",it,action="query:$it")})}
        }
    }
    private fun submitSearch(query:String){
        val q=TextKey.clean(query).take(100)
        if(q.isBlank()){queryView?.requestFocus();return}
        (getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).hideSoftInputFromWindow(queryView?.windowToken,0)
        if(current.screen!=TvScreen.SEARCH){navigate(TvNav(TvScreen.SEARCH,query=q));launchScreen{data.remember(q)};return}
        cancelWork();feedBusy=false;current=current.copy(query=q,searchType=SearchType.ALL,searchPage=0);feeds.remove(TvScreen.SEARCH);pendingFeeds.remove(TvScreen.SEARCH)
        launchScreen{data.remember(q)}
        render(current)
    }
    private fun library() {
        classicHeading(getString(if(current.history)R.string.tv_history else R.string.tv_my_list),false)
        gridContainer();showLibrary()
    }

    private fun showLibrary() {
        body.findViewWithTag<View>("tab:list")?.isSelected=!current.history
        body.findViewWithTag<View>("tab:history")?.isSelected=current.history
        val state=current
        launchScreen{token->
            val records=data.recordsPage(!state.history,state.libraryPage*60,61)
            if(active(token)&&current.history==state.history&&current.libraryPage==state.libraryPage){
                val visible=records.take(60)
                val ordered=when(classicSort){ClassicSort.DEFAULT->visible;ClassicSort.NEWEST->visible.sortedByDescending{it.card.year?:0}
                    ClassicSort.RATING->visible.sortedByDescending{it.card.imdb?:0.0};ClassicSort.TITLE->visible.sortedBy{TextKey.fold(it.card.title)}}
                val tiles=buildList{
                    if(state.libraryPage>0)add(TvTile("library:previous",getString(R.string.tv_previous),action="library:previous"))
                    addAll(ordered.map(::libraryTile))
                    if(records.size>60)add(TvTile("library:next",getString(R.string.tv_more),action="library:next"))
                }
                grid?.show(tiles.ifEmpty{listOf(statusTile(R.string.tv_empty_library,"movies"))})
            }
        }
        status.text=getString(if(current.history)R.string.tv_history else R.string.tv_my_list)
    }
    private fun open(item:UnifiedItem){
        if(settings.value.hideAdult&&AdultContent.blocked(item)){Toast.makeText(this,R.string.settings_filtered,Toast.LENGTH_LONG).show();return}
        if(item.card.ref.kind==ContentKind.EPISODE){requestEpisode(item);return}
        if(item.card.ref.kind==ContentKind.COLLECTION)collectionPage=0
        if(current.item?.card?.ref?.kind!=ContentKind.COLLECTION)collectionContext=null
        navigate(TvNav(TvScreen.DETAIL,item),true)
    }
    private fun detail(){
        status.setText(R.string.tv_loading)
        gridContainer().show(listOf(statusTile(R.string.tv_loading)))
        val item=current.item?:return
        launchScreen{token->
            val detail=data.detail(item.card)
            val unified=data.work(detail.card)
            seriesWatch=if(detail.card.ref.kind==ContentKind.SERIES)data.tracking(detail.card)else null
            library=data.records()
            if(active(token)){current=current.copy(item=unified.copy(card=detail.card));detailValue=detail;showDetail(detail)}
        }
    }
    private fun showDetail(detail:Detail){
        if(current.screen!=TvScreen.DETAIL)return
        if(detail.card.ref.kind==ContentKind.COLLECTION){showCollectionDetail(detail);return}
        supportFragmentManager.findFragmentById(R.id.tv_content)?.let{supportFragmentManager.beginTransaction().remove(it).commitNow()}
        rows=null;grid=null;episodeGrid=null
        while(body.childCount>1)body.removeViewAt(1)
        val seriesDetail=detail.card.ref.kind==ContentKind.SERIES
        if(!seriesDetail)episodeValues=emptyList()
        applySeriesCatalog=null;declaredSeasons=detail.seasons
        if(seriesDetail)episodeValues=detail.episodes.map{it.copy(card=it.card.copy(seriesRef=it.series))}
        val back=FrameLayout(this)
        back.addView(button(getString(R.string.classic_back)){onBackPressed()},FrameLayout.LayoutParams(dp(57),dp(31)).apply{topMargin=dp(14)})
        back.addView(ClassicUi.text(this,"",10f,ClassicUi.gold).apply{id=R.id.fm_detail_progress;gravity=Gravity.CENTER_VERTICAL},FrameLayout.LayoutParams(dp(680),dp(31),Gravity.RIGHT).apply{topMargin=dp(14)})
        body.addView(back,LinearLayout.LayoutParams(-1,dp(59)))
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val poster=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background=ClassicUi.shape(this@TvActivity,0xFF1D2B3D.toInt(),5f);clipToOutline=true}
        detailImage=poster
        val artWidth=if(seriesDetail)102 else 175;val artHeight=if(seriesDetail)145 else 249
        top.addView(poster,LinearLayout.LayoutParams(dp(artWidth),dp(artHeight)).apply{marginEnd=dp(if(seriesDetail)18 else 22)})
        Posters.load(poster,detail.card,dp(artWidth),dp(artHeight))
        val copy=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(4),0,0)}
        copy.addView(ClassicUi.text(this,getString(if(seriesDetail)R.string.classic_series else R.string.classic_movie),9f,ClassicUi.gold).apply{letterSpacing=0.12f},LinearLayout.LayoutParams(-1,dp(17)))
        copy.addView(ClassicUi.text(this,ContentIdentity.displayTitle(detail.card),if(seriesDetail)18f else 23f,bold=true).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END})
        val durationText=TvPolicy.durationMinutes(detail.duration)?.let{getString(R.string.tv_duration_minutes,it)}?:detail.duration
        copy.addView(ClassicUi.text(this,listOfNotNull(detail.card.year?.toString(),durationText.takeIf{it.isNotBlank()&&it!="0"},detail.card.imdb?.let{"IMDb "+it},detail.card.language.takeIf{it.isNotBlank()}).joinToString(" · "),12f,ClassicUi.gold),LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(if(seriesDetail)6 else 9)})
        copy.addView(ClassicUi.text(this,detail.genres.joinToString(" / "),12f,0xFFA5B7CE.toInt()).apply{maxLines=2},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(if(seriesDetail)6 else 9)})
        val controls=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val record=library.firstOrNull{it.identity==ContentIdentity.key(detail.card)||ContentIdentity.equivalent(it.card,detail.card)}
        val progress=if(seriesDetail)TvPolicy.continuing(library).firstOrNull{it.card.seriesIdentity==ContentIdentity.key(detail.card)}else record
        val resume=progress?.positionMs?.takeIf{it>=10000&&(progress.durationMs<=0||it<progress.durationMs*95/100)}?:0
        val play=ClassicUi.button(this,if(resume>0)"▶ "+getString(R.string.tv_resume,time(resume))else getString(R.string.classic_watch),R.id.tv_play_action,goldFill=seriesDetail){
            if(seriesDetail){
                val episode=progress?.card?:episodeValues.firstOrNull{it.number.season==current.season}?.card?:episodeValues.firstOrNull()?.card
                if(episode==null)status.setText(R.string.series_no_episode_links)
                else requestEpisode(episodeItem(episode))
            }else requestStart(detail)
        }
        controls.addView(play,LinearLayout.LayoutParams(-2,dp(31)));firstContent=play
        var listed=record?.listed==true
        val save=button(getString(if(listed)R.string.tv_remove else R.string.tv_add),R.id.tv_list_action){}
        save.setOnClickListener{
            save.isEnabled=false;val wanted=!listed
            launchScreen{t->try{data.update(detail.card,listed=wanted);library=data.records();if(active(t)){listed=wanted;save.setText(if(listed)R.string.tv_remove else R.string.tv_add)}}
                catch(e:CancellationException){throw e}catch(e:Exception){if(active(t))status.setText(R.string.tv_save_error)}finally{if(active(t))save.isEnabled=true}}
        }
        controls.addView(save,LinearLayout.LayoutParams(-2,dp(31)).apply{marginStart=dp(8)})
        if(seriesDetail)controls.addView(button(getString(R.string.classic_operations)){seriesOperations(detail)},LinearLayout.LayoutParams(-2,dp(31)).apply{marginStart=dp(8)})
        val item=current.item?:UnifiedItem(ContentIdentity.key(detail.card),detail.card,listOf(detail.card))
        controls.addView(button(getString(R.string.classic_sites_count,item.alternatives.map{it.ref.siteId}.distinct().size),R.id.fm_detail_sites){chooseProvider(current.item?:item)},LinearLayout.LayoutParams(-2,dp(31)).apply{marginStart=dp(8)})

        copy.addView(controls,LinearLayout.LayoutParams(-1,dp(31)).apply{topMargin=dp(10)})
        val secondary=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        secondary.addView(button(getString(R.string.pool_find_sites),R.id.fm_find_sites){findOtherSites()},LinearLayout.LayoutParams(-2,dp(29)))
        if(seriesDetail)secondary.addView(button(getString(if(seriesWatch?.following==true)R.string.fm_unfollow else R.string.fm_follow),R.id.fm_follow_series){
            launchScreen{t->data.follow(detail.card,episodeValues,seriesWatch?.following!=true);seriesWatch=data.tracking(detail.card);if(active(t))showDetail(detail)}
        },LinearLayout.LayoutParams(-2,dp(29)).apply{marginStart=dp(8)})
        copy.addView(secondary,LinearLayout.LayoutParams(-1,dp(29)).apply{topMargin=dp(8)})
        if(!seriesDetail){
            collectionContext?.let{(collection,index)->
                val actions=LinearLayout(this)
                if(index>0)actions.addView(button(getString(R.string.fm_previous_member)){openCollectionMember(collection,index-1)},LinearLayout.LayoutParams(-2,dp(28)))
                if(index+1<collection.collectionItems.size)actions.addView(button(getString(R.string.fm_next_member)){openCollectionMember(collection,index+1)},LinearLayout.LayoutParams(-2,dp(28)))
                copy.addView(actions)
            }
            copy.addView(ClassicUi.text(this,detail.description.ifBlank{getString(R.string.tv_no_description)},13.5f,0xFFC4CFDD.toInt()).apply{maxLines=4;ellipsize=android.text.TextUtils.TruncateAt.END;setLineSpacing(dp(4).toFloat(),1f)},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(16)})
            copy.addView(button(getString(R.string.classic_read_more)){showFullText(ContentIdentity.displayTitle(detail.card),detail.description.ifBlank{getString(R.string.tv_no_description)})},LinearLayout.LayoutParams(-2,dp(31)).apply{topMargin=dp(10)})
        }else copy.addView(button(getString(R.string.classic_cast)){showFullText(getString(R.string.classic_cast),detail.cast.joinToString("\n").ifBlank{getString(R.string.classic_no_cast)})},LinearLayout.LayoutParams(-2,dp(31)).apply{topMargin=dp(6)})
        if(seriesDetail)top.addView(ScrollView(this).apply{addView(copy)},LinearLayout.LayoutParams(0,-1,1f))else top.addView(copy,LinearLayout.LayoutParams(0,-2,1f))
        if(seriesDetail)body.addView(top,LinearLayout.LayoutParams(-1,dp(199)))
        else body.addView(ScrollView(this).apply{isFillViewport=false;addView(top)},LinearLayout.LayoutParams(-1,0,1f))
        play.requestFocus();status.text=""
        if(seriesDetail){
            val watched=episodeValues.count{e->seriesWatch?.marks?.get(e.number.trackingKey())?:library.any{it.card.seriesIdentity==ContentIdentity.key(detail.card)&&it.card.episodeNumber==e.number&&it.durationMs>0&&it.positionMs>=it.durationMs*95/100}}
            val summary=ClassicUi.text(this,getString(R.string.classic_episode_progress,watched,episodeValues.size),12f,ClassicUi.gold)
            body.addView(summary,LinearLayout.LayoutParams(-1,dp(22)).apply{marginStart=dp(120)})
            body.addView(ClassicUi.text(this,getString(R.string.classic_episodes),15.5f,bold=true),LinearLayout.LayoutParams(-1,dp(27)))
            val seasons=SeasonButtons(this).apply{id=R.id.tv_season;down={episodeGrid?.focusContent()}}
            body.addView(seasons,LinearLayout.LayoutParams(-1,dp(28)).apply{bottomMargin=dp(9)})
            body.addView(FrameLayout(this).apply{id=R.id.tv_content},LinearLayout.LayoutParams(-1,0,1f))
            episodeGrid=ClassicEpisodeGridFragment().also{f->f.click=::tileClick;f.options=::episodeOptions
                supportFragmentManager.beginTransaction().replace(R.id.tv_content,f).commitNow()}
            // An archive may initially contain only the newest season. That automatic
            // provisional choice must not masquerade as a user's saved season choice.
            var requestedSeason=current.season
            fun updateSeasons(){
                val numbers=(declaredSeasons+episodeValues.map{it.number.season}).distinct().sorted()
                val chosen=requestedSeason.takeIf{it in numbers}?:episodeValues.minOfOrNull{it.number.season}?:numbers.firstOrNull()?:1
                current=current.copy(season=chosen,episodePage=if(current.season==chosen)current.episodePage else 0)
                seasons.bind(numbers,chosen)
                seasons.select={n->
                    if(current.season!=n){requestedSeason=n;current=current.copy(season=n,episodePage=0)};showEpisodes()
                }
                val updatedWatched=episodeValues.count{e->seriesWatch?.marks?.get(e.number.trackingKey())?:library.any{it.card.seriesIdentity==ContentIdentity.key(detail.card)&&it.card.episodeNumber==e.number&&it.durationMs>0&&it.positionMs>=it.durationMs*95/100}}
                summary.text=getString(R.string.classic_episode_progress,updatedWatched,episodeValues.size);showEpisodes()
            }
            episodeBusy=true;updateSeasons()
            applySeriesCatalog={catalog->episodeValues=catalog.episodes.map{it.copy(card=it.card.copy(seriesRef=it.series))};declaredSeasons=catalog.seasons;detailValue=detail.copy(episodes=episodeValues,seasons=declaredSeasons);episodeBusy=false;updateSeasons()
                val info=listOfNotNull(catalog.failures.size.takeIf{it>0}?.let{getString(R.string.series_incomplete_sites,it)},catalog.unverifiedParts.takeIf{it>0}?.let{getString(R.string.series_unverified_parts,it)}).joinToString(" · ")
                status.text=info;findViewById<TextView>(R.id.fm_detail_progress)?.text=info}
            launchScreen{t->try{val catalog=data.episodeCatalog(detail.card);val tracking=data.tracking(detail.card);if(active(t)){seriesWatch=tracking;applySeriesCatalog?.invoke(catalog)}}
                catch(e:CancellationException){throw e}catch(e:Exception){if(active(t)){episodeBusy=false;status.setText(R.string.tv_episodes_failed);showEpisodes()}}}
        }
    }
    private fun showFullText(title:String,value:String){
        val scroll=ScrollView(this);scroll.addView(ClassicUi.text(this,value,13.5f).apply{setPadding(dp(20),dp(16),dp(20),dp(16));isFocusable=true})
        dialog=ClassicDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton(R.string.tv_back,null).create().also{it.show()}
    }
    private fun seriesOperations(detail:Detail){dialog=ClassicDialog.Builder(this).setTitle(R.string.classic_operations)
        .setItems(arrayOf(getString(R.string.tv_add),getString(R.string.classic_reload_episodes),getString(R.string.fm_season_watched),getString(R.string.fm_season_unwatched),getString(R.string.fm_clear_new),getString(R.string.tv_history))){_,i->
            when(i){0->findViewById<View>(R.id.tv_list_action)?.performClick();1->{services.trim();render(current)};2,3->launchScreen{t->data.markEpisodes(detail.card,episodeValues.filter{it.number.season==current.season}.map{it.number},i==2);seriesWatch=data.tracking(detail.card);if(active(t))showDetail(detail)}
                4->launchScreen{t->data.clearNew(detail.card);seriesWatch=data.tracking(detail.card);if(active(t))showDetail(detail)};5->navigate(TvNav(TvScreen.HISTORY,history=true),true)}
        }.setNegativeButton(R.string.tv_cancel,null).create().also{it.show()}}
    private fun episodeOptions(tile:TvTile){
        val card=tile.item?.card?:return;val parent=detailValue?.card?:return;val number=card.episodeNumber
        val watched=number?.let{seriesWatch?.marks?.get(it.trackingKey())}==true
        dialog=ClassicDialog.Builder(this).setTitle(tile.label).setItems(arrayOf(getString(R.string.classic_watch),getString(R.string.classic_episode_restart),getString(if(watched)R.string.fm_unwatched else R.string.fm_watched))){_,i->
            when(i){0->tileClick(tile);1->requestEpisode(checkNotNull(tile.item),true)
                2->if(number!=null)launchScreen{t->data.markEpisodes(parent,listOf(number),!watched);seriesWatch=data.tracking(parent);if(active(t))detailValue?.let(::showDetail)}}
        }.setNegativeButton(R.string.tv_cancel,null).create().also{it.show()}
    }
    private fun showEpisodes(){
        val season=episodeValues.filter{it.number.season==current.season}.sortedBy{it.number.number}
        val page=current.episodePage.coerceIn(0,((season.size-1).coerceAtLeast(0))/TvPolicy.EPISODE_PAGE)
        if(page!=current.episodePage)current=current.copy(episodePage=page)
        val offset=page*TvPolicy.EPISODE_PAGE
        val values=buildList{
            if(offset>0)add(TvTile("episodes:previous",getString(R.string.tv_previous_episodes),action="episodes:previous"))
            addAll(season.drop(offset).take(TvPolicy.EPISODE_PAGE).map{e->TvTile(e.card.ref.id,(if(seriesWatch?.marks?.get(e.number.trackingKey())==true)"✓ "else "")+getString(R.string.classic_episode_title,e.number.season,e.number.number),UnifiedItem(ContentIdentity.key(e.card),e.card,(listOf(e.card)+e.alternatives).distinctBy{SeriesIdentity.sourceKey(it)}))})
            if(season.size>offset+TvPolicy.EPISODE_PAGE)add(TvTile("episodes:next",getString(R.string.tv_more_episodes),action="episodes:next"))
            if(isEmpty())add(statusTile(if(episodeBusy)R.string.tv_loading else R.string.series_no_episode_links,"episodes:retry"))
        };episodeGrid?.show(values)
        if(!playback.active&&pendingCheckpoint==null&&!entryBusy&&ui.visibility==View.VISIBLE)returnEpisodeKey?.let{key->
            if(values.any{it.key==key}){episodeGrid?.restore(key);returnEpisodeKey=null}
        }
    }
    private fun chooseProvider(item:UnifiedItem){
        val window=overlay(getString(R.string.ux_watch_site),origin=findViewById(R.id.fm_detail_sites));providerOverlay=window;updateProviders()
    }
    private var providerOverlay:ClassicOverlay?=null
    private fun updateProviders(){val window=providerOverlay?.takeIf{it.showing}?:return;val item=current.item?:return
        window.update(item.alternatives.map{c->ClassicRow(c.ref.id,Sites.get(c.ref.siteId).name+" · "+ContentIdentity.displayTitle(c),listOfNotNull(c.sourceSeason?.let{getString(R.string.tv_season,it)},c.language.takeIf{it.isNotBlank()}).joinToString(" · "),c.ref.id==item.card.ref.id)},
            listOf(ClassicAction("find",getString(R.string.pool_find_sites)){findOtherSites()}),findViewById<TextView>(R.id.fm_detail_progress)?.text?.toString().orEmpty()){id->
                item.alternatives.firstOrNull{it.ref.id==id}?.let{c->window.close();current=current.copy(item=item.copy(card=c));detailValue=null;render(current)}
            }
    }
    private fun findOtherSites(){
        val detail=detailValue?:return
        launchScreen{token->data.discoverAlternatives(detail.card,true).collect{found->if(active(token)){
            current=current.copy(item=found.item.copy(card=detail.card))
            findViewById<Button>(R.id.fm_detail_sites)?.text=getString(R.string.classic_sites_count,found.item.alternatives.map{it.ref.siteId}.distinct().size)
            findViewById<TextView>(R.id.fm_detail_progress)?.text=getString(R.string.fm_alternatives_progress,found.completed,found.total,found.item.alternatives.map{it.ref.siteId}.distinct().size,found.failures.size)
            updateProviders()
            if(!found.running&&detail.card.ref.kind==ContentKind.SERIES){val catalog=data.episodeCatalog(detail.card);if(active(token))applySeriesCatalog?.invoke(catalog)}
        }}}
    }
    private suspend fun backgroundGate(){
        while(playback.active&&playback.player?.let{it.playbackState==androidx.media3.common.Player.STATE_BUFFERING}==true)delay(250)
    }
    private fun startSourceUpdates(card:Card,initial:List<SourceOption>,revision:Int){
        sourcePoolJob?.cancel()
        sourcePoolJob=scope.launch{try{
            val episode=sourceEpisode?.takeIf{it.card.ref.id==card.ref.id}
            val updates=if(episode!=null)data.episodeSourcePool(episode,playerPreferences.crossSiteSources,::backgroundGate)
                else data.sourcePool(card,initial,playerPreferences.crossSiteSources,::backgroundGate)
            updates.collect{pool->
                if(revision!=sourceRevision)return@collect
                sourceState.value=pool;pool.options.forEach{sourceOptions[it.id]=it}
                if(playback.active){playback.appendCandidates(pool.options.filter{it.state==OptionState.AVAILABLE}.map(::candidate),pool.pending);playerControls?.sourcesChanged(pool.pending)}
                else {status.text=pool.progress.ifBlank{getString(R.string.pool_searching)};playerNotice?.text=status.text;findViewById<TextView>(R.id.fm_detail_progress)?.text=status.text;initialSourceCard?.let{drawInitialSources(it,initialSourceResume)}}
            }
        }catch(e:CancellationException){throw e}catch(e:Exception){if(revision==sourceRevision){sourceState.value=sourceState.value.copy(pending=false,progress=e.message.orEmpty());if(playback.active)playback.appendCandidates(emptyList(),false)}}}
    }
    private fun chooseSource(detail:Detail,resume:Long){
        cancelSources();sourceOptions.clear();primedSources.clear()
        initialSourceResume=resume
        val revision=sourceRevision
        sourceState.value=SourcePool(detail.options.map{it.scoped()},sourceEpisode!=null||playerPreferences.crossSiteSources)
        sourceState.value.options.forEach{sourceOptions[it.id]=it}
        startSourceUpdates(detail.card,detail.options,revision)
        if(!playerPreferences.automaticSources){showInitialSources(detail.card,resume);return}
        status.setText(R.string.pool_searching);playerNotice?.setText(R.string.pool_searching)
        sourceStartJob=scope.launch{
            try{
                val ready=FirstReadySource.await(sourceState){data.resolve(it)}
                if(revision!=sourceRevision||!foreground)return@launch
                primedSources[ready.first.id]=ready.second
                val options=listOf(ready.first)+sourceState.value.options.filter{it.id!=ready.first.id&&it.state==OptionState.AVAILABLE}
                play(detail.card,options,resume);playback.appendCandidates(emptyList(),sourceState.value.pending)
            }catch(e:CancellationException){if(e !is TimeoutCancellationException)throw e;if(revision==sourceRevision){sourceProblem(getString(R.string.fm_start_timeout));showInitialSources(detail.card,resume)}}
            catch(e:Exception){if(revision==sourceRevision){sourceProblem(e.message?:getString(R.string.tv_no_source));showInitialSources(detail.card,resume)}}
        }
        sourceDeadlineJob=scope.launch{delay(60000);if(revision==sourceRevision&&playback.active&&!playback.snapshot().optBoolean("firstFrame")){
            sourceStartJob?.cancel();playerNotice?.text=getString(R.string.fm_start_timeout);playerControls?.openSources()
        }}
    }
    private fun sourceProblem(message:String){
        status.text=message;findViewById<TextView>(R.id.fm_detail_progress)?.text=message
        Toast.makeText(this,message.take(160),Toast.LENGTH_LONG).show()
    }
    private fun showInitialSources(card:Card,resume:Long){
        initialSourceCard=card;initialSourceResume=resume
        overlay(getString(R.string.ux_sources),true,back=::cancelEntry)
        drawInitialSources(card,resume)
    }
    private fun sourceRows(pool:SourcePool):List<ClassicRow> = buildList {
        pool.options.groupBy{it.content.siteId}.forEach{(site,options)->
            add(ClassicRow("header:$site",Sites.get(site).name,header=true))
            options.forEach{o->add(ClassicRow(o.id,o.label,o.reason.ifBlank{getString(if(o.state==OptionState.AVAILABLE)R.string.ux_untried else R.string.tv_no_source)},enabled=o.state==OptionState.AVAILABLE))}
        }
        pool.errors.forEachIndexed{i,e->add(ClassicRow("error:$i:${e.siteId}",Sites.all.firstOrNull{it.id==e.siteId}?.name?:e.siteId,e.message,enabled=false))}
    }
    private fun drawInitialSources(card:Card,resume:Long){
        val pool=sourceState.value
        classicOverlay?.update(sourceRows(pool),
            actions=if(!pool.pending)listOf(ClassicAction("retry",getString(R.string.tv_retry)){chooseSource(Detail(card),resume)})else emptyList(),
            message=pool.progress){id->
            val option=sourceState.value.options.firstOrNull{it.id==id&&it.state==OptionState.AVAILABLE}?:return@update
            initialSourceCard=null;classicOverlay?.close();sourceStartJob?.cancel()
            sourceStartJob=scope.launch{play(card,listOf(option)+sourceState.value.options.filter{it.id!=id&&it.state==OptionState.AVAILABLE},resume)}
        }
    }
    private fun ensurePlayerView(){
        if(playerView!=null)return
        val root=FrameLayout(this).apply{setBackgroundColor(Color.BLACK)}
        playerView=(layoutInflater.inflate(R.layout.tv_player,root,false)as PlayerView).apply{
            setBackgroundColor(Color.BLACK);setControllerAnimationEnabled(false)
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener{visible->subtitleAppearance?.controls(visible==View.VISIBLE)})
        }
        subtitleAppearance=SubtitleAppearance(playerView!!).also{it.apply(playbackOverrides.apply(settings.value.player));it.bind(playback.player)}
        root.addView(playerView,FrameLayout.LayoutParams(-1,-1))
        playerControls=TvPlayerControls(this,playerView!!,playback,playerPreferences,::sourceSettingsChanged,::requestExit,::changeEpisode,{sourceState.value},::rememberTrack,{sleepMinutes},::setSleep,{subtitleAppearance?.seeking(it)},::showSettings,::resetContentPreferences)
        playerNotice=text("",15f).apply{setBackgroundColor(0xA0090B10.toInt());setPadding(dp(12),dp(6),dp(12),dp(6))}
        root.addView(playerNotice,FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START))
        host.addView(root,0,FrameLayout.LayoutParams(-1,-1))
    }
    private fun episodeItem(card:Card):UnifiedItem {
        val row=episodeValues.firstOrNull{it.card.ref.id==card.ref.id||it.number==card.episodeNumber}
        return UnifiedItem(ContentIdentity.key(card),card,(listOf(card)+row?.alternatives.orEmpty()).distinctBy(SeriesIdentity::sourceKey))
    }
    private fun requestEpisode(item:UnifiedItem,restart:Boolean=false){
        val parent=detailValue?.card?.takeIf{it.ref.kind==ContentKind.SERIES&&current.screen==TvScreen.DETAIL&&current.item?.card?.ref?.id==it.ref.id&&
            episodeValues.any{e->SeriesIdentity.sourceKey(e.card)==SeriesIdentity.sourceKey(item.card)}}
        val card=item.card.copy(seriesRef=item.card.seriesRef?:parent?.ref,
            seriesIdentity=item.card.seriesIdentity.ifBlank{parent?.let{current.item?.identity?:ContentIdentity.key(it)}.orEmpty()})
        val episode=item.copy(card=card,alternatives=item.alternatives.map{if(SeriesIdentity.sourceKey(it)==SeriesIdentity.sourceKey(card))card else it})
        if(current.screen==TvScreen.DETAIL&&parent!=null)returnEpisodeKey=card.ref.id
        requestStart(Detail(card),episode,restart)
    }
    private fun requestStart(detail:Detail,episode:UnifiedItem?=null,restart:Boolean=false){
        if(settings.value.hideAdult&&AdultContent.blocked(detail.card)){sourceProblem(getString(R.string.settings_filtered));return}
        if(pendingCheckpoint!=null||finishBusy||entryBusy)return
        entryBusy=true;sourceEpisode=episode;val revision=++entryRevision
        entryJob=scope.launch{
            var opened:PlaybackCheckpoint?=null;var handedOff=false
            try{
            withContext(NonCancellable){opened=data.beginPlayback(detail.card)}
            currentCoroutineContext().ensureActive()
            if(revision!=entryRevision||!foreground)return@launch
            val cp=checkNotNull(opened);pendingCheckpoint=cp;handedOff=true
            if(!foreground){cancelEntry();return@launch}
            ensurePlayerView();ui.visibility=View.GONE;playerControls?.title(ContentIdentity.displayTitle(detail.card));playerControls?.episodes(false,false)
            Glide.with(this@TvActivity).pauseRequestsRecursive();playerView?.requestFocus();playerControls?.show()
            playerNotice?.text=if(episode!=null)getString(R.string.ux_episode_loading)else getString(R.string.player_preparing,ContentIdentity.displayTitle(detail.card))
            val canResume=cp.positionMs>=10000&&(cp.durationMs<=0||cp.positionMs<cp.durationMs*95/100)
            val resume=(cp.positionMs-settings.value.resumeRewind*1000L).coerceAtLeast(0)
            if(restart||settings.value.entry=="RESTART"){chooseSource(detail,0);return@launch}
            if(settings.value.entry=="RESUME"){chooseSource(detail,if(canResume)resume else 0);return@launch}
            if(episode!=null&&(restart||!canResume)){chooseSource(detail,0);return@launch}
            val window=overlay(getString(R.string.ux_start_title),back=::cancelEntry)
            window.update(emptyList(),listOf(
                ClassicAction("continue",if(canResume)getString(R.string.ux_continue_time,time(resume))else getString(R.string.ux_continue),canResume){window.close();chooseSource(detail,resume)},
                ClassicAction("restart",getString(R.string.ux_restart)){window.close();chooseSource(detail,0)},
                ClassicAction("exit",getString(R.string.tv_exit)){window.close();cancelEntry()}
            ),if(canResume)getString(R.string.ux_last_position,time(cp.positionMs))else getString(R.string.ux_no_position))
            window.focus(if(canResume)"continue"else "restart")
        }catch(e:CancellationException){throw e}catch(e:Exception){sourceProblem(getString(R.string.ux_storage_retry))}
        finally{
            if(!handedOff)opened?.let{cp->services.storageScope.launch{try{data.abandonPlayback(cp)}catch(_:Exception){android.util.Log.e("FilmStorage","Unused entry checkpoint")}}}
            if(revision==entryRevision){entryBusy=false;if(!handedOff)sourceEpisode=null}
        }}
    }
    private fun cancelEntry()=cancelEntry(true)
    private fun cancelEntry(restore:Boolean){
        entryRevision++;entryJob?.cancel();entryJob=null;entryBusy=false
        cancelSources();initialSourceCard=null;sourceEpisode=null;classicOverlay?.close()
        val cp=pendingCheckpoint;pendingCheckpoint=null
        if(cp!=null)services.storageScope.launch{try{data.abandonPlayback(cp)}catch(e:Exception){android.util.Log.e("FilmStorage","Entry checkpoint",e)}}
        if(!playback.active){Glide.with(this@TvActivity).resumeRequestsRecursive();ui.visibility=View.VISIBLE;if(restore&&foreground)render(current)}
    }
    private fun requestExit(){
        if(!playback.active){if(pendingCheckpoint!=null||entryBusy)cancelEntry();return}
        if(finishBusy)return
        if(exitOpen)return
        nextEpisodeJob?.cancel();playerControls?.close()
        exitWasPlaying=playback.player?.playWhenReady==true;exitOpen=true;playback.setPlaying(false)
        drawExit()
    }
    private fun drawExit(message:String=""){
        val window=overlay(getString(R.string.ux_exit_title),back={resumeExit(false)})
        window.canDismiss={!finishBusy}
        window.update(emptyList(),listOf(
            ClassicAction("continue",getString(R.string.ux_continue)){resumeExit(true)},
            ClassicAction("restart",getString(R.string.ux_restart)){resumeExit(true,true)},
            ClassicAction("save",getString(R.string.ux_save_exit)){finishPlayback(PlaybackFinish.SAVE)},
            ClassicAction("discard",getString(R.string.ux_discard_exit)){finishPlayback(PlaybackFinish.DISCARD)}
        ),message)
    }
    private fun resumeExit(play:Boolean,restart:Boolean=false){
        if(finishBusy)return
        finishBusy=true
        scope.launch{try{
            checkpoint?.let{data.cancelPlaybackFinish(it)}
            classicOverlay?.close();exitOpen=false
            if(restart)playback.player?.seekTo(0)
            playback.setPlaying(if(play)true else exitWasPlaying)
        }catch(e:Exception){drawExit(getString(R.string.ux_storage_retry))}finally{finishBusy=false}}
    }
    private fun finishPlayback(mode:PlaybackFinish){
        if(finishBusy)return
        val cp=checkpoint?:return;finishBusy=true
        val position=playback.player?.currentPosition?:0;val duration=playback.player?.duration?.coerceAtLeast(0)?:0
        classicOverlay?.update(emptyList(),message=getString(R.string.ux_saving))
        scope.launch{try{
            data.finishPlayback(cp,mode,position,duration)
            checkpoint=null;exitOpen=false;adultBlocked=false;sleepMinutes=0;sleepDeadline=0;classicOverlay?.close();cancelSources();playerControls?.close();mediaButtons.close()
            settingsScreen?.let{it.dispose();host.removeView(it)};settingsScreen=null
            playback.stopPlayback();playingCard=null;sourceEpisode=null;Glide.with(this@TvActivity).resumeRequestsRecursive();render(current);firstContent?.requestFocus()
        }catch(e:CancellationException){throw e}catch(e:Exception){drawExit(getString(R.string.ux_storage_retry))}finally{finishBusy=false}}
    }
    private fun applyPlayerSettings(){
        val v=settings.value;subtitleAppearance?.apply(playbackOverrides.apply(v.player));playerControls?.applySettings();playback.configure(playbackOverrides.apply(v.player));playback.diagnosticsEnabled=v.diagnostics
        playback.automaticSources=v.automaticSources;playback.crossSiteSources=v.crossSiteSources
        playback.candidateAllowed={candidate->!adultBlocked&&sourceOptions[candidate.optString("lazyOptionId")]?.let{v.sourceAllowed(it.content.siteId)}!=false}
        playerView?.resizeMode=when(playbackOverrides.apply(v.player).sizing){app.filmmerkezi.tv.VideoSizing.FIT->androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT;app.filmmerkezi.tv.VideoSizing.ZOOM->androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;else->androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL}
    }
    private fun resetContentPreferences(){
        val card=playingCard?:return
        val wasPlaying=playback.player?.playWhenReady==true;playback.setPlaying(false)
        dialog=ClassicDialog.Builder(this).setTitle(R.string.settings_reset_this).setMessage(R.string.settings_clear_overrides_help)
            .setPositiveButton(R.string.settings_apply){_,_->scope.launch{try{playbackOverrides=data.updatePlaybackPreferences(card){app.filmmerkezi.tv.settings.PlaybackOverrides()};preferenceRevision++;applyPlayerSettings();playback.setPlaying(wasPlaying)}catch(_:Exception){Toast.makeText(this@TvActivity,R.string.settings_operation_error,Toast.LENGTH_LONG).show()}}}
            .setNegativeButton(R.string.tv_cancel){_,_->playback.setPlaying(wasPlaying)}.create().also{it.setOnCancelListener{playback.setPlaying(wasPlaying)};it.show()}
    }
    private fun rememberTrack(type:Int,choice:PlaybackSession.TrackChoice?){
        val card=playingCard?:return;val revision=sourceRevision
        val lang=choice?.format?.language?.takeIf{it!="und"&&it.matches(Regex("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*"))}
        if(type!=androidx.media3.common.C.TRACK_TYPE_VIDEO&&choice!=null&&lang==null)return
        if(type==androidx.media3.common.C.TRACK_TYPE_VIDEO&&choice!=null&&choice.format.height<=0)return
        val change:(app.filmmerkezi.tv.settings.PlaybackOverrides)->app.filmmerkezi.tv.settings.PlaybackOverrides={old->when(type){
                androidx.media3.common.C.TRACK_TYPE_VIDEO->old.copy(height=choice?.format?.height?:0)
                androidx.media3.common.C.TRACK_TYPE_AUDIO->old.copy(audio=lang,audioRole=choice?.format?.roleFlags)
                else->old.copy(subtitles=if(choice==null)app.filmmerkezi.tv.SubtitlePolicy.OFF else app.filmmerkezi.tv.SubtitlePolicy.ON,text=lang,textRole=choice?.format?.roleFlags)
            }}
        playbackOverrides=change(playbackOverrides);val preferenceTicket=++preferenceRevision
        scope.launch{try{
            val saved=data.updatePlaybackPreferences(card,change)
            if(playingCard?.ref?.id==card.ref.id&&revision==sourceRevision&&preferenceTicket==preferenceRevision){playbackOverrides=saved;applyPlayerSettings()}
        }catch(_:Exception){Toast.makeText(this@TvActivity,R.string.settings_saved_error,Toast.LENGTH_LONG).show()}}
    }
    private fun setSleep(minutes:Int){sleepMinutes=minutes;sleepDeadline=if(minutes>0&&playback.active)android.os.SystemClock.elapsedRealtime()+minutes*60000L else 0}
    private fun sleepExpired()=sleepDeadline>0&&android.os.SystemClock.elapsedRealtime()>=sleepDeadline
    private fun finishForSleep(){nextEpisodeJob?.cancel();playback.setPlaying(false);finishPlayback(PlaybackFinish.SAVE)}
    private fun blockAdultPlayback(){
        adultBlocked=true;playback.setPlaying(false);cancelSources()
        val w=overlay(getString(R.string.settings_adult),back={});w.canDismiss={false}
        w.update(emptyList(),listOf(ClassicAction("exit",getString(R.string.ux_save_exit)){finishPlayback(PlaybackFinish.SAVE)},ClassicAction("settings",getString(R.string.settings_title)){w.close();showSettings()}),getString(R.string.settings_filtered))
    }
    internal fun sleepForTest(milliseconds:Long){sleepMinutes=1;sleepDeadline=android.os.SystemClock.elapsedRealtime()+milliseconds}
    internal fun endForTest(){playback.player?.let{it.seekTo(it.duration.coerceAtLeast(0))}}
    private fun sourceSettingsChanged(){
        applyPlayerSettings()
        if(pendingCheckpoint!=null&&sourceEpisode!=null){chooseSource(Detail(checkNotNull(sourceEpisode).card),initialSourceResume);return}
        if(!playerPreferences.crossSiteSources){sourcePoolJob?.cancel();if(playback.active)playback.appendCandidates(emptyList(),false)}
        else if(playback.active)playingCard?.let{startSourceUpdates(it,sourceOptions.values.toList(),sourceRevision)}
    }
    private fun sourceSettings(){
        dialog=ClassicDialog.Builder(this).setTitle(R.string.pool_settings).setMultiChoiceItems(arrayOf(getString(R.string.pool_automatic),getString(R.string.pool_cross_site)),
            booleanArrayOf(settings.value.automaticSources,settings.value.crossSiteSources)){_,i,on->scope.launch{
            try{settings.update{if(i==0)it.copy(automaticSources=on)else it.copy(crossSiteSources=on)};sourceSettingsChanged()}
            catch(_:Exception){dialog?.dismiss();dialog=ClassicDialog.Builder(this@TvActivity).setMessage(R.string.settings_saved_error).setPositiveButton(R.string.tv_back,null).show()}
        }}.setPositiveButton(R.string.tv_player_done,null).create().also{it.show()}
    }
    private fun about(){
        body.addView(label(R.string.tv_about_description,23f).apply{setPadding(dp(15),dp(25),0,dp(12))})
        val version=packageManager.getPackageInfo(packageName,0).versionName.orEmpty()
        body.addView(text(getString(R.string.tv_about_version,version),16f,muted))
        val licenses=button(getString(R.string.tv_licenses)){launchScreen{token->
            val content=withContext(Dispatchers.IO){assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use{it.readText()}}
            if(active(token)){
                val scroll=ScrollView(this@TvActivity);val text=text(content,14f).apply{setPadding(dp(18),dp(18),dp(18),dp(18));isFocusable=true}
                scroll.addView(text)
                dialog=ClassicDialog.Builder(this@TvActivity).setTitle(R.string.tv_licenses).setView(scroll).setPositiveButton(R.string.tv_back,null).create().also{it.show()}
            }
        }}
        body.addView(licenses,LinearLayout.LayoutParams(dp(410),dp(50)).apply{topMargin=dp(25)});firstContent=licenses
    }
    private fun tileClick(tile:TvTile){
        tile.item?.let{focuses[current.screen]=(focuses[current.screen]?.first?:0L) to tile.key;open(it);return}
        when{
            tile.action=="settings"->showSettings()
            tile.action=="movies"->navigate(TvNav(TvScreen.MOVIES))
            tile.action=="series"->navigate(TvNav(TvScreen.SERIES))
            tile.action=="library"->navigate(TvNav(TvScreen.LIBRARY))
            tile.action=="feed:more"->loadFeed(true)
            tile.action=="feed:previous"->{current=current.copy(searchPage=(current.searchPage-1).coerceAtLeast(0));grid?.resetSelection();feeds[TvScreen.SEARCH]?.let(::showFeed)}
            tile.action=="feed:wait"->Unit
            tile.action=="library:next"->{current=current.copy(libraryPage=current.libraryPage+1);showLibrary()}
            tile.action=="library:previous"->{current=current.copy(libraryPage=(current.libraryPage-1).coerceAtLeast(0));showLibrary()}
            tile.action=="search:focus"->queryView?.requestFocus()
            tile.action.startsWith("query:")->{val q=tile.action.removePrefix("query:");queryView?.setText(q);submitSearch(q)}
            tile.action=="episodes:next"->{current=current.copy(episodePage=current.episodePage+1);showEpisodes();episodeGrid?.focusContent()}
            tile.action=="episodes:previous"->{current=current.copy(episodePage=(current.episodePage-1).coerceAtLeast(0));showEpisodes();episodeGrid?.focusContent()}
            tile.action=="episodes:retry"->{detailValue=null;render(current)}
            else->if(current.screen==TvScreen.SEARCH&&current.query.isBlank())queryView?.requestFocus()else render(current)
        }
    }
    private fun showError(){
        status.setText(R.string.tv_network_error)
        grid?.show(listOf(statusTile(R.string.tv_retry)))
        if(rows==null&&grid==null)body.addView(button(getString(R.string.tv_retry)){render(current)}.also{firstContent=it})
    }
    private fun focusAboveContent(fromGrid:Boolean=false){
        if(current.screen==TvScreen.SEARCH){searchTabs[current.searchType]?.requestFocus();return}
        if(firstContent?.isShown==true){firstContent?.requestFocus();return}
        if(fromGrid)(navigation[current.screen]?:navigation[TvScreen.HOME])?.requestFocus()else focusContent()
    }
    /** Explicit TV focus zones; asynchronous catalog replies never choose the zone. */
    private fun catalogDirection(key:Int):Boolean {
        if(setupShowing||!::body.isInitialized)return false
        val focus=currentFocus?:return false
        val nav=navigation.values.toList()
        val chips=searchTabs.values.toList()
        val query=queryView
        val header=listOfNotNull(query,findViewById<View>(R.id.tv_search_submit),findViewById(R.id.classic_search_clear),findViewById(R.id.classic_search_recent),findViewById(R.id.tv_nav_about))
        val sites=findViewById<View>(R.id.tv_site_picker)
        val responses=body.findViewWithTag<View>("catalog:responses")?.takeIf{it.isShown}
        val filters=findViewById<View>(R.id.classic_filter)
        val sort=findViewById<View>(R.id.classic_sort)
        val added=body.findViewWithTag<View>("catalog:new")
        val collections=findViewById<View>(R.id.fm_collections)
        fun go(target:View?):Boolean{target?.takeIf{it.isShown&&it.isFocusable}?.requestFocus();return true}
        fun down():Boolean{focusAboveContent();return true}
        fun cards():Boolean{if(grid!=null)grid?.focusContent()else focusContent();return true}
        if(focus===query){
            if(key==KeyEvent.KEYCODE_DPAD_DOWN)return go(navigation[current.screen]?:nav.firstOrNull())
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT&&query.selectionStart==query.text.length&&query.selectionEnd==query.text.length)return go(header.getOrNull(1))
            return false
        }
        val h=header.indexOf(focus)
        if(h>=0)return when(key){KeyEvent.KEYCODE_DPAD_LEFT->go(header.getOrNull(h-1));KeyEvent.KEYCODE_DPAD_RIGHT->go(header.getOrNull(h+1));KeyEvent.KEYCODE_DPAD_DOWN->go(if(focus.id==R.id.tv_nav_about)sites else navigation[current.screen]?:nav.firstOrNull());KeyEvent.KEYCODE_DPAD_UP->true;else->false}
        val n=nav.indexOf(focus)
        if(n>=0)return when(key){KeyEvent.KEYCODE_DPAD_LEFT->go(nav.getOrNull(n-1));KeyEvent.KEYCODE_DPAD_RIGHT->go(nav.getOrNull(n+1)?:sites);KeyEvent.KEYCODE_DPAD_UP->go(query);KeyEvent.KEYCODE_DPAD_DOWN->down();else->false}
        if(focus===sites)return when(key){KeyEvent.KEYCODE_DPAD_UP->go(header.lastOrNull());KeyEvent.KEYCODE_DPAD_DOWN->go(responses?:sort?:firstContent);KeyEvent.KEYCODE_DPAD_LEFT->go(nav.lastOrNull());KeyEvent.KEYCODE_DPAD_RIGHT->true;else->false}
        if(focus===responses)return when(key){KeyEvent.KEYCODE_DPAD_UP->go(sites);KeyEvent.KEYCODE_DPAD_DOWN->if(sort!=null)go(sort)else down();KeyEvent.KEYCODE_DPAD_LEFT->if(filters!=null)go(filters)else down();KeyEvent.KEYCODE_DPAD_RIGHT->true;else->false}
        if(focus===filters||focus===sort)return when(key){
            KeyEvent.KEYCODE_DPAD_UP->go(if(focus===sort)responses?:sites else navigation[TvScreen.DISCOVER]?:nav.firstOrNull())
            KeyEvent.KEYCODE_DPAD_DOWN->down()
            KeyEvent.KEYCODE_DPAD_LEFT->go(if(focus===sort)filters?:chips.lastOrNull()?:added else chips.lastOrNull()?:added)
            KeyEvent.KEYCODE_DPAD_RIGHT->go(if(focus===filters)sort else sites)
            else->false}
        val chip=chips.indexOf(focus)
        if(chip>=0)return when(key){
            KeyEvent.KEYCODE_DPAD_UP->go(navigation[listOf(TvScreen.HOME,TvScreen.MOVIES,TvScreen.SERIES,TvScreen.DISCOVER)[chip]]?:nav.firstOrNull())
            KeyEvent.KEYCODE_DPAD_DOWN->cards()
            KeyEvent.KEYCODE_DPAD_LEFT->go(chips.getOrNull(chip-1))
            KeyEvent.KEYCODE_DPAD_RIGHT->go(chips.getOrNull(chip+1)?:filters?:sort)
            else->false}
        if(focus===added||focus===collections)return when(key){KeyEvent.KEYCODE_DPAD_UP->go(navigation[current.screen]?:nav.firstOrNull());KeyEvent.KEYCODE_DPAD_DOWN->cards();KeyEvent.KEYCODE_DPAD_LEFT->go(added);KeyEvent.KEYCODE_DPAD_RIGHT->go(if(focus===added)collections?:filters else filters);else->false}
        if(key==KeyEvent.KEYCODE_DPAD_UP){
            val inContent=findViewById<View>(R.id.tv_content)?.hasFocus()==true
            if(inContent&&episodeGrid?.atTop()==true){findViewById<SeasonButtons>(R.id.tv_season)?.requestFocus();return true}
            if(inContent&&(grid?.atTop()==true||episodeGrid?.atTop()==true||rows?.atTop()==true)){focusAboveContent(true);return true}
            if(focus===firstContent||heroButton?.hasFocus()==true)return go(navigation[current.screen]?:nav.firstOrNull())
        }
        return grid?.move(key)==true
    }
    private fun focusContent(){
        if(firstContent?.isShown==true){firstContent?.requestFocus();return}
        if(episodeGrid!=null)episodeGrid?.focusContent()else if(rows!=null)rows?.focusContent()else if(grid!=null)grid?.focusContent()else navigation[current.screen]?.requestFocus()
    }
    // Framework Activity override: route TV keys before child views consume them.
    // Core's legacy ComponentActivity marks its implementation library-restricted.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event:KeyEvent):Boolean {
        // Open on release: the same Back key's UP must not immediately dismiss the new dialog.
        if(playback.active&&event.keyCode==KeyEvent.KEYCODE_BACK){if(event.action==KeyEvent.ACTION_UP)onBackPressed();return true}
        if(playback.active&&event.keyCode==KeyEvent.KEYCODE_MEDIA_STOP&&event.action==KeyEvent.ACTION_DOWN){onBackPressed();return true}
        if(playback.active&&!exitOpen&&!finishBusy&&classicOverlay?.showing!=true&&dialog?.isShowing!=true&&playerControls?.handleDirectional(event)==true)return true
        if(playback.active&&event.action==KeyEvent.ACTION_DOWN){
            if(exitOpen||finishBusy)return true
            when(event.keyCode){KeyEvent.KEYCODE_MEDIA_NEXT->{changeEpisode(1);return true};KeyEvent.KEYCODE_MEDIA_PREVIOUS->{changeEpisode(-1);return true}}
            when(event.keyCode){
                KeyEvent.KEYCODE_MEDIA_PAUSE->{playback.setPlaying(false);return true}
                KeyEvent.KEYCODE_MEDIA_PLAY->{playback.setPlaying(true);return true}
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE->{playback.setPlaying(playback.player?.playWhenReady!=true);return true}
            }
        }
        if(!playback.active&&pendingCheckpoint==null&&!entryBusy&&classicOverlay?.showing!=true&&dialog?.isShowing!=true&&event.action==KeyEvent.ACTION_DOWN&&catalogDirection(event.keyCode))return true
        if(playback.active&&playerView?.dispatchKeyEvent(event)==true)return true
        return super.dispatchKeyEvent(event)
    }
    private suspend fun play(card:Card,options:List<SourceOption>,position:Long){
        if(!foreground)return
        if(sleepExpired()){finishForSleep();return}
        val cp=pendingCheckpoint?:data.beginPlayback(card)
        try{
            checkpoint?.takeIf{it.token!=cp.token}?.let{previous->data.finishPlayback(previous,PlaybackFinish.SAVE,playback.player?.currentPosition?:0,playback.player?.duration?.coerceAtLeast(0)?:0)}
        }catch(e:Exception){sourceProblem(getString(R.string.ux_storage_retry));return}
        checkpoint=cp;pendingCheckpoint=null;initialSourceCard=null;classicOverlay?.close()
        playerControls?.close();cancelWork();playback.stopPlayback();playingCard=card
        ensurePlayerView()
        ui.visibility=View.GONE
        Glide.with(this@TvActivity).pauseRequestsRecursive()
        playerView!!.apply{isFocusable=true;useController=true;keepScreenOn=true;descendantFocusability=android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS}
        options.forEach{sourceOptions[it.id]=it}
        val lookup=PlaybackSession.SourceLookup{candidate,done->
            val id=candidate.getString("lazyOptionId");val primed=primedSources.remove(id);val option=sourceOptions[id]
            val task=scope.launch{try{
                val json=withContext(Dispatchers.Default){Contracts.source(primed?:data.resolve(checkNotNull(option)))}
                done(json,null)
            }catch(e:CancellationException){throw e}catch(e:Exception){done(null,e)}}
            AutoCloseable{task.cancel()}
        }
        playbackOverrides=data.playbackPreferences(card);applyPlayerSettings()
        if(sleepMinutes>0&&sleepDeadline==0L)sleepDeadline=android.os.SystemClock.elapsedRealtime()+sleepMinutes*60000L
        playback.start(options.map{JSONObject().put("id",it.id).put("label",it.label).put("lazyOptionId",it.id)},services.network.client,lookup=lookup,resumePositionMs=position)
        playerControls?.title(ContentIdentity.displayTitle(card));mediaButtons.title(ContentIdentity.displayTitle(card));playerControls?.show();playback.appendCandidates(emptyList(),sourceState.value.pending);loadNeighbours(card);if(!foreground)playback.suspendPlayback()
    }
    private fun loadNeighbours(card:Card){
        neighbours=emptyList();playerControls?.episodes(false,false);neighbourJob?.cancel()
        if(card.ref.kind!=ContentKind.EPISODE)return
        if(detailValue?.card?.ref?.kind==ContentKind.SERIES&&card.seriesIdentity==current.item?.identity&&episodeValues.any{it.number==card.episodeNumber}){
            neighbours=episodeValues.sortedWith(compareBy<Episode>{it.number.season}.thenBy{it.number.number})
            val index=episodeIndex(card);playerControls?.episodes(index>0,index>=0&&index<neighbours.lastIndex);return
        }
        val parent=card.seriesRef?:return
        neighbourJob=scope.launch{try{
            val found=data.episodes(Card(parent,card.title,year=card.year)).sortedWith(compareBy<Episode>{it.number.season}.thenBy{it.number.number})
            if(playingCard?.ref?.id!=card.ref.id)return@launch
            neighbours=found;val index=episodeIndex(card)
            playerControls?.episodes(index>0,index>=0&&index<found.lastIndex)
        }catch(e:CancellationException){throw e}catch(e:Exception){playerControls?.episodes(false,false)}}
    }
    private fun episodeIndex(card:Card)=neighbours.indexOfFirst{it.card.ref.id==card.ref.id||card.episodeNumber==it.number}
    private fun nextEpisode(){
        if(adultBlocked)return
        if(sleepExpired()){finishForSleep();return}
        saveProgress();if(!playerPreferences.autoNext)return
        val id=playingCard?.ref?.id?:return
        if(neighbourJob?.isActive==true)scope.launch{
            neighbourJob?.join()
            if(playingCard?.ref?.id==id&&playerPreferences.autoNext&&playback.player?.playWhenReady==true&&
                playback.player?.playbackState==androidx.media3.common.Player.STATE_ENDED)changeEpisode(1)
        }else changeEpisode(1)
    }
    private fun changeEpisode(direction:Int){
        if(adultBlocked)return
        if(sleepExpired()){finishForSleep();return}
        val previous=playingCard?:return
        if(exitOpen||finishBusy||nextEpisodeJob?.isActive==true||!foreground)return
        val index=episodeIndex(previous);val target=neighbours.getOrNull(index+direction).takeIf{index>=0}?:return
        val wasPlaying=playback.player?.playWhenReady==true
        val previousSources=sourceEpisode
        playback.setPlaying(false);playerNotice?.setText(R.string.ux_episode_loading)
        nextEpisodeJob=scope.launch{
            var switched=false
            var discovery:Job?=null
            try{
                val card=target.card.copy(seriesRef=target.card.seriesRef?:previous.seriesRef,episodeNumber=target.number,seriesIdentity=previous.seriesIdentity)
                val item=UnifiedItem(ContentIdentity.key(card),card,(listOf(card)+target.alternatives).distinctBy(SeriesIdentity::sourceKey))
                val state=MutableStateFlow(SourcePool(emptyList(),true))
                discovery=launch{data.episodeSourcePool(item,playerPreferences.crossSiteSources,::backgroundGate).collect{state.value=it}}
                val ready=if(playerPreferences.automaticSources)FirstReadySource.await(state){data.resolve(it)}else{
                    val selected=CompletableDeferred<SourceOption>()
                    val window=overlay(getString(R.string.ux_sources),true,back={selected.cancel()})
                    val painter=launch{state.collect{pool->window.update(sourceRows(pool),message=pool.progress){id->pool.options.firstOrNull{it.id==id&&it.state==OptionState.AVAILABLE}?.let{selected.complete(it)}}}}
                    try{val option=selected.await();option to data.resolve(option)}finally{painter.cancel();window.close()}
                }
                if(!foreground||playingCard?.ref?.id!=previous.ref.id)return@launch
                pendingCheckpoint=data.beginPlayback(card)
                sourceEpisode=item
                cancelSources();sourceOptions.clear();primedSources.clear();sourceState.value=state.value
                sourceState.value.options.forEach{sourceOptions[it.id]=it};primedSources[ready.first.id]=ready.second
                finishBusy=true
                try{withContext(NonCancellable){play(card,listOf(ready.first)+state.value.options.filter{it.state==OptionState.AVAILABLE&&it.id!=ready.first.id},0)}}finally{finishBusy=false}
                switched=playingCard?.ref?.id==card.ref.id
                if(switched){if(current.item?.card?.ref?.kind==ContentKind.EPISODE)current=current.copy(item=UnifiedItem(ContentIdentity.key(card),card,listOf(card)))
                    startSourceUpdates(card,state.value.options,sourceRevision)}
            }catch(e:CancellationException){throw e}catch(e:Exception){playerNotice?.setText(R.string.tv_player_next_failed)}
            finally{discovery?.cancel();if(!switched){sourceEpisode=previousSources;pendingCheckpoint?.let{cp->services.storageScope.launch{data.abandonPlayback(cp)}};pendingCheckpoint=null;if(foreground&&!exitOpen)playback.setPlaying(wasPlaying)}}
        }
    }
    private fun saveProgress(){
        val cp=checkpoint?:return;if(!playback.active||exitOpen||finishBusy)return
        val state=playback.snapshot();val position=state.optLong("positionMs");if(position<=0&&!state.optBoolean("firstFrame"))return
        val duration=playback.player?.duration?.coerceAtLeast(0)?:0
        services.storageScope.launch{try{data.savePlayback(cp,position,duration)}catch(e:CancellationException){throw e}catch(e:Exception){
            android.util.Log.w("FilmStorage","Progress save: "+e.javaClass.simpleName)
            scope.launch{if(foreground)Toast.makeText(this@TvActivity,R.string.tv_storage_save_failed,Toast.LENGTH_SHORT).show()}
        }}
    }
    @Deprecated("TV remote Back")
    @android.annotation.SuppressLint("GestureBackNavigation")
    override fun onBackPressed(){
        settingsScreen?.let{it.back();return}
        if(classicOverlay?.showing==true){if(classicOverlay?.canDismiss?.invoke()==true){if(exitOpen)resumeExit(false)else if(pendingCheckpoint!=null)cancelEntry()else classicOverlay?.close()};return}
        if(playerControls?.handleBack()==true)return
        if(dialog?.isShowing==true){dialog?.dismiss();return}
        if(setupShowing){if(UiPreferences(this).configured)render(TvNav())else finish();return}
        if(playback.active){requestExit();return}
        if(pendingCheckpoint!=null||entryBusy){cancelEntry();return}
        if(stack.isNotEmpty()){
            val previous=stack.removeLast();val savedFocus=focuses[previous.screen];render(previous)
            savedFocus?.let{(row,key)->body.post{rows?.restore(row,key);grid?.restore(key);if(rows!=null)rows?.focusContent()else grid?.focusContent()}}
            return
        }
        if(current.screen!=TvScreen.HOME){navigate(TvNav());return}
        askAppExit()
    }
    private fun askAppExit(){
        if(!settings.value.exitConfirmation){finish();return}
        dialog=ClassicDialog.Builder(this).setMessage(R.string.tv_exit_message).setPositiveButton(R.string.tv_exit){_,_->finish()}
            .setNegativeButton(R.string.tv_cancel,null).create().also{it.setOnDismissListener{navigation[TvScreen.HOME]?.requestFocus()};it.show()}
    }
    override fun onKeyDown(key:Int,event:KeyEvent):Boolean {
        if(!playback.active&&(pendingCheckpoint!=null||entryBusy)&&key==KeyEvent.KEYCODE_MEDIA_STOP){cancelEntry();return true}
        if(playback.active&&key==KeyEvent.KEYCODE_MEDIA_STOP){onBackPressed();return true}
        val p=playback.player
        if(adultBlocked&&playback.active&&key in listOf(KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))return true
        if(playback.active&&p!=null)when(key){
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE->{if(p.playWhenReady)p.pause()else p.play();return true}
            KeyEvent.KEYCODE_MEDIA_PLAY->{p.play();return true}
            KeyEvent.KEYCODE_MEDIA_PAUSE->{p.pause();return true}
            KeyEvent.KEYCODE_MEDIA_STOP->{onBackPressed();return true}
            KeyEvent.KEYCODE_MEDIA_REWIND->{p.seekTo((p.currentPosition-settings.value.player.seekSeconds*1000L).coerceAtLeast(0));return true}
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD->{p.seekTo((p.currentPosition+settings.value.player.seekSeconds*1000L).coerceAtMost(if(p.duration>0)p.duration else Long.MAX_VALUE));return true}
        }
        return super.onKeyDown(key,event)
    }
    override fun onStart(){super.onStart();foreground=true;if(settingsScreen!=null)return;if(started&&!playback.active){if(setupShowing)showInterfaceSetup()else render(current)}else if(started&&playback.active){playingCard?.let{startSourceUpdates(it,sourceOptions.values.toList(),sourceRevision)}};started=true;if(android.os.Build.VERSION.SDK_INT>=24)playback.resumePlayback()}
    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus:Boolean){
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus)window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
    override fun onResume(){super.onResume();foreground=true;if(android.os.Build.VERSION.SDK_INT<=23)playback.resumePlayback()}
    override fun onPause(){if(android.os.Build.VERSION.SDK_INT<=23){foreground=false;saveProgress();playback.suspendPlayback()};super.onPause()}
    override fun onStop(){foreground=false;if(!finishBusy&&(pendingCheckpoint!=null||entryBusy))cancelEntry(false);cancelSources();nextEpisodeJob?.cancel();playerControls?.close();cancelWork();saveProgress();if(android.os.Build.VERSION.SDK_INT>=24)playback.suspendPlayback();super.onStop()}
    override fun onTrimMemory(level:Int){
        super.onTrimMemory(level)
        if(::services.isInitialized){services.trim();Posters.trim(this,level)}
        if(::playback.isInitialized)playback.trimIdle()
        // Preserve the current screen's active pagination state; evict other screens.
        feeds.keys.retainAll(setOf(current.screen));pendingFeeds.keys.retainAll(setOf(current.screen))
        if(current.screen!=TvScreen.HOME){movies=UnifiedPage(emptyList(),emptyMap(),emptyList());series=movies}
        if(current.screen!=TvScreen.DETAIL){detailValue=null;episodeValues=emptyList()}
        if(current.screen!=TvScreen.HOME&&current.screen!=TvScreen.DETAIL)library=emptyList()
    }
    override fun onLowMemory(){super.onLowMemory();onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)}
    override fun onDestroy(){subtitleAppearance?.close();settingsScreen?.dispose();classicOverlay?.close();neighbourJob?.cancel();cancelSources();cancelWork();nextEpisodeJob?.cancel();playerControls?.close();mediaButtons.close();dialog?.dismiss();saveProgress();playback.close();heroImage?.let{Posters.clear(it)};scope.cancel();super.onDestroy()}
    override fun onSaveInstanceState(out:Bundle){
        out.putString("searchType",current.searchType.name);out.putInt("searchPage",current.searchPage)
        out.putBoolean("interfaceSetup",setupShowing)
        out.putString("screen",current.screen.name);out.putString("query",current.query);out.putInt("season",current.season);out.putInt("episodePage",current.episodePage);out.putBoolean("history",current.history);out.putInt("libraryPage",current.libraryPage)
        current.item?.card?.let{c->out.putBundle("card",Bundle().apply{
            putString("site",c.ref.siteId);putString("url",c.ref.pageUrl);putString("kind",c.ref.kind.name)
            putString("title",c.title);putString("poster",c.poster);c.year?.let{putInt("year",it)}
            putString("seriesIdentity",c.seriesIdentity)
            c.episodeNumber?.let{putInt("episodeSeason",it.season);putInt("episodeNumber",it.number)}
            c.seriesRef?.let{parent->putBundle("seriesRef",Bundle().apply{putString("site",parent.siteId);putString("url",parent.pageUrl)})}
        })}
        super.onSaveInstanceState(out)
    }
    private fun readCard(b:Bundle):Card?=runCatching{
        Card(ContentRef(b.getString("site")!!,b.getString("url")!!,ContentKind.valueOf(b.getString("kind")!!)),
            b.getString("title")!!,b.getString("poster").orEmpty(),if(b.containsKey("year"))b.getInt("year")else null,
            seriesIdentity=b.getString("seriesIdentity").orEmpty(),
            episodeNumber=if(b.containsKey("episodeSeason"))EpisodeNumber(b.getInt("episodeSeason"),b.getInt("episodeNumber"))else null,
            seriesRef=b.getBundle("seriesRef")?.let{ContentRef(it.getString("site")!!,it.getString("url")!!,ContentKind.SERIES)})
    }.getOrNull()
    private fun time(ms:Long)=String.format(java.util.Locale.ROOT,"%d:%02d",ms/60000,ms/1000%60)
    internal fun snapshotForUiTest()=playback.snapshot().put("controllerVisible",playerView?.isControllerFullyVisible==true).put("uiDialog",dialog?.isShowing==true).put("playingTitle",playingCard?.title).put("entry",pendingCheckpoint!=null).put("exit",exitOpen).put("classicWindow",classicOverlay?.showing==true)
        .put("screen",current.screen.name).put("detailKind",detailValue?.card?.ref?.kind?.name).put("preparing",entryBusy||pendingCheckpoint!=null)
        .put("poolPending",sourceState.value.pending).put("poolErrorCount",sourceState.value.errors.size)
        .put("subtitleText",playback.player?.currentCues?.cues.orEmpty().mapNotNull{it.text?.toString()}.joinToString("|"))
    internal fun siteDialogForTest()=dialog
    internal fun classicOverlayForTest()=classicOverlay
    internal fun playerOverlayForTest()=playerControls?.overlayForTest()
    internal fun saveForTest()=saveProgress()
}
