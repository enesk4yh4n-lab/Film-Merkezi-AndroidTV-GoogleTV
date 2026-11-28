package app.filmmerkezi.tv.tv

import android.app.Activity
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.*
import app.filmmerkezi.tv.R

/** Recycled update pages embedded in the Classic options screen, without another window. */
internal class UpdateCenterView(private val activity:Activity,private val left:()->Unit,private val up:()->Unit=left):LinearLayout(activity) {
    private val title=ClassicUi.text(activity,"",21f,bold=true)
    private val status=ClassicUi.text(activity,"",12f,ClassicUi.muted).apply{maxLines=4}
    private val progress=ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal).apply{isIndeterminate=true;visibility=GONE;tag="updates:loading"}
    private val back=ClassicUi.button(activity,activity.getString(R.string.tv_back)){onBack?.invoke()}.apply{tag="updates:back"}
    private val list=RecyclerView(activity).apply{layoutManager=LinearLayoutManager(activity);itemAnimator=null;setItemViewCacheSize(3);overScrollMode=View.OVER_SCROLL_NEVER}
    private val footer=LinearLayout(activity)
    private var rows=emptyList<ClassicRow>()
    private var choose:(String)->Unit={}
    private var actions=emptyList<ClassicAction>()
    private var preferred:String?=null
    private data class Position(val scroll:android.os.Parcelable?,val focus:String?)
    private val positions=object:LinkedHashMap<String,Position>(24,0.75f,true){override fun removeEldestEntry(e:MutableMap.MutableEntry<String,Position>?)=size>24}
    private var pageId=""
    private var resumeFocus:String?=null
    private var restoreScroll:android.os.Parcelable?=null
    private var restoreFocus:String?=null
    private var rendering=0
    private var focusRevision=0
    private var pendingFocus=false
    var onBack:(()->Unit)?=null
    private fun u(v:Float)=ClassicUi.u(activity,v)
    private fun afterLayout(action:()->Unit){list.addOnLayoutChangeListener(object:View.OnLayoutChangeListener{
        override fun onLayoutChange(v:View,l:Int,t:Int,r:Int,b:Int,ol:Int,ot:Int,or:Int,ob:Int){v.removeOnLayoutChangeListener(this);v.post{action()}}
    });list.requestLayout()}
    private val adapter=object:RecyclerView.Adapter<Holder>() {
        init{setHasStableIds(true)}
        override fun getItemCount()=rows.size
        override fun getItemId(position:Int)=app.filmmerkezi.tv.core.TextKey.hash(rows[position].key).take(15).toLong(16)
        override fun onCreateViewHolder(parent:ViewGroup,type:Int):Holder {
            val box=LinearLayout(activity).apply{orientation=VERTICAL;setPadding(u(14f),u(10f),u(14f),u(10f));background=ClassicUi.focus(activity);layoutParams=RecyclerView.LayoutParams(-1,-2).apply{bottomMargin=u(7f)}}
            val heading=ClassicUi.text(activity,"",14f,bold=true);val info=ClassicUi.text(activity,"",11.5f,ClassicUi.muted).apply{maxLines=3;ellipsize=android.text.TextUtils.TruncateAt.END}
            box.addView(heading);box.addView(info,LayoutParams(-1,-2).apply{topMargin=u(5f)})
            box.setOnFocusChangeListener{v,focused->if(focused)resumeFocus=v.tag as? String}
            box.setOnKeyListener{v,code,event->when{
                event.action!=KeyEvent.ACTION_DOWN->false
                code==KeyEvent.KEYCODE_DPAD_LEFT->{left();true}
                code==KeyEvent.KEYCODE_DPAD_RIGHT->true
                code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN->{
                    val available=rows.filter{it.enabled&&!it.header};val index=available.indexOfFirst{"updates:"+it.key==v.tag}
                    val next=index+if(code==KeyEvent.KEYCODE_DPAD_UP)-1 else 1
                    if(next<0){if(back.isShown)back.requestFocus()else up()}
                    else if(next>=available.size){if(actions.any{it.enabled})focusFooter()}
                    else focusKey("updates:"+available[next].key);true
                }
                else->false
            }}
            return Holder(box,heading,info)
        }
        override fun onBindViewHolder(holder:Holder,position:Int) {
            val row=rows[position];holder.box.tag="updates:"+row.key;holder.box.isEnabled=row.enabled&&!row.header;holder.box.isFocusable=holder.box.isEnabled;holder.box.isFocusableInTouchMode=holder.box.isFocusable;holder.box.isSelected=row.selected
            holder.heading.text=(if(row.selected)"✓ "else "")+row.title;holder.heading.setTextColor(if(row.selected)ClassicUi.gold else if(row.enabled)ClassicUi.foreground else ClassicUi.muted)
            holder.info.text=row.description;holder.info.visibility=if(row.description.isBlank())GONE else VISIBLE
            holder.box.contentDescription=listOf(row.title,row.description).filter{it.isNotBlank()}.joinToString(" · ")
            holder.box.setOnClickListener{val key=row.key;if(rows.any{it.key==key&&it.enabled&&!it.header})choose(key)}
        }
    }
    private class Holder(val box:LinearLayout,val heading:TextView,val info:TextView):RecyclerView.ViewHolder(box)
    init {
        orientation=VERTICAL;tag="update-center";setPadding(u(5f),u(9f),0,0)
        val heading=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL;addView(title,LayoutParams(0,-2,1f));addView(back,LayoutParams(u(64f),u(32f)))}
        addView(heading);addView(progress,LayoutParams(-1,u(3f)).apply{topMargin=u(8f)});addView(status,LayoutParams(-1,-2).apply{topMargin=u(10f);bottomMargin=u(12f)})
        list.adapter=adapter;addView(list,LayoutParams(-1,0,1f));addView(footer,LayoutParams(-1,-2))
        back.setOnKeyListener{_,code,event->if(event.action!=KeyEvent.ACTION_DOWN)false else when(code){
            KeyEvent.KEYCODE_DPAD_LEFT->{left();true}
            KeyEvent.KEYCODE_DPAD_DOWN->{focusFirst();true}
            KeyEvent.KEYCODE_DPAD_UP->{up();true}
            KeyEvent.KEYCODE_DPAD_RIGHT->true
            else->false
        }}
        back.setOnFocusChangeListener{v,focused->if(focused)resumeFocus=v.tag as? String}
    }
    fun page(value:String,nested:Boolean){
        if(pageId.isNotEmpty())positions[pageId]=Position(list.layoutManager?.onSaveInstanceState(),resumeFocus)
        rendering++;pageId=value;val saved=positions[value];restoreScroll=saved?.scroll;restoreFocus=saved?.focus;resumeFocus=saved?.focus
        title.text=value;back.visibility=if(nested)VISIBLE else GONE;preferred=null;loading(false)
        val count=rows.size;rows=emptyList();choose={};if(count>0)adapter.notifyItemRangeRemoved(0,count)
        actions=emptyList();footer.removeAllViews();status.text="";status.visibility=GONE
    }
    fun loading(value:Boolean){progress.isIndeterminate=true;progress.visibility=if(value)VISIBLE else GONE}
    fun progress(completed:Long,total:Long){progress.isIndeterminate=total<=0;progress.max=1000;progress.progress=if(total>0)(completed.coerceIn(0,total)*1000/total).toInt()else 0;progress.visibility=VISIBLE}
    fun update(values:List<ClassicRow>,actions:List<ClassicAction> = emptyList(),message:String="",click:(String)->Unit={}) {
        val serial=++rendering;val navigation=focusRevision;val focused=findFocus()?.tag as? String;val inside=hasFocus();val previous=rows
        val saved=list.layoutManager?.onSaveInstanceState()
        choose=click;rows=values;status.text=message;status.visibility=if(message.isBlank())GONE else VISIBLE
        val diff=DiffUtil.calculateDiff(object:DiffUtil.Callback(){override fun getOldListSize()=previous.size;override fun getNewListSize()=values.size
            override fun areItemsTheSame(a:Int,b:Int)=previous[a].key==values[b].key
            override fun areContentsTheSame(a:Int,b:Int)=previous[a]==values[b]})
        diff.dispatchUpdatesTo(adapter)
        val restoring=restoreScroll!=null&&values.isNotEmpty()
        if(restoring)list.layoutManager?.onRestoreInstanceState(restoreScroll)
        else if(focused==null||values.none{"updates:"+it.key==focused})list.layoutManager?.onRestoreInstanceState(saved)
        if(restoring)restoreScroll=null
        val changed=this.actions.map{Triple(it.key,it.title,it.enabled)}!=actions.map{Triple(it.key,it.title,it.enabled)};this.actions=actions
        if(changed){footer.removeAllViews();actions.forEach{a->footer.addView(ClassicUi.button(activity,a.title){this.actions.firstOrNull{it.key==a.key&&it.enabled}?.run?.invoke()}.apply{
            tag="updates:"+a.key;isEnabled=a.enabled;isFocusable=a.enabled
            setOnFocusChangeListener{v,focused->if(focused)resumeFocus=v.tag as? String}
            setOnKeyListener{_,code,event->when{
                event.action!=KeyEvent.ACTION_DOWN->false
                code==KeyEvent.KEYCODE_DPAD_LEFT&&footer.indexOfChild(this)==0->{left();true}
                code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT->{val enabled=this@UpdateCenterView.actions.filter{it.enabled};val at=enabled.indexOfFirst{"updates:"+it.key==tag};val target=(at+if(code==KeyEvent.KEYCODE_DPAD_LEFT)-1 else 1).coerceIn(0,(enabled.size-1).coerceAtLeast(0));enabled.getOrNull(target)?.let{focusKey("updates:"+it.key)};true}
                code==KeyEvent.KEYCODE_DPAD_DOWN->true
                code==KeyEvent.KEYCODE_DPAD_UP&&rows.any{it.enabled&&!it.header}->{val key=rows.last{it.enabled&&!it.header}.key;focusKey("updates:$key");true}
                code==KeyEvent.KEYCODE_DPAD_UP->{if(back.isShown)back.requestFocus()else up();true}
                else->false
            }}
        },LayoutParams(0,u(38f),1f).apply{marginEnd=u(6f);topMargin=u(9f)})}}
        // Background status refreshes must never steal focus from the category rail.
        val target=preferred?:restoreFocus?:focused
        val explicit=preferred!=null||pendingFocus
        if(explicit||inside){
            fun restore(){val outside=activity.currentFocus?.tag?.toString()?.startsWith("settings-category:")==true
                if(serial==rendering&&navigation==focusRevision&&(explicit||!outside)){
                    if(values.isNotEmpty()||actions.isNotEmpty())restoreFocus=null
                    if(target==null||!focusKey(target))focusFirst()
                }}
            if(previous!=values)afterLayout{restore()}else post{restore()}
        }
    }
    fun requestKey(key:String){focusRevision++;preferred="updates:$key";val current=pageId;post{if(current==pageId&&focusKey("updates:$key"))preferred=null}}
    private fun focusKey(key:String):Boolean {
        val index=rows.indexOfFirst{"updates:"+it.key==key&&it.enabled&&!it.header}
        if(index<0){val done=findViewWithTag<View>(key)?.takeIf{it.isShown&&it.isFocusable}?.requestFocus()==true;if(done)pendingFocus=false;return done}
        val item=list.findViewHolderForAdapterPosition(index)?.itemView
        if(item!=null&&item.top>=0&&item.bottom<=list.height&&item.requestFocus()){pendingFocus=false;return true}
        val serial=rendering;list.scrollToPosition(index);afterLayout{if(serial==rendering&&list.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()==true)pendingFocus=false};return true
    }
    private fun focusFooter(){actions.firstOrNull{it.enabled}?.let{focusKey("updates:"+it.key)}}
    fun focusContent(){pendingFocus=true;if(resumeFocus?.let(::focusKey)!=true)focusFirst()}
    fun cancelFocusRequest(){pendingFocus=false}
    fun focusFirst(){rows.firstOrNull{it.enabled&&!it.header}?.let{focusKey("updates:"+it.key)}?:actions.firstOrNull{it.enabled}?.let{focusKey("updates:"+it.key)}?:back.takeIf{it.visibility==VISIBLE}?.let{focusKey("updates:back")}}
    internal fun keys()=rows.map{it.key}
    internal fun titles()=rows.map{it.title}
    internal fun row(key:String)=rows.single{it.key==key}
    internal fun actionKeys()=actions.map{it.key}
    internal fun select(key:String){if(rows.any{it.key==key&&it.enabled&&!it.header})choose(key)else actions.firstOrNull{it.key==key&&it.enabled}?.run?.invoke()}
}
