package app.filmmerkezi.tv.core

internal class CatalogAvailability(private val now:()->Long=System::currentTimeMillis) {
    private data class Site(var failures:Int=0,var until:Long=0L,var generation:Long=0)
    class Ticket internal constructor(val id:String,val generation:Long)
    private val sites=mutableMapOf<String,Site>()

    @Synchronized fun remaining(id:String):Long=(sites[id]?.until?.minus(now())?:0L).coerceAtLeast(0)
    @Synchronized fun request(id:String):Ticket? {
        if(remaining(id)>0L)return null
        return Ticket(id,sites.getOrPut(id,::Site).generation)
    }
    @Synchronized fun reset(id:String){
        val site=sites.getOrPut(id,::Site)
        site.until=0L;site.failures=0;site.generation++
    }
    @Synchronized fun result(ticket:Ticket,failure:SiteFailure?) {
        val site=sites[ticket.id]?:return
        if(site.generation!=ticket.generation)return
        if(failure==null){reset(ticket.id);return}
        val access=failure.kind in setOf(FailureKind.NETWORK,FailureKind.TIMEOUT,FailureKind.ACCESS)||
            failure.kind==FailureKind.HTTP&&failure.code?.let{it==401||it==403||it==429||it in 500..599}==true
        if(access&&++site.failures>=2){site.until=now()+30*60*1000L;site.generation++}
    }
}
