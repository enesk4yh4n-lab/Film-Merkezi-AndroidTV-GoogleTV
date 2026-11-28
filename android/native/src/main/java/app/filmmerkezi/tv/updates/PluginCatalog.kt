package app.filmmerkezi.tv.updates

import org.json.JSONObject

data class CatalogPlugin(val key:String,val repository:String,val repositoryName:String,val entry:UpdateProtocol.Entry,
    val enabled:Boolean,val installed:InstalledModule?,val prepared:InstalledModule?,val appCode:Int){
    val compatible get()=enabled&&entry.minApp<=appCode
    val ready get()=prepared?.module?.version==entry.version
    val fresh get()=installed==null&&!ready&&compatible
    val update get()=installed!=null&&!ready&&compatible&&UpdateProtocol.compare(entry.version,installed.module.version)>0
    val token get()=if(installed==null)"new:$key"else "plugin:$key@${entry.version}"
}

/** Only verified repository indexes; no provider/search requests are needed to count plugins. */
class PluginCatalog(state:JSONObject,repositories:List<JSONObject>,indices:Map<String,UpdateProtocol.Index>,val appCode:Int,val errors:Map<String,String> = emptyMap()){
    val restartRequired=state.optBoolean("needsBoot")||state.optBoolean("bootPending")
    val pending=state.has("pending")||restartRequired
    private val active=PluginInventory.modules(state.getJSONObject("packages"))
    private val prepared=state.optJSONObject("pending")?.let(PluginInventory::modules).orEmpty()
    val installedCount=active.values.count{it.module.type=="site"}
    val repositoryCount=repositories.size
    val enabledRepositories=repositories.filter{it.optBoolean("enabled",true)}.map{it.getString("key")}.toSet()
    val plugins=repositories.flatMap{repo->
        val repository=repo.getString("key");val fp=repo.getString("fingerprint")
        indices[repository]?.packages.orEmpty().filter{it.type=="site"}.groupBy{it.id}.values.map{versions->
            val entry=versions.maxWithOrNull{a,b->UpdateProtocol.compare(a.version,b.version)}!!;val key="$fp/${entry.id}"
            CatalogPlugin(key,repository,repo.getString("name"),entry,repo.optBoolean("enabled",true),active[key],prepared[key],appCode)
        }
    }.groupBy{it.key}.values.map{copies->copies.maxWithOrNull(compareBy<CatalogPlugin>{it.enabled}.thenComparator{a,b->UpdateProtocol.compare(a.entry.version,b.entry.version)})!!}
        .sortedWith(compareBy<CatalogPlugin>{Regex("^p(\\d{3})$").matchEntire(it.entry.id)?.groupValues?.get(1)?.toIntOrNull()?:Int.MAX_VALUE}.thenBy{it.entry.name}.thenBy{it.repositoryName})
    val availableCount get()=plugins.count{it.compatible}
    val fresh get()=plugins.filter{it.fresh}
    val updates get()=plugins.filter{it.update}
}

enum class UpdatePage { HOME,CATALOG,INSTALLED,UPDATES,APPLICATION,REPOSITORIES,ADVANCED }
data class UpdateRoute(val page:UpdatePage=UpdatePage.HOME,val install:List<String> = emptyList(),val downloadApplication:Boolean=false,val applicationCode:Int=0)

/** Download verification stays atomic. Background/playing sessions defer activation to a visible page. */
object InstallCompletion {
    suspend fun finish(canApply:()->Boolean,activate:suspend()->Unit,restart:suspend()->Unit,defer:()->Unit){
        if(canApply()){activate();if(canApply())restart()else defer()}else defer()
    }
}
