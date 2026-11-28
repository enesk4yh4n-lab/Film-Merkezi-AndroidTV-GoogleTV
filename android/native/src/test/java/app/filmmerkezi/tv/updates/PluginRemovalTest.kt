package app.filmmerkezi.tv.updates

import org.json.*
import org.junit.Assert.*
import org.junit.Test

class PluginRemovalTest {
    private val fp="a".repeat(64)
    private fun record(id:String,vararg deps:String):JSONObject {
        val site=id.startsWith("p")
        return JSONObject().put("repo","$fp/example").put("manifest",JSONObject().put("schemaVersion",1).put("platform","android").put("runtime",UpdateProtocol.RUNTIME)
            .put("sdkMin",1).put("sdkMax",2).put("id",id).put("version","1.0.0").put("type",if(site)"site"else"resolver").put("name",id)
            .put("minAppVersionCode",38).put("minSdk",23).put("entryClass","app.filmmerkezi.addon.Entry")
            .put("site",if(site)JSONObject().put("id",id).put("name",id).put("base","https://fixture.example")else null)
            .put("dependencies",JSONArray(deps.map{JSONObject().put("publisher",fp).put("id",it).put("minVersion","1.0.0").put("maxVersion","2.0.0")}))
            .put("origins",JSONArray().put("https://fixture.example")).put("artifact",JSONObject().put("path","packages/$id/1.0.0/plugin.jar").put("sha256","0".repeat(64)).put("size",1)).toString())
    }
    @Test fun exclusiveResolverCanBeRemovedWithProviderWithoutChangingInput(){
        val set=JSONObject().put("$fp/p001",record("p001","r001")).put("$fp/r001",record("r001"));val before=set.toString()
        val plan=PluginRemovals.plan(set,"$fp/p001");assertEquals(listOf("$fp/r001"),plan.unused.map{it.key})
        assertEquals(0,PluginRemovals.apply(set,plan,true).length());assertEquals(1,PluginRemovals.apply(set,plan,false).length());assertEquals(before,set.toString())
    }
    @Test fun sharedResolverIsNeverRemovedWithOneProvider(){
        val set=JSONObject().put("$fp/p001",record("p001","r001")).put("$fp/p002",record("p002","r001")).put("$fp/r001",record("r001"))
        val plan=PluginRemovals.plan(set,"$fp/p001");assertTrue(plan.unused.isEmpty());assertEquals(1,plan.shared.size)
        assertTrue(PluginRemovals.apply(set,plan,true).has("$fp/r001"))
    }
    @Test fun transitiveDependenciesAreRemovedTogetherOnlyWhenUnused(){
        val set=JSONObject().put("$fp/p001",record("p001","r001")).put("$fp/r001",record("r001","r002")).put("$fp/r002",record("r002"))
        assertEquals(2,PluginRemovals.plan(set,"$fp/p001").unused.size)
        set.put("$fp/p002",record("p002","r002"));val plan=PluginRemovals.plan(set,"$fp/p001")
        assertEquals(listOf("r001"),plan.unused.map{it.module.id});assertEquals(listOf("r002"),plan.shared.map{it.module.id})
    }
    @Test fun orphanResolverStillProtectsItsOwnDependency(){
        val set=JSONObject().put("$fp/p001",record("p001","r001")).put("$fp/r001",record("r001"))
            .put("$fp/r009",record("r009","r001"))
        assertTrue(PluginRemovals.plan(set,"$fp/p001").unused.isEmpty())
    }
    @Test fun resolverWithAnyUsersCannotBeRemovedDirectly(){
        val set=JSONObject().put("$fp/p001",record("p001","r001")).put("$fp/r001",record("r001"))
        try{PluginRemovals.plan(set,"$fp/r001");fail()}catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("p001"))}
    }
    @Test fun changedSetRejectsStaleRemovalPlan(){
        val set=JSONObject().put("$fp/p001",record("p001"));val plan=PluginRemovals.plan(set,"$fp/p001")
        set.put("$fp/r002",record("r002"));try{PluginRemovals.apply(set,plan,true);fail()}catch(_:IllegalArgumentException){}
    }
}
