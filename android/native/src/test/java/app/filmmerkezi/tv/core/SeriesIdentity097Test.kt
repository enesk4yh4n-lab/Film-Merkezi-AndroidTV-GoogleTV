package app.filmmerkezi.tv.core

import org.junit.Test
import org.junit.Assert.*

class SeriesIdentity097Test {
    private fun html(name:String)=checkNotNull(javaClass.getResource("/series097/$name.html")).readText()
    private fun house():List<Card>{
        val site=Sites.get("filmcibaba");val parser=CatalogParser(site)
        return parser.list(html("baba-search"),site.base+"/?s=House+of+the+Dragon").cards.mapIndexed{i,c->PageIdentity.attach(html("baba-house-${i+1}"),parser.detail(html("baba-house-${i+1}"),c),c).card}
    }
    @Test fun houseSeasonPagesAreSeriesAndHaveOneWorkIdentity(){
        val cards=house();assertEquals(3,cards.size)
        assertTrue(cards.all{ContentIdentity.facts(it).kind==ContentKind.SERIES})
        assertEquals(listOf(3,2,1),cards.map{it.sourceSeason})
        assertEquals(1,ContentIdentity.merge(cards).size)
        assertEquals("House of the Dragon",ContentIdentity.displayTitle(cards.first()))
    }
    @Test fun seriesArchiveNeverImportsRecommendedDifferentShowEpisode(){
        val site=Sites.get("dizibox");val c=Card(ContentRef(site.id,site.base+"/diziler/lis/",ContentKind.SERIES),"LIS")
        val d=PageIdentity.attach(html("dizibox-empty-series"),CatalogParser(site).detail(html("dizibox-empty-series"),c),c)
        assertEquals(ContentKind.SERIES,ContentIdentity.facts(d.card).kind)
        assertTrue(d.episodes.isEmpty())
    }
    @Test fun validEmptySearchIsEmptyInsteadOfParserFailure(){
        for((site,file)in listOf("fixfullfilmizle" to "fix-empty","filmifullizle" to "filmifull-empty")){
            assertTrue(CatalogParser(Sites.get(site)).list(html(file),Sites.get(site).base+"/?s=House+of+the+Dragon").cards.isEmpty())
        }
    }
    @Test fun delisiHouseEpisodesAreScopedToTheRequestedSeries(){
        val site=Sites.get("hdfilmdelisi");val c=Card(ContentRef(site.id,site.base+"/dizi/house-of-the-dragon-izle",ContentKind.SERIES),"House of the Dragon")
        val d=CatalogParser(site).detail(html("delisi-house"),c)
        assertEquals(listOf(10,8,8),d.episodes.groupBy{it.number.season}.values.map{it.size})
        assertTrue(d.episodes.all{it.card.ref.pageUrl.startsWith(c.ref.pageUrl+"/sezon-")})
        val identity=PageIdentity.attach(html("delisi-house"),d,c).card
        assertTrue(identity.identityEvidence!!.yearVerified)
        assertEquals(2022,identity.year)
    }
    @Test fun delisiIgnoresOtherSeriesFlightAndUsesSeasonFallback(){
        val c=Card(ContentRef("hdfilmdelisi","https://hdfilmdelisi.one/dizi/target/",ContentKind.SERIES),"Target")
        fun entry(slug:String,title:String,n:Int)=org.json.JSONObject().put("slug",slug).put("seriesTitle",title).put("seasons",org.json.JSONArray().put(org.json.JSONObject().put("sezonNo",2).put("episodes",org.json.JSONArray().put(org.json.JSONObject().put("bolumNo",n)))))
        val frame=org.json.JSONArray().put(entry("other","Other",99)).put(entry("target","Target",1))
        val body="<script>self.__next_f.push("+org.json.JSONArray().put(1).put("1:$frame\n")+")</script>"
        val out=CatalogParser(Sites.get("hdfilmdelisi")).episodes(HtmlData.document(body,c.ref.pageUrl),c)
        assertEquals(listOf(EpisodeNumber(2,1)),out.map{it.number})
        assertFalse(out.single().card.ref.pageUrl.contains("//sezon"))
    }
    @Test fun sameNamedRemakesAndConflictingImdbCannotMergeViaSeasonNumber(){
        val a=house().last()
        val url=Sites.get("filmcibaba").base+"/house-remake/"
        val remake=a.copy(ref=a.ref.copy(pageUrl=url),yearHint=1990,identityEvidence=a.identityEvidence!!.copy(pageUrl=url))
        assertEquals(IdentityState.DIFFERENT,ContentIdentity.decide(a,remake).state)
        assertEquals(IdentityState.DIFFERENT,ContentIdentity.decide(a.copy(imdbId="tt1111111"),a.copy(imdbId="tt2222222")).state)
    }
    @Test fun wordpressEpisodePageIsNotCollapsedToSeasonRoot(){
        val site=Sites.get("filmcibaba");val c=Card(ContentRef(site.id,site.base+"/house-of-the-dragon-3-sezon-hbo-max-izle/2/",ContentKind.EPISODE),"House of the Dragon",episodeNumber=EpisodeNumber(3,2),seriesRef=ContentRef(site.id,site.base+"/house-of-the-dragon-3-sezon-hbo-max-izle/",ContentKind.SERIES))
        assertEquals(c.ref.pageUrl,CatalogParser(site).detail(html("baba-house-1"),c).card.ref.pageUrl)
    }
    @Test fun newlyFoundFilmmakinesiSeriesCatalogAndAllSeasons(){
        val site=Sites.get("filmmakinesi");val p=CatalogParser(site)
        val rows=p.list(html("makinesi-series"),site.base+site.seriesPath).cards
        assertEquals(24,rows.size);assertTrue(rows.all{it.ref.kind==ContentKind.SERIES})
        val c=Card(ContentRef(site.id,site.base+"/dizi/house-of-the-dragon-izle-2022-fmwygs/",ContentKind.SERIES),"House of the Dragon")
        val d=p.detail(html("makinesi-house"),c)
        assertEquals(listOf(10,8,8),d.episodes.groupBy{it.number.season}.values.map{it.size})
    }
    @Test fun loveSeriesIndexAndSeasonArticlesAreNotMoviesOrInventedEpisodes(){
        val site=Sites.get("lovefilmizle");val p=CatalogParser(site)
        val page=p.seriesIndex(html("love-series"),site.base+site.seriesPath)
        assertEquals(60,page.cards.size);assertNotNull(page.next);assertTrue(page.cards.all{it.ref.kind==ContentKind.SERIES})
        val c=Card(ContentRef(site.id,site.base+"/tv-dizisi/house-of-the-dragon-izle/",ContentKind.SERIES),"House of the Dragon")
        val d=p.detail(html("love-tv-house"),c)
        assertEquals(1,d.episodePages.size)
        val season=PageIdentity.attach(html("love-house"),p.detail(html("love-house"),Card(ContentRef(site.id,d.episodePages.single(),ContentKind.SERIES),"House of the Dragon 1. Sezon"))).card
        assertEquals(ContentKind.SERIES,ContentIdentity.facts(season).kind)
        assertTrue(p.episodes(HtmlData.document(html("love-house"),season.ref.pageUrl),season).isEmpty())
    }
    @Test fun verifiedDelisiRedirectHostKeepsStableCatalogIdentity(){
        val site=Sites.get("hdfilmdelisi")
        assertEquals(site.base+"/dizi/house-of-the-dragon-izle",site.catalogUrl("https://hdfilmdelisi1.com/dizi/house-of-the-dragon-izle"))
        assertNull(site.catalogUrl("https://unrelated.invalid/dizi/house-of-the-dragon-izle"))
    }
    @Test fun thirdSeasonAloneJoinsTheVerifiedWholeSeriesWithoutUsingSeasonReleaseYear(){
        val third=house().first()
        val c=Card(ContentRef("hdfilmdelisi","https://hdfilmdelisi.one/dizi/house-of-the-dragon-izle",ContentKind.SERIES),"House of the Dragon")
        val full=PageIdentity.attach(html("delisi-house"),CatalogParser(Sites.get(c.ref.siteId)).detail(html("delisi-house"),c),c).card
        val merged=ContentIdentity.merge(listOf(third,full));assertEquals(1,merged.size)
        assertEquals(2022,merged.single().alternatives.first{it.ref.siteId=="filmcibaba"}.year)
    }
}
