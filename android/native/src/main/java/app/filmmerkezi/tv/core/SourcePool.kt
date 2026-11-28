package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select

data class SourcePool(val options:List<SourceOption>,val pending:Boolean,val progress:String="",val errors:List<SiteFailure> = emptyList())
fun SourceOption.scoped()=if(id.startsWith(content.siteId+"|"))this else copy(id=content.siteId+"|"+TextKey.hash(content.pageUrl)+"|"+id,label=Sites.get(content.siteId).name+" · "+label)
internal fun SourcePool.withResolutionFailures(failures:Map<String,String>):SourcePool {
    if(failures.isEmpty())return this
    return copy(options=options.map{option->
        val failure=failures[option.id]
        if(option.state==OptionState.AVAILABLE&&failure!=null)option.copy(reason=failure)else option
    })
}

class SourcePoolService(private val repository:CatalogRepository,private val works:WorkStore,private val discovery:AlternativeDiscovery,private val allowed:(String)->Boolean={true},private val hideAdult:()->Boolean={false}){
    fun episode(item:UnifiedItem,crossSite:Boolean,gate:suspend()->Unit={}):Flow<SourcePool> =
        EpisodeSourcePool.observe(item.copy(alternatives=item.alternatives.filter{allowed(it.ref.siteId)}),crossSite,{c->
            if(!allowed(c.ref.siteId))throw DataFailure(FailureKind.ACCESS,"Bu sitenin kaynakları kapalı","site-settings")
            repository.detail(c).also{if(hideAdult()&&AdultContent.blocked(it.card))throw DataFailure(FailureKind.ACCESS,"Yetişkin filtresi","adult-filter")}
        },{known->observe(item.card,emptyList(),true,gate,known)},gate)
    fun observe(card:Card,initial:List<SourceOption>,crossSite:Boolean,gate:suspend()->Unit={},knownEpisodes:Set<String> = emptySet()):Flow<SourcePool> = flow{
        val options=initial.filter{allowed(it.content.siteId)}.map{it.scoped()}.toMutableList();val loaded=mutableSetOf(card.ref.siteId+":"+card.ref.pageUrl)
        emit(SourcePool(options.toList(),crossSite))
        if(!crossSite)return@flow
        if(card.ref.kind==ContentKind.EPISODE&&!EpisodeSourcePool.canDiscover(card)){
            emit(SourcePool(options.toList(),false,"Siteler arasında bölüm numarası doğrulanamadı"));return@flow
        }
        val target=if(card.ref.kind==ContentKind.EPISODE){
            works.cached(card.seriesIdentity)?.card?:card.seriesRef?.let{Card(it,card.title.substringBefore(" · "),year=card.year)}
                ?:return@flow emit(SourcePool(options.toList(),false,"Dizi kimliği bulunamadı"))
        }else card
        val errors=mutableListOf<SiteFailure>()
        val finished=withTimeoutOrNull(90000){discovery.discover(target,background=true,gate=gate).collect{found->
            for(variant in found.item.alternatives){
                if(!allowed(variant.ref.siteId)||hideAdult()&&AdultContent.blocked(found.item))continue
                val source=variant.ref.siteId+":"+variant.ref.pageUrl
                if(!loaded.add(source))continue
                try{
                    gate()
                    val detail=if(card.ref.kind==ContentKind.EPISODE){
                        val number=card.episodeNumber?:continue
                        val episode=repository.siteEpisodes(variant).firstOrNull{it.number==number}?:continue
                        if(SeriesIdentity.sourceKey(episode.card) in knownEpisodes)continue
                        if(!EpisodeMerge.explicit(episode)){
                            options.add(SourceOption("unverified-episode",episode.card.ref,"Bölüm numarası doğrulanamadı",episode.card.ref.pageUrl,state=OptionState.UNSUPPORTED,reason="Site sayfa numarası başka sitenin bölümüyle eşleştirilemedi").scoped())
                            emit(SourcePool(options.toList(),true,"Bölüm eşleşmesi doğrulanamadı",found.failures+errors));continue
                        }
                        repository.detail(episode.card)
                    }else repository.detail(variant)
                    if(hideAdult()&&AdultContent.blocked(detail.card))continue
                    val added=detail.options.filter{allowed(it.content.siteId)}.map{it.scoped()};options.addAll(added.filter{a->options.none{it.id==a.id}})
                }catch(e:CancellationException){throw e}catch(e:Exception){errors.add(SiteFailure(variant.ref.siteId,(e as? DataFailure)?.kind?:FailureKind.PARSE,e.message?:"Kaynak listesi alınamadı"))}
                emit(SourcePool(options.toList(),true,"Kaynaklar güncelleniyor",found.failures+errors))
            }
            emit(SourcePool(options.toList(),found.running,"Siteler: ${found.completed}/${found.total}",found.failures+errors))
        };true}?:false
        if(!finished)emit(SourcePool(options.toList(),false,"Kaynak keşfi süre sınırı; sonuç kısmi",errors+SiteFailure("all",FailureKind.TIMEOUT,"Kaynak keşfi süre sınırı")))
    }
}

/** Race metadata only; one winning source is handed to the sole decoder/player. */
object FirstReadySource {
    suspend fun await(pool:StateFlow<SourcePool>,onFailure:(SourceOption,Throwable)->Unit={_,_->},resolve:suspend(SourceOption)->ResolvedSource):Pair<SourceOption,ResolvedSource> = withTimeout(60000){
        supervisorScope{
            val changes=Channel<Unit>(Channel.CONFLATED);val results=Channel<Pair<SourceOption,Result<ResolvedSource>>>(2)
            val observer=launch{pool.collect{changes.trySend(Unit)}};val tried=mutableSetOf<String>();val jobs=mutableListOf<Job>();var running=0;var last:Throwable?=null
            try{
                while(true){
                    for(option in pool.value.options.filter{it.state==OptionState.AVAILABLE&&it.id !in tried}.take(2-running)){
                        tried.add(option.id)
                        running++
                        jobs.add(launch {
                            val result = try {
                                Result.success(resolve(option))
                            } catch (e: TimeoutCancellationException) {
                                currentCoroutineContext().ensureActive()
                                Result.failure(DataFailure(FailureKind.TIMEOUT,"Kaynak çözümleme zaman aşımına uğradı","resolve",cause=e))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Result.failure(e)
                            }
                            results.send(option to result)
                        })
                    }
                    if(running==0&&!pool.value.pending){
                        val failure=pool.value.errors.singleOrNull()
                        throw last?:failure?.let{DataFailure(it.kind,it.message,"source-pool",it.code)}
                            ?:DataFailure(FailureKind.UNSUPPORTED,"Oynatılabilir kaynak bulunamadı","source-pool")
                    }
                    val result=select<Pair<SourceOption,Result<ResolvedSource>>?>{results.onReceive{it};changes.onReceive{null}}
                    if(result!=null){
                        running--
                        if(result.second.isSuccess)return@supervisorScope result.first to result.second.getOrThrow()
                        last=result.second.exceptionOrNull()
                        if(last!=null)onFailure(result.first,last)
                    }
                }
                @Suppress("UNREACHABLE_CODE") error("unreachable")
            }finally{observer.cancel();jobs.forEach{it.cancel()};changes.close();results.close()}
        }
    }
}
