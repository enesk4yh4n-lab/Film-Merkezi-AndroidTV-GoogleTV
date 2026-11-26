package app.filmmerkezi.tv.tv

import app.filmmerkezi.tv.core.*
import org.junit.Assert.*
import org.junit.Test

class ClassicCatalogPolicyTest {
    private fun item(n:Int,year:Int?,rating:Double?,language:String="") : UnifiedItem{
        val c=Card(ContentRef("filmcibaba","https://filmizle.ink/classic-$n/"),"Film $n",year=year,imdb=rating,language=language)
        return UnifiedItem(ContentIdentity.key(c),c,listOf(c))
    }
    @Test fun sortingRetainsUnknownMetadataWithoutInventingValues(){
        val items=listOf(item(1,null,null),item(2,2026,6.0),item(3,2009,8.5))
        assertEquals(items,ClassicCatalogPolicy.apply(items,ClassicFilter(),ClassicSort.DEFAULT))
        assertEquals(listOf(2026,2009,null),ClassicCatalogPolicy.apply(items,ClassicFilter(),ClassicSort.NEWEST).map{it.card.year})
        assertEquals(listOf(8.5,6.0,null),ClassicCatalogPolicy.apply(items,ClassicFilter(),ClassicSort.RATING).map{it.card.imdb})
    }
    @Test fun filterMatchesAnAlternativeSiteWithoutDroppingItsSharedIdentity(){
        val movie=item(1,2026,7.8,"Türkçe Dublaj")
        val alternate=movie.card.copy(ref=ContentRef("lovefilmizle","https://lovefilmizle.net/classic/"),language="Türkçe Altyazılı")
        val unified=movie.copy(alternatives=listOf(movie.card,alternate))
        assertEquals(listOf(unified),ClassicCatalogPolicy.apply(listOf(unified),ClassicFilter(2026,7.0,"altyazi"),ClassicSort.DEFAULT))
        assertTrue(ClassicCatalogPolicy.apply(listOf(unified),ClassicFilter(2020,8.0),ClassicSort.DEFAULT).isEmpty())
    }
    @Test fun recommendationsExcludeWatchedEquivalentFromAnotherSite(){
        val movie=item(1,2026,8.5);val second=item(2,2025,7.5)
        val record=LibraryRecord("watched",movie.card.copy(ref=ContentRef("lovefilmizle","https://lovefilmizle.net/same-film/")),false,20000,100000,0)
        assertEquals(listOf(second),ClassicCatalogPolicy.recommendations(listOf(movie,second),listOf(record),ClassicFilter()))
    }
}
