package app.filmmerkezi.tv

import android.app.Activity
import app.filmmerkezi.tv.tv.ClassicDialog
import android.os.Bundle
import android.view.View
import android.view.KeyEvent
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import app.filmmerkezi.tv.core.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import org.json.JSONObject

/** Stage-1 diagnostic UI. The Leanback product screens remain Stage 2. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class NativeMainActivity:Activity() {
    private class Services(context:android.content.Context) {
        val network=ProviderNetwork(context);val catalog=CatalogRepository(network)
        val resolver=SourceResolver(network);val library=LibraryStore(context)
        val storageScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    }
    companion object {
        @Volatile private var services:Services?=null
        private fun services(context:android.content.Context):Services=services?:synchronized(this){services?:Services(context.applicationContext).also{services=it}}
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private lateinit var data:Services
    private lateinit var host:FrameLayout
    private lateinit var panel:LinearLayout
    private lateinit var status:TextView
    private lateinit var list:ListView
    private lateinit var adapter:ArrayAdapter<String>
    private var actions=emptyList<Pair<String,()->Unit>>()
    private var work:Job?=null
    private var generation=0
    private var foreground=false
    private lateinit var playback:PlaybackSession
    private var playerView:PlayerView?=null
    private var playerHost:FrameLayout?=null
    private var playerNotice:TextView?=null
    private var lastDetail:Detail?=null
    private var playingCard:Card?=null
    private var isHome=true
    private var returnTo:(()->Unit)?=null

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        data=services(this);host=FrameLayout(this);setContentView(host)
        playback=PlaybackSession(this,object:PlaybackSession.Listener {
            override fun onEngineChanged(player:ExoPlayer?){playerView?.player=player}
            override fun onMessage(resource:Int,label:String){playerNotice?.text=getString(resource,label)}
        })
        home()
        scope.launch {
            while(isActive){delay(15000);if(foreground&&playback.active)saveProgress()}
        }
    }
    private fun screen(title:String) {
        generation++;work?.cancel();work=null
        if(::panel.isInitialized)host.removeView(panel)
        panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(28,16,28,12);setBackgroundColor(Color.BLACK)}
        status=TextView(this).apply{text=title;textSize=19f;setTextColor(Color.WHITE);setPadding(0,0,0,8)}
        panel.addView(status)
        list=ListView(this).apply{isFocusable=true;dividerHeight=6;setBackgroundColor(Color.BLACK)}
        adapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,mutableListOf<String>())
        list.adapter=adapter
        list.setOnItemClickListener{_,_,position,_->actions.getOrNull(position)?.second?.invoke()}
        panel.addView(list,LinearLayout.LayoutParams(-1,0,1f))
        host.addView(panel,FrameLayout.LayoutParams(-1,-1))
        playerView?.apply{keepScreenOn=false;isFocusable=false;useController=false;descendantFocusability=android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS}
        playerHost?.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    private fun rows(values:List<Pair<String,()->Unit>>) {
        val selected=list.selectedItemPosition.coerceAtLeast(0)
        actions=values.take(100);adapter.clear();adapter.addAll(actions.map{it.first});adapter.notifyDataSetChanged()
        if(actions.isNotEmpty())list.setSelection(selected.coerceAtMost(actions.lastIndex))
    }
    private fun task(block:suspend()->Unit) {
        work?.cancel();val token=generation
        work=scope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:Exception){if(token==generation)status.text=e.message}}
    }
    private fun home() {
        isHome=true;returnTo=null;lastDetail=null
        screen(getString(R.string.native_notice))
        val controls=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val query=EditText(this).apply{hint=getString(R.string.native_query_hint);isSingleLine=true;maxLines=1}
        panel.addView(query,1,LinearLayout.LayoutParams(-1,-2))
        fun button(id:Int,action:()->Unit){controls.addView(Button(this).apply{setText(id);setOnClickListener{action()}})}
        button(R.string.native_search){
            val q=query.text.toString()
            task {data.library.rememberSearch(q);data.catalog.observeSearch(q).collect{showPage(it)}}
        }
        button(R.string.native_browse){task{data.catalog.observeBrowse().collect{showPage(it)}}}
        button(R.string.native_library){library()}
        panel.addView(controls,2,LinearLayout.LayoutParams(-1,-2))
        rows(emptyList());controls.getChildAt(0).requestFocus()
    }
    private fun showPage(page:UnifiedPage) {
        status.text=getString(R.string.native_results,page.items.size,page.failures.size)
        rows(page.items.take(70).map{item->
            (item.card.title+" · "+(item.card.year?.toString()?:"")+" · "+item.alternatives.size) to {
                if(item.alternatives.size==1)open(item.card)
                else ClassicDialog.Builder(this).setTitle(item.card.title).setItems(item.alternatives.map{Sites.get(it.ref.siteId).name+" · "+it.language}.toTypedArray()){_,i->open(item.alternatives[i])}.setNegativeButton(R.string.cancel,null).show()
                Unit
            }
        }+page.failures.map{f->getString(R.string.native_site_error,Sites.get(f.siteId).name,f.message) to {}})
    }
    private fun open(card:Card) {
        isHome=false;returnTo={home()};screen(getString(R.string.native_loading));val token=generation
        task {val detail=data.catalog.detail(card);if(token==generation)detail(detail)}
    }
    private fun detail(value:Detail) {
        lastDetail=value;isHome=false
        screen(value.card.title+"\n"+value.description.take(500))
        val available=value.options.filter{it.state==OptionState.AVAILABLE}
        val options=value.options.map{o->(o.label+if(o.reason.isBlank())""else" · "+o.reason) to {
            if(o.state!=OptionState.AVAILABLE)status.text=o.reason else {
                val ordered=listOf(o)+available.filter{it.id!=o.id}
                task {val saved=data.library.records().find{it.identity==ContentIdentity.key(value.card)}
                    play(value.card,ordered,saved?.positionMs?:0)}
            }
        }}
        val listing=listOf(getString(R.string.native_add_list) to {task{data.library.update(value.card,listed=true);status.setText(R.string.native_saved)}})
        val episodes=if(value.card.ref.kind==ContentKind.SERIES)listOf(getString(R.string.native_episodes) to {task{val eps=data.catalog.episodes(value.card);episodeRows(value,eps)}})else emptyList()
        rows(listing+episodes+options+if(options.isEmpty()&&episodes.isEmpty())listOf(getString(R.string.native_no_sources) to {})else emptyList())
        list.requestFocus()
    }
    private fun episodeRows(parent:Detail,episodes:List<Episode>) {
        screen(parent.card.title);isHome=false;returnTo={detail(parent)}
        rows(episodes.take(100).map{e->"${e.number.season}. Sezon ${e.number.number}. Bölüm · ${e.card.title}" to {open(e.card)}})
        list.requestFocus()
    }
    private fun library() {
        isHome=false;returnTo={home()};screen(getString(R.string.native_library))
        task{
            val records=data.library.records()
            rows(records.map{r->((if(r.listed)"★ "else"")+"${r.card.title} · ${r.positionMs/60000} dk") to {
                ClassicDialog.Builder(this).setTitle(r.card.title).setItems(arrayOf(getString(R.string.native_detail),getString(if(r.listed)R.string.native_remove_list else R.string.native_add_list))){_,i->
                    if(i==0)open(r.card)else task{data.library.update(r.card,listed=!r.listed);library()}
                }.setNegativeButton(R.string.cancel,null).show()
                Unit
            }})
            if(records.isEmpty())status.setText(R.string.native_empty);list.requestFocus()
        }
    }
    private fun showPlayer() {
        if(playerView==null){
            playerHost=FrameLayout(this).also{root->
                playerView=PlayerView(this).apply{setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)}
                root.addView(playerView,FrameLayout.LayoutParams(-1,-1))
                playerNotice=TextView(this).apply{textSize=16f;setTextColor(Color.WHITE);setBackgroundColor(0xA0000000.toInt());setPadding(12,6,12,6)}
                root.addView(playerNotice,FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START))
                host.addView(root,0,FrameLayout.LayoutParams(-1,-1))
            }
        }
        host.removeView(panel)
        playerHost?.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        playerView!!.apply{keepScreenOn=true;isFocusable=true;useController=true;descendantFocusability=android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS}
    }
    private fun play(card:Card,options:List<SourceOption>,position:Long) {
        if(!foreground||options.isEmpty())return
        work?.cancel();work=null
        saveProgress();playback.stopPlayback();playingCard=card
        showPlayer()
        val byId=options.associateBy{it.id}
        val lookup=PlaybackSession.SourceLookup { candidate,done->
            val job=scope.launch {
                try {val option=checkNotNull(byId[candidate.getString("lazyOptionId")])
                    val json=withContext(Dispatchers.Default){
                        val source=data.resolver.resolve(option)
                        if(source.type==StreamType.DASH)throw DataFailure(FailureKind.UNSUPPORTED,"DASH bu deney paketinde etkin değil","player")
                        Contracts.source(source)
                    }
                    done(json,null)
                }catch(e:CancellationException){throw e}catch(e:Exception){done(null,e)}
            }
            AutoCloseable{job.cancel()}
        }
        playback.start(options.map{JSONObject().put("id",it.id).put("label",it.label).put("lazyOptionId",it.id)},data.network.client,lookup=lookup,resumePositionMs=position)
        playerView!!.requestFocus()
    }
    private fun saveProgress() {
        val card=playingCard?:return
        if(!playback.active)return
        val snapshot=playback.snapshot();val duration=playback.player?.duration?.coerceAtLeast(0)?:0
        if(snapshot.optLong("positionMs")<=0&&!snapshot.optBoolean("firstFrame"))return
        data.storageScope.launch {try{data.library.update(card,positionMs=snapshot.optLong("positionMs"),durationMs=duration)}
            catch(e:Exception){android.util.Log.w("FilmStorage","Progress could not be stored: "+e.javaClass.simpleName)}}
    }
    fun playbackSnapshotForTest()=playback.snapshot()
    internal fun playbackSessionForTest()=playback
    internal fun startSourcesWithClientForTest(sources:List<JSONObject>,http:okhttp3.OkHttpClient)=startSourcesForTest(sources,http=http)
    internal fun seekPlaybackForTest(position:Long){playback.player?.seekTo(position)}
    internal fun pausePlaybackForTest(){playback.player?.pause()}
    internal fun resumePlaybackForTest(){playback.player?.play()}
    internal fun startSourcesForTest(sources:List<JSONObject>,lookup:PlaybackSession.SourceLookup?=null,http:okhttp3.OkHttpClient?=null){showPlayer();playback.diagnosticsEnabled=true;playback.start(sources,http?:data.network.client,lookup=lookup)}
    internal fun stopSourcesForTest(){playback.stopPlayback();home()}
    @Deprecated("TV remote back; predictive gesture disabled in manifest")
    @android.annotation.SuppressLint("GestureBackNavigation")
    override fun onBackPressed(){
        if(playback.active){saveProgress();playback.stopPlayback();playingCard=null;lastDetail?.let{detail(it)}?:home()}
        else if(!isHome){returnTo?.invoke()?:home()}
        else ClassicDialog.Builder(this).setMessage(R.string.native_exit_question).setPositiveButton(R.string.native_exit){_,_->finish()}.setNegativeButton(R.string.cancel,null).show()
    }
    override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean {
        val p=playback.player
        if(playback.active && p!=null)when(keyCode){
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE->{if(p.isPlaying)p.pause()else p.play();return true}
            KeyEvent.KEYCODE_MEDIA_PLAY->{p.play();return true}
            KeyEvent.KEYCODE_MEDIA_PAUSE->{p.pause();return true}
            KeyEvent.KEYCODE_MEDIA_STOP->{onBackPressed();return true}
            KeyEvent.KEYCODE_MEDIA_REWIND->{p.seekBack();return true}
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD->{p.seekForward();return true}
        }
        return super.onKeyDown(keyCode,event)
    }
    override fun onStart(){super.onStart();foreground=true;if(android.os.Build.VERSION.SDK_INT>=24)playback.resumePlayback()}
    override fun onResume(){super.onResume();foreground=true;if(android.os.Build.VERSION.SDK_INT<=23)playback.resumePlayback()}
    override fun onPause(){if(android.os.Build.VERSION.SDK_INT<=23){foreground=false;saveProgress();playback.suspendPlayback()};super.onPause()}
    override fun onStop(){foreground=false;work?.cancel();saveProgress();if(android.os.Build.VERSION.SDK_INT>=24)playback.suspendPlayback();super.onStop()}
    override fun onTrimMemory(level:Int){super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW){data.catalog.trim();playback.trimIdle()}}
    override fun onDestroy(){saveProgress();playback.close();scope.cancel();super.onDestroy()}
}
