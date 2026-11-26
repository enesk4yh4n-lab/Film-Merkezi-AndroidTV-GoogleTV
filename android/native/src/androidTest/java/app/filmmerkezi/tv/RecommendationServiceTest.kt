package app.filmmerkezi.tv

import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.last
import org.junit.Test
import org.junit.Assert.*

class RecommendationServiceTest {
    @Test fun realNativeServiceVerifiesDetailsExcludesWatchedAndSeenAndNeverResolves()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val name="recommendation-works-test.db";context.deleteDatabase(name)
        val calls=mutableListOf<WebRequest>()
        val grid=(1..6).joinToString(""){n->"<div class='film_kutusu'><a href='/film/film-$n/'><img src='https://sinemacc.com/$n.jpg'/><span class='title'><span class='text'>Film $n</span></span><span class='years'>2024</span><span class='imdb'>8.0</span></a></div>"}
        val options="<select name='rcat'><option value='9'>Aksiyon</option></select><select name='rimdb'><option value='7'>7+</option></select>"
        val transport=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
            calls.add(request);delay(5)
            val n=Regex("/film/film-(\\d+)/").find(request.url)?.groupValues?.get(1)
            val body=if(n!=null)"<h1 class='baslik'>Film $n</h1><span itemprop='genre'>Aksiyon</span><script type='application/ld+json'>{\"@type\":\"Movie\",\"url\":\"${request.url}\",\"name\":\"Film $n\",\"dateCreated\":\"2024\",\"aggregateRating\":{\"ratingValue\":8.0}}</script>"else if(request.method=="POST")grid else options
            return WebResponse(request.url,body)
        }}
        fun card(n:Int)=Card(ContentRef("sinemacc","https://sinemacc.com/film/film-$n/"),"Film $n",year=2024,imdb=8.0)
        try{WorkStore(context,name).use{works->
            val seen=works.item(card(2)).identity
            val result=RecommendationService(CatalogRepository(transport,works),works).generate(CatalogQuery(genre="Aksiyon",minRating=7.0),"sinemacc",listOf(LibraryRecord("old",card(1),false,10000,100000,0)),listOf(seen),0).last()
            assertTrue(result.finished);assertEquals(listOf("Film 3","Film 4","Film 5"),result.items.map{it.card.title});assertTrue(result.checked<=12)
            assertTrue(calls.any{it.method=="POST"&&it.fields["action"]=="ajax_archive"});assertTrue(calls.none{it.url.contains("m3u8")||it.url.contains("hotstream")})
        }}finally{context.deleteDatabase(name)}
    }
}
