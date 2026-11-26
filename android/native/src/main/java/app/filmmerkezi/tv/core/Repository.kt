package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.LinkedHashMap

interface SiteProvider {
    val site: SiteSpec
    suspend fun search(query:String,next:PageCursor?=null):CatalogPage
    suspend fun browse(kind:ContentKind=ContentKind.MOVIE,next:PageCursor?=null):CatalogPage
    suspend fun detail(card:Card):Detail
    suspend fun episodes(card:Card):List<Episode>
}

/** Failure in one provider cannot cancel successful providers. Unified mode is always on. */
class CatalogRepository(private val network:WebTransport,private val works:WorkStore?=null,private val requestOptions:()->CatalogRequestOptions={CatalogRequestOptions()},private val metadataSites:()->Set<String> = {requestOptions().sites.toSet()}) {
    private val parsing=Dispatchers.Default.limitedParallelism(1)
    val providers:Map<String,NativeSiteProvider> = Sites.all.associate { it.id to NativeSiteProvider(it,network,parsing) }
    private val cache=object:LinkedHashMap<String,Pair<Long,UnifiedPage>>(8,0.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,Pair<Long,UnifiedPage>>?)=size>6
    }
    private val siteSlots=Semaphore(2)
    private var trimGeneration=0L
    suspend fun search(query:String,cursors:Map<String,PageCursor> = emptyMap()):UnifiedPage = observeSearch(query,cursors).last()
    suspend fun browse(kind:ContentKind=ContentKind.MOVIE,cursors:Map<String,PageCursor> = emptyMap()):UnifiedPage = observeBrowse(kind,cursors).last()
    fun observeSearch(query:String,cursors:Map<String,PageCursor> = emptyMap(),siteId:String?=null):Flow<UnifiedPage> =
        unified("search:$siteId:$query:$cursors",cursors,siteId,query){p,c->p.search(query,c)}
    fun observeBrowse(kind:ContentKind=ContentKind.MOVIE,cursors:Map<String,PageCursor> = emptyMap(),siteId:String?=null):Flow<UnifiedPage> =
        unified("browse:$siteId:$kind:$cursors",cursors,siteId){p,c->p.browse(kind,c)}
    fun observeFiltered(q:CatalogQuery,cursors:Map<String,PageCursor> = emptyMap(),siteId:String?=null):Flow<UnifiedPage>{
        val budget=java.util.concurrent.atomic.AtomicInteger(12)
        return unified("filtered:$siteId:${q.signature}:$cursors",cursors,siteId){p,c->p.filtered(q,c,check={budget.getAndDecrement()>0})}
    }
    private fun unified(key:String,cursors:Map<String,PageCursor>,siteId:String?,query:String?=null,read:suspend(NativeSiteProvider,PageCursor?)->CatalogPage):Flow<UnifiedPage> = channelFlow {
        val options=requestOptions()
        val selected=options.sites.mapNotNull(providers::get).filter{siteId==null||it.site.id==siteId}
        val cacheKey=key+":"+options.signature+":"+Sites.revision
        if(cursors.keys.any{id->selected.none{it.site.id==id}})throw DataFailure(FailureKind.PARSE,"Devam bağlantısı seçili siteye ait değil","pagination")
        val requested=selected.filter{cursors.isEmpty()||cursors.containsKey(it.site.id)}
        val (generation,hit)=synchronized(cache){trimGeneration to cache[cacheKey]?.takeIf{System.currentTimeMillis()-it.first<60000}?.second}
        if(hit!=null){send(hit.copy(items=CatalogPresentation.project(if(works==null)hit.items else works.refresh(hit.items.flatMap{it.alternatives}),options,hit.items.flatMap{it.alternatives}.map{it.ref.id}.toSet())));return@channelFlow}
        val rows=linkedMapOf<String,CatalogPage>();val failures=linkedMapOf<String,SiteFailure>();val lock=Mutex()
        var latest=UnifiedPage(emptyList(),emptyMap(),emptyList(),requestedSites=requested.size)
        send(latest)
        suspend fun snapshot()=UnifiedPage(withContext(parsing){CatalogMerge.pages(rows)}.let{items->works?.observe(items.flatMap{it.alternatives})?:items}.let{CatalogPresentation.project(it,options,rows.values.flatMap{it.cards}.map{it.ref.id}.toSet())},
            rows.mapNotNull{(id,r)->r.next?.let{id to it}}.toMap(),failures.values.toList(),
            rows.mapValues{it.value.cards.size},rows.size+failures.size,requested.size,rows.values.mapNotNull{it.coverage}).also{latest=it}
        supervisorScope {
            requested.forEach { p -> launch {
                // Waiting for a slot is outside the per-site deadline.
                siteSlots.withPermit {
                    var row:CatalogPage?=null;var failure:SiteFailure?=null
                    try { row=withTimeout(20000){read(p,cursors[p.site.id])} }
                    catch(e:CancellationException){if(e is TimeoutCancellationException)failure=SiteFailure(p.site.id,FailureKind.TIMEOUT,"Site zaman aşımı")else throw e}
                    catch(e:Exception){failure=SiteFailure(p.site.id,(e as? DataFailure)?.kind?:FailureKind.PARSE,e.message?:"Site hatası",(e as? DataFailure)?.httpCode)}
                    lock.withLock {
                        row?.let{rows[p.site.id]=it};failure?.let{failures[p.site.id]=it}
                        val result=snapshot()
                        send(result)
                    }
                }
            } }
        }
        if(works!=null&&options.merge){
            val attempted=mutableSetOf<String>();val eligible=rows.values.flatMap{it.cards}.map{it.ref.id}.toSet()
            withTimeoutOrNull(20000){supervisorScope{repeat(2){launch{
                while(isActive){
                    val c=lock.withLock{
                        if(attempted.size>=12)null else withContext(parsing){ContentIdentity.verificationCandidates(latest.items.flatMap{it.alternatives}.filter{it.ref.id in eligible},query,eligible.size)}
                            .firstOrNull{it.ref.id !in attempted}?.also{attempted.add(it.ref.id)}
                    }?:break
                    siteSlots.withPermit{
                        try{val checked=provider(c.ref.siteId).detail(c).card;lock.withLock{
                            val row=rows[c.ref.siteId]?:return@withLock
                            rows[c.ref.siteId]=row.copy(cards=row.cards.map{if(it.ref.id==c.ref.id)checked else it});send(snapshot())
                        }}catch(e:CancellationException){throw e}catch(_:Exception){}
                    }
                }
            }}}}
        }
        val result=snapshot()
        synchronized(cache){if(generation==trimGeneration)cache[cacheKey]=System.currentTimeMillis() to result}
        send(result)
    }
    suspend fun collections():List<Card> {
        if("filmcibaba" !in providers)return emptyList()
        val site=Sites.get("filmcibaba")
        return provider(site.id).collections()

    }
    fun provider(id:String)=providers[id]?:throw DataFailure(FailureKind.PARSE,"Bilinmeyen site")
    suspend fun detail(card:Card):Detail {
        val d=provider(card.ref.siteId).detail(card);val group=works?.item(d.card)
        val normalized=group?.alternatives?.firstOrNull{it.ref.pageUrl==d.card.ref.pageUrl}?:d.card
        return d.copy(card=normalized.copy(workId=group?.identity?:card.workId,ref=normalized.ref.copy(kind=ContentIdentity.facts(normalized).kind)),episodes=d.episodes.map{e->e.copy(card=e.card.copy(seriesIdentity=group?.identity?:ContentIdentity.key(d.card),seriesRef=d.card.ref))})
    }
    suspend fun siteEpisodes(card:Card):List<Episode> {
        val parent=works?.item(card)?.identity?:ContentIdentity.key(card)
        return provider(card.ref.siteId).episodes(card).map{e->e.copy(card=e.card.copy(seriesIdentity=parent,seriesRef=card.ref))}
    }
    private val seasonSearches=object:LinkedHashMap<String,Long>(8,0.75f,true){override fun removeEldestEntry(e:MutableMap.MutableEntry<String,Long>?)=size>16}
    suspend fun episodes(card:Card):List<Episode> = episodeCatalog(card).episodes
    suspend fun episodeCatalog(card:Card):SeriesEpisodes {
        var group=works?.item(card)?:UnifiedItem(ContentIdentity.key(card),card,listOf(card))
        val discoveryFailures=mutableListOf<SiteFailure>()
        // Find sibling season pages on the originating site; no cross-site search is
        // required to display the already-known catalog. Strong identity rules gate additions.
        val enabled=metadataSites()
        if(works!=null)for(seed in group.alternatives.filter{it.sourceSeason!=null&&it.ref.siteId in enabled}.distinctBy{it.ref.siteId}){
            val key=group.identity+":"+seed.ref.siteId
            val fresh=synchronized(seasonSearches){seasonSearches[key]?.let{System.currentTimeMillis()-it<60000}==true}
            if(fresh)continue
            try{withTimeout(20000){
                val p=provider(seed.ref.siteId)
                val candidates=p.search(ContentIdentity.cleanTitle(seed.title,seed.year)).cards.filter{ContentIdentity.likelyRelated(seed,it)}.take(8)
                for(raw in candidates){
                    val checked=p.detail(raw).card
                    if(ContentIdentity.decide(seed,checked).state==IdentityState.SAME)works.observe(listOf(checked))
                }
                synchronized(seasonSearches){seasonSearches[key]=System.currentTimeMillis()}
            }}catch(e:CancellationException){if(e !is TimeoutCancellationException)throw e else discoveryFailures.add(SiteFailure(seed.ref.siteId,FailureKind.TIMEOUT,"Diğer sezon sayfaları zamanında alınamadı"))}
            catch(e:Exception){discoveryFailures.add(SiteFailure(seed.ref.siteId,(e as? DataFailure)?.kind?:FailureKind.PARSE,e.message?:"Diğer sezon sayfaları alınamadı"))}
        }
        if(works!=null)group=works.item(card)
        val variants=group.alternatives.filter{it.ref.siteId in enabled&&ContentIdentity.facts(it).kind==ContentKind.SERIES}.distinctBy{SeriesIdentity.sourceKey(it)}.ifEmpty{listOf(card).filter{it.ref.siteId in enabled}}
        val results=supervisorScope{val slots=Semaphore(2)
            variants.map{variant->async{slots.withPermit{
                try{val p=provider(variant.ref.siteId);val d=p.detail(variant);Triple(p.episodes(variant),null,d.seasons+listOfNotNull(variant.sourceSeason))}
                catch(e:CancellationException){throw e}catch(e:Exception){Triple(emptyList<Episode>(),SiteFailure(variant.ref.siteId,(e as? DataFailure)?.kind?:FailureKind.PARSE,e.message?:"Bölüm listesi alınamadı"),listOfNotNull(variant.sourceSeason))}
            }}}.awaitAll()
        }
        if(results.all{it.first.isEmpty()}&&results.any{it.second!=null})throw DataFailure(FailureKind.PARSE,results.firstNotNullOf{it.second}.message,"episodes")
        val merged=EpisodeMerge.combine(results.map{it.first},group.identity,card.ref.siteId,discoveryFailures+results.mapNotNull{it.second})
        return merged.copy(seasons=(merged.seasons+results.flatMap{it.third}).filter{it in 1..1000}.distinct().sorted())
    }
    fun trim(){synchronized(cache){trimGeneration++;cache.clear()};synchronized(seasonSearches){seasonSearches.clear()};providers.values.forEach{it.trim()}}
}
