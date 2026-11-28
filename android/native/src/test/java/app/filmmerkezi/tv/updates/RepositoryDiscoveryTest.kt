package app.filmmerkezi.tv.updates

import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
import java.security.*
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

class RepositoryDiscoveryTest {
    companion object {
        private val key=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair()
        private const val root="https://raw.githubusercontent.com/example/film-merkezi/main/"
    }
    private fun signed(platform:String="android"):Map<String,ByteArray>{
        val entry=JSONObject().put("id","p002").put("version","1.0.0").put("type","site").put("name","Site")
            .put("manifest","packages/p002/1.0.0/manifest.json").put("manifestSha256","a".repeat(64)).put("minAppVersionCode",38)
        val raw=JSONObject().put("schemaVersion",1).put("platform",platform).put("runtime",UpdateProtocol.RUNTIME)
            .put("id","example").put("name","Example").put("publicKey",Base64.getEncoder().encodeToString(key.public.encoded))
            .put("revision",1).put("packages",JSONArray().put(entry)).toString().toByteArray()
        val sig=Signature.getInstance("SHA256withRSA").run{initSign(key.private);update(raw);Base64.getEncoder().encode(sign())}
        return mapOf(UpdateProtocol.INDEX to raw,UpdateProtocol.INDEX.removeSuffix(".json")+".sig" to sig)
    }
    private fun result(name:String="example/film-merkezi",branch:String="main")=JSONObject().put("full_name",name).put("default_branch",branch)
    private fun page(vararg rows:JSONObject,total:Int=rows.size,incomplete:Boolean=false)=JSONObject().put("items",JSONArray(rows.toList())).put("total_count",total).put("incomplete_results",incomplete).toString().toByteArray()
    private fun missing():Nothing=throw UpdateHttpFailure(404,message="HTTP 404")
    private suspend fun reject(block:suspend()->Unit):Throwable=try{block();error("Expected rejection")}catch(e:AssertionError){throw e}catch(e:Exception){e}

    @Test fun defaultQueryUsesRepositoryNameAndChecksExactSignature()=runBlocking {
        val signed=signed();val calls=mutableListOf<String>()
        val discovery=RepositoryDiscovery({url,_->calls.add(url);when{url.contains("/search/")->page(result());url.startsWith(root)->signed[url.removePrefix(root)]?:missing();else->missing()}})
        val found=discovery.search("   ")
        assertEquals("film-merkezi",found.query);assertEquals(1,found.items.size);assertEquals(1,found.items.single().sites)
        assertEquals(root+UpdateProtocol.INDEX,found.items.single().address)
        val query=URLDecoder.decode(calls.first().substringAfter("q=").substringBefore('&'),"UTF-8")
        assertEquals("film-merkezi in:name",query);assertFalse(calls.first().contains("in%3Areadme"))
        assertEquals(3,calls.size)
    }
    @Test fun tizenUnsignedAndTamperedIndicesNeverBecomeAndroidResults()=runBlocking {
        val valid=signed();val tizen=signed("tizen")
        val d=RepositoryDiscovery({url,_->when{
            url.contains("/search/")->page(result(),result("example/tizen"),result("example/tampered"),result("example/missing"))
            url.startsWith(root)->valid[url.removePrefix(root)]?:missing()
            url.contains("/tizen/main/")->tizen[url.substringAfter("/tizen/main/")]?:missing()
            url.contains("/tampered/main/")->valid[url.substringAfter("/tampered/main/")]?.let{if(url.endsWith(".json"))it+32.toByte()else it}?:missing()
            else->missing()
        }})
        val found=d.search();assertEquals(listOf("example/film-merkezi"),found.items.map{it.fullName});assertEquals(2,found.verificationFailures)
    }
    @Test fun readmeMarkerWhitespaceAndSubdirectoryAreRespected()=runBlocking {
        val files=signed();val marker="<!--\n film-merkezi-android-repository:\tupdates/${UpdateProtocol.INDEX}  -->"
        val d=RepositoryDiscovery({url,_->when{
            url.contains("/search/")->page(result())
            url==root+"README.md"->marker.toByteArray()
            url.startsWith(root+"updates/")->files[url.removePrefix(root+"updates/")]?:missing()
            else->missing()
        }})
        assertEquals(root+"updates/"+UpdateProtocol.INDEX,d.search().items.single().address)
    }
    @Test fun readmeFallbackAndRealDefaultBranchWorkForAddressEntry()=runBlocking {
        val body="<!-- film-merkezi-android-repository: nested/${UpdateProtocol.INDEX} -->"
        val d=RepositoryDiscovery({url,_->when{
            url=="https://api.github.com/repos/example/film-merkezi"->"{\"default_branch\":\"stable\"}".toByteArray()
            url.contains("/readme?ref=stable")->JSONObject().put("encoding","base64").put("content",Base64.getEncoder().encodeToString(body.toByteArray())).toString().toByteArray()
            else->missing()
        }})
        assertEquals(root.replace("/main/","/stable/")+"nested/"+UpdateProtocol.INDEX,d.repository("https://github.com/example/film-merkezi/"))
    }
    @Test fun unsafeOrConflictingReadmeMarkersAreRejected()=runBlocking {
        for(marker in listOf("../${UpdateProtocol.INDEX}","https://elsewhere.example/${UpdateProtocol.INDEX}","a/%2e%2e/${UpdateProtocol.INDEX}")){
            val d=RepositoryDiscovery({_,_->"<!-- film-merkezi-android-repository: $marker -->".toByteArray()})
            assertTrue(reject{d.address(RepositorySearchResult("example/film-merkezi","main",""))} is IllegalArgumentException)
        }
        val d=RepositoryDiscovery({_,_->("<!-- film-merkezi-android-repository: ${UpdateProtocol.INDEX} -->\n"+
            "<!-- film-merkezi-android-repository: another/${UpdateProtocol.INDEX} -->").toByteArray()})
        assertTrue(reject{d.address(RepositorySearchResult("example/film-merkezi","main",""))} is IllegalArgumentException)
    }
    @Test fun cacheIsScopedExpiresAndExplicitRefreshBypassesIt()=runBlocking {
        var now=0L;var calls=0
        val d=RepositoryDiscovery({_,_->calls++;page()},now={now})
        d.search();d.search("film-merkezi");assertEquals(1,calls)
        d.search(refresh=true);assertEquals(2,calls)
        d.search(page=2);d.search("example",true);assertEquals(4,calls)
        now=300001;d.search();assertEquals(5,calls)
    }
    @Test fun paginationUsesGithubCountEvenWhenNoAndroidRepoOnFirstPage()=runBlocking {
        val d=RepositoryDiscovery({_,_->page(total=15,incomplete=true)})
        assertTrue(d.search().next);assertTrue(d.search().incomplete);assertFalse(d.search(page=2).next)
    }
    @Test fun rateLimitAndAccessErrorsDoNotReturnEmptySuccess()=runBlocking {
        val failure=UpdateHttpFailure.response(403,"api.github.com","0",null,"120",100000)
        assertTrue(failure.rateLimited);assertEquals(120000L,failure.retryAt);assertTrue(failure.message.contains("20 saniye"))
        val d=RepositoryDiscovery({_,_->throw failure})
        assertSame(failure,reject{d.search()})
        assertFalse(UpdateHttpFailure.response(403,"api.github.com","4",null,null).rateLimited)
        assertTrue(UpdateHttpFailure.response(429,"api.github.com",null,"12",null,100000).message.contains("12 saniye"))
        assertTrue(UpdateHttpFailure.response(403,"api.github.com","4","12",null,100000).rateLimited)
    }
    @Test fun cancellationPropagatesAndDoesNotCacheLateResults()=runBlocking {
        var calls=0
        val d=RepositoryDiscovery({_,_->calls++;delay(10000);page()})
        val job=async{d.search()};delay(20);job.cancelAndJoin();assertTrue(job.isCancelled)
        val next=async{d.search()};delay(20);next.cancelAndJoin();assertEquals(2,calls)
    }
    @Test fun timeoutIsAnErrorAndNetworkFailureIsNotCached()=runBlocking {
        val d=RepositoryDiscovery({_,_->delay(1000);page()},timeoutMs=30)
        assertTrue(reject{d.search()}.message.orEmpty().contains("zaman aşımı"))
        var calls=0;val offline=RepositoryDiscovery({_,_->calls++;throw java.io.IOException("offline")})
        reject{offline.search()};reject{offline.search()};assertEquals(2,calls)
    }
    @Test fun slowCandidateCannotHideAlreadyVerifiedResults()=runBlocking {
        val files=signed()
        val d=RepositoryDiscovery({url,_->when{
            url.contains("/search/")->page(result(),result("example/slow"))
            url.startsWith(root)->files[url.removePrefix(root)]?:missing()
            else->{delay(2000);missing()}
        }},timeoutMs=300)
        val found=d.search();assertEquals(1,found.items.size);assertTrue(found.incomplete);assertEquals(1,found.verificationFailures)
    }
    @Test fun candidateVerificationHasAtMostTwoConcurrentRequests()=runBlocking {
        val active=AtomicInteger();val peak=AtomicInteger();val signed=signed()
        val d=RepositoryDiscovery({url,_->
            if(url.contains("/search/"))page(*(1..6).map{result("example/film-merkezi-$it")}.toTypedArray())else{
                val count=active.incrementAndGet();peak.accumulateAndGet(count,::maxOf)
                try{delay(10);signed[url.substringAfter("/main/")]?:missing()}finally{active.decrementAndGet()}
            }
        })
        assertEquals(6,d.search().items.size);assertEquals(2,peak.get())
    }
    @Test fun malformedSearchAndUnsafeCandidateAddressesFailClosed()=runBlocking {
        val d=RepositoryDiscovery({_,_->error("No request expected")})
        for(user in listOf("", "bad user", "../"))assertTrue(reject{d.search(user,true)} is IllegalArgumentException)
        assertTrue(reject{d.address(RepositorySearchResult("../example","main",""))} is IllegalArgumentException)
        assertTrue(reject{d.address(RepositorySearchResult("example/repo","../branch",""))} is IllegalArgumentException)
        assertTrue(reject{d.address(RepositorySearchResult("example/repo","main","","https://elsewhere.example/${UpdateProtocol.INDEX}"))} is IllegalArgumentException)
    }
}
