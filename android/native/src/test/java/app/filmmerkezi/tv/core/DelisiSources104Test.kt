package app.filmmerkezi.tv.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DelisiSources104Test {
    private val site=Sites.get("hdfilmdelisi")
    private val content=ContentRef(site.id,site.base+"/film/saplanti-obsession")
    private fun wire(value:Any)= "<script>self.__next_f.push("+JSONArray().put(1).put("1:"+value+"\n")+");</script>"
    private fun player(url:String="https://pl.vixolity.com/fixture")=JSONObject().put("isCopyrighted",false)
        .put("sources",JSONArray().put(JSONObject().put("title","Delicore(VIP)").put("url",url)))
    private fun guard(p:JSONObject,public:Boolean=true)=JSONObject().put("accessLevel",if(public)"public"else "private")
        .put("requireAuthForWatch",!public).put("children",p)
    @Test fun capturedCurrentPlayerSourcesAreListedWithoutRunningPageScripts() {
        val body=checkNotNull(javaClass.getResource("/delisi104/player.html")).readText()
        val reference=JSONObject(checkNotNull(javaClass.getResource("/delisi104/reference.json")).readText())
        val result=SourceMenus.parse(site,body,content)
        assertEquals(1,result.size);assertEquals(reference.getString("url"),result.single().url)
        assertEquals("Delicore(VIP)",result.single().label);assertEquals(OptionState.AVAILABLE,result.single().state)
    }
    @Test fun legacyVideoUrlStillWorks() {
        val p=JSONObject().put("videoUrl","https://ha.vixolity.com/vs/legacy").put("isCopyrighted",false)
        assertEquals(p.getString("videoUrl"),SourceMenus.parse(site,wire(guard(p)),content).single().url)
    }
    @Test fun privateOrCopyrightedPlayerDoesNotFallThroughToPublicFrames() {
        for(g in listOf(guard(player(),false),guard(player().put("isCopyrighted",true)))) {
            val out=SourceMenus.parse(site,wire(g)+"<iframe src='https://pl.vixolity.com/unrelated'></iframe>",content)
            assertEquals(1,out.size);assertEquals(OptionState.ACCESS_REQUIRED,out.single().state)
        }
    }
    @Test fun onlyActivePlayerNotRecommendationsSuppliesSources() {
        val graph=JSONArray().put(guard(player().put("sources",JSONArray())))
            .put(player("https://pl.vixolity.com/recommendation"))
        assertTrue(SourceMenus.parse(site,wire(graph),content).isEmpty())
    }
    @Test fun unknownHostsAndDuplicateSourcesAreNotAdded() {
        val p=player();p.getJSONArray("sources").put(p.getJSONArray("sources").getJSONObject(0))
            .put(JSONObject().put("url","https://ads.invalid/embed").put("title","Ad"))
        assertEquals(1,SourceMenus.parse(site,wire(guard(p)),content).size)
    }
}
