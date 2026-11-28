package app.filmmerkezi.tv.tv

import android.app.Activity
import app.filmmerkezi.tv.updates.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal class UpdateNoticeController(private val activity:Activity,parent:CoroutineScope,private val manager:DistributionManager,
    private val canShow:()->Boolean,private val route:(UpdateRoute)->Unit,private val badge:(Boolean)->Unit):AutoCloseable {
    private val lifetime=SupervisorJob(parent.coroutineContext[Job]);private val scope=CoroutineScope(parent.coroutineContext+lifetime)
    private var catalog:PluginCatalog?=null
    private var popup:UpdateNoticePopup?=null
    private var lastClosed=0L
    var hasUpdates=false;private set
    init {
        scope.launch{combine(manager.status,manager.store.revision,manager.notices.changes){status,_,_->status}.collectLatest{status->
            try{catalog=withContext(Dispatchers.IO){manager.catalog()};hasUpdates=status.release!=null||status.moduleUpdates>0||catalog!!.fresh.any{it.token !in manager.notices.value.ignored};badge(hasUpdates)}
            catch(e:CancellationException){throw e}catch(e:Exception){catalog=null;manager.recordIssue(e)}
        }}
        scope.launch{while(isActive){delay(1000);if(popup?.showing!=true&&android.os.SystemClock.elapsedRealtime()-lastClosed>=15000&&canShow())catalog?.let{c->
            UpdateNoticePolicy.next(c,manager.status.value,manager.notices.policyState(),System.currentTimeMillis(),manager.installedCode,android.os.Build.VERSION.SDK_INT)?.let{offer->
                popup=UpdateNoticePopup(activity,scope,manager.notices,offer,route).also{window->window.onClosed={lastClosed=android.os.SystemClock.elapsedRealtime();if(popup===window)popup=null}}
            }
        }}}
    }
    fun hide(){popup?.close();popup=null}
    internal fun popupForTest()=popup
    override fun close(){hide();lifetime.cancel()}
}
