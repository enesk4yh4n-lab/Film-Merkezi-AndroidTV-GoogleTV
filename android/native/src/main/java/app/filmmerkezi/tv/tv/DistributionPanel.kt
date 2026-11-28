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
import kotlinx.coroutines.flow.combine
import org.json.JSONObject

/** Single update flow embedded in Options. Dialogs are limited to input and confirmation. */
internal class DistributionPanel(private val activity:Activity,parentScope:CoroutineScope,private val playing:()->Boolean,left:()->Unit={},up:()->Unit=left) {
    private val lifetime=SupervisorJob(parentScope.coroutineContext[Job])
    private val scope=CoroutineScope(parentScope.coroutineContext+lifetime)
    private val manager=DistributionManager.get(activity)
    private val installer=PackageInstaller(manager)
    val view=UpdateCenterView(activity,left,up)
    private var returnPage:(()->Unit)?=null
    private var generation=0
    private var observer:Job?=null
    private var download:Job?=null
    private var work:Job?=null
    private var dialog:ClassicDialog?=null
    private val selected=linkedSetOf<String>()
    private var noticePopup:UpdateNoticePopup?=null
    private fun s(id:Int)=activity.getString(id)
    private fun display(builder:ClassicDialog.Builder){dialog?.dismiss();dialog=builder.show()}
    private fun problemText(e:Throwable)=if(e is UpdateHttpFailure&&e.rateLimited)e.message else s(when(UpdateProblem.classify(e)){
        UpdateProblem.NETWORK->R.string.updates_error_network
        UpdateProblem.TIMEOUT->R.string.updates_error_timeout
        UpdateProblem.INTEGRITY->R.string.updates_error_integrity
        UpdateProblem.STORAGE->R.string.updates_error_storage
        UpdateProblem.CONFLICT->R.string.updates_error_conflict
        UpdateProblem.UNSUPPORTED->R.string.updates_error_unsupported
        UpdateProblem.ADDRESS->R.string.updates_error_address
        UpdateProblem.OTHER->R.string.updates_error_other
    })
    private fun notice(message:String){display(ClassicDialog.Builder(activity).setMessage(message).setPositiveButton(android.R.string.ok,null))}
    private fun error(e:Throwable){manager.recordIssue(e);display(ClassicDialog.Builder(activity).setTitle(R.string.updates_operation_failed).setMessage(problemText(e))
        .setPositiveButton(android.R.string.ok,null).setNeutralButton(R.string.updates_diagnostics){_,_->diagnostics()})}
    private fun launchWork(block:suspend()->Unit){if(work?.isActive==true)return;work=scope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:Exception){error(e)}}}
    private fun inventoryPage(p:UpdateCenterView,render:(PluginInventory)->Unit){val token=generation;p.loading(true);p.update(emptyList());work=scope.launch{
        try{val value=withContext(Dispatchers.IO){manager.inventory()};if(token==generation){p.loading(false);render(value)}}catch(e:CancellationException){throw e}catch(e:Exception){if(token==generation){p.loading(false);error(e)}}}}
    private fun idle():Boolean {if(!playing())return true;notice(s(R.string.updates_busy_playing));return false}
    private fun show(title:String,parent:(()->Unit)?=null,key:String=""):UpdateCenterView {
        observer?.cancel();generation++;returnPage=parent?.let{{it();view.requestKey(key)}}
        view.page(title,parent!=null);view.onBack={back()};return view
    }
    fun canLeave()=download?.isActive!=true
    fun back():Boolean {
        if(dialog?.isShowing==true){dialog?.dismiss();return true}
        if(!canLeave()){display(ClassicDialog.Builder(activity).setMessage(R.string.updates_cancel_download_confirm).setPositiveButton(R.string.tv_cancel){_,_->download?.cancel()}.setNegativeButton(R.string.updates_keep_downloading,null));return true}
        work?.cancel();work=null
        returnPage?.let{it();return true};return false
    }
    fun dispose(){generation++;noticePopup?.close();dialog?.dismiss();lifetime.cancel()}
    internal fun dialogForTest()=dialog
    private fun applicationStatus(v:DistributionStatus):String=when(v.appCheck){
        CheckState.UNCHECKED->s(R.string.updates_app_unchecked)
        CheckState.CHECKING->s(R.string.updates_app_checking)
        CheckState.CURRENT->s(R.string.updates_app_current)
        CheckState.AVAILABLE->activity.getString(R.string.updates_version_available,v.release?.name.orEmpty())
        CheckState.FAILED->s(R.string.updates_app_failed)
        CheckState.CANCELLED->s(R.string.updates_check_cancelled)
    }
    private fun repositoryStatus(v:DistributionStatus,enabled:Set<String>):String {
        val checks=v.repositoryChecks.values.filter{it.key in enabled};val failed=checks.count{it.state==CheckState.FAILED};val checked=checks.count{it.state==CheckState.CURRENT}
        return when {checks.any{it.state==CheckState.CHECKING}->s(R.string.updates_repos_checking)
            failed>0->activity.getString(R.string.updates_repos_partial,checked,failed)
            checks.isNotEmpty()&&checks.all{it.state==CheckState.CURRENT}->activity.getString(R.string.updates_repos_checked,checked)
            else->s(R.string.updates_repos_unchecked)}
    }
    fun open(){val p=show(s(R.string.updates_title));p.loading(true);observer=scope.launch{combine(manager.status,manager.store.revision){v,_->v}.collectLatest{v->
        val catalog=withContext(Dispatchers.IO){try{manager.catalog()}catch(e:CancellationException){throw e}catch(e:Exception){manager.recordIssue(e);null}}
        p.loading(false)
        val rows=buildList{
            add(ClassicRow("add",s(R.string.hub_catalog),catalog?.let{activity.getString(R.string.hub_catalog_count,it.availableCount,it.fresh.size)}?:s(R.string.updates_inventory_failed)))
            add(ClassicRow("installed",s(R.string.hub_installed)+(catalog?.let{" · "+it.installedCount}?:""),if(catalog==null)s(R.string.updates_inventory_failed)else s(R.string.hub_installed_help)))
            add(ClassicRow("updates",s(R.string.hub_updates),applicationStatus(v)+" · "+(catalog?.let{activity.getString(R.string.hub_update_count,it.updates.size)}?:s(R.string.updates_inventory_failed))))
            add(ClassicRow("management",s(R.string.hub_management),header=true))
            add(ClassicRow("repositories",s(R.string.updates_repositories)+(catalog?.let{" · "+it.repositoryCount}?:""),repositoryStatus(v,catalog?.enabledRepositories.orEmpty())))
            if(catalog?.pending==true)add(ClassicRow("apply",s(R.string.hub_finish_install),s(R.string.updates_ready_help)))
            add(ClassicRow("technical",s(R.string.hub_advanced)))
        }
        p.update(rows,message=catalog?.let{summary(it)}?:s(R.string.updates_inventory_failed)){key->when(key){
            "updates"->updates()
            "repositories"->repositories()
            "installed"->installed()
            "add"->addPlugins()
            "technical"->technical()
            "apply"->if(idle())ready()
        }}
    }}}
    fun navigate(route:UpdateRoute){if(route.install.isNotEmpty()){addPlugins();installKeys(route.install);return}
        when(route.page){UpdatePage.HOME->open();UpdatePage.CATALOG->addPlugins();UpdatePage.INSTALLED->installed();UpdatePage.UPDATES->updates();UpdatePage.APPLICATION->{application();if(route.downloadApplication&&manager.status.value.release?.code==route.applicationCode)updateApplication()};UpdatePage.REPOSITORIES->repositories();UpdatePage.ADVANCED->technical()}}
    private fun summary(c:PluginCatalog)=activity.getString(R.string.hub_summary,c.repositoryCount,c.availableCount,c.installedCount)+(if(c.errors.isEmpty())""else"\n"+activity.getString(R.string.hub_repository_errors,c.errors.size))
    private fun addPlugins(){val p=show(s(R.string.hub_catalog),::open,"add");p.loading(true)
        observer=scope.launch{combine(manager.store.revision,manager.status){_,v->v}.collectLatest{
            val catalog=withContext(Dispatchers.IO){manager.catalog()};p.loading(false)
            val available=catalog.plugins.filter{it.compatible&&!it.ready&&(it.fresh||it.update)}.map{it.key}.toSet();selected.retainAll(available)
            if(catalog.repositoryCount==0){repositoryChoices(p);return@collectLatest}
            val rows=catalog.plugins.map{plugin->val reason=when{!plugin.enabled->s(R.string.updates_plugin_repository_off);plugin.entry.minApp>manager.installedCode->s(R.string.updates_plugin_newer_app);plugin.ready->s(R.string.updates_plugin_prepared);plugin.update->s(R.string.updates_plugin_update);plugin.installed!=null->s(R.string.hub_already_installed);else->s(R.string.hub_can_install)}
                ClassicRow("catalog:"+plugin.key,plugin.entry.name,plugin.entry.version+" · "+reason+"\n"+plugin.repositoryName,selected=plugin.key in selected,enabled=plugin.key in available)}
            p.update(rows,actions=listOf(
                ClassicAction("install-selected",if(catalog.pending)s(R.string.hub_finish_install)else activity.getString(R.string.hub_install_selected,selected.size),enabled=catalog.pending||selected.isNotEmpty()){if(catalog.pending)ready()else installKeys(selected.toList())},
                ClassicAction("select-all",s(if(selected.containsAll(available)&&available.isNotEmpty())R.string.hub_deselect else R.string.hub_select_all),enabled=available.isNotEmpty()){
                    if(selected.containsAll(available))selected.clear()else selected.addAll(available);addPlugins();view.requestKey("select-all")},
                ClassicAction("add-repo",s(R.string.updates_add_repository)){addRepository()},
                ClassicAction("refresh-catalog",s(R.string.hub_refresh)){refreshCatalog()}
            ),message=summary(catalog)+(if(catalog.plugins.isEmpty())"\n"+s(R.string.updates_empty_repository)else "")){key->
                val id=key.removePrefix("catalog:");if(id in selected)selected.remove(id)else selected.add(id);addPlugins();view.requestKey(key)
            }
        }}
    }
    private fun repositoryChoices(p:UpdateCenterView){
        p.update(listOf(ClassicRow("search",s(R.string.updates_search)),ClassicRow("search-user",s(R.string.updates_search_user)),ClassicRow("address",s(R.string.updates_add_address))),message=s(R.string.updates_add_plugins_help)){key->when(key){
            "address"->address()
            "search"->results(RepositoryDiscovery.DEFAULT_QUERY,false,1)
            "search-user"->editSearch("",true)
        }}
    }
    private fun addRepository(){val p=show(s(R.string.updates_add_repository),::addPlugins,if(manager.store.repositoryCount==0)"search"else"add-repo");repositoryChoices(p)}
    private fun refreshCatalog(){launchWork{val errors=mutableListOf<String>();for(repo in manager.repositories().filter{it.optBoolean("enabled",true)})try{manager.refresh(repo.getString("key"))}catch(e:CancellationException){throw e}catch(e:Exception){errors.add(repo.getString("name"))};addPlugins();if(errors.isNotEmpty())notice(activity.getString(R.string.hub_repository_errors,errors.size))}}
    private fun installKeys(keys:List<String>){if(!idle())return;launchWork{
        val plan=withContext(Dispatchers.IO){val catalog=manager.catalog();val choices=catalog.plugins.filter{it.key in keys&&it.compatible&&!it.ready&&(it.fresh||it.update)}
            require(choices.isNotEmpty()){s(R.string.hub_selection_changed)}
            val all=linkedMapOf<String,PackageInstaller.Candidate>();for(plugin in choices)for(candidate in installer.plan(plugin.repository,plugin.entry.id)){
                val old=all[candidate.identity];require(old==null||old.manifest.version==candidate.manifest.version){s(R.string.updates_error_conflict)};all[candidate.identity]=candidate
            };all.values.toList()};confirmInstall(plan)
    }}
    private fun updates(){val p=show(s(R.string.hub_updates),::open,"updates");observer=scope.launch{combine(manager.status,manager.store.revision){v,_->v}.collectLatest{v->
        val catalog=withContext(Dispatchers.IO){manager.catalog()};val rows=listOf(ClassicRow("application",s(R.string.updates_application),applicationStatus(v)))+
            catalog.updates.map{ClassicRow("update:"+it.key,it.entry.name,it.installed!!.module.version+" → "+it.entry.version)}+
            if(catalog.pending)listOf(ClassicRow("apply",s(R.string.hub_finish_install),s(R.string.updates_ready_help)))else emptyList()
        p.update(rows,actions=listOf(ClassicAction("check",s(R.string.updates_check),enabled=!v.busy){launchWork{manager.check()}},ClassicAction("update-all",s(R.string.hub_update_all),enabled=catalog.updates.isNotEmpty()){installKeys(catalog.updates.map{it.key})}),message=if(v.busy)s(R.string.updates_check_running)else activity.getString(R.string.hub_update_count,catalog.updates.size)){key->when(key){"application"->application();"apply"->ready();else->installKeys(listOf(key.removePrefix("update:")))}}
    }}}
    private fun technical(){val p=show(s(R.string.updates_technical),::open,"technical");val state=manager.store.snapshot()
        p.update(buildList{
            add(ClassicRow("notifications",s(R.string.hub_notifications)))
            add(ClassicRow("diagnostics",s(R.string.updates_diagnostics),s(R.string.updates_diagnostics_help)))
            add(ClassicRow("cleanup",s(R.string.updates_cleanup),s(R.string.updates_cleanup_help)))
            if(state.has("pending"))add(ClassicRow("cancel-pending",s(R.string.updates_cancel_pending)))
            if(state.has("previous")){add(ClassicRow("rollback",s(R.string.updates_previous)));add(ClassicRow("forget-previous",s(R.string.updates_forget_previous)))}
        },message=if(!state.has("pending")&&!state.has("previous"))s(R.string.updates_no_pending)else s(R.string.updates_technical_help)){key->when(key){
            "notifications"->notifications()
            "diagnostics"->diagnostics()
            "cleanup"->if(idle())display(ClassicDialog.Builder(activity).setMessage(R.string.updates_cleanup_help).setPositiveButton(android.R.string.ok){_,_->launchWork{val freed=withContext(Dispatchers.IO){installer.cleanup()};notice(activity.getString(R.string.updates_cleaned,freed/1024))}}.setNegativeButton(R.string.tv_cancel,null))
            "cancel-pending"->if(idle())launchWork{installer.clearPending();technical()}
            "rollback"->if(idle())launchWork{installer.rollback();open()}
            "forget-previous"->if(idle())display(ClassicDialog.Builder(activity).setMessage(R.string.updates_forget_previous_confirm).setPositiveButton(android.R.string.ok){_,_->launchWork{manager.store.commit{it.remove("previous")};technical()}}.setNegativeButton(R.string.tv_cancel,null))
        }}
    }
    private fun notifications(){val p=show(s(R.string.hub_notifications),::technical,"notifications");observer=scope.launch{manager.notices.changes.collectLatest{v->
        p.update(NoticeKind.entries.map{kind->ClassicRow("notice:"+kind.name,s(when(kind){NoticeKind.APPLICATION->R.string.hub_app_notices;NoticeKind.NEW_PLUGINS->R.string.hub_new_notices;NoticeKind.PLUGIN_UPDATES->R.string.hub_plugin_notices}),s(if(kind in v.disabled)R.string.settings_off else R.string.settings_on))},actions=listOf(ClassicAction("reset-notices",s(R.string.hub_reset_notices)){launchWork{manager.notices.update{NoticeState()}}}),message=s(R.string.hub_notice_rules)){key->val kind=NoticeKind.valueOf(key.removePrefix("notice:"));launchWork{manager.notices.update{it.copy(disabled=if(kind in it.disabled)it.disabled-kind else it.disabled+kind)}}}
    }}}
    private fun diagnostics(){val v=manager.status.value;val errors=buildList{
        if(v.appError.isNotBlank())add(s(R.string.updates_application)+": "+v.appError)
        if(v.pluginError.isNotBlank())add(s(R.string.updates_my_plugins)+": "+v.pluginError)
        v.repositoryChecks.values.filter{it.error.isNotBlank()}.forEach{add(it.name+": "+it.error)}
        if(manager.lastIssue.isNotBlank())add(manager.lastIssue)
        if(v.message.isNotBlank())add(v.message)
        manager.store.failure?.let{add(it)}
    }.distinct().joinToString("\n\n").take(4000)
        display(ClassicDialog.Builder(activity).setTitle(R.string.updates_diagnostics).setMessage(errors.ifBlank{s(R.string.updates_no_diagnostics)}).setPositiveButton(android.R.string.ok,null))
    }
    private fun application(){val p=show(s(R.string.updates_application),::updates,"application");observer=scope.launch{manager.status.collectLatest{v->
        val operation=when{v.busy&&v.bytes>0->s(R.string.updates_phase_downloading)+" · ${v.bytes/1024} KiB";v.apk!=null->s(R.string.updates_apk_ready);v.message.isNotBlank()->problemText(IllegalStateException(v.message));else->""}
        if(v.busy){if(v.bytes>0)p.progress(v.bytes,v.release?.size?.toLong()?:0)else p.loading(true)}else p.loading(false)
        p.update(listOf(ClassicRow("update-application",s(if(manager.readyApk()!=null)R.string.hub_finish_application else R.string.hub_update_application),enabled=v.release!=null&&!v.busy)),message=listOf(applicationStatus(v),operation,v.release?.notes?:s(R.string.updates_no_application)).filter{it.isNotBlank()}.joinToString("\n")){updateApplication()}
    }}}
    private fun updateApplication(){if(!idle())return;if(manager.readyApk()!=null){installApk();return};if(manager.status.value.release==null)return
        launchWork{withContext(Dispatchers.IO){manager.downloadApk()};if(manager.readyApk()!=null&&activity.hasWindowFocus()&&!activity.isFinishing&&!playing())installApk()}
    }
    private fun installApk(){
        if(Build.VERSION.SDK_INT>=26&&!activity.packageManager.canRequestPackageInstalls()){
            display(ClassicDialog.Builder(activity).setMessage(R.string.updates_unknown_sources).setPositiveButton(android.R.string.ok){_,_->activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.packageName)))}.setNegativeButton(R.string.tv_cancel,null));return
        }
        val file=manager.readyApk()?:return
        val uri=FileProvider.getUriForFile(activity,activity.packageName+".updates",file)
        activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
    private fun prompt(title:Int,hint:Int,initial:String="",submit:(String)->Unit){val input=EditText(activity).apply{tag="updates:input";setTextColor(ClassicUi.foreground);setHintTextColor(ClassicUi.muted);setSingleLine();setHint(hint);setText(initial);setSelection(text.length)}
        display(ClassicDialog.Builder(activity).setTitle(title).setView(input).setPositiveButton(R.string.updates_probe){_,_->submit(input.text.toString().trim())}.setNegativeButton(R.string.tv_cancel,null))
    }
    private fun trust(probe:RepositoryProbe){display(ClassicDialog.Builder(activity).setTitle(R.string.updates_probe)
        .setMessage(activity.getString(R.string.updates_trust_message,probe.index.name,probe.fingerprint,probe.index.packages.filter{it.type=="site"&&it.minApp<=manager.installedCode}.map{it.id}.distinct().size))
        .setPositiveButton(R.string.updates_trust){_,_->launchWork{manager.trust(probe);addPlugins()
            val catalog=withContext(Dispatchers.IO){manager.catalog()};val items=catalog.fresh.filter{it.repository==probe.fingerprint+"/"+probe.index.id&&manager.notices.policyState().permits(it.token,NoticeKind.NEW_PLUGINS,System.currentTimeMillis())}
            repeat(5){if(!activity.hasWindowFocus())delay(100)}
            if(items.isNotEmpty()&&activity.hasWindowFocus()&&!activity.isFinishing&&!playing()){noticePopup?.close();noticePopup=UpdateNoticePopup(activity,scope,manager.notices,UpdateNotice(NoticeKind.NEW_PLUGINS,items.map{it.token},items)){route->navigate(route)}}
        }}.setNegativeButton(R.string.tv_cancel,null))}
    private fun repositories(){val p=show(s(R.string.updates_repositories),::open,"repositories");val repos=manager.repositories();p.update(repos.map{r->val check=manager.status.value.repositoryChecks[r.getString("key")];ClassicRow(r.getString("key"),r.getString("name"),when{!r.optBoolean("enabled",true)->s(R.string.updates_off);check?.state==CheckState.FAILED->s(R.string.updates_repo_failed);check?.state==CheckState.CURRENT->s(R.string.updates_repo_current);else->s(R.string.updates_repos_unchecked)})},actions=listOf(ClassicAction("add",s(R.string.updates_add_repository)){addRepository()}),message=if(repos.isEmpty())s(R.string.updates_no_repositories)else ""){key->repository(repos.single{it.getString("key")==key})}}
    private fun repository(repo:JSONObject){
        val p=show(repo.getString("name"),::repositories,repo.getString("key"));val enabled=repo.optBoolean("enabled",true)
        val entries=manager.index(repo).packages.groupBy{it.id}.mapNotNull{(_,rows)->rows.maxWithOrNull{a,b->UpdateProtocol.compare(a.version,b.version)}}
        val active=manager.store.snapshot().getJSONObject("packages")
        p.update(listOf(
            ClassicRow("refresh",s(R.string.updates_refresh_repository)),
            ClassicRow("enabled",s(R.string.updates_repository_enabled),if(enabled)s(R.string.updates_on)else s(R.string.updates_off)),
            ClassicRow("address",s(R.string.updates_change_address),repo.getString("url")),
            ClassicRow("remove",s(R.string.updates_remove_repository)),
            ClassicRow("all",s(R.string.updates_install_sites),enabled=enabled&&entries.any{it.type=="site"&&it.minApp<=manager.installedCode})
        )+entries.filter{it.type=="site"}.map{e->val current=active.optJSONObject(repo.getString("fingerprint")+"/"+e.id)?.let{UpdateProtocol.module(it.getString("manifest").toByteArray()).version};ClassicRow("package:"+e.id,e.name+" · "+e.version,when{e.minApp>manager.installedCode->s(R.string.updates_plugin_newer_app);current==null->s(R.string.updates_plugin_not_installed);UpdateProtocol.compare(e.version,current)>0->s(R.string.updates_plugin_update);else->s(R.string.updates_plugin_current)})},message=if(entries.none{it.type=="site"})s(R.string.updates_empty_repository)else s(R.string.updates_resolvers_automatic)){key->
            val id=repo.getString("key")
            when(key){
                "refresh"->launchWork{manager.refresh(id);repository(manager.repositories().single{it.getString("key")==id})}
                "enabled"->if(idle())launchWork{manager.setRepositoryEnabled(id,!enabled);repository(manager.repositories().single{it.getString("key")==id})}
                "address"->prompt(R.string.updates_change_address,R.string.updates_address_hint){url->launchWork{manager.changeAddress(id,url);repository(manager.repositories().single{it.getString("key")==id})}}
                "remove"->if(idle())display(ClassicDialog.Builder(activity).setMessage(R.string.updates_remove_repository).setPositiveButton(android.R.string.ok){_,_->launchWork{manager.removeRepository(id);repositories()}}.setNegativeButton(R.string.tv_cancel,null))
                else->if(key!="all")availablePlugin(repo,entries.single{it.id==key.removePrefix("package:")})else if(idle())launchWork{
                    val requested=entries.filter{it.type=="site"&&it.minApp<=manager.installedCode}.map{it.id}
                    val plan=mutableMapOf<String,PackageInstaller.Candidate>()
                    for(pluginId in requested)for(c in withContext(Dispatchers.IO){installer.plan(id,pluginId)}){val old=plan[c.identity];require(old==null||old.manifest.version==c.manifest.version){"Bağımlılıklar aynı sürümde uzlaşmıyor"};plan[c.identity]=c}
                    confirmInstall(plan.values.toList())
                }
            }
        }
    }
    private fun availablePlugin(repo:JSONObject,entry:UpdateProtocol.Entry){
        val p=show(entry.name,{repository(repo)},"package:"+entry.id)
        val key=repo.getString("fingerprint")+"/"+entry.id
        inventoryPage(p){inventory->val installed=inventory.providers.firstOrNull{it.key==key}
        val canInstall=repo.optBoolean("enabled",true)&&entry.minApp<=manager.installedCode
        p.update(listOf(ClassicRow("version",s(R.string.updates_plugin_version),entry.version,enabled=false))+
            (installed?.let{groupRows(it)}?:emptyList()),actions=listOf(
                ClassicAction("prepare",s(if(installed==null)R.string.updates_plugin_install else R.string.updates_plugin_update_action),enabled=canInstall){if(idle())launchWork{confirmInstall(withContext(Dispatchers.IO){installer.plan(repo.getString("key"),entry.id)})}}
            ),message=when{!repo.optBoolean("enabled",true)->s(R.string.updates_plugin_repository_off);entry.minApp>manager.installedCode->s(R.string.updates_plugin_newer_app);else->s(R.string.updates_resolvers_automatic)})}
    }
    private fun pluginState(group:PluginGroup)=s(when(group.state){
        PluginState.ACTIVE->R.string.updates_plugin_current
        PluginState.UPDATE_AVAILABLE->R.string.updates_plugin_update
        PluginState.READY->R.string.updates_plugin_prepared
        PluginState.REMOVAL_READY->R.string.updates_plugin_removal_prepared
        PluginState.DISABLED->R.string.updates_plugin_disabled
        PluginState.WITHDRAWN->R.string.updates_plugin_withdrawn
        PluginState.NEWER_APP_REQUIRED->R.string.updates_plugin_newer_app
    })
    private fun groupRows(group:PluginGroup)=listOf(ClassicRow("active-version",s(R.string.updates_plugin_active_version),group.active?.module?.version?:s(R.string.updates_plugin_not_installed),enabled=false))+
        (group.prepared?.let{listOf(ClassicRow("prepared-version",s(R.string.updates_plugin_ready_version),it.module.version,enabled=false))}?:emptyList())+
        group.resolvers.map{ClassicRow("resolver:"+it.key,it.module.name,it.module.version,enabled=false)}+
        group.missingResolvers.map{ClassicRow("missing:"+it,it,s(R.string.updates_plugin_missing_resolver),enabled=false)}
    private fun confirmInstall(plan:List<PackageInstaller.Candidate>){display(ClassicDialog.Builder(activity).setTitle(R.string.hub_install_title).setMessage(plan.filter{it.manifest.type=="site"}.joinToString("\n"){it.manifest.name+" · "+it.manifest.version+it.replaces.joinToString(""){key->"\n"+activity.getString(R.string.updates_replaces_package,key.substringAfter('/'))}}+"\n\n"+s(R.string.hub_install_help))
        .setPositiveButton(R.string.hub_confirm_install){_,_->
            val p=show(s(R.string.updates_download));val token=generation
            val latest=java.util.concurrent.atomic.AtomicReference(InstallProgress(InstallPhase.DOWNLOADING,"",0,plan.sumOf{it.manifest.size.toLong()}))
            fun progress(v:InstallProgress){if(token!=generation)return;p.progress(v.completed,v.total);p.update(emptyList(),actions=listOf(ClassicAction("cancel",s(R.string.tv_cancel)){back()}),message=s(if(v.phase==InstallPhase.DOWNLOADING)R.string.updates_phase_downloading else R.string.updates_phase_verifying)+"\n"+v.name+"\n${v.completed/1024} / ${v.total/1024} KiB")}
            progress(latest.get());p.focusFirst()
            download=scope.launch{try{withContext(Dispatchers.IO){installer.install(plan,onState={v->latest.set(v);scope.launch{progress(v)}})}
                val until=android.os.SystemClock.elapsedRealtime()+500
                while(!activity.hasWindowFocus()&&!activity.isFinishing&&android.os.SystemClock.elapsedRealtime()<until)delay(50)
                InstallCompletion.finish({activity.hasWindowFocus()&&!activity.isFinishing&&!playing()},{installer.activate()},{DistributionRestart.run(activity)},{ready()})}
                catch(e:CancellationException){if(latest.get().phase==InstallPhase.READY)ready()else{open();display(ClassicDialog.Builder(activity).setMessage(R.string.updates_download_cancelled).setPositiveButton(android.R.string.ok,null))};throw e}
                catch(e:Exception){open();error(e)}}
        }.setNegativeButton(R.string.tv_cancel,null))
    }
    private fun ready(){val p=show(s(R.string.updates_phase_ready),::open,"apply");inventoryPage(p){inventory->val groups=inventory.providers.filter{it.state==PluginState.READY||it.state==PluginState.REMOVAL_READY}
        p.update(groups.map{ClassicRow(it.key,it.current.module.name,pluginState(it),enabled=false)},actions=listOf(
            ClassicAction("apply",s(R.string.hub_finish_install)){if(idle())launchWork{if(!manager.store.restartRequired)installer.activate();restart()}},
            ClassicAction("later",s(R.string.updates_later)){open()}
        ),message=s(if(manager.store.restartRequired)R.string.hub_restart_help else R.string.updates_ready_help));p.focusFirst()}
    }
    private fun installed(){val p=show(s(R.string.updates_installed),::open,"installed");inventoryPage(p){inventory->
        p.update(inventory.providers.map{g->ClassicRow(g.key,g.current.module.name,pluginState(g)+" · "+g.current.module.version)},
            actions=if(inventory.unusedResolvers.isEmpty())emptyList()else listOf(ClassicAction("unused-resolvers",s(R.string.updates_unused_resolvers)){unusedResolvers()}),
            message=if(inventory.providers.isEmpty())s(R.string.updates_no_plugins)else s(R.string.updates_resolvers_in_details)){key->installedPlugin(inventory.providers.single{it.key==key})}}
    }
    private fun installedPlugin(group:PluginGroup){val p=show(group.current.module.name,::installed,group.key)
        p.update(groupRows(group),actions=buildList{
            val repo=manager.repositories().firstOrNull{it.getString("key")==group.current.repo}
            if(group.state in listOf(PluginState.READY,PluginState.REMOVAL_READY))add(ClassicAction("apply",s(R.string.updates_phase_ready)){ready()})
            else if(group.available!=null&&repo!=null&&repo.optBoolean("enabled",true)&&group.available.minApp<=manager.installedCode)
                add(ClassicAction("update",s(R.string.updates_plugin_update_action)){if(idle())launchWork{confirmInstall(withContext(Dispatchers.IO){installer.plan(group.current.repo,group.current.module.id)})}})
            if(group.state!=PluginState.REMOVAL_READY)add(ClassicAction("remove",s(R.string.updates_remove)){removal(group.key)})
        },message=pluginState(group))
    }
    private fun unusedResolvers(){val p=show(s(R.string.updates_unused_resolvers),::installed,"unused-resolvers");inventoryPage(p){inventory->val entries=inventory.unusedResolvers
        p.update(entries.map{ClassicRow(it.key,it.module.name,it.module.version)},message=s(R.string.updates_unused_resolvers_help)){key->
            removal(key)}}
    }
    private fun removal(key:String){if(!idle())return;launchWork{
        val plan=withContext(Dispatchers.IO){installer.removalPlan(key)}
        val message=buildList{
            add(s(R.string.updates_remove_package_confirm))
            if(plan.unused.isNotEmpty())add(s(R.string.updates_remove_unused_help)+"\n"+plan.unused.joinToString("\n"){it.module.name})
            if(plan.shared.isNotEmpty())add(s(R.string.updates_remove_shared_help)+"\n"+plan.shared.joinToString("\n"){it.module.name})
        }.joinToString("\n\n")
        fun remove(includeUnused:Boolean){launchWork{withContext(Dispatchers.IO){installer.remove(plan,includeUnused)};ready()}}
        val window=ClassicDialog.Builder(activity).setTitle(plan.name).setMessage(message)
            .setPositiveButton(if(plan.unused.isEmpty())R.string.updates_remove else R.string.updates_remove_together){_,_->remove(plan.unused.isNotEmpty())}
            .setNegativeButton(R.string.tv_cancel,null)
        if(plan.unused.isNotEmpty())window.setNeutralButton(R.string.updates_remove_only){_,_->remove(false)}
        display(window)
    }}
    private fun address(){prompt(R.string.updates_add_address,R.string.updates_address_hint){url->launchWork{trust(withContext(Dispatchers.IO){manager.probe(url)})}}}
    private fun editSearch(query:String,user:Boolean){prompt(if(user)R.string.updates_search_user else R.string.updates_search,R.string.updates_search_hint,query){q->results(q,user,1)}}
    private fun results(query:String,user:Boolean,page:Int,refresh:Boolean=false){
        work?.cancel()
        val p=show(s(if(user)R.string.updates_search_user else R.string.updates_search),::addPlugins,if(user)"search-user"else"search")
        val token=generation
        val term=query.trim().ifBlank{if(user)""else RepositoryDiscovery.DEFAULT_QUERY}
        p.loading(true)
        p.update(emptyList(),actions=listOf(ClassicAction("cancel",s(R.string.tv_cancel)){back()},ClassicAction("address",s(R.string.updates_add_address)){back();address()}),message=activity.getString(R.string.updates_searching,term))
        p.focusFirst()
        work=scope.launch {
            try {
                val result=withContext(Dispatchers.IO){manager.search(term,user,page,refresh)}
                if(token!=generation)return@launch
                p.loading(false)
                val known=manager.repositories().map{it.getString("url")}.toSet()
                val rows=result.items.map{r->ClassicRow(r.fullName,r.fullName,
                    activity.getString(R.string.updates_search_package_counts,r.sites,r.resolvers)+(if(r.address in known)" · "+s(R.string.updates_repository_added)else "")+r.description.takeIf{it.isNotBlank()}?.let{"\n$it"}.orEmpty())}+buildList{
                    if(page>1)add(ClassicRow("previous",s(R.string.updates_search_previous)))
                    if(result.next)add(ClassicRow("next",s(R.string.updates_search_next)))
                }
                val message=buildList{
                    add(activity.getString(R.string.updates_search_summary,result.query,page,result.items.size))
                    if(result.items.isEmpty())add(s(R.string.updates_no_search_results))
                    if(result.incomplete)add(s(R.string.updates_search_incomplete))
                    if(result.verificationFailures>0)add(activity.getString(R.string.updates_search_unverified,result.verificationFailures))
                }.joinToString("\n")
                p.update(rows,actions=listOf(
                    ClassicAction("edit",s(R.string.updates_search_edit)){editSearch(result.query,user)},
                    ClassicAction("refresh",s(R.string.updates_search_retry)){results(result.query,user,page,true)},
                    ClassicAction("address",s(R.string.updates_add_address)){address()}
                ),message=message){key->when(key){
                    "previous"->results(result.query,user,page-1)
                    "next"->results(result.query,user,page+1)
                    else->launchWork{p.loading(true);try{val candidate=result.items.single{it.fullName==key};trust(withContext(Dispatchers.IO){manager.probe(candidate.address)})}finally{if(token==generation)p.loading(false)}}
                }}
            }catch(e:CancellationException){if(token==generation)p.loading(false);throw e}
            catch(e:Exception){if(token==generation){p.loading(false);p.update(emptyList(),actions=listOf(
                ClassicAction("retry",s(R.string.updates_search_retry)){results(term,user,page,true)},
                ClassicAction("edit",s(R.string.updates_search_edit)){editSearch(term,user)},
                ClassicAction("address",s(R.string.updates_add_address)){address()}
            ),message=activity.getString(R.string.updates_search_failed,e.message?:e.javaClass.simpleName))}}
        }
    }
    private fun restart(){scope.launch{DistributionRestart.run(activity)}}
}

/** Restart is independent of update-page construction, including after a data import. */
internal object DistributionRestart {
    suspend fun run(activity:Activity){
        (activity as? TvActivity)?.quiesceForDistribution()
        ModuleRuntime.stop()
        activity.startActivity(Intent(activity,RestartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        activity.finishAffinity();android.os.Process.killProcess(android.os.Process.myPid())
    }
}
