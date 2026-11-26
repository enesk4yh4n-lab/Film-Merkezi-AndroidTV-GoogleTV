package app.filmmerkezi.tv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CatalogHeaderProbeTest {
    @Test fun onlyPreviously403CatalogOrigins()=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("catalogHeaderProbe")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val network=ProviderNetwork(context)
        val useDefault=InstrumentationRegistry.getArguments().getString("defaultPolicy")=="true"
        val rows=JSONArray()
        for(id in listOf("hdfilmcehennemi","fullhdfilmizlesene","filmmakinesi")){
            val s=Sites.get(id);val row=JSONObject().put("site",id);rows.put(row)
            try{
                val response=network.request(WebRequest(s.base+s.list,headers=(if(useDefault)emptyMap()else mapOf("User-Agent" to SiteHttpPolicy.BROWSER_USER_AGENT))+
                    if(id=="hdfilmcehennemi")mapOf("Accept" to "application/json, text/html","X-Requested-With" to "fetch")else emptyMap()))
                row.put("code",response.code).put("cards",CatalogParser(s).list(response.body,response.url).cards.size)
            }catch(e:Exception){row.put("error",e.message).put("code",(e as? DataFailure)?.httpCode?:JSONObject.NULL)}
        }
        File(context.getExternalFilesDir(null),if(useDefault)"catalog-header-default.json"else"catalog-header-probe.json").writeText(JSONObject().put("fixture",false).put("defaultPolicy",useDefault)
            .put("scope","Only the three catalog roots that returned 403; explicit observed browser UA").put("rows",rows).toString(2))
        Unit
    }
}
