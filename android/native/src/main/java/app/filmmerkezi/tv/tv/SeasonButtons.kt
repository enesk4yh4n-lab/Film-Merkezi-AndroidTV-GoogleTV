package app.filmmerkezi.tv.tv

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Button
import app.filmmerkezi.tv.R

/** Small native strip. Stable views preserve DPAD focus across late catalog updates. */
class SeasonButtons(context:Context):HorizontalScrollView(context) {
    private val row=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL}
    private val buttons=linkedMapOf<Int,Button>()
    var selectedSeason:Int=1;private set
    val count get()=buttons.size
    var select:((Int)->Unit)?=null
    var down:(()->Unit)?=null
    init {isHorizontalScrollBarEnabled=false;isFillViewport=false;isFocusable=false;isSmoothScrollingEnabled=false;addView(row)}
    fun bind(numbers:List<Int>,chosen:Int) {
        val values=numbers.filter{it in 1..1000}.distinct().sorted();val focused=buttons.entries.firstOrNull{it.value.hasFocus()}?.key
        buttons.keys.filter{it !in values}.toList().forEach{n->row.removeView(buttons.remove(n))}
        values.forEachIndexed{i,n->
            val b=buttons.getOrPut(n){ClassicUi.button(context,if(n==0)context.getString(R.string.catalog_specials)else context.getString(R.string.season_tab,n)){
                bind(values,n);select?.invoke(n)
            }.apply{tag="season:$n";setOnKeyListener{_,key,event->
                if(event.action==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_DOWN){down?.invoke();true}
                else if(key==KeyEvent.KEYCODE_DPAD_LEFT&&n==buttons.keys.minOrNull()||key==KeyEvent.KEYCODE_DPAD_RIGHT&&n==buttons.keys.maxOrNull())true else false
            }}}
            // Existing listeners must use the newest list after a late season arrives.
            b.setOnClickListener{bind(buttons.keys.toList(),n);select?.invoke(n)}
            if(row.indexOfChild(b)!=i){row.removeView(b);row.addView(b,i,LinearLayout.LayoutParams(ClassicUi.u(context,90f),-1).apply{marginEnd=ClassicUi.u(context,8f)})}
            b.isSelected=n==chosen;b.setTextColor(if(b.isSelected)ClassicUi.gold else ClassicUi.foreground)
        }
        selectedSeason=chosen
        val order=values.mapNotNull{buttons[it]};order.forEachIndexed{i,b->b.nextFocusLeftId=order.getOrNull(i-1)?.id?:b.id;b.nextFocusRightId=order.getOrNull(i+1)?.id?:b.id}
        if(focused!=null){(buttons[focused]?:buttons[chosen])?.requestFocus()}
    }
    fun selectSeason(number:Int){buttons[number]?.performClick()}
    override fun requestFocus(direction:Int,previouslyFocusedRect:android.graphics.Rect?):Boolean =
        (buttons[selectedSeason]?:buttons.values.firstOrNull())?.requestFocus(direction,previouslyFocusedRect)?:false
}
