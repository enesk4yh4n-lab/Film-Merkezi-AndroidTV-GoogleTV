package app.filmmerkezi.tv.core

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

enum class CatalogSection {
    ANIMATION, DRAMA_SHORT;

    private val defaultKinds get()=if(this==ANIMATION)setOf(ContentKind.MOVIE,ContentKind.SERIES)else setOf(ContentKind.SERIES)

    internal fun routes(siteId:String):List<SectionRoute> = Sites.declaredSections(siteId)?.let{it[this].orEmpty()}?:legacyRoutes(siteId)

    private fun legacyRoutes(siteId:String):List<SectionRoute> = when(siteId){
        "filmcibaba"->when(this){
            ANIMATION->listOf(SectionRoute("/Kategori/tur/animasyon-filmleri/",defaultKinds),SectionRoute("/Kategori/anime-izle/",defaultKinds))
            DRAMA_SHORT->listOf(SectionRoute("/Kategori/k-drama-reels-asya-dizileri/",setOf(ContentKind.MOVIE,ContentKind.SERIES)))
        }
        "diziboxum"->when(this){
            DRAMA_SHORT->listOf(SectionRoute("/asya-dizileri/",defaultKinds))
            else->emptyList()
        }
        else->emptyList()
    }

    fun accepts(card:Card):Boolean {
        val genres=card.genres.map(TextKey::fold)
        return card.ref.kind in defaultKinds&&genres.any{it in if(this==ANIMATION)animation else dramaShort}
    }

    companion object {
        private val animation=setOf("animasyon","animasyon filmleri","animation","animated","anime","anime dizileri","cizgi film","cizgi filmler","cizgifilm","cartoon")
        private val dramaShort=setOf("kore dizisi","kore dizileri","asya dizisi","asya dizileri","asian drama","k drama","korean drama","short drama","short series",
            "kisa dizi","kisa diziler","mini drama","bitesize drama","vertical drama","vertical series")

        internal fun fromManifest(site:JSONObject):Map<CatalogSection,List<SectionRoute>>? {
            if(!site.has("catalogSections"))return null
            val sections=site.getJSONObject("catalogSections")
            return sections.keys().asSequence().associate { name ->
                val section=valueOf(name)
                val entries=sections.getJSONArray(name)
                require(entries.length() in 1..4){"Kategori yolu sayısı geçersiz"}
                val routes=(0 until entries.length()).map { index ->
                    val entry=entries.getJSONObject(index)
                    require(entry.keys().asSequence().all{it in setOf("path","kinds","pageParameter")}){"Bilinmeyen kategori alanı"}
                    val path=entry.getString("path")
                    require(path.length<=180&&path.matches(Regex("/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]+/?"))){"Kategori yolu geçersiz"}
                    val parameter=if(entry.has("pageParameter"))entry.getString("pageParameter")else "sayfa"
                    require(parameter.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,31}"))){"Kategori sayfa parametresi geçersiz"}
                    val kinds=if(entry.has("kinds"))entry.getJSONArray("kinds").let{values->
                        require(values.length() in 1..2)
                        (0 until values.length()).map{ContentKind.valueOf(values.getString(it))}.toSet().also{set->
                            require(set.size==values.length()&&set.all{it==ContentKind.MOVIE||it==ContentKind.SERIES}){"Kategori içerik türü geçersiz"}
                        }
                    }else section.defaultKinds
                    SectionRoute(path,kinds,parameter)
                }
                require(routes.distinctBy{it.path.trimEnd('/')}.size==routes.size){"Kategori yolu yineleniyor"}
                section to routes
            }
        }
    }
}

internal data class SectionRoute(val path:String,val kinds:Set<ContentKind>,val pageParameter:String="sayfa")

internal fun sectionPageNumber(site:SiteSpec,route:SectionRoute,url:String):Int? {
    val u=site.catalogUrl(url)?.toHttpUrl()?:return null
    val path=u.encodedPath.trimEnd('/')
    val category=route.path.trimEnd('/')
    if(path==category){
        if(u.querySize==0)return 1
        return u.queryParameter(route.pageParameter)?.toIntOrNull()?.takeIf{u.querySize==1&&it in 1..100000}
    }
    val prefix="$category/page/"
    return path.takeIf{u.querySize==0&&it.startsWith(prefix)}?.removePrefix(prefix)?.toIntOrNull()?.takeIf{it in 2..100000}
}
