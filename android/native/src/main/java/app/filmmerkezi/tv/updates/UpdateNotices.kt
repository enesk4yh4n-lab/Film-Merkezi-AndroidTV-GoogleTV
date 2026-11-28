package app.filmmerkezi.tv.updates

import org.json.JSONArray
import org.json.JSONObject

enum class NoticeKind { APPLICATION,NEW_PLUGINS,PLUGIN_UPDATES }
enum class NoticeDecision { LATER,IGNORE }
data class NoticeState(val ignored:Set<String> = emptySet(),val later:Map<String,Long> = emptyMap(),val disabled:Set<NoticeKind> = emptySet()){
    fun permits(token:String,kind:NoticeKind,now:Long)=kind !in disabled&&token !in ignored&&(later[token]?:0)<=now
    fun decide(tokens:List<String>,decision:NoticeDecision,now:Long)=when(decision){
        NoticeDecision.IGNORE->copy(ignored=ignored+tokens,later=later-tokens.toSet())
        NoticeDecision.LATER->copy(later=later.filterValues{it>now}+tokens.associateWith{now+24L*60*60*1000})
    }
    fun encode()=JSONObject().put("schemaVersion",1).put("ignored",JSONArray(ignored.toList())).put("later",JSONObject(later)).put("disabled",JSONArray(disabled.map{it.name})).toString()
    companion object {
        fun decode(raw:String?):NoticeState=try{
            if(raw==null)NoticeState()else{val o=JSONObject(raw);require(o.getInt("schemaVersion")==1)
                fun strings(name:String)=o.optJSONArray(name)?.let{a->(0 until a.length()).mapNotNull{a.optString(it).takeIf{it.isNotBlank()}}}.orEmpty()
                val later=o.optJSONObject("later")?:JSONObject()
                NoticeState(strings("ignored").toSet(),later.keys().asSequence().mapNotNull{k->later.opt(k).let{if(it is Number)k to it.toLong()else null}}.toMap(),strings("disabled").mapNotNull{runCatching{NoticeKind.valueOf(it)}.getOrNull()}.toSet())}
        }catch(_:Exception){NoticeState()}
    }
}
data class UpdateNotice(val kind:NoticeKind,val tokens:List<String>,val plugins:List<CatalogPlugin> = emptyList(),val release:UpdateProtocol.Release?=null)
object UpdateNoticePolicy {
    fun next(catalog:PluginCatalog,status:DistributionStatus,preferences:NoticeState,now:Long,installedCode:Int,deviceSdk:Int):UpdateNotice? {
        if(status.busy||catalog.pending)return null
        status.release?.takeIf{status.appCheck==CheckState.AVAILABLE&&it.code>installedCode&&it.minSdk<=deviceSdk}?.let{release->
            val token="app:${release.code}";if(preferences.permits(token,NoticeKind.APPLICATION,now))return UpdateNotice(NoticeKind.APPLICATION,listOf(token),release=release)
        }
        for((kind,entries)in listOf(NoticeKind.PLUGIN_UPDATES to catalog.updates,NoticeKind.NEW_PLUGINS to catalog.fresh)){
            val allowed=entries.filter{plugin->status.repositoryChecks[plugin.repository]?.state !in listOf(CheckState.FAILED,CheckState.CANCELLED,CheckState.CHECKING)&&plugin.repository !in catalog.errors&&preferences.permits(plugin.token,kind,now)}
            if(allowed.isNotEmpty())return UpdateNotice(kind,allowed.map{it.token},allowed)
        };return null
    }
}
