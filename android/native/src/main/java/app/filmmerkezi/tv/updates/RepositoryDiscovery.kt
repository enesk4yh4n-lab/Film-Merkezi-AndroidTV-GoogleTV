package app.filmmerkezi.tv.updates

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

data class RepositorySearchResult(val fullName:String,val branch:String,val description:String,
    val address:String="",val sites:Int=0,val resolvers:Int=0)
data class RepositorySearchPage(val query:String,val user:Boolean,val page:Int,val items:List<RepositorySearchResult>,
    val next:Boolean=false,val incomplete:Boolean=false,val verificationFailures:Int=0)

/** Discovery is public and read-only. An item is shown only after its Android index signature passes. */
class RepositoryDiscovery(private val read:suspend(String,Int)->ByteArray,private val now:()->Long={System.nanoTime()/1_000_000},private val timeoutMs:Long=25000) {
    companion object {const val DEFAULT_QUERY="film-merkezi"}
    private val cache=object:LinkedHashMap<String,Pair<Long,RepositorySearchPage>>(8,0.75f,true){override fun removeEldestEntry(e:MutableMap.MutableEntry<String,Pair<Long,RepositorySearchPage>>?)=size>8}
    private val slots=Semaphore(2)
    private fun encode(v:String)=URLEncoder.encode(v,"UTF-8")
    fun query(text:String,user:Boolean=false):String {
        val term=text.trim().ifBlank{if(user)throw IllegalArgumentException("GitHub kullanıcı adını yazın.")else DEFAULT_QUERY}
        require(term.length<=80&&!term.any{it<' '||it=='"'||it=='\\'}){"Arama en fazla 80 karakter olmalı."}
        require(!user||term.matches(Regex("[A-Za-z0-9-]{1,39}"))){"Geçerli bir GitHub kullanıcı adı yazın."}
        require(user||term.any{it.isLetterOrDigit()}){"Bir depo adı yazın."}
        return term
    }
    private fun validate(result:RepositorySearchResult) {
        require(result.fullName.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")))
        require(result.fullName.split('/').none{it=="."||it==".."})
        require(result.branch.matches(Regex("[A-Za-z0-9_./-]{1,100}"))&&!result.branch.contains("..")&&!result.branch.startsWith('/'))
    }
    private fun base(result:RepositorySearchResult):String {validate(result);return "https://raw.githubusercontent.com/${result.fullName}/${result.branch}/"}
    private suspend fun optional(url:String,limit:Int):ByteArray?=try{read(url,limit)}catch(e:UpdateHttpFailure){if(e.code==404)null else throw e}
    private fun marker(text:String):String? {
        val matches=Regex("<!--\\s*film-merkezi-android-repository:\\s*([^\\s>]+)\\s*-->").findAll(text).map{it.groupValues[1]}.distinct().toList()
        require(matches.size<=1){"README içinde çelişen Android depo adresleri var."}
        return matches.firstOrNull()?.also{UpdateProtocol.path(it);require(it.substringAfterLast('/')==UpdateProtocol.INDEX){"Android depo dosyası seçilmeli."}}
    }
    suspend fun address(result:RepositorySearchResult):String {
        val root=base(result)
        if(result.address.isNotBlank()){
            require(result.address.startsWith(root)){"Depo adresi arama sonucuyla eşleşmiyor."}
            val path=result.address.removePrefix(root);UpdateProtocol.path(path);require(path.substringAfterLast('/')==UpdateProtocol.INDEX)
            return UpdateTransport.repository(result.address)
        }
        var body=optional(root+"README.md",262144)?.toString(Charsets.UTF_8)
        if(body==null){
            val raw=optional("https://api.github.com/repos/${result.fullName}/readme?ref="+encode(result.branch),524288)
            if(raw!=null){val j=JSONObject(raw.toString(Charsets.UTF_8));require(j.getString("encoding")=="base64")
                body=java.util.Base64.getMimeDecoder().decode(j.getString("content")).toString(Charsets.UTF_8)
                require(body.length<=262144){"README çok büyük."}
            }
        }
        return UpdateTransport.repository(root+(body?.let(::marker)?:UpdateProtocol.INDEX))
    }
    suspend fun repository(value:String):String {
        val uri=java.net.URI(UpdateTransport.https(value.trim()))
        val parts=uri.path.split('/').filter{it.isNotBlank()}
        if(uri.host=="github.com"&&parts.size==2){
            require(uri.rawQuery==null)
            val name=parts.joinToString("/");validate(RepositorySearchResult(name,"main",""))
            val data=JSONObject(read("https://api.github.com/repos/$name",65536).toString(Charsets.UTF_8))
            return address(RepositorySearchResult(name,data.getString("default_branch"),""))
        }
        return UpdateTransport.repository(value)
    }
    private suspend fun verify(result:RepositorySearchResult):RepositorySearchResult? {
        val root=base(result);var url=root+UpdateProtocol.INDEX
        var raw=optional(url,UpdateProtocol.INDEX_LIMIT)
        if(raw==null){url=address(result);if(url==root+UpdateProtocol.INDEX)return null;raw=optional(url,UpdateProtocol.INDEX_LIMIT)?:return null}
        val index=UpdateProtocol.index(raw)
        val sig=read(url.removeSuffix(".json")+".sig",2048).toString(Charsets.US_ASCII)
        UpdateProtocol.verify(raw,sig,index.publicKey)
        val entries=index.packages.distinctBy{it.id}
        return result.copy(address=url,sites=entries.count{it.type=="site"},resolvers=entries.count{it.type=="resolver"})
    }
    suspend fun search(text:String="",user:Boolean=false,page:Int=1,refresh:Boolean=false):RepositorySearchPage {
        require(page in 1..10){"Arama sayfası geçersiz."};val term=query(text,user);val key="$user|$term|$page"
        if(!refresh)synchronized(cache){cache[key]?.takeIf{now()-it.first<300000}?.let{return it.second}}
        val completed=java.util.concurrent.ConcurrentHashMap<String,RepositorySearchResult>()
        var candidates=emptyList<RepositorySearchResult>();var hasNext=false
        val found=withTimeoutOrNull(timeoutMs){
            // Name discovery is independent of GitHub's README indexing delay. Each result is verified below.
            val q=if(user)"user:$term"else term.replace(Regex("[:<>]")," ")+" in:name"
            val data=JSONObject(read("https://api.github.com/search/repositories?q=${encode(q)}&per_page=10&page=$page",262144).toString(Charsets.UTF_8))
            val rows=data.optJSONArray("items")?:throw IOException("GitHub arama yanıtı okunamadı.")
            candidates=(0 until minOf(rows.length(),10)).mapNotNull{i->val r=rows.getJSONObject(i)
                if(r.optBoolean("private")||r.optBoolean("disabled"))null else RepositorySearchResult(r.getString("full_name"),r.getString("default_branch"),r.optString("description").takeUnless{it=="null"}.orEmpty().take(200))
            }.distinctBy{it.fullName}
            hasNext=page<10&&page*10<data.optInt("total_count")
            var failed=0
            val items=coroutineScope {candidates.map{r->async{slots.withPermit {
                try{verify(r)?.also{completed[r.fullName]=it}}catch(e:CancellationException){throw e}catch(e:UpdateHttpFailure){if(e.rateLimited)throw e; synchronized(this@RepositoryDiscovery){failed++};null}
                catch(_:Exception){synchronized(this@RepositoryDiscovery){failed++};null}
            }}}.awaitAll().filterNotNull()}
            RepositorySearchPage(term,user,page,items,next=hasNext,incomplete=data.optBoolean("incomplete_results"),verificationFailures=failed)
        }?:completed.takeIf{it.isNotEmpty()}?.let{RepositorySearchPage(term,user,page,candidates.mapNotNull{r->it[r.fullName]},next=hasNext,incomplete=true,verificationFailures=candidates.size-it.size)}
            ?:throw IOException("Depo araması zaman aşımına uğradı. Yeniden deneyin veya adresle ekleyin.")
        if(!found.incomplete&&found.verificationFailures==0)synchronized(cache){cache[key]=now() to found};return found
    }
}

class UpdateHttpFailure(val code:Int,val rateLimited:Boolean=false,val retryAt:Long?=null,override val message:String):IOException(message) {
    companion object {
        fun response(code:Int,host:String,remaining:String?,retryAfter:String?,reset:String?,now:Long=System.currentTimeMillis()):UpdateHttpFailure {
            val limited=code==429||code==403&&(remaining=="0"||retryAfter?.toLongOrNull()!=null)
            val until=retryAfter?.toLongOrNull()?.takeIf{it in 0..86400}?.let{now+it*1000}
                ?:reset?.toLongOrNull()?.let{it*1000}?.takeIf{it>now}
            val message=if(limited){val seconds=until?.let{((it-now+999)/1000).coerceAtLeast(1)}
                (if(host=="api.github.com")"GitHub istek sınırına ulaşıldı."else"Sunucu istek sınırına ulaşıldı.")+(seconds?.let{" $it saniye sonra yeniden deneyin veya adresle ekleyin."}?:" Daha sonra yeniden deneyin veya adresle ekleyin.")
            }else if(host=="api.github.com"&&code==403)"GitHub isteği reddetti (HTTP 403). Daha sonra yeniden deneyin veya adresle ekleyin."
            else "HTTP $code"
            return UpdateHttpFailure(code,limited,until,message)
        }
    }
}
