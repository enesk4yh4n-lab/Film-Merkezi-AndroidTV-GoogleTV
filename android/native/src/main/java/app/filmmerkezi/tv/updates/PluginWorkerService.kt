package app.filmmerkezi.tv.updates

import android.app.Service
import android.content.Intent
import android.os.*
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.pluginapi.*
import dalvik.system.DexClassLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Failure containment in :plugins. Same UID: this is explicitly NOT an untrusted-code sandbox. */
class PluginWorkerService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val jobs=ConcurrentHashMap<Long,Job>()
    private val entries=object:LinkedHashMap<String,Plugin>(4,0.75f,true){override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,Plugin>?)=size>2}
    private val loaders=object:LinkedHashMap<String,ClassLoader>(4,0.75f,true){override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,ClassLoader>?)=size>2}
    private val gate=kotlinx.coroutines.sync.Semaphore(2)
    private val identityLocks=ConcurrentHashMap<String,kotlinx.coroutines.sync.Mutex>()
    private lateinit var store:DistributionStore
    private lateinit var network:ProviderNetwork
    private val cookies=ConcurrentHashMap<String,MutableList<okhttp3.Cookie>>()
    private val httpSlots=kotlinx.coroutines.sync.Semaphore(2)
    companion object {const val TOKEN="app.filmmerkezi.tv.plugins.v1";const val CALL=1;const val CANCEL=2;const val PID=3;const val TRIM=4}
    override fun onCreate(){super.onCreate();store=DistributionStore(this);network=ProviderNetwork(this);installSites(store.snapshot())}
    private fun installSites(state:JSONObject){val packages=state.getJSONObject("packages");Sites.installed(packages.keys().asSequence().map{packages.getJSONObject(it)}.filter{it.optBoolean("enabled",true)}.map{UpdateProtocol.module(it.getString("manifest").toByteArray())}.filter{it.type=="site"}.map{PluginWire.site(it.site!!)}.toList())}
    private val binder=object:Binder(){
        override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
            data.enforceInterface(TOKEN)
            when(code){
                PID->{reply!!.writeNoException();reply.writeInt(android.os.Process.myPid());return true}
                CANCEL->{jobs[data.readLong()]?.cancel();return true}
                TRIM->{if(jobs.isEmpty()){synchronized(entries){entries.clear();loaders.clear()};cookies.clear();network.client.connectionPool.evictAll()};return true}
                CALL->{
                    val id=data.readLong();val identity=data.readString()!!;val operation=data.readString()!!
                    @Suppress("DEPRECATION") val input=data.readParcelable<ParcelFileDescriptor>(javaClass.classLoader)!!
                    val output=ParcelFileDescriptor.createPipe()
                    val task=scope.launch(start=CoroutineStart.LAZY){
                        try{
                            val raw=ParcelFileDescriptor.AutoCloseInputStream(input).use{readBounded(it,4*1024*1024)}
                            val payload=JSONObject(raw.toString(Charsets.UTF_8));val lock=identityLocks.getOrPut(identity){kotlinx.coroutines.sync.Mutex()}
                            val result=gate.withPermit{lock.withLock{withContext(Dispatchers.IO){invoke(identity,operation,payload,currentCoroutineContext()[Job])}}}
                            write(output[1],JSONObject().put("ok",true).put("result",JSONObject(result)))
                        }catch(e:Throwable){write(output[1],JSONObject().put("ok",false).put("message",e.message?:"Eklenti hatası").put("kind",(e as? DataFailure)?.kind?.name?:(e as? PluginException)?.kind?:"PARSE").put("stage",(e as? DataFailure)?.stage?:(e as? PluginException)?.stage?:"plugin").put("httpCode",(e as? DataFailure)?.httpCode?:(e as? PluginException)?.httpCode?.takeIf{it>0}?:JSONObject.NULL))}
                        finally{jobs.remove(id);input.close();output[1].close()}
                    };jobs[id]=task;task.start();reply!!.writeNoException();reply.writeParcelable(output[0],Parcelable.PARCELABLE_WRITE_RETURN_VALUE);return true
                }
            };return super.onTransact(code,data,reply,flags)
        }
    }
    private fun write(fd:ParcelFileDescriptor,value:JSONObject){try{val bytes=value.toString().toByteArray();require(bytes.size<=4*1024*1024){"Eklenti yanıt sınırı"};ParcelFileDescriptor.AutoCloseOutputStream(fd).use{it.write(bytes)}}catch(_:Exception){}}
    private fun readBounded(input:java.io.InputStream,limit:Int):ByteArray{val out=java.io.ByteArrayOutputStream();val buf=ByteArray(16384);while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=limit);out.write(buf,0,n)};return out.toByteArray()}
    private fun invoke(identity:String,operation:String,payload:JSONObject,callJob:Job?):String {
        val state=store.snapshot();val record=state.getJSONObject("packages").optJSONObject(identity)?:error("Eklenti kurulu değil")
        val repo=state.getJSONObject("repositories").getJSONObject(record.getString("repo"));require(record.optBoolean("enabled",true))
        val raw=record.getString("manifest").toByteArray(Charsets.UTF_8);UpdateProtocol.verify(raw,record.getString("signature"),repo.getString("publicKey"));val m=UpdateProtocol.module(raw)
        require(identity==repo.getString("fingerprint")+"/"+m.id&&m.hash==record.getString("artifact"));require(m.minSdk<=Build.VERSION.SDK_INT&&m.minApp<=app.filmmerkezi.tv.BuildConfig.VERSION_CODE)
        require(operation=="describe"||if(m.type=="site")operation in setOf("search","browse","detail","episodes","options","filtered","parseList","collections","trim")else operation=="resolve")
        payload.optJSONObject("site")?.let{v->Sites.updateMetadata(PluginWire.site(v))}
        val file=File(store.directory,"objects/${m.hash}.jar");require(file.exists()&&file.length()==m.size.toLong())
        val siteId=payload.getJSONObject("site").getString("id")
        val all=state.getJSONObject("packages")
        val siteOwner=all.keys().asSequence().map{all.getJSONObject(it)}.firstOrNull{record->val mm=UpdateProtocol.module(record.getString("manifest").toByteArray());mm.type=="site"&&mm.site!!.getString("id")==siteId}
        val cookieScope=if(siteOwner!=null)state.getJSONObject("repositories").getJSONObject(siteOwner.getString("repo")).getString("fingerprint")+"/"+siteId else identity
        val sessionCookies=cookies.getOrPut(cookieScope){mutableListOf()}
        val plugin=synchronized(entries){entries[identity]?:run{
            require(UpdateProtocol.sha(file.readBytes())==m.hash){"Kurulu eklenti bozulmuş"};require(file.setReadOnly())
            val loader=loaders[m.hash]?:DexClassLoader(file.path,codeCacheDir.path,null,classLoader).also{loaders[m.hash]=it}
            (loader.loadClass(m.entry).also{require(it.classLoader===loader){"Eklenti sinifi APK tarafindan golgeleniyor"}}.getDeclaredConstructor().newInstance()as Plugin).also{entries[identity]=it}
        }}
        val host=object:PluginHost {
            private val scopedNetwork=ProviderNetwork(this@PluginWorkerService,{u->allowed(u.toString())},network.client.connectionPool,sessionCookies)
            fun close(){scopedNetwork.client.dispatcher.executorService.shutdown()}
            override fun cancelled()=callJob?.isActive==false
            private fun allowed(url:String){if(url.isBlank())return;val u=url.toHttpUrl();require(u.scheme=="https"&&u.port==443&&u.username.isEmpty()&&u.password.isEmpty())
                val custom=payload.optJSONObject("site")?.optString("base")?.let{runCatching{it.toHttpUrl().host}.getOrNull()}
                require(u.host==custom||m.origins.any{origin->val pattern=origin.removePrefix("https://").replace(".","\\.").replace("*","[a-z0-9.-]+").replace("{n}","[0-9]{1,6}");Regex("^$pattern$").matches(u.host)}){"Paket ağ izni dışında: ${u.host}"}}
            override fun request(payload:String):String=runBlocking(callJob?:kotlin.coroutines.EmptyCoroutineContext){
                val o=JSONObject(payload);allowed(o.getString("url"));allowed(o.optString("referer"));fun map(k:String)=o.optJSONObject(k)?.let{x->x.keys().asSequence().associateWith{x.getString(it)}}?:emptyMap()
                map("headers").forEach{(k,v)->if(k.equals("Origin",true)||k.equals("Referer",true))allowed(v)}
                val r=httpSlots.withPermit{scopedNetwork.request(WebRequest(o.getString("url"),o.optString("referer"),o.optString("method","GET"),map("fields"),if(o.isNull("body"))null else o.optString("body"),map("headers"),o.optLong("timeoutMs",12000),o.optInt("byteLimit",2097152)))}
                allowed(r.url);JSONObject().put("url",r.url).put("body",r.body).put("code",r.code).put("headers",JSONObject(r.headers)).toString()
            }
            override fun resolver(dependencyId:String,operation:String,payload:String):String {
                if(dependencyId=="youtube"){val o=JSONObject(payload);return runBlocking{Contracts.source(NewPipeYoutube(network).resolve(PluginWire.option(o.getJSONObject("option")),o.getString("url"))).toString()}}
                val d=m.dependencies.single{it.id==dependencyId};val target=d.publisher+"/"+d.id
                return runBlocking(callJob?:kotlin.coroutines.EmptyCoroutineContext){identityLocks.getOrPut(target){kotlinx.coroutines.sync.Mutex()}.withLock{invoke(target,operation,JSONObject(payload),callJob)}}
            }
            override fun storage(operation:String,key:String,value:String):String {
                require(key.length in 1..100&&value.length<=16384);val prefs=getSharedPreferences("plugin-"+UpdateProtocol.sha(identity.toByteArray()),0)
                return when(operation){"get"->prefs.getString(key,"").orEmpty();"put"->{val all=prefs.all;require((all.keys+key).size<=128&&all.filterKeys{it!=key}.values.sumOf{(it as? String).orEmpty().length}+value.length<=262144){"Eklenti depolama siniri"};require(prefs.edit().putString(key,value).commit());""};"remove"->{require(prefs.edit().remove(key).commit());""};else->error("Depolama işlemi geçersiz")}
            }
        }
        return try{val value=plugin.call(operation,payload.toString(),host)
            if(operation!="resolve")value else JSONObject(value).put("sessionCookies",synchronized(sessionCookies){JSONArray(sessionCookies.map{c->JSONObject().put("name",c.name).put("value",c.value).put("domain",c.domain).put("path",c.path).put("expiresAt",c.expiresAt).put("secure",c.secure).put("httpOnly",c.httpOnly).put("hostOnly",c.hostOnly)})}).toString()
        }finally{host.close()}
    }
    override fun onBind(intent:Intent)=binder
    override fun onDestroy(){scope.cancel();entries.clear();loaders.clear();super.onDestroy()}
}
