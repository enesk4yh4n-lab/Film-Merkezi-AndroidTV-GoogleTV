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
import app.filmmerkezi.tv.PlaybackSession
import androidx.media3.common.Format
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

/** Canvas captions, one owner for style and controller/seek insets. Never touches the media source. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal class SubtitleAppearance(private val view:PlayerView):AutoCloseable {
    private var settings=PlaybackSettings()
    private var player:Player?=null
    private var cues=emptyList<Cue>()
    private var controls=false;private var seeking=false
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private var shifted:List<CuesWithTiming> = emptyList()
    private var prefixEnd=LongArray(0)
    private var activeFile:(()->PlaybackSession.SubtitleFile?)?=null
    private var fileId=""
    private var result=emptyList<Cue>()
    var offsetMs=0
        private set
    private val tick=object:Runnable {
        override fun run(){
            if(offsetMs==0)return
            if(activeFile?.invoke()?.id!=fileId){clearOffset();return}
            val time=(player?.currentPosition?:0L)*1000-offsetMs*1000L
            val current=selectCues(time)
            if(result!=current){result=current;render()}
            handler.postDelayed(this,100)
        }
    }
    private val listener=object:Player.Listener{override fun onCues(group:CueGroup){cues=group.cues;render()}}
    fun bind(p:Player?){if(player===p)return;player?.removeListener(listener);player=p;cues=emptyList();clearOffset();p?.addListener(listener);render()}
    suspend fun setOffset(value:Int,selected:()->PlaybackSession.SubtitleFile?):Boolean{
        if(value==0){clearOffset();return true}
        val file=selected()?:return false
        if(file.id!=fileId){
            val parsed=withContext(Dispatchers.IO){
                val bytes=java.io.ByteArrayOutputStream()
                DataSourceInputStream(file.factory.createDataSource(),DataSpec(file.uri)).use{input->
                    val buffer=ByteArray(8192)
                    while(true){
                        coroutineContext.ensureActive()
                        val n=input.read(buffer)
                        if(n<0)break
                        require(bytes.size()+n<=512*1024)
                        bytes.write(buffer,0,n)
                    }
                }
                val all=mutableListOf<CuesWithTiming>()
                val parser=DefaultSubtitleParserFactory().create(Format.Builder().setSampleMimeType(file.mime).build())
                parser.parse(bytes.toByteArray(),SubtitleParser.OutputOptions.allCues()){cue->
                    require(all.size<8000)
                    if(cue.endTimeUs>cue.startTimeUs&&cue.startTimeUs>=0L)all.add(cue)
                }
                all.sortedBy{it.startTimeUs}
            }
            if(parsed.isEmpty()||selected()?.id!=file.id)return false
            shifted=parsed
            var end=0L
            prefixEnd=LongArray(parsed.size){i->end=maxOf(end,parsed[i].endTimeUs);end}
            fileId=file.id
        }
        activeFile=selected
        offsetMs=value.coerceIn(-10000,10000)
        handler.removeCallbacks(tick)
        tick.run()
        render()
        return true
    }
    private fun selectCues(timeUs:Long):List<Cue>{
        var i=shifted.binarySearchBy(timeUs){it.startTimeUs}
        if(i<0)i=-i-2 else while(i+1<shifted.size&&shifted[i+1].startTimeUs==timeUs)i++
        if(i<0||prefixEnd[i]<=timeUs)return emptyList()
        return buildList {
            while(i>=0&&prefixEnd[i]>timeUs){
                if(shifted[i].endTimeUs>timeUs)addAll(shifted[i].cues)
                i--
            }
        }.distinct()
    }
    fun clearOffset(){
        handler.removeCallbacks(tick);offsetMs=0;activeFile=null;fileId="";shifted=emptyList();prefixEnd=LongArray(0);result=emptyList();render()
    }
    fun apply(value:PlaybackSettings){settings=value;view.subtitleView?.let{style(it,value)};render()}
    fun controls(visible:Boolean){controls=visible;render()}
    fun seeking(visible:Boolean){seeking=visible;render()}
    private fun render(){val target=view.subtitleView?:return
        val bottom=maxOf(settings.subtitleBottom/100f,if(controls)0.30f else 0f,if(seeking)0.28f else 0f)
        target.setBottomPaddingFraction(bottom)
        target.setCues((if(offsetMs==0)cues else result).map{adjust(it,bottom,settings.customSubtitles,controls||seeking)})
    }
    override fun close(){player?.removeListener(listener);player=null;cues=emptyList();clearOffset()}
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
