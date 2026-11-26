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

data class DistributionStatus(val release:UpdateProtocol.Release?=null,val busy:Boolean=false,val message:String="",val bytes:Long=0,val apk:File?=null,val checkedAt:Long=0,val moduleUpdates:Int=0)
data class RepositoryProbe(val url:String,val raw:ByteArray,val signature:String,val index:UpdateProtocol.Index){val fingerprint get()=UpdateProtocol.fingerprint(index.publicKey)}
data class RepositorySearchResult(val fullName:String,val branch:String,val description:String)

class DistributionManager private constructor(private val context:Context) {
    val store=DistributionStore(context)
    val transport=UpdateTransport(context)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val operation=Mutex()
    val installedCode=context.packageManager.getPackageInfo(context.packageName,0).versionCode
    private val state=MutableStateFlow(DistributionStatus(message=store.failure?:store.snapshot().optString("runtimeError")))
    val status:StateFlow<DistributionStatus> = state.asStateFlow()
    private val key=context.assets.open("update-public-key.txt").bufferedReader().use{it.readText().trim()}
    companion object {
        const val APP_ROOT="https://raw.githubusercontent.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/main/"
        private var singleton:DistributionManager?=null
        fun get(context:Context)=synchronized(this){singleton?:DistributionManager(context.applicationContext).also{singleton=it}}
    }
    fun foreground(playing:Boolean){if(!playing&&!state.value.busy&&System.currentTimeMillis()-state.value.checkedAt>=1800000)scope.launch{check()}}
    suspend fun check()=operation.withLock{
        state.value=state.value.copy(busy=true,message="Güncellemeler kontrol ediliyor")
        try{
            val bytes=transport.bytes(APP_ROOT+"update.json");val sig=transport.bytes(APP_ROOT+"update.sig",2048).toString(Charsets.US_ASCII);UpdateProtocol.verify(bytes,sig,key)
            val release=UpdateProtocol.release(bytes);val hash=UpdateProtocol.sha(bytes);val previous=store.snapshot()
            require(release.code>=previous.optInt("appHighestCode",0)){"Eski uygulama bildirimi reddedildi"}
            require(release.code!=previous.optInt("appHighestCode",0)||previous.optString("appFeedHash")==hash){"Aynı sürümde farklı bildirim reddedildi"}
            store.commit{it.put("appHighestCode",release.code).put("appFeedHash",hash)}
            state.value=state.value.copy(release=release.takeIf{it.code>installedCode},message=if(release.code>installedCode)"Yeni uygulama: ${release.name}"else"Uygulama güncel")
        }catch(e:CancellationException){state.value=state.value.copy(busy=false);throw e}catch(e:Exception){state.value=state.value.copy(message=if(e.message=="HTTP 404")"Henüz kararlı uygulama bildirimi yayımlanmadı"else e.message?:"Güncelleme kontrolü başarısız")}
        finally{state.value=state.value.copy(checkedAt=System.currentTimeMillis())}
        try{
            val failures=mutableListOf<String>()
            for(repo in repositories().filter{it.optBoolean("enabled",true)})try{refresh(repo.getString("key"))}catch(e:CancellationException){throw e}catch(e:Exception){failures.add(repo.getString("name")+": "+e.message)}
            state.value=state.value.copy(moduleUpdates=availableUpdates(),message=state.value.message+(if(failures.isEmpty())""else"\n"+failures.joinToString("\n")))
        }finally{state.value=state.value.copy(busy=false)}
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
        val url=UpdateTransport.repository(value);val raw=transport.bytes(url,UpdateProtocol.INDEX_LIMIT);val index=UpdateProtocol.index(raw)
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
    suspend fun refresh(repoKey:String){val old=repositories().single{it.getString("key")==repoKey};val p=probe(old.getString("url"));require(p.fingerprint==old.getString("fingerprint")){"Yayıncı anahtarı değişti; yeni anahtara otomatik güvenilmedi"};trust(p)}
    fun index(repo:JSONObject):UpdateProtocol.Index {val raw=repo.getString("index").toByteArray(Charsets.UTF_8);UpdateProtocol.verify(raw,repo.getString("signature"),repo.getString("publicKey"));return UpdateProtocol.index(raw)}
    suspend fun search(text:String,user:Boolean=false,page:Int=1):List<RepositorySearchResult> {
        require(text.length in 1..80&&!text.any{it<' '||it=='"'||it=='\\'});require(!user||text.matches(Regex("[A-Za-z0-9-]{1,39}")));require(page in 1..10)
        val query=(if(user)"user:$text"else text.replace(Regex("[:<>]")," "))+" \"${UpdateProtocol.INDEX}\" in:readme"
        val url="https://api.github.com/search/repositories?q="+java.net.URLEncoder.encode(query,"UTF-8")+"&per_page=10&page=$page"
        val o=JSONObject(transport.bytes(url,262144).toString(Charsets.UTF_8));val a=o.getJSONArray("items")
        return (0 until minOf(a.length(),10)).mapNotNull{i->val r=a.getJSONObject(i);if(r.optBoolean("private"))null else RepositorySearchResult(r.getString("full_name"),r.getString("default_branch"),r.optString("description").take(200))}
    }
    suspend fun address(result:RepositorySearchResult):String {
        require(result.fullName.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")));require(result.branch.matches(Regex("[A-Za-z0-9_./-]{1,100}"))&&!result.branch.contains(".."))
        val u="https://api.github.com/repos/${result.fullName}/readme?ref="+java.net.URLEncoder.encode(result.branch,"UTF-8")
        val o=JSONObject(transport.bytes(u,262144).toString(Charsets.UTF_8));require(o.getString("encoding")=="base64")
        val readme=java.util.Base64.getMimeDecoder().decode(o.getString("content")).toString(Charsets.UTF_8)
        val path=Regex("<!--\\s*film-merkezi-android-repository:\\s*([A-Za-z0-9_./-]+)\\s*-->").find(readme)?.groupValues?.get(1)?:UpdateProtocol.INDEX
        UpdateProtocol.path(path);require(path.substringAfterLast('/')==UpdateProtocol.INDEX)
        return UpdateTransport.repository("https://raw.githubusercontent.com/${result.fullName}/${result.branch}/$path")
    }
}
