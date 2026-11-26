package app.filmmerkezi.tv.updates

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.view.Gravity

/** A short, visible separate process restarts the host after its class loaders are gone. */
class RestartActivity:Activity() {
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply{text=getString(app.filmmerkezi.tv.R.string.updates_restarting);setTextColor(0xFFFFFFFF.toInt());setBackgroundColor(0xFF0A101C.toInt());gravity=Gravity.CENTER})
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this,app.filmmerkezi.tv.tv.TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            Handler(Looper.getMainLooper()).postDelayed({android.os.Process.killProcess(android.os.Process.myPid())},1000)
        },800)
    }
}
