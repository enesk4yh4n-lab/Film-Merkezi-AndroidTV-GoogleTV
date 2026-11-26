package app.filmmerkezi.tv.core

import app.filmmerkezi.tv.pluginapi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*

/** Production uses a process boundary. JVM fixtures can load the source runtime on their classpath. */
object PluginGateway {
    val parsing=Dispatchers.Default.limitedParallelism(1)
    @Volatile var remote:(suspend(String,String,String,JSONObject)->JSONObject)?=null
    fun standalone():Plugin=try{Class.forName("app.filmmerkezi.addon.BuiltinEntry").getDeclaredConstructor().newInstance()as Plugin}catch(_:ClassNotFoundException){throw DataFailure(FailureKind.UNSUPPORTED,"Bu site için eklenti kurun: Güncelleme Merkezi → Depo ekle","plugin")}
    suspend fun call(site:SiteSpec,type:String,operation:String,data:JSONObject,network:WebTransport,youtube:YoutubeResolver?=null,local:()->Plugin):JSONObject {
        val input=JSONObject(data.toString()).put("site",PluginWire.site(site))
        remote?.let{return it(site.id,type,operation,input)}
        return withContext(Dispatchers.IO){
            val job=currentCoroutineContext()[Job]
            val host=object:PluginHost {
                override fun cancelled()=job?.isActive==false
                override fun request(payload:String):String=runBlocking(job?:kotlin.coroutines.EmptyCoroutineContext){
                    val o=JSONObject(payload);fun map(k:String)=o.optJSONObject(k)?.let{x->x.keys().asSequence().associateWith{x.getString(it)}}?:emptyMap()
                    val r=network.request(WebRequest(o.getString("url"),o.optString("referer"),o.optString("method","GET"),map("fields"),if(o.isNull("body"))null else o.optString("body"),map("headers"),o.optLong("timeoutMs",12000),o.optInt("byteLimit",2097152)))
                    JSONObject().put("url",r.url).put("body",r.body).put("code",r.code).put("headers",JSONObject(r.headers)).toString()
                }
                override fun resolver(dependencyId:String,operation:String,payload:String):String=runBlocking(job?:kotlin.coroutines.EmptyCoroutineContext){
                    require(dependencyId=="youtube"&&youtube!=null);val o=JSONObject(payload);Contracts.source(youtube.resolve(PluginWire.option(o.getJSONObject("option")),o.getString("url"))).toString()
                }
                override fun storage(operation:String,key:String,value:String)=error("Fixture storage is not available")
            }
            JSONObject(local().call(operation,input.toString(),host))
        }
    }
}

class NativeSiteProvider(private val template:SiteSpec,private val network:WebTransport,private val parsing:CoroutineDispatcher):SiteProvider {
    override val site get()=Sites.current(template)
    private var standalone:Plugin?=null
    private val standaloneLock=Mutex()
    private suspend fun invoke(op:String,o:JSONObject=JSONObject()):JSONObject {
        suspend fun call()=PluginGateway.call(site,"site",op,o,network,local={standalone?:PluginGateway.standalone().also{standalone=it}})
        return if(PluginGateway.remote!=null)call()else standaloneLock.withLock{call()}
    }
    override suspend fun search(query:String,next:PageCursor?)=PluginWire.page(invoke("search",JSONObject().put("query",query).put("next",next?.let(Contracts::cursor)?:JSONObject.NULL)))
    override suspend fun browse(kind:ContentKind,next:PageCursor?)=PluginWire.page(invoke("browse",JSONObject().put("kind",kind.name).put("next",next?.let(Contracts::cursor)?:JSONObject.NULL)))
    override suspend fun detail(card:Card)=PluginWire.detail(invoke("detail",JSONObject().put("card",Contracts.card(card))))
    override suspend fun episodes(card:Card):List<Episode>{val a=invoke("episodes",JSONObject().put("card",Contracts.card(card))).getJSONArray("episodes");return (0 until a.length()).map{PluginWire.episode(a.getJSONObject(it))}}
    suspend fun options()=PluginWire.options(invoke("options"))
    suspend fun filtered(q:CatalogQuery,next:PageCursor?,check:()->Boolean,unverifiedCandidates:Boolean=false):CatalogPage {
        // The shared verification budget stays in the host. Reserving it before a module
        // returns would let a provider with no missing metadata consume everyone else's budget.
        val page=PluginWire.page(invoke("filtered",JSONObject().put("query",PluginWire.query(q)).put("next",next?.let(Contracts::cursor)?:JSONObject.NULL).put("verificationBudget",0).put("unverifiedCandidates",true)))
        val coverage=page.coverage?:return page
        var checked=0;var omitted=0
        val cards=mutableListOf<Card>()
        for(raw in page.cards){var card=raw
            if(!q.accepts(card,coverage.remote)&&coverage.local.isNotEmpty()&&check()){
                checked++;try{card=detail(card).card}catch(e:CancellationException){throw e}catch(_:Exception){}
            }
            if(unverifiedCandidates||q.accepts(card,coverage.remote))cards.add(card)else omitted++
        }
        val sorted=when(q.order){CatalogOrder.DEFAULT->cards;CatalogOrder.YEAR->cards.sortedByDescending{it.year?:0};CatalogOrder.RATING->cards.sortedByDescending{it.imdb?:0.0};CatalogOrder.TITLE->cards.sortedBy{TextKey.fold(it.title)}}
        return page.copy(cards=sorted,coverage=coverage.copy(checked=checked,omitted=omitted))
    }
    suspend fun parseList(body:String,url:String)=PluginWire.page(invoke("parseList",JSONObject().put("body",body).put("url",url)))
    suspend fun collections():List<Card>{val a=invoke("collections").getJSONArray("cards");return (0 until a.length()).map{Contracts.readCard(a.getJSONObject(it))}}
    fun trim(){standalone=null}
}

class SourceResolver(private val network:WebTransport,private val youtube:YoutubeResolver=NewPipeYoutube(network)) {
    suspend fun resolve(option:SourceOption)=PluginWire.source(PluginGateway.call(Sites.get(option.content.siteId),"resolver","resolve",JSONObject().put("option",Contracts.option(option)),network,youtube){PluginGateway.standalone()})
}
