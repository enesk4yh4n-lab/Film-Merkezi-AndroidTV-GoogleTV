package app.filmmerkezi.tv.tv

import android.content.Context
import app.filmmerkezi.tv.core.Sites

internal class CatalogPreferences(context:Context){
    private val settings=app.filmmerkezi.tv.settings.SettingsRepository.get(context)
    val siteId get()=settings.value.selectedSite.takeUnless{it in settings.value.disabledCatalog}.orEmpty()
    suspend fun select(id:String){require(id.isEmpty()||Sites.all.any{it.id==id});settings.update{it.copy(selectedSite=id)}}
}
