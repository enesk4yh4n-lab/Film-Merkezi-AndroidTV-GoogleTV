package app.filmmerkezi.tv.core

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogPosterSizeTest {
    private val page="https://example.test/films/"

    @Test fun choosesSmallAdvertisedPosterAndKeepsItsQuery(){
        val node=Jsoup.parseBodyFragment("""<img src="/p-original.jpg"
            data-srcset="/p-160.jpg 160w, /p-360.jpg?token=a 360w, /p-1200.jpg 1200w">""").selectFirst("img")
        assertEquals("https://example.test/p-360.jpg?token=a",CatalogPosters.image(node,page))
    }

    @Test fun ignoresUnusableVariantsAndFallsBackToExistingImage(){
        val node=Jsoup.parseBodyFragment("""<img src="/original.jpg" srcset="data:image/gif,placeholder 80w, javascript:bad 320w">""").selectFirst("img")
        assertEquals("https://example.test/original.jpg",CatalogPosters.image(node,page))
    }

    @Test fun keepsCatalogImageWhenItsAdvertisedWidthIsAlreadySmall(){
        val markup=listOf(
            """<img src="/small.jpg" srcset="/small.jpg 239w, /detail.jpg 399w">""",
            """<img src="/small.jpg" srcset="/small.jpg 160w, /detail.jpg 200w">"""
        )
        for(html in markup){
            val node=Jsoup.parseBodyFragment(html).selectFirst("img")
            assertEquals("https://example.test/small.jpg",CatalogPosters.image(node,page))
        }
    }
}
