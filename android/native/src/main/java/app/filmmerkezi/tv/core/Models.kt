package app.filmmerkezi.tv.core

import java.net.URI
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

enum class ContentKind { MOVIE, SERIES, EPISODE, COLLECTION }
data class ContentRef(val siteId: String, val pageUrl: String, val kind: ContentKind = ContentKind.MOVIE) {
    val id: String get() = "$siteId:${kind.name.lowercase(Locale.ROOT)}:" + URI(pageUrl).path.trim('/')
}
data class EpisodeNumber(val season: Int, val number: Int) {
    init { require(season in 1..1000 && number in 1..100000) }
}
data class Card(
    val ref: ContentRef, val title: String, val poster: String = "", val year: Int? = null,
    val imdb: Double? = null, val language: String = "", val originalTitle: String = "",
    val imdbId: String = "", val aliases: List<String> = emptyList(),
    val seriesIdentity:String = "", val episodeNumber:EpisodeNumber?=null,
    val seriesRef:ContentRef?=null, val workId:String="", val identityEvidence:IdentityEvidence?=null, val genres:List<String> = emptyList(),val yearHint:Int?=null,val sourceSeason:Int?=null,val adultEvidence:AdultEvidence?=null
)
data class Episode(val card: Card, val series: ContentRef, val number: EpisodeNumber, val description: String = "",val alternatives:List<Card> = emptyList())
data class SeriesEpisodes(val episodes:List<Episode>,val failures:List<SiteFailure> = emptyList(),val unverifiedParts:Int=0,val seasons:List<Int> = episodes.map{it.number.season}.distinct().sorted()){val complete:Boolean get()=failures.isEmpty()}
data class PageCursor(val siteId: String, val url: String, val page: Int = 2, val fields: Map<String, String> = emptyMap())
data class CatalogPage(val cards: List<Card>, val next: PageCursor? = null,val coverage:FilterCoverage?=null)
data class Detail(
    val card: Card, val description: String = "", val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(), val duration: String = "", val episodes: List<Episode> = emptyList(),
    val episodePages: List<String> = emptyList(), val options: List<SourceOption> = emptyList(),
    val episodesNext: PageCursor? = null,val collectionItems:List<CollectionEntry> = emptyList(),val seasons:List<Int> = episodes.map{it.number.season}.distinct().sorted()
)
enum class OptionState { AVAILABLE, UNSUPPORTED, ACCESS_REQUIRED }
data class SourceOption(
    val id: String, val content: ContentRef, val label: String, val url: String,
    val extractor: String = "", val state: OptionState = OptionState.AVAILABLE,
    val reason: String = "", val fields: Map<String, String> = emptyMap()
)
enum class StreamType { HLS, MP4, DASH, WEBM }
data class Subtitle(val url: String, val label: String = "Altyazı", val language: String = "", val format: String = "vtt")
data class MediaProfile(val videoCodec: String? = null, val audioCodec: String? = null, val width: Int? = null, val height: Int? = null)
data class MediaAudio(val url:String,val language:String="",val label:String="",val mimeType:String="audio/mp4")
data class MediaVariant(val id:String,val url:String,val height:Int,val width:Int=0,val audio:List<MediaAudio> = emptyList(),val codec:String="",val fps:Int=0,val mimeType:String="video/mp4")
data class SessionCookie(val name:String,val value:String,val domain:String,val path:String,val expiresAt:Long,val secure:Boolean,val httpOnly:Boolean,val hostOnly:Boolean)
data class ResolvedSource(
    val id: String, val label: String, val url: String, val type: StreamType,
    val referer: String = "", val userAgent: String = "", val cookie: String = "",
    val headers: Map<String, String> = emptyMap(), val subtitles: List<Subtitle> = emptyList(),
    val playbackRequirements: Set<String> = emptySet(), val mediaProfile: MediaProfile? = null,
    val expiresAt: Long? = null, val audioStreams:List<MediaAudio> = emptyList(), val variants:List<MediaVariant> = emptyList(),val sessionCookies:List<SessionCookie> = emptyList()
)
enum class FailureKind { NETWORK, TIMEOUT, HTTP, ACCESS, PARSE, UNSUPPORTED, LIMIT, STORAGE }
class DataFailure(
    val kind: FailureKind, override val message: String, val stage: String = "",
    val httpCode: Int? = null, cause: Throwable? = null
) : Exception(message, cause)
data class SiteFailure(val siteId: String, val kind: FailureKind, val message: String, val code: Int? = null)
data class UnifiedItem(val identity: String, val card: Card, val alternatives: List<Card>,val displayKey:String="")
data class UnifiedPage(val items: List<UnifiedItem>, val next: Map<String, PageCursor>, val failures: List<SiteFailure>,
    val siteCounts:Map<String,Int> = emptyMap(),val completedSites:Int=0,val requestedSites:Int=0,val coverage:List<FilterCoverage> = emptyList())

/** Interleave providers before deduplication, so a fast/full provider cannot own the first shelf. */
object CatalogMerge {
    fun pages(rows:Map<String,CatalogPage>):List<UnifiedItem> {
        val lists=Sites.all.mapNotNull{rows[it.id]?.cards}
        val cards=buildList{for(i in 0 until (lists.maxOfOrNull{it.size}?:0))for(list in lists)list.getOrNull(i)?.let{add(it)}}
        return ContentIdentity.merge(cards)
    }
}

object TextKey {
    fun clean(value: String) = value.replace(Regex("\\s+"), " ").trim()
    fun fold(value: String): String = Normalizer.normalize(value.replace('İ', 'i').replace('ı', 'i')
        .replace('ş', 's').replace('Ş', 's').replace('ğ', 'g').replace('Ğ', 'g'), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
    fun title(value: String): String = clean(value).replace(Regex("\\s+(?:full\\s+(?:hd\\s+)?film(?:\\s+hd)?|full\\s+hd|hd\\s+film|türkçe\\s+dublaj|türkçe\\s+altyazılı)?\\s*izle$", RegexOption.IGNORE_CASE), "").trim()
    fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it.toInt() and 255) }
}

