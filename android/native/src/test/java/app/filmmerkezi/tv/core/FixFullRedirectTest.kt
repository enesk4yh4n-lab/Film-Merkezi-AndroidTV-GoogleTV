package app.filmmerkezi.tv.core

import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Test

class FixFullRedirectTest {
    @Test fun recognizedVevocloudRedirectReportsActualServerFailure()=runBlocking {
        val rows=JSONArray(checkNotNull(javaClass.getResource("/sources/index.json")).readText()).values().filterIsInstance<JSONObject>()
        val row=rows.first{it.string("id")=="fixfull-poseidon"}
        val replies=row.getJSONArray("responses").values().filterIsInstance<JSONObject>()
        val api=replies.first{r->r.string("url").contains("myplayersvideo.xyz")&&r.string("method")=="POST"&&
            checkNotNull(javaClass.getResource("/sources/"+r.string("file"))).readText().contains("vevocloud.com")}
        val embed=api.string("url").substringBefore('?');var probed=false
        val resolver=SourceResolver(object:WebTransport{
            override suspend fun request(request:WebRequest):WebResponse {
                if(request.url.startsWith("https://vevocloud.com/play/index/")){
                    probed=true;throw DataFailure(FailureKind.HTTP,"HTTP 521","http",521)
                }
                val response=replies.first{it.string("url")==request.url&&it.string("method")==request.method}
                return WebResponse(response.string("finalUrl"),checkNotNull(javaClass.getResource("/sources/"+response.string("file"))).readText())
            }
        })
        try{resolver.resolve(SourceOption("vevo",ContentRef("fixfullfilmizle",row.string("page")),"Kaynak 2",embed));fail("Server failure expected")}
        catch(e:DataFailure){assertTrue(e.message,probed);assertEquals(FailureKind.HTTP,e.kind);assertEquals(521,e.httpCode)}
        Unit
    }
}
