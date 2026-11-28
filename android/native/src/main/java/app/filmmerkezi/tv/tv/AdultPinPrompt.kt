package app.filmmerkezi.tv.tv

import android.app.Activity
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.settings.AdultPin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AdultPinPrompt(private val activity:Activity,private val scope:CoroutineScope) {
    private val pin=AdultPin(activity)
    private var dialog:ClassicDialog?=null
    val configured get()=pin.configured

    fun requirePin(done:()->Unit){
        if(!pin.configured){create(done);return}
        if(pin.locked){message(R.string.pin_locked);return}
        input(R.string.pin_existing){value->
            run {
                when(withContext(Dispatchers.IO){pin.verify(value)}){
                    AdultPin.Result.ACCEPTED->done()
                    AdultPin.Result.WRONG->message(R.string.pin_wrong)
                    AdultPin.Result.LOCKED->message(R.string.pin_locked)
                }
            }
        }
    }

    fun change(done:()->Unit={}) {
        if(configured)requirePin{create(done)}else create(done)
    }

    fun close(){dialog?.dismiss()}

    private fun create(done:()->Unit){
        input(R.string.pin_new){first->
            input(R.string.pin_repeat){second->
                if(first!=second){message(R.string.pin_mismatch);return@input}
                run {withContext(Dispatchers.IO){pin.replace(first)};done()}
            }
        }
    }

    private fun input(title:Int,submit:(String)->Unit){
        val field=EditText(activity).apply{
            inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters=arrayOf(InputFilter.LengthFilter(4));setSingleLine()
            setHint(R.string.pin_hint);setTextColor(ClassicUi.foreground)
        }
        close()
        dialog=ClassicDialog.Builder(activity).setTitle(title).setView(field)
            .setNegativeButton(R.string.tv_cancel,null).setPositiveButton(android.R.string.ok){_,_->
                val value=field.text.toString()
                field.text.clear()
                if(value.matches(Regex("[0-9]{4}")))submit(value)else message(R.string.pin_hint)
            }.show()
    }

    private fun run(action:suspend()->Unit){scope.launch{
        try{action()}catch(e:CancellationException){throw e}catch(_:Exception){message(R.string.settings_saved_error)}
    }}
    private fun message(id:Int){Toast.makeText(activity,id,Toast.LENGTH_SHORT).show()}
}
