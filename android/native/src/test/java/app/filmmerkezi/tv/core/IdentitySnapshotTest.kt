package app.filmmerkezi.tv.core

import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class IdentitySnapshotTest {
    private fun card(o:JSONObject):Card {
        val e=o.optJSONObject("identityEvidence")
        return Card(ContentRef(o.getString("providerId"),o.getString("pageUrl"),when(o.optString("type")){"series"->ContentKind.SERIES;"episode"->ContentKind.EPISODE;else->ContentKind.MOVIE}),o.optString("title"),
            year=if(o.optInt("year")>0)o.getInt("year")else null,originalTitle=o.optString("originalTitle"),imdbId=o.optString("imdbId"),
            aliases=o.optJSONArray("titleAliases")?.let{a->(0 until a.length()).map{a.getString(it)}}?:emptyList(),identityEvidence=e?.let{IdentityEvidence(it.optString("pageUrl"),when(it.optString("type")){"series"->ContentKind.SERIES;"movie"->ContentKind.MOVIE;else->null},it.optInt("year").takeIf{v->v>0},it.optString("imdbId"),it.optString("originalTitle"),it.optJSONArray("aliases")?.let{a->(0 until a.length()).map{a.getString(it)}}?:emptyList(),it.optString("title"),it.optBoolean("typeVerified"),it.optBoolean("yearVerified"),it.optBoolean("detailChecked"),it.optBoolean("imdbConflict"))})
    }
    @Test fun referenceSnapshot(){
        val input=JSONObject(checkNotNull(javaClass.getResource("/identity/reference.json")).readText())
        val reviewed=JSONObject(checkNotNull(javaClass.getResource("/identity/reviewed-extra-pairs.json")).readText())
        val result=org.json.JSONArray();var missing=0;var wrong=0
        val cases=input.getJSONArray("cases")
        for(i in 0 until cases.length()){
            val row=cases.getJSONObject(i);val raw=row.getJSONArray("cards");val cards=(0 until raw.length()).map{card(raw.getJSONObject(it))}
            val groups=ContentIdentity.merge(cards).map{it.alternatives.map{c->c.ref.pageUrl}.toSet()}
            val expected=row.getJSONArray("groups").let{a->(0 until a.length()).map{n->a.getJSONArray(n).let{b->(0 until b.length()).map{b.getString(it)}.toSet()}}}
            fun pairs(rows:List<Set<String>>)=rows.flatMap{g->val l=g.sorted();l.flatMapIndexed{n,a->l.drop(n+1).map{b->"$a\n$b"}}}.toSet()
            val want=pairs(expected);val got=pairs(groups);missing+=(want-got).size;wrong+=(got-want).size
            val approved=reviewed.optJSONArray(row.getString("query"))?.let{a->(0 until a.length()).map{a.getString(it)}.toSet()}.orEmpty()
            assertEquals("Unreviewed change for "+row.getString("query"),approved,got-want)
            result.put(JSONObject().put("query",row.getString("query")).put("expectedGroups",expected.size).put("actualGroups",groups.size).put("missingPairs",(want-got).size).put("extraPairs",(got-want).size).put("extra",org.json.JSONArray((got-want).toList())).put("missing",org.json.JSONArray((want-got).toList())))
        }
        val output=JSONObject().put("queries",result).put("missingPairs",missing).put("extraPairs",wrong)
        val file:File=File("../../reports/merge-research/tizen-reference-after.json")
        file.parentFile?.mkdirs();file.writeText(output.toString(2))
        assertEquals(6,cases.length())
        assertEquals("missed Tizen matches",0,missing)
        assertEquals("Only reviewed first-film/numbered-subtitle links may differ",94,wrong)
    }
}
