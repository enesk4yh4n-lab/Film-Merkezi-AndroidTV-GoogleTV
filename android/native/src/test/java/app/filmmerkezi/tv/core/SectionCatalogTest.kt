package app.filmmerkezi.tv.core

import java.io.File
import java.util.Collections
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject

class SectionCatalogTest {
    @Test fun acceptsOnlyTheSameCategoryAndSequentialPageShapes(){
        val site=Sites.get("filmcibaba")
        val root=CatalogSection.ANIMATION.routes(site.id).first()
        assertEquals(1,sectionPageNumber(site,root,root.path))
        assertEquals(2,sectionPageNumber(site,root,"${root.path}page/2/"))
        assertEquals(3,sectionPageNumber(site,root,"${root.path}?sayfa=3"))
        assertNull(sectionPageNumber(site,root,"/page/2/"))
        assertNull(sectionPageNumber(site,root,"${root.path}?sayfa=2&s=film"))
        assertNull(sectionPageNumber(site,root,"https://other.example${root.path}"))
    }

    @Test fun declaredRouteOverridesLegacyAndWorksForAnIndependentSite()=runBlocking{
        val installed=Sites.all
        val remote=PluginGateway.remote
        val site=SiteSpec("independent-sections","Example","https://independent.example","/","/","main a","h2")
        val original=checkNotNull(installed.firstOrNull{it.id=="filmcibaba"})
        val declaration=CatalogSection.fromManifest(JSONObject("""{"catalogSections":{"DRAMA_SHORT":[{"path":"/shorts/","kinds":["MOVIE"],"pageParameter":"page"}]}}"""))
        val calls=mutableListOf<String>()
        val transport=object:WebTransport{
            override suspend fun request(request:WebRequest):WebResponse{
                calls.add(request.url)
                return WebResponse(request.url,"""<main><a href="/shorts/?page=2">Next</a></main>""")
            }
        }
        try{
            Sites.installed(listOf(site,original),mapOf(site.id to CatalogPluginMetadata("p-test@1",declaration),original.id to CatalogPluginMetadata("p004@next",emptyMap())))
            assertTrue(CatalogSection.ANIMATION.routes(original.id).isEmpty())
            assertTrue(CatalogSection.DRAMA_SHORT.routes(original.id).isEmpty())
            PluginGateway.remote={id,type,operation,_ ->
                assertEquals(site.id,id);assertEquals("site",type);assertEquals("parseList",operation)
                val cards=listOf(Card(ContentRef(id,"${site.base}/short/",ContentKind.MOVIE),"Short"),
                    Card(ContentRef(id,"${site.base}/series/",ContentKind.SERIES),"Series"))
                PluginWire.page(CatalogPage(cards))
            }
            val repository=CatalogRepository(transport)
            val first=repository.observeSection(CatalogSection.DRAMA_SHORT).last()
            assertEquals(listOf("Short"),first.items.map{it.card.title})
            repository.observeSection(CatalogSection.DRAMA_SHORT,first.next).last()
            assertEquals(listOf("${site.base}/shorts/","${site.base}/shorts/?page=2"),calls)
        }finally{PluginGateway.remote=remote;Sites.installed(installed)}
    }

    @Test fun malformedCategoryDeclarationsAreRejected(){
        for(path in listOf("//other.example/","/../catalog/","/catalog/?page=1","/%2e%2e/catalog/","/")){
            val site=JSONObject().put("catalogSections",JSONObject().put("ANIMATION",org.json.JSONArray().put(JSONObject().put("path",path))))
            assertThrows(IllegalArgumentException::class.java){CatalogSection.fromManifest(site)}
        }
        assertNull(CatalogSection.fromManifest(JSONObject()))
    }

    @Test fun unsupportedOrDisabledSitesProduceNoRequests()=runBlocking{
        val noNetwork=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse=error(request.url)}
        val onlyOther=CatalogRepository(noNetwork,requestOptions={CatalogRequestOptions(sites=listOf("p002","diziboxum"))})
        assertEquals(0,onlyOther.observeSection(CatalogSection.ANIMATION).last().requestedSites)
        assertEquals(0,onlyOther.observeSection(CatalogSection.DRAMA_SHORT,siteId="p002").last().requestedSites)
    }

    @Test fun providerAdaptersParseTheFourRecordedCategoriesAndKeepTheirPages()=runBlocking{
        val root=System.getenv("FM_CATEGORY_FIXTURES")?.let(::File)
        assumeTrue("Recorded provider responses unavailable",root?.isDirectory==true)
        val fixtures=linkedMapOf(
            "/Kategori/tur/animasyon-filmleri/" to "p004-animation",
            "/Kategori/anime-izle/" to "p004-anime",
            "/asya-dizileri/" to "p007-drama",
            "/Kategori/k-drama-reels-asya-dizileri/" to "p004-short"
        )
        val calls=Collections.synchronizedList(mutableListOf<String>())
        val transport=object:WebTransport{
            override suspend fun request(request:WebRequest):WebResponse{
                val url=request.url.toHttpUrl();calls.add(request.url)
                val path=url.encodedPath.replace(Regex("page/2/$"),"")
                val name=fixtures.getValue(path)
                val page=if(path!=url.encodedPath||url.queryParameter("sayfa")=="2")"-2" else ""
                val body=File(root!!,"$name$page.html").readText()
                return WebResponse(request.url,body)
            }
        }
        val repo=CatalogRepository(transport,requestOptions={CatalogRequestOptions(sites=listOf("filmcibaba","diziboxum","p002"))})
        val animation=repo.observeSection(CatalogSection.ANIMATION).last()
        assertTrue(animation.failures.toString(),animation.failures.isEmpty())
        assertEquals(1,animation.requestedSites)
        assertTrue(animation.items.isNotEmpty())
        assertEquals(fixtures.keys.take(2),calls.map{it.toHttpUrl().encodedPath})
        assertEquals(2,animation.next.getValue("filmcibaba").fields.size)
        val animationNext=repo.observeSection(CatalogSection.ANIMATION,animation.next).last()
        assertTrue(animationNext.items.any{it.card.ref.id !in animation.items.map{old->old.card.ref.id}.toSet()})
        assertEquals(2,calls.drop(2).count{it.endsWith("/page/2/")})
        val drama=repo.observeSection(CatalogSection.DRAMA_SHORT).last()
        assertTrue(drama.failures.toString(),drama.failures.isEmpty())
        assertEquals(2,drama.requestedSites)
        val asian=drama.items.filter{it.card.ref.siteId=="diziboxum"}
        val shorts=drama.items.filter{it.card.ref.siteId=="filmcibaba"}
        assertTrue(asian.isNotEmpty()&&asian.all{it.card.ref.kind==ContentKind.SERIES})
        assertTrue(shorts.isNotEmpty()&&shorts.all{it.card.ref.kind in setOf(ContentKind.MOVIE,ContentKind.SERIES)})
        assertTrue(drama.next.getValue("diziboxum").url.endsWith("?sayfa=2"))
        val dramaNext=repo.observeSection(CatalogSection.DRAMA_SHORT,drama.next).last()
        assertEquals(setOf("filmcibaba","diziboxum"),dramaNext.siteCounts.keys)
        val firstDrama=drama.items.map{it.card.ref.id}.toSet()
        assertTrue(dramaNext.items.any{it.card.ref.id !in firstDrama})
        assertEquals(8,calls.size)
        println("Recorded cards: animation/anime=${animation.items.size}, Asian series=${asian.size}, shorts=${shorts.size}")
    }
}
