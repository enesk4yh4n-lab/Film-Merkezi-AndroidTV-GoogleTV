package app.filmmerkezi.tv.core

import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

/** Regression inputs are recorded site HTML, not invented missing episode URLs. */
class EpisodeCatalogTest {
    private fun html(name:String)=checkNotNull(javaClass.getResource("/episodes/$name.html")).readText()
    private fun card(site:String,path:String,title:String)=Card(ContentRef(site,Sites.get(site).base+path,ContentKind.SERIES),title)
    @Test fun vladimirKeepsSeriesTypeDespiteSitesGenericMovieSchema()=runBlocking {
        val c=card("filmcibaba","/vladimir-i2/","Vladimir 1.Sezon")
        val repository=CatalogRepository(object:WebTransport{override suspend fun request(request:WebRequest)=WebResponse(request.url,html("vladimir"))})
        val d=repository.detail(c)
        assertEquals(ContentKind.SERIES,d.card.ref.kind)
        assertEquals((1..8).toList(),d.episodes.map{it.number.number})
        assertEquals(8,repository.episodes(d.card).size)
    }
    @Test fun ddiziPaginationNeverSelectsHomeOrOtherSeries()=runBlocking {
        val c=card("ddizi","/diziler/1815/dark-hd1","Dark")
        val requests=mutableListOf<String>()
        val network=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse {
            requests.add(request.url)
            val file=when(request.url){c.ref.pageUrl->"ddizi";c.ref.pageUrl+"/sayfa-1"->"ddizi-1";c.ref.pageUrl+"/sayfa-2"->"ddizi-2";else->error("Wrong episode archive: ${request.url}")}
            return WebResponse(request.url,html(file))
        }}
        val episodes=NativeSiteProvider(Sites.get("ddizi"),network,Dispatchers.Unconfined).episodes(c)
        assertEquals(26,episodes.size)
        assertEquals(EpisodeNumber(1,1),episodes.first().number)
        assertEquals(listOf(10,8,8),episodes.groupBy{it.number.season}.values.map{it.size})
        assertEquals(3,requests.size)
    }
    @Test fun declaredSeasonsAreFollowedRecursivelyAndDoNotMixArchives()=runBlocking {
        val c=card("filmizlebabam","/dizi/example/","Example")
        fun page(s:Int,next:Int?)="""<h1>Example</h1><a href='/'>Home</a>
            <a href='/bolum/example-$s-sezon-1-bolum/'>$s. Sezon 1. Bölüm</a>
            ${next?.let{"<a href='?sezon=$it#bolumler'>$it. Sezon</a>"}.orEmpty()}
            <a href='/dizi/another/?sezon=9'>Other show</a>"""
        val requests=mutableListOf<String>()
        val provider=NativeSiteProvider(Sites.get("filmizlebabam"),object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
            requests.add(request.url);val s=request.url.toHttpUrl().queryParameter("sezon")?.toInt()?:1
            require(request.url.startsWith(c.ref.pageUrl));return WebResponse(request.url,page(s,if(s<3)s+1 else null))
        }},Dispatchers.Unconfined)
        assertEquals(listOf(1,2,3),provider.episodes(c).map{it.number.season})
        assertEquals(3,requests.size)
    }
    @Test fun currentDiziboxAndDelisiHtmlRetainEveryAvailableSeason()=runBlocking {
        for((site,path,file,count,seasons) in listOf(
            listOf("dizibox","/diziler/visionquest/","dizibox","7","1"),
            listOf("hdfilmdelisi","/dizi/breaking-bad","delisi","62","5"),
            listOf("hdfilmdelisi","/dizi/the-mentalist","delisi-multi","151","7")
        )){
            val c=card(site,path,"Fixture series")
            val repository=CatalogRepository(object:WebTransport{override suspend fun request(request:WebRequest)=WebResponse(request.url,html(file))})
            val d=repository.detail(c)
            assertEquals(site,ContentKind.SERIES,d.card.ref.kind)
            assertEquals(site,count.toInt(),d.episodes.size)
            assertEquals(site,seasons.toInt(),d.episodes.map{it.number.season}.distinct().size)
            assertEquals(EpisodeNumber(1,1),d.episodes.first().number)
        }
    }
    @Test fun diziboxMultiSeasonSchemaCannotTurnSeriesIntoEpisode()=runBlocking {
        val c=card("dizibox","/diziler/12-monkeys-izle-1/","12 Monkeys")
        val network=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
            val season=Regex("/([1-4])-sezon-12-monkeys/").find(request.url)?.groupValues?.get(1)
            return WebResponse(request.url,html(if(season==null)"dizibox-multi"else "dizibox-season-$season"))
        }}
        val repository=CatalogRepository(network);val d=repository.detail(c)
        assertEquals(ContentKind.SERIES,d.card.ref.kind)
        val episodes=repository.episodes(d.card)
        assertEquals(listOf(13,13,10,11),episodes.groupBy{it.number.season}.values.map{it.size})
        assertEquals(EpisodeNumber(1,1),episodes.first().number)
        val oldLabel=c.copy(ref=c.ref.copy(kind=ContentKind.EPISODE),identityEvidence=IdentityEvidence(c.ref.pageUrl,ContentKind.EPISODE,typeVerified=true,detailChecked=true))
        assertEquals(ContentKind.SERIES,repository.detail(oldLabel).card.ref.kind)
    }
    @Test fun historicalBabamArchiveIncludesAllSevenSeasons()=runBlocking {
        val c=card("filmizlebabam","/dizi/the-mentalist/","The Mentalist")
        val provider=NativeSiteProvider(Sites.get("filmizlebabam"),object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
            val season=request.url.toHttpUrl().queryParameter("sezon")?.toInt()?:1
            return WebResponse(request.url,html("babam-$season"))
        }},Dispatchers.Unconfined)
        val episodes=provider.episodes(c)
        assertEquals(151,episodes.size)
        assertEquals(listOf(23,23,24,24,22,22,13),episodes.groupBy{it.number.season}.values.map{it.size})
    }
    @Test fun ddiziSameNumericSeriesRedirectWorksButForeignRedirectFails()=runBlocking {
        val c=card("ddizi","/diziler/1815/dark-hd","Dark")
        val target=Sites.get("ddizi").base+"/diziler/1815/dark-hd1"
        val provider=NativeSiteProvider(Sites.get("ddizi"),object:WebTransport{override suspend fun request(request:WebRequest)=WebResponse(target,html("ddizi"))},Dispatchers.Unconfined)
        assertEquals(target,provider.detail(c).card.ref.pageUrl)
        val bad=NativeSiteProvider(Sites.get("ddizi"),object:WebTransport{override suspend fun request(request:WebRequest)=WebResponse(target.replace("1815","9999"),html("ddizi"))},Dispatchers.Unconfined)
        try{bad.detail(c);fail("Foreign series redirect must not be accepted")}catch(e:DataFailure){assertEquals(FailureKind.PARSE,e.kind)}
    }
    @Test fun linkedEpisodeRemainsEpisodeWhenPageReusesMovieSchema(){
        val series=card("filmcibaba","/vladimir-i2/","Vladimir 1.Sezon")
        val episode=series.copy(ref=series.ref.copy(kind=ContentKind.EPISODE),seriesRef=series.ref,seriesIdentity="work:vladimir",episodeNumber=EpisodeNumber(1,1))
        val d=PageIdentity.attach(html("vladimir"),CatalogParser(Sites.get("filmcibaba")).detail(html("vladimir"),episode),episode)
        assertEquals(ContentKind.EPISODE,ContentIdentity.facts(d.card).kind)
    }
    @Test fun otherSeriesProvidersKeepTheirCapturedCompleteArchives()=runBlocking {
        val rows=org.json.JSONArray(checkNotNull(javaClass.getResource("/catalog/index.json")).readText()).values().filterIsInstance<org.json.JSONObject>()
        for((site,count,seasons) in listOf(Triple("hdfilmcehennemi",33,7),Triple("fixfullfilmizle",18,3))){
            val row=rows.first{it.string("site")==site&&it.string("scenario").contains("episodes")}
            val body=checkNotNull(javaClass.getResource("/catalog/"+row.string("file"))).readText()
            val c=Card(ContentRef(site,row.string("page"),ContentKind.SERIES),"Fixture")
            val repository=CatalogRepository(object:WebTransport{override suspend fun request(request:WebRequest)=WebResponse(request.url,body)})
            val d=repository.detail(c);assertEquals(ContentKind.SERIES,d.card.ref.kind)
            val episodes=repository.episodes(d.card);assertEquals(count,episodes.size)
            assertEquals(seasons,episodes.map{it.number.season}.distinct().size)
            assertEquals(EpisodeNumber(1,1),episodes.first().number)
        }
    }
}
