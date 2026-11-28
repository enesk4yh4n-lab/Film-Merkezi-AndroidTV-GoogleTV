package app.filmmerkezi.tv.updates

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Preferences contain package/version identifiers only, never signed media URLs or cookies. */
class UpdateNoticeStore(context:Context){
    private val preferences=context.applicationContext.getSharedPreferences("update-notices",Context.MODE_PRIVATE)
    private val lock=Mutex()
    private val state=MutableStateFlow(NoticeState.decode(runCatching{preferences.getString("state",null)}.getOrNull()))
    val changes=state.asStateFlow()
    val value get()=state.value
    private val session:MutableMap<String,Boolean> = java.util.concurrent.ConcurrentHashMap()
    fun suppressSession(tokens:List<String>){tokens.forEach{session[it]=true}}
    fun policyState()=value.copy(ignored=value.ignored+session.keys)
    suspend fun update(change:(NoticeState)->NoticeState)=withContext(Dispatchers.IO){lock.withLock{
        val next=change(state.value);check(preferences.edit().putString("state",next.encode()).commit()){"Bildirim tercihi kaydedilemedi"};state.value=next;if(next==NoticeState())session.clear()
    }}
    suspend fun decide(notice:UpdateNotice,decision:NoticeDecision)=update{it.decide(notice.tokens,decision,System.currentTimeMillis())}
}
