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
                "timeout"->PluginWorkerTest().independentPublisherTypedErrorAndUncooperativeCancellation()
                "download"->DistributionSmokeTest().githubRepositoryDownloadsAndStagesSignedDependencySet()
                "ui"->DistributionSmokeTest().emptyHomeOpensClassicNativeUpdatePanelAndBackReturnsFocus()
                "playback"->PublicationPlaybackTest().signedModulesAndMedia3PreserveCookieHeadersAndProgress()
                "pilot"->PilotWorkerTest().signaturesDependenciesAndFourCatalogsLoadInRealWorker()
                "pilot-playback"->PilotWorkerTest().fourLiveSourcesRenderAndAdvanceTwentySeconds()
                "stage"->kotlinx.coroutines.runBlocking{
                    val manager=DistributionManager.get(targetContext);val probe=manager.probe("https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV-Eklentiler");manager.trust(probe)
                    val installer=PackageInstaller(manager);val plan=installer.plan(probe.fingerprint+"/"+probe.index.id,"site-filmcibaba");installer.install(plan)
                }
                "check-state"->{val manager=DistributionManager.get(targetContext);val state=manager.store.snapshot();check(state.getJSONObject("packages").length()==2);check(!state.has("pending"));result.putString("state",state.toString())}
                "check-feed"->kotlinx.coroutines.runBlocking{
                    val manager=DistributionManager.get(targetContext);manager.check()
                    check(manager.store.snapshot().optInt("appHighestCode")==arguments.getString("expectedCode","36").toInt()){manager.status.value.message}
                    result.putString("feedStatus",manager.status.value.message)
                    if(arguments.getBoolean("expectUpdate"))check(manager.status.value.release?.code==36)
                }
                "startup-check"->{
                    val manager=DistributionManager.get(targetContext);val started=System.currentTimeMillis()
                    val activity=startActivitySync(android.content.Intent(targetContext,app.filmmerkezi.tv.tv.TvActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    val deadline=android.os.SystemClock.elapsedRealtime()+20000
                    while(manager.status.value.checkedAt<started&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(100)
                    check(manager.status.value.checkedAt>=started){"Opening the app did not start the update check"}
                    result.putLong("startupCheckMs",manager.status.value.checkedAt-started);runOnMainSync{activity.finish()}
                }
                "download-apk"->kotlinx.coroutines.runBlocking{
                    val manager=DistributionManager.get(targetContext);manager.check()
                    // Only the test process offers the current signed APK to exercise the
                    // complete download verifier without publishing a fabricated version.
                    val release=UpdateProtocol.release(manager.transport.bytes(DistributionManager.APP_ROOT+"update.json"))
                    val field=DistributionManager::class.java.getDeclaredField("state").apply{isAccessible=true}
                    @Suppress("UNCHECKED_CAST") val state=field.get(manager)as kotlinx.coroutines.flow.MutableStateFlow<DistributionStatus>
                    state.value=state.value.copy(release=release)
                    manager.downloadApk()
                    if(manager.installedCode<36)check(manager.status.value.apk==null&&manager.status.value.message.contains("kalıcı imzaya geçiş")){manager.status.value.message}
                    else check(manager.status.value.apk?.length()==release.size.toLong()){manager.status.value.message}
                    result.putString("verifiedDownload",manager.status.value.message)
                }
                else->error("Choose an explicit probe action")
            }
            result.putString("stream","PASS: "+arguments.getString("action")+"\n");finish(-1,result)
        }catch(e:Throwable){result.putString("stream",e.stackTraceToString());finish(0,result)}
    }
}
