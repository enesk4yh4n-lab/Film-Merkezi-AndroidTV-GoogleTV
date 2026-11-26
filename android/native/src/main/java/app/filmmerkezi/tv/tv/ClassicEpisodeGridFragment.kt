package app.filmmerkezi.tv.tv

import android.os.Bundle
import android.view.*
import android.widget.*
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.*
import app.filmmerkezi.tv.R

/** Draw above button backgrounds so the focused row cannot lose its outline. */
private class EpisodeFocusRow(context:Context):LinearLayout(context){
    private val border=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xFFFFCA55.toInt();style=Paint.Style.STROKE;strokeWidth=ClassicUi.u(context,1.5f).coerceAtLeast(2).toFloat()}
    override fun requestChildFocus(child:View,focused:View){super.requestChildFocus(child,focused);invalidate()}
    override fun clearChildFocus(child:View){super.clearChildFocus(child);invalidate()}
    override fun dispatchDraw(canvas:Canvas){
        super.dispatchDraw(canvas)
        if(hasFocus()){val inset=border.strokeWidth/2;val radius=ClassicUi.u(context,3f).toFloat()
            canvas.drawRoundRect(inset,inset,width-inset,height-inset,radius,radius,border)}
    }
}

class ClassicEpisodeGridFragment:VerticalGridSupportFragment(){
    var click:((TvTile)->Unit)?=null
    var options:((TvTile)->Unit)?=null
    private var selected=0
    private val cards=ArrayObjectAdapter(object:Presenter(){
        override fun onCreateViewHolder(parent:ViewGroup):ViewHolder {
            val c=parent.context
            val row=EpisodeFocusRow(c).apply{
                orientation=LinearLayout.HORIZONTAL;layoutParams=ViewGroup.LayoutParams(ClassicUi.u(c,880f),ClassicUi.u(c,39f))
                // A clickable container becomes focusable automatically on newer
                // Android versions. Keep DPAD on the actual episode/action buttons.
                isFocusable=false;descendantFocusability=ViewGroup.FOCUS_AFTER_DESCENDANTS
                setAddStatesFromChildren(true);background=ClassicUi.focus(c)
                // Leave the row's focus outline visible around the child backgrounds.
                val inset=ClassicUi.u(c,2f).coerceAtLeast(1);setPadding(inset,inset,inset,inset)
            }
            val title=ClassicUi.button(c,""){}.apply{gravity=Gravity.LEFT or Gravity.CENTER_VERTICAL}
            val more=ClassicUi.button(c,c.getString(R.string.classic_episode_options)){}.apply{maxLines=2;gravity=Gravity.LEFT or Gravity.CENTER_VERTICAL
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,ClassicUi.u(c,11f).toFloat())}
            row.addView(title,LinearLayout.LayoutParams(0,-1,1f));row.addView(more,LinearLayout.LayoutParams(ClassicUi.u(c,110f),-1))
            return ViewHolder(row)
        }
        override fun onBindViewHolder(holder:ViewHolder,item:Any?){val tile=item as TvTile;val row=holder.view as LinearLayout
            row.tag=tile.key;row.setOnClickListener{click?.invoke(tile)};row.isFocusable=false
            val title=row.getChildAt(0)as Button;val more=row.getChildAt(1)as Button
            title.text=tile.label;title.setOnClickListener{click?.invoke(tile)}
            more.tag=tile.key+":options";more.visibility=if(tile.item!=null)View.VISIBLE else View.GONE;more.setOnClickListener{options?.invoke(tile)}
        }
        override fun onUnbindViewHolder(holder:ViewHolder){val row=holder.view as LinearLayout;row.tag=null;row.setOnClickListener(null)
            for(i in 0 until row.childCount){row.getChildAt(i).setOnClickListener(null);row.getChildAt(i).tag=null}}
    })
    override fun onCreate(state:Bundle?){super.onCreate(state)
        setGridPresenter(VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_NONE,false).apply{setNumberOfColumns(1);setShadowEnabled(false)})
        adapter=cards;setOnItemViewSelectedListener(OnItemViewSelectedListener{_,item,_,_->selected=(0 until cards.size()).firstOrNull{cards[it]==item}?:0})
    }
    override fun onViewCreated(view:View,state:Bundle?){super.onViewCreated(view,state)
        view.findViewById<VerticalGridView>(androidx.leanback.R.id.browse_grid)?.apply{
            setPadding(0,ClassicUi.u(context,4f),0,0);verticalSpacing=ClassicUi.u(context,5f);itemAnimator=null
            windowAlignment=BaseGridView.WINDOW_ALIGN_LOW_EDGE;windowAlignmentOffsetPercent=0f;itemAlignmentOffsetPercent=0f
        }
    }
    fun show(values:List<TvTile>){cards.setItems(values,object:DiffCallback<TvTile>(){
        override fun areItemsTheSame(a:TvTile,b:TvTile)=a.key==b.key
        override fun areContentsTheSame(a:TvTile,b:TvTile)=a==b
    })}
    fun focusContent(){view?.findViewById<View>(androidx.leanback.R.id.browse_grid)?.requestFocus()}
    fun restore(key:String){
        val index=(0 until cards.size()).firstOrNull{(cards[it]as? TvTile)?.key==key}?:return
        selected=index;setSelectedPosition(index)
        view?.post{val grid=view?.findViewById<VerticalGridView>(androidx.leanback.R.id.browse_grid)
            grid?.setSelectedPosition(index);grid?.post{val row=grid.findViewWithTag<View>(key)as? ViewGroup
                if(row!=null)row.getChildAt(0)?.requestFocus()else grid.requestFocus()}}
    }
    fun atTop()=selected==0
}
