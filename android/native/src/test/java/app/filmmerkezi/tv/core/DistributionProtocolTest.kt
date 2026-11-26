package app.filmmerkezi.tv.core

import app.filmmerkezi.tv.updates.UpdateProtocol as P
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
}
