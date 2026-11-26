package app.filmmerkezi.tv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.last
import org.json.*
import org.junit.Assume
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Manual catalog-only observation; no resolving or playback of unrelated sites. */
@RunWith(AndroidJUnit4::class)
class CatalogLiveTest {
    @Test fun combinedMovieCatalog()=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("catalogLive")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val network=ProviderNetwork(context)
        val capture=File(context.getExternalFilesDir(null),"catalog-live-capture").apply{mkdirs()}
        val transport=object:WebTransport{
            override suspend fun request(request:WebRequest):WebResponse {
                val r=network.request(request)
                File(capture,TextKey.hash(request.url)+".json").writeText(JSONObject().put("url",request.url).put("finalUrl",r.url).put("body",r.body).toString())
                return r
            }
        }
        val page=CatalogRepository(transport).observeBrowse().last()
        File(context.getExternalFilesDir(null),"catalog-live.json").writeText(Contracts.unified(page).put("fixture",false).put("api",android.os.Build.VERSION.SDK_INT)
            .put("scope","Movie browse only; zero counts on series-only sites are expected").toString(2))
        assertEquals(Sites.all.size,page.completedSites);assertTrue("All sites failed; inspect catalog-live.json",page.items.isNotEmpty())
        Unit
    }
}
