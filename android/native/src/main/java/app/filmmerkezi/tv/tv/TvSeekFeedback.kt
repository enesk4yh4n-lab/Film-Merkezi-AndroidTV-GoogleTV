package app.filmmerkezi.tv.tv

import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.media3.ui.PlayerView
import app.filmmerkezi.tv.R
import java.util.Locale

/** A non-focusable seek readout, independent of the player controller and video surface. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal class TvSeekFeedback(private val view:PlayerView,private val visibility:(Boolean)->Unit={}):AutoCloseable {
    private val context=view.context
    private fun u(value:Int)=ClassicUi.u(context,value.toFloat())
    private val handler=Handler(Looper.getMainLooper())
    private val delta=ClassicUi.text(context,"",12f,ClassicUi.gold,bold=true).apply{id=R.id.fm_seek_delta}
    private val time=ClassicUi.text(context,"",25f,bold=true).apply{id=R.id.fm_seek_time}
    private val remaining=ClassicUi.text(context,"",11f,ClassicUi.muted).apply{id=R.id.fm_seek_remaining;gravity=Gravity.END}
    private val preview=android.widget.ImageView(context).apply{
        visibility=View.GONE;scaleType=android.widget.ImageView.ScaleType.FIT_CENTER
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val progress=ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal).apply{
        id=R.id.fm_seek_progress;isIndeterminate=false;max=10000
        progressTintList=ColorStateList.valueOf(ClassicUi.gold)
        progressBackgroundTintList=ColorStateList.valueOf(0xFF344258.toInt())
    }
    private val root=LinearLayout(context).apply{
        id=R.id.fm_seek_feedback;orientation=LinearLayout.VERTICAL
        setPadding(u(17),u(12),u(17),u(14));visibility=View.GONE
        isFocusable=false;isClickable=false;isFocusableInTouchMode=false
        background=ClassicUi.shape(context,0xF2142233.toInt(),5f,0xFF576044.toInt())
        accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        addView(preview,LinearLayout.LayoutParams(-1,u(80)))
        addView(delta,LinearLayout.LayoutParams(-1,-2))
        addView(time,LinearLayout.LayoutParams(-1,-2).apply{topMargin=u(4)})
        addView(progress,LinearLayout.LayoutParams(-1,u(5)).apply{topMargin=u(9);bottomMargin=u(7)})
        addView(remaining,LinearLayout.LayoutParams(-1,-2))
    }
    private val hideTask=Runnable{hide()}
    init {
        (view.overlayFrameLayout?:view).addView(root,FrameLayout.LayoutParams(u(540),-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply{bottomMargin=u(26)})
    }
    private fun clock(ms:Long):String {
        val seconds=ms.coerceAtLeast(0)/1000
        return if(seconds>=3600)String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)
            else String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60)
    }
    fun show(target:Long,duration:Long,change:Long,direction:Int){
        val known=duration>0;val position=if(known)target.coerceIn(0,duration)else target.coerceAtLeast(0)
        delta.text=context.getString(if(direction<0)R.string.seek_backward_delta else R.string.seek_forward_delta,clock(if(change<0)-change else change))
        time.text=context.getString(R.string.seek_position_total,clock(position),if(known)clock(duration)else context.getString(R.string.seek_unknown_time))
        remaining.text=if(known)context.getString(R.string.seek_remaining,clock((duration-position).coerceAtLeast(0)))else context.getString(R.string.seek_duration_unknown)
        progress.visibility=if(known)View.VISIBLE else View.GONE
        progress.progress=if(known)((position.toDouble()/duration)*10000).toInt().coerceIn(0,10000)else 0
        root.contentDescription=listOf(delta.text,time.text,remaining.text).joinToString(", ")
        root.visibility=View.VISIBLE
        visibility(true)
        handler.removeCallbacks(hideTask);handler.postDelayed(hideTask,2500)
    }
    fun hide(){
        handler.removeCallbacks(hideTask);root.visibility=View.GONE
        setPreview(null)
        visibility(false)
    }
    fun setPreview(bitmap:android.graphics.Bitmap?){
        preview.setImageBitmap(bitmap)
        preview.visibility=if(bitmap==null||root.visibility!=View.VISIBLE)View.GONE else View.VISIBLE
    }
    override fun close()=hide()
}
