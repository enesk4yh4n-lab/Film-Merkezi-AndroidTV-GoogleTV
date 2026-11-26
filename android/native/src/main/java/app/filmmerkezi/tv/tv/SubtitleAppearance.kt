package app.filmmerkezi.tv.tv

import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import app.filmmerkezi.tv.PlaybackSettings

/** Canvas captions, one owner for style and controller/seek insets. Never touches the media source. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal class SubtitleAppearance(private val view:PlayerView):AutoCloseable {
    private var settings=PlaybackSettings()
    private var player:Player?=null
    private var cues=emptyList<Cue>()
    private var controls=false;private var seeking=false
    private val listener=object:Player.Listener{override fun onCues(group:CueGroup){cues=group.cues;render()}}
    fun bind(p:Player?){if(player===p)return;player?.removeListener(listener);player=p;cues=emptyList();p?.addListener(listener);render()}
    fun apply(value:PlaybackSettings){settings=value;view.subtitleView?.let{style(it,value)};render()}
    fun controls(visible:Boolean){controls=visible;render()}
    fun seeking(visible:Boolean){seeking=visible;render()}
    private fun render(){val target=view.subtitleView?:return
        val bottom=maxOf(settings.subtitleBottom/100f,if(controls)0.30f else 0f,if(seeking)0.28f else 0f)
        target.setBottomPaddingFraction(bottom)
        target.setCues(cues.map{adjust(it,bottom,settings.customSubtitles,controls||seeking)})
    }
    override fun close(){player?.removeListener(listener);player=null;cues=emptyList()}
    companion object {
        internal fun adjust(cue:Cue,bottom:Float,custom:Boolean,overlay:Boolean):Cue {
            if(cue.bitmap!=null||!custom&&!overlay)return cue
            // Preserve explicit captions near the top; lift bottom/default cues above controls.
            val line=if(custom)1f-bottom else if(cue.lineType==Cue.LINE_TYPE_FRACTION)minOf(cue.line,1f-bottom)
                else if(cue.line==Cue.DIMEN_UNSET||cue.line<0)1f-bottom else return cue
            return cue.buildUpon().setLine(line,Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_END).build()
        }
        fun style(target:SubtitleView,p:PlaybackSettings,previewViewportHeight:Int=0){
            target.setViewType(SubtitleView.VIEW_TYPE_CANVAS)
            target.setApplyEmbeddedStyles(!p.customSubtitles);target.setApplyEmbeddedFontSizes(!p.customSubtitles)
            if(p.customSubtitles){
                val size=listOf(0.040f,0.0533f,0.065f,0.075f)[p.subtitleSize.coerceIn(0,3)]
                if(previewViewportHeight>0)target.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size*previewViewportHeight)else target.setFractionalTextSize(size)
                target.setStyle(CaptionStyleCompat(if(p.subtitleYellow)Color.YELLOW else Color.WHITE,
                    when(p.subtitleBackground){1->0x80000000.toInt();2->Color.BLACK;else->Color.TRANSPARENT},Color.TRANSPARENT,
                    if(p.subtitleOutline)CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE,Color.BLACK,Typeface.DEFAULT))
            }else{if(previewViewportHeight>0)target.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,SubtitleView.DEFAULT_TEXT_SIZE_FRACTION*previewViewportHeight)else target.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION);target.setStyle(CaptionStyleCompat.DEFAULT)}
        }
    }
}
