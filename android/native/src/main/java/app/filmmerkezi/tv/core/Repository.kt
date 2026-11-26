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

/** Single native parse queue. Parsed data, rather than DOM documents, crosses it. */
class NativeSiteProvider(private val template:SiteSpec,private val network:WebTransport,private val parsing:CoroutineDispatcher) : SiteProvider {
    override val site get()=Sites.current(template)
    private val parser get()=CatalogParser(site)
    private var addressRevision=Sites.revision
    private fun addressChanged(){if(addressRevision!=Sites.revision){trim();filterOptions=null;addressRevision=Sites.revision}}
    private val detailMutex=Mutex()
    @Volatile private var lastDetail:Pair<Long,Detail>?=null
    private val detailCacheLock=Any()
    private var trimGeneration=0L
    private suspend fun fetch(request:WebRequest):WebResponse {
        val url=site.catalogUrl(request.url)?:throw DataFailure(FailureKind.PARSE,"Site dışı katalog adresi","request")
        val headers=if(site.id=="hdfilmcehennemi")mapOf("Accept" to "application/json, text/html","X-Requested-With" to "fetch")+request.headers else request.headers
        return network.request(request.copy(url=url,referer=site.catalogUrl(request.referer)?:request.referer,headers=headers))
    }
    private suspend fun listRequest(request:WebRequest):CatalogPage {
        val response=fetch(request)
        return withContext(parsing){AdultContent.listing(response.body,parser.list(response.body,request.url),site)}
    }
    override suspend fun search(query:String,next:PageCursor?):CatalogPage {
        addressChanged()
        val q=TextKey.clean(query)
        if(q.length !in 1..100)throw DataFailure(FailureKind.PARSE,"Arama 1–100 karakter olmalı","search")
        val url=next?.also{validateCursor(it)}?.url?:when(site.id) {
            "dizibox"->site.base+"/"
            "fullhdfilmizlesene"->site.base+site.search+java.net.URLEncoder.encode(q,"UTF-8").replace("+","%20")
            "ddizi"->site.base+"/arama/"
            else->site.base+site.search+java.net.URLEncoder.encode(q,"UTF-8")
        }
        val request=if(site.id=="ddizi")WebRequest(url,site.base+"/","POST",mapOf("arama" to q)) else WebRequest(url,site.base+"/")
        return if(site.id=="dizibox") {
            val response=fetch(WebRequest(site.base+"/"))
            withContext(parsing){
                val d=HtmlData.document(response.body,url)
                val index=d.select(".alphabetical-category-list a[href]").mapNotNull { a->
                    val u=site.catalogUrl(a.attr("href"),url)?:return@mapNotNull null
                    if(site.contentPattern.matches(u.toHttpUrl().encodedPath))Card(ContentRef(site.id,u,ContentKind.SERIES),TextKey.title(a.text()))else null
                }.filter{TextKey.fold(it.title).contains(TextKey.fold(q))}.distinctBy{it.ref.id}
                CatalogPage(index)
            }
        }else if(site.id=="fixfullfilmizle"){
            val response=fetch(request)
            withContext(parsing){FixFullSearch.project(site,response.body,request.url,q,AdultContent.listing(response.body,parser.list(response.body,request.url),site))}
        }else listRequest(request)
    }
    override suspend fun browse(kind:ContentKind,next:PageCursor?):CatalogPage {
        addressChanged()
        if(kind==ContentKind.EPISODE)throw DataFailure(FailureKind.PARSE,"Bölümleri dizi detayından alın","browse")
        if(kind==ContentKind.SERIES && site.seriesPath==null)return CatalogPage(emptyList())
        if(kind==ContentKind.MOVIE && site.id in setOf("dizibox","ddizi","diziboxum"))return CatalogPage(emptyList())
        val url=next?.also{validateCursor(it)}?.url?:site.base+(if(kind==ContentKind.SERIES)site.seriesPath?:site.list else site.list)
        if(kind==ContentKind.SERIES && site.id in setOf("ddizi","fixfullfilmizle","lovefilmizle")){
            val response=fetch(WebRequest(url,site.base+"/"))
            return withContext(parsing){parser.seriesIndex(response.body,url,next?.fields?.get("indexOffset")?.toIntOrNull()?:0)}
        }
        val fields=next?.fields?.filterKeys{it!="max"}?:emptyMap()
        val headers=if(site.id=="hdfilmcehennemi")mapOf("Accept" to "application/json, text/html","X-Requested-With" to "fetch")+(if(fields.isNotEmpty())mapOf("X-Mofycore-Action" to "discover")else emptyMap())else emptyMap()
        var response=listRequest(WebRequest(url,site.base+"/",if(fields.isNotEmpty())"POST"else"GET",fields,headers=headers))
        if(site.id=="ddizi" && next?.fields?.containsKey("max")==true && response.next==null && response.cards.isNotEmpty()){
            val max=next.fields["max"]?.toIntOrNull()?:1
            if(next.page<max)response=response.copy(next=PageCursor(site.id,site.base+"/l.php?sayfa="+(next.page+1),next.page+1,next.fields))
        }
        return response.copy(cards=response.cards.filter{it.ref.kind==kind || (kind==ContentKind.MOVIE&&it.ref.kind==ContentKind.COLLECTION) || (kind==ContentKind.SERIES && it.ref.kind==ContentKind.EPISODE)})
    }
    private var filterOptions:Pair<Long,FilterOptions>?=null
    suspend fun options():FilterOptions {
        addressChanged()
        if(!SiteFilters.hasOptions(site))return FilterOptions()
        filterOptions?.takeIf{System.currentTimeMillis()-it.first<3600000}?.let{return it.second}
        val response=fetch(WebRequest(site.base+SiteFilters.optionsPath(site),site.base+"/"))
        return withContext(parsing){SiteFilters.options(site,response.body)}.also{filterOptions=System.currentTimeMillis() to it}
    }
    suspend fun filtered(q:CatalogQuery,next:PageCursor?,check:()->Boolean,unverifiedCandidates:Boolean=false):CatalogPage {
        if(q.kind==ContentKind.MOVIE&&site.id in setOf("dizibox","ddizi","diziboxum")||q.kind==ContentKind.SERIES&&site.seriesPath==null)return CatalogPage(emptyList())
        if(next!=null){validateCursor(next);if(next.fields["_query"]!=q.signature)throw DataFailure(FailureKind.PARSE,"Devam sayfası başka filtreye ait","pagination")}
        val options=if(q.filtered)options()else FilterOptions()
        val plan=SiteFilters.plan(site,q,options,next)
        val filterResponse=plan.request?.let{fetch(it)}
        val page=if(plan.request==null)browse(q.kind,next?.copy(fields=next.fields.filterKeys{!it.startsWith("_")}))else withContext(parsing){parser.list(checkNotNull(filterResponse).body,plan.request.url)}
        var inspected=0;var omitted=0
        val cards=mutableListOf<Card>()
        for(raw in page.cards){
            if(raw.ref.kind!=q.kind&&!(raw.ref.kind==ContentKind.COLLECTION&&q.kind==ContentKind.MOVIE&&!q.filtered))continue
            var c=raw
            if(!q.accepts(c,plan.coverage.remote)&&plan.coverage.local.isNotEmpty()&&check()){
                inspected++;try{c=detail(c).card}catch(e:CancellationException){throw e}catch(_:Exception){}
            }
            if(unverifiedCandidates||q.accepts(c,plan.coverage.remote))cards.add(c)else omitted++
        }
        val ordered=when(q.order){CatalogOrder.DEFAULT->cards;CatalogOrder.YEAR->cards.sortedByDescending{it.year?:0};CatalogOrder.RATING->cards.sortedByDescending{it.imdb?:0.0};CatalogOrder.TITLE->cards.sortedBy{TextKey.fold(it.title)}}
        val archiveNext=if(site.id=="sinemacc"&&plan.request?.method=="POST"){
            val responseDoc=HtmlData.document(checkNotNull(filterResponse).body,plan.request.url)
            responseDoc.selectFirst(".pagination a.next[rel]")?.attr("rel")?.toIntOrNull()?.takeIf{it==(next?.page?:1)+1}?.let{PageCursor(site.id,plan.request.url,it)}
        }else null
        val nextPage=archiveNext?:page.next
        val cursor=nextPage?.copy(fields=nextPage.fields+("_query" to q.signature))
        return page.copy(cards=ordered,next=cursor,coverage=plan.coverage.copy(checked=inspected,omitted=omitted))
    }
    private fun validateCursor(cursor:PageCursor) {
        if(cursor.siteId!=site.id || site.catalogUrl(cursor.url)==null || cursor.page !in 1..100000)throw DataFailure(FailureKind.PARSE,"Geçersiz devam bağlantısı","pagination")
    }
    override suspend fun detail(card:Card):Detail = detailMutex.withLock {
        addressChanged()
        lastDetail?.takeIf { it.second.card.ref.id==card.ref.id && System.currentTimeMillis()-it.first<60000 }?.let{return@withLock it.second}
        val generation=synchronized(detailCacheLock){trimGeneration}
        val response=fetch(WebRequest(card.ref.pageUrl,site.base+"/"))
        val resolved=if(response.url!=card.ref.pageUrl&&site.id=="ddizi"&&card.ref.kind==ContentKind.SERIES){
            if(!EpisodePages.sameArchive(site,card.ref.pageUrl,response.url))throw DataFailure(FailureKind.PARSE,"Yanıt başka bir diziye yönlendi","detail")
            card.copy(ref=card.ref.copy(pageUrl=response.url))
        }else SiteAddresses.card(card)
        var value=withContext(parsing){AdultContent.detail(response.body,PageIdentity.attach(response.body,parser.detail(response.body,resolved),card))}
        if(site.id=="diziboxum"){
            val options=mutableListOf<SourceOption>()
            for(option in value.options){
                if(!KatrePlayer.accepts(option.url)){options.add(option);continue}
                try{
                    val embed=network.request(WebRequest(option.url,value.card.ref.pageUrl))
                    val config=withContext(parsing){KatrePlayer.parse(embed.body,embed.url)}
                    options.addAll(KatrePlayer.options(option,config))
                }catch(e:CancellationException){throw e}catch(e:DataFailure){options.add(option.copy(reason=e.message))}
            }
            value=value.copy(options=options)
        }
        synchronized(detailCacheLock){if(generation==trimGeneration)lastDetail=System.currentTimeMillis() to value}
        value
    }
    override suspend fun episodes(card:Card):List<Episode> {
        val first=detail(card)
        if(first.card.ref.kind!=ContentKind.SERIES)return emptyList()
        val out=first.episodes.toMutableList()
        val queue=ArrayDeque(first.episodePages);first.episodesNext?.let{queue.add(it.url)}
        val seen=mutableSetOf(EpisodePages.key(site,card.ref.pageUrl),EpisodePages.key(site,first.card.ref.pageUrl));var requests=0
        try{withTimeout(60000) {
            while(queue.isNotEmpty()) {
                val url=queue.removeFirst()
                if(!seen.add(EpisodePages.key(site,url)))continue
                if(++requests>50)throw DataFailure(FailureKind.LIMIT,"Bölüm listesi 50 sayfa sınırında","episodes")
                val response=fetch(WebRequest(url,card.ref.pageUrl))
                if(site.id=="ddizi"&&!EpisodePages.sameArchive(site,first.card.ref.pageUrl,response.url))throw DataFailure(FailureKind.PARSE,"Bölüm sayfası başka diziye yönlendi","episodes")
                val pages=withContext(parsing) {
                    val doc=HtmlData.document(response.body,response.url)
                    out.addAll(parser.episodes(doc,first.card))
                    EpisodePages.links(site,doc,first.card,response.url)
                }
                pages.forEach{if(EpisodePages.key(site,it) !in seen&&it !in queue)queue.add(it)}
                if(out.size>4000)throw DataFailure(FailureKind.LIMIT,"Bölüm listesi boyut sınırında","episodes")
            }
        }}catch(e:TimeoutCancellationException){throw DataFailure(FailureKind.TIMEOUT,"Bölüm listesinin tamamı zamanında alınamadı","episodes",cause=e)}
        return out.distinctBy{"${it.number.season}:${it.number.number}"}.sortedWith(compareBy({it.number.season},{it.number.number}))
    }
    fun trim(){synchronized(detailCacheLock){trimGeneration++;lastDetail=null}}
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
        val site=Sites.get("filmcibaba");val response=network.request(WebRequest(site.base+"/",site.base+"/"))
        val home=withContext(parsing){CatalogParser(site).list(response.body,site.base+"/").cards.filter{it.ref.kind==ContentKind.COLLECTION}}
        if(home.isNotEmpty())return home
        // Collections are no longer advertised on every home page. Search the site's own index.
        val found=mutableListOf<Card>();var failure:Exception?=null;var succeeded=false
        for(query in listOf("izleme sırası","top"))try{
            found.addAll(provider(site.id).search(query).cards.filter{it.ref.kind==ContentKind.COLLECTION});succeeded=true
        }catch(e:CancellationException){throw e}catch(e:Exception){failure=e}
        if(!succeeded&&failure!=null)throw failure
        return found.distinctBy{it.ref.id}
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
