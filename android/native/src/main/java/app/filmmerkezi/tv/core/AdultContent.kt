package app.filmmerkezi.tv.core

import org.json.JSONObject
import org.json.JSONArray
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document

data class AdultEvidence(val page:String,val reason:String,val confirmed:Boolean=true)

/** Positive sexual-category evidence only. An age rating or a word in SEO/CSS is not evidence. */
object AdultContent {
    private val categories=setOf("erotik","erotik film","erotik filmler","erotik filmleri","erotic","erotic movies","adult","adult movies","yetiskin","yetiskin filmler","yetiskin filmleri","pornographic")
    fun category(v:String)=TextKey.fold(v).trim() in categories
    fun blocked(c:Card)=c.adultEvidence?.confirmed==true
    fun blocked(g:UnifiedItem)=blocked(g.card)||g.alternatives.any(::blocked)
    fun genres(c:Card,values:List<String>,page:String=c.ref.pageUrl)=values.firstOrNull(::category)?.let{c.copy(adultEvidence=AdultEvidence(page,"genre:"+TextKey.fold(it)))}?:c
    private val explicitDescription=Regex("(?:(?:tur|kategori|icerik turu|genre|category|content type) )?(?:bir )?(?:erotik|yetiskin|pornografik|erotic|adult|pornographic) (?:film|filmi|filmdir|filmidir)|bu (?:film|yapim|icerik|eser) (?:bir )?(?:erotik|yetiskin|pornografik) (?:filmdir|filmidir)|this (?:film|movie) is an? (?:erotic|adult|pornographic) film")
    fun declaredDescription(value:Detail):Detail {
        if(blocked(value.card)||value.card.ref.kind!=ContentKind.MOVIE)return value
        val sentences=value.description.take(4097).split(Regex("[.!?;\\r\\n…]+"))
        val complete=if(value.description.length>4096)sentences.dropLast(1)else sentences
        return if(complete.any{explicitDescription.matches(TextKey.fold(it))})
            value.copy(card=value.card.copy(adultEvidence=AdultEvidence(value.card.ref.pageUrl,"description:classification"))) else value
    }
    fun listing(body:String,page:CatalogPage,site:SiteSpec)=
        if(page.cards.isEmpty())page else listing(HtmlData.document(body,site.base+"/"),page,site)

    internal fun listing(d:Document,page:CatalogPage,site:SiteSpec):CatalogPage {
        if(page.cards.isEmpty())return page
        val nodes=d.select(site.cards)
        return page.copy(cards=page.cards.map{c->
            val node=nodes.firstOrNull{n->(listOf(n)+n.select("a[href]")).any{site.catalogUrl(it.attr("href"),site.base+"/")?.let(site::canonical)==site.canonical(c.ref.pageUrl)}}
            if(node==null)c else genres(c,node.select("[itemprop=genre], .genres, .genre, [data-genres]").flatMap{n->
                if(n.hasAttr("data-genres"))n.attr("data-genres").split(',', '|')else listOf(n.text())})
        })
    }
    fun detail(body:String,value:Detail):Detail {
        val c=value.card;val site=Sites.get(c.ref.siteId);val d=HtmlData.document(body,c.ref.pageUrl)
        fun same(u:String)=site.catalogUrl(u,c.ref.pageUrl)?.substringBefore('#')?.trimEnd('/')==c.ref.pageUrl.trimEnd('/')
        val schema=HtmlData.schemas(d).firstOrNull{o->
            val type=o.string("@type");type in setOf("Movie","TVSeries","TVEpisode")&&
                (same(o.string("url"))||same(o.string("@id"))||o.string("url").isBlank()&&o.string("@id").isBlank()&&TextKey.fold(o.string("name"))==TextKey.fold(c.title))
        }
        val tags=when(val x=schema?.opt("genre")){is JSONArray->x.values().filterIsInstance<String>();is String->x.split(',','/');else->emptyList()}
        val selector=when(site.id){
            "filmmakinesi"->"#info--box .type a"
            "turkcefilmler"->"#video_infos [itemprop=genre], #video_infos a[rel~=category]"
            "fixfullfilmizle"->".filmaltiaciklama a[rel~=category], .filmaltiaciklama [itemprop=genre]"
            "filmifullizle"->"#filmbilgileri a[href*=/tur/], #filmbilgileri [itemprop=genre]"
            "filmcibaba"->".p-v12-genres a, .p-v12-categories a, .p-v12-meta [itemprop=genre]"
            "hdfilmcehennemi"->".post-info-content [itemprop=genre], .post-info-genres a"
            else->"article [itemprop=genre], main [itemprop=genre]"
        }
        val own=d.select(selector).filter{n->n.parents().none{it.normalName() in setOf("nav","aside","footer","header")}}.map(Element::text)
        return declaredDescription(value.copy(card=genres(c,tags+own)))
    }
}
