package app.filmmerkezi.tv.tv

import android.app.Activity
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.updates.*
import kotlinx.coroutines.*

/** Application-owned notice, with no Android notification permission or extra player surface. */
internal class UpdateNoticePopup(private val activity:Activity,private val scope:CoroutineScope,private val store:UpdateNoticeStore,
    private val notice:UpdateNotice,private val navigate:(UpdateRoute)->Unit):AutoCloseable {
    private var busy=false;private var failed=false
    private val window=ClassicOverlay(activity,activity.getString(when(notice.kind){NoticeKind.APPLICATION->R.string.hub_notice_app_title;NoticeKind.NEW_PLUGINS->R.string.hub_notice_new_title;NoticeKind.PLUGIN_UPDATES->R.string.hub_notice_updates_title}),origin=activity.currentFocus)
    var onClosed:(()->Unit)?=null
    val showing get()=window.showing
    init {
        window.canDismiss={if(!busy){if(failed){store.suppressSession(notice.tokens);close()}else decide(NoticeDecision.LATER,null)};false}
        window.onClosed={onClosed?.invoke()};draw()
    }
    private fun route(install:Boolean)=if(notice.kind==NoticeKind.APPLICATION)UpdateRoute(UpdatePage.APPLICATION,downloadApplication=install,applicationCode=notice.release!!.code)
        else UpdateRoute(if(notice.kind==NoticeKind.NEW_PLUGINS)UpdatePage.CATALOG else UpdatePage.UPDATES,install=if(install)notice.plugins.map{it.key}else emptyList())
    private fun draw(){val body=buildList{
        add(if(notice.kind==NoticeKind.APPLICATION)activity.getString(R.string.hub_notice_app_version,notice.release!!.name)
            else activity.getString(if(notice.kind==NoticeKind.NEW_PLUGINS)R.string.hub_notice_new_count else R.string.hub_notice_updates_count,notice.plugins.size))
        if(notice.plugins.isNotEmpty()){add(notice.plugins.take(3).joinToString(" · "){it.entry.name});if(notice.plugins.size>3)add(activity.getString(R.string.hub_notice_more,notice.plugins.size-3))}
        add(activity.getString(if(failed)R.string.hub_notice_save_error else R.string.hub_notice_later_help))
    }.joinToString("\n")
        window.update(emptyList(),listOf(
            ClassicAction("notice-install",activity.getString(if(notice.kind==NoticeKind.NEW_PLUGINS)R.string.hub_confirm_install else R.string.hub_update_application),enabled=!busy){decide(NoticeDecision.LATER,route(true))},
            ClassicAction("notice-inspect",activity.getString(R.string.hub_notice_inspect),enabled=!busy){decide(NoticeDecision.LATER,route(false))},
            ClassicAction("notice-later",activity.getString(R.string.hub_notice_later),enabled=!busy){decide(NoticeDecision.LATER,null)},
            ClassicAction("notice-ignore",activity.getString(R.string.hub_notice_ignore),enabled=!busy){decide(NoticeDecision.IGNORE,null)}
        ),body)
    }
    private fun decide(decision:NoticeDecision,route:UpdateRoute?){if(busy)return;busy=true
        scope.launch{try{
            store.decide(notice,decision);store.suppressSession(notice.tokens);close()
            if(route!=null){delay(150);if(!activity.isFinishing)navigate(route)}
        }catch(e:CancellationException){throw e}catch(_:Exception){busy=false;failed=true;draw()}}
    }
    internal fun panelForTest()=window
    override fun close(){window.close()}
}
