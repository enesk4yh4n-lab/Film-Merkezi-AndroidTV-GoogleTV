package app.filmmerkezi.tv.updates

internal data class InstallJobItem(val key:String,val name:String,val status:String,val provider:Boolean,
    val dependencies:List<String>,val problem:UpdateProblem?=null,val httpCode:Int?=null)

internal class InstallJobSummary(val items:List<InstallJobItem>,private val availableDependencies:Set<String> = emptySet()) {
    private val byKey=items.associateBy{it.key}
    private val checked=mutableMapOf<String,Boolean>()
    private fun verified(key:String,path:Set<String>):Boolean {
        if(key in path)return false
        val item=byKey[key]?:return key in availableDependencies
        return checked.getOrPut(key){item.status=="ready"&&item.dependencies.all{verified(it,path+key)}}
    }
    val readyKeys=items.filter{verified(it.key,emptySet())}.map{it.key}.toSet()
    private val providers=items.filter{it.provider}
    val total=providers.size
    val completed=providers.count{it.key in readyKeys}
    val percent=if(total>0)completed*100/total else 0
    val complete=total>0&&completed==total
}

internal data class InstallJobStep(val key:String,val name:String,val phase:InstallPhase)
internal data class PluginInstallRun(val busy:Boolean=false,val plannedProviders:Int=0,val step:InstallJobStep?=null,
    val failure:UpdateProblem?=null)
