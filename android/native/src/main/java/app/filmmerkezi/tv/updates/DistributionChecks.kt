package app.filmmerkezi.tv.updates

import kotlinx.coroutines.CancellationException

enum class CheckState { UNCHECKED, CHECKING, CURRENT, AVAILABLE, FAILED, CANCELLED }
data class RepositoryCheck(val key:String,val name:String,val state:CheckState=CheckState.UNCHECKED,val error:String="")

/** Independent outcomes: failure of one repository never overwrites application or sibling status. */
object DistributionChecks {
    suspend fun run(start:DistributionStatus,installedCode:Int,repositories:List<RepositoryCheck>,
        application:suspend()->UpdateProtocol.Release,refresh:suspend(String)->Unit,count:()->Int,
        now:()->Long={System.currentTimeMillis()},emit:(DistributionStatus)->Unit) {
        var current=start.copy(busy=true,appCheck=CheckState.CHECKING,appError="",pluginError="",repositoryChecks=repositories.associateBy{it.key})
        fun publish(){emit(current)}
        publish()
        try {
            try {
                val release=application()
                current=current.copy(release=release.takeIf{it.code>installedCode},appCheck=if(release.code>installedCode)CheckState.AVAILABLE else CheckState.CURRENT)
            }catch(e:CancellationException){throw e}catch(e:Exception){current=current.copy(appCheck=CheckState.FAILED,appError=e.message?:"Uygulama kontrol edilemedi")}
            publish()
            for(repo in repositories){
                current=current.copy(repositoryChecks=current.repositoryChecks+(repo.key to repo.copy(state=CheckState.CHECKING)));publish()
                val result=try{refresh(repo.key);repo.copy(state=CheckState.CURRENT)}catch(e:CancellationException){throw e}
                    catch(e:Exception){repo.copy(state=CheckState.FAILED,error=e.message?:"Depo kontrol edilemedi")}
                current=current.copy(repositoryChecks=current.repositoryChecks+(repo.key to result));publish()
            }
            current=try{current.copy(moduleUpdates=count(),checkedAt=now())}catch(e:Exception){current.copy(pluginError=e.message?:"Eklenti sayısı okunamadı",checkedAt=now())}
        }catch(e:CancellationException){
            current=current.copy(appCheck=if(current.appCheck==CheckState.CHECKING)CheckState.CANCELLED else current.appCheck,
                repositoryChecks=current.repositoryChecks.mapValues{(_,r)->if(r.state==CheckState.CHECKING)r.copy(state=CheckState.CANCELLED)else r})
            throw e
        }finally{current=current.copy(busy=false);publish()}
    }
}
