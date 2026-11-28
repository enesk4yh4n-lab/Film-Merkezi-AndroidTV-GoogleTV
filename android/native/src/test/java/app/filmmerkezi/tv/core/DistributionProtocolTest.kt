package app.filmmerkezi.tv.core

import app.filmmerkezi.tv.updates.UpdateProtocol as P
import app.filmmerkezi.tv.updates.PackageSets
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.security.*
import java.util.Base64

class DistributionProtocolTest {
    private fun key()=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair()
    private fun index(k:KeyPair)=JSONObject().put("schemaVersion",1).put("platform","android").put("runtime",P.RUNTIME).put("id","example").put("name","Example")
        .put("publicKey",Base64.getEncoder().encodeToString(k.public.encoded)).put("revision",1).put("packages",JSONArray())
    private fun bytes(o:JSONObject)=o.toString().toByteArray()
    @Test fun signedIndependentPublishersAndTampering(){
        val a=key();val b=key();val raw=bytes(index(a));val sig=Base64.getEncoder().encodeToString(Signature.getInstance("SHA256withRSA").run{initSign(a.private);update(raw);sign()})
        val doc=P.index(raw);P.verify(raw,sig,doc.publicKey);assertEquals(64,P.fingerprint(doc.publicKey).length)
        for(candidate in listOf(raw+32.toByte()))try{P.verify(candidate,sig,doc.publicKey);fail()}catch(_:IllegalArgumentException){}
        try{P.verify(raw,sig,Base64.getEncoder().encodeToString(b.public.encoded));fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun tizenAndUnknownRuntimeAreRejected(){val k=key();for(o in listOf(index(k).put("schemaVersion",2),index(k).put("runtime","es5"),index(k).put("platform","tizen"))){try{P.index(bytes(o));fail()}catch(_:IllegalArgumentException){}}}
    @Test fun packagePathsCannotEscape(){for(p in listOf("../plugin.jar","packages/../a","/absolute","a//b","a/%2e%2e/b","a\\b")){try{P.path(p);fail(p)}catch(_:IllegalArgumentException){}};assertEquals("packages/a/1.0.0/plugin.jar",P.path("packages/a/1.0.0/plugin.jar"))}
    @Test fun dependencyRangePreservesNumericOrdering(){val d=P.Dependency("a".repeat(64),"resolver","1.2.0","2.0.0");assertTrue(d.accepts("1.10.0"));assertFalse(d.accepts("2.0.0"));assertFalse(d.accepts("1.1.9"))}
    @Test fun indicesCannotDuplicateVersions(){val k=key();val e=JSONObject().put("id","site").put("version","1.0.0").put("type","site").put("name","Site").put("manifest","packages/site/1.0.0/manifest.json").put("manifestSha256","a".repeat(64)).put("minAppVersionCode",36)
        try{P.index(bytes(index(k).put("packages",JSONArray().put(e).put(e))));fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun signedManifestCanChangeTheActiveCategoryWithoutChangingProviderCode(){
        val original=Sites.all
        val publisher=key()
        val publicKey=Base64.getEncoder().encodeToString(publisher.public.encoded)
        val fingerprint=P.fingerprint(publicKey)
        val repoKey="$fingerprint/example"
        val packageKey="$fingerprint/p004"
        val hash="a".repeat(64)
        val installed=JSONObject().put(packageKey,JSONObject().put("repo",repoKey).put("artifact",hash))
        val state=JSONObject().put("packages",installed).put("repositories",JSONObject().put(repoKey,
            JSONObject().put("publicKey",publicKey).put("fingerprint",fingerprint)))
        fun register(version:String,path:String,enabled:Boolean=true){
            val site=PluginWire.site(Sites.get("filmcibaba"))
            site.put("catalogSections",JSONObject().put("ANIMATION",JSONArray().put(JSONObject().put("path",path))))
            val manifest=JSONObject().put("schemaVersion",1).put("platform",P.PLATFORM).put("runtime",P.RUNTIME)
                .put("id","p004").put("type","site").put("name","Example").put("version",version)
                .put("sdkMin",1).put("sdkMax",2).put("minAppVersionCode",39).put("minSdk",23)
                .put("entryClass","app.filmmerkezi.addon.Example").put("site",site)
                .put("origins",JSONArray().put("https://filmizle.ink")).put("artifact",JSONObject()
                    .put("path","packages/p004/$version/plugin.jar").put("size",100).put("sha256",hash))
            val raw=bytes(manifest)
            val signature=Base64.getEncoder().encodeToString(Signature.getInstance("SHA256withRSA").run{initSign(publisher.private);update(raw);sign()})
            installed.getJSONObject(packageKey).put("manifest",manifest.toString()).put("signature",signature).put("enabled",enabled)
            PackageSets.registerSites(state,PackageSets.validate(state,installed,39,28))
        }
        try{
            register("1.1.0","/anime/")
            assertEquals(listOf("/anime/"),CatalogSection.ANIMATION.routes("filmcibaba").map{it.path})
            val first=Sites.catalogRevision("filmcibaba")
            register("1.2.0","/anime-new/")
            assertEquals(listOf("/anime-new/"),CatalogSection.ANIMATION.routes("filmcibaba").map{it.path})
            assertNotEquals(first,Sites.catalogRevision("filmcibaba"))
            assertEquals(listOf("filmcibaba"),Sites.all.map{it.id})
            register("1.2.0","/anime-new/",false)
            assertFalse(Sites.isInstalled("filmcibaba"))
        }finally{Sites.installed(original)}
    }
}
