package app.filmmerkezi.tv.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

data class SiteAddress(val base:String,val name:String="",val previousHosts:Set<String> = emptySet())

/** Address changes preserve provider IDs, paths and the history of explicitly saved hosts. */
object SiteAddresses {
    fun normalize(value:String):String {
        val input=value.trim();val url=(if("://" in input)input else "https://$input").toHttpUrlOrNull()
            ?:throw IllegalArgumentException("Geçerli bir site adresi yazın.")
        require(url.scheme=="https"&&url.port==443&&url.username.isEmpty()&&url.password.isEmpty()&&url.encodedPath=="/"&&url.query==null&&url.fragment==null&&publicHost(url.host)){
            "Yalnız HTTPS site adresini yazın; bölüm yolu, kullanıcı bilgisi veya ek parametre kullanmayın."
        }
        return url.newBuilder().build().toString().trimEnd('/')
    }
    private fun publicHost(host:String)=host.length<=253&&host.contains('.')&&!host.endsWith(".local")&&!host.endsWith(".localhost")&&host.split('.').all{it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))}&&!host.all{it.isDigit()||it=='.'}
    fun hosts(site:SiteSpec)=site.catalogAliases+site.base.toHttpUrl().host
    private fun variants(host:String)=setOf(host,if(host.startsWith("www."))host.removePrefix("www.")else "www.$host")
    fun change(current:Map<String,SiteAddress>,id:String,base:String,name:String):Map<String,SiteAddress> {
        val original=Sites.default(id);val normalized=normalize(base);val host=normalized.toHttpUrl().host
        val label=name.trim();require(label.length in 1..80&&!label.any{it.isISOControl()}){"Site adı 1–80 karakter olmalı."}
        require(Sites.all.filter{it.id!=id}.none{other->hosts(other).any{it.removePrefix("www.")==host.removePrefix("www.")}}){"Bu adres başka bir siteye ait; o siteyi düzenleyin."}
        val previous=(current[id]?.previousHosts.orEmpty()+current[id]?.base?.toHttpUrl()?.host.orEmpty()+hosts(original)).filter{it.isNotBlank()}.toSet()
        return current+(id to SiteAddress(normalized,label,previous))
    }
    fun reset(current:Map<String,SiteAddress>,id:String)=change(current,id,Sites.default(id).base,Sites.default(id).name)
    fun apply(site:SiteSpec,entry:SiteAddress?):SiteSpec {
        if(entry==null)return site
        val host=entry.base.toHttpUrl().host
        return site.copy(base=entry.base,name=entry.name.ifBlank{site.name},catalogAliases=(hosts(site)+entry.previousHosts+variants(host)).flatMap(::variants).toSet()-host)
    }
    fun encode(values:Map<String,SiteAddress>)=JSONObject().apply{values.toSortedMap().forEach{(id,v)->put(id,JSONObject().put("base",v.base).put("name",v.name).put("previousHosts",JSONArray(v.previousHosts.sorted())))}}.toString()
    fun decode(raw:Any?):Map<String,SiteAddress> = runCatching {
        val text=(raw as? String).orEmpty();if(text.length>65536)return@runCatching emptyMap()
        val json=JSONObject(text.ifBlank{"{}"});Sites.defaults.mapNotNull{site->
            val value=json.optJSONObject(site.id)?:return@mapNotNull null
            val base=runCatching{normalize(value.getString("base"))}.getOrNull()?:return@mapNotNull null
            val name=value.optString("name").trim().takeIf{it.length in 1..80&&!it.any(Char::isISOControl)}?:site.name
            val hosts=value.optJSONArray("previousHosts")?.values()?.filterIsInstance<String>()?.filter(::publicHost)?.take(64)?.toSet().orEmpty()
            site.id to SiteAddress(base,name,hosts)
        }.toMap()
    }.getOrDefault(emptyMap())
    fun rebase(value:String,site:SiteSpec):String=site.catalogUrl(value)?:value
    fun rebaseKnown(value:String):String=Sites.all.firstNotNullOfOrNull{it.catalogUrl(value)}?:value
    fun card(value:Card):Card {
        val site=Sites.get(value.ref.siteId)
        return value.copy(ref=value.ref.copy(pageUrl=rebase(value.ref.pageUrl,site)),poster=rebase(value.poster,site),
            seriesRef=value.seriesRef?.let{it.copy(pageUrl=rebase(it.pageUrl,Sites.get(it.siteId)))})
    }
    fun userAgent(url:HttpUrl):String {
        val original=Sites.defaults.firstOrNull{site->url.host in hosts(Sites.get(site.id))}
        return app.filmmerkezi.tv.SiteHttpPolicy.defaultUserAgent(original?.base?.toHttpUrl()?:url)
    }
}
