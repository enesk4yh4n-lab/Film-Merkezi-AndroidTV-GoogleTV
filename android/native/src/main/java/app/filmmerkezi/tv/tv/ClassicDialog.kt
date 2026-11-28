package app.filmmerkezi.tv.tv

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.filmmerkezi.tv.R

/** Classic application menus, including forms. No platform alert/list/choice popup. */
internal class ClassicDialog private constructor(private val spec:Builder):Dialog(spec.activity) {
    private val activity=spec.activity
    private val origin=activity.currentFocus
    private val choices=spec.items.toList()
    private val checked=BooleanArray(choices.size){if(spec.multiple!=null)spec.multiple!!.getOrElse(it){false}else it==spec.selected}
    private val buttons=mutableMapOf<Int,Button>()
    private val backButton=ClassicUi.button(activity,activity.getString(R.string.tv_back)){cancel()}.apply{tag="classic-dialog:back"}
    private var lastBody:View?=null
    private var contentScroll:ScrollView?=null
    private val recycler=RecyclerView(activity).apply{layoutManager=LinearLayoutManager(activity);itemAnimator=null;overScrollMode=View.OVER_SCROLL_NEVER}
    val choiceCount get()=choices.size
    fun isChoiceChecked(index:Int)=checked.getOrElse(index){false}
    fun getButton(which:Int)=checkNotNull(buttons[which])
    fun choose(index:Int){
        if(index !in choices.indices)return
        when {
            spec.multiple!=null->{checked[index]=!checked[index];recycler.adapter?.notifyItemChanged(index);spec.onMultiple?.invoke(this,index,checked[index])}
            spec.single->{checked.fill(false);checked[index]=true;recycler.adapter?.notifyDataSetChanged();spec.onChoice?.invoke(this,index)}
            else->{dismiss();spec.onChoice?.invoke(this,index)}
        }
    }
    private fun u(n:Int)=ClassicUi.u(activity,n.toFloat())
    private class Holder(val button:Button):RecyclerView.ViewHolder(button)
    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;tag="classic-dialog";setPadding(u(22),u(20),u(22),u(18));background=ClassicUi.shape(activity,0xFF142233.toInt(),8f,0xFF344258.toInt())}
        val heading=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        heading.addView(ClassicUi.text(activity,spec.title,20f,bold=true),LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(backButton,LinearLayout.LayoutParams(u(58),u(30)))
        root.addView(heading)
        if(spec.message.isNotBlank())root.addView(ClassicUi.text(activity,spec.message,12f,ClassicUi.muted),LinearLayout.LayoutParams(-1,-2).apply{topMargin=u(12);bottomMargin=u(10)})
        if(choices.isNotEmpty()){
            recycler.adapter=object:RecyclerView.Adapter<Holder>(){
                override fun getItemCount()=choices.size
                override fun onCreateViewHolder(parent:ViewGroup,type:Int)=Holder(ClassicUi.button(activity,""){}.apply{
                    gravity=Gravity.LEFT or Gravity.CENTER_VERTICAL;setPadding(u(12),u(8),u(12),u(8));minimumHeight=u(38)
                    layoutParams=RecyclerView.LayoutParams(-1,-2).apply{bottomMargin=u(6)}
                })
                override fun onBindViewHolder(holder:Holder,index:Int){holder.button.apply{
                    tag="classic-dialog-row:$index";text=(if(checked[index])"✓ "else "")+choices[index]
                    isSelected=checked[index];setTextColor(if(checked[index])ClassicUi.gold else ClassicUi.foreground)
                    contentDescription=choices[index]+if(checked[index])" · "+activity.getString(R.string.ux_selected)else ""
                    setOnClickListener{choose(holder.bindingAdapterPosition)}
                }}
            }
            root.addView(recycler,LinearLayout.LayoutParams(-1,0,1f).apply{topMargin=u(12)})
        }
        spec.content?.let{view->val scroll=if(view is ScrollView)view else ScrollView(activity).apply{isFillViewport=false;addView(view)};contentScroll=scroll;root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f).apply{topMargin=u(12)})}
        val footer=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL}
        spec.actions.forEach{(which,a)->val button=ClassicUi.button(activity,a.first){dismiss();a.second?.invoke(this,which)}.apply{tag="classic-dialog-action:$which"}
            buttons[which]=button;footer.addView(button,LinearLayout.LayoutParams(0,u(40),1f).apply{topMargin=u(12);marginEnd=u(7)})}
        if(buttons.isNotEmpty())root.addView(footer)
        root.addView(ClassicUi.text(activity,activity.getString(R.string.ux_panel_hint),10f,ClassicUi.muted),LinearLayout.LayoutParams(-1,-2).apply{topMargin=u(10)})
        setContentView(root);setCanceledOnTouchOutside(false)
        window?.apply{setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);setDimAmount(0.62f);setGravity(Gravity.CENTER);attributes=attributes.apply{windowAnimations=0}}
        setOnDismissListener{origin?.takeIf{it.isShown}?.post{origin.requestFocus()}}
    }
    override fun dispatchKeyEvent(event:KeyEvent):Boolean {
        val code=event.keyCode
        if(code !in listOf(KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN))return super.dispatchKeyEvent(event)
        val focus=currentFocus?:return super.dispatchKeyEvent(event)
        fun go(target:View?):Boolean{if(event.action==KeyEvent.ACTION_DOWN)target?.requestFocus();return true}
        fun focusableBody():List<View> = spec.content?.getFocusables(View.FOCUS_FORWARD)?.filter{it !is ViewGroup&&it.isShown&&it.isEnabled}?:emptyList()
        fun body(last:Boolean=false):Boolean {
            if(event.action==KeyEvent.ACTION_DOWN){
                if(choices.isNotEmpty())focusChoice(if(last)choices.lastIndex else (lastBody?.tag?.toString()?.substringAfterLast(':')?.toIntOrNull()?:checked.indexOfFirst{it}).coerceAtLeast(0))
                else (lastBody?.takeIf{it.isShown}?:if(last)focusableBody().lastOrNull()else focusableBody().firstOrNull())?.requestFocus()
            };return true
        }
        if(focus===backButton)return when(code){KeyEvent.KEYCODE_DPAD_DOWN->if(choices.isNotEmpty()||focusableBody().isNotEmpty())body()else go(buttons.values.firstOrNull());else->true}
        val actions=buttons.values.filter{it.isShown&&it.isEnabled};val action=actions.indexOf(focus)
        if(action>=0)return when(code){
            KeyEvent.KEYCODE_DPAD_LEFT->go(actions.getOrNull((action-1).coerceAtLeast(0)))
            KeyEvent.KEYCODE_DPAD_RIGHT->go(actions.getOrNull((action+1).coerceAtMost(actions.lastIndex)))
            KeyEvent.KEYCODE_DPAD_UP->if(choices.isNotEmpty()||focusableBody().isNotEmpty())body(true)else go(backButton)
            else->true
        }
        val index=focus.tag?.toString()?.takeIf{it.startsWith("classic-dialog-row:")}?.substringAfterLast(':')?.toIntOrNull()
        if(index!=null){
            lastBody=focus
            if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT)return true
            if(event.action==KeyEvent.ACTION_UP)return true
            val next=index+if(code==KeyEvent.KEYCODE_DPAD_UP)-1 else 1
            if(next<0)backButton.requestFocus()else if(next>=choices.size)(actions.firstOrNull()?:focus).requestFocus()else focusChoice(next)
            return true
        }
        val fields=focusableBody();val field=fields.indexOf(focus)
        if(field>=0){
            lastBody=focus
            val horizontal=code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT
            if(horizontal&&focus is EditText)return super.dispatchKeyEvent(event)
            val container=window!!.decorView as ViewGroup
            fun bounds(node:View)=android.graphics.Rect().also{node.getDrawingRect(it);container.offsetDescendantRectToMyCoords(node,it)}
            val from=bounds(focus)
            val next=fields.filter{it!==focus}.mapNotNull{node->
                val rect=bounds(node)
                val aligned=if(horizontal)rect.bottom>from.top&&rect.top<from.bottom else rect.right>from.left&&rect.left<from.right
                val distance=when(code){KeyEvent.KEYCODE_DPAD_LEFT->from.left-rect.right;KeyEvent.KEYCODE_DPAD_RIGHT->rect.left-from.right;KeyEvent.KEYCODE_DPAD_UP->from.top-rect.bottom;else->rect.top-from.bottom}
                if(aligned&&distance>=0)node to (distance*10000L+kotlin.math.abs(if(horizontal)rect.centerY()-from.centerY()else rect.centerX()-from.centerX()))else null
            }.minByOrNull{it.second}?.first
            if(horizontal)return go(next?:focus)
            // Let the ScrollView move long text before leaving its content.
            val scrollDirection=if(code==KeyEvent.KEYCODE_DPAD_UP)-1 else 1
            if(fields.size==1&&focus !is Button&&focus !is EditText&&contentScroll?.canScrollVertically(scrollDirection)==true){if(event.action==KeyEvent.ACTION_DOWN)contentScroll?.let{it.scrollBy(0,scrollDirection*(it.height*0.7f).toInt().coerceAtLeast(1))};return true}
            return go(next?:if(code==KeyEvent.KEYCODE_DPAD_UP)backButton else actions.firstOrNull()?:focus)
        }
        return super.dispatchKeyEvent(event)
    }
    private fun focusChoice(index:Int){
        val target=index.coerceIn(0,choices.lastIndex)
        recycler.findViewHolderForAdapterPosition(target)?.itemView?.let{if(it.top>=0&&it.bottom<=recycler.height){it.requestFocus();return}}
        recycler.scrollToPosition(target)
        recycler.addOnLayoutChangeListener(object:View.OnLayoutChangeListener{
            override fun onLayoutChange(v:View,l:Int,t:Int,r:Int,b:Int,ol:Int,ot:Int,or:Int,ob:Int){recycler.removeOnLayoutChangeListener(this);recycler.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus()}
        });recycler.requestLayout()
    }
    override fun show(){
        super.show()
        val height=if(choices.isNotEmpty()||spec.content!=null)u(350).coerceAtMost((activity.resources.displayMetrics.heightPixels*0.86f).toInt())else -2
        window?.setLayout(u(650).coerceAtMost((activity.resources.displayMetrics.widthPixels*0.9f).toInt()),height)
        if(choices.isNotEmpty())recycler.post{
            val index=checked.indexOfFirst{it}.coerceAtLeast(0);recycler.scrollToPosition(index)
            recycler.post{recycler.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()}
        }else {
            val first=spec.content?.getFocusables(View.FOCUS_FORWARD)?.firstOrNull{it !is ViewGroup&&it.isShown&&it.isEnabled}
            if(first?.requestFocus()!=true&&spec.content?.requestFocus(View.FOCUS_DOWN)!=true)buttons[DialogInterface.BUTTON_POSITIVE]?.requestFocus()
        }
    }
    class Builder(internal val activity:Activity){
        internal var title="";internal var message="";internal var content:View?=null
        internal var items=emptyList<String>();internal var selected=-1;internal var single=false;internal var multiple:BooleanArray?=null
        internal var onChoice:((ClassicDialog,Int)->Unit)?=null;internal var onMultiple:((ClassicDialog,Int,Boolean)->Unit)?=null
        internal val actions=linkedMapOf<Int,Pair<String,((ClassicDialog,Int)->Unit)?>>()
        fun setTitle(value:String)=apply{title=value}
        fun setTitle(id:Int)=setTitle(activity.getString(id))
        fun setMessage(value:String)=apply{message=value}
        fun setMessage(id:Int)=setMessage(activity.getString(id))
        fun setView(value:View)=apply{content=value}
        fun setItems(values:Array<String>,choose:(ClassicDialog,Int)->Unit)=apply{items=values.toList();onChoice=choose}
        fun setSingleChoiceItems(values:Array<String>,checked:Int,choose:(ClassicDialog,Int)->Unit)=apply{items=values.toList();selected=checked;single=true;onChoice=choose}
        fun setMultiChoiceItems(values:Array<String>,checked:BooleanArray,choose:(ClassicDialog,Int,Boolean)->Unit)=apply{items=values.toList();multiple=checked.copyOf();onMultiple=choose}
        fun setPositiveButton(id:Int,click:((ClassicDialog,Int)->Unit)?)=apply{actions[DialogInterface.BUTTON_POSITIVE]=activity.getString(id) to click}
        fun setNegativeButton(id:Int,click:((ClassicDialog,Int)->Unit)?)=apply{actions[DialogInterface.BUTTON_NEGATIVE]=activity.getString(id) to click}
        fun setNeutralButton(id:Int,click:((ClassicDialog,Int)->Unit)?)=apply{actions[DialogInterface.BUTTON_NEUTRAL]=activity.getString(id) to click}
        fun create()=ClassicDialog(this)
        fun show()=create().also{it.show()}
    }
}

/** Form field whose choices use the same Classic window, with DPAD focus restored. */
internal class ClassicChoice(private val activity:Activity):FrameLayout(activity){
    private var values=emptyList<String>();private var popup:ClassicDialog?=null
    var selectedItemPosition=0;private set
    private val button=ClassicUi.button(activity,""){
        if(values.isNotEmpty())popup=ClassicDialog.Builder(activity).setTitle(contentDescription?.toString().orEmpty())
            .setSingleChoiceItems(values.toTypedArray(),selectedItemPosition){d,i->setSelection(i);d.dismiss()}.show()
    }
    init{addView(button,LayoutParams(-1,-1))}
    fun setOptions(options:List<String>){values=options.toList();setSelection(selectedItemPosition)}
    fun setSelection(index:Int){selectedItemPosition=index.coerceIn(0,(values.size-1).coerceAtLeast(0));button.text=values.getOrNull(selectedItemPosition).orEmpty();button.contentDescription=listOf(contentDescription?.toString().orEmpty(),button.text.toString()).filter{it.isNotBlank()}.joinToString(" · ")}
    internal fun popupForTest()=popup
    override fun onDetachedFromWindow(){popup?.dismiss();popup=null;super.onDetachedFromWindow()}
}
