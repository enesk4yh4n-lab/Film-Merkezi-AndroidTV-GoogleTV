package app.filmmerkezi.tv.tv

import android.view.*

/** Horizontal controls remain in their row at either edge. Vertical navigation stays native. */
internal object ClassicDirections {
    fun standalone(view:View){view.setOnKeyListener{_,code,_->code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT}}
    fun horizontal(row:ViewGroup){
        for(i in 0 until row.childCount)row.getChildAt(i).setOnKeyListener{v,code,event->
            if(code!=KeyEvent.KEYCODE_DPAD_LEFT&&code!=KeyEvent.KEYCODE_DPAD_RIGHT)false
            else {
                if(event.action==KeyEvent.ACTION_DOWN){
                    val choices=(0 until row.childCount).map(row::getChildAt).filter{it.isShown&&it.isEnabled&&it.isFocusable}
                    val index=choices.indexOf(v)
                    choices.getOrNull((index+if(code==KeyEvent.KEYCODE_DPAD_LEFT)-1 else 1).coerceIn(0,(choices.size-1).coerceAtLeast(0)))?.requestFocus()
                };true
            }
        }
    }
}
