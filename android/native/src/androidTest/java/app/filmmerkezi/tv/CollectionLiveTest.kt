package app.filmmerkezi.tv

import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.io.File

/** One affected collection endpoint, not the full site/player matrix. */
class CollectionLiveTest {
    @Test fun liveTopFiftyDetailUsesNativeHttpAndParser():Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val network=ProviderNetwork(context);val report=JSONObject().put("live",true)
        val url="https://filmizle.ink/haber/imdb-top-50-filmi-sinema-dunyasinin-zirvesine-yolculuk/"
        try{
            val detail=CatalogRepository(network).detail(Card(ContentRef("filmcibaba",url,ContentKind.COLLECTION),"IMDB Top 50"))
            report.put("url",url).put("title",detail.card.title).put("members",detail.collectionItems.size)
            assertEquals(50,detail.collectionItems.size);assertEquals(1994,detail.collectionItems.first().year)
            report.put("passed",true)
        }catch(e:Throwable){report.put("passed",false).put("error",e.toString());throw e}
        finally{File(context.getExternalFilesDir(null),"catalog-094-live.json").writeText(report.toString(2));network.client.connectionPool.evictAll();network.client.dispatcher.executorService.shutdown()}
    }
}
