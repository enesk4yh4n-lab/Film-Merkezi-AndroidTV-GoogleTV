package app.filmmerkezi.tv.core

import org.junit.Assert.*
import org.junit.Test

class CatalogParityTest {
    @Test fun accessPauseCountsRealFailuresAndIgnoresLateResponses(){
        var time=0L
        val state=CatalogAvailability{time}
        val denied=SiteFailure("a",FailureKind.HTTP,"",403)
        state.result(state.request("a")!!,SiteFailure("a",FailureKind.PARSE,""))
        state.result(state.request("a")!!,denied)
        assertNotNull(state.request("a"))
        val late=state.request("a")!!
        state.result(state.request("a")!!,denied)
        assertNull(state.request("a"))
        assertNotNull(state.request("b"))
        assertEquals(1_800_000L,state.remaining("a"))
        state.result(late,null)
        assertNull(state.request("a"))
        time=1_800_001L
        assertNotNull(state.request("a"))
        state.reset("a")
        state.result(state.request("a")!!,denied)
        assertEquals(0L,state.remaining("a"))
    }

    @Test fun explicitClassificationAndGenresOnly(){
        val movie=Card(ContentRef("first","https://first.example/movie/"),"Title")
        assertTrue(AdultContent.blocked(AdultContent.declaredDescription(Detail(movie,"Tür: Erotik film.")).card))
        assertFalse(AdultContent.blocked(AdultContent.declaredDescription(Detail(movie,"Yönetmen bir erotik film çeker. 18+.")).card))
        assertTrue(CatalogSection.ANIMATION.accepts(movie.copy(genres=listOf("Animasyon"))))
        val series=movie.copy(ref=movie.ref.copy(kind=ContentKind.SERIES))
        assertFalse(CatalogSection.DRAMA_SHORT.accepts(series.copy(genres=listOf("Dram"))))
        assertTrue(CatalogSection.DRAMA_SHORT.accepts(series.copy(genres=listOf("Kore Dizileri"))))
    }

    @Test fun strictDisplayKeepsPersistentIdentityAndOnlyGroupsStrongEvidence(){
        val first=Card(ContentRef("first","https://first.example/movie/"),"Örnek",year=2025,imdbId="tt2234567")
        val same=first.copy(ref=ContentRef("second","https://second.example/movie/"))
        val noId=first.copy(ref=ContentRef("third","https://third.example/movie/"),imdbId="")
        val group=UnifiedItem("work:example",first,listOf(first,same,noId))
        val shown=CatalogPresentation.project(listOf(group),CatalogRequestOptions(sites=listOf("first","second","third"),mode=MatchingMode.STRICT))
        assertEquals(listOf(2,1),shown.map{it.alternatives.size})
        assertEquals(setOf("work:example"),shown.map{it.identity}.toSet())
        assertEquals(2,shown.map{it.displayKey}.toSet().size)
    }
}
