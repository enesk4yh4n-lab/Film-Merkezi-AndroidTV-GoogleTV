package app.filmmerkezi.tv.tv

import android.app.Activity
import android.content.Intent
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.updates.*
import kotlinx.coroutines.*
import java.io.File

internal class DataTransferPanel(private val activity:TvActivity,private val scope:CoroutineScope,private val playing:()->Boolean) {
    companion object{const val EXPORT=4121;const val IMPORT=4122}
    private val store=DataTransferStore(activity)
    private var exported:File?=null
    private var panel:ClassicOverlay?=null
    private var busy=false
    private fun message(value:String){ClassicDialog.Builder(activity).setTitle(R.string.transfer_title).setMessage(value).setPositiveButton(android.R.string.ok,null).show()}
    fun open(){panel?.close();panel=ClassicOverlay(activity,activity.getString(R.string.transfer_title),side=true).also{p->p.canDismiss={!busy};p.update(listOf(ClassicRow("export",activity.getString(R.string.transfer_export)),ClassicRow("import",activity.getString(R.string.transfer_import)),ClassicRow("local",activity.getString(R.string.transfer_import_local))),message=activity.getString(R.string.transfer_help)){key->
        if(busy)return@update
        if(playing()){message(activity.getString(R.string.updates_busy_playing));return@update}
        if(key=="export")scope.launch{busy=true;try{activity.quiesceForDistribution();exported=store.export();try{activity.startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,"Film-Merkezi-Kayitlar.fmdata"),EXPORT)}catch(_:android.content.ActivityNotFoundException){message(activity.getString(R.string.transfer_picker_missing,exported!!.absolutePath))}}catch(e:Exception){message(e.message.orEmpty())}finally{busy=false}}
        else if(key=="local")scope.launch{busy=true;try{val file=File(activity.getExternalFilesDir(null),"migration-input.fmdata");if(!file.exists())message(activity.getString(R.string.transfer_local_missing,file.absolutePath))else confirm(store.prepare(file.inputStream()))}catch(e:Exception){message(e.message.orEmpty())}finally{busy=false}}
        else try{activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),IMPORT)}catch(_:android.content.ActivityNotFoundException){message(activity.getString(R.string.transfer_picker_missing,activity.getExternalFilesDir(null)?.absolutePath.orEmpty()))}
    }}}
    fun result(request:Int,result:Int,data:Intent?):Boolean {
        if(request !in listOf(EXPORT,IMPORT))return false
        val uri=data?.data;if(result!=Activity.RESULT_OK||uri==null)return true
        scope.launch{busy=true;try{if(request==EXPORT){withContext(Dispatchers.IO){val file=checkNotNull(exported);activity.contentResolver.openOutputStream(uri,"wt")!!.use{output->file.inputStream().use{check(it.copyTo(output)==file.length()){"Yedek tam kopyalanamadı"}}}};message(activity.getString(R.string.transfer_saved))}
            else confirm(withContext(Dispatchers.IO){store.prepare(activity.contentResolver.openInputStream(uri)!!)})
        }catch(e:CancellationException){throw e}catch(e:Exception){message(e.message.orEmpty())}finally{busy=false}};return true
    }
    private fun confirm(prepared:org.json.JSONObject){ClassicDialog.Builder(activity).setTitle(R.string.transfer_title).setMessage(activity.getString(R.string.transfer_confirm,prepared.getInt("rows")))
        .setPositiveButton(R.string.transfer_apply){_,_->scope.launch{busy=true;try{activity.quiesceForDistribution();withContext(Dispatchers.IO){store.schedule(prepared)};DistributionPanel(activity,scope,playing).restart()}catch(e:Exception){message(e.message.orEmpty())}finally{busy=false}}}.setNegativeButton(R.string.tv_cancel,null).show()}
}
