package app.filmmerkezi.tv.tv

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.*
import android.widget.*
import app.filmmerkezi.tv.R

/** Native, resolution-scaled rendering of the 1920x1080 Film Merkezi catalog. No WebView. */
internal object ClassicUi {
    private fun fontScale(c:Context)=if(app.filmmerkezi.tv.settings.SettingsRepository.get(c).value.largeText)1.15f else 1f
    val background=Color.rgb(11,16,27);val header=Color.rgb(14,21,34)
    val gold=Color.rgb(237,195,101);val foreground=Color.rgb(238,241,245);val muted=Color.rgb(146,159,177)
    fun u(c:Context,value:Float)=(c.resources.displayMetrics.widthPixels/960f*value).toInt()
    fun text(c:Context,value:String,size:Float=12f,color:Int=foreground,bold:Boolean=false)=TextView(c).apply{
        text=value;setTextColor(color);setTextSize(TypedValue.COMPLEX_UNIT_PX,u(c,size*fontScale(c)).toFloat());includeFontPadding=false
        typeface=if(bold)Typeface.create("sans-serif",Typeface.BOLD)else Typeface.create("sans-serif",Typeface.NORMAL)
    }
    fun shape(c:Context,color:Int,radius:Float=4f,stroke:Int=Color.TRANSPARENT,width:Float=0.5f)=GradientDrawable().apply{
        setColor(color);cornerRadius=u(c,radius).toFloat();if(stroke!=Color.TRANSPARENT)setStroke(u(c,width).coerceAtLeast(1),stroke)
    }
    fun focus(c:Context,flat:Boolean=false,goldFill:Boolean=false)=StateListDrawable().apply{
        addState(intArrayOf(android.R.attr.state_focused),shape(c,if(goldFill)gold else 0xFF26374C.toInt(),3f,0xFFFFCA55.toInt(),1.5f))
        addState(intArrayOf(android.R.attr.state_selected),shape(c,if(flat)Color.TRANSPARENT else 0xFF302D26.toInt(),3f,if(flat)Color.TRANSPARENT else gold,0.5f))
        addState(intArrayOf(),shape(c,if(goldFill)gold else if(flat)Color.TRANSPARENT else 0xFF1A2636.toInt(),3f,if(flat)Color.TRANSPARENT else 0xFF344258.toInt()))
    }
    fun button(c:Context,value:String,id:Int=View.generateViewId(),goldFill:Boolean=false,flat:Boolean=false,action:()->Unit)=Button(c).apply{
        this.id=id;text=value;isAllCaps=false;setTextColor(if(goldFill)0xFF171D27.toInt()else ClassicUi.foreground)
        setTextSize(TypedValue.COMPLEX_UNIT_PX,u(c,12f*fontScale(c)).toFloat());includeFontPadding=false
        typeface=Typeface.create("sans-serif",if(goldFill)Typeface.BOLD else Typeface.NORMAL)
        background=focus(c,flat,goldFill);stateListAnimator=null;isFocusable=true;isFocusableInTouchMode=true
        minimumHeight=0;minHeight=0;minimumWidth=0;minWidth=0;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
        setPadding(u(c,if(goldFill)6f else 11f),0,u(c,if(goldFill)6f else 11f),0)
        setOnClickListener{action()}
    }
    data class Shell(val root:FrameLayout,val query:EditText,val navigation:Map<TvScreen,Button>)
    fun shell(c:Context,screen:TvScreen,query:String,siteName:String,allowed:(TvScreen)->Boolean,
        navigate:(TvScreen)->Unit,search:(String)->Unit,recent:()->Unit,sites:()->Unit,options:()->Unit,down:()->Unit):Shell {
        val root=FrameLayout(c).apply{setBackgroundColor(header)}
        fun put(v:View,x:Float,y:Float,w:Float,h:Float){root.addView(v,FrameLayout.LayoutParams(u(c,w),u(c,h)).apply{leftMargin=u(c,x);topMargin=u(c,y)})}
        put(ImageView(c).apply{setImageResource(R.drawable.classic_logo);scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription=c.getString(R.string.native_app_name)},36f,13f,34f,34f)
        put(text(c,c.getString(R.string.native_app_name),16f,bold=true),78f,15f,115f,22f)
        val version=c.packageManager.getPackageInfo(c.packageName,0).versionName.orEmpty()
        put(text(c,"v"+version,9f,gold),174f,22f,70f,12f)
        put(text(c,c.getString(R.string.classic_tagline),7.5f,muted).apply{letterSpacing=0.18f},78f,37f,160f,12f)
        val input=EditText(c).apply{id=R.id.tv_query;isSingleLine=true;hint=c.getString(R.string.classic_search_hint)
            setText(query);setTextColor(ClassicUi.foreground);setHintTextColor(0xFF9DA8B9.toInt());includeFontPadding=false
            setTextSize(TypedValue.COMPLEX_UNIT_PX,u(c,13f).toFloat());setPadding(u(c,14f),0,u(c,8f),0)
            background=focus(c);imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener{_,action,event->if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH||event?.keyCode==KeyEvent.KEYCODE_ENTER){search(text.toString());true}else false}
            setOnKeyListener{_,key,event->if(key==KeyEvent.KEYCODE_DPAD_DOWN&&event.action==KeyEvent.ACTION_DOWN){down();true}else false}
        }
        put(input,250f,12f,325f,34f)
        put(button(c,c.getString(R.string.tv_search),R.id.tv_search_submit){search(input.text.toString())},575f,12f,50f,34f)
        put(button(c,"×",R.id.classic_search_clear){input.setText("");input.requestFocus()}.apply{setPadding(0,0,0,0)},625f,12f,30f,34f)
        put(button(c,"↺",R.id.classic_search_recent){recent()}.apply{setPadding(0,0,0,0)},655f,12f,30f,34f)
        put(button(c,c.getString(R.string.classic_options),R.id.tv_nav_about){options()}.apply{setPadding(u(c,4f),0,u(c,4f),0)},846f,14f,78f,26f)
        put(button(c,c.getString(R.string.classic_site_name,siteName),R.id.tv_site_picker){sites()}.apply{
            setTextSize(TypedValue.COMPLEX_UNIT_PX,u(c,8f).toFloat());setPadding(0,0,0,0)
        },841f,45f,92f,20f)
        val tabs=listOf(Triple(TvScreen.HOME,R.string.tv_home,R.id.tv_nav_home),Triple(TvScreen.MOVIES,R.string.tv_movies,R.id.tv_nav_movies),
            Triple(TvScreen.SERIES,R.string.tv_series,R.id.tv_nav_series),Triple(TvScreen.DISCOVER,R.string.classic_discover,R.id.tv_nav_discover),
            Triple(TvScreen.LIBRARY,R.string.tv_my_list,R.id.tv_nav_library),Triple(TvScreen.HISTORY,R.string.tv_history,R.id.tv_nav_history))
        val navigation=linkedMapOf<TvScreen,Button>();var x=36f
        for((s,title,id)in tabs){if(!allowed(s))continue
            val active=screen==s
            val b=button(c,c.getString(title),id,flat=true){navigate(s)}.apply{
                isSelected=active;setTextColor(if(active)gold else 0xFFADB7C7.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_PX,u(c,13.5f).toFloat())
                setPadding(u(c,8f),0,u(c,8f),0)
                setOnKeyListener{_,key,event->if(event.action==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_DOWN){down();true}else false}
            }
            val width=if(s==TvScreen.HOME)86f else 64f
            put(b,x,54f,width,32f);navigation[s]=b
            if(active)put(View(c).apply{setBackgroundColor(gold)},x,83f,width,1.5f)
            x+=width+11f
        }
        put(View(c).apply{setBackgroundColor(0xFF263040.toInt())},0f,87f,960f,0.5f)
        return Shell(root,input,navigation)
    }
}
