package app.filmmerkezi.tv.updates

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.io.File

data class DistributionStatus(val release:UpdateProtocol.Release?=null,val busy:Boolean=false,val message:String="",val bytes:Long=0,val apk:File?=null,val checkedAt:Long=0,val moduleUpdates:Int=0,
    val appCheck:CheckState=CheckState.UNCHECKED,val appError:String="",val repositoryChecks:Map<String,RepositoryCheck> = emptyMap(),val pluginError:String="")
data class RepositoryProbe(val url:String,val raw:ByteArray,val signature:String,val index:UpdateProtocol.Index){val fingerprint get()=UpdateProtocol.fingerprint(index.publicKey)}
class DistributionManager private constructor(private val context:Context) {
    val store=DistributionStore(context)
    val transport=UpdateTransport(context)
    val notices=UpdateNoticeStore(context)
    private val discovery=RepositoryDiscovery({url,limit->transport.bytes(url,limit,10000)})
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val installRun=MutableStateFlow(PluginInstallRun())
    internal val pluginInstall=installRun.asStateFlow()
    @Volatile private var unrecordedInstall:List<PackageInstaller.Candidate>?=null
    private val operation=Mutex()
    private var foregroundJob:Job?=null
    @Volatile private var inventoryCache:Pair<Long,PluginInventory>?=null
    @Volatile private var catalogCache:Pair<Long,PluginCatalog>?=null
    val installedCode=context.packageManager.getPackageInfo(context.packageName,0).versionCode
    private val state=MutableStateFlow(DistributionStatus(message=store.failure?:store.snapshot().optString("runtimeError")))
    @Volatile var lastIssue:String="";private set
    fun recordIssue(error:Throwable){lastIssue=(error.message?:error.javaClass.simpleName).take(2000)}
    @Synchronized internal fun startPluginInstall(plan:List<PackageInstaller.Candidate>?=null) {
        if(installRun.value.busy)return
        val toRecord=plan?:unrecordedInstall
        unrecordedInstall=toRecord
        val planned=toRecord?.count{it.manifest.type=="site"}?:0
        installRun.value=PluginInstallRun(busy=true,plannedProviders=planned)
        scope.launch{
            val installer=PackageInstaller(this@DistributionManager)
            var failure:UpdateProblem?=null
            try{
                if(toRecord!=null){installer.beginJob(toRecord);unrecordedInstall=null}
                installer.downloadJob{step->installRun.update{it.copy(step=step)}}
            }catch(e:CancellationException){throw e}catch(e:Exception){recordIssue(e);failure=UpdateProblem.classify(e)}
            finally{installRun.value=PluginInstallRun(plannedProviders=if(unrecordedInstall==null)0 else planned,failure=failure)}
        }
    }
    val status:StateFlow<DistributionStatus> = state.asStateFlow()
    fun readyApk():File?=state.value.release?.let{release->state.value.apk?.takeIf{it.name=="application-${release.code}.apk"&&it.exists()}}
    private val key=context.assets.open("update-public-key.txt").bufferedReader().use{it.readText().trim()}
    companion object {
        const val APP_ROOT="https://raw.githubusercontent.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/main/"
        private var singleton:DistributionManager?=null
        fun get(context:Context)=synchronized(this){singleton?:DistributionManager(context.applicationContext).also{singleton=it}}
    }
    @Synchronized fun foreground(playing:Boolean){if(!playing&&!state.value.busy&&foregroundJob?.isActive!=true&&System.currentTimeMillis()-state.value.checkedAt>=1800000)foregroundJob=scope.launch{
        try{check()}catch(e:CancellationException){throw e}catch(e:Exception){recordIssue(e);state.update{it.copy(busy=false,pluginError=e.message.orEmpty())}}
    }}
    suspend fun check()=operation.withLock{
        DistributionChecks.run(state.value,installedCode,repositories().filter{it.optBoolean("enabled",true)}.map{RepositoryCheck(it.getString("key"),it.getString("name"))},application={
            val bytes=transport.bytes(APP_ROOT+"update.json");val sig=transport.bytes(APP_ROOT+"update.sig",2048).toString(Charsets.US_ASCII);UpdateProtocol.verify(bytes,sig,key)
            val release=UpdateProtocol.release(bytes);val hash=UpdateProtocol.sha(bytes);val previous=store.snapshot()
            require(release.code>=previous.optInt("appHighestCode",0)){"Eski uygulama bildirimi reddedildi"}
            require(release.code!=previous.optInt("appHighestCode",0)||previous.optString("appFeedHash")==hash){"Aynı sürümde farklı bildirim reddedildi"}
            store.commit{it.put("appHighestCode",release.code).put("appFeedHash",hash)}
            release
        },refresh={refresh(it)},count={availableUpdates()},emit={state.value=it})
    }
    suspend fun downloadApk()=operation.withLock{
        val release=checkNotNull(state.value.release);require(Build.VERSION.SDK_INT>=release.minSdk){"Bu Android sürümü yeni APK'yı desteklemiyor"}
        val file=File(store.directory,"application-${release.code}.apk");state.value=state.value.copy(busy=true,message="APK indiriliyor",bytes=0)
        try{
            transport.download(release.url,file,release.size,release.hash){n->state.value=state.value.copy(bytes=n)}
            val info=context.packageManager.getPackageArchiveInfo(file.path,PackageManager.GET_SIGNATURES)?:error("APK okunamadı")
            require(info.packageName==context.packageName&&info.versionCode==release.code){"APK kimliği/sürümü eşleşmiyor"}
            require(info.signatures?.any{UpdateProtocol.sha(it.toByteArray())==release.certificate}==true){"APK imzası yayın bildirimiyle eşleşmiyor"}
            val own=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNATURES).signatures.orEmpty().map{UpdateProtocol.sha(it.toByteArray())}
            require(release.certificate in own){"Bu deneme kurulumundan kalıcı imzaya geçiş gerekiyor. Önce kayıtları dışa aktarın; uygulamayı kaldırarak devam etmeyin."}
            state.value=state.value.copy(apk=file,message="APK doğrulandı; kurulum için hazır")
        }catch(e:CancellationException){throw e}catch(e:Exception){file.delete();state.value=state.value.copy(message=e.message?:"İndirme başarısız",apk=null)}
        finally{state.value=state.value.copy(busy=false)}
    }
    fun repositories():List<JSONObject>{val o=store.snapshot().getJSONObject("repositories");return o.keys().asSequence().map{o.getJSONObject(it)}.toList()}
    fun inventory():PluginInventory {val revision=store.revision.value;inventoryCache?.takeIf{it.first==revision}?.let{return it.second}
        return PluginInventory(store.snapshot(),repositories().associate{it.getString("key") to index(it)},installedCode).also{inventoryCache=revision to it}}
    fun catalog():PluginCatalog {
        val revision=store.revision.value;catalogCache?.takeIf{it.first==revision}?.let{return it.second}
        val snapshot=store.snapshot();val all=snapshot.getJSONObject("repositories");val repos=all.keys().asSequence().map{all.getJSONObject(it)}.toList()
        val errors=linkedMapOf<String,String>();val indices=repos.mapNotNull{repo->val id=repo.getString("key");try{id to index(repo)}catch(e:Exception){errors[id]=e.message?:"Depo okunamadı";null}}.toMap()
        return PluginCatalog(snapshot,repos,indices,installedCode,errors).also{catalogCache=revision to it}
    }
    fun availableUpdates():Int {
        val installed=store.snapshot().getJSONObject("packages")
        return repositories().filter{it.optBoolean("enabled",true)}.sumOf{repo->index(repo).packages.groupBy{it.id}.count{(id,rows)->
            val current=installed.optJSONObject(repo.getString("fingerprint")+"/"+id)?.let{UpdateProtocol.module(it.getString("manifest").toByteArray())}
            current!=null&&rows.any{it.minApp<=installedCode&&UpdateProtocol.compare(it.version,current.version)>0}
        }}
    }
    suspend fun setRepositoryEnabled(key:String,enabled:Boolean){store.commit{it.getJSONObject("repositories").getJSONObject(key).put("enabled",enabled)}}
    suspend fun changeAddress(key:String,address:String){
        val old=repositories().single{it.getString("key")==key};val next=probe(address)
        require(next.fingerprint==old.getString("fingerprint")&&next.index.id==old.getString("id")){"Adres aynı yayıncı ve depo kimliğine ait olmalı"};trust(next)
    }
    suspend fun removeRepository(key:String){store.commit{s->
        for(name in listOf("packages","pending","previous")){val set=s.optJSONObject(name)?:continue;require(set.keys().asSequence().none{set.getJSONObject(it).getString("repo")==key}){"Depo kurulu, hazır veya geri dönüş paketlerinde kullanılıyor. Önce bu paketleri kaldırın ve geri dönüş setini temizleyin."}}
        s.getJSONObject("repositories").remove(key)
    }}
    suspend fun probe(value:String):RepositoryProbe {
        val url=discovery.repository(value);val raw=transport.bytes(url,UpdateProtocol.INDEX_LIMIT);val index=UpdateProtocol.index(raw)
        val signature=transport.bytes(url.removeSuffix(".json")+".sig",2048).toString(Charsets.US_ASCII);UpdateProtocol.verify(raw,signature,index.publicKey)
        return RepositoryProbe(url,raw,signature,index)
    }
    suspend fun trust(probe:RepositoryProbe){
        UpdateProtocol.verify(probe.raw,probe.signature,probe.index.publicKey)
        val fp=probe.fingerprint;val id=probe.index.id;val repoKey="$fp/$id"
        store.commit{s->val repos=s.getJSONObject("repositories");val old=repos.optJSONObject(repoKey)
            require(repos.length()<16||old!=null){"16 depo sınırına ulaşıldı"}
            require(old==null||old.getString("id")==id){"Yayıncı depo kimliği değişti"}
            require(old==null||probe.index.revision>=old.getInt("revision")){"Eski depo revizyonu reddedildi"}
            if(old!=null&&probe.index.revision==old.getInt("revision"))require(old.getString("indexHash")==UpdateProtocol.sha(probe.raw)){"Depo revizyonu çelişiyor"}
            val versions=old?.optJSONObject("versionHashes")?:JSONObject()
            old?.let{index(it).packages.forEach{e->versions.put(e.id+"@"+e.version,e.hash)}}
            probe.index.packages.forEach{e->val versionKey=e.id+"@"+e.version;require(!versions.has(versionKey)||versions.getString(versionKey)==e.hash){"Aynı paket sürümünün içeriği değiştirilemez"};versions.put(versionKey,e.hash)}
            repos.put(repoKey,JSONObject().put("key",repoKey).put("id",id).put("name",probe.index.name).put("fingerprint",fp).put("publicKey",probe.index.publicKey).put("url",probe.url).put("enabled",old?.optBoolean("enabled",true)?:true).put("revision",probe.index.revision).put("indexHash",UpdateProtocol.sha(probe.raw)).put("index",probe.raw.toString(Charsets.UTF_8)).put("signature",probe.signature).put("versionHashes",versions))
        }
    }
    suspend fun refresh(repoKey:String){val old=repositories().single{it.getString("key")==repoKey};val checking=RepositoryCheck(repoKey,old.getString("name"),CheckState.CHECKING)
        state.update{it.copy(repositoryChecks=it.repositoryChecks+(repoKey to checking))}
        try{val p=probe(old.getString("url"));require(p.fingerprint==old.getString("fingerprint")){"Yayıncı anahtarı değişti; yeni anahtara otomatik güvenilmedi"};trust(p)
            state.update{it.copy(repositoryChecks=it.repositoryChecks+(repoKey to checking.copy(state=CheckState.CURRENT)))}}
        catch(e:CancellationException){state.update{it.copy(repositoryChecks=it.repositoryChecks+(repoKey to checking.copy(state=CheckState.CANCELLED)))};throw e}
        catch(e:Exception){state.update{it.copy(repositoryChecks=it.repositoryChecks+(repoKey to checking.copy(state=CheckState.FAILED,error=e.message.orEmpty())))};throw e}
    }
    fun index(repo:JSONObject):UpdateProtocol.Index {val raw=repo.getString("index").toByteArray(Charsets.UTF_8);UpdateProtocol.verify(raw,repo.getString("signature"),repo.getString("publicKey"));return UpdateProtocol.index(raw)}
    suspend fun search(text:String="",user:Boolean=false,page:Int=1,refresh:Boolean=false)=discovery.search(text,user,page,refresh)
    suspend fun address(result:RepositorySearchResult)=discovery.address(result)
}
