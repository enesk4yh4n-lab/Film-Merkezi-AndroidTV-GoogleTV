package app.filmmerkezi.tv.updates

import org.json.*
import org.junit.Assert.*
import org.junit.Test

class PluginInventoryTest {
    private val fp="a".repeat(64);private val repo="$fp/example"
    private fun record(id:String,site:String?=null,version:String="1.0.0",dependency:String?=null)=JSONObject().put("repo",repo).put("enabled",true).put("manifest",JSONObject()
        .put("schemaVersion",1).put("platform","android").put("runtime",UpdateProtocol.RUNTIME).put("sdkMin",1).put("sdkMax",2)
        .put("id",id).put("version",version).put("type",if(site==null)"resolver"else"site").put("name",id).put("minAppVersionCode",38).put("minSdk",23).put("entryClass","app.filmmerkezi.addon.Entry")
        .put("site",site?.let{JSONObject().put("id",it).put("name",it).put("base","https://fixture.example")})
        .put("origins",JSONArray().put("https://fixture.example"))
        .put("dependencies",JSONArray().apply{dependency?.let{put(JSONObject().put("publisher",fp).put("id",it).put("minVersion","1.0.0").put("maxVersion","3.0.0"))}})
        .put("artifact",JSONObject().put("path","packages/$id/$version/plugin.jar").put("sha256",if(version=="1.0.0")"0".repeat(64)else"1".repeat(64)).put("size",1)).toString())
    private fun entry(id:String,version:String="1.0.0",min:Int=38)=UpdateProtocol.Entry(id,version,"site",id,"packages/$id/$version/manifest.json","a".repeat(64),min)
    private fun inventory(active:JSONObject,pending:JSONObject?=null,entries:List<UpdateProtocol.Entry> = listOf(entry("p002")))=PluginInventory(JSONObject().put("packages",active).apply{pending?.let{put("pending",it)}},mapOf(repo to UpdateProtocol.Index("example","Example","",1,entries)),38)
    @Test fun providersContainDependenciesInsteadOfSeparateMovieSiteRows(){
        val set=JSONObject().put("$fp/p002",record("p002","p002",dependency="r002")).put("$fp/r002",record("r002")).put("$fp/r099",record("r099"))
        val i=inventory(set);assertEquals(1,i.providers.size);assertEquals(listOf("r002"),i.providers.single().resolvers.map{it.module.id});assertEquals(listOf("r099"),i.unusedResolvers.map{it.module.id})
    }
    @Test fun renamedPreparedProviderIsOneSiteAndKeepsActiveVersion(){
        val active=JSONObject().put("$fp/old",record("old","stable"));val pending=JSONObject().put("$fp/p002",record("p002","stable","2.0.0"))
        val g=inventory(active,pending).providers.single();assertEquals("old",g.active!!.module.id);assertEquals("p002",g.prepared!!.module.id);assertEquals(PluginState.READY,g.state)
    }
    @Test fun preparedRemovalIsNotAlreadyRemoved(){
        val g=inventory(JSONObject().put("$fp/p002",record("p002","p002",dependency="r002")).put("$fp/r002",record("r002")),JSONObject()).providers.single()
        assertEquals(PluginState.REMOVAL_READY,g.state);assertNotNull(g.active);assertNull(g.prepared)
        assertEquals(listOf("r002"),g.resolvers.map{it.module.id});assertTrue(g.missingResolvers.isEmpty())
    }
    @Test fun updateCompatibilityAndWithdrawalAreDifferentStates(){
        val active=JSONObject().put("$fp/p002",record("p002","p002"))
        assertEquals(PluginState.ACTIVE,inventory(active).providers.single().state)
        assertEquals(PluginState.UPDATE_AVAILABLE,inventory(active,entries=listOf(entry("p002","2.0.0"))).providers.single().state)
        assertEquals(PluginState.NEWER_APP_REQUIRED,inventory(active,entries=listOf(entry("p002","2.0.0",99))).providers.single().state)
        assertEquals(PluginState.WITHDRAWN,inventory(active,entries=emptyList()).providers.single().state)
    }
    @Test fun disabledDependencyDoesNotAppearAsWorkingProvider(){
        val active=JSONObject().put("$fp/p002",record("p002","p002",dependency="r002")).put("$fp/r002",record("r002").put("enabled",false))
        assertEquals(PluginState.DISABLED,inventory(active).providers.single().state)
    }
}
