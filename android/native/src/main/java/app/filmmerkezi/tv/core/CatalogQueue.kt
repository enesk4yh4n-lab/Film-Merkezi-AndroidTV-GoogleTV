package app.filmmerkezi.tv.core

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

internal class CatalogVisit(@Volatile var visible:Boolean):AbstractCoroutineContextElement(Key){
    companion object Key:CoroutineContext.Key<CatalogVisit>
}

/** Finish active provider calls, then give the visible screen first choice of the free slots. */
internal class CatalogQueue(private val capacity:Int=2){
    private class Ticket(val visit:CatalogVisit?)
    private val lock=Any()
    private val waiting=mutableListOf<Ticket>()
    private val revision=MutableStateFlow(0L)
    private var active=0

    suspend fun <T> withSlot(block:suspend()->T):T {
        val context=currentCoroutineContext()
        val ticket=Ticket(context[CatalogVisit])
        var granted=false
        synchronized(lock){waiting.add(ticket)}
        try{
            while(!granted){
                context.ensureActive()
                val seen=synchronized(lock){
                    val next=waiting.firstOrNull{it.visit?.visible==true}?:waiting.firstOrNull()
                    if(active<capacity&&next===ticket){
                        waiting.remove(ticket);active++;granted=true;revision.value++
                    }
                    revision.value
                }
                if(!granted)revision.first{it!=seen}
            }
            context.ensureActive()
            return block()
        }finally{
            synchronized(lock){
                if(granted)active-- else waiting.remove(ticket)
                revision.value++
            }
        }
    }
}
