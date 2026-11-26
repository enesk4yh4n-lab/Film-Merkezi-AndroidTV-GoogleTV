package app.filmmerkezi.tv.core

import org.json.JSONObject
import org.json.JSONArray
import java.util.Locale

/** Versioned diagnostic/persistence JSON. Native callers use the typed models. */
object Contracts {
    const val VERSION=2
    fun ref(v:ContentRef)=JSONObject().put("siteId",v.siteId).put("pageUrl",v.pageUrl).put("kind",v.kind.name.lowercase(Locale.ROOT))
    fun card(v:Card)=JSONObject().put("schemaVersion",VERSION).put("ref",ref(v.ref)).put("title",v.title).put("poster",v.poster)
        .put("year",v.year?:JSONObject.NULL).put("imdb",v.imdb?:JSONObject.NULL).put("language",v.language)
        .put("originalTitle",v.originalTitle).put("imdbId",v.imdbId).put("aliases",JSONArray(v.aliases)).put("seriesIdentity",v.seriesIdentity)
        .put("episode",v.episodeNumber?.let{JSONObject().put("season",it.season).put("number",it.number)}?:JSONObject.NULL)
        .put("workId",v.workId).put("yearHint",v.yearHint?:JSONObject.NULL).put("sourceSeason",v.sourceSeason?:JSONObject.NULL).put("genres",JSONArray(v.genres)).apply{v.identityEvidence?.let{e->put("identityEvidence",evidence(e))};v.seriesRef?.let{put("seriesRef",ref(it))};v.adultEvidence?.let{put("adultEvidence",JSONObject().put("page",it.page).put("reason",it.reason).put("confirmed",it.confirmed))}}
    fun evidence(e:IdentityEvidence)=JSONObject().put("pageUrl",e.pageUrl).put("kind",e.kind?.name).put("year",e.year?:JSONObject.NULL)
        .put("imdbId",e.imdbId).put("originalTitle",e.originalTitle).put("title",e.title).put("aliases",JSONArray(e.aliases.take(8)))
        .put("typeVerified",e.typeVerified).put("yearVerified",e.yearVerified).put("detailChecked",e.detailChecked).put("imdbConflict",e.imdbConflict)
        .put("reason",e.reason).put("referencePage",e.referencePage).put("season",e.season?:JSONObject.NULL).put("revision",e.revision)
    fun readEvidence(e:JSONObject)=IdentityEvidence(e.getString("pageUrl"),runCatching{ContentKind.valueOf(e.optString("kind"))}.getOrNull(),
        e.optInt("year").takeIf{it in 1888..2100},e.string("imdbId"),e.string("originalTitle"),e.optJSONArray("aliases")?.values()?.take(8)?.map{it.toString().take(300)}?:emptyList(),
        e.string("title"),e.optBoolean("typeVerified"),e.optBoolean("yearVerified"),e.optBoolean("detailChecked"),e.optBoolean("imdbConflict"),e.string("reason"),e.string("referencePage"),e.optInt("season").takeIf{it in 1..1000},e.optInt("revision",0))
    fun readCard(o:JSONObject):Card {
        require(o.getInt("schemaVersion") in 1..VERSION)
        val r=o.getJSONObject("ref");val site=Sites.get(r.getString("siteId"))
        val url=site.catalogUrl(r.getString("pageUrl"))?:throw DataFailure(FailureKind.STORAGE,"Kayıt adresi bozuk","storage")
        return Card(ContentRef(site.id,url,ContentKind.valueOf(r.getString("kind").uppercase(Locale.ROOT))),o.getString("title"),SiteAddresses.rebase(o.string("poster"),site),
            if(o.isNull("year"))null else o.getInt("year"),if(o.isNull("imdb"))null else o.getDouble("imdb"),o.string("language"),
            o.string("originalTitle"),o.string("imdbId"),o.optJSONArray("aliases")?.values()?.map{it.toString()}?:emptyList(),
            o.string("seriesIdentity"),o.optJSONObject("episode")?.let{EpisodeNumber(it.getInt("season"),it.getInt("number"))},
            o.optJSONObject("seriesRef")?.let{r->
                val s=Sites.get(r.getString("siteId"));val url=s.catalogUrl(r.getString("pageUrl"))
                    ?:throw DataFailure(FailureKind.STORAGE,"Dizi adresi bozuk","storage")
                ContentRef(s.id,url,ContentKind.SERIES)
            },o.string("workId"),o.optJSONObject("identityEvidence")?.let(::readEvidence),o.optJSONArray("genres")?.values()?.map{it.toString()}?:emptyList(),o.optInt("yearHint").takeIf{it in 1888..2100},o.optInt("sourceSeason").takeIf{it in 1..1000},o.optJSONObject("adultEvidence")?.let{AdultEvidence(it.string("page"),it.string("reason"),it.optBoolean("confirmed"))})
    }
    fun option(v:SourceOption)=JSONObject().put("id",v.id).put("content",ref(v.content)).put("label",v.label).put("url",v.url)
        .put("extractor",v.extractor).put("state",v.state.name.lowercase(Locale.ROOT)).put("reason",v.reason).put("fields",JSONObject(v.fields))
    fun cursor(v:PageCursor)=JSONObject().put("siteId",v.siteId).put("url",v.url).put("page",v.page).put("fields",JSONObject(v.fields))
    fun source(v:ResolvedSource)=JSONObject().put("schemaVersion",VERSION).put("id",v.id).put("label",v.label).put("url",v.url)
        .put("type",v.type.name.lowercase(Locale.ROOT)).put("referer",v.referer).put("userAgent",v.userAgent).put("cookie",v.cookie)
        .put("headers",JSONObject(v.headers)).put("subtitles",JSONArray(v.subtitles.map{s->JSONObject().put("url",s.url).put("label",s.label).put("language",s.language).put("format",s.format)}))
        .put("playbackRequirements",JSONArray(v.playbackRequirements.toList())).put("expiresAt",v.expiresAt?:JSONObject.NULL)
        .put("sessionCookies",JSONArray(v.sessionCookies.map{c->JSONObject().put("name",c.name).put("value",c.value).put("domain",c.domain).put("path",c.path).put("expiresAt",c.expiresAt).put("secure",c.secure).put("httpOnly",c.httpOnly).put("hostOnly",c.hostOnly)}))
        .put("audioStreams",audio(v.audioStreams)).put("variants",JSONArray(v.variants.map{p->JSONObject().put("id",p.id).put("url",p.url)
            .put("height",p.height).put("width",p.width).put("codec",p.codec).put("fps",p.fps).put("mimeType",p.mimeType).put("audioStreams",audio(p.audio))}))
        .put("mediaProfile",v.mediaProfile?.let{p->JSONObject().put("videoCodec",p.videoCodec?:JSONObject.NULL).put("audioCodec",p.audioCodec?:JSONObject.NULL)
            .put("width",p.width?:JSONObject.NULL).put("height",p.height?:JSONObject.NULL)}?:JSONObject.NULL)
    private fun audio(values:List<MediaAudio>)=JSONArray(values.map{JSONObject().put("url",it.url).put("language",it.language).put("label",it.label).put("mimeType",it.mimeType)})
    fun detail(v:Detail)=JSONObject().put("schemaVersion",VERSION).put("card",card(v.card)).put("description",v.description)
        .put("genres",JSONArray(v.genres)).put("cast",JSONArray(v.cast)).put("duration",v.duration)
        .put("episodes",JSONArray(v.episodes.map{e->JSONObject().put("card",card(e.card)).put("series",ref(e.series)).put("season",e.number.season).put("number",e.number.number)}))
        .put("options",JSONArray(v.options.map(::option))).put("episodePages",JSONArray(v.episodePages))
        .put("episodesNext",v.episodesNext?.let(::cursor)?:JSONObject.NULL)
    fun unified(v:UnifiedPage)=JSONObject().put("schemaVersion",VERSION)
        .put("siteCounts",JSONObject(v.siteCounts)).put("completedSites",v.completedSites).put("requestedSites",v.requestedSites)
        .put("items",JSONArray(v.items.map{u->JSONObject().put("identity",u.identity).put("card",card(u.card)).put("alternatives",JSONArray(u.alternatives.map(::card)))}))
        .put("next",JSONObject().apply{v.next.forEach{(id,c)->put(id,cursor(c))}})
        .put("failures",JSONArray(v.failures.map{f->JSONObject().put("siteId",f.siteId).put("kind",f.kind.name.lowercase(Locale.ROOT)).put("message",f.message).put("code",f.code?:JSONObject.NULL)}))
}
