package app.filmmerkezi.tv.core

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlinx.coroutines.runBlocking
import java.io.File

@RunWith(Parameterized::class)
class SourceFixtureTest(private val id:String,private val row:JSONObject) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun cases():List<Array<Any>> =
            JSONArray(checkNotNull(SourceFixtureTest::class.java.getResource("/sources/index.json")).readText())
                .values().filterIsInstance<JSONObject>().map{arrayOf(it.getString("id"),it)}
    }
    @Test fun selectedOptionsResolveAgainstCapturedMetadata()= runBlocking {
        val calls=mutableListOf<String>()
        val replies=row.getJSONArray("responses").values().filterIsInstance<JSONObject>()
        val site=Sites.get(row.string("site"));val page=row.string("page")
        fun current(url:String)=if(site.id=="filmizlebabam")site.catalogUrl(url)?:url else url
        val transport=object:WebTransport {
            override suspend fun request(request:WebRequest):WebResponse {
                calls.add(request.method+" "+request.url)
                val reply=replies.find{current(it.string("url"))==current(request.url)&&it.string("method")==request.method}
                    ?:throw DataFailure(FailureKind.NETWORK,"FIXTURE EKSİK: "+request.url,"fixture")
                if(reply.optInt("code") !in 200..299)throw DataFailure(FailureKind.HTTP,"HTTP "+reply.optInt("code"),"fixture",reply.optInt("code"))
                return WebResponse(current(reply.string("finalUrl")),checkNotNull(javaClass.getResource("/sources/"+reply.string("file"))).readText(),reply.optInt("code"))
            }
        }
        val content=ContentRef(site.id,page,if(page.contains("-bolum"))ContentKind.EPISODE else ContentKind.MOVIE)
        val results=JSONArray();val errors=JSONArray();val good=mutableListOf<ResolvedSource>()
        var options=emptyList<SourceOption>()
        try {
            val body=transport.request(WebRequest(page)).body
            options=SourceMenus.parse(site,body,content,page)
            assertEquals("Source menu must not perform network I/O",1,calls.size)
            val resolver=SourceResolver(transport)
            for(option in options) {
                try {val s=resolver.resolve(option);good.add(s);results.put(Contracts.source(s))}
                catch(e:Exception){errors.put(JSONObject().put("label",option.label).put("error",e.message))}
            }
        }catch(e:Exception){errors.put(JSONObject().put("error",e.message))}
        val expected=row.getJSONArray("referenceSources").values().filterIsInstance<JSONObject>()
        val deliberatelyUnsupported = if (id == "filmifull-spider") setOf("filmifullizle-source0", "filmifullizle-source2") else emptySet()
        for (id in deliberatelyUnsupported) {
            val source = options.single { it.id == id }
            assertEquals(OptionState.UNSUPPORTED, source.state)
            assertTrue(source.reason.isNotBlank())
            assertTrue(good.none { it.id == id })
        }
        // The native player now retains all real quality variants. The historical
        // Tizen default can be one of those variants rather than the highest URL.
        fun includes(source:ResolvedSource,url:String)=source.url==url||source.variants.any{it.url==url}
        val missing=expected.filter{e->e.string("id") !in deliberatelyUnsupported && good.none{includes(it,e.string("url"))}}
        // Some original resolvers expose a HLS endpoint whose response redirects to another
        // playlist. This is valid only if that URL was actually traversed in the same capture.
        val unresolved=missing.filter{e->good.none{s->replies.any{it.string("url")==e.string("url")&&includes(s,it.string("finalUrl"))}}}
        val issue=row.optString("fixtureIssue").takeUnless{it=="null" || it.isBlank()}
        val status=when{issue!=null->"FIXTURE_INCONSISTENT";expected.isNotEmpty() && unresolved.isEmpty()->"PASS";expected.isEmpty()&&good.isEmpty()->"CAPTURE_REJECTION";else->"FAIL"}
        val report=JSONObject().put("site",site.id).put("id",id).put("status",status).put("scope",row.string("scope"))
            .put("options",JSONArray(options.map(Contracts::option))).put("sources",results).put("errors",errors)
            .put("missingReferenceSources",JSONArray(unresolved)).put("requests",JSONArray(calls)).put("fixtureIssue",issue?:JSONObject.NULL)
        File("build/reports/native-sources").mkdirs();File("build/reports/native-sources/$id.json").writeText(report.toString(2))
        Assume.assumeTrue(issue?:"",issue==null)
        assertNotEquals(report.toString(2).take(7000),"FAIL",status)
    }
}
