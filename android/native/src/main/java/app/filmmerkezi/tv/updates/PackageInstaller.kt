package app.filmmerkezi.tv.updates

import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.io.File
import java.util.zip.ZipFile

/** A downloaded set is staged atomically. Existing/previous sets stay referenced until acceptance. */
class PackageInstaller(private val manager:DistributionManager) {
    companion object{private val mutex=Mutex()}
    data class Candidate(val repo:JSONObject,val manifest:UpdateProtocol.Module,val raw:ByteArray,val signature:String,val replaces:List<String> = emptyList()){val identity get()=repo.getString("fingerprint")+"/"+manifest.id}
    private fun candidates()=manager.repositories().filter{it.optBoolean("enabled",true)}.flatMap{r->manager.index(r).packages.map{r to it}}
    private suspend fun manifest(repo:JSONObject,e:UpdateProtocol.Entry):Candidate {
        val base=repo.getString("url").substringBeforeLast('/')+"/"
        val raw=manager.transport.bytes(base+e.manifest);require(UpdateProtocol.sha(raw)==e.hash){"Manifest özeti eşleşmiyor"}
        val sig=manager.transport.bytes(base+e.manifest.removeSuffix(".json")+".sig",2048).toString(Charsets.US_ASCII)
        UpdateProtocol.verify(raw,sig,repo.getString("publicKey"));val m=UpdateProtocol.module(raw)
        require(m.id==e.id&&m.version==e.version&&m.type==e.type&&m.minApp==e.minApp){"Depo kaydı ve manifest uyuşmuyor"}
        require(m.minApp<=manager.installedCode&&m.minSdk<=Build.VERSION.SDK_INT){"Bu eklenti için daha yeni uygulama/Android gerekiyor"}
        return Candidate(repo,m,raw,sig)
    }
    suspend fun plan(repoKey:String,pluginId:String):List<Candidate> {
        val all=candidates();val cache=mutableMapOf<String,Candidate>();var steps=0
        suspend fun read(p:Pair<JSONObject,UpdateProtocol.Entry>):Candidate {val key=p.first.getString("key")+"/"+p.second.id+"@"+p.second.version;return cache[key]?:manifest(p.first,p.second).also{cache[key]=it}}
        val roots=all.filter{it.first.getString("key")==repoKey&&it.second.id==pluginId&&it.second.minApp<=manager.installedCode}.sortedWith{a,b->UpdateProtocol.compare(b.second.version,a.second.version)}
        require(roots.isNotEmpty()){"Uyumlu paket yok; uygulamayı güncelleyin"}
        val snapshot=manager.store.snapshot();val existing=snapshot.optJSONObject("pending")?:snapshot.getJSONObject("packages")
        data class Need(val publisher:String,val id:String,val min:String,val max:String,val exact:Candidate?=null)
        suspend fun solve(todo:List<Need>,chosen:Map<String,Candidate>,visiting:Set<String>):Map<String,Candidate>? {
            check(++steps<=8192){"Bağımlılık çözümleme sınırı"};if(todo.isEmpty())return chosen
            val n=todo.first();val identity=n.publisher+"/"+n.id
            if(identity in visiting)return null
            val selected=chosen[identity];if(selected!=null)return if(n.exact?.manifest?.version?.let{it==selected.manifest.version}?:UpdateProtocol.Dependency(n.publisher,n.id,n.min,n.max).accepts(selected.manifest.version))solve(todo.drop(1),chosen,visiting)else null
            val options=if(n.exact!=null)listOf(n.exact)else all.filter{it.first.getString("fingerprint")==n.publisher&&it.second.id==n.id&&it.second.type=="resolver"&&it.second.minApp<=manager.installedCode&&UpdateProtocol.Dependency(n.publisher,n.id,n.min,n.max).accepts(it.second.version)}.sortedWith{a,b->UpdateProtocol.compare(b.second.version,a.second.version)}.map{read(it)}
            for(c in options){
                val deps=c.manifest.dependencies.map{Need(it.publisher,it.id,it.min,it.max)}
                // The selected map catches incompatible diamonds; validate cycles separately after solving.
                val found=solve(deps+todo.drop(1),chosen+(identity to c),visiting)
                if(found!=null)return found
            };return null
        }
        val root=read(roots.first());val chosen=solve(listOf(Need(root.repo.getString("fingerprint"),root.manifest.id,"0.0.0","9999.9999.9999",root)),emptyMap(),emptySet())?:error("Uyumlu bağımlılık takımı bulunamadı")
        val replacements=PackageSets.replacedProviders(existing,chosen.mapValues{it.value.manifest});val removed=replacements.values.flatten().toSet()
        val order=mutableListOf<Candidate>();val visiting=mutableSetOf<String>();val visited=mutableSetOf<String>()
        fun visit(c:Candidate){if(c.identity in visited)return;require(visiting.add(c.identity)){"Bağımlılık döngüsü"};c.manifest.dependencies.forEach{visit(chosen.getValue(it.publisher+"/"+it.id))};visiting.remove(c.identity);visited.add(c.identity);order.add(c)};chosen.values.forEach(::visit)
        for(key in existing.keys())if(key !in chosen&&key !in removed){val m=UpdateProtocol.module(existing.getJSONObject(key).getString("manifest").toByteArray(Charsets.UTF_8));for(d in m.dependencies){val next=chosen[d.publisher+"/"+d.id];require(next==null||d.accepts(next.manifest.version)){"Kurulu ${m.name} başka çözümleyici sürümü gerektiriyor"}}}
        for(c in chosen.values){existing.optJSONObject(c.identity)?.let{old->val previous=UpdateProtocol.module(old.getString("manifest").toByteArray());require(UpdateProtocol.compare(c.manifest.version,previous.version)>=0){"Normal guncelleme daha eski surume donemez; onceki paket setini kullanin"}}}
        require((existing.keys().asSequence().toSet()-removed+chosen.keys).size<=64){"64 kurulu paket sınırı"};return order.map{it.copy(replaces=replacements[it.identity].orEmpty())}
    }
    suspend fun install(plan:List<Candidate>,progress:(String,Long)->Unit={_,_->},onState:(InstallProgress)->Unit={})=mutex.withLock{
        require(plan.isNotEmpty());val total=plan.sumOf{it.manifest.size.toLong()};var completed=0L
        collectGarbage()
        val objects=File(manager.store.directory,"objects").apply{mkdirs()};val state=manager.store.snapshot();val staged=JSONObject((state.optJSONObject("pending")?:state.getJSONObject("packages")).toString())
        val replacements=PackageSets.replacedProviders(staged,plan.associate{it.identity to it.manifest})
        for(c in plan){require(c.replaces.toSet()==replacements[c.identity].orEmpty().toSet()){"Kurulum planı değişti; yeniden seçin."};c.replaces.forEach{staged.remove(it)}}
        for(c in plan){val m=c.manifest;val target=File(objects,m.hash+".jar")
            if(!target.exists()||target.length()!=m.size.toLong()||UpdateProtocol.sha(target.readBytes())!=m.hash){
                val used=objects.listFiles().orEmpty().sumOf{it.length()};require(used+m.size<=64L*1024*1024){"Eklenti alanı dolu; önce kullanılmayan paketleri kaldırın"}
                val temporary=File(objects,"download-${m.hash}.jar")
                try{onState(InstallProgress(InstallPhase.DOWNLOADING,m.name,completed,total));manager.transport.download(c.repo.getString("url").substringBeforeLast('/')+"/"+m.artifact,temporary,m.size,m.hash){progress(m.name,it);onState(InstallProgress(InstallPhase.DOWNLOADING,m.name,completed+it,total))};onState(InstallProgress(InstallPhase.VERIFYING,m.name,completed+m.size,total));validateJar(temporary);require(temporary.renameTo(target)){"Doğrulanan paket yerleştirilemedi"}}finally{temporary.delete()}
            }
            currentCoroutineContext().ensureActive();onState(InstallProgress(InstallPhase.VERIFYING,m.name,completed+m.size,total))
            validateJar(target);require(target.setReadOnly()){"Paket salt okunur yapılamadı"}
            staged.put(c.identity,JSONObject().put("repo",c.repo.getString("key")).put("manifest",c.raw.toString(Charsets.UTF_8)).put("signature",c.signature).put("artifact",m.hash).put("enabled",true))
            completed+=m.size
        }
        PackageSets.validate(state,staged,manager.installedCode,Build.VERSION.SDK_INT)
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable){manager.store.commit{current->require(current.getJSONObject("packages").toString()==state.getJSONObject("packages").toString()){ "Kurulum sirasinda paket seti degisti; tekrar deneyin" };require(current.optJSONObject("pending")?.toString()==state.optJSONObject("pending")?.toString()){ "Hazir paket seti degisti; tekrar deneyin" };PackageSets.validate(current,staged,manager.installedCode,Build.VERSION.SDK_INT);current.put("pending",staged)};onState(InstallProgress(InstallPhase.READY,"",total,total))}
    }
    fun validateJar(file:File){
        ZipFile(file).use{zip->val entries=zip.entries().asSequence().toList();require(entries.size in 1..64&&entries.any{it.name=="classes.dex"}){"Geçerli Kotlin/DEX paketi değil"}
            var total=0L;for(e in entries){UpdateProtocol.path(e.name.trimEnd('/'));require(!e.name.endsWith(".so")&&!e.name.startsWith("lib/")){"Native ikili eklentiyle kurulamaz"};if(e.isDirectory)continue
                zip.getInputStream(e).use{input->val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=8L*1024*1024){"Açılmış paket boyut sınırı"}}}}
        }
    }
    suspend fun activate(){manager.store.commit{s->val pending=s.optJSONObject("pending")?:error("Hazir paket yok");PackageSets.validate(s,pending,manager.installedCode,Build.VERSION.SDK_INT);PackageSets.rememberSites(s,s.getJSONObject("packages"),pending);s.put("previous",s.getJSONObject("packages")).put("packages",pending).put("bootPending",false).put("needsBoot",true);s.remove("pending")}}
    fun removalPlan(identity:String):PluginRemoval {val state=manager.store.snapshot();return PluginRemovals.plan(state.optJSONObject("pending")?:state.getJSONObject("packages"),identity)}
    suspend fun remove(identity:String)=remove(removalPlan(identity),false)
    suspend fun remove(plan:PluginRemoval,includeUnused:Boolean)=mutex.withLock{manager.store.commit{s->
        val next=PluginRemovals.apply(s.optJSONObject("pending")?:s.getJSONObject("packages"),plan,includeUnused)
        PackageSets.validate(s,next,manager.installedCode,Build.VERSION.SDK_INT);s.put("pending",next)
    }}
    suspend fun clearPending(){manager.store.commit{it.remove("pending")}}
    fun collectGarbage(){
        val state=manager.store.snapshot();val referenced=mutableSetOf<String>()
        for(name in listOf("packages","pending","previous")){val set=state.optJSONObject(name)?:continue;set.keys().forEach{referenced.add(set.getJSONObject(it).getString("artifact")+".jar")}}
        File(manager.store.directory,"objects").listFiles().orEmpty().filter{it.isFile&&it.name !in referenced&&(it.name.matches(Regex("[a-f0-9]{64}\\.jar"))||it.name.matches(Regex("download-[a-f0-9]{64}\\.jar")))}.forEach{require(it.delete()){"Kullanılmayan paket temizlenemedi"}}
    }
    suspend fun cleanup():Long=mutex.withLock{val before=File(manager.store.directory,"objects").listFiles().orEmpty().sumOf{it.length()};collectGarbage();before-File(manager.store.directory,"objects").listFiles().orEmpty().sumOf{it.length()}}
    suspend fun accept(){manager.store.commit{it.put("bootPending",false)}}
    suspend fun recover(){manager.store.commit{s->if(s.optBoolean("bootPending")){s.put("packages",s.optJSONObject("previous")?:JSONObject()).put("bootPending",false).put("recovered",true);s.remove("needsBoot")}}}
    suspend fun rollback(){manager.store.commit{s->val previous=s.optJSONObject("previous")?:error("Onceki paket seti yok");PackageSets.validate(s,previous,manager.installedCode,Build.VERSION.SDK_INT);s.put("pending",previous)}}
}
