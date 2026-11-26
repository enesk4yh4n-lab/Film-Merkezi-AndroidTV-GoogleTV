package app.filmmerkezi.tv.core

import org.junit.Test
import org.junit.Assert.*

class CatalogFiltersTest {
    private fun options(site:SiteSpec):FilterOptions{
        val names=listOf("genres","genres[]","years","lang","lang[]","imdb","tur[]","yil","filter_category","filter_year","filter_lang","tur","dil","category","rcat","rdate","rdil","rimdb")
        val html=names.joinToString(""){name->"<select name='$name'><option value='g'>Aksiyon</option><option value='2024'>2024</option><option value='dub'>Türkçe Dublaj</option><option value='7-10'>7+</option></select>"}+
            "<a href='/kategori/aksiyon/'>Aksiyon</a>"+listOf("genres" to "g","years" to "2024","lang" to "dub","imdb" to "7-10").joinToString(""){(k,v)->"<button data-dropdown-key='$k' data-dropdown-value='$v'>"+when(k){"genres"->"Aksiyon";"years"->"2024";"lang"->"Türkçe Dublaj";else->"7+"}+"</button>"}
        return SiteFilters.options(site,html)
    }
    @Test fun allConfiguredSitesDeclareRealAndPartialCoverage(){
        val expected=mapOf("hdfilmcehennemi" to 4,"dizibox" to 3,"filmcibaba" to 1,"filmifullizle" to 4,"filmizlebabam" to 0,"filmizlehell" to 2,"filmmakinesi" to 0,"fixfullfilmizle" to 1,"fullhdfilmizlesene" to 0,"hdfilmdelisi" to 1,"lovefilmizle" to 3,"sinemacc" to 4,"turkcefilmler" to 1,"ddizi" to 0,"dizipal" to 0,"diziboxum" to 0)
        assertEquals(Sites.all.map{it.id}.toSet(),expected.keys)
        for(site in Sites.all){val q=CatalogQuery(genre="Aksiyon",year=2024,language="dublaj",minRating=7.0);val p=SiteFilters.plan(site,q,options(site));assertEquals(site.id,expected[site.id],p.coverage.remote.size);assertEquals(4-p.coverage.remote.size,p.coverage.local.size)}
    }
    @Test fun hdfAndSinemaccUseTheirActualPostContracts(){
        val q=CatalogQuery(genre="Aksiyon",year=2024,language="dublaj",minRating=7.0)
        val hdf=Sites.get("hdfilmcehennemi");val a=SiteFilters.plan(hdf,q,options(hdf)).request!!
        assertEquals("POST",a.method);assertEquals("[\"g\"]",a.fields["genres"]);assertEquals("2024",a.fields["years"]);assertEquals("discover",a.headers["X-Mofycore-Action"])
        val cc=Sites.get("sinemacc");val b=SiteFilters.plan(cc,q,options(cc)).request!!
        assertEquals("POST",b.method);assertEquals("ajax_archive",b.fields["action"]);assertEquals("g",b.fields["rcat"]);assertEquals("7",b.fields["rimdb"])
    }
    @Test fun queryValuesAreEncodedAndKeepPerSiteNames(){
        val site=Sites.get("lovefilmizle");val request=SiteFilters.plan(site,CatalogQuery(genre="Aksiyon",year=2024),options(site)).request!!
        assertTrue(request.url.contains("/arama-robotu/?"));assertTrue(request.url.contains("filter_category=g"));assertTrue(request.url.contains("filter_year=2024"))
        val d=Sites.get("dizibox");assertTrue(SiteFilters.plan(d,CatalogQuery(ContentKind.SERIES,genre="Aksiyon"),options(d)).request!!.url.contains("tur%5B%5D=g"))
    }
    @Test fun unknownMetadataNeverPassesLocalFilters(){val q=CatalogQuery(year=2024,minRating=7.0);val c=Card(ContentRef("filmcibaba","https://filmizle.ink/test/"),"Test");assertFalse(q.accepts(c));assertTrue(q.accepts(c,setOf("year","rating")));assertFalse(q.accepts(c.copy(year=2023,imdb=6.0),setOf("year","rating")))}
    @Test fun cursorsFromDifferentCriteriaAreRejected(){val site=Sites.get("sinemacc");val a=CatalogQuery(year=2024);try{SiteFilters.plan(site,a,options(site),PageCursor(site.id,site.base+"/film-arsivi/",2,mapOf("_query" to CatalogQuery(year=2023).signature)));fail()}catch(e:DataFailure){assertEquals(FailureKind.PARSE,e.kind)}}
    @Test fun aliasesMapGenresWithoutInventingRemoteValues(){assertEquals(GenreKey.key("Science Fiction"),GenreKey.key("Bilim Kurgu Filmleri"));assertEquals("aksiyon",GenreKey.key("Aksiyon (125)"))}
    @Test fun seriesFiltersNeverRequestAnotherSitesFilmArchive(){
        for(id in listOf("hdfilmdelisi","lovefilmizle","filmmakinesi","filmcibaba","fixfullfilmizle")){
            val site=Sites.get(id);val plan=SiteFilters.plan(site,CatalogQuery(ContentKind.SERIES,genre="Aksiyon",year=2024,order=CatalogOrder.RATING),options(site))
            assertNull(id,plan.request);assertEquals(setOf("genre","year"),plan.coverage.local);assertTrue(plan.coverage.remote.isEmpty())
        }
    }
}
