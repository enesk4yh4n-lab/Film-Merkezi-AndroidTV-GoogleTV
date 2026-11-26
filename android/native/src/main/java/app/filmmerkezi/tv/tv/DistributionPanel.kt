package app.filmmerkezi.tv.tv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.EditText
import androidx.core.content.FileProvider
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.updates.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject

/** All distribution decisions use the existing Classic native windows and DPAD focus. */
internal class DistributionPanel(private val activity:Activity,private val scope:CoroutineScope,private val playing:()->Boolean) {
    private val manager=DistributionManager.get(activity)
    private val installer=PackageInstaller(manager)
    private var panel:ClassicOverlay?=null
    private var observer:Job?=null
    private var download:Job?=null
    private fun s(id:Int)=activity.getString(id)
    private fun error(e:Throwable){ClassicDialog.Builder(activity).setMessage(e.message?:e.javaClass.simpleName).setPositiveButton(android.R.string.ok,null).show()}
    private fun launchWork(block:suspend()->Unit){scope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:Exception){error(e)}}}
    private fun idle():Boolean {if(!playing())return true;error(IllegalStateException(s(R.string.updates_busy_playing)));return false}
    private fun show(title:String):ClassicOverlay {observer?.cancel();panel?.close();return ClassicOverlay(activity,title,side=true,origin=activity.currentFocus).also{panel=it;it.onClosed={observer?.cancel()}}}
    fun open(){val p=show(s(R.string.updates_title));observer=scope.launch{manager.status.collectLatest{v->
        val rows=buildList{
            add(ClassicRow("check",s(R.string.updates_check),enabled=!v.busy))
            v.release?.let{add(ClassicRow("application",s(R.string.updates_application)+" · "+it.name,it.notes))}
            add(ClassicRow("repositories",s(R.string.updates_repositories),manager.repositories().size.toString()))
            add(ClassicRow("installed",s(R.string.updates_installed),manager.store.snapshot().getJSONObject("packages").length().toString()))
            add(ClassicRow("search",s(R.string.updates_search)));add(ClassicRow("search-user",s(R.string.updates_search_user)));add(ClassicRow("address",s(R.string.updates_add_address)))
            if(manager.store.snapshot().has("pending"))add(ClassicRow("apply",s(R.string.updates_apply)))
            if(manager.store.snapshot().has("pending"))add(ClassicRow("cancel-pending",s(R.string.updates_cancel_pending)))
            if(manager.store.snapshot().has("previous"))add(ClassicRow("rollback",s(R.string.updates_previous)))
            if(manager.store.snapshot().has("previous"))add(ClassicRow("forget-previous",s(R.string.updates_forget_previous)))
        }
        p.update(rows,message=v.message+(if(v.busy&&v.bytes>0)" · ${v.bytes/1024} KiB"else "")){key->when(key){
            "check"->if(idle())launchWork{manager.check()}
            "application"->application()
            "repositories"->repositories()
            "installed"->installed()
            "cancel-pending"->if(idle())launchWork{installer.clearPending();open()}
            "forget-previous"->if(idle())ClassicDialog.Builder(activity).setMessage(R.string.updates_forget_previous_confirm).setPositiveButton(android.R.string.ok){_,_->launchWork{manager.store.commit{it.remove("previous")};open()}}.setNegativeButton(R.string.tv_cancel,null).show()
            "address"->prompt(R.string.updates_add_address,R.string.updates_address_hint){url->launchWork{trust(manager.probe(url))}}
            "search","search-user"->prompt(if(key=="search")R.string.updates_search else R.string.updates_search_user,R.string.updates_search_hint){q->launchWork{results(q,key=="search-user",1)}}
            "rollback"->if(idle())launchWork{installer.rollback();open()}
            "apply"->if(idle())launchWork{installer.activate();restart()}
        }}
    }}}
    private fun application(){val p=show(s(R.string.updates_application));observer=scope.launch{manager.status.collectLatest{v->
        p.update(listOf(ClassicRow("download",s(R.string.updates_download),enabled=!v.busy),ClassicRow("install",s(R.string.updates_install),enabled=v.apk!=null&&!v.busy)),message=v.message+"\n"+v.release?.notes.orEmpty()){key->when(key){
            "download"->if(idle())launchWork{manager.downloadApk()}
            "install"->if(idle())installApk()
        }}
    }}}
    private fun installApk(){
        if(Build.VERSION.SDK_INT>=26&&!activity.packageManager.canRequestPackageInstalls()){
            ClassicDialog.Builder(activity).setMessage(R.string.updates_unknown_sources).setPositiveButton(android.R.string.ok){_,_->activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.packageName)))}.setNegativeButton(R.string.tv_cancel,null).show();return
        }
        val file=manager.status.value.apk?:return
        val uri=FileProvider.getUriForFile(activity,activity.packageName+".updates",file)
        activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    private fun prompt(title:Int,hint:Int,submit:(String)->Unit){val input=EditText(activity).apply{setTextColor(ClassicUi.foreground);setHintTextColor(ClassicUi.muted);setSingleLine();setHint(hint)}
        ClassicDialog.Builder(activity).setTitle(title).setView(input).setPositiveButton(R.string.updates_probe){_,_->submit(input.text.toString().trim())}.setNegativeButton(R.string.tv_cancel,null).show()
    }
    private fun trust(probe:RepositoryProbe){ClassicDialog.Builder(activity).setTitle(R.string.updates_probe)
        .setMessage(activity.getString(R.string.updates_trust_message,probe.index.name,probe.fingerprint,probe.index.packages.filter{it.type=="site"&&it.minApp<=manager.installedCode}.map{it.id}.distinct().size))
        .setPositiveButton(R.string.updates_trust){_,_->launchWork{manager.trust(probe);repositories()}}.setNegativeButton(R.string.tv_cancel,null).show()}
    private fun repositories(){val p=show(s(R.string.updates_repositories));val repos=manager.repositories();p.update(repos.map{ClassicRow(it.getString("key"),it.getString("name"),it.getString("url"))}){key->repository(repos.single{it.getString("key")==key})}}
    private fun repository(repo:JSONObject){
        val p=show(repo.getString("name"));val enabled=repo.optBoolean("enabled",true)
        val entries=manager.index(repo).packages.groupBy{it.id}.mapNotNull{(_,rows)->rows.filter{it.minApp<=manager.installedCode}.maxWithOrNull{a,b->UpdateProtocol.compare(a.version,b.version)}}
        val active=manager.store.snapshot().getJSONObject("packages")
        p.update(listOf(
            ClassicRow("refresh",s(R.string.updates_refresh_repository)),
            ClassicRow("enabled",s(R.string.updates_repository_enabled),if(enabled)s(R.string.updates_on)else s(R.string.updates_off)),
            ClassicRow("address",s(R.string.updates_change_address),repo.getString("url")),
            ClassicRow("remove",s(R.string.updates_remove_repository)),
            ClassicRow("all",s(R.string.updates_install_sites),enabled=enabled&&entries.any{it.type=="site"})
        )+entries.map{e->val current=active.optJSONObject(repo.getString("fingerprint")+"/"+e.id)?.let{UpdateProtocol.module(it.getString("manifest").toByteArray()).version};ClassicRow("package:"+e.id,e.name+" · "+e.version,e.type+(current?.let{" · "+s(R.string.updates_installed)+": "+it}?:""),enabled=enabled)},message=if(entries.isEmpty())s(R.string.updates_empty_repository)else ""){key->
            val id=repo.getString("key")
            when(key){
                "refresh"->launchWork{manager.refresh(id);repository(manager.repositories().single{it.getString("key")==id})}
                "enabled"->if(idle())launchWork{manager.setRepositoryEnabled(id,!enabled);repository(manager.repositories().single{it.getString("key")==id})}
                "address"->prompt(R.string.updates_change_address,R.string.updates_address_hint){url->launchWork{manager.changeAddress(id,url);repository(manager.repositories().single{it.getString("key")==id})}}
                "remove"->if(idle())ClassicDialog.Builder(activity).setMessage(R.string.updates_remove_repository).setPositiveButton(android.R.string.ok){_,_->launchWork{manager.removeRepository(id);repositories()}}.setNegativeButton(R.string.tv_cancel,null).show()
                else->if(idle())launchWork{
                    val requested=if(key=="all")entries.filter{it.type=="site"}.map{it.id}else listOf(key.removePrefix("package:"))
                    val plan=mutableMapOf<String,PackageInstaller.Candidate>()
                    for(pluginId in requested)for(c in withContext(Dispatchers.IO){installer.plan(id,pluginId)}){val old=plan[c.identity];require(old==null||old.manifest.version==c.manifest.version){"Bağımlılıklar aynı sürümde uzlaşmıyor"};plan[c.identity]=c}
                    confirmInstall(plan.values.toList())
                }
            }
        }
    }
    private fun confirmInstall(plan:List<PackageInstaller.Candidate>){ClassicDialog.Builder(activity).setTitle(R.string.updates_plan).setMessage(plan.joinToString("\n"){it.manifest.name+" · "+it.manifest.version})
        .setPositiveButton(R.string.updates_download){_,_->
            val p=show(s(R.string.updates_download));p.canDismiss={download?.isActive!=true}
            fun progress(name:String,n:Long){if(panel!==p)return;p.update(emptyList(),actions=listOf(ClassicAction("cancel",s(R.string.tv_cancel)){download?.cancel()}),message="$name · ${n/1024} KiB")}
            download=scope.launch{try{progress(s(R.string.updates_download),0);withContext(Dispatchers.IO){installer.install(plan){name,n->scope.launch{progress(name,n)}}};open()}
                catch(e:CancellationException){open();throw e}catch(e:Exception){open();error(e)}}
        }.setNegativeButton(R.string.tv_cancel,null).show()
    }
    private fun installed(){val p=show(s(R.string.updates_installed));val all=manager.store.snapshot().getJSONObject("packages");val entries=all.keys().asSequence().map{key->key to UpdateProtocol.module(all.getJSONObject(key).getString("manifest").toByteArray())}.toList()
        p.update(entries.map{(key,m)->ClassicRow(key,m.name+" · "+m.version,m.type)}){key->
            val m=entries.single{it.first==key}.second
            ClassicDialog.Builder(activity).setTitle(m.name).setMessage(R.string.updates_remove_package_confirm).setPositiveButton(R.string.updates_remove){_,_->if(idle())launchWork{installer.remove(key);open()}}.setNegativeButton(R.string.tv_cancel,null).show()
        }
    }
    private suspend fun results(query:String,user:Boolean,page:Int){val rows=manager.search(query,user,page);val p=show(s(R.string.updates_search));p.update(rows.map{ClassicRow(it.fullName,it.fullName,it.description)},actions=buildList{
        if(page>1)add(ClassicAction("previous","‹"){launchWork{results(query,user,page-1)}})
        if(rows.size==10&&page<10)add(ClassicAction("next","›"){launchWork{results(query,user,page+1)}})
    }){key->launchWork{trust(manager.probe(manager.address(rows.single{it.fullName==key})))}}}
    internal fun restart(){scope.launch{
        (activity as? TvActivity)?.quiesceForDistribution()
        ModuleRuntime.stop()
        activity.startActivity(Intent(activity,RestartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        activity.finishAffinity();android.os.Process.killProcess(android.os.Process.myPid())
    }}
}
