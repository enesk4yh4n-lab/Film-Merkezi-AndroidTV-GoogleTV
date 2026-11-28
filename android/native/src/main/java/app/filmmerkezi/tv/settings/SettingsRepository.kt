package app.filmmerkezi.tv.settings

import android.content.Context
import android.content.SharedPreferences
import app.filmmerkezi.tv.*
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SourceScanProfile { SELECTED, BACKUP, MENU }

internal val homeRowOrder=listOf("continue","following","movies","series","animation","drama-short","library")

data class AppSettings(
    val startScreen:String="HOME",val largeText:Boolean=false,val exitConfirmation:Boolean=true,
    val hiddenRows:Set<String> = emptySet(),val rowOrder:List<String> = homeRowOrder,
    val merge:Boolean=true,val hideAdult:Boolean=true,val disabledCatalog:Set<String> = emptySet(),val disabledSources:Set<String> = emptySet(),
    val siteOrder:List<String> = Sites.all.map{it.id},val selectedSite:String="",
    val automaticSources:Boolean=true,val crossSiteSources:Boolean=true,val autoNext:Boolean=true,
    val entry:String="ASK",val resumeRewind:Int=5,val diagnostics:Boolean=false,
    val player:PlaybackSettings=PlaybackSettings(),val siteAddresses:Map<String,SiteAddress> = emptyMap(),
    val searchSuggestions:Boolean=false,val suggestionSite:String="",val showPosters:Boolean=true,
    val matchingMode:MatchingMode=MatchingMode.SMART,val rememberTracks:Boolean=true,val sourceScan:SourceScanProfile=SourceScanProfile.BACKUP
) {
    fun installedSites()=Sites.orderedInstalled(siteOrder)
    fun catalog()=CatalogRequestOptions(merge,hideAdult,installedSites().map{it.id}.filter{it !in disabledCatalog},matchingMode)
    fun sourceAllowed(id:String)=Sites.isInstalled(id)&&id !in disabledSources
}

/** Pure, bounded decoding also handles older/wrong-type preferences without rewriting them. */
object SettingsCodec {
    val heights=listOf(0,360,480,720,1080,1440,2160)
    val frames=listOf(0,24,25,30,50,60)
    private fun Map<String,*>.b(k:String,d:Boolean)=this[k]as? Boolean?:d
    private fun Map<String,*>.s(k:String,d:String)=this[k]as? String?:d
    private fun Map<String,*>.i(k:String,d:Int)=this[k]as? Int?:d
    private fun lang(v:String,d:String)=v.takeIf{it.matches(Regex("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*"))}?:d
    private inline fun <reified T:Enum<T>> en(v:String,d:T)=enumValues<T>().firstOrNull{it.name==v}?:d
    fun read(values:Map<String,Any?>,legacy:Map<String,Any?> =emptyMap(),catalog:Map<String,Any?> =emptyMap()):AppSettings {
        fun b(k:String,d:Boolean)=values.b(k,legacy.b(k,d))
        fun i(k:String,d:Int)=values.i(k,legacy.i(k,d))
        fun s(k:String,d:String)=values.s(k,legacy.s(k,d))
        val sites=Sites.known.map{it.id};fun ids(k:String)=(values[k]as? Set<*>)?.filterIsInstance<String>()?.filter{it in sites}?.toSet().orEmpty()
        fun order(k:String,all:List<String>)=(values.s(k,"").split('|').filter{it in all}.distinct()+all).distinct()
        val rows=homeRowOrder
        val textMode=en(values.s("subtitlePolicy",if(legacy.b("textEnabled",true))"AUTO"else "OFF"),SubtitlePolicy.AUTO)
        return AppSettings(
            values.s("startScreen","HOME").takeIf{it in listOf("HOME","MOVIES","SERIES","DISCOVER","LIBRARY","HISTORY")}?:"HOME",
            values.b("largeText",false),values.b("exitConfirmation",true),
            (values["hiddenRows"]as? Set<*>)?.filterIsInstance<String>()?.filter{it in rows}?.toSet().orEmpty(),order("rowOrder",rows),
            values.b("merge",true),values.b("hideAdult",true),ids("disabledCatalog"),ids("disabledSources"),order("siteOrder",sites),
            values.s("selectedSite",catalog.s("siteId","")).takeIf{Sites.isInstalled(it)}.orEmpty(),
            b("automaticSources",true),b("crossSiteSources",true),b("autoNext",true),
            values.s("entry","ASK").takeIf{it in listOf("ASK","RESUME","RESTART")}?:"ASK",values.i("resumeRewind",5).takeIf{it in listOf(0,5,10)}?:5,b("diagnostics",false),
            PlaybackSettings(lang(s("audio","tr"),"tr"),lang(values.s("secondAudio","en"),"en"),values.b("sourceAudio",false),lang(s("text","tr"),"tr"),textMode,
                values.i("preferredHeight",legacy.i("height",0)).takeIf{it in heights}?:0,values.i("autoMaxHeight",0).takeIf{it in heights}?:0,values.i("autoMaxFps",0).takeIf{it in frames}?:0,
                en(values.s("sizing","FIT"),VideoSizing.FIT),en(values.s("buffer","AUTO"),BufferProfile.AUTO),
                values.i("seekSeconds",10).takeIf{it in listOf(5,10,15,30)}?:10,values.b("accelerate",true),values.i("controlsSeconds",5).takeIf{it in listOf(0,3,5,10)}?:5,
                values.b("customSubtitles",false),values.i("subtitleSize",1).coerceIn(0,3),values.b("subtitleYellow",false),values.b("subtitleOutline",true),
                values.i("subtitleBackground",0).coerceIn(0,2),values.i("subtitleBottom",8).takeIf{it in listOf(5,8,12,16)}?:8),SiteAddresses.decode(values["siteAddresses"]),
            searchSuggestions=values.b("searchSuggestions",false),suggestionSite=values.s("suggestionSite",""),
            showPosters=values.b("showPosters",true),matchingMode=en(values.s("matchingMode","SMART"),MatchingMode.SMART),
            rememberTracks=values.b("rememberTracks",true),sourceScan=en(values.s("sourceScan","BACKUP"),SourceScanProfile.BACKUP))
    }
    fun fields(v:AppSettings):Map<String,Any> = linkedMapOf(
        "schema" to 3,"searchSuggestions" to v.searchSuggestions,"suggestionSite" to v.suggestionSite,"showPosters" to v.showPosters,
        "matchingMode" to v.matchingMode.name,"rememberTracks" to v.rememberTracks,"sourceScan" to v.sourceScan.name,
        "siteAddresses" to SiteAddresses.encode(v.siteAddresses),"startScreen" to v.startScreen,"largeText" to v.largeText,"exitConfirmation" to v.exitConfirmation,
        "hiddenRows" to v.hiddenRows,"rowOrder" to v.rowOrder.joinToString("|"),"merge" to v.merge,"hideAdult" to v.hideAdult,
        "disabledCatalog" to v.disabledCatalog,"disabledSources" to v.disabledSources,"siteOrder" to v.siteOrder.joinToString("|"),"selectedSite" to v.selectedSite,
        "automaticSources" to v.automaticSources,"crossSiteSources" to v.crossSiteSources,"autoNext" to v.autoNext,"entry" to v.entry,"resumeRewind" to v.resumeRewind,"diagnostics" to v.diagnostics,
        "audio" to v.player.audio,"secondAudio" to v.player.secondAudio,"sourceAudio" to v.player.sourceAudio,"text" to v.player.text,"subtitlePolicy" to v.player.subtitles.name,
        "preferredHeight" to v.player.preferredHeight,"autoMaxHeight" to v.player.autoMaxHeight,"autoMaxFps" to v.player.autoMaxFps,"sizing" to v.player.sizing.name,"buffer" to v.player.buffer.name,
        "seekSeconds" to v.player.seekSeconds,"accelerate" to v.player.accelerate,"controlsSeconds" to v.player.controlsSeconds,"customSubtitles" to v.player.customSubtitles,
        "subtitleSize" to v.player.subtitleSize,"subtitleYellow" to v.player.subtitleYellow,"subtitleOutline" to v.player.subtitleOutline,"subtitleBackground" to v.player.subtitleBackground,"subtitleBottom" to v.player.subtitleBottom)
}

class SettingsRepository(context:Context,name:String="film-settings",private val persist:((SharedPreferences.Editor)->Boolean)?=null) {
    private val app=context.applicationContext
    private val store=app.getSharedPreferences(name,0)
    private val lock=Mutex()
    private fun read()=SettingsCodec.read(store.all,app.getSharedPreferences("player-preferences",0).all,app.getSharedPreferences("catalog-preferences",0).all)
    private val state=MutableStateFlow(read())
    val changes:StateFlow<AppSettings> = state.asStateFlow()
    val value get()=state.value
    private val managesSites=name=="film-settings"
    init{if(managesSites)Sites.configure(state.value.siteAddresses)}
    fun reload(){val v=read();if(managesSites)Sites.configure(v.siteAddresses);state.value=v}
    suspend fun migrate(){if(store.all["schema"]!=3)update{it}}
    suspend fun update(change:(AppSettings)->AppSettings):AppSettings=withContext(Dispatchers.IO){lock.withLock{
        val previous=store.all.toMap();val next=change(state.value);val e=store.edit()
        SettingsCodec.fields(next).forEach{(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v);is Set<*>->e.putStringSet(k,v.filterIsInstance<String>().toSet())}}
        if(!(persist?.invoke(e)?:e.commit())){
            // SharedPreferences mutates its in-process map even when the disk commit fails.
            // Restore that map too, so reopening this screen cannot expose the rejected value.
            val rollback=store.edit().clear()
            previous.forEach{(k,v)->when(v){is String->rollback.putString(k,v);is Boolean->rollback.putBoolean(k,v);is Int->rollback.putInt(k,v);is Long->rollback.putLong(k,v);is Float->rollback.putFloat(k,v);is Set<*>->rollback.putStringSet(k,v.filterIsInstance<String>().toSet())}}
            rollback.commit()
            throw DataFailure(FailureKind.STORAGE,"Ayar kaydedilemedi; önceki değer korundu","settings")
        }
        if(managesSites)Sites.configure(next.siteAddresses)
        state.value=next;next
    }}
    companion object {
        @Volatile private var instance:SettingsRepository?=null
        fun get(context:Context)=instance?:synchronized(this){instance?:SettingsRepository(context).also{instance=it}}
    }
}
