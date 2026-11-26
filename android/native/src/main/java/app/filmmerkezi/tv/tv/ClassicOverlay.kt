package app.filmmerkezi.tv.tv

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.filmmerkezi.tv.R

internal data class ClassicRow(val key:String,val title:String,val description:String="",val selected:Boolean=false,val enabled:Boolean=true,val header:Boolean=false)
internal data class ClassicAction(val key:String,val title:String,val enabled:Boolean=true,val run:()->Unit)

/** Custom native window; the underlying video surface and its player are never replaced. */
internal class ClassicOverlay(private val activity:Activity,title:String,private val side:Boolean=false,
    private val origin:View?=null,private val back:()->Unit={},private val media:(KeyEvent)->Boolean={false}):AutoCloseable {
    var canDismiss:()->Boolean={true}
    private val dialog=object:Dialog(activity){
        override fun dispatchKeyEvent(event:KeyEvent):Boolean {
            if(event.keyCode==KeyEvent.KEYCODE_BACK){if(event.action==KeyEvent.ACTION_UP&&canDismiss()){back();close()};return true}
            if(media(event))return true
            return super.dispatchKeyEvent(event)
        }
    }
    private val root=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(u(22),u(20),u(22),u(18));background=ClassicUi.shape(activity,0xFF142233.toInt(),if(side)0f else 8f,0xFF344258.toInt())}
    private val status=ClassicUi.text(activity,"",11f,ClassicUi.muted)
    private val list=RecyclerView(activity).apply{layoutManager=LinearLayoutManager(activity);itemAnimator=null;overScrollMode=View.OVER_SCROLL_NEVER}
    private val footer=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL}
    private var actionSignature=emptyList<Triple<String,String,Boolean>>();private var lastMessage:String?=null
    private var rows=emptyList<ClassicRow>();private var click:(String)->Unit={};private var closed=false
    private val adapter=object:RecyclerView.Adapter<Holder>(){
        init{setHasStableIds(true)}
        override fun getItemCount()=rows.size
        override fun getItemId(position:Int)=rows[position].key.fold(1125899906842597L){h,c->31*h+c.code}
        override fun onCreateViewHolder(parent:ViewGroup,type:Int):Holder {
            val box=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(u(12),u(9),u(12),u(9));minimumHeight=u(36)
                layoutParams=RecyclerView.LayoutParams(-1,-2).apply{bottomMargin=u(6)}}
            val heading=ClassicUi.text(activity,"",13f);val info=ClassicUi.text(activity,"",10.5f,ClassicUi.muted)
            box.addView(heading);box.addView(info,LinearLayout.LayoutParams(-1,-2).apply{topMargin=u(4)})
            return Holder(box,heading,info)
        }
        override fun onBindViewHolder(h:Holder,position:Int){val r=rows[position]
            h.box.tag=r.key;h.box.isFocusable=r.enabled&&!r.header;h.box.isFocusableInTouchMode=h.box.isFocusable
            h.box.isClickable=h.box.isFocusable;h.box.isSelected=r.selected
            h.box.background=if(r.header)ColorDrawable(Color.TRANSPARENT)else ClassicUi.focus(activity)
            h.title.text=(if(r.selected)"✓ "else "")+r.title;h.title.setTextColor(if(r.selected)ClassicUi.gold else if(r.enabled)ClassicUi.foreground else ClassicUi.muted)
            h.info.text=r.description;h.info.visibility=if(r.description.isBlank())View.GONE else View.VISIBLE
            h.box.contentDescription=listOf(r.title,r.description,if(r.selected)activity.getString(R.string.ux_selected)else "").filter{it.isNotBlank()}.joinToString(" · ")
            h.box.setOnClickListener{if(r.enabled&&!r.header)click(r.key)}
        }
    }
    private class Holder(val box:LinearLayout,val title:TextView,val info:TextView):RecyclerView.ViewHolder(box)
    private fun u(n:Int)=ClassicUi.u(activity,n.toFloat())
    var onClosed:(()->Unit)?=null
    val showing get()=dialog.isShowing
    init {
        val top=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        top.addView(ClassicUi.text(activity,title,20f,bold=true),LinearLayout.LayoutParams(0,-2,1f))
        top.addView(ClassicUi.button(activity,activity.getString(R.string.tv_back)){if(canDismiss()){back();close()}},LinearLayout.LayoutParams(u(58),u(30)))
        root.addView(top);root.addView(status,LinearLayout.LayoutParams(-1,-2).apply{topMargin=u(8);bottomMargin=u(8)})
        list.adapter=adapter;root.addView(list,LinearLayout.LayoutParams(-1,0,1f));root.addView(footer,LinearLayout.LayoutParams(-1,-2))
        root.addView(ClassicUi.text(activity,activity.getString(R.string.ux_panel_hint),10f,ClassicUi.muted),LinearLayout.LayoutParams(-1,-2).apply{topMargin=u(10)})
        dialog.setContentView(root);dialog.setCanceledOnTouchOutside(false)
        dialog.window?.apply{setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);setDimAmount(0.62f)
            setGravity(if(side)Gravity.RIGHT or Gravity.TOP else Gravity.CENTER);attributes=attributes.apply{windowAnimations=0}}
        dialog.setOnDismissListener{if(!closed){closed=true;onClosed?.invoke();origin?.takeIf{it.isShown}?.let{node->node.post{node.requestFocus()}}}}
        dialog.show();dialog.window?.setLayout(if(side)(activity.resources.displayMetrics.widthPixels*0.44f).toInt()else u(650),if(side)-1 else u(350))
    }
    fun update(values:List<ClassicRow>,actions:List<ClassicAction> = emptyList(),message:String="",choose:(String)->Unit={}){
        val signature=actions.map{Triple(it.key,it.title,it.enabled)}
        click=choose
        if(rows==values&&signature==actionSignature&&message==lastMessage)return
        actionSignature=signature;lastMessage=message
        if(!side)dialog.window?.setLayout(u(650),u(if(values.isEmpty())190 else 350))
        val focus=dialog.currentFocus?.tag as? String;val old=rows.indexOfFirst{it.key==focus};val saved=list.layoutManager?.onSaveInstanceState()
        rows=values;click=choose;status.text=message;status.visibility=if(message.isBlank())View.GONE else View.VISIBLE
        adapter.notifyDataSetChanged();list.layoutManager?.onRestoreInstanceState(saved)
        footer.removeAllViews();actions.forEach{a->footer.addView(ClassicUi.button(activity,a.title){if(a.enabled)a.run()}.apply{
            tag=a.key;isEnabled=a.enabled;isFocusable=a.enabled;alpha=if(a.enabled)1f else 0.45f
        },LinearLayout.LayoutParams(0,u(40),1f).apply{marginEnd=u(7);topMargin=u(10)})}
        list.post{
            val key=focus?.takeIf{k->rows.any{it.key==k&&it.enabled}||actions.any{it.key==k&&it.enabled}}
                ?:rows.firstOrNull{it.selected&&it.enabled&&!it.header}?.key
                ?:rows.getOrNull(old.coerceAtLeast(0))?.takeIf{it.enabled&&!it.header}?.key
                ?:rows.firstOrNull{it.enabled&&!it.header}?.key
            val node=key?.let{root.findViewWithTag<View>(it)}
            if(node!=null)node.requestFocus()else if(key!=null){val index=rows.indexOfFirst{it.key==key};if(index>=0){list.scrollToPosition(index);list.post{root.findViewWithTag<View>(key)?.requestFocus()}}}
            else if(actions.isNotEmpty())footer.getChildAt(actions.indexOfFirst{it.enabled}.coerceAtLeast(0))?.requestFocus()
        }
    }
    fun focus(key:String){root.post{root.findViewWithTag<View>(key)?.requestFocus()}}
    internal fun rowKeys()=rows.map{it.key}
    internal fun select(key:String){if(rows.any{it.key==key&&it.enabled&&!it.header})click(key)else root.findViewWithTag<View>(key)?.performClick()}
    internal fun focusedKey()=dialog.currentFocus?.tag as? String
    internal fun titles()=rows.map{it.title}
    override fun close(){dialog.dismiss()}
}
