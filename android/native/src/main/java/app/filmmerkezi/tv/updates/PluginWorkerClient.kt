package app.filmmerkezi.tv.updates

import android.content.*
import android.os.*
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.util.concurrent.atomic.AtomicLong

class PluginWorkerClient(private val context:Context,private val manager:DistributionManager) {
    private val ids=AtomicLong()
    private val binding=Mutex();private val health=Mutex()
    @Volatile private var connected:IBinder?=null
    @Volatile private var pid=0
    private var connection:ServiceConnection?=null
    private suspend fun binder():IBinder=binding.withLock{
        connected?.takeIf{it.isBinderAlive}?.let{return@withLock it}
        val ready=CompletableDeferred<IBinder>();val conn=object:ServiceConnection{
            override fun onServiceConnected(name:ComponentName,service:IBinder){connected=service;ready.complete(service)}
            override fun onServiceDisconnected(name:ComponentName){connected=null;pid=0}
            override fun onBindingDied(name:ComponentName){connected=null;pid=0;ready.completeExceptionally(IllegalStateException("Eklenti süreci kapandı"))}
        };connection=conn
        withContext(Dispatchers.Main){check(context.bindService(Intent(context,PluginWorkerService::class.java),conn,Context.BIND_AUTO_CREATE)){"Eklenti süreci başlatılamadı"}}
        val b=withTimeout(10000){ready.await()};withContext(Dispatchers.IO){val data=Parcel.obtain();val reply=Parcel.obtain();try{data.writeInterfaceToken(PluginWorkerService.TOKEN);b.transact(PluginWorkerService.PID,data,reply,0);reply.readException();pid=reply.readInt()}finally{data.recycle();reply.recycle()}};b
    }
    suspend fun verifyBoot(){
        health.withLock{
            if(manager.store.snapshot().optBoolean("bootPending")){
                try{val all=manager.store.snapshot().getJSONObject("packages");for(key in all.keys()){
                    val m=UpdateProtocol.module(all.getJSONObject(key).getString("manifest").toByteArray());val spec=m.site?:Sites.known.firstOrNull()?.let(PluginWire::site)?:JSONObject().put("id","probe").put("name","Probe").put("base","https://example.com")
                    request(key,"describe",JSONObject().put("site",spec),10000)
                };PackageInstaller(manager).accept()}
                catch(e:CancellationException){throw e}
                catch(e:Exception){PackageInstaller(manager).recover();stop();ModuleRuntime.register(manager.store.snapshot());throw DataFailure(FailureKind.PARSE,"Eklenti seti başlatılamadı; önceki sete dönüldü. Uygulamayı yeniden açın.","plugin",cause=e)}
            }
        }
    }
    suspend fun call(siteId:String,type:String,operation:String,payload:JSONObject):JSONObject {
        verifyBoot()
        val all=manager.store.snapshot().getJSONObject("packages");val sites=all.keys().asSequence().filter{key->val m=UpdateProtocol.module(all.getJSONObject(key).getString("manifest").toByteArray());m.type=="site"&&m.site!!.getString("id")==siteId}.toList()
        val owner=sites.singleOrNull()?:throw DataFailure(FailureKind.UNSUPPORTED,"Bu site için eklenti kurun: $siteId","plugin")
        val identity=if(type=="site")owner else{
            val m=UpdateProtocol.module(all.getJSONObject(owner).getString("manifest").toByteArray())
            val fields=payload.optJSONObject("option")?.optJSONObject("fields");val requested=fields?.optString("_resolverId").orEmpty()
            val d=(if(requested.isBlank())m.dependencies.firstOrNull()else m.dependencies.singleOrNull{it.id==requested&&(fields?.optString("_resolverPublisher").isNullOrBlank()||it.publisher==fields!!.getString("_resolverPublisher"))})?:throw DataFailure(FailureKind.UNSUPPORTED,"Bildirilen çözümleyici bağımlılığı yok","plugin");d.publisher+"/"+d.id
        }
        return request(identity,operation,payload,if(operation=="episodes")65000 else 35000)
    }
    suspend fun probe(identity:String,payload:JSONObject)=request(identity,"describe",payload,10000)
    fun trim(){val b=connected?:return;val data=Parcel.obtain();try{data.writeInterfaceToken(PluginWorkerService.TOKEN);b.transact(PluginWorkerService.TRIM,data,null,IBinder.FLAG_ONEWAY)}catch(_:Exception){}finally{data.recycle()}}
    @OptIn(InternalCoroutinesApi::class)
    private suspend fun request(identity:String,operation:String,payload:JSONObject,timeout:Long):JSONObject {
        val b=binder();val id=ids.incrementAndGet();val raw=payload.toString().toByteArray(Charsets.UTF_8);require(raw.size<=4*1024*1024)
        val done=java.util.concurrent.atomic.AtomicBoolean(false)
        val timedOut=java.util.concurrent.atomic.AtomicBoolean(false)
        val watchdogScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val watchdog=watchdogScope.launch{delay(timeout);if(!done.get()){timedOut.set(true);stop()}}
        val cancellation=currentCoroutineContext()[Job]?.invokeOnCompletion(onCancelling=true,invokeImmediately=true){cause->if(cause!=null){
            val cancel=Parcel.obtain();try{cancel.writeInterfaceToken(PluginWorkerService.TOKEN);cancel.writeLong(id);b.transact(PluginWorkerService.CANCEL,cancel,null,IBinder.FLAG_ONEWAY)}catch(_:Exception){}finally{cancel.recycle()}
            watchdogScope.launch{delay(500);if(!done.get())stop()}
        }}
        return try{withTimeout(timeout){withContext(Dispatchers.IO){
            val input=ParcelFileDescriptor.createPipe();val writer=async(Dispatchers.IO){ParcelFileDescriptor.AutoCloseOutputStream(input[1]).use{it.write(raw)}}
            val data=Parcel.obtain();val reply=Parcel.obtain()
            try{
                data.writeInterfaceToken(PluginWorkerService.TOKEN);data.writeLong(id);data.writeString(identity);data.writeString(operation);data.writeParcelable(input[0],0)
                check(b.transact(PluginWorkerService.CALL,data,reply,0));reply.readException()
                @Suppress("DEPRECATION") val output=reply.readParcelable<ParcelFileDescriptor>(javaClass.classLoader)!!
                val result=withContext(Dispatchers.IO){ParcelFileDescriptor.AutoCloseInputStream(output).use{stream->val out=java.io.ByteArrayOutputStream();val bytes=ByteArray(16384);while(true){ensureActive();val n=stream.read(bytes);if(n<0)break;require(out.size()+n<=4*1024*1024);out.write(bytes,0,n)}
                    if(out.size()==0)throw DataFailure(if(timedOut.get())FailureKind.TIMEOUT else FailureKind.NETWORK,if(timedOut.get())"Eklenti zaman aşımına uğradı; yeniden deneyin"else"Eklenti süreci kapandı; yeniden deneyin","plugin")
                    JSONObject(out.toString("UTF-8"))}}
                writer.await();if(!result.getBoolean("ok"))throw DataFailure(runCatching{FailureKind.valueOf(result.getString("kind"))}.getOrDefault(FailureKind.PARSE),result.optString("message"),result.optString("stage"),result.optInt("httpCode").takeIf{it>0})
                result.getJSONObject("result")
            }finally{input[0].close();writer.cancel();data.recycle();reply.recycle()}
        }}}
        catch(e:CancellationException){val cancel=Parcel.obtain();try{cancel.writeInterfaceToken(PluginWorkerService.TOKEN);cancel.writeLong(id);b.transact(PluginWorkerService.CANCEL,cancel,null,IBinder.FLAG_ONEWAY)}catch(_:Exception){}finally{cancel.recycle()}
            // The process watchdog also stops code that ignores cooperative cancellation.
            if(e is TimeoutCancellationException){stop();throw DataFailure(FailureKind.TIMEOUT,"Eklenti zaman aşımına uğradı; yardımcı süreç yeniden başlatılacak","plugin",cause=e)};throw e
        }
        finally{done.set(true);cancellation?.dispose();watchdog.cancel();watchdogScope.cancel()}
    }
    fun stop(){val previousPid=pid;pid=0;connected=null;connection?.let{runCatching{context.unbindService(it)}};connection=null;if(previousPid>0&&previousPid!=android.os.Process.myPid())android.os.Process.killProcess(previousPid)}
}

object ModuleRuntime {
    @Volatile private var prepared=false
    private var client:PluginWorkerClient?=null
    @Synchronized fun prepare(context:Context){if(prepared)return
        if(app.filmmerkezi.tv.BuildConfig.MIGRATION){Sites.installed(Sites.defaults);prepared=true;return}
        val manager=DistributionManager.get(context);val installer=PackageInstaller(manager)
        runBlocking(Dispatchers.IO){
            if(manager.store.snapshot().optBoolean("bootPending"))installer.recover()
            if(manager.store.snapshot().optBoolean("needsBoot"))manager.store.commit{it.remove("needsBoot");it.put("bootPending",true)}
        }
        try{register(manager.store.snapshot())}catch(e:Exception){
            // Keep the broken state for review/removal; the native update UI remains usable.
            Sites.installed(emptyList())
            runBlocking(Dispatchers.IO){manager.store.commit{it.put("runtimeError",e.message?:"Eklenti seti doğrulanamadı")}}
        }
        client=PluginWorkerClient(context.applicationContext,manager);PluginGateway.remote={site,type,operation,payload->client!!.call(site,type,operation,payload)};prepared=true
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{runCatching{client!!.verifyBoot()}}
    }
    fun register(state:JSONObject){
        val modules=PackageSets.validate(state,state.getJSONObject("packages"),app.filmmerkezi.tv.BuildConfig.VERSION_CODE,Build.VERSION.SDK_INT)
        Sites.installed(PackageSets.activeSites(state,modules));
        state.optJSONArray("knownSites")?.let{a->Sites.remember((0 until a.length()).map{PluginWire.site(a.getJSONObject(it))})}
    }
    fun stop(){client?.stop()}
    fun trim(){client?.trim()}
}
