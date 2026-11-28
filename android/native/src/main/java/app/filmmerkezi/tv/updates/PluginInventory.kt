package app.filmmerkezi.tv.updates

import org.json.JSONObject

data class InstalledModule(val key:String,val repo:String,val module:UpdateProtocol.Module,val enabled:Boolean)
enum class PluginState { ACTIVE, UPDATE_AVAILABLE, READY, REMOVAL_READY, DISABLED, WITHDRAWN, NEWER_APP_REQUIRED }
data class PluginGroup(val key:String,val active:InstalledModule?,val prepared:InstalledModule?,val state:PluginState,
    val resolvers:List<InstalledModule>,val missingResolvers:List<String>,val available:UpdateProtocol.Entry?) {
    val current get()=prepared?:active!!
}

/** Read-only projection of active, prepared and repository sets. Stable site id folds renamed packages. */
class PluginInventory(state:JSONObject,private val indices:Map<String,UpdateProtocol.Index>,private val appCode:Int) {
    companion object {
        fun modules(set:JSONObject)=set.keys().asSequence().associateWith{key->val r=set.getJSONObject(key)
            InstalledModule(key,r.getString("repo"),UpdateProtocol.module(r.getString("manifest").toByteArray(Charsets.UTF_8)),r.optBoolean("enabled",true))}
    }
    val active=modules(state.getJSONObject("packages"))
    val pending=state.optJSONObject("pending")?.let(::modules)
    val effective=pending?:active
    val providers:List<PluginGroup>
    val unusedResolvers:List<InstalledModule>
    init {
        fun logical(m:InstalledModule)=m.key.substringBefore('/')+"/"+m.module.site!!.getString("id")
        val old=active.values.filter{it.module.type=="site"}.associateBy(::logical)
        val next=effective.values.filter{it.module.type=="site"}.associateBy(::logical)
        providers=(old.keys+next.keys).map{id->
            val a=old[id];val p=if(pending!=null)next[id]else null;val current=p?:a!!;val m=current.module
            val latest=indices[current.repo]?.packages?.filter{it.id==m.id}?.maxWithOrNull{l,r->UpdateProtocol.compare(l.version,r.version)}
            val dependencySet=if(pending!=null&&p==null)active else effective
            val deps=m.dependencies.mapNotNull{dependencySet[it.publisher+"/"+it.id]}
            val missing=m.dependencies.filter{dependencySet[it.publisher+"/"+it.id]?.enabled!=true}.map{it.id}
            val status=when {
                pending!=null&&p==null->PluginState.REMOVAL_READY
                pending!=null&&(a==null||a.key!=p?.key||a.module.hash!=p.module.hash||a.enabled!=p.enabled)->PluginState.READY
                !current.enabled||missing.isNotEmpty()->PluginState.DISABLED
                latest==null->PluginState.WITHDRAWN
                latest.minApp>appCode->PluginState.NEWER_APP_REQUIRED
                a!=null&&UpdateProtocol.compare(latest.version,a.module.version)>0->PluginState.UPDATE_AVAILABLE
                else->PluginState.ACTIVE
            }
            PluginGroup(current.key,a,p,status,deps,missing,latest)
        }.sortedWith(compareBy<PluginGroup>{Regex("^p(\\d{3})$").matchEntire(it.current.module.id)?.groupValues?.get(1)?.toIntOrNull()?:Int.MAX_VALUE}.thenBy{it.current.module.name})
        val used=effective.values.flatMap{it.module.dependencies}.map{it.publisher+"/"+it.id}.toSet()
        unusedResolvers=effective.values.filter{it.module.type=="resolver"&&it.key !in used}.sortedBy{it.module.id}
    }
}
