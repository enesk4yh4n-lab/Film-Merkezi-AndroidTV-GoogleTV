package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*

data class RecommendationResult(val items:List<UnifiedItem>,val checked:Int,val completedSites:Int,val totalSites:Int,val finished:Boolean,val errors:List<SiteFailure> = emptyList())

/** Metadata only. Never calls a resolver or downloads a media manifest. */
class RecommendationService(private val repository:CatalogRepository,private val works:WorkStore,private val options:()->CatalogRequestOptions={CatalogRequestOptions()}){
    fun generate(criteria:CatalogQuery,siteId:String,watched:List<LibraryRecord>,seen:List<String>,rotation:Int):Flow<RecommendationResult> = channelFlow {
        val policy=options()
        val sites=policy.sites.mapNotNull(repository.providers::get).filter{(siteId.isBlank()||it.site.id==siteId)&&it.site.supports(ContentKind.MOVIE)}
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
    // Preserve the published DEX constructor ABI. Zero represents unknown only internally;
    // JSON/UI use null/absent, and it cannot establish an automatic identity match.
    val knownYear get()=year.takeIf{it in 1888..2100}
    fun candidate(c:Card)=c.ref.kind==ContentKind.MOVIE&&(knownYear==null||c.year==year)&&(listOf(title)+aliases).any{TextKey.fold(ContentIdentity.cleanTitle(it,knownYear)) in ContentIdentity.facts(c).names}
    fun matches(c:Card)=knownYear!=null&&candidate(c)
}
object CollectionParser {
    private fun scope(d:org.jsoup.nodes.Document)=d.selectFirst(".news-content.article-content, article .entry-content, article .article-content, [itemprop=articleBody], .entry-content, article")?:throw DataFailure(FailureKind.PARSE,"Sıralı koleksiyon bulunamadı","collection")
    private fun primary(n:org.jsoup.nodes.Element)=n.parents().none{it.normalName() in setOf("aside","footer")||Regex("recommend|related|benzer|oneri").containsMatchIn(TextKey.fold(it.className()+" "+it.id()))}
    private fun read(root:org.jsoup.nodes.Element):List<CollectionEntry> {
        val number=Regex("""^(\d{1,3})[.)]\s*(.+)$""");val dated=Regex("""^(.+?)(?:\s*\(((?:18|19|20)\d{2})\)|\s+[–—-]\s*((?:18|19|20)\d{2}))\s*$""")
        val headings=root.select("h2,h3").filter(::primary)
        val nodes=if(headings.any{number.containsMatchIn(TextKey.clean(it.text()))||dated.containsMatchIn(TextKey.clean(it.text()))})headings else root.select("ol > li").filter(::primary)
        val items=nodes.mapIndexedNotNull{index,h->
            val raw=TextKey.clean(if(h.normalName()=="li")h.selectFirst("a[href]")?.text()?:h.ownText()else h.text())
            val n=number.find(raw);val value=n?.groupValues?.get(2)?:raw;val m=dated.find(value)
            if(n==null&&m==null&&h.normalName()!="li")return@mapIndexedNotNull null
            val title=TextKey.clean(m?.groupValues?.get(1)?:value);if(title.isBlank()||title.length>300)return@mapIndexedNotNull null
            val year=m?.let{it.groupValues[2].ifBlank{it.groupValues[3]}.toIntOrNull()}?:0
            val parts=title.split(Regex("""\s+[–—-]\s+"""))
            val parentheses=Regex("""\(([^()]+)\)""").findAll(title).map{TextKey.clean(it.groupValues[1])}.toList()
            val primary=TextKey.clean(title.replace(Regex("""\([^()]+\)""")," "))
            val aliases=((if(parts.size>1)parts else emptyList())+parentheses+listOfNotNull(primary.takeIf{it!=title&&it.isNotBlank()})).distinct()
            CollectionEntry(n?.groupValues?.get(1)?.toInt()?:index+1,title,year,aliases)
        }.distinctBy{Triple(it.ordinal,it.title,it.year)}.take(200)
        if(items.size<2)throw DataFailure(FailureKind.PARSE,"Doğrulanabilir sıralı film listesi bulunamadı","collection")
        return items
    }
    private fun structured(d:org.jsoup.nodes.Document,url:String):List<CollectionEntry> {
        val lists=HtmlData.schemas(d).flatMap{n->when{n.optString("@type")=="ItemList"->listOf(n);n.optJSONObject("mainEntity")?.optString("@type")=="ItemList"->listOf(n.getJSONObject("mainEntity"));else->emptyList()}}
        val rows=lists.filter{n->val address=n.optString("url").ifBlank{n.optString("@id")}
            if(address.isBlank())CatalogContent.collectionLink(HtmlData.text(d,"h1"),url)
            else publicUrl(address,url)?.substringBefore('#')?.trimEnd('/')==url.substringBefore('#').trimEnd('/')}
            .map{n->n.optJSONArray("itemListElement")?.values()?.mapIndexedNotNull{i,value->
                val row=value as? org.json.JSONObject?:return@mapIndexedNotNull null;val item=row.optJSONObject("item")?:row
                if(item.optString("@type")!="Movie")return@mapIndexedNotNull null
                val title=TextKey.clean(item.optString("name"));if(title.isBlank())return@mapIndexedNotNull null
                val year=listOf("releaseDate","startDate").mapNotNull{ContentMetadata.year(item.optString(it))}.distinct().singleOrNull()?:0
                CollectionEntry(row.optInt("position",i+1),title,year,listOf(item.optString("alternateName")).filter{it.isNotBlank()})
            }.orEmpty()}.filter{it.size>=2}
        return rows.singleOrNull().orEmpty().take(200)
    }
    fun entries(body:String,url:String):List<CollectionEntry>{val d=HtmlData.document(body,url);return structured(d,url).ifEmpty{read(scope(d))}}
    fun recognizes(body:String,card:Card):Boolean {
        val d=HtmlData.document(body,card.ref.pageUrl)
        val title=HtmlData.text(d,"h1").ifBlank{card.title}
        if(structured(d,card.ref.pageUrl).size>=2&&HtmlData.schemas(d).none{it.optString("@type") in listOf("Movie","TVSeries","TVEpisode")})return true
        if(!CatalogContent.collectionLink(title,card.ref.pageUrl))return false
        return try{read(scope(d)).size>=2}catch(_:DataFailure){false}
    }
    fun detail(body:String,card:Card):Detail {
        val d=HtmlData.document(body,card.ref.pageUrl);val data=entries(body,card.ref.pageUrl)
        val root=runCatching{scope(d)}.getOrElse{d.selectFirst("main")?:d}
        val declared=d.selectFirst("link[rel=canonical]")?.attr("href")?.let{Sites.get(card.ref.siteId).catalogUrl(it,card.ref.pageUrl)}
        if(declared!=null&&declared.substringBefore('?').trimEnd('/')!=card.ref.pageUrl.substringBefore('?').trimEnd('/'))throw DataFailure(FailureKind.PARSE,"Koleksiyon adresi yanıtla eşleşmiyor","collection")
        return Detail(card.copy(ref=card.ref.copy(kind=ContentKind.COLLECTION),title=HtmlData.text(root,"h1").ifBlank{HtmlData.text(d,"h1")}.ifBlank{card.title}),
            description=HtmlData.text(root,"p").take(2000),collectionItems=data)
    }
}
