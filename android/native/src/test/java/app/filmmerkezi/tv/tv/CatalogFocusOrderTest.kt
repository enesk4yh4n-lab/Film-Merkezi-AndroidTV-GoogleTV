package app.filmmerkezi.tv.tv
import app.filmmerkezi.tv.core.*
import org.junit.Test
import org.junit.Assert.*
class CatalogFocusOrderTest {
    private fun item(id:String,url:String=id)=Card(ContentRef("filmcibaba","https://filmizle.ink/$url/"),id,workId=id).let{UnifiedItem(id,it,listOf(it))}
    @Test fun newSiteRepliesAppendWithoutMovingExistingCards(){
        val a=item("a");val b=item("b");val c=item("c")
        val updated=b.copy(card=b.card.copy(imdb=8.0))
        val result=stableContentOrder(listOf(a,b),listOf(c,updated,a))
        assertEquals(listOf("a","b","c"),result.map{it.identity});assertEquals(8.0,result[1].card.imdb)
    }
    @Test fun mergedWorkRetainsItsFirstPlaceAndNewEvidence(){
        val a=item("a");val b=item("b");val c=item("c")
        val merged=b.copy(identity="merged",alternatives=listOf(a.card,b.card))
        val result=stableContentOrder(listOf(a,c,b),listOf(merged,c))
        assertEquals(listOf("merged","c"),result.map{it.identity});assertEquals(2,result.first().alternatives.size)
    }
}
