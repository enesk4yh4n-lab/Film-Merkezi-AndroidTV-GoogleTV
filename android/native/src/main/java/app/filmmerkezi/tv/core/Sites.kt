package app.filmmerkezi.tv.core

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SiteSpec(
    val id: String, val name: String, val base: String, val search: String, val list: String,
    val cards: String, val title: String, val detailTitle: String = "h1", val description: String = "[itemprop=description], .movie-description",
    val poster: String = "img", val year: String = ".year", val imdb: String = ".imdb",
    val language: String = ".language", val seriesPath: String? = null,
    val contentPattern: Regex = Regex("^/[a-z0-9-]+/(?:[1-9]\\d*/)?$"),
    val episodeSelector: String = "a[href*=\"-sezon-\"][href*=\"-bolum\"], a[href*=\"/bolum/\"], a.season-episode",
    val genres: String = "[itemprop=genre], a[rel~=category], a[href*=\"/tur/\"], a[href*=\"/kategori/\"]",
    val catalogAliases:Set<String> = emptySet()
) {
    fun catalogUrl(value: String, page: String = "$base/"): String? {
        if(value.isBlank()||value.trim().startsWith("#"))return null
        val url = page.toHttpUrl().resolve(value.trim()) ?: return null
        val origin = base.toHttpUrl()
        return url.takeIf { it.scheme == origin.scheme && (it.host == origin.host||it.host in catalogAliases) && it.port == origin.port && it.username.isEmpty() && it.password.isEmpty() }
            ?.newBuilder()?.host(origin.host)?.fragment(null)?.build()?.toString()
    }
    fun canonical(value: String): String = if (id in setOf("filmcibaba", "lovefilmizle", "fixfullfilmizle")) value.replace(Regex("/[1-9]\\d*/$"), "/") else value
    fun kind(url: String): ContentKind = when {
        Regex("/(?:bolum/|bolumler/|izle/\\d+/|sezon-\\d+/bolum-)|-\\d+-sezon-\\d+-bolum").containsMatchIn(url) -> ContentKind.EPISODE
        Regex("/(?:dizi/|diziler/|tv-dizisi/)|-\\d+-sezon-").containsMatchIn(url) || id == "dizibox" -> ContentKind.SERIES
        else -> ContentKind.MOVIE
    }
    fun supports(kind:ContentKind)=when(kind){
        ContentKind.SERIES,ContentKind.EPISODE->seriesPath!=null
        ContentKind.MOVIE->seriesPath==null||list!=seriesPath
        ContentKind.COLLECTION->true
    }
}

object Sites {
    /** Optional historical fixture/migration source set. The normal APK has no site inventory. */
    val defaults: List<SiteSpec> = runCatching {
        @Suppress("UNCHECKED_CAST")
        (Class.forName("app.filmmerkezi.tv.core.LegacySites").getMethod("definitions").invoke(null) as List<SiteSpec>)
    }.getOrDefault(emptyList())
    private data class Configuration(val addresses:Map<String,SiteAddress>,val sites:List<SiteSpec>,val revision:Long)
    private var templates=defaults
    @Volatile private var available:Set<String> = defaults.map{it.id}.toSet()
    @Volatile private var configuration=Configuration(emptyMap(),defaults,0)
    private val installedState=MutableStateFlow(configuration.sites.filter{it.id in available})
    val installedChanges=installedState.asStateFlow()
    val all:List<SiteSpec> get()=configuration.sites.filter{it.id in available}
    val known:List<SiteSpec> get()=configuration.sites
    fun isInstalled(id:String)=all.any{it.id==id}
    fun orderedInstalled(order:List<String>):List<SiteSpec> {
        val active=all.associateBy{it.id}
        return (order+active.keys).distinct().mapNotNull(active::get)
    }
    @Synchronized fun installed(sites:List<SiteSpec>){templates=(sites+defaults).distinctBy{it.id};available=sites.map{it.id}.toSet();configuration=Configuration(configuration.addresses,templates.map{SiteAddresses.apply(it,configuration.addresses[it.id])},configuration.revision+1);installedState.value=all}
    @Synchronized fun updateMetadata(site:SiteSpec){templates=(listOf(site)+templates).distinctBy{it.id};configuration=Configuration(configuration.addresses,templates.map{SiteAddresses.apply(it,configuration.addresses[it.id])},configuration.revision+1);installedState.value=all}
    @Synchronized fun remember(sites:List<SiteSpec>){templates=(templates+sites).distinctBy{it.id};configuration=Configuration(configuration.addresses,templates.map{SiteAddresses.apply(it,configuration.addresses[it.id])},configuration.revision+1);installedState.value=all}
    val revision:Long get()=configuration.revision
    @Synchronized fun configure(addresses:Map<String,SiteAddress>){if(addresses!=configuration.addresses){configuration=Configuration(addresses.toMap(),templates.map{SiteAddresses.apply(it,addresses[it.id])},configuration.revision+1);installedState.value=all}}
    fun current(site:SiteSpec)=SiteAddresses.apply(site,configuration.addresses[site.id])
    fun default(id:String)=templates.singleOrNull{it.id==id}?:throw DataFailure(FailureKind.PARSE,"Bilinmeyen site: $id")
    fun get(id: String): SiteSpec = known.singleOrNull { it.id == id } ?: throw DataFailure(FailureKind.PARSE,"Bilinmeyen site: $id")
    /** Recover an archived card without embedding a legacy provider list or activating a provider. */
    @Synchronized fun forRecord(id:String,page:String):SiteSpec {
        known.firstOrNull{it.id==id}?.let{return it}
        require(id.matches(Regex("[a-z][a-z0-9-]{0,63}"))){"Kayıt site kimliği bozuk"}
        val url=page.toHttpUrl()
        val origin=SiteAddresses.normalize(url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString())
        val archived=SiteSpec(id,id,origin,"","","","",contentPattern=Regex("^/.*$"))
        remember(listOf(archived));return archived
    }
}
