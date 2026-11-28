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

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal class TvPlayerControls(private val activity:Activity,private val view:PlayerView,
    private val session:PlaybackSession,private val prefs:PlayerPreferences,private val settingsChanged:()->Unit={},
    private val exit:()->Unit={},private val episode:(Int)->Unit={},private val pool:()->SourcePool={SourcePool(emptyList(),false)},
    private val trackChanged:(Int,PlaybackSession.TrackChoice?)->Unit={_,_->},private val sleepState:()->Int={0},private val setSleep:(Int)->Unit={},
    private val seekVisible:(Boolean)->Unit={},private val allSettings:()->Unit={},private val resetContent:()->Unit={}):AutoCloseable {
    private var overlay:ClassicOverlay?=null
    private val feedback=TvSeekFeedback(view,seekVisible)
    private val seekSteps=SeekKeySteps({prefs.repository.value.player.seekSeconds},{prefs.repository.value.player.accelerate})
    private val directSteps=SeekKeySteps({prefs.repository.value.player.seekSeconds},{prefs.repository.value.player.accelerate})
    private var directTarget:Long?=null
    private var directDirection=0
    private var timelineReturn=androidx.media3.ui.R.id.exo_play_pause
    private var menu="";private var type=C.TRACK_TYPE_UNKNOWN
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
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
    fun openSources(){menu="sources";panel(R.string.ux_sources,R.id.fm_player_sources);refresh()}
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
                add(ClassicRow("error:$i:${error.siteId}",Sites.all.firstOrNull{it.id==error.siteId}?.name?:error.siteId,error.message,enabled=false))
            }
        }
        window.update(rows,message=if(sourcePool.pending)activity.getString(R.string.pool_searching)else ""){id->if(session.chooseSource(id))window.close()}
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
        window.update(rows,message=if(tracks.isEmpty())activity.getString(when(type){C.TRACK_TYPE_TEXT->R.string.ux_no_subtitles;C.TRACK_TYPE_VIDEO->R.string.ux_no_quality;else->R.string.tv_player_no_tracks})else ""){key->
            val selected=if(key=="default")null else choices[key]
            if(key!="default"&&selected==null||!session.chooseTrack(type,selected)){window.update(rows,message=activity.getString(R.string.tv_player_track_changed));handler.post{drawTracks()}}
            else {trackChanged(type,selected);window.close()}
        }
    }
    private fun settings(){menu="settings";panel(R.string.tv_player_settings,R.id.fm_player_settings);drawSettings()}
    private fun drawSettings(){val window=overlay?.takeIf{it.showing}?:return
        val v=prefs.repository.value
        val rows=listOf(ClassicRow("diagnostics",activity.getString(R.string.tv_player_diagnostics),selected=v.diagnostics),ClassicRow("next",activity.getString(R.string.tv_player_autonext),selected=v.autoNext),ClassicRow("auto",activity.getString(R.string.pool_automatic),selected=v.automaticSources),ClassicRow("cross",activity.getString(R.string.pool_cross_site),selected=v.crossSiteSources),
            ClassicRow("sleep",activity.getString(R.string.settings_sleep),if(sleepState()==0)activity.getString(R.string.settings_off)else activity.getString(R.string.settings_minutes,sleepState())),
            ClassicRow("all",activity.getString(R.string.settings_title)),ClassicRow("reset-content",activity.getString(R.string.settings_reset_this)))
        window.update(rows){id->if(id=="all"){window.close();allSettings()}else if(id=="reset-content"){window.close();resetContent()}else if(id=="sleep"){
            val minutes=listOf(0,30,60,90,120)
            window.update(minutes.map{ClassicRow("sleep:$it",if(it==0)activity.getString(R.string.settings_off)else activity.getString(R.string.settings_minutes,it),selected=it==sleepState())}){key->setSleep(key.substringAfter(':').toInt());drawSettings()}
        }else saveSettings({s->when(id){"diagnostics"->s.copy(diagnostics=!s.diagnostics);"next"->s.copy(autoNext=!s.autoNext);"auto"->s.copy(automaticSources=!s.automaticSources);"cross"->s.copy(crossSiteSources=!s.crossSiteSources);else->s}}){settingsChanged();drawSettings()}}
    }
    private fun saveSettings(change:(app.filmmerkezi.tv.settings.AppSettings)->app.filmmerkezi.tv.settings.AppSettings,done:()->Unit){
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try{prefs.repository.update(change);done()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){android.widget.Toast.makeText(activity,R.string.settings_saved_error,android.widget.Toast.LENGTH_LONG).show()}
        }
    }
    override fun close(){feedback.close();seekSteps.reset();directSteps.reset();directTarget=null;handler.removeCallbacks(refreshTask);overlay?.close();overlay=null}
    internal fun overlayForTest()=overlay
}
