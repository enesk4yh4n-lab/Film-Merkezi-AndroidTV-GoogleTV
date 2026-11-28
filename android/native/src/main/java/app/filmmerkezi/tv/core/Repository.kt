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
class CatalogRepository(private val network:WebTransport,private val works:WorkStore?=null,private val requestOptions:()->CatalogRequestOptions={CatalogRequestOptions()},private val metadataSites:()->Set<String> = {requestOptions().sites.toSet()},private val snapshots:CatalogSnapshots?=null) {
    private val parsing=Dispatchers.Default.limitedParallelism(1)
    private var providerRevision=-1L
    private var providerSnapshot:Map<String,NativeSiteProvider> = emptyMap()
    val providers:Map<String,NativeSiteProvider> get()=synchronized(this){
        if(providerRevision!=Sites.revision){providerSnapshot=Sites.all.associate{it.id to NativeSiteProvider(it,network,parsing)};providerRevision=Sites.revision}
        providerSnapshot
    }
    private val cache=object:LinkedHashMap<String,Pair<Long,UnifiedPage>>(8,0.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,Pair<Long,UnifiedPage>>?)=size>6
    }
    private val siteSlots=CatalogQueue()
    internal fun prioritizePlayback(active:Boolean)=siteSlots.prioritizePlayback(active)
    private val availability=CatalogAvailability()
    fun pausedFor(id:String)=availability.remaining(id)
    fun retrySite(id:String){availability.reset(id);invalidatePages()}
    fun invalidatePages(){synchronized(cache){cache.clear();trimGeneration++}}

    suspend fun suggest(siteId:String,query:String):List<Card> = siteSlots.withSlot {
        if(siteId !in requestOptions().sites||pausedFor(siteId)>0)return@withSlot emptyList()
        withTimeoutOrNull(8000){provider(siteId).suggest(query).filter{!requestOptions().hideAdult||!AdultContent.blocked(it)}.take(8)}.orEmpty()
    }
    private var trimGeneration=0L
    suspend fun search(query:String,cursors:Map<String,PageCursor> = emptyMap()):UnifiedPage = observeSearch(query,cursors).last()
    suspend fun browse(kind:ContentKind=ContentKind.MOVIE,cursors:Map<String,PageCursor> = emptyMap()):UnifiedPage = observeBrowse(kind,cursors).last()
    fun observeSearch(query:String,cursors:Map<String,PageCursor> = emptyMap(),siteId:String?=null):Flow<UnifiedPage> =
        unified("search:$siteId:$query:$cursors",cursors,siteId,query){p,c->p.search(query,c)}
    fun observeBrowse(kind:ContentKind=ContentKind.MOVIE,cursors:Map<String,PageCursor> = emptyMap(),siteId:String?=null):Flow<UnifiedPage> =
        unified("browse:$siteId:$kind:$cursors",cursors,siteId,persist=true,eligible={Sites.get(it).supports(kind)}){p,c->p.browse(kind,c)}
    fun observeFiltered(q:CatalogQuery,cursors:Map<String,PageCursor> = emptyMap(),siteId:String?=null):Flow<UnifiedPage>{
        val budget=java.util.concurrent.atomic.AtomicInteger(12)
        return unified("filtered:$siteId:${q.signature}:$cursors",cursors,siteId,persist=true,eligible={Sites.get(it).supports(q.kind)}){p,c->p.filtered(q,c,check={budget.getAndDecrement()>0})}
    }
    fun observeSection(section:CatalogSection,cursors:Map<String,PageCursor> = emptyMap(),siteId:String?=null):Flow<UnifiedPage> =
        unified("section:$section:$siteId:$cursors",cursors,siteId,eligible={section.routes(it).isNotEmpty()},verifyDetails=false,persist=true){p,c->p.section(section,c)}
    private fun unified(key:String,cursors:Map<String,PageCursor>,siteId:String?,query:String?=null,
        eligible:(String)->Boolean={true},verifyDetails:Boolean=true,persist:Boolean=false,read:suspend(NativeSiteProvider,PageCursor?)->CatalogPage):Flow<UnifiedPage> = channelFlow {
        val options=requestOptions()
        val selected=options.sites.filter{(siteId==null||it==siteId)&&eligible(it)}.mapNotNull(providers::get)
        val cacheKey=key+":"+options.signature+":"+Sites.revision
        if(cursors.keys.any{id->selected.none{it.site.id==id}})throw DataFailure(FailureKind.PARSE,"Devam bağlantısı seçili siteye ait değil","pagination")
        val requested=selected.filter{cursors.isEmpty()||cursors.containsKey(it.site.id)}
        val (generation,hit)=synchronized(cache){trimGeneration to cache[cacheKey]?.takeIf{System.currentTimeMillis()-it.first<60000}?.second}
        if(hit!=null){
            val cards=hit.items.flatMap{it.alternatives}
            val items=if(works==null)hit.items else catalogPosters(works.refresh(cards),cards)
            send(hit.copy(items=CatalogPresentation.project(items,options,cards.map{it.ref.id}.toSet())));return@channelFlow
        }
        val eligibleIds=requested.map{it.site.id}.toSet()
        val diskKey=if(snapshots!=null&&persist&&cursors.isEmpty()&&requested.isNotEmpty()){
            val stamp=requested.joinToString("|"){it.site.id+"="+it.site.base+"@"+Sites.catalogRevision(it.site.id)}
            app.filmmerkezi.tv.updates.UpdateProtocol.sha("$key:${options.signature}:$stamp".toByteArray(Charsets.UTF_8))
        }else null
        val diskGeneration=snapshots?.generation?:0L
        val saved=diskKey?.let{CatalogPresentation.project(snapshots!!.read(it,eligibleIds),options)}
            .orEmpty().flatMap{it.alternatives}.distinctBy{it.ref.id}
        val rows=linkedMapOf<String,CatalogPage>();val failures=linkedMapOf<String,SiteFailure>();val lock=Mutex()
        var latest=UnifiedPage(emptyList(),emptyMap(),emptyList(),requestedSites=requested.size)
        suspend fun publish(page:UnifiedPage,finished:Boolean=false){
            val incomplete=page.coverage.filter{"section" in it.local}.map{it.siteId}.toSet()
            val freshSites=page.siteCounts.keys-incomplete
            val stale=saved.filter{it.ref.siteId !in freshSites && (!finished||it.ref.siteId in incomplete||
                page.failures.any{f->f.siteId==it.ref.siteId})}
            val items=if(stale.isEmpty())page.items else withContext(parsing){
                val cards=(page.items.flatMap{it.alternatives}+stale).distinctBy{it.ref.id}
                CatalogPresentation.project(ContentIdentity.merge(cards),options,cards.map{it.ref.id}.toSet())
            }
            send(if(stale.isEmpty())page else page.copy(items=items,cached=true))
        }
        publish(latest)
        suspend fun snapshot()=UnifiedPage(withContext(parsing){CatalogMerge.pages(rows)}.let{items->
            val cards=items.flatMap{it.alternatives}
            works?.let{catalogPosters(it.observe(cards),cards)}?:items
        }.let{CatalogPresentation.project(it,options,rows.values.flatMap{it.cards}.map{it.ref.id}.toSet())},
            rows.mapNotNull{(id,r)->r.next?.let{id to it}}.toMap(),failures.values.toList(),
            rows.mapValues{it.value.cards.size},rows.size+failures.size,requested.size,rows.values.mapNotNull{it.coverage}).also{latest=it}
        supervisorScope {
            requested.forEach { p -> launch {
                // Waiting for a slot is outside the per-site deadline.
                val (row,failure)=siteSlots.withSlot {
                    var result:CatalogPage?=null;var error:SiteFailure?=null
                    val ticket=availability.request(p.site.id)
                    if(ticket==null)error=SiteFailure(p.site.id,FailureKind.ACCESS,"Site geçici olarak bekletiliyor")
                    else{
                        try { result=withTimeout(20000){read(p,cursors[p.site.id])} }
                        catch(e:CancellationException){if(e is TimeoutCancellationException)error=SiteFailure(p.site.id,FailureKind.TIMEOUT,"Site zaman aşımı")else throw e}
                        catch(e:Exception){error=SiteFailure(p.site.id,(e as? DataFailure)?.kind?:FailureKind.PARSE,e.message?:"Site hatası",(e as? DataFailure)?.httpCode)}
                        availability.result(ticket,error)
                    }
                    result to error
                }
                lock.withLock {
                    row?.let{rows[p.site.id]=it};failure?.let{failures[p.site.id]=it}
                    publish(snapshot())
                }
            } }
        }
        if(verifyDetails&&works!=null&&options.merge){
            val attempted=mutableSetOf<String>();val eligible=rows.values.flatMap{it.cards}.map{it.ref.id}.toSet()
            withTimeoutOrNull(20000){supervisorScope{repeat(2){launch{
                while(isActive){
                    val c=lock.withLock{
                        if(attempted.size>=12)null else withContext(parsing){ContentIdentity.verificationCandidates(latest.items.flatMap{it.alternatives}.filter{it.ref.id in eligible},query,eligible.size)}
                            .firstOrNull{it.ref.id !in attempted}?.also{attempted.add(it.ref.id)}
                    }?:break
                    try{val checked=siteSlots.withSlot{provider(c.ref.siteId).detail(c).card};lock.withLock{
                            val row=rows[c.ref.siteId]?:return@withLock
                            rows[c.ref.siteId]=row.copy(cards=row.cards.map{if(it.ref.id==c.ref.id)checked.copy(poster=it.poster.ifBlank{checked.poster}) else it});publish(snapshot())
                    }}catch(e:CancellationException){throw e}catch(_:Exception){}
                }
            }}}}
        }
        val result=snapshot()
        if(result.failures.isEmpty()&&result.coverage.none{"section" in it.local}){
            val stillCurrent=synchronized(cache){if(generation==trimGeneration){cache[cacheKey]=System.currentTimeMillis() to result;true}else false}
            publish(result,finished=true)
            if(stillCurrent&&diskKey!=null)snapshots?.write(diskKey,result.items,diskGeneration)
        }else publish(result,finished=true)
    }
    private fun catalogPosters(items:List<UnifiedItem>,listing:List<Card>):List<UnifiedItem>{
        val posters=listing.filter{it.poster.isNotBlank()}.associate{it.ref.id to it.poster}
        fun apply(card:Card)=posters[card.ref.id]?.takeIf{it!=card.poster}?.let{card.copy(poster=it)}?:card
        return items.map{it.copy(card=apply(it.card),alternatives=it.alternatives.map(::apply))}
    }
    suspend fun collections():List<Card> {
        val result=mutableListOf<Card>()
        for(id in requestOptions().sites){val p=providers[id]?:continue
            try{result.addAll(p.collections())}catch(e:CancellationException){throw e}catch(_:Exception){}
        }
        return result.distinctBy{it.ref.id}
    }
    fun provider(id:String)=providers[id]?:throw DataFailure(FailureKind.UNSUPPORTED,"Site eklentisi kurulu değil: $id","plugin")
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
    fun trim(){invalidatePages();synchronized(seasonSearches){seasonSearches.clear()};providers.values.forEach{it.trim()}}
}
