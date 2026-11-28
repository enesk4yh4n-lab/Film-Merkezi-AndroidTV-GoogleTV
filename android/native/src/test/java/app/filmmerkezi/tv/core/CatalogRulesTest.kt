package app.filmmerkezi.tv.core

import app.filmmerkezi.addon.pilot.PilotEntry
import app.filmmerkezi.tv.pluginapi.PluginHost
import org.json.*
import org.junit.*
import org.junit.Assert.*

class CatalogRulesTest {
    private val film=Sites.defaults.first{it.id=="filmcibaba"}
    private val now=SiteSpec("p003","NOW","https://www.nowtv.com.tr","/arama","/dizi-arsivi","a","h2")
    private val puhu=SiteSpec("p002","Puhu","https://puhutv.com","/","/","a","h2")
    private val star=SiteSpec("p005","Star","https://www.startv.com.tr","/dizi","/dizi","a","h2")
    private var old=emptyList<SiteSpec>();private var known=emptyList<SiteSpec>()
    @Before fun setup(){old=Sites.all.toList();known=Sites.known.toList();Sites.installed(listOf(film,now,puhu,star))}
    @After fun restore(){Sites.installed(old);Sites.remember(known)}
    private fun movie(title:String="The Example",year:Int?=2010)=Card(ContentRef(film.id,film.base+"/example/"),title,year=year)
    private fun html(schema:JSONObject)= "<h1>The Example</h1><script type='application/ld+json'>$schema</script>"
    private class Host(val body:(JSONObject)->String):PluginHost {
        val requests=mutableListOf<JSONObject>()
        override fun request(payload:String):String {val r=JSONObject(payload);requests.add(r);return JSONObject().put("url",r.getString("url")).put("code",200).put("body",body(r)).put("headers",JSONObject()).toString()}
        override fun resolver(id:String,operation:String,payload:String):String=error("No resolver expected")
        override fun storage(operation:String,key:String,value:String):String=error("No storage expected")
        override fun cancelled()=false
    }
    private fun call(site:SiteSpec,op:String,data:JSONObject,host:Host)=JSONObject(PilotEntry().call(op,data.put("site",PluginWire.site(site)).toString(),host))
    @Test fun ownMetadataFillsIdentityGenresCastAndKeepsRatingSeparate(){
        val c=movie();val schema=JSONObject().put("@type","Movie").put("url",c.ref.pageUrl).put("name",c.title).put("alternateName","Example Original").put("releaseDate","2010-04-01")
            .put("sameAs","https://www.imdb.com/title/tt1234567/").put("genre",JSONArray(listOf("Drama","Action"))).put("actor",JSONArray().put(JSONObject().put("name","Actor"))).put("aggregateRating",JSONObject().put("ratingValue",8.9))
        val d=ContentMetadata.enrich(Detail(c),html(schema));assertEquals("tt1234567",d.card.imdbId);assertEquals("Example Original",d.card.originalTitle)
        assertEquals(listOf("Drama","Action"),d.card.genres);assertEquals(listOf("Actor"),d.cast);assertNull(d.card.imdb);assertTrue(d.card.identityEvidence!!.yearVerified)
    }
    @Test fun recommendedWorkAndEpisodePublicationNeverBecomeSeriesIdentity(){
        val c=movie(year=null).copy(ref=movie().ref.copy(kind=ContentKind.SERIES))
        val body=html(JSONObject().put("@type","TVEpisode").put("name",c.title).put("url",c.ref.pageUrl).put("datePublished","2026-01-01").put("sameAs","https://www.imdb.com/title/tt9999999/"))+
            "<aside><a class='imdb_link' href='https://www.imdb.com/title/tt7777777/'>Recommended</a></aside>"
        val d=ContentMetadata.enrich(Detail(c),body);assertEquals(ContentKind.SERIES,d.card.ref.kind);assertNull(d.card.year);assertEquals("",d.card.imdbId)
    }
    @Test fun conflictingPrimaryIdentifiersRemainUncertain(){
        val c=movie();val s=JSONObject().put("@type","Movie").put("name",c.title).put("url",c.ref.pageUrl).put("sameAs",JSONArray(listOf("https://imdb.com/title/tt1111111/","https://imdb.com/title/tt2222222/")))
        val a=ContentMetadata.enrich(Detail(c),html(s)).card
        assertTrue(a.identityEvidence!!.imdbConflict);assertEquals(IdentityState.UNCERTAIN,ContentIdentity.decide(a,c.copy(ref=c.ref.copy(siteId=now.id))).state)
    }
    @Test fun legacyMovieSchemaCannotTurnVerifiedSeasonIntoMovieOrBorrowSeasonYear(){
        val c=movie(year=null).copy(ref=movie().ref.copy(kind=ContentKind.SERIES),sourceSeason=2)
        val schema=JSONObject().put("@type","Movie").put("name",c.title).put("url",c.ref.pageUrl).put("releaseDate","2026-01-01")
        val d=ContentMetadata.enrich(Detail(c),html(schema));assertEquals(ContentKind.SERIES,d.card.ref.kind);assertNull(d.card.year)
    }
    @Test fun episodesProjectToOneParentWithoutBorrowingEpisodeYearOrImdb(){
        val parent=ContentRef(film.id,film.base+"/series/",ContentKind.SERIES)
        val children=(1..3).map{n->Card(ContentRef(film.id,film.base+"/episode-$n/",ContentKind.EPISODE),"Series · $n. Bölüm",year=2026,imdbId="tt1234567",episodeNumber=EpisodeNumber(1,n),seriesRef=parent)}
        val out=CatalogContent.catalog(CatalogPage(children));assertEquals(1,out.cards.size);assertEquals(parent,out.cards.single().ref);assertEquals("Series",out.cards.single().title)
        assertNull(out.cards.single().year);assertEquals("",out.cards.single().imdbId);assertNull(out.cards.single().episodeNumber)
    }
    @Test fun orphanEpisodeNeverAppearsAsIndependentMovie(){
        val c=movie().copy(ref=movie().ref.copy(kind=ContentKind.EPISODE),episodeNumber=EpisodeNumber(1,1))
        assertTrue(CatalogContent.catalog(CatalogPage(listOf(c))).cards.isEmpty())
    }
    @Test fun collectionsStayInSearchButNotMovieBrowseOrFilters(){
        val collection=Card(ContentRef(film.id,film.base+"/haber/marvel/",ContentKind.COLLECTION),"Marvel Filmleri")
        val page=CatalogPage(listOf(movie(),collection))
        assertEquals(2,CatalogContent.catalog(page).cards.size);assertEquals(listOf(movie()),CatalogContent.forKind(page,ContentKind.MOVIE).cards)
    }
    @Test fun primaryYearCategoryBeatsUploadYearAndIsNotGenre(){
        val c=movie("The Gorge",null)
        val body="<h1>The Gorge</h1><div class='p-v12-meta-info'><span class='meta-item'>2026</span><span class='meta-item'><a rel='category tag' href='/Kategori/tur/2025-filmleri-izle/'>2025 Filmleri izle</a></span></div>"
        val raw=Detail(c,genres=listOf("2025 Filmleri izle","Romantik Filmler"))
        val d=ContentMetadata.enrich(PageIdentity.attach(body,raw,c),body)
        assertEquals(2025,d.card.year);assertEquals(listOf("Romantik Filmler"),d.card.genres);assertEquals("romantik",GenreKey.key(d.card.genres.single()))
    }
    @Test fun explicitSameSeriesParentGroupsSeasonPagesButCannotBridgeConflictingImdb(){
        val parent=ContentRef(film.id,film.base+"/series/",ContentKind.SERIES)
        val a=Card(parent.copy(pageUrl=film.base+"/series-season-1/"),"Series 1. Sezon",seriesRef=parent,sourceSeason=1)
        val b=a.copy(ref=a.ref.copy(pageUrl=film.base+"/series-season-2/"),sourceSeason=2,title="Series 2. Sezon")
        assertEquals(IdentityState.SAME,ContentIdentity.decide(a,b).state)
        assertEquals(1,ContentIdentity.merge(listOf(a,b.copy(title="Different season display name"))).size)
        assertEquals(IdentityState.DIFFERENT,ContentIdentity.decide(a.copy(imdbId="tt1111111"),b.copy(imdbId="tt2222222")).state)
    }
    @Test fun marvelRecommendationAndSeriesArticlesAreCollectionsInSearch(){
        for(title in listOf("Marvel Filmleri","Öneri Filmler","Seri Filmler")){
            val body="<a class='v100-card' href='${film.base}/haber/liste/'><h2 class='v100-title'>$title</h2></a>"
            assertEquals(ContentKind.COLLECTION,CatalogParser(film).list(body,film.base+"/").cards.single().ref.kind)
        }
        assertFalse(CatalogContent.collectionLink("The Collection",film.base+"/the-collection/"))
    }
    @Test fun incorrectlyTypedCollectionOpensMembersWithoutPlayerOptionsOrInventedYear(){
        val c=Card(ContentRef(film.id,film.base+"/haber/marvel/"),"Marvel Filmleri")
        val body="<h1>Marvel Filmleri</h1><article class='entry-content'><h2>1. Iron Man (2008)</h2><h2>2. Avengers</h2><aside><h2>3. Advert (2026)</h2></aside></article>"
        val d=CatalogParser(film).detail(body,c);assertEquals(ContentKind.COLLECTION,d.card.ref.kind);assertEquals(2,d.collectionItems.size);assertTrue(d.options.isEmpty())
        assertNull(d.collectionItems[1].knownYear);assertFalse(d.collectionItems[1].matches(movie("Avengers")))
        val wire=PluginWire.detail(d);assertTrue(wire.getJSONArray("collectionItems").getJSONObject(1).isNull("year"));assertNull(PluginWire.detail(wire).collectionItems[1].knownYear)
    }
    @Test fun structuredCollectionPreservesMemberOrder(){
        val c=movie().copy(title="Collection");val list=JSONObject().put("@type","ItemList").put("url",c.ref.pageUrl).put("itemListElement",JSONArray((1..2).map{i->JSONObject().put("position",3-i).put("item",JSONObject().put("@type","Movie").put("name","Film $i").put("releaseDate","200$i-01-01"))}))
        val d=CatalogParser(film).detail(html(list),c);assertEquals(ContentKind.COLLECTION,d.card.ref.kind);assertEquals(listOf(2,1),d.collectionItems.map{it.ordinal})
    }
    @Test fun projectionCannotReplaceRicherParentCardOrMergeOrphan(){
        val parent=Card(ContentRef(film.id,film.base+"/series/",ContentKind.SERIES),"Series",year=2020,imdbId="tt1111111")
        val ep=parent.copy(ref=parent.ref.copy(pageUrl=film.base+"/ep/",kind=ContentKind.EPISODE),title="Series · 2×3",seriesRef=parent.ref,episodeNumber=EpisodeNumber(2,3))
        val result=CatalogContent.catalog(CatalogPage(listOf(ep,parent))).cards.single();assertEquals(2020,result.year);assertEquals("tt1111111",result.imdbId);assertEquals("Series",result.title)
    }
    @Test fun unrelatedRecommendationItemListCannotReclassifyOrdinaryFilm(){
        val c=movie();val list=JSONObject().put("@type","ItemList").put("itemListElement",JSONArray((1..2).map{i->JSONObject().put("@type","Movie").put("name","Other $i")}))
        assertFalse(CollectionParser.recognizes(html(list),c))
    }
    @Test fun allSourcePagesBeyondSixteenAreInventoried(){
        val c=movie();val body="<div class='p-v12-ep-list'>"+(1..24).joinToString(""){n->"<a class='post-page-numbers' href='${c.ref.pageUrl}${if(n==1)""else"$n/"}'>$n</a>"}+"</div>"
        val options=SourceMenus.parse(film,body,c.ref);assertEquals(24,options.size);assertTrue(options.any{it.url.endsWith("/24/")})
    }
    @Test fun otherEpisodesAreNeverFallbackSourcesOfSelectedEpisode(){
        val ref=ContentRef(film.id,film.base+"/series/2/",ContentKind.EPISODE)
        val body="<iframe src='https://hotstream.club/embed/current'></iframe><div class='p-v12-ep-list'><a class='post-page-numbers' href='${film.base}/series/'>1. Bölüm</a><a class='post-page-numbers' href='${film.base}/series/3/'>3. Bölüm</a></div>"
        val options=SourceMenus.parse(film,body,ref);assertEquals(1,options.size);assertTrue(options.single().url.contains("/current"));assertEquals(ref,options.single().content)
    }
    @Test fun nowOwnInformationPageAddsStoryGenreAndActorNotCharacter(){
        val host=Host{r->when{
            r.getString("url").endsWith("/bolumler")->"<h1>Example - BÖLÜMLER</h1><select id='video-filter-changer'><option value='1'>1</option></select><div class='ajax-load-more-video' data-program-id='10' data-type='2' data-count='1'></div><a href='/Example/bilgi'>Bilgi</a>"
            r.getString("url").endsWith("/bilgi")->"<div class='about-content-container'><div class='about-meta'><div class='about-meta-header'>HİKAYE</div><p>Gerçek konu.</p></div><div class='about-meta'><p><strong>TÜR:</strong> DRAM<br><strong>YAPIM ŞİRKETİ:</strong> STUDIO<br><strong>YÖNETMEN:</strong> DIRECTOR</p></div></div><div class='act-name'>Character</div><div class='act-desc'>(Actor Name)</div>"
            else->"{\"code\":200,\"count\":1,\"data\":\"<a href='/Example/bolum/1'>Bölüm</a>\"}"}}
        val card=Card(ContentRef(now.id,now.base+"/Example/izle",ContentKind.SERIES),"Example")
        val d=PluginWire.detail(call(now,"detail",JSONObject().put("card",Contracts.card(card)),host))
        assertEquals("Gerçek konu.",d.description);assertEquals(listOf("DRAM"),d.card.genres);assertEquals(listOf("Actor Name"),d.cast);assertNull(d.card.year);assertNull(d.card.imdb)
    }
    @Test fun starCastUsesArtisteNamesInsteadOfCharacterNames(){
        val record=JSONObject().put("resourceType","TvSeries").put("_id","123456789012345678901234").put("name","Example").put("url","/dizi/example")
            .put("cast",JSONArray().put(JSONObject().put("name","Character Name").put("artiste",JSONObject().put("firstName","Actor").put("lastName","Surname"))))
        val stream="1:$record\n";val body="<script>self.__next_f.push("+JSONArray().put(1).put(stream)+")</script>"
        val host=Host{q->if(q.getString("url").contains("/api/"))"{\"items\":[]}"else body}
        val card=Card(ContentRef(star.id,star.base+"/dizi/example",ContentKind.SERIES),"Example")
        val result=PluginWire.detail(call(star,"detail",JSONObject().put("card",Contracts.card(card)),host));assertEquals(listOf("Actor Surname"),result.cast)
    }
}
