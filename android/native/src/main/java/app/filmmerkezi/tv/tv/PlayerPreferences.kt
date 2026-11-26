package app.filmmerkezi.tv.tv

import android.content.Context
import app.filmmerkezi.tv.*
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.settings.*
import kotlinx.coroutines.*

/** Compatibility editor; durable state is owned only by SettingsRepository. */
internal class PlayerPreferences(context:Context) {
    val repository=SettingsRepository.get(context)
    private val app=context.applicationContext
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var sequence=0L
    private data class Edit(val id:Long,val apply:(AppSettings)->AppSettings)
    private val edits=mutableListOf<Edit>()
    private val value get()=synchronized(edits){edits.fold(repository.value){s,e->e.apply(s)}}
    private fun edit(change:(AppSettings)->AppSettings){synchronized(edits){edits.add(Edit(++sequence,change))}}
    var audio:String get()=value.player.audio;set(v){edit{it.copy(player=it.player.copy(audio=v))}}
    var text:String get()=value.player.text;set(v){edit{it.copy(player=it.player.copy(text=v))}}
    var textEnabled:Boolean get()=value.player.subtitles!=SubtitlePolicy.OFF;set(v){edit{it.copy(player=it.player.copy(subtitles=if(v)SubtitlePolicy.ON else SubtitlePolicy.OFF))}}
    var height:Int get()=value.player.preferredHeight;set(v){edit{it.copy(player=it.player.copy(preferredHeight=v))}}
    var diagnostics:Boolean get()=value.diagnostics;set(v){edit{it.copy(diagnostics=v)}}
    var autoNext:Boolean get()=value.autoNext;set(v){edit{it.copy(autoNext=v)}}
    var automaticSources:Boolean get()=value.automaticSources;set(v){edit{it.copy(automaticSources=v)}}
    var crossSiteSources:Boolean get()=value.crossSiteSources;set(v){edit{it.copy(crossSiteSources=v)}}
    fun applyTo(session:PlaybackSession){val v=value;session.configure(v.player);session.diagnosticsEnabled=v.diagnostics;session.automaticSources=v.automaticSources;session.crossSiteSources=v.crossSiteSources}
    fun save():Job {
        val captured=synchronized(edits){edits.toList()};val ids=captured.map{it.id}.toSet()
        return scope.launch{try{repository.update{base->captured.fold(base){s,e->e.apply(s)}}}
            catch(e:CancellationException){throw e}catch(_:Exception){android.os.Handler(android.os.Looper.getMainLooper()).post{android.widget.Toast.makeText(app,R.string.settings_saved_error,android.widget.Toast.LENGTH_LONG).show()}}
            finally{synchronized(edits){edits.removeAll{it.id in ids}}}}
    }
}
