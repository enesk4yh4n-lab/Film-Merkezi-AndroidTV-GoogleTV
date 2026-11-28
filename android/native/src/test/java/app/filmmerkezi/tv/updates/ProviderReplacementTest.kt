package app.filmmerkezi.tv.updates

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProviderReplacementTest {
    private val publisher="a".repeat(64)
    private fun module(id:String,site:String?="stable-site",type:String="site")=UpdateProtocol.Module(id,"1.0.0",type,id,38,23,"app.filmmerkezi.addon.Entry",
        site?.let{JSONObject().put("id",it).put("name","Fixture").put("base","https://fixture.example")},emptyList(),listOf("https://fixture.example"),"packages/$id/1.0.0/plugin.jar","0".repeat(64),1)
    private fun stored(module:UpdateProtocol.Module):JSONObject {
        val raw=JSONObject().put("schemaVersion",1).put("platform","android").put("runtime",UpdateProtocol.RUNTIME).put("sdkMin",1).put("sdkMax",2)
            .put("id",module.id).put("version",module.version).put("type",module.type).put("name",module.name)
            .put("minAppVersionCode",38).put("minSdk",23).put("entryClass",module.entry)
            .put("site",module.site).put("origins",org.json.JSONArray(module.origins))
            .put("artifact",JSONObject().put("path",module.artifact).put("sha256",module.hash).put("size",module.size))
        return JSONObject().put("manifest",raw.toString())
    }
    @Test fun renamedPackageReplacesOnlySamePublisherAndSameSite(){
        val old="$publisher/old-provider";val next="$publisher/p004"
        val set=JSONObject().put(old,stored(module("old-provider"))).put("$publisher/other",stored(module("other","other-site")))
        assertEquals(listOf(old),PackageSets.replacedProviders(set,mapOf(next to module("p004")))[next])
    }
    @Test fun ordinaryUpgradeAndResolverDoNotReplaceOtherPackages(){
        val key="$publisher/p004";val set=JSONObject().put(key,stored(module("p004")))
        assertTrue(PackageSets.replacedProviders(set,mapOf(key to module("p004")))[key]!!.isEmpty())
        assertTrue(PackageSets.replacedProviders(set,mapOf("$publisher/r004" to module("r004",null,"resolver"))).isEmpty())
    }
    @Test fun anotherPublisherCannotSilentlyReplaceInstalledProvider(){
        val set=JSONObject().put("${"b".repeat(64)}/old-provider",stored(module("old-provider")))
        try{PackageSets.replacedProviders(set,mapOf("$publisher/p004" to module("p004")));fail()}catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("başka bir yayıncı"))}
    }
    @Test fun twoSelectedProviderIdsForOneSiteAreRejected(){
        val old="$publisher/old-provider";val set=JSONObject().put(old,stored(module("old-provider")))
        try{PackageSets.replacedProviders(set,mapOf(old to module("old-provider"),"$publisher/p004" to module("p004")));fail()}catch(_:IllegalArgumentException){}
    }
}
