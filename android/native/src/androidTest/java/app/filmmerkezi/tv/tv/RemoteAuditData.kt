package app.filmmerkezi.tv.tv

import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.flow.*

/** Metadata-only fixture. No provider calls, adult content or persistent viewing changes. */
internal class RemoteAuditData:TvData {
    var includeRecords=true
    val movies=(1..36).map{i->Card(ContentRef("filmcibaba","https://fixture.example/movie-$i/"),"Deneme filmi $i",year=2024,imdb=7.5,workId="work:remote-movie-$i",genres=listOf("Macera"))}
    val series=Card(ContentRef("filmcibaba","https://fixture.example/series/",ContentKind.SERIES),"Deneme dizisi",year=2024,workId="work:remote-series")
    val collection=Card(ContentRef("filmcibaba","https://fixture.example/collection/",ContentKind.COLLECTION),"Deneme koleksiyonu",workId="work:remote-collection")
    val eps=(1..3).flatMap{s->(1..12).map{n->Episode(Card(ContentRef("filmcibaba","https://fixture.example/s${s}e$n/",ContentKind.EPISODE),"$s. Sezon $n. Bölüm",workId="work:remote-series",episodeNumber=EpisodeNumber(s,n),seriesRef=series.ref),series.ref,EpisodeNumber(s,n))}}
    fun item(card:Card)=UnifiedItem(card.workId,card,listOf(card))
    override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage((if(kind==ContentKind.SERIES)listOf(series)else movies).map(::item),emptyMap(),emptyList()))
    override fun search(query:String,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage((movies+series+collection).map(::item),emptyMap(),emptyList()))
    override suspend fun detail(card:Card)=if(card.ref.kind==ContentKind.COLLECTION)Detail(card,collectionItems=movies.mapIndexed{i,c->CollectionEntry(i+1,c.title,2024)})
        else Detail(card,description="Açıklama ".repeat(500),genres=listOf("Macera"),cast=(1..12).map{"Oyuncu $it"},episodes=if(card.ref.kind==ContentKind.SERIES)eps else emptyList(),options=listOf(SourceOption(card.ref.id,card.ref,"Yerel kaynak",card.ref.pageUrl)))
    override suspend fun work(card:Card)=item(card)
    override suspend fun episodes(card:Card)=eps
    override suspend fun resolve(option:SourceOption):ResolvedSource=error("Metadata navigation must not start playback")
    override suspend fun records()=if(includeRecords)(movies.take(14)+series+collection).map{LibraryRecord(it.workId,it,true,25000,600000,1)}else emptyList()
    override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){}
    override suspend fun remember(query:String){}
    override suspend fun searches()=listOf("Deneme","Macera","Dizi")
    override suspend fun filterOptions(siteId:String)=FilterOptions(mapOf("genre" to listOf(FilterValue("macera","Macera"),FilterValue("komedi","Komedi"))))
    override suspend fun collections()=listOf(collection)
    override suspend fun collectionMatches(entry:CollectionEntry,search:Boolean)=listOf(item(movies[entry.ordinal-1]))
    override fun recommendations(criteria:CatalogQuery,siteId:String)=flowOf(RecommendationResult(movies.take(3).map(::item),3,1,1,true))
}
