package app.filmmerkezi.tv.tv

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** A future Modern renderer will share the same data, library and player. */
internal class UiPreferences(context:Context){
    private val store=context.applicationContext.getSharedPreferences("interface-preferences",0)
    val configured get()=store.all["configured"]==true&&store.all["style"]=="classic"
    val language get()=(store.all["language"] as? String)?.takeIf{it=="en"}?:"tr"
    val step get()=(store.all["setup-step"] as? Int)?.coerceIn(0,4)?:0
    fun step(value:Int)=store.edit().putInt("setup-step",value).commit()
    fun language(value:String)=store.edit().putString("language",value).commit()
    fun classic()=store.edit().putString("style","classic").putBoolean("configured",true).remove("setup-step").commit()

    companion object {
        fun localized(context:Context):Context {
            val locale=Locale.forLanguageTag(UiPreferences(context).language)
            return context.createConfigurationContext(Configuration(context.resources.configuration).apply{setLocale(locale)})
        }
    }
}
