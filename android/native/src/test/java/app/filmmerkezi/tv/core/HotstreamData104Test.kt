package app.filmmerkezi.tv.core

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HotstreamData104Test {
    private val html=checkNotNull(javaClass.getResource("/hotstream104/config.html")).readText()
    private val reference=JSONObject(checkNotNull(javaClass.getResource("/hotstream104/reference.json")).readText())
    private val page=reference.getString("page")
    @Test fun actualInertConfigMatchesIndependentDecryption(){assertEquals(reference.getString("media"),HotstreamData.media(html,page))}
    @Test fun legacyLiteralCallStillWorksAfterAFunctionDeclaration(){
        val data=JSONObject(HtmlData.document(html,page).getElementById("be-player-config")!!.attr("data-config"))
        val legacy="function bePlayer(a,b){}; bePlayer("+JSONObject.quote(data.getString("videoId"))+", "+JSONObject.quote(data.getString("settings"))+");"
        assertEquals(reference.getString("media"),HotstreamData.media(legacy,page))
    }
    @Test fun ambiguousConfigAndExecutableArgumentsAreRejected(){
        for(body in listOf(html+html,"bePlayer(getPassword(), getSettings());","<script>bePlayer(config.videoId,config.settings)</script>")){
            try{HotstreamData.media(body,page);fail("Untrusted config accepted")}catch(e:DataFailure){assertEquals(FailureKind.PARSE,e.kind)}
        }
    }
    @Test fun decryptedForeignOriginIsRejected(){
        val d=HtmlData.document(html,page);val n=d.getElementById("be-player-config")!!
        val config=JSONObject(n.attr("data-config")).put("settings",reference.getString("foreignSettings"));n.attr("data-config",config.toString())
        try{HotstreamData.media(d.outerHtml(),page);fail("Foreign media accepted")}catch(e:DataFailure){assertEquals(FailureKind.PARSE,e.kind)}
    }
    @Test fun nativeResolverKeepsHotstreamHeadersAndTransportContract()=runBlocking {
        val transport=object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{assertEquals(page,request.url);return WebResponse(page,html)}}
        val source=SourceResolver(transport).resolve(SourceOption("hotstream",ContentRef("filmcibaba","https://filmizle.ink/the-gorge-2025-izle/"),"Hotstream",page))
        assertEquals(reference.getString("media"),source.url);assertEquals(page,source.referer)
        assertEquals(StreamType.HLS,source.type);assertTrue(source.playbackRequirements.containsAll(listOf("accept-any","referer")))
    }
}
