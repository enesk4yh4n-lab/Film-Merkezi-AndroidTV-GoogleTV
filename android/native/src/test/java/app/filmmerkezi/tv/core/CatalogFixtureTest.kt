package app.filmmerkezi.tv.core

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Captured inputs and their real Chromium/Tizen-parser reference, not live connectivity. */
@RunWith(Parameterized::class)
class CatalogFixtureTest(private val name:String,private val row:JSONObject) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun cases():List<Array<Any>> =
            JSONArray(checkNotNull(CatalogFixtureTest::class.java.getResource("/catalog/index.json")).readText())
                .values().filterIsInstance<JSONObject>().map{arrayOf(it.string("site")+"/"+it.string("scenario"),it)}
    }
    @Test fun matchesCapturedReference() {
        assertFalse("FIXTURE EKSİK",row.has("missing"))
        val body=checkNotNull(javaClass.getResource("/catalog/"+row.getString("file"))).readText()
        val site=Sites.get(row.getString("site"));val parser=CatalogParser(site)
        fun sameUrl(a:String,b:String)=(site.catalogUrl(a)?:a)==(site.catalogUrl(b)?:b)
        val page=row.getString("page");val ref=row.getJSONObject("reference")
        val diffs=mutableListOf<String>();var count=0
        try {
            val originals=row.optJSONObject("original")
            val kind=runCatching{ContentKind.valueOf(originals?.string("type").orEmpty().uppercase())}.getOrDefault(site.kind(page))
            val detail=row.string("method")=="detail"
            val actual:Any=if(detail)parser.detail(body,Card(ContentRef(site.id,page,kind),originals?.string("title")?:"Fixture"))
                else if(row.string("method")=="seriesIndex")parser.seriesIndex(body,page)else parser.list(body,page)
            if(ref.has("error"))diffs.add("Expected rejection: "+ref.string("error"))
            else {
                val expected=ref.getJSONObject("value")
                fun compareCard(e:JSONObject,a:Card,prefix:String) {
                    fun cmp(key:String,value:Any?){
                        val wanted=e.opt(key).takeUnless{it==JSONObject.NULL}
                        fun norm(v:Any?):Any?=when {v is Number->v.toDouble();key in setOf("pageUrl","poster")&&v is String->site.catalogUrl(v)?:v;key=="language" && v is String->v.replace("Altyazı","Altyazılı").replace("Altyazılılı","Altyazılı");else->v}
                        if(norm(wanted)!=norm(value))diffs.add("$prefix.$key: expected=$wanted actual=$value")
                    }
                    cmp("pageUrl",a.ref.pageUrl);cmp("title",a.title);cmp("poster",a.poster)
                    cmp("year",a.year);cmp("imdb",a.imdb);cmp("language",a.language)
                }
                when(actual) {
                    is CatalogPage->{
                        val wanted=expected.optJSONArray("cards")?.values()?.filterIsInstance<JSONObject>()?:emptyList()
                        count=actual.cards.size
                        if(wanted.size!=actual.cards.size)diffs.add("cards.size: expected=${wanted.size} actual=${actual.cards.size}")
                        wanted.zip(actual.cards).forEachIndexed{i,(e,a)->compareCard(e,a,"cards[$i]")}
                        expected.optJSONObject("next")?.let{n->
                            if(actual.next==null)diffs.add("next missing")
                            else if(n.string("url").isNotBlank() && actual.next.url!=(site.catalogUrl(n.string("url"))?:n.string("url")))diffs.add("next.url differs: "+actual.next.url)
                        }
                    }
                    is Detail->{
                        count=actual.episodes.size
                        if(expected.has("cards")) {
                            val wanted=expected.getJSONArray("cards").values().filterIsInstance<JSONObject>()
                            if(wanted.size!=actual.episodes.size)diffs.add("episodes.size: expected=${wanted.size} actual=${actual.episodes.size}")
                            wanted.forEach{e->if(actual.episodes.none{sameUrl(it.card.ref.pageUrl,e.string("pageUrl"))})diffs.add("episode missing: "+e.string("pageUrl"))}
                        } else {
                            compareCard(expected,actual.card,"detail")
                            if(expected.has("description") && TextKey.clean(expected.string("description"))!=TextKey.clean(actual.description))diffs.add("description differs")
                            val wanted=expected.optJSONArray("episodes")?.values()?.filterIsInstance<JSONObject>()?:emptyList()
                            if(wanted.size!=actual.episodes.size)diffs.add("episodes.size: expected=${wanted.size} actual=${actual.episodes.size}")
                            wanted.zip(actual.episodes).forEachIndexed{i,(e,_)->
                                val a=actual.episodes.find{sameUrl(it.card.ref.pageUrl,e.string("pageUrl"))}
                                if(a==null){diffs.add("episode missing: "+e.string("pageUrl"));return@forEachIndexed}
                                if(!sameUrl(e.string("pageUrl"),a.card.ref.pageUrl))diffs.add("episodes[$i].pageUrl differs")
                                val n=e.optJSONObject("episode")
                                if(n!=null&&(n.optInt("season")!=a.number.season||n.optInt("number")!=a.number.number))diffs.add("episodes[$i].number differs")
                            }
                        }
                    }
                }
            }
        }catch(e:Exception){if(!ref.has("error") || e !is DataFailure)diffs.add(e.javaClass.simpleName+": "+e.message)}
        val result=JSONObject().put("site",site.id).put("scenario",row.string("scenario")).put("count",count).put("status",if(diffs.isEmpty())if(ref.has("error"))"BLOCKED_FIXTURE_REJECTED" else "PASS" else "FAIL").put("differences",JSONArray(diffs))
        File("build/reports/native-catalog").mkdirs()
        File("build/reports/native-catalog/"+site.id+"-"+row.string("scenario")+".json").writeText(result.toString(2))
        assertTrue(diffs.take(15).joinToString("\n"),diffs.isEmpty())
    }
}
