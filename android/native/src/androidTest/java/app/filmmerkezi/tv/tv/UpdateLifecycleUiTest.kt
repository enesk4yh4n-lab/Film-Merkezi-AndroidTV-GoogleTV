package app.filmmerkezi.tv.tv

import android.view.*
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.settings.AppSettings
import app.filmmerkezi.tv.updates.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdateLifecycleUiTest {
    @Test fun independentSummariesGroupedProvidersAndPreparedActivation()=runBlocking {PilotWorkerTest().withSet{
        val f=Settings110Fixture();val manager=DistributionManager.get(f.context);val installer=PackageInstaller(manager)
        val stateField=DistributionManager::class.java.getDeclaredField("state").apply{isAccessible=true}
        @Suppress("UNCHECKED_CAST") val status=stateField.get(manager) as MutableStateFlow<DistributionStatus>
        val original=status.value;val report=JSONObject()
        fun center()=checkNotNull(f.activity.updateCenterForTest())
        fun press(key:Int)=f.ins.sendKeyDownUpSync(key)
        try {
            val repoKey=manager.repositories().single().getString("key")
            status.value=DistributionStatus(appCheck=CheckState.CURRENT,moduleUpdates=2,repositoryChecks=mapOf("removed" to RepositoryCheck("removed","A",CheckState.CURRENT),repoKey to RepositoryCheck(repoKey,"B",CheckState.FAILED,"offline")))
            f.start(AppSettings(),UpdateTestData.empty());f.main{f.activity.findViewById<View>(R.id.tv_nav_about).performClick()}
            f.waitFor{f.tag("settings-category:updates")!=null};f.main{f.tag("settings-category:updates").requestFocus()}
            f.waitFor{f.activity.updateCenterForTest()?.keys()?.contains("installed")==true}
            f.main{assertTrue(center().row("application").description.contains("Uygulama güncel"));assertTrue(center().row("repositories").description.contains("1 depoya"));assertTrue(center().row("installed").description.contains("2"));center().select("installed")}
            f.waitFor{center().keys().size==4}
            val keys=center().keys();assertTrue(keys.all{it.substringAfter('/').startsWith("p")})
            val p002=keys.single{it.endsWith("/p002")}
            f.main{center().select(p002)};f.waitFor{center().keys().any{it.startsWith("resolver:")}}
            f.main{assertTrue(center().keys().any{it.endsWith("/r002")})};report.put("step1Summaries",true).put("step2Providers",4).put("resolversInDetails",true)
            f.screenshot("updates-grouped-provider.png")
            press(KeyEvent.KEYCODE_BACK);press(KeyEvent.KEYCODE_BACK)
            val all=manager.store.snapshot().getJSONObject("packages");val repository=manager.repositories().single()
            val plan=listOf("r002","p002").map{id->val key=repository.getString("fingerprint")+"/"+id;val r=all.getJSONObject(key)
                PackageInstaller.Candidate(repository,UpdateProtocol.module(r.getString("manifest").toByteArray()),r.getString("manifest").toByteArray(),r.getString("signature"))}
            manager.store.commit{it.put("previous",all);val reduced=JSONObject(all.toString());plan.forEach{p->reduced.remove(p.identity)};it.put("packages",reduced);it.remove("pending")}
            val active=manager.store.snapshot().getJSONObject("packages").toString()
            try{withContext(Dispatchers.IO){installer.install(plan,onState={if(it.phase==InstallPhase.VERIFYING)throw CancellationException("cancel verification")})};fail("Cancellation expected")}catch(_:CancellationException){}
            assertFalse(manager.store.snapshot().has("pending"));assertEquals(active,manager.store.snapshot().getJSONObject("packages").toString())
            val phases=mutableListOf<InstallPhase>();withContext(Dispatchers.IO){installer.install(plan,onState={phases.add(it.phase)})}
            assertEquals(InstallPhase.READY,phases.last());assertEquals(active,manager.store.snapshot().getJSONObject("packages").toString())
            f.waitFor{center().keys().contains("apply")}
            f.main{center().select("installed")};f.waitFor{center().keys().size==4}
            f.main{assertTrue(center().row(p002).description.contains("Etkinleştirmeye hazır"));center().select(p002)}
            f.waitFor{center().actionKeys().contains("apply")};f.main{center().select("apply")}
            f.waitFor{center().actionKeys()==listOf("apply","later")}
            f.screenshot("updates-activation-ready.png")
            f.main{center().select("later")};f.waitFor{center().keys().contains("apply")}
            assertEquals(active,manager.store.snapshot().getJSONObject("packages").toString())
            report.put("step3CancelledBeforeCommit",true).put("step3ReadyDoesNotActivate",true).put("step3ExplicitApplyAction",true)
        }finally{status.value=original;File(f.context.getExternalFilesDir(null),"updates-lifecycle-ui.json").writeText(report.toString(2));f.close()}
    }}
}
