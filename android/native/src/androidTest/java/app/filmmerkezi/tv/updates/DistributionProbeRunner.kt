package app.filmmerkezi.tv.updates

import android.app.Instrumentation
import android.os.Bundle

/** Framework runner avoids debug-only AndroidX dependencies removed from a shrunk host. */
class DistributionProbeRunner:Instrumentation() {
    private lateinit var arguments:Bundle
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);this.arguments=arguments?:Bundle();start()}
    override fun onStart(){
        super.onStart()
        androidx.test.platform.app.InstrumentationRegistry.registerInstance(this,arguments)
        val result=Bundle()
        try{
            when(arguments.getString("action")){
                "export"->MigrationTransferProbe().exportInstalledData()
                "seed"->MigrationTransferProbe().seedVerificationData()
                "import"->MigrationTransferProbe().importPreviouslyExportedData()
                "worker"->{val test=PluginWorkerTest();test.allThirtyTwoSignedEntriesStartInWorker();test.parserRunsAcrossPipeAndMatchesJvmSchema();test.independentPublisherTypedErrorAndUncooperativeCancellation();test.setValidationRejectsMissingDependencyAndRecoverRestoresWholeSet()}
                "download"->DistributionSmokeTest().githubRepositoryDownloadsAndStagesSignedDependencySet()
                "ui"->DistributionSmokeTest().emptyHomeOpensClassicNativeUpdatePanelAndBackReturnsFocus()
                "playback"->PublicationPlaybackTest().signedModulesAndMedia3PreserveCookieHeadersAndProgress()
                "stage"->kotlinx.coroutines.runBlocking{
                    val manager=DistributionManager.get(targetContext);val probe=manager.probe("https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV-Eklentiler");manager.trust(probe)
                    val installer=PackageInstaller(manager);val plan=installer.plan(probe.fingerprint+"/"+probe.index.id,"site-filmcibaba");installer.install(plan)
                }
                "check-state"->{val manager=DistributionManager.get(targetContext);val state=manager.store.snapshot();check(state.getJSONObject("packages").length()==2);check(!state.has("pending"));result.putString("state",state.toString())}
                "check-feed"->kotlinx.coroutines.runBlocking{
                    val manager=DistributionManager.get(targetContext);manager.check()
                    check(manager.store.snapshot().optInt("appHighestCode")==36){manager.status.value.message}
                    result.putString("feedStatus",manager.status.value.message)
                    if(arguments.getBoolean("expectUpdate"))check(manager.status.value.release?.code==36)
                }
                else->error("Choose an explicit probe action")
            }
            result.putString("stream","PASS: "+arguments.getString("action")+"\n");finish(-1,result)
        }catch(e:Throwable){result.putString("stream",e.stackTraceToString());finish(0,result)}
    }
}
