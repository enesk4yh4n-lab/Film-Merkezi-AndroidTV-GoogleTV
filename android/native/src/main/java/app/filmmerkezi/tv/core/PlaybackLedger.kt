package app.filmmerkezi.tv.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class PlaybackCheckpoint(val token:String,val card:Card,val positionMs:Long=0,val durationMs:Long=0)
enum class PlaybackFinish { SAVE,DISCARD }

/** Serialized progress writes plus a durable, idempotent rollback intent across the two stores. */
class PlaybackLedger(private val library:LibraryStore,private val series:SeriesTrackingStore,
    private val canonical:suspend(Card)->Card={it},private val parent:suspend(Card)->Card?={null}) {
    private val lock=Mutex();private var recovered=false
    private suspend fun recoverLocked(){if(recovered)return
        for((token,payload,state)in library.playbackJournals()){
            if(state=="discard"){
                val mark=library.restorePlayback(token,canonical(Contracts.readCard(payload.getJSONObject("card"))))
                restoreMark(mark)
            }
            library.endPlayback(token) // An abandoned open session retains its autosaved progress.
        };recovered=true
    }
    suspend fun recover()=lock.withLock{recoverLocked()}
    private suspend fun seriesParent(c:Card):Card? {
        val p=parent(c)?.let{canonical(it)}?:return null
        if(c.seriesIdentity.isNotBlank())series.rekey(c.seriesIdentity,p)
        return p
    }
    private suspend fun restoreMark(mark:JSONObject){
        if(mark.has("parent")){
            val old=Contracts.readCard(mark.getJSONObject("parent"));val now=canonical(old)
            series.rekey(ContentIdentity.key(old),now);mark.put("parent",Contracts.card(now))
        }
        series.restorePlaybackMark(mark)
    }
    suspend fun begin(c:Card):PlaybackCheckpoint=lock.withLock{
        recoverLocked();val card=canonical(c)
        val p=if(card.ref.kind==ContentKind.EPISODE&&card.episodeNumber!=null)seriesParent(card)else null
        library.beginPlayback(card,if(p!=null)series.playbackMark(p,checkNotNull(card.episodeNumber))else JSONObject())
    }
    private suspend fun write(c:Card,position:Long,duration:Long){
        val card=canonical(c);library.update(card,positionMs=position,durationMs=duration)
        if(card.ref.kind==ContentKind.EPISODE&&card.episodeNumber!=null&&duration>0&&position>=duration*95/100){
            seriesParent(card)?.let{series.mark(it,listOf(card.episodeNumber),true,false)}
        }
    }
    suspend fun save(cp:PlaybackCheckpoint,position:Long,duration:Long)=lock.withLock{
        recoverLocked();if(library.playbackOpen(cp.token))write(cp.card,position,duration)
    }
    /** No progress was written: close only the journal, never restore an older snapshot. */
    suspend fun abandon(cp:PlaybackCheckpoint)=lock.withLock{recoverLocked();library.endPlayback(cp.token)}
    suspend fun cancelFinish(cp:PlaybackCheckpoint)=lock.withLock{library.cancelDiscard(cp.token)}
    suspend fun finish(cp:PlaybackCheckpoint,mode:PlaybackFinish,position:Long,duration:Long)=lock.withLock{
        recoverLocked()
        if(library.playbackJournals().none{it.first==cp.token})return@withLock
        if(mode==PlaybackFinish.DISCARD){library.discardIntent(cp.token);val mark=library.restorePlayback(cp.token,canonical(cp.card));restoreMark(mark)}
        else write(cp.card,position,duration)
        library.endPlayback(cp.token)
    }
}
