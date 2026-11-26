package app.filmmerkezi.tv.tv

import android.content.Context

/** A future Modern renderer will share the same data, library and player. */
internal class UiPreferences(context:Context){
    private val store=context.applicationContext.getSharedPreferences("interface-preferences",0)
    val configured get()=store.all["configured"]==true&&store.all["style"]=="classic"
    fun classic(){store.edit().putString("style","classic").putBoolean("configured",true).apply()}
}
