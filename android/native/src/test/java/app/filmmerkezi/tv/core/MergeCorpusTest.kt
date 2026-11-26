package app.filmmerkezi.tv.core

import org.json.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.zip.GZIPInputStream

class MergeCorpusTest {
    @Test fun recordedSearchesAndPrimaryDetailsConvergeWithoutCrossingReviewedWorks(){
        val corpus=GZIPInputStream(checkNotNull(javaClass.getResourceAsStream("/identity/merge-corpus.json.gz"))).bufferedReader().use{JSONObject(it.readText())}
        val bodies=corpus.getJSONArray("responses").values().filterIsInstance<JSONObject>().associate{it.getString("url") to it.getString("body")}
        val report=JSONArray();var missed=0;var crossed=0
        for(search in corpus.getJSONArray("searches").values().filterIsInstance<JSONObject>()){
            val query=search.getString("query")
            val old=search.getJSONArray("items").values().filterIsInstance<JSONObject>()
            val seeds=old.flatMap{it.getJSONArray("alternatives").values().filterIsInstance<JSONObject>().map(Contracts::readCard)}.distinctBy{it.ref.id}
            val errors=JSONArray()
            val cards=seeds.map{raw->val c=raw.copy(workId="",identityEvidence=null);val body=bodies[c.ref.pageUrl]
                if(body==null||c.ref.kind==ContentKind.COLLECTION)c else try{
                    PageIdentity.attach(body,CatalogParser(Sites.get(c.ref.siteId)).detail(body,c),c).card
                }catch(e:Exception){errors.put(JSONObject().put("url",c.ref.pageUrl).put("error",e.message));c}
            }
            val started=System.nanoTime();val merged=ContentIdentity.merge(cards)
            val checks=JSONArray();val expected=corpus.getJSONArray("expectations").values().filterIsInstance<JSONObject>().filter{it.getString("query")==query}
            for(e in expected){val pages=e.getJSONArray("pages").values().map{it.toString()}.toSet()
                val groups=merged.filter{g->g.alternatives.any{it.ref.pageUrl in pages}}
                if(groups.size!=1)missed++
                val other=expected.filter{it!==e}.flatMap{it.getJSONArray("pages").values().map{v->v.toString()}}.toSet()
                if(groups.any{g->g.alternatives.any{it.ref.pageUrl in other}})crossed++
                checks.put(JSONObject().put("work",e.getString("label")).put("pages",pages.size).put("groups",groups.size)
                    .put("groupTitles",JSONArray(groups.map{it.card.title})))
            }
            report.put(JSONObject().put("query",query).put("before",old.size).put("after",merged.size).put("cards",cards.size)
                .put("mergeMs",(System.nanoTime()-started)/1000000.0).put("checks",checks).put("parseErrors",errors)
                .put("groups",JSONArray(merged.map{JSONObject().put("card",Contracts.card(it.card)).put("alternatives",JSONArray(it.alternatives.map(Contracts::card)))})))
        }
        File("../../reports/merge-research/corpus-after.json").writeText(JSONObject().put("queries",report).put("missedWorks",missed).put("crossedWorks",crossed).toString(2))
        assertEquals("reviewed work remains fragmented; inspect corpus-after.json",0,missed)
        assertEquals("distinct reviewed works merged",0,crossed)
    }
}
