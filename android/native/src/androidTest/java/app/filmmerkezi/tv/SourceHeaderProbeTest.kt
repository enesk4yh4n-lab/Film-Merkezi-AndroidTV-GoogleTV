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
class SourceHeaderProbeTest {
    @Test fun onlyFailedEmbedOrigins()=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("sourceHeaderProbe")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val network=ProviderNetwork(context)
        val previous=JSONObject(File(context.getExternalFilesDir(null),"native-live.json").readText())
        val rows=JSONArray()
        for(target in previous.getJSONArray("targets").values().filterIsInstance<JSONObject>())
            for(option in target.getJSONArray("options").values().filterIsInstance<JSONObject>().filter{it.string("status")=="RESOLVE_FAIL"}){
                val url=option.string("optionUrl");val row=JSONObject().put("host",java.net.URI(url).host);rows.put(row)
                try{val response=network.request(WebRequest(url,target.string("url"),headers=mapOf("User-Agent" to SiteHttpPolicy.BROWSER_USER_AGENT)))
                    row.put("code",response.code).put("bytes",response.body.length)
                    val dir=File(context.getExternalFilesDir(null),"source-header-probe-html").apply{mkdirs()}
                    File(dir,TextKey.hash(url)+".html").writeText(response.body)
                }catch(e:Exception){row.put("error",e.message).put("code",(e as? DataFailure)?.httpCode?:JSONObject.NULL)}
            }
        File(context.getExternalFilesDir(null),"source-header-probe.json").writeText(JSONObject().put("fixture",false).put("rows",rows).toString(2))
        Unit
    }
}
