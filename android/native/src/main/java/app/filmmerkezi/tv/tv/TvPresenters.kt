package app.filmmerkezi.tv.tv

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.*
import app.filmmerkezi.tv.R

internal fun View.dp(value:Int)=(value*resources.displayMetrics.density+0.5f).toInt()

private fun View.cp(value:Int)=ClassicUi.u(context,value.toFloat())

class PosterPresenter:Presenter() {
    class Holder(val box:LinearLayout,val image:ImageView,val title:TextView,val meta:TextView,val progress:ProgressBar,
        val badges:LinearLayout,val dubbed:ImageView,val subbed:ImageView,val sites:TextView):ViewHolder(box)
    override fun onCreateViewHolder(parent:ViewGroup):ViewHolder {
        val c=parent.context
        fun cp(v:Int)=ClassicUi.u(c,v.toFloat())
        val box=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;isFocusable=true;isFocusableInTouchMode=true
            background=ClassicUi.focus(c,true);setPadding(cp(3),cp(3),cp(3),cp(2));layoutParams=ViewGroup.LayoutParams(cp(130),cp(208))}
        val art=FrameLayout(c).apply{background=ClassicUi.shape(c,0xFF1D2B3D.toInt());clipToOutline=true}
        val image=ImageView(c).apply{scaleType=ImageView.ScaleType.CENTER_CROP;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
        art.addView(image,FrameLayout.LayoutParams(-1,-1))
        val badges=LinearLayout(c).apply{setPadding(cp(3),cp(3),cp(3),cp(3));background=ClassicUi.shape(c,0xDD07101C.toInt(),2f)}
        val dubbed=ImageView(c).apply{setImageResource(R.drawable.classic_badge_tr)}
        val subbed=ImageView(c).apply{setImageResource(R.drawable.classic_badge_cc)}
        badges.addView(dubbed,LinearLayout.LayoutParams(cp(18),cp(13)))
        badges.addView(subbed,LinearLayout.LayoutParams(cp(18),cp(13)).apply{marginStart=cp(3)})
        art.addView(badges,FrameLayout.LayoutParams(-2,-2,Gravity.LEFT or Gravity.TOP).apply{leftMargin=cp(5);topMargin=cp(5)})
        val sites=ClassicUi.text(c,"",9.5f,0xFFE5D3A9.toInt(),true).apply{background=ClassicUi.shape(c,0xEE0C1624.toInt(),2f,0xFF70819A.toInt());setPadding(cp(5),cp(3),cp(5),cp(3))}
        art.addView(sites,FrameLayout.LayoutParams(-2,-2,Gravity.RIGHT or Gravity.TOP).apply{rightMargin=cp(5);topMargin=cp(5)})
        val progress=ProgressBar(c,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;isFocusable=false
            progressTintList=android.content.res.ColorStateList.valueOf(ClassicUi.gold);progressBackgroundTintList=android.content.res.ColorStateList.valueOf(0xFF3C4655.toInt())}
        art.addView(progress,FrameLayout.LayoutParams(-1,cp(3),Gravity.BOTTOM))
        box.addView(art,LinearLayout.LayoutParams(-1,cp(152)))
        val title=ClassicUi.text(c,"",12.5f).apply{maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;setPadding(cp(1),cp(8),0,0)}
        val meta=ClassicUi.text(c,"",10f,ClassicUi.muted).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;setPadding(cp(1),0,0,0)}
        box.addView(title,LinearLayout.LayoutParams(-1,cp(38)));box.addView(meta,LinearLayout.LayoutParams(-1,cp(14)))
        return Holder(box,image,title,meta,progress,badges,dubbed,subbed,sites)
    }
    override fun onBindViewHolder(viewHolder:ViewHolder,item:Any?) {
        val h=viewHolder as Holder;val tile=item as TvTile;val c=tile.item?.card
        h.box.tag=tile.key;h.title.text=tile.label
        h.meta.text=c?.let{listOfNotNull(it.year?.toString(),h.box.context.getString(when(it.ref.kind){app.filmmerkezi.tv.core.ContentKind.MOVIE->R.string.classic_movie_meta;app.filmmerkezi.tv.core.ContentKind.COLLECTION->R.string.fm_collection;else->R.string.classic_series_meta}),it.imdb?.let{v->"IMDb "+v}).joinToString(" · ")}.orEmpty()
        val ids=tile.item?.alternatives.orEmpty().map{it.ref.siteId}.distinct()
        h.sites.text=h.box.context.getString(R.string.tv_site_count,ids.size);h.sites.visibility=if(c==null)View.GONE else View.VISIBLE
        val language=app.filmmerkezi.tv.core.TextKey.fold(tile.item?.alternatives.orEmpty().joinToString(" "){it.language})
        h.dubbed.visibility=if(language.contains("dublaj")||language.contains("dubbed"))View.VISIBLE else View.GONE
        h.subbed.visibility=if(language.contains("altyazi")||language.contains("subtitled"))View.VISIBLE else View.GONE
        h.badges.visibility=if(h.dubbed.visibility==View.VISIBLE||h.subbed.visibility==View.VISIBLE)View.VISIBLE else View.GONE
        h.box.contentDescription=tile.label+" · "+ids.joinToString{app.filmmerkezi.tv.core.Sites.get(it).name}
        h.progress.visibility=if(tile.positionMs>0&&tile.durationMs>0)View.VISIBLE else View.INVISIBLE
        h.progress.progress=if(tile.durationMs>0)(tile.positionMs*100/tile.durationMs).coerceIn(0,100).toInt()else 0
        Posters.load(h.image,c,h.box.cp(123),h.box.cp(152))
    }
    override fun onUnbindViewHolder(viewHolder:ViewHolder){val h=viewHolder as Holder;Posters.clear(h.image);h.box.tag=null;h.box.contentDescription=null;h.title.text="";h.meta.text=""}
}

private class ClassicRowHeader(private val move:(Long,Int)->Unit):RowHeaderPresenter(){
    class Holder(view:FrameLayout,val label:TextView,val previous:Button,val next:Button):RowHeaderPresenter.ViewHolder(view)
    override fun onCreateViewHolder(parent:ViewGroup):Presenter.ViewHolder {
        val c=parent.context;val frame=FrameLayout(c).apply{layoutParams=ViewGroup.LayoutParams(cp(886),cp(36))}
        val label=ClassicUi.text(c,"",15.5f,bold=true)
        frame.addView(label,FrameLayout.LayoutParams(-1,frame.cp(24)).apply{topMargin=frame.cp(4)})
        val previous=ClassicUi.button(c,"‹"){}.apply{setPadding(0,0,0,0);setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,frame.cp(16).toFloat())}
        val next=ClassicUi.button(c,"›"){}.apply{setPadding(0,0,0,0);setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,frame.cp(16).toFloat())}
        frame.addView(previous,FrameLayout.LayoutParams(frame.cp(27),frame.cp(23),Gravity.RIGHT or Gravity.TOP).apply{rightMargin=frame.cp(34);topMargin=frame.cp(2)})
        frame.addView(next,FrameLayout.LayoutParams(frame.cp(27),frame.cp(23),Gravity.RIGHT or Gravity.TOP).apply{rightMargin=frame.cp(0);topMargin=frame.cp(2)})
        return Holder(frame,label,previous,next)
    }
    override fun onBindViewHolder(viewHolder:Presenter.ViewHolder,item:Any?){val h=viewHolder as Holder;val row=item as ListRow
        h.label.text=row.headerItem?.name.orEmpty();h.previous.setOnClickListener{move(row.headerItem.id,-1)};h.next.setOnClickListener{move(row.headerItem.id,1)};h.view.alpha=1f}
    override fun onUnbindViewHolder(viewHolder:Presenter.ViewHolder){val h=viewHolder as Holder;h.label.text="";h.previous.setOnClickListener(null);h.next.setOnClickListener(null)}
    override fun onSelectLevelChanged(holder:RowHeaderPresenter.ViewHolder){holder.view.alpha=1f}
}

/** Episode names remain visible below the detail header even on a 720p TV. */
class EpisodePresenter:Presenter() {
    override fun onCreateViewHolder(parent:ViewGroup)=ViewHolder(TextView(parent.context).apply{
        isFocusable=true;isFocusableInTouchMode=true
        setTextColor(Color.WHITE);textSize=17f;gravity=Gravity.CENTER;maxLines=2
        background=context.getDrawable(R.drawable.tv_focus);setPadding(dp(8),dp(6),dp(8),dp(6))
        layoutParams=ViewGroup.LayoutParams(dp(150),dp(70))
    })
    override fun onBindViewHolder(viewHolder:ViewHolder,item:Any?){
        val tile=item as TvTile
        (viewHolder.view as TextView).apply{text=tile.label;tag=tile.key;contentDescription=tile.label}
    }
    override fun onUnbindViewHolder(viewHolder:ViewHolder){(viewHolder.view as TextView).apply{text="";tag=null;contentDescription=null}}
}

private val tileDiff=object:DiffCallback<TvTile>() {
    override fun areItemsTheSame(oldItem:TvTile,newItem:TvTile)=oldItem.key==newItem.key
    override fun areContentsTheSame(oldItem:TvTile,newItem:TvTile)=oldItem==newItem
}
private fun sameVisibleContent(a:TvTile,b:TvTile):Boolean = a.key==b.key||a.item?.alternatives.orEmpty().any{x->b.item?.alternatives.orEmpty().any{y->x.ref.siteId==y.ref.siteId&&x.ref.pageUrl==y.ref.pageUrl}}

class TvRowsFragment:RowsSupportFragment() {
    var click:((TvTile)->Unit)?=null
    var selection:((Long,TvTile)->Unit)?=null
    private val columns=mutableMapOf<Long,Int>()
    private val rowPresenter=object:ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE){
        private fun classicPadding(holder:RowPresenter.ViewHolder){
            val row=holder as ListRowPresenter.ViewHolder
            row.gridView.apply{setPadding(ClassicUi.u(context,3f),ClassicUi.u(context,1f),0,0);horizontalSpacing=ClassicUi.u(context,10f);itemAnimator=null}
            row.headerViewHolder?.view?.let{v->(v.parent as? View)?.setPadding(0,0,0,0);(v.layoutParams as? ViewGroup.MarginLayoutParams)?.let{p->
                if(p.marginStart!=0||p.leftMargin!=0){p.marginStart=0;p.leftMargin=0;p.rightMargin=0;v.layoutParams=p}
            }}
        }
        override fun onBindRowViewHolder(holder:RowPresenter.ViewHolder,item:Any){super.onBindRowViewHolder(holder,item)
            classicPadding(holder)
        }
        override fun onRowViewSelected(holder:RowPresenter.ViewHolder,selected:Boolean){super.onRowViewSelected(holder,selected);classicPadding(holder)}
        override fun onRowViewExpanded(holder:RowPresenter.ViewHolder,expanded:Boolean){super.onRowViewExpanded(holder,expanded);classicPadding(holder)}
        override fun onSelectLevelChanged(holder:RowPresenter.ViewHolder){super.onSelectLevelChanged(holder);classicPadding(holder)}
    }.apply{setShadowEnabled(false);setSelectEffectEnabled(false);headerPresenter=ClassicRowHeader{row,delta->
        val a=items[row];if(a!=null){val col=((columns[row]?:0)+delta*6).coerceIn(0,(a.size()-1).coerceAtLeast(0));columns[row]=col
            setSelectedPosition(items.keys.indexOf(row),false,ListRowPresenter.SelectItemViewHolderTask(col))}
    }}
    private val rows=ArrayObjectAdapter(rowPresenter)
    private val items=linkedMapOf<Long,ArrayObjectAdapter>()
    override fun onCreate(state:Bundle?){super.onCreate(state);adapter=rows
        onItemViewClickedListener=OnItemViewClickedListener{_,item,_,_->(item as? TvTile)?.let{click?.invoke(it)}}
        onItemViewSelectedListener=OnItemViewSelectedListener{_,item,_,row->if(item is TvTile&&row is ListRow){columns[row.headerItem.id]=(0 until row.adapter.size()).firstOrNull{row.adapter[it]==item}?:0;selection?.invoke(row.headerItem.id,item)}}
    }
    override fun onViewCreated(view:View,state:Bundle?){super.onViewCreated(view,state);verticalGridView?.apply{itemAnimator=null;setPadding(0,0,0,0);verticalSpacing=view.cp(16)};enableRowScaling(false);setAlignment(view.cp(36))}
    fun show(sections:List<TvSection>) {
        val selectedRow=items.keys.elementAtOrNull(selectedPosition)
        val selectedTile=selectedRow?.let{id->items[id]?.let{a->(if((columns[id]?:0) in 0 until a.size())a[columns[id]?:0]else null)as? TvTile}}
        if(items.keys.toList()!=sections.map{it.id}){
            rows.clear();items.clear()
            sections.forEach{s->val a=ArrayObjectAdapter(if(arguments?.getBoolean("compact")==true)EpisodePresenter()else PosterPresenter());items[s.id]=a;rows.add(ListRow(HeaderItem(s.id,s.title),a))}
        }
        sections.forEach{s->items.getValue(s.id).setItems(s.tiles,tileDiff)}
        if(selectedRow!=null&&selectedTile!=null){val section=sections.firstOrNull{it.id==selectedRow};val column=section?.tiles?.indexOfFirst{sameVisibleContent(selectedTile,it)}?:-1
            if(column>=0)setSelectedPosition(sections.indexOf(section),false,ListRowPresenter.SelectItemViewHolderTask(column))}
    }
    fun focusContent(){verticalGridView?.requestFocus()}
    fun restore(rowId:Long,key:String){
        val index=items.keys.indexOf(rowId);if(index<0)return
        val a=items.getValue(rowId);val column=(0 until a.size()).firstOrNull{(a[it]as TvTile).key==key}?:0
        setSelectedPosition(index,false,ListRowPresenter.SelectItemViewHolderTask(column))
    }
    fun atTop()=selectedPosition<=0
}

/** Articles have ordered names/years, not poster URLs: keep every member readable. */
private class CollectionMemberPresenter:Presenter(){
    override fun onCreateViewHolder(parent:ViewGroup)=ViewHolder(ClassicUi.text(parent.context,"",12.5f).apply{
        isFocusable=true;isFocusableInTouchMode=true;gravity=Gravity.CENTER_VERTICAL;maxLines=2
        setPadding(cp(10),cp(6),cp(10),cp(6));background=ClassicUi.focus(context)
        layoutParams=ViewGroup.LayoutParams(cp(438),cp(50))
    })
    override fun onBindViewHolder(holder:ViewHolder,item:Any?){val tile=item as TvTile;(holder.view as TextView).apply{tag=tile.key;text=tile.label;contentDescription=tile.label}}
    override fun onUnbindViewHolder(holder:ViewHolder){(holder.view as TextView).apply{tag=null;text="";contentDescription=null}}
}

class TvGridFragment:VerticalGridSupportFragment() {
    var click:((TvTile)->Unit)?=null
    var selection:((TvTile)->Unit)?=null
    private val columns get()=if(arguments?.getBoolean("collection")==true)2 else 6
    private val cards by lazy{ArrayObjectAdapter(if(columns==2)CollectionMemberPresenter()else PosterPresenter())}
    private var selected=0
    private var values=emptyList<TvTile>()
    private var vertical:VerticalGridView?=null
    private var updating=false
    private var resetNext=false
    private var restoreKey:String?=null
    private var focusRequested=false
    private var focusOrigin:View?=null
    private val diff=object:DiffCallback<TvTile>(){
        override fun areItemsTheSame(a:TvTile,b:TvTile)=sameVisibleContent(a,b)
        override fun areContentsTheSame(a:TvTile,b:TvTile)=a==b
    }
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        setGridPresenter(VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_NONE,false).apply{setNumberOfColumns(columns);setShadowEnabled(false)})
        adapter=cards
        onItemViewClickedListener=OnItemViewClickedListener{_,item,_,_->(item as? TvTile)?.let{click?.invoke(it)}}
        setOnItemViewSelectedListener(OnItemViewSelectedListener{_,item,_,_->if(!updating)(item as? TvTile)?.let{tile->
            values.indexOfFirst{it.key==tile.key}.takeIf{it>=0}?.let{selected=it}
            if(hasContentFocus())selection?.invoke(tile)
        }})
    }
    fun resetSelection(){resetNext=true;selected=0}
    fun show(next:List<TvTile>){
        if(values==next&&!resetNext){applyRestoredFocus();return}
        val focused=hasContentFocus();val old=if(resetNext)null else values.getOrNull(selected)
        val previous=selected
        if(resetNext){restoreKey=null;focusRequested=false;focusOrigin=null}
        val index=restoreKey?.let{k->next.indexOfFirst{it.key==k}}?.takeIf{it>=0}?:old?.let{tile->next.indexOfFirst{sameVisibleContent(tile,it)}}?.takeIf{it>=0}
            ?:if(resetNext)0 else selected.coerceAtMost((next.size-1).coerceAtLeast(0))
        updating=true;values=next;cards.setItems(next,diff);updating=false;selected=index
        // DiffUtil already retains a focused holder. Re-select only if its position moved.
        // BaseGridView's immediate selection avoids queued smooth-scroll jumps on each site reply.
        if(focused&&(resetNext||index!=previous||old!=null&&next.none{sameVisibleContent(old,it)}))vertical?.setSelectedPosition(index)
        resetNext=false
        applyRestoredFocus()
    }
    override fun onViewCreated(view:View,state:Bundle?){
        super.onViewCreated(view,state)
        vertical=view.findViewById<VerticalGridView>(androidx.leanback.R.id.browse_grid)?.apply{
            itemAnimator=null
            setPadding(0,cp(5),0,cp(5));setColumnWidth(cp(if(columns==2)438 else 140));if(columns==2)horizontalSpacing=cp(8);verticalSpacing=cp(10)
            windowAlignment=BaseGridView.WINDOW_ALIGN_BOTH_EDGE
            windowAlignmentOffset=0;windowAlignmentOffsetPercent=0f
            itemAlignmentOffset=0;itemAlignmentOffsetPercent=0f
        }
    }
    override fun onDestroyView(){vertical=null;focusRequested=false;focusOrigin=null;super.onDestroyView()}
    private fun applyRestoredFocus(){
        val v=vertical?:return
        if(values.isEmpty())return
        val restored=restoreKey?.let{key->values.indexOfFirst{it.key==key}.takeIf{it>=0}}
        if(restored!=null)selected=restored
        if(!focusRequested){if(restored!=null){v.setSelectedPosition(selected);restoreKey=null};return}
        v.setSelectedPosition(selected,object:ViewHolderTask{
            override fun run(holder:androidx.recyclerview.widget.RecyclerView.ViewHolder){
                if(v!==vertical||!focusRequested)return
                val now=activity?.currentFocus
                if(now==null||now===focusOrigin||v.hasFocus())holder.itemView.requestFocus()
                focusRequested=false;focusOrigin=null;restoreKey=null
            }
        })
    }
    fun focusContent(){focusRequested=true;focusOrigin=activity?.currentFocus;applyRestoredFocus()}
    fun restore(key:String){restoreKey=key;values.indexOfFirst{it.key==key}.takeIf{it>=0}?.let{selected=it};applyRestoredFocus()}
    fun atTop()=selected<columns
    fun hasContentFocus()=vertical?.hasFocus()==true
    fun move(key:Int):Boolean {
        if(!hasContentFocus()||values.isEmpty())return false
        val target=when(key){
            KeyEvent.KEYCODE_DPAD_LEFT->if(selected%columns==0)selected else selected-1
            KeyEvent.KEYCODE_DPAD_RIGHT->if(selected%columns==columns-1)selected else minOf(selected+1,values.lastIndex)
            KeyEvent.KEYCODE_DPAD_DOWN->if(selected/columns==values.lastIndex/columns)selected else minOf(selected+columns,values.lastIndex)
            KeyEvent.KEYCODE_DPAD_UP->if(selected<columns)return false else selected-columns
            else->return false
        }
        selected=target;vertical?.setSelectedPosition(target);return true
    }
    internal fun selectedKey()=values.getOrNull(selected)?.key
}
