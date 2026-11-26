package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*

data class DiscoverySnapshot(val item:UnifiedItem,val completed:Int=0,val total:Int=Sites.all.size,val failures:List<SiteFailure> = emptyList(),val running:Boolean=true,val cached:Boolean=false)

/** Foreground-owned search. No global scope, no late updates after navigation cancellation. */
class AlternativeDiscovery(private val repository:CatalogRepository,private val works:WorkStore,private val allowed:()->List<String> = {Sites.all.map{it.id}}){
    fun discover(card:Card,force:Boolean=false,background:Boolean=false,gate:suspend()->Unit={}):Flow<DiscoverySnapshot> = channelFlow {
        var item=works.item(card)
        val queries=(listOf(ContentIdentity.cleanTitle(item.card.title,item.card.year),item.card.originalTitle)+item.alternatives.flatMap{listOf(it.originalTitle)+it.aliases}).filter{it.isNotBlank()}.distinctBy{TextKey.fold(it)}.take(2)
        val providers=allowed().mapNotNull(repository.providers::get).filter{p->val kind=ContentIdentity.facts(card).kind;if(kind==ContentKind.SERIES)p.site.seriesPath!=null else p.site.id !in setOf("dizibox","ddizi","diziboxum")}
        val signature="|sites:"+providers.joinToString(","){it.site.id}
        if(!force&&works.fresh(item.identity,signature)){send(DiscoverySnapshot(item,providers.size,providers.size,running=false,cached=true));return@channelFlow}
        val slots=Semaphore(if(background)1 else 2);val lock=Mutex();var done=0;val errors=mutableListOf<SiteFailure>();var inspected=0
        send(DiscoverySnapshot(item,0,providers.size))
        val complete=withTimeoutOrNull(90000){supervisorScope{providers.forEach{p->launch{slots.withPermit{
            try{
                val candidates=mutableListOf<Card>()
                for(q in queries){currentCoroutineContext().ensureActive();gate();candidates.addAll(p.search(q).cards.filter{c->queries.any{ContentIdentity.relevant(c,it)}});if(candidates.any{ContentIdentity.equivalent(card,it)})break}
                val ordered=candidates.distinctBy{it.ref.id}.sortedByDescending{c->queries.count{ContentIdentity.relevant(c,it)}}
                for(raw in ordered.take(3)){
                    var checked=raw
                    val shouldCheck=lock.withLock{if(inspected<12){inspected++;true}else false}
                    if(shouldCheck){gate();checked=p.detail(raw).card}
                    val target=lock.withLock{item}
                    checked=ContentIdentity.corroborate(checked,target.alternatives)
                    val same=target.alternatives.any{ContentIdentity.decide(checked,it).state==IdentityState.SAME}&&target.alternatives.none{ContentIdentity.decide(checked,it).state==IdentityState.DIFFERENT}
                    // Keep independently checked candidates, including conflicting remakes,
                    // as separate evidence. The identity service can then corroborate an
                    // unknown season in either direction and revoke ambiguous derived years.
                    val evidence=ContentIdentity.facts(checked).checked&&target.alternatives.any{ContentIdentity.candidateNames(it).intersect(ContentIdentity.candidateNames(checked)).isNotEmpty()}
                    if(same||evidence){
                        works.observe(listOf(checked));lock.withLock{item=works.item(card)}
                    }
                }
            }catch(e:CancellationException){throw e}catch(e:Exception){lock.withLock{errors.add(SiteFailure(p.site.id,(e as? DataFailure)?.kind?:FailureKind.PARSE,e.message?:"Site hatası",(e as? DataFailure)?.httpCode))}}
            lock.withLock{done++;send(DiscoverySnapshot(item,done,providers.size,errors.toList()))}
        }}}};true}?:false
        if(complete&&errors.isEmpty())works.discovered(item.identity,signature)
        if(!complete)errors.add(SiteFailure("all",FailureKind.TIMEOUT,"Alternatif arama süre sınırı; sonuç kısmi"))
        send(DiscoverySnapshot(item,done,providers.size,errors.toList(),running=false))
    }
}
