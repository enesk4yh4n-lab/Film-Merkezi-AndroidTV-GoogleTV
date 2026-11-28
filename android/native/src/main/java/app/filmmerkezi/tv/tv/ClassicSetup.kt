package app.filmmerkezi.tv.tv

import android.app.Activity
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.settings.SettingsRepository
import app.filmmerkezi.tv.updates.DistributionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ClassicSetup(
    private val activity:Activity,
    private val scope:CoroutineScope,
    private val settings:SettingsRepository,
    private val finish:()->Unit
):LinearLayout(activity) {
    private val prefs=UiPreferences(activity)
    private val distribution=DistributionManager.get(activity)
    private var panel:DistributionPanel?=null
    private val pin=AdultPinPrompt(activity,scope)
    private fun s(id:Int)=activity.getString(id)
    private fun u(value:Float)=ClassicUi.u(activity,value)

    init {
        orientation=VERTICAL
        setPadding(u(38f),u(20f),u(38f),u(16f))
        setBackgroundColor(ClassicUi.background)
        draw()
    }

    private fun move(step:Int){
        if(prefs.step(step))draw()else error()
    }

    private fun error(){Toast.makeText(activity,R.string.settings_saved_error,Toast.LENGTH_SHORT).show()}
    private fun complete(){
        if(prefs.classic())finish()else error()
    }

    private fun brand(){
        val bar=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL}
        bar.addView(ImageView(activity).apply{setImageResource(R.drawable.classic_logo)},LayoutParams(u(36f),u(36f)))
        @Suppress("DEPRECATION")
        val version=activity.packageManager.getPackageInfo(activity.packageName,0).versionName.orEmpty()
        bar.addView(ClassicUi.text(activity,s(R.string.native_app_name)+"  v"+version,19f,bold=true),
            LayoutParams(-2,u(43f)).apply{leftMargin=u(9f)})
        addView(bar)
    }

    private fun title(heading:Int){
        brand()
        addView(ClassicUi.text(activity,s(heading),24f,ClassicUi.gold,true),LayoutParams(-1,u(51f)))
    }

    fun draw(){
        panel?.dispose();panel=null
        removeAllViews()
        val step=prefs.step
        if(step==2||step==4&&distribution.store.repositoryCount>0){
            packagePanel(step==2)
            return
        }
        val heading=listOf(R.string.setup_language,R.string.setup_usage,R.string.updates_add_repository,
            R.string.settings_catalog,R.string.hub_catalog)[step]
        title(heading)
        val actions=LinearLayout(activity).apply{orientation=VERTICAL}
        var focused=false
        fun action(label:String, id:Int=android.view.View.generateViewId(), primary:Boolean=false, run:()->Unit){
            val b=ClassicUi.button(activity,label,id,goldFill=primary,action=run)
            actions.addView(b,LayoutParams(u(320f),u(37f)).apply{topMargin=u(7f)})
            if(!focused){focused=true;b.requestFocus()}
        }
        fun message(id:Int){addView(ClassicUi.text(activity,s(id),13f,ClassicUi.muted),
            LayoutParams(-1,-2).apply{bottomMargin=u(10f)})}
        when(step){
            0->{
                message(R.string.setup_language_help)
                listOf("tr" to "Türkçe","en" to "English").forEach{(code,label)->
                    action(label,primary=prefs.language==code){
                        val changed=prefs.language!=code
                        if(!prefs.language(code)||!prefs.step(1)){error();return@action}
                        if(changed)activity.recreate()else draw()
                    }
                }
            }
            1->{message(R.string.setup_usage_help);action(s(R.string.setup_next),primary=true){move(2)}}
            3->{
                message(R.string.setup_catalog_help)
                action(s(R.string.settings_merge)+" · "+s(if(settings.value.merge)R.string.settings_on else R.string.settings_off)){
                    update{it.copy(merge=!it.merge)}
                }
                action(s(R.string.setup_search_suggestions)+" · "+s(if(settings.value.searchSuggestions)R.string.settings_on else R.string.settings_off)){
                    update{it.copy(searchSuggestions=!it.searchSuggestions)}
                }
                action(s(R.string.setup_next),primary=true){move(4)}
                if(!pin.configured)action(s(R.string.pin_new)){pin.change(::draw)}
            }
            else->{message(R.string.setup_skip_repository_help);action(s(R.string.classic_setup_start),R.id.classic_setup_start,true,::complete)}
        }
        addView(actions)
        actions.getChildAt(0)?.requestFocus()
    }

    private fun update(change:(app.filmmerkezi.tv.settings.AppSettings)->app.filmmerkezi.tv.settings.AppSettings){
        scope.launch{try{settings.update(change);draw()}catch(e:CancellationException){throw e}catch(_:Exception){error()}}
    }

    private fun packagePanel(repository:Boolean){
        brand()
        val next=DistributionPanel(activity,scope,{false},
            setupExit={move(if(repository)1 else 3)},setupInstalled=::complete,setupTrusted={move(3)},
            setupApplied={check(prefs.classic())},setupSkip=if(repository)({move(3)})else null)
        panel=next
        addView(next.view,LayoutParams(-1,0,1f))
        if(repository)next.openSetupRepositories()else next.openSetupPackages()
        if(!repository)next.view.focusContent()
    }

    fun back():Boolean {
        if(panel?.back()==true)return true
        if(prefs.step==0)return false
        move(prefs.step-1)
        return true
    }

    fun dispose(){panel?.dispose();pin.close()}
}
