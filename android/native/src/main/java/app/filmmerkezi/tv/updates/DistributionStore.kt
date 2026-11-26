package app.filmmerkezi.tv.updates

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Unknown/corrupt state is never silently replaced. One atomic commit owns the package set. */
class DistributionStore(context:Context,name:String="distribution") {
    val directory=java.io.File(context.filesDir,name).apply{mkdirs()}
    private val file=AtomicFile(java.io.File(directory,"state.json"))
    private val lock=Mutex()
    var failure:String?=null;private set
    @Volatile private var current:JSONObject=try{if(file.baseFile.exists())JSONObject(file.openRead().use{it.readBytes().toString(Charsets.UTF_8)}).also{
        require(it.getInt("schemaVersion")==1){"Güncelleme kaydı sürümü desteklenmiyor; dosya korundu"}
    }else empty()}catch(e:Exception){failure=e.message?:"Güncelleme kaydı okunamadı; dosya korundu";empty()}
    private fun empty()=JSONObject().put("schemaVersion",1).put("repositories",JSONObject()).put("packages",JSONObject())
    fun snapshot()=JSONObject(current.toString())
    suspend fun commit(change:(JSONObject)->Unit)=lock.withLock{
        check(failure==null){failure.orEmpty()};val next=snapshot();change(next);val bytes=next.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=2*1024*1024)
        val stream=file.startWrite();try{stream.write(bytes);file.finishWrite(stream);current=next}catch(e:Throwable){file.failWrite(stream);throw e}
    }
}
