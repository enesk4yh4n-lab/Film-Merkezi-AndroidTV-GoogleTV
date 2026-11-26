package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** A failed episode page never prevents verified sibling sites from preparing sources. */
object EpisodeSourcePool {
    private fun explicit(c:Card):Boolean {
        val number=c.episodeNumber?:return false
        val parent=c.seriesRef?:c.ref.copy(kind=ContentKind.SERIES)
        return EpisodeMerge.explicit(Episode(c,parent,number))
    }
    fun canDiscover(card:Card)=card.episodeNumber!=null&&explicit(card)
    fun matches(target:Card,candidate:Card):Boolean {
        if(candidate.ref.kind!=ContentKind.EPISODE)return false
        if(SeriesIdentity.sourceKey(target)==SeriesIdentity.sourceKey(candidate))return true
        return target.episodeNumber!=null&&target.episodeNumber==candidate.episodeNumber&&
            target.seriesIdentity.isNotBlank()&&target.seriesIdentity==candidate.seriesIdentity&&explicit(target)&&explicit(candidate)
    }
    fun seeds(item:UnifiedItem,crossSite:Boolean):List<Card> = (listOf(item.card)+item.alternatives)
        .filter{(crossSite||it.ref.siteId==item.card.ref.siteId)&&matches(item.card,it)}.distinctBy(SeriesIdentity::sourceKey)

    fun observe(item:UnifiedItem,crossSite:Boolean,detail:suspend(Card)->Detail,
        discover:(Set<String>)->Flow<SourcePool>,gate:suspend()->Unit={}):Flow<SourcePool> = flow {
        val candidates=seeds(item,crossSite)
        val known=candidates.map(SeriesIdentity::sourceKey).toSet()
        val local=channelFlow {
            val options=mutableListOf<SourceOption>();val errors=mutableListOf<SiteFailure>();val lock=Mutex();val slots=Semaphore(2)
            send(SourcePool(emptyList(),true,"Bölüm kaynakları hazırlanıyor"))
            coroutineScope{candidates.forEach{card->launch{slots.withPermit{
                try{
                    gate()
                    val found=withTimeout(20000){detail(card)}
                    if(found.card.episodeNumber!=null&&card.episodeNumber!=null&&found.card.episodeNumber!=card.episodeNumber)
                        throw DataFailure(FailureKind.PARSE,"Yanıt farklı bir bölüme ait","episode-sources")
                    val added=found.options.filter{it.content.siteId==card.ref.siteId}.map{it.scoped()}
                    lock.withLock{
                        options.addAll(added.filter{a->options.none{it.id==a.id}})
                        if(added.isEmpty())errors.add(SiteFailure(card.ref.siteId,FailureKind.UNSUPPORTED,"Bu sitede bölüm için kaynak bulunamadı"))
                    }
                }catch(e:CancellationException){if(e !is TimeoutCancellationException)throw e
                    lock.withLock{errors.add(SiteFailure(card.ref.siteId,FailureKind.TIMEOUT,"Bölüm sayfası süre sınırını aştı"))}}
                catch(e:Exception){lock.withLock{errors.add(SiteFailure(card.ref.siteId,(e as? DataFailure)?.kind?:FailureKind.PARSE,e.message?:"Bölüm kaynakları alınamadı",(e as? DataFailure)?.httpCode))}}
                lock.withLock{send(SourcePool(options.toList(),true,"Bölüm kaynakları güncelleniyor",errors.toList()))}
            }}}}
            send(SourcePool(options.toList(),false,"",errors.toList()))
        }
        val remote=if(crossSite&&canDiscover(item.card))discover(known).catch{e->
            if(e is CancellationException)throw e
            emit(SourcePool(emptyList(),false,errors=listOf(SiteFailure("all",(e as? DataFailure)?.kind?:FailureKind.PARSE,e.message?:"Diğer siteler aranamadı"))))
        }else flowOf(SourcePool(emptyList(),false))
        var last=SourcePool(emptyList(),true,"Bölüm kaynakları hazırlanıyor")
        emit(last)
        val complete=withTimeoutOrNull(90000){combine(local,remote){a,b->
            val options=(a.options+b.options).distinctBy{it.id};val pending=a.pending||b.pending
            SourcePool(options,pending,b.progress.ifBlank{if(pending)a.progress else if(options.any{it.state==OptionState.AVAILABLE})"Bölüm kaynakları hazır"else "Bölüm kaynakları tamamlandı"},
                (a.errors+b.errors).distinctBy{listOf(it.siteId,it.kind,it.message)})
        }.collect{last=it;emit(it)};true}?:false
        if(!complete)emit(last.copy(pending=false,progress="Kaynak keşfi süre sınırı; sonuç kısmi",
            errors=last.errors+SiteFailure("all",FailureKind.TIMEOUT,"Bölüm kaynakları süre sınırı")))
    }
}
