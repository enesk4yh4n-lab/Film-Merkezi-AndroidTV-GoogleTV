package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*

data class RecommendationResult(val items:List<UnifiedItem>,val checked:Int,val completedSites:Int,val totalSites:Int,val finished:Boolean,val errors:List<SiteFailure> = emptyList())

/** Metadata only. Never calls a resolver or downloads a media manifest. */
class RecommendationService(private val repository:CatalogRepository,private val works:WorkStore,private val options:()->CatalogRequestOptions={CatalogRequestOptions()}){
    fun generate(criteria:CatalogQuery,siteId:String,watched:List<LibraryRecord>,seen:List<String>,rotation:Int):Flow<RecommendationResult> = channelFlow {
        val policy=options()
        val sites=policy.sites.mapNotNull(repository.providers::get).filter{(siteId.isBlank()||it.site.id==siteId)&&it.site.id !in setOf("dizibox","ddizi","diziboxum")}
        val ordered=if(sites.isEmpty())sites else sites.drop(rotation%sites.size)+sites.take(rotation%sites.size)
        val slots=Semaphore(2);val lock=Mutex();val accepted=mutableListOf<UnifiedItem>();val errors=mutableListOf<SiteFailure>();var checked=0;var completed=0
        send(RecommendationResult(emptyList(),0,0,sites.size,false))
        withTimeoutOrNull(30000){supervisorScope{ordered.forEach{p->launch{slots.withPermit{
            if(lock.withLock{accepted.size>=3})return@withPermit
            try{
                val page=p.filtered(criteria,null,{false},unverifiedCandidates=true)
                for(raw in page.cards){
                    val inspect=lock.withLock{if(checked>=12||accepted.size>=3)false else{checked++;true}}
                    if(!inspect)break
                    if(raw.ref.kind!=ContentKind.MOVIE)continue
                    val detail=p.detail(raw)
                    if(!criteria.accepts(detail.card))continue
                    val item=works.item(detail.card)
                    if(policy.hideAdult&&AdultContent.blocked(item))continue
                    if(item.identity in seen||watched.any{it.positionMs>=5000&&ContentIdentity.equivalent(it.card,item.card)})continue
                    lock.withLock{if(accepted.none{it.identity==item.identity}&&accepted.size<3)accepted.add(item)}
                }
            }catch(e:CancellationException){throw e}catch(e:Exception){lock.withLock{errors.add(SiteFailure(p.site.id,(e as? DataFailure)?.kind?:FailureKind.PARSE,e.message?:"Öneri alınamadı"))}}
            lock.withLock{completed++;send(RecommendationResult(accepted.toList(),checked,completed,sites.size,false,errors.toList()))}
        }}}}}
        send(RecommendationResult(accepted.toList(),checked,completed,sites.size,true,errors.toList()))
    }
}

data class CollectionEntry(val ordinal:Int,val title:String,val year:Int,val aliases:List<String> = emptyList()){
    fun matches(c:Card)=c.ref.kind==ContentKind.MOVIE&&c.year==year&&(listOf(title)+aliases).any{TextKey.fold(ContentIdentity.cleanTitle(it,year)) in ContentIdentity.facts(c).names}
}
object CollectionParser {
    private fun scope(d:org.jsoup.nodes.Document)=d.selectFirst(".news-content.article-content")?:throw DataFailure(FailureKind.PARSE,"Sıralı koleksiyon bulunamadı","collection")
    private fun read(root:org.jsoup.nodes.Element):List<CollectionEntry> {
        val pattern=Regex("""^(\d{1,3})[.)]\s*(.+?)(?:\s*\(((?:18|19|20)\d{2})\)|\s+[–—-]\s*((?:18|19|20)\d{2}))\s*$""")
        val items=root.select("h2,h3").mapNotNull{h->
            val m=pattern.find(TextKey.clean(h.text()))?:return@mapNotNull null
            val title=TextKey.clean(m.groupValues[2]);val year=(m.groupValues[3].ifBlank{m.groupValues[4]}).toInt()
            val parts=title.split(Regex("""\s+[–—-]\s+"""))
            val parentheses=Regex("""\(([^()]+)\)""").findAll(title).map{TextKey.clean(it.groupValues[1])}.toList()
            val primary=TextKey.clean(title.replace(Regex("""\([^()]+\)""")," "))
            val aliases=((if(parts.size>1)parts else emptyList())+parentheses+listOfNotNull(primary.takeIf{it!=title&&it.isNotBlank()})).distinct()
            CollectionEntry(m.groupValues[1].toInt(),title,year,aliases)
        }.distinctBy{Triple(it.ordinal,it.title,it.year)}.take(200)
        if(items.size<2)throw DataFailure(FailureKind.PARSE,"Doğrulanabilir sıralı film listesi bulunamadı","collection")
        return items
    }
    fun entries(body:String,url:String)=read(scope(HtmlData.document(body,url)))
    fun detail(body:String,card:Card):Detail {
        val d=HtmlData.document(body,card.ref.pageUrl);val root=scope(d)
        val declared=d.selectFirst("link[rel=canonical]")?.attr("href")?.let{Sites.get(card.ref.siteId).catalogUrl(it,card.ref.pageUrl)}
        if(declared!=null&&declared.substringBefore('?').trimEnd('/')!=card.ref.pageUrl.substringBefore('?').trimEnd('/'))throw DataFailure(FailureKind.PARSE,"Koleksiyon adresi yanıtla eşleşmiyor","collection")
        return Detail(card.copy(ref=card.ref.copy(kind=ContentKind.COLLECTION),title=HtmlData.text(root,"h1").ifBlank{HtmlData.text(d,"h1")}.ifBlank{card.title}),
            description=HtmlData.text(root,"p").take(2000),collectionItems=read(root))
    }
}
