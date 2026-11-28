package app.filmmerkezi.tv.core

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element

/** Shared host rules also available to signed adapters. No extra HTTP or player work. */
object CatalogContent {
    private val curated=Regex("kronolojik|izleme sirasi|koleksiyon|oner[iı].*film|marvel.*film|film.*marvel|seri film|film serisi|top ?\\d+|en iyi.*film",RegexOption.IGNORE_CASE)
    fun collectionLink(title:String,url:String):Boolean {
        val path=runCatching{java.net.URI(url).path.lowercase()}.getOrDefault("")
        return (path.contains("/haber/")||path.contains("/koleksiyon/")||path.contains("/koleksiyonlar/")||path.contains("/collection/"))&&curated.containsMatchIn(TextKey.fold(title))
    }
    fun seriesTitle(title:String):String = ContentIdentity.cleanTitle(title)
        .replace(Regex("\\s*(?:[·|–—-]\\s*)?\\d+\\.?\\s*(?:sezon|season)(?:\\s*\\d+\\.?\\s*(?:bölüm|bolum|episode))?.*$",RegexOption.IGNORE_CASE),"")
        .replace(Regex("\\s*[·|–—-]\\s*(?:\\d+\\s*[×x]\\s*\\d+|s\\d+e\\d+).*$",RegexOption.IGNORE_CASE),"")
        .replace(Regex("\\s*[·|–—-]\\s*\\d+\\.?\\s*(?:bölüm|bolum|episode).*$",RegexOption.IGNORE_CASE),"").trim()
    /** Only explicit parent references are projected. A guessed slug is never a parent identity. */
    fun catalog(page:CatalogPage):CatalogPage {
        val cards=page.cards.mapNotNull{c->
            if(c.ref.kind!=ContentKind.EPISODE)return@mapNotNull if(collectionLink(c.title,c.ref.pageUrl))c.copy(ref=c.ref.copy(kind=ContentKind.COLLECTION),year=null,imdb=null,imdbId="",identityEvidence=null)else c
            val parent=c.seriesRef?.takeIf{it.kind==ContentKind.SERIES&&it.siteId==c.ref.siteId}?:return@mapNotNull null
            val title=seriesTitle(c.title).takeIf{it.isNotBlank()&&!Regex("^(?:\\d+\\.?\\s*)?(?:bölüm|bolum|episode)(?:\\s*\\d+)?$",RegexOption.IGNORE_CASE).matches(it)}?:return@mapNotNull null
            c.copy(ref=parent,title=title,year=null,imdb=null,episodeNumber=null,seriesRef=null,seriesIdentity="",workId="",identityEvidence=null,imdbId="",originalTitle="",aliases=emptyList())
        }.groupBy{SeriesIdentity.sourceKey(it)}.values.map{versions->versions.maxBy{(if(it.identityEvidence?.detailChecked==true)8 else 0)+(if(it.year!=null)2 else 0)+(if(it.imdbId.isNotBlank())4 else 0)}}
        return page.copy(cards=cards)
    }
    fun forKind(page:CatalogPage,kind:ContentKind):CatalogPage {val normalized=catalog(page);return normalized.copy(cards=normalized.cards.filter{it.ref.kind==kind})}
}

/** Read identity fields from the page's own work, never recommended works or WebPage dates. */
object ContentMetadata {
    private val validImdb=Regex("^tt\\d{7,10}$")
    fun imdb(value:String):String {
        if(validImdb.matches(value.trim()))return value.trim()
        val u=runCatching{java.net.URI(value)}.getOrNull()?:return ""
        return if(u.host?.matches(Regex("(?i)(?:www\\.)?imdb\\.com"))==true)Regex("^/title/(tt\\d{7,10})(?:/|$)").find(u.path)?.groupValues?.get(1).orEmpty()else ""
    }
    fun year(value:String):Int?=Regex("^(?:18|19|20)\\d{2}(?:$|[-/])").find(value.trim())?.value?.take(4)?.toIntOrNull()?.takeIf{it in 1888..2100}
    private fun names(value:Any?):List<String> = when(value){is String->listOf(TextKey.clean(value));is JSONArray->value.values().flatMap(::names);is JSONObject->names(value.opt("name"));else->emptyList()}.filter{it.isNotBlank()}.distinct()
    private fun primary(n:Element)=n.parents().none{it.normalName() in setOf("aside","footer")||Regex("recommend|related|benzer|oneri").containsMatchIn(TextKey.fold(it.className()+" "+it.id()))}
    private fun normalized(value:String,base:String)=publicUrl(value,base)?.substringBefore('#')?.trimEnd('/')
    fun enrich(detail:Detail,body:String,page:String=detail.card.ref.pageUrl):Detail {
        if(detail.card.ref.kind==ContentKind.COLLECTION)return detail
        val c=detail.card;val doc=HtmlData.document(body,page)
        val expected=normalized(c.ref.pageUrl,page);val parent=c.seriesRef?.let{normalized(it.pageUrl,page)}
        val heading=doc.select("h1").firstOrNull(::primary)?.text().orEmpty()
        val nodes=HtmlData.schemas(doc).filter{n->
            val type=names(n.opt("@type"));val kind=when{ "TVEpisode" in type->ContentKind.EPISODE;"TVSeries" in type->ContentKind.SERIES;"Movie" in type->ContentKind.MOVIE;else->null}
            if(kind==null||c.ref.kind==ContentKind.SERIES&&kind!=ContentKind.SERIES||c.ref.kind==ContentKind.EPISODE&&kind!=ContentKind.EPISODE)false
            else {
                val address=n.optString("url").ifBlank{n.optString("@id")};val url=normalized(address,page)
                val name=TextKey.fold(ContentIdentity.cleanTitle(n.optString("name"),c.year))
                if(address.isNotBlank())url==expected&&url!=parent&&(name.isBlank()||name in (listOf(c.title,c.originalTitle,heading)+c.aliases).filter{it.isNotBlank()}.map{TextKey.fold(ContentIdentity.cleanTitle(it,c.year))})
                else name.isNotBlank()&&name in listOf(c.title,heading).map{TextKey.fold(ContentIdentity.cleanTitle(it,c.year))}
            }
        }
        val schema=nodes.singleOrNull()
        fun identifiers(value:Any?):List<String> = when(value){is String->listOf(value);is JSONArray->value.values().flatMap(::identifiers);is JSONObject->if(value.optString("propertyID").equals("imdb",true))listOf(value.optString("value"))else listOf(value.optString("url"),value.optString("@id"));else->emptyList()}
        val ids=nodes.flatMap{names(it.opt("sameAs"))+identifiers(it.opt("identifier"))+listOf(it.optString("url"))}.map(::imdb).filter{it.isNotBlank()}.toMutableList()
        imdb(c.imdbId).takeIf{it.isNotBlank()}?.let(ids::add)
        doc.select(".imdb_link[href], .post-info-imdb a[href], .p-v12-meta-info a[href*=imdb.com/title/], [itemprop=sameAs][href*=imdb.com/title/]").filter(::primary).mapTo(ids){imdb(it.attr("href"))}
        val distinct=ids.filter{it.isNotBlank()}.distinct();val conflict=distinct.size>1||c.identityEvidence?.imdbConflict==true
        val originals=names(schema?.opt("alternateName"));val genres=names(schema?.opt("genre"))
        val release=listOf("startDate","releaseDate").mapNotNull{schema?.optString(it)?.let(::year)}.distinct().singleOrNull()
        val evidence=c.identityEvidence?.takeIf{normalized(it.pageUrl,page)==expected}
        val types=names(schema?.opt("@type"));val kind=when{"TVSeries" in types->ContentKind.SERIES;"Movie" in types->ContentKind.MOVIE;"TVEpisode" in types->ContentKind.EPISODE;else->c.ref.kind}
        val original=c.originalTitle.ifBlank{originals.firstOrNull().orEmpty()}
        val resolvedGenres=detail.genres.ifEmpty{c.genres}.ifEmpty{genres}.filterNot{Regex("^(?:18|19|20)\\d{2} filmleri(?: izle)?$").matches(TextKey.fold(it))}
        val id=if(conflict)""else distinct.singleOrNull()?:c.imdbId
        val enriched=c.copy(ref=c.ref.copy(kind=kind),year=release?:c.year,imdbId=id,originalTitle=original,
            aliases=(c.aliases+originals).distinct().take(16),genres=resolvedGenres,
            identityEvidence=IdentityEvidence(c.ref.pageUrl,kind,release?:c.year,id,original,(c.aliases+originals).distinct().take(16),c.title,
                typeVerified=schema!=null||evidence?.typeVerified==true||c.ref.kind==ContentKind.SERIES&&detail.episodes.isNotEmpty(),
                yearVerified=release!=null||evidence?.yearVerified==true,detailChecked=true,imdbConflict=conflict,
                reason=if(release!=null)"primary-release-year"else evidence?.reason.orEmpty(),referencePage=evidence?.referencePage.orEmpty(),season=c.sourceSeason))
        return detail.copy(card=enriched,description=detail.description.ifBlank{schema?.optString("description").orEmpty()},genres=resolvedGenres,
            cast=detail.cast.ifEmpty{names(schema?.opt("actor"))},duration=detail.duration.ifBlank{schema?.optString("duration").orEmpty()})
    }
}
