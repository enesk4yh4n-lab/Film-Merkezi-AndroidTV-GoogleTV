package app.filmmerkezi.tv.tv

import android.app.Activity
import android.view.View
import android.view.KeyEvent
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.ui.PlayerView
import app.filmmerkezi.tv.PlaybackSession
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal class TvPlayerControls(private val activity:Activity,private val view:PlayerView,
    private val session:PlaybackSession,private val prefs:PlayerPreferences,private val settingsChanged:()->Unit={},
    private val exit:()->Unit={},private val episode:(Int)->Unit={},private val pool:()->SourcePool={SourcePool(emptyList(),false)},
    private val trackChanged:(Int,PlaybackSession.TrackChoice?)->Unit={_,_->},private val sleepState:()->Int={0},private val setSleep:(Int)->Unit={},
    private val seekVisible:(Boolean)->Unit={},private val allSettings:()->Unit={},private val resetContent:()->Unit={},
    private val onSourcesOpened:()->Unit={},private val onRefreshSources:()->Unit={},private val openEpisodes:()->Unit={},
    private val hasEpisodes:()->Boolean={false},private val playbackIdentity:()->String={""},
    private val subtitles:()->SubtitleAppearance?={null}):AutoCloseable {
    private var overlay:ClassicOverlay?=null
    private val feedback=TvSeekFeedback(view,seekVisible)
    private val seekSteps=SeekKeySteps({prefs.repository.value.player.seekSeconds},{prefs.repository.value.player.accelerate})
    private val directSteps=SeekKeySteps({prefs.repository.value.player.seekSeconds},{prefs.repository.value.player.accelerate})
    private var directTarget:Long?=null
    private var directDirection=0
    private var timelineReturn=androidx.media3.ui.R.id.exo_play_pause
    private var menu="";private var type=C.TRACK_TYPE_UNKNOWN
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private val toolScope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
    private var subtitleJob:kotlinx.coroutines.Job?=null
    private var previewJob:kotlinx.coroutines.Job?=null
    private val previewLock=kotlinx.coroutines.sync.Mutex()
    private var lastPreview:PlaybackSession.PreviewFile?=null
    private val refreshTask=object:Runnable{override fun run(){if(overlay?.showing==true){refresh();handler.postDelayed(this,500)}}}
    init {
        view.findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val timeBar=view.findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)
        // Media3 defaults to duration / 20, which can jump minutes on a long film.
        timeBar.setKeyTimeIncrement(prefs.repository.value.player.seekSeconds*1000L)
        timeBar.setOnKeyListener{_,key,event->
            if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)false
            else if(event.action==KeyEvent.ACTION_UP){seekSteps.reset();timeBar.setKeyTimeIncrement(prefs.repository.value.player.seekSeconds*1000L);false}
            else if(event.action==KeyEvent.ACTION_DOWN){
                val step=seekSteps.down(if(key==KeyEvent.KEYCODE_DPAD_RIGHT)1 else -1,event.eventTime,event.repeatCount)
                if(step==null)true else {timeBar.setKeyTimeIncrement(step);false}
            }else false
        }
        bind(R.id.fm_player_rewind){seek(-1)};bind(R.id.fm_player_forward){seek(1)}
        bind(R.id.fm_player_sources){openSources()};bind(R.id.fm_player_quality){tracks(C.TRACK_TYPE_VIDEO)}
        bind(R.id.fm_player_audio){tracks(C.TRACK_TYPE_AUDIO)};bind(R.id.fm_player_subtitles){tracks(C.TRACK_TYPE_TEXT)}
        bind(R.id.fm_player_settings){settings()};bind(R.id.fm_player_exit){exit()}
        bind(R.id.fm_player_previous){episode(-1)};bind(R.id.fm_player_next){episode(1)}
    }
    private fun bind(id:Int,action:()->Unit){view.findViewById<View>(id).setOnClickListener{action()}}
    private fun seek(direction:Int){session.player?.let{p->p.seekTo((p.currentPosition+direction*prefs.repository.value.player.seekSeconds*1000L).coerceIn(0,if(p.duration>0)p.duration else Long.MAX_VALUE))}}
    fun applySettings(){if(overlay?.showing!=true)view.controllerShowTimeoutMs=prefs.repository.value.player.controlsSeconds*1000}
    fun episodes(previous:Boolean,next:Boolean){view.findViewById<View>(R.id.fm_player_previous).apply{isEnabled=previous;isFocusable=previous;alpha=if(previous)1f else 0.3f};view.findViewById<View>(R.id.fm_player_next).apply{isEnabled=next;isFocusable=next;alpha=if(next)1f else 0.3f}}
    fun title(value:String){view.findViewById<TextView>(R.id.fm_player_title).text=value}
    fun show(){feedback.hide();view.controllerAutoShow=false;directSteps.reset();directTarget=null;view.showController();view.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause).requestFocus()}
    /** Route before PlayerView can use left/right to reveal or traverse its controller. */
    fun handleDirectional(event:KeyEvent):Boolean {
        if(overlay?.showing==true)return false
        // Visibility is the mode, including initial display and returning from a panel.
        // A separate flag used to require Down before already visible buttons could navigate.
        val controlsVisible=view.isControllerFullyVisible
        val key=event.keyCode
        val timeline=view.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
        if(key==KeyEvent.KEYCODE_DPAD_DOWN&&event.action==KeyEvent.ACTION_DOWN&&!controlsVisible){
            feedback.hide();directSteps.reset();directTarget=null
            view.showController();view.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause).requestFocus();return true
        }
        // PlayerView otherwise treats this release as a new D-pad event and reopens
        // the controller that the matching Down event has just hidden.
        if(key==KeyEvent.KEYCODE_DPAD_UP&&event.action==KeyEvent.ACTION_UP)return true
        if(key==KeyEvent.KEYCODE_DPAD_UP&&event.action==KeyEvent.ACTION_DOWN&&controlsVisible){
            if(!timeline.hasFocus()){timelineReturn=activity.currentFocus?.id?:androidx.media3.ui.R.id.exo_play_pause;feedback.hide();timeline.requestFocus();return true}
            feedback.hide();view.controllerAutoShow=false;view.hideController();view.requestFocus()
            seekSteps.reset();directSteps.reset();directTarget=null;return true
        }
        if(key==KeyEvent.KEYCODE_DPAD_DOWN&&event.action==KeyEvent.ACTION_DOWN&&controlsVisible){
            if(timeline.hasFocus()||activity.currentFocus===view)(view.findViewById<View>(timelineReturn)?.takeIf{it.isEnabled&&it.isShown&&it.isFocusable}
                ?:view.findViewById(androidx.media3.ui.R.id.exo_play_pause)).requestFocus()
            return true
        }
        if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false
        if(controlsVisible){directSteps.reset();directTarget=null
            if(timeline.hasFocus())return false
            val buttons=listOf(R.id.fm_player_previous,R.id.fm_player_rewind,androidx.media3.ui.R.id.exo_play_pause,R.id.fm_player_forward,
                R.id.fm_player_next,R.id.fm_player_sources,R.id.fm_player_quality,R.id.fm_player_audio,R.id.fm_player_subtitles,R.id.fm_player_settings,R.id.fm_player_exit)
                .map{view.findViewById<View>(it)}.filter{it.isEnabled&&it.isFocusable&&it.isShown}
            val index=buttons.indexOfFirst{it.hasFocus()}
            if(index>=0){if(event.action==KeyEvent.ACTION_DOWN)buttons[(index+if(key==KeyEvent.KEYCODE_DPAD_RIGHT)1 else -1).coerceIn(0,buttons.lastIndex)].requestFocus();return true}
            return false
        }
        if(event.action==KeyEvent.ACTION_UP){directSteps.reset();directTarget=null;return true}
        if(event.action!=KeyEvent.ACTION_DOWN)return true
        val direction=if(key==KeyEvent.KEYCODE_DPAD_RIGHT)1 else -1
        val fresh=event.repeatCount==0||direction!=directDirection
        val step=directSteps.down(direction,event.eventTime,event.repeatCount)?:return true
        directDirection=direction
        view.controllerAutoShow=false;view.hideController();view.requestFocus()
        val player=session.player?:return true
        if(!player.isCurrentMediaItemSeekable)return true
        val base=(if(fresh)player.currentPosition else directTarget?:player.currentPosition).coerceAtLeast(0)
        val remaining=if(player.duration>0)(player.duration-base).coerceAtLeast(0)else Long.MAX_VALUE-base
        val target=if(direction>0)base+minOf(step,remaining)else base-minOf(step,base)
        directTarget=target;player.seekTo(target);feedback.show(target,player.duration,target-base,direction)
        showPreview(target)
        return true
    }
    fun handleBack():Boolean{if(overlay?.showing==true){overlay?.close();return true};return false}
    private fun panel(title:Int,button:Int){
        feedback.hide();overlay?.close();view.controllerShowTimeoutMs=0
        overlay=ClassicOverlay(activity,activity.getString(title),true,view.findViewById(button),media={event->
            if(event.keyCode in listOf(KeyEvent.KEYCODE_MEDIA_PLAY,KeyEvent.KEYCODE_MEDIA_PAUSE,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,KeyEvent.KEYCODE_MEDIA_STOP,KeyEvent.KEYCODE_MEDIA_REWIND,KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,KeyEvent.KEYCODE_MEDIA_NEXT,KeyEvent.KEYCODE_MEDIA_PREVIOUS))activity.dispatchKeyEvent(event)else false
        }).also{window->window.onClosed={if(overlay===window){overlay=null;handler.removeCallbacks(refreshTask);applySettings();view.showController()}}}
        handler.removeCallbacks(refreshTask);handler.postDelayed(refreshTask,500)
    }
    private var sourceFailureMessage=""
    fun openSources(failure:String?=null){
        sourceFailureMessage=failure.orEmpty();menu="sources";panel(R.string.ux_sources,R.id.fm_player_sources)
        if(failure==null)onSourcesOpened()
        refresh()
    }
    fun sourcesChanged(pending:Boolean=false){refresh()}
    fun refresh(){when(menu){"sources"->drawSources();"tracks"->drawTracks();"settings"->drawSettings()}}
    private fun drawSources(){val window=overlay?.takeIf{it.showing}?:return
        val all=session.sourceEntries();val selectedSite=all.firstOrNull{it.selected}?.id?.substringBefore('|');val actual=all.filter{prefs.crossSiteSources||it.id.substringBefore('|')==selectedSite};val entries=actual.associateBy{it.id};val sourcePool=pool()
        val options=sourcePool.options.filter{prefs.crossSiteSources||it.content.siteId==actual.firstOrNull{e->e.selected}?.id?.substringBefore('|')}
        val byId=options.associateBy{it.id}
        val ids=(options.map{it.id}+actual.map{it.id}).distinct();val groups=ids.groupBy{it.substringBefore('|')}
        val rows=buildList{
            for((site,values)in groups){add(ClassicRow("header:$site",Sites.all.firstOrNull{it.id==site}?.name?:site,header=true))
                for(id in values){val choice=entries[id];val option=byId[id]
                    val state=when(choice?.state){"resolving"->R.string.ux_resolving;"ready"->R.string.ux_ready;"playing"->R.string.ux_playing;"paused"->R.string.ux_paused;else->R.string.ux_untried}
                    val explanation=choice?.reason?.takeIf(String::isNotBlank)?:when(choice?.state){
                        "resolving","ready","playing","paused"->activity.getString(state)
                        else->option?.reason?.takeIf(String::isNotBlank)?:activity.getString(state)
                    }
                    add(ClassicRow(id,option?.label?:choice?.label.orEmpty(),explanation,choice?.selected==true,option?.state?.let{it==OptionState.AVAILABLE}?:true))
                }
            }
            sourcePool.errors.forEachIndexed{i,error->
                add(ClassicRow("error:$i:${error.siteId}",Sites.all.firstOrNull{it.id==error.siteId}?.name?:error.siteId,
                    sourceFailureText(activity,error.kind,error.code),enabled=false))
            }
        }
        window.update(rows,actions=buildList{
            add(ClassicAction("refresh",activity.getString(R.string.player_refresh_links)){onRefreshSources()})
            if(session.hasPreviousSource())add(ClassicAction("previous",activity.getString(R.string.player_previous_source)){if(session.returnToPrevious())window.close()})
        },message=listOf(sourceFailureMessage,if(sourcePool.pending)activity.getString(R.string.pool_searching)else "").filter(String::isNotBlank).joinToString(" · ")){id->if(session.chooseSource(id))window.close()}
    }
    private fun language(value:String?):String {
        if(value.isNullOrBlank()||value=="und")return activity.getString(R.string.tv_player_unknown)
        return runCatching{Locale.forLanguageTag(value.replace('_','-')).getDisplayLanguage(Locale.forLanguageTag("tr"))}
            .getOrDefault(value).ifBlank{value}
    }
    private fun label(c:PlaybackSession.TrackChoice,type:Int):String {
        val f=c.format
        val mime=when(f.sampleMimeType){
            "audio/mp4a-latm"->"AAC";"audio/mpeg"->"MP3";"audio/ac3"->"Dolby Digital"
            "audio/eac3","audio/eac3-joc"->"Dolby Digital Plus";"audio/vnd.dts","audio/vnd.dts.hd"->"DTS"
            "video/avc"->"H.264";"video/hevc"->"HEVC";"video/x-vnd.on2.vp9"->"VP9"
            else->f.sampleMimeType?.substringAfter('/')?.uppercase(Locale.ROOT).orEmpty()
        }
        return if(type==C.TRACK_TYPE_VIDEO)listOfNotNull(
            f.height.takeIf{it>0}?.let{it.toString()+"p"},
            f.bitrate.takeIf{it>0}?.let{String.format(Locale.ROOT,"%.1f Mbps",it/1000000.0)},mime.takeIf{it.isNotBlank()}).joinToString(" · ")
        else listOfNotNull(language(f.language),f.label?.takeIf{it.isNotBlank()},
            if(type==C.TRACK_TYPE_AUDIO&&f.channelCount>0)activity.getString(R.string.tv_player_channels,f.channelCount)else null,
            mime.takeIf{type==C.TRACK_TYPE_AUDIO&&it.isNotBlank()}).distinct().joinToString(" · ")
    }
    private fun tracks(trackType:Int){menu="tracks";type=trackType
        panel(when(type){C.TRACK_TYPE_VIDEO->R.string.tv_player_quality;C.TRACK_TYPE_AUDIO->R.string.tv_player_audio;else->R.string.tv_player_subtitles},when(type){C.TRACK_TYPE_VIDEO->R.id.fm_player_quality;C.TRACK_TYPE_AUDIO->R.id.fm_player_audio;else->R.id.fm_player_subtitles});drawTracks()
    }
    private var choices=emptyMap<String,PlaybackSession.TrackChoice>()
    private fun drawTracks(){val window=overlay?.takeIf{it.showing}?:return
        val tracks=session.trackChoices(type)
        val mapped=tracks.mapIndexed{i,t->"track:${t.revision}:${type}:${t.group.hashCode()}:${t.index}:$i" to t}.toMap();choices=mapped
        val first=when(type){C.TRACK_TYPE_VIDEO->R.string.tv_player_auto;C.TRACK_TYPE_AUDIO->R.string.settings_global_preferences;else->R.string.tv_player_off}
        val isDefault=type==C.TRACK_TYPE_VIDEO&&session.automaticQuality||type==C.TRACK_TYPE_TEXT&&(!session.subtitlesEnabled||tracks.none{it.selected})||type==C.TRACK_TYPE_AUDIO&&tracks.none{it.selected}
        val current=session.player?.videoFormat?.height?.takeIf{it>0}?.let{activity.getString(R.string.ux_current_quality,"${it}p")}.orEmpty()
        val rows=listOf(ClassicRow("default",activity.getString(first),if(type==C.TRACK_TYPE_VIDEO)current else "",isDefault))+mapped.map{(key,c)->ClassicRow(key,label(c,type),selected=!isDefault&&c.selected)}
        val actions=if(type==C.TRACK_TYPE_TEXT&&session.selectedSubtitleFile()!=null&&session.player?.isCurrentMediaItemLive==false)
            listOf(ClassicAction("subtitle-sync",activity.getString(R.string.player_subtitle_sync)){showSubtitleOffset()})else emptyList()
        window.update(rows,actions=actions,message=if(tracks.isEmpty())activity.getString(when(type){C.TRACK_TYPE_TEXT->R.string.ux_no_subtitles;C.TRACK_TYPE_VIDEO->R.string.ux_no_quality;else->R.string.tv_player_no_tracks})else ""){key->
            val selected=if(key=="default")null else choices[key]
            if(key!="default"&&selected==null||!session.chooseTrack(type,selected)){window.update(rows,message=activity.getString(R.string.tv_player_track_changed));handler.post{drawTracks()}}
            else {if(type==C.TRACK_TYPE_TEXT)subtitles()?.clearOffset();trackChanged(type,selected);window.close()}
        }
    }
    private fun settings(){menu="settings";panel(R.string.tv_player_settings,R.id.fm_player_settings);drawSettings()}
    private fun drawSettings(){val window=overlay?.takeIf{it.showing}?:return
        val v=prefs.repository.value
        val rows=buildList{
            add(ClassicRow("goto",activity.getString(R.string.player_goto),enabled=session.player?.isCurrentMediaItemSeekable==true))
            if(hasEpisodes())add(ClassicRow("episodes",activity.getString(R.string.classic_episodes)))
            add(ClassicRow("speed",activity.getString(R.string.player_speed),"${session.player?.playbackParameters?.speed?:1f}×"))
            add(ClassicRow("bookmarks",activity.getString(R.string.player_bookmarks)))
            if(session.navigation.chapters.isNotEmpty())add(ClassicRow("chapters",activity.getString(R.string.player_chapters)))
            add(ClassicRow("loop",activity.getString(R.string.player_loop),session.loopStart?.let{clock(it)+(session.loopEnd?.let{end->" – "+clock(end)}?:"")}.orEmpty()))
            add(ClassicRow("diagnostics",activity.getString(R.string.tv_player_diagnostics),selected=v.diagnostics))
            add(ClassicRow("next",activity.getString(R.string.tv_player_autonext),selected=v.autoNext))
            add(ClassicRow("auto",activity.getString(R.string.pool_automatic),selected=v.automaticSources))
            add(ClassicRow("cross",activity.getString(R.string.pool_cross_site),selected=v.crossSiteSources))
            add(ClassicRow("sleep",activity.getString(R.string.settings_sleep),sleepLabel(sleepState())))
            add(ClassicRow("all",activity.getString(R.string.settings_title)))
            add(ClassicRow("reset-content",activity.getString(R.string.settings_reset_this)))
        }
        window.update(rows){id->if(id=="all"){window.close();allSettings()}else if(id=="reset-content"){window.close();resetContent()}else if(id=="sleep"){
            menu="sleep"
            val minutes=listOf(0,15,30,60,90,120,-1)
            window.update(minutes.map{ClassicRow("sleep:$it",sleepLabel(it),selected=it==sleepState())}){key->setSleep(key.substringAfter(':').toInt());menu="settings";drawSettings()}
        }else if(id=="episodes"){window.close();openEpisodes()}
        else if(id=="goto"){window.close();gotoTime()}
        else if(id=="speed"){showSpeed()}
        else if(id=="bookmarks"){showBookmarks()}
        else if(id=="chapters"){showChapters()}
        else if(id=="loop"){showLoop()
        }else saveSettings({s->when(id){"diagnostics"->s.copy(diagnostics=!s.diagnostics);"next"->s.copy(autoNext=!s.autoNext);"auto"->s.copy(automaticSources=!s.automaticSources);"cross"->s.copy(crossSiteSources=!s.crossSiteSources);else->s}}){settingsChanged();drawSettings()}}
    }

    private fun showSubtitleOffset(){
        val window=overlay?:return
        menu="subtitle-sync"
        val appearance=subtitles()?:return
        val offset=appearance.offsetMs
        window.update(listOf(
            ClassicRow("earlier",activity.getString(R.string.player_subtitle_earlier)),
            ClassicRow("later",activity.getString(R.string.player_subtitle_later)),
            ClassicRow("reset",activity.getString(R.string.player_subtitle_reset))
        ),message=String.format(Locale.ROOT,"%+.1f s",offset/1000.0)){key->
            if(subtitleJob?.isActive==true)return@update
            val next=when(key){"earlier"->offset-500;"later"->offset+500;else->0}.coerceIn(-10000,10000)
            subtitleJob=toolScope.launch{
                val saved=try{appearance.setOffset(next,session::selectedSubtitleFile)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){false}
                if(window.showing){
                    if(!saved)toast(R.string.player_subtitle_unavailable)
                    showSubtitleOffset()
                }
            }
        }
    }

    private fun sleepLabel(value:Int)=when(value){0->activity.getString(R.string.settings_off);-1->activity.getString(R.string.player_sleep_end)
        else->activity.getString(R.string.settings_minutes,value)}

    private fun clock(ms:Long):String{
        val seconds=ms.coerceAtLeast(0L)/1000L
        return String.format(Locale.ROOT,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)
    }

    private fun gotoTime(){
        val player=session.player?:return
        if(!player.isCurrentMediaItemSeekable)return
        val input=android.widget.EditText(activity).apply{
            inputType=android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_TIME
            setTextColor(ClassicUi.foreground);setSingleLine();setText(clock(player.currentPosition));selectAll()
        }
        ClassicDialog.Builder(activity).setTitle(R.string.player_goto).setMessage(R.string.player_goto_format).setView(input)
            .setNegativeButton(R.string.tv_cancel,null).setPositiveButton(android.R.string.ok){_,_->
                val parts=input.text.toString().trim().split(':')
                val values=parts.map{it.toLongOrNull()}
                val seconds=if(values.size==3&&values.all{it!=null}&&values[0]!! in 0..999&&values[1]!! in 0..59&&values[2]!! in 0..59)
                    3600L*values[0]!!+60L*values[1]!!+values[2]!! else -1L
                if(seconds<0||player.duration>0&&seconds*1000L>player.duration)toast(R.string.player_goto_invalid)
                else if(player===session.player)player.seekTo(seconds*1000L)
            }.show()
    }

    private fun showSpeed(){
        val window=overlay?:return
        menu="speed"
        val values=listOf(0.5f,0.75f,1f,1.25f,1.5f,1.75f,2f)
        window.update(values.map{ClassicRow(it.toString(),"$it×",selected=session.player?.playbackParameters?.speed==it)}){id->
            try{session.setSpeed(id.toFloat());showSpeed()}catch(_:RuntimeException){session.setSpeed(1f);toast(R.string.player_speed_failed)}
        }
    }

    private val bookmarks=activity.applicationContext.getSharedPreferences("player-bookmarks",0)
    private fun showBookmarks(){
        val window=overlay?:return
        val identity=playbackIdentity().takeIf{it.isNotBlank()}?:return
        val key=TextKey.hash(identity)
        menu="bookmarks"
        val times=bookmarks.getString(key,"").orEmpty().split('|').mapNotNull(String::toLongOrNull).filter{it>=0L}.sorted().take(20)
        fun save(entries:List<Long>){
            toolScope.launch{
                val ok=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){bookmarks.edit().putString(key,entries.distinct().sorted().joinToString("|")).commit()}
                if(!ok)toast(R.string.settings_saved_error)
                if(window.showing)showBookmarks()
            }
        }
        window.update(times.map{ClassicRow(it.toString(),clock(it))},actions=listOf(ClassicAction("mark",activity.getString(R.string.player_add_bookmark),times.size<20){
            session.player?.currentPosition?.takeIf{it>=0L}?.let{save(times+it)}
        })){id->
            val time=id.toLong()
            window.update(listOf(ClassicRow("open",activity.getString(R.string.player_goto)),ClassicRow("delete",activity.getString(R.string.tv_remove))),message=clock(time)){action->
                if(action=="open"){session.player?.takeIf{it.isCurrentMediaItemSeekable}?.seekTo(time);window.close()}else save(times-time)
            }
        }
    }

    private fun showLoop(){
        val window=overlay?:return
        menu="loop"
        val markingEnd=session.loopStart!=null&&session.loopEnd==null
        window.update(listOf(
            ClassicRow("mark",activity.getString(if(markingEnd)R.string.player_loop_end else R.string.player_loop_start)),
            ClassicRow("clear",activity.getString(R.string.player_loop_clear),enabled=session.loopStart!=null)
        )){key->
            if(key=="clear")session.clearLoop()else if(!session.markLoop())toast(R.string.player_loop_invalid)
            showLoop()
        }
    }

    private fun showChapters(){
        val window=overlay?:return
        val player=session.player?.takeIf{it.isCurrentMediaItemSeekable}?:return
        menu="chapters"
        val chapters=session.navigation.chapters.filter{player.duration<=0||it.time<player.duration}
        val intro=chapters.firstOrNull{it.intro&&it.end!=null&&it.end>player.currentPosition&&it.time<=player.currentPosition}
        val actions=if(intro==null)emptyList()else listOf(ClassicAction("intro",activity.getString(R.string.player_skip_intro)){
            if(player===session.player){player.seekTo(checkNotNull(intro.end).coerceAtMost(player.duration.takeIf{it>0}?:Long.MAX_VALUE));window.close()}
        })
        window.update(chapters.mapIndexed{i,c->ClassicRow(i.toString(),c.title.ifBlank{activity.getString(R.string.player_skip_intro)},clock(c.time))},actions=actions){key->
            if(player===session.player){player.seekTo(chapters[key.toInt()].time);window.close()}
        }
    }

    private fun showPreview(time:Long){
        val preview=session.previewAt(time)
        if(lastPreview==preview&&previewJob?.isActive==true)return
        lastPreview=preview
        previewJob?.cancel()
        feedback.setPreview(null)
        if(preview==null)return
        previewJob=toolScope.launch{
            kotlinx.coroutines.delay(180L)
            val image=try{previewLock.withLock{kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){readPreview(preview)}}}
                catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){null}
            if(session.previewAt(time)==preview)feedback.setPreview(image)
        }
    }

    @Suppress("DEPRECATION")
    private fun readPreview(file:PlaybackSession.PreviewFile):android.graphics.Bitmap? {
        val data=java.io.ByteArrayOutputStream()
        val spec=androidx.media3.datasource.DataSpec(android.net.Uri.parse(file.preview.url))
        androidx.media3.datasource.DataSourceInputStream(file.factory.createDataSource(),spec).use{input->
            val buffer=ByteArray(8192)
            while(true){
                val n=input.read(buffer)
                if(n<0)break
                if(data.size()+n>512*1024)return null
                data.write(buffer,0,n)
            }
        }
        val bytes=data.toByteArray()
        val region=file.preview.region
        if(region!=null){
            val decoder=android.graphics.BitmapRegionDecoder.newInstance(bytes,0,bytes.size,false)?:return null
            return try{
                if(region.right>decoder.width||region.bottom>decoder.height)null
                else decoder.decodeRegion(region,previewOptions(region.width(),region.height()))
            }finally{decoder.recycle()}
        }
        val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
        android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if(bounds.outWidth !in 1..4096||bounds.outHeight !in 1..4096)return null
        return android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,previewOptions(bounds.outWidth,bounds.outHeight))
    }

    private fun previewOptions(width:Int,height:Int)=android.graphics.BitmapFactory.Options().apply{
        inPreferredConfig=android.graphics.Bitmap.Config.RGB_565
        inSampleSize=1
        while(width/inSampleSize>360||height/inSampleSize>200)inSampleSize*=2
    }

    private fun toast(id:Int){android.widget.Toast.makeText(activity,id,android.widget.Toast.LENGTH_SHORT).show()}
    private fun saveSettings(change:(app.filmmerkezi.tv.settings.AppSettings)->app.filmmerkezi.tv.settings.AppSettings,done:()->Unit){
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try{prefs.repository.update(change);done()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){android.widget.Toast.makeText(activity,R.string.settings_saved_error,android.widget.Toast.LENGTH_LONG).show()}
        }
    }
    override fun close(){subtitleJob?.cancel();previewJob?.cancel();lastPreview=null;feedback.close();seekSteps.reset();directSteps.reset();directTarget=null;handler.removeCallbacks(refreshTask);overlay?.close();overlay=null}
    internal fun overlayForTest()=overlay
}
