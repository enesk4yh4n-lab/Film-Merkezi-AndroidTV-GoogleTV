package app.filmmerkezi.tv.tv

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Foreground OS key routing only. No service, notification or second playback engine. */
internal class TvMediaButtons(context:Context,private val playing:(Boolean)->Unit,private val stop:()->Unit,private val episode:(Int)->Unit={},private val seekSeconds:()->Int={10}):AutoCloseable {
    private val app=context.applicationContext
    private var session:MediaSession?=null
    private var player:ExoPlayer?=null
    private var title=""
    private val listener=object:Player.Listener{
        override fun onEvents(player:Player,events:Player.Events){update()}
    }
    @Suppress("DEPRECATION")
    fun bind(value:ExoPlayer?){
        if(value===player){update();return}
        close();if(value==null)return
        player=value;value.addListener(listener)
        session=MediaSession(app,"FilmMerkeziPlayback").apply{
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object:MediaSession.Callback(){
                override fun onPlay(){playing(true)}
                override fun onPause(){playing(false)}
                override fun onStop(){stop()}
                override fun onSkipToNext(){episode(1)}
                override fun onSkipToPrevious(){episode(-1)}
                override fun onSeekTo(position:Long){player?.seekTo(position.coerceAtLeast(0))}
                override fun onFastForward(){seek(1)}
                override fun onRewind(){seek(-1)}
                override fun onMediaButtonEvent(intent:Intent):Boolean {
                    val key=intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if(key?.keyCode==KeyEvent.KEYCODE_MEDIA_STOP){
                        if(key.action==KeyEvent.ACTION_DOWN)stop()
                        return true
                    }
                    return super.onMediaButtonEvent(intent)
                }
            },Handler(Looper.getMainLooper()))
            isActive=true
        }
        update()
    }
    fun title(value:String){title=value;update()}
    private fun seek(direction:Int){player?.let{p->p.seekTo((p.currentPosition+direction*seekSeconds()*1000L).coerceIn(0,if(p.duration>0)p.duration else Long.MAX_VALUE))}}
    private fun update(){
        val p=player?:return;val s=session?:return
        val state=when(p.playbackState){
            Player.STATE_BUFFERING->PlaybackState.STATE_BUFFERING
            Player.STATE_ENDED->PlaybackState.STATE_STOPPED
            Player.STATE_READY->if(p.isPlaying)PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            else->PlaybackState.STATE_NONE
        }
        s.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(state,p.currentPosition,if(p.isPlaying)1f else 0f).build())
        s.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title)
            .putLong(MediaMetadata.METADATA_KEY_DURATION,p.duration.coerceAtLeast(0)).build())
    }
    override fun close(){
        player?.removeListener(listener);player=null
        session?.isActive=false;session?.release();session=null
    }
}
