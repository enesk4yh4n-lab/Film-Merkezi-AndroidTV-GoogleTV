package app.filmmerkezi.tv.core

import okhttp3.HttpUrl.Companion.toHttpUrl

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
}

object Sites {
    val defaults: List<SiteSpec> = listOf(
        SiteSpec("dizipal","Dizipal","https://dizipalorjinal19.com","/api/v1/search?q=","/filmler",
            "a.group.block[href]","h3",seriesPath="/diziler",
            contentPattern=Regex("^/(?:filmler|diziler|bolumler)/[a-z0-9-]+/?$"),episodeSelector="a[href*=\"/bolumler/\"]"),
        SiteSpec("hdfilmcehennemi","HDFilmCehennemi","https://www.hdfilmcehennemi.nl","/search/?q=","/",
            "a.poster, a.search-result",".title, .poster-title","h1",".post-info-content",
            year=".year, .poster-meta",imdb=".imdb, .mini-poster-imdb",language=".poster-lang",seriesPath="/yabancidiziizle-2/"),
        SiteSpec("filmcibaba","Filmci Baba","https://filmizle.ink","/?s=","/Kategori/en-populer-filmler/",
            "a.v100-card, .custom-card-wrapper, .v50-card",".v100-title, .movie-title, .v50-details h3",
            ".p-v12-main-title, h1",".p-v12-summary, .film-ozeti, [itemprop=description]",
            year=".movie-release",imdb=".v100-imdb-tag, .v50-imdb-tag",seriesPath="/Kategori/dizi-izle/"),
        SiteSpec("dizibox","Dizibox","https://www.dizibox.live","/","/dizi-arsivi/",
            ".article-series-poster, .article-series-small-grid, .detailed-article",".poster-title, .tv-title, h3",
            ".tv-overview h1",".tv-story",year=".release, .custom-fields-container",imdb=".imdb, .label-imdb b",
            seriesPath="/dizi-arsivi/",contentPattern=Regex("^/diziler/[^/]+/?$")),
        SiteSpec("diziboxum","Diziboxum","https://diziboxum.com","/?s=","/diziler/",
            "a.r-card, .single-item",".r-cap-t, .categorytitle",seriesPath="/diziler/",
            contentPattern=Regex("^/(?:dizi/[a-z0-9-]+/|[a-z0-9-]+-\\d+-sezon-\\d+-bolum(?:-[a-z0-9-]+)?/(?:[1-9]\\d*/)?)$"),
            catalogAliases=setOf("www.diziboxum.com")),
        SiteSpec("turkcefilmler","Türkçe Filmler","https://turkcefilmler.to","/arama/?s=","/",
            ".move_k","h2","h1 [itemprop=name]","#video_infos [itemprop=description]",year=".year",language=".box.type"),
        SiteSpec("hdfilmdelisi","HDFilmDelisi","https://hdfilmdelisi.one","/api/search?q=","/",
            "a.poster","h2, h3","h1",".description, [itemprop=description]",seriesPath="/api/series/new?page=1",
            contentPattern=Regex("^/(film|dizi)/[a-z0-9-]+(?:/sezon-\\d+/bolum-\\d+)?$"),catalogAliases=setOf("hdfilmdelisi1.com")),
        SiteSpec("ddizi","DDizi","https://www.ddizi.tel","/arama/","/",
            ".dizi-boxpost > a, .dizi-boxpost-cat > a","h2, h3, .title",".breadcrumbX li:last-child, .dizi_content h1",
            seriesPath="/",contentPattern=Regex("^/(diziler|izle)/\\d+/[a-z0-9.-]+(?:/sayfa-\\d+|/\\d+)?$")),
        SiteSpec("fixfullfilmizle","FixFullFilmİzle","https://www.fixfullfilmizle.net","/?s=","/",
            ".moviefilm",".movief a",".leftC h1",".filmaltiaciklama .konuozet",
            imdb=".imdbpoint",seriesPath="/",episodeSelector=".moviefilm .movief a, a[href*=\"-sezon-\"]"),
        SiteSpec("sinemacc","Sinemacc","https://sinemacc.com","/arama/?s=","/",
            ".film_kutusu, .film_uzun_kutu",".title .text, .detay > .baslik","h1.baslik",".summary, .ozet, [itemprop=description]",
            year=".years",
            contentPattern=Regex("^/film/[a-z0-9-]+/?$")),
        SiteSpec("filmizlebabam","Film İzle Babam","https://filmizlebabam.icu","/ara?q=","/film-izle/",
            "a.group.block","h3","h1","[itemprop=description]",year="p",imdb=".badge-rating",language=".badge-language",
            seriesPath="/yabanci-dizi-izle/",contentPattern=Regex("^/(?:[a-z0-9-]+/|(?:dizi|bolum)/[a-z0-9-]+/)$"),
            catalogAliases=setOf("www.filmizlebabam.com","filmizlebabam.com","www.filmizlebabam.icu")),
        SiteSpec("filmifullizle","Filmi Full İzle","https://www.filmifullizle.news","/?s=","/",
            ".movie-box",".film-ismi","h1","#film-aciklama",year=".film-yil",imdb=".imdb",language=".film-dil",
            contentPattern=Regex("^/film/[a-z0-9-]+\\.html$")),
        SiteSpec("filmizlehell","Film İzle Hell","https://filmizlehell.net","/arama?q=","/filmler",
            "a.poster-card, div.grid > div.group","h3","h1",".movie-description",
            year=".overlay, .bottom-0 span",imdb=".rating, .top-2.left-2",language=".language-badge, .top-2.right-2",
            contentPattern=Regex("^/film/\\d+-[a-z0-9-]+$")),
        SiteSpec("lovefilmizle","Love Film İzle","https://lovefilmizle.net","/?s=","/",
            ".poster",".title","h1.movie-title",".movie-description, .movie-summary",year=".icon-year",imdb=".poster-imdb",language=".poster-lang",seriesPath="/yabanci-dizi-izle/",contentPattern=Regex("^/(?:[a-z0-9-]+/|tv-dizisi/[a-z0-9-]+/)$")),
        SiteSpec("fullhdfilmizlesene","Full HD Film İzlesene","https://www.fullhdfilmizlesene.now","/arama/","/",
            ".film",".film-title","h1",".ozet, .film-ozet",year=".film-yil",imdb=".imdb",language=".trz",
            contentPattern=Regex("^/film/[a-z0-9-]+/$")),
        SiteSpec("filmmakinesi","Film Makinesi","https://filmmakinesi.to","/arama/?s=","/filmler-1/",
            "a.item[data-title]",".item-title, .title","h1","#info--box .description",
            year=".item-footer .info span:first-child",imdb=".imdb, .imdb-score, .rating",language=".item-info > div:nth-child(2)",
            seriesPath="/yabanci-dizi-izle-fmq45w/",episodeSelector="#seasons a.item-ep[href]",
            genres="#info--box .type a[href*=\"/tur/\"]",
            contentPattern=Regex("^/(?:film/[a-z0-9-]+/|dizi/[a-z0-9-]+/(?:sezon-\\d+/bolum-\\d+/)?)$"))
    )
    private data class Configuration(val addresses:Map<String,SiteAddress>,val sites:List<SiteSpec>,val revision:Long)
    private var templates=defaults
    @Volatile private var available:Set<String>?=null
    @Volatile private var configuration=Configuration(emptyMap(),defaults,0)
    val all:List<SiteSpec> get()=configuration.sites.filter{available?.contains(it.id)!=false}
    val known:List<SiteSpec> get()=configuration.sites
    @Synchronized fun installed(sites:List<SiteSpec>){templates=(sites+defaults).distinctBy{it.id};available=sites.map{it.id}.toSet();configuration=Configuration(configuration.addresses,templates.map{SiteAddresses.apply(it,configuration.addresses[it.id])},configuration.revision+1)}
    @Synchronized fun updateMetadata(site:SiteSpec){templates=(listOf(site)+templates).distinctBy{it.id};configuration=Configuration(configuration.addresses,templates.map{SiteAddresses.apply(it,configuration.addresses[it.id])},configuration.revision+1)}
    @Synchronized fun remember(sites:List<SiteSpec>){templates=(templates+sites).distinctBy{it.id};configuration=Configuration(configuration.addresses,templates.map{SiteAddresses.apply(it,configuration.addresses[it.id])},configuration.revision+1)}
    val revision:Long get()=configuration.revision
    @Synchronized fun configure(addresses:Map<String,SiteAddress>){if(addresses!=configuration.addresses)configuration=Configuration(addresses.toMap(),templates.map{SiteAddresses.apply(it,addresses[it.id])},configuration.revision+1)}
    fun current(site:SiteSpec)=SiteAddresses.apply(site,configuration.addresses[site.id])
    fun default(id:String)=templates.singleOrNull{it.id==id}?:throw DataFailure(FailureKind.PARSE,"Bilinmeyen site: $id")
    fun get(id: String): SiteSpec = known.singleOrNull { it.id == id } ?: throw DataFailure(FailureKind.PARSE,"Bilinmeyen site: $id")
}
