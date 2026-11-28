package app.filmmerkezi.tv.core

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

internal class CatalogVisit(@Volatile var visible:Boolean):AbstractCoroutineContextElement(Key){
    companion object Key:CoroutineContext.Key<CatalogVisit>
}

/** The visible catalog gets the first free slot. Playback can park all catalog reads. */
internal class CatalogQueue(private val capacity:Int=2){
    private class Ticket(val visit:CatalogVisit?)
    private val lock=Any()
    private val waiting=mutableListOf<Ticket>()
    private val revision=MutableStateFlow(0L)
    private var active=0
    private var paused=false
    private val calls=mutableSetOf<Job>()
    private class PlaybackPriority:CancellationException()

    fun prioritizePlayback(value:Boolean) {
        val parked=synchronized(lock){
            if(paused==value)return
            paused=value
            revision.value++
            if(value) calls.toList() else emptyList()
        }
        parked.forEach{it.cancel(PlaybackPriority())}
    }

    suspend fun <T> withSlot(block:suspend()->T):T {
        while(true){
            try{return runSlot(block)}
            catch(_:PlaybackPriority){currentCoroutineContext().ensureActive()}
        }
    }

    private suspend fun <T> runSlot(block:suspend()->T):T {
        val context=currentCoroutineContext()
        val ticket=Ticket(context[CatalogVisit])
        var granted=false
        synchronized(lock){waiting.add(ticket)}
        try{
            while(!granted){
                context.ensureActive()
                val seen=synchronized(lock){
                    val next=waiting.firstOrNull{it.visit?.visible==true}?:waiting.firstOrNull()
                    if(!paused&&active<capacity&&next===ticket){
                        waiting.remove(ticket);active++;granted=true;revision.value++
                    }
                    revision.value
                }
                if(!granted)revision.first{it!=seen}
            }
            context.ensureActive()
            return coroutineScope{
                val call=async(start=CoroutineStart.LAZY){block()}
                synchronized(lock){if(paused)call.cancel(PlaybackPriority())else calls.add(call)}
                try{call.await()}finally{synchronized(lock){calls.remove(call)}}
            }
        }finally{
            synchronized(lock){
                if(granted)active-- else waiting.remove(ticket)
                revision.value++
            }
        }
    }
}
