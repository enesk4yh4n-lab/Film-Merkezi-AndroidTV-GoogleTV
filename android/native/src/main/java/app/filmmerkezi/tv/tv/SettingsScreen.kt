package app.filmmerkezi.tv.tv

import android.content.Context
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DiffUtil
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Own native, recyclable two-column settings. No platform preference dialogs. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
internal class SettingsScreen(c:android.app.Activity,private val scope:CoroutineScope,private val repository:SettingsRepository,
    private val close:()->Unit,private val maintenance:SettingsMaintenance,private val sleepState:()->Int={0},private val setSleep:(Int)->Unit={}):LinearLayout(c) {
    private val activity=c
    private data class Row(val key:String,val title:String,val value:String="",val help:String="",val action:()->Unit)
    private var category="general";private var dialog:ClassicDialog?=null
    private val categoryViews=linkedMapOf<String,Button>()
    private val options=RecyclerView(c).apply{layoutManager=LinearLayoutManager(c);itemAnimator=null;setItemViewCacheSize(3)}
    private val adapter=Rows()
    private var task:Job?=null;private var cacheBytes=0L
    private val preview=androidx.media3.ui.SubtitleView(c).apply{setBackgroundColor(android.graphics.Color.BLACK);isFocusable=false}
    private var active=true;private val observer:Job
    private fun s(id:Int)=context.getString(id)
    private fun on(v:Boolean)=s(if(v)R.string.settings_on else R.string.settings_off)
    private fun u(v:Float)=ClassicUi.u(context,v)
    init {
        orientation=VERTICAL;setBackgroundColor(ClassicUi.background);setPadding(u(32f),u(16f),u(32f),u(12f))
        val top=LinearLayout(c).apply{gravity=Gravity.CENTER_VERTICAL}
        top.addView(ImageView(c).apply{setImageResource(R.drawable.classic_logo);contentDescription=s(R.string.native_app_name)},LayoutParams(u(42f),u(42f)))
        val version=c.packageManager.getPackageInfo(c.packageName,0).versionName.orEmpty()
        top.addView(ClassicUi.text(c,s(R.string.native_app_name)+"  v"+version+"  ·  "+s(R.string.settings_title),22f,bold=true),LayoutParams(0,u(48f),1f).apply{marginStart=u(10f)})
        val back=ClassicUi.button(c,s(R.string.tv_back)){back()};top.addView(back,LayoutParams(u(80f),u(34f)));addView(top)
        val content=LinearLayout(c);val categories=LinearLayout(c).apply{orientation=VERTICAL;setPadding(0,u(10f),u(14f),0)}
        val names=listOf("general" to R.string.settings_general,"catalog" to R.string.settings_catalog,"playback" to R.string.settings_playback,"video" to R.string.settings_video,"audio" to R.string.settings_audio,"remote" to R.string.settings_remote,"storage" to R.string.settings_storage,"about" to R.string.settings_about)
        names.forEach{(key,label)->val b=ClassicUi.button(c,s(label)){select(key,true)};b.tag="settings-category:$key";b.maxLines=2;b.gravity=Gravity.START or Gravity.CENTER_VERTICAL
            b.setOnFocusChangeListener{_,has->if(has)select(key,false)}
            b.setOnKeyListener{_,code,e->if(e.action==KeyEvent.ACTION_DOWN&&code==KeyEvent.KEYCODE_DPAD_RIGHT){focusOptions();true}else false}
            categoryViews[key]=b;categories.addView(b,LayoutParams(-1,u(44f)).apply{bottomMargin=u(5f)})}
        content.addView(ScrollView(c).apply{isFillViewport=true;addView(categories)},LayoutParams(u(250f),-1));options.adapter=adapter
        val right=LinearLayout(c).apply{orientation=VERTICAL;addView(preview,LayoutParams(-1,u(76f)));addView(options,LayoutParams(-1,0,1f))}
        content.addView(right,LayoutParams(0,-1,1f));addView(content,LayoutParams(-1,0,1f))
        addView(ClassicUi.text(c,s(R.string.settings_help),10f,ClassicUi.muted),LayoutParams(-1,u(24f)))
        back.nextFocusDownId=categoryViews.values.first().id;select(category,false)
        observer=scope.launch{repository.changes.collect{if(active)draw()}}
        post{categoryViews[category]?.requestFocus()}
    }
    private fun select(key:String,focus:Boolean){val changed=category!=key;category=key;if(changed&&key=="storage")scope.launch{cacheBytes=maintenance.cacheBytes();if(active&&category=="storage")draw()};categoryViews.forEach{(k,b)->b.isSelected=k==key};if(changed||adapter.rows.isEmpty())draw();if(focus)focusOptions()}
    private fun focusOptions(){options.scrollToPosition(0);options.post{options.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()?:options.requestFocus()}}
    private fun update(change:(AppSettings)->AppSettings){scope.launch{
        try{repository.update(change)}catch(e:CancellationException){throw e}catch(_:Exception){if(active){dialog?.dismiss();dialog=ClassicDialog.Builder(activity).setMessage(R.string.settings_saved_error).setPositiveButton(R.string.settings_retry){_,_->update(change)}.setNegativeButton(R.string.tv_cancel,null).show()}}
    }}
    private fun choose(title:Int,labels:List<String>,selected:Int,action:(Int)->Unit){dialog=ClassicDialog.Builder(activity).setTitle(title).setSingleChoiceItems(labels.toTypedArray(),selected){d,i->d.dismiss();action(i)}.setNegativeButton(R.string.tv_cancel,null).show()}
    private fun draw(){
        val v=repository.value;val rows=mutableListOf<Row>()
        fun row(key:String,title:Int,value:String="",help:String="",action:()->Unit){rows.add(Row(key,s(title),value,help,action))}
        when(category){
            "general"->{
                val screens=listOf("HOME","MOVIES","SERIES","DISCOVER","LIBRARY","HISTORY")
                val labels=listOf(R.string.tv_home,R.string.tv_movies,R.string.tv_series,R.string.classic_discover,R.string.tv_my_list,R.string.tv_history).map(::s)
                row("start",R.string.settings_start,labels[screens.indexOf(v.startScreen).coerceAtLeast(0)]){choose(R.string.settings_start,labels,screens.indexOf(v.startScreen)){i->update{it.copy(startScreen=screens[i])}}}
                row("font",R.string.settings_font,s(if(v.largeText)R.string.settings_large else R.string.settings_normal)){update{it.copy(largeText=!it.largeText)}}
                row("exit",R.string.settings_exit,on(v.exitConfirmation)){update{it.copy(exitConfirmation=!it.exitConfirmation)}}
                row("rows",R.string.settings_rows){showRows()}
            }
            "catalog"->{
                row("merge",R.string.settings_merge,on(v.merge),s(R.string.settings_merge_help)){update{it.copy(merge=!it.merge)}}
                row("adult",R.string.settings_adult,on(v.hideAdult),s(R.string.settings_adult_help)){update{it.copy(hideAdult=!it.hideAdult)}}
                row("site-management",R.string.site_manager_title,help=s(R.string.site_manager_help)){manageSites()}
                v.siteOrder.forEach{id->val site=Sites.get(id);rows.add(Row("site:$id",site.name,context.getString(R.string.settings_site_state,on(id !in v.disabledCatalog),on(v.sourceAllowed(id)))){site(id)})}
            }
            "about"->{
                row("updates",R.string.updates_title){DistributionPanel(activity,scope,maintenance.playing).open()}
                row("transfer",R.string.transfer_title,help=s(R.string.transfer_help)){(activity as TvActivity).openDataTransfer()}
                row("about",R.string.settings_licenses){information(R.string.settings_licenses){maintenance.licenses()}}
                row("device",R.string.settings_device,android.os.Build.MODEL+" · API "+android.os.Build.VERSION.SDK_INT){information(R.string.settings_device){maintenance.deviceInfo()}}
                row("diagnostics",R.string.tv_player_diagnostics,on(v.diagnostics)){update{it.copy(diagnostics=!it.diagnostics)}}
                row("local-log",R.string.settings_local_log){information(R.string.settings_local_log){maintenance.playbackLog()}}
                row("connection",R.string.settings_connection,help=s(R.string.settings_connection_help)){information(R.string.settings_connection){maintenance.connections(repository.value)}}
            }
            "remote"->{
                val steps=listOf(5,10,15,30)
                row("seek",R.string.settings_seek,context.getString(R.string.settings_seconds,v.player.seekSeconds)){choose(R.string.settings_seek,steps.map{context.getString(R.string.settings_seconds,it)},steps.indexOf(v.player.seekSeconds)){i->update{it.copy(player=it.player.copy(seekSeconds=steps[i]))}}}
                row("accelerate",R.string.settings_accelerate,on(v.player.accelerate)){update{it.copy(player=it.player.copy(accelerate=!it.player.accelerate))}}
                val seconds=listOf(0,3,5,10)
                row("controls",R.string.settings_controls,if(v.player.controlsSeconds==0)s(R.string.settings_manual_hide)else context.getString(R.string.settings_seconds,v.player.controlsSeconds)){choose(R.string.settings_controls,seconds.map{if(it==0)s(R.string.settings_manual_hide)else context.getString(R.string.settings_seconds,it)},seconds.indexOf(v.player.controlsSeconds)){i->update{it.copy(player=it.player.copy(controlsSeconds=seconds[i]))}}}
            }
            "storage"->{
                val profiles=app.filmmerkezi.tv.BufferProfile.entries;val labels=listOf(R.string.tv_player_auto,R.string.settings_buffer_low,R.string.settings_buffer_long).map(::s)
                row("buffer",R.string.settings_buffer,labels[profiles.indexOf(v.player.buffer)],s(R.string.settings_next_playback)){choose(R.string.settings_buffer,labels,profiles.indexOf(v.player.buffer)){i->update{it.copy(player=it.player.copy(buffer=profiles[i]))}}}
                row("cache",R.string.settings_clear_cache,context.getString(R.string.settings_cache_size,cacheBytes/1048576.0)){confirm("cache",R.string.settings_clear_cache,R.string.settings_clear_cache_help)}
                row("search",R.string.settings_clear_search){confirm("search",R.string.settings_clear_search,R.string.settings_clear_search_help)}
                row("history",R.string.settings_clear_history,help=s(R.string.settings_clear_history_help)){confirm("history",R.string.settings_clear_history,R.string.settings_clear_history_help)}
                row("overrides",R.string.settings_clear_overrides){confirm("overrides",R.string.settings_clear_overrides,R.string.settings_clear_overrides_help)}
                row("reset",R.string.settings_reset){confirm("reset",R.string.settings_reset,R.string.settings_reset_help)}
            }
            "playback"->{
                row("automatic",R.string.pool_automatic,on(v.automaticSources)){update{it.copy(automaticSources=!it.automaticSources)}}
                row("cross",R.string.pool_cross_site,on(v.crossSiteSources)){update{it.copy(crossSiteSources=!it.crossSiteSources)}}
                val entries=listOf("ASK","RESUME","RESTART");val labels=listOf(R.string.settings_ask,R.string.settings_resume,R.string.settings_restart).map(::s)
                row("entry",R.string.settings_entry,labels[entries.indexOf(v.entry)]){choose(R.string.settings_entry,labels,entries.indexOf(v.entry)){i->update{it.copy(entry=entries[i])}}}
                row("resume-rewind",R.string.settings_rewind_resume,context.getString(R.string.settings_seconds,v.resumeRewind)){val values=listOf(0,5,10);choose(R.string.settings_rewind_resume,values.map{context.getString(R.string.settings_seconds,it)},values.indexOf(v.resumeRewind)){i->update{it.copy(resumeRewind=values[i])}}}
                row("next",R.string.tv_player_autonext,on(v.autoNext)){update{it.copy(autoNext=!it.autoNext)}}
                row("sleep",R.string.settings_sleep,if(sleepState()==0)s(R.string.settings_off)else context.getString(R.string.settings_minutes,sleepState()),s(R.string.settings_sleep_help)){
                    val values=listOf(0,30,60,90,120);choose(R.string.settings_sleep,values.map{if(it==0)s(R.string.settings_off)else context.getString(R.string.settings_minutes,it)},values.indexOf(sleepState()).coerceAtLeast(0)){i->setSleep(values[i]);draw()}
                }
            }
            "video"->{
                fun height(n:Int,automatic:Boolean)=if(n==0)s(if(automatic)R.string.tv_player_auto else R.string.settings_unlimited)else "${n}p"
                row("quality",R.string.settings_default_quality,height(v.player.preferredHeight,true)){choose(R.string.settings_default_quality,SettingsCodec.heights.map{height(it,true)},SettingsCodec.heights.indexOf(v.player.preferredHeight)){i->update{it.copy(player=it.player.copy(preferredHeight=SettingsCodec.heights[i]))}}}
                row("max-height",R.string.settings_max_height,height(v.player.autoMaxHeight,false),s(R.string.settings_auto_limit_help)){choose(R.string.settings_max_height,SettingsCodec.heights.map{height(it,false)},SettingsCodec.heights.indexOf(v.player.autoMaxHeight)){i->update{it.copy(player=it.player.copy(autoMaxHeight=SettingsCodec.heights[i]))}}}
                row("max-fps",R.string.settings_max_fps,if(v.player.autoMaxFps==0)s(R.string.settings_unlimited)else "${v.player.autoMaxFps} FPS",s(R.string.settings_auto_limit_help)){choose(R.string.settings_max_fps,SettingsCodec.frames.map{if(it==0)s(R.string.settings_unlimited)else "$it FPS"},SettingsCodec.frames.indexOf(v.player.autoMaxFps)){i->update{it.copy(player=it.player.copy(autoMaxFps=SettingsCodec.frames[i]))}}}
                val sizing=app.filmmerkezi.tv.VideoSizing.entries;val names=listOf(R.string.settings_fit,R.string.settings_zoom,R.string.settings_stretch).map(::s)
                row("sizing",R.string.settings_sizing,names[sizing.indexOf(v.player.sizing)]){choose(R.string.settings_sizing,names,sizing.indexOf(v.player.sizing)){i->update{it.copy(player=it.player.copy(sizing=sizing[i]))}}}
            }
            "audio"->{
                row("source-audio",R.string.settings_source_audio,on(v.player.sourceAudio)){update{it.copy(player=it.player.copy(sourceAudio=!it.player.sourceAudio))}}
                row("audio",R.string.settings_audio_first,languageName(v.player.audio)){chooseLanguage(R.string.settings_audio_first,v.player.audio){lang->update{it.copy(player=it.player.copy(audio=lang))}}}
                row("second-audio",R.string.settings_audio_second,languageName(v.player.secondAudio)){chooseLanguage(R.string.settings_audio_second,v.player.secondAudio){lang->update{it.copy(player=it.player.copy(secondAudio=lang))}}}
                row("text",R.string.settings_text_language,languageName(v.player.text)){chooseLanguage(R.string.settings_text_language,v.player.text){lang->update{it.copy(player=it.player.copy(text=lang))}}}
                val modes=app.filmmerkezi.tv.SubtitlePolicy.entries;val labels=listOf(R.string.settings_subtitle_auto,R.string.settings_on,R.string.settings_off).map(::s)
                row("subtitles",R.string.settings_subtitle_policy,labels[modes.indexOf(v.player.subtitles)]){choose(R.string.settings_subtitle_policy,labels,modes.indexOf(v.player.subtitles)){i->update{it.copy(player=it.player.copy(subtitles=modes[i]))}}}
                row("caption-style",R.string.settings_subtitle_look,s(if(v.player.customSubtitles)R.string.settings_custom_style else R.string.settings_source_style),s(R.string.settings_caption_help)){update{it.copy(player=it.player.copy(customSubtitles=!it.player.customSubtitles))}}
                if(v.player.customSubtitles){
                    val sizes=listOf(R.string.settings_small,R.string.settings_normal,R.string.settings_large,R.string.settings_very_large).map(::s)
                    row("caption-size",R.string.settings_subtitle_size,sizes[v.player.subtitleSize]){choose(R.string.settings_subtitle_size,sizes,v.player.subtitleSize){i->update{it.copy(player=it.player.copy(subtitleSize=i))}}}
                    row("caption-color",R.string.settings_subtitle_color,s(if(v.player.subtitleYellow)R.string.settings_yellow else R.string.settings_white)){update{it.copy(player=it.player.copy(subtitleYellow=!it.player.subtitleYellow))}}
                    row("caption-outline",R.string.settings_outline,on(v.player.subtitleOutline)){update{it.copy(player=it.player.copy(subtitleOutline=!it.player.subtitleOutline))}}
                    val backgrounds=listOf(R.string.settings_transparent,R.string.settings_half_black,R.string.settings_black).map(::s)
                    row("caption-background",R.string.settings_subtitle_background,backgrounds[v.player.subtitleBackground]){choose(R.string.settings_subtitle_background,backgrounds,v.player.subtitleBackground){i->update{it.copy(player=it.player.copy(subtitleBackground=i))}}}
                    val positions=listOf(5,8,12,16)
                    row("caption-bottom",R.string.settings_subtitle_bottom,"%"+v.player.subtitleBottom){choose(R.string.settings_subtitle_bottom,positions.map{"%$it"},positions.indexOf(v.player.subtitleBottom)){i->update{it.copy(player=it.player.copy(subtitleBottom=positions[i]))}}}
                }

            }
        }
        preview.visibility=if(category=="audio")VISIBLE else GONE
        if(category=="audio"){
            SubtitleAppearance.style(preview,v.player,resources.displayMetrics.heightPixels)
            preview.setBottomPaddingFraction(v.player.subtitleBottom/100f)
            preview.setCues(listOf(androidx.media3.common.text.Cue.Builder().setText(s(R.string.settings_subtitle_preview)).build()))
        }
        categoryViews.values.forEach{it.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,u(12f*(if(v.largeText)1.15f else 1f)).toFloat())}
        adapter.replace(rows)
    }
    private fun information(title:Int,load:suspend()->String){
        task?.cancel();dialog?.dismiss()
        val waiting=ClassicDialog.Builder(activity).setTitle(title).setMessage(R.string.settings_working).setNegativeButton(R.string.tv_cancel,null).show();dialog=waiting
        val origin=activity.currentFocus
        waiting.setOnDismissListener{task?.cancel();origin?.post{if(origin.isShown)origin.requestFocus()}}
        task=scope.launch{try{
            val value=load();if(active){waiting.setOnDismissListener(null);waiting.dismiss()
                dialog=ClassicDialog.Builder(activity).setTitle(title).setView(ClassicUi.text(context,value,11f).apply{isFocusable=true;setPadding(u(6f),0,u(6f),0)}).setPositiveButton(R.string.tv_back,null).show()
            }
        }catch(e:CancellationException){throw e}catch(_:Exception){if(active){waiting.setOnDismissListener(null);waiting.dismiss();dialog=ClassicDialog.Builder(activity).setMessage(R.string.settings_operation_error).setPositiveButton(R.string.tv_back,null).show()}}}
    }
    private fun confirm(key:String,title:Int,help:Int){
        if(key in listOf("history","overrides")&&maintenance.playing()){dialog=ClassicDialog.Builder(activity).setMessage(R.string.settings_stop_first).setPositiveButton(R.string.tv_back,null).show();return}
        dialog=ClassicDialog.Builder(activity).setTitle(title).setMessage(help).setPositiveButton(R.string.settings_apply){_,_->
            task=scope.launch{try{
                if(key=="reset")repository.update{old->AppSettings(siteAddresses=old.siteAddresses.keys.fold(old.siteAddresses){all,id->SiteAddresses.reset(all,id)})}else maintenance.run(key)
                cacheBytes=maintenance.cacheBytes();if(active){draw();dialog=ClassicDialog.Builder(activity).setMessage(R.string.settings_done).setPositiveButton(R.string.tv_back,null).show()}
            }catch(e:CancellationException){throw e}catch(_:Exception){if(active)dialog=ClassicDialog.Builder(activity).setMessage(R.string.settings_operation_error).setPositiveButton(R.string.tv_back,null).show()}}
        }.setNegativeButton(R.string.tv_cancel,null).show()
    }
    private fun languageName(code:String)=java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.forLanguageTag("tr")).ifBlank{code}
    private fun chooseLanguage(title:Int,current:String,save:(String)->Unit){
        val codes=(listOf("tr","en")+java.util.Locale.getISOLanguages().sortedBy{languageName(it)}).distinct()
        choose(title,codes.map(::languageName),codes.indexOf(current)){i->save(codes[i])}
    }
    private fun site(id:String){val v=repository.value;val labels=listOf(s(R.string.settings_site_catalog)+": "+on(id !in v.disabledCatalog),s(R.string.settings_site_sources)+": "+on(v.sourceAllowed(id)),s(R.string.settings_up),s(R.string.settings_down))
        dialog=ClassicDialog.Builder(activity).setTitle(Sites.get(id).name).setItems(labels.toTypedArray()){_,i->update{old->when(i){
            0->old.copy(disabledCatalog=if(id in old.disabledCatalog)old.disabledCatalog-id else old.disabledCatalog+id)
            1->old.copy(disabledSources=if(id in old.disabledSources)old.disabledSources-id else old.disabledSources+id)
            else->{val order=old.siteOrder.toMutableList();val at=order.indexOf(id);val target=(at+if(i==2)-1 else 1).coerceIn(0,order.lastIndex);java.util.Collections.swap(order,at,target);old.copy(siteOrder=order)}
        }}}.setNegativeButton(R.string.tv_back,null).show()
    }
    private fun manageSites(){
        val sites=repository.value.siteOrder.map(Sites::get)
        dialog=ClassicDialog.Builder(activity).setTitle(R.string.site_manager_title)
            .setItems(sites.map{it.name+"\n"+it.base}.toTypedArray()){_,i->editSite(sites[i].id)}.setNegativeButton(R.string.tv_back,null).show()
    }
    private fun editSite(id:String){
        val current=Sites.get(id);val original=Sites.default(id)
        val content=LinearLayout(context).apply{orientation=VERTICAL}
        fun field(label:Int,value:String,key:String,uri:Boolean=false):EditText {
            content.addView(ClassicUi.text(context,s(label),12f,ClassicUi.muted))
            return EditText(context).apply{tag=key;setText(value);setSingleLine(true);setSelectAllOnFocus(true)
                inputType=android.text.InputType.TYPE_CLASS_TEXT or if(uri)android.text.InputType.TYPE_TEXT_VARIATION_URI else android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
                setTextColor(ClassicUi.foreground);setTextSize(14f);setPadding(u(10f),u(6f),u(10f),u(6f));background=ClassicUi.focus(context);contentDescription=s(label)
                content.addView(this,LayoutParams(-1,u(40f)).apply{bottomMargin=u(10f)})}
        }
        val name=field(R.string.site_manager_name,current.name,"site-manager-name")
        val address=field(R.string.site_manager_address,current.base,"site-manager-address",true)
        val status=ClassicUi.text(context,s(R.string.site_manager_help),11f,ClassicUi.muted).apply{tag="site-manager-status"}
        val actions=LinearLayout(context)
        lateinit var window:ClassicDialog
        var check:Job?=null;var saving=false;var revision=0
        val test=ClassicUi.button(activity,s(R.string.site_manager_test)){
            check?.cancel();val version=++revision
            val base=try{SiteAddresses.normalize(address.text.toString())}catch(e:IllegalArgumentException){status.text=e.message;return@button}
            status.text=s(R.string.tv_loading)
            check=scope.launch{try{val result=maintenance.checkSite(current,base);if(active&&window.isShowing&&revision==version)status.text=result}
                catch(e:CancellationException){throw e}catch(e:Exception){if(active&&window.isShowing&&revision==version)status.text=context.getString(R.string.site_manager_test_failed,e.message.orEmpty())}}
        }.apply{tag="site-manager-test"}
        val reset=ClassicUi.button(activity,s(R.string.site_manager_default)){
            check?.cancel();revision++;name.setText(original.name);address.setText(original.base);status.text=s(R.string.site_manager_pending)
        }.apply{tag="site-manager-reset"}
        actions.addView(test,LayoutParams(0,u(38f),1f).apply{marginEnd=u(8f)});actions.addView(reset,LayoutParams(0,u(38f),1f))
        content.addView(actions);content.addView(status,LayoutParams(-1,-2).apply{topMargin=u(8f)})
        val watcher=object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){check?.cancel();revision++;status.text=this@SettingsScreen.s(R.string.site_manager_pending)}
            override fun afterTextChanged(s:android.text.Editable?){}
        }
        name.addTextChangedListener(watcher);address.addTextChangedListener(watcher)
        window=ClassicDialog.Builder(activity).setTitle(current.name).setView(content).setPositiveButton(R.string.settings_apply,null)
            .setNegativeButton(R.string.tv_cancel){_,_->manageSites()}.show();dialog=window
        window.setOnCancelListener{if(!saving)manageSites()}
        window.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener{
            if(saving)return@setOnClickListener
            val updated=try{SiteAddresses.change(repository.value.siteAddresses,id,address.text.toString(),name.text.toString())}catch(e:IllegalArgumentException){status.text=e.message;return@setOnClickListener}
            check?.cancel();saving=true;window.setCancelable(false);name.isEnabled=false;address.isEnabled=false;test.isEnabled=false;reset.isEnabled=false
            window.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled=false
            task=scope.launch{try{
                repository.update{it.copy(siteAddresses=updated)}
                if(active){window.dismiss();manageSites()}
            }catch(e:CancellationException){throw e}catch(_:Exception){if(active&&window.isShowing){status.text=s(R.string.settings_saved_error)}}
                finally{saving=false;window.setCancelable(true);name.isEnabled=true;address.isEnabled=true;test.isEnabled=true;reset.isEnabled=true;window.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled=true}}
        }
        window.setOnDismissListener{check?.cancel();revision++;if(active)post{options.findViewWithTag<View>("setting:site-management")?.requestFocus()}}
    }
    private fun showRows(){val v=repository.value;val names=mapOf("continue" to R.string.tv_continue,"following" to R.string.fm_following,"movies" to R.string.classic_new_movies,"series" to R.string.classic_new_series,"library" to R.string.tv_my_list)
        dialog=ClassicDialog.Builder(activity).setTitle(R.string.settings_rows).setItems(v.rowOrder.map{(if(it in v.hiddenRows)""else "✓ ")+s(names.getValue(it))}.toTypedArray()){_,i->val id=v.rowOrder[i]
            dialog=ClassicDialog.Builder(activity).setTitle(names.getValue(id)).setItems(arrayOf(s(R.string.settings_visibility),s(R.string.settings_up),s(R.string.settings_down))){_,action->update{old->if(action==0)old.copy(hiddenRows=if(id in old.hiddenRows)old.hiddenRows-id else old.hiddenRows+id)else{val list=old.rowOrder.toMutableList();val at=list.indexOf(id);java.util.Collections.swap(list,at,(at+if(action==1)-1 else 1).coerceIn(0,list.lastIndex));old.copy(rowOrder=list)}}}.setNegativeButton(R.string.tv_back,null).show()
        }.setNegativeButton(R.string.tv_back,null).show()
    }
    fun back(){if(dialog?.isShowing==true){dialog?.dismiss();return};dispose();close()}
    internal fun dialogForTest()=dialog
    fun dispose(){active=false;observer.cancel();task?.cancel();dialog?.dismiss()}
    private inner class Holder(val box:LinearLayout,val title:TextView,val value:TextView,val help:TextView):RecyclerView.ViewHolder(box)
    private inner class Rows:RecyclerView.Adapter<Holder>() {
        var rows=emptyList<Row>();private set
        private var large=repository.value.largeText
        init{setHasStableIds(true)}
        override fun getItemCount()=rows.size
        override fun getItemId(position:Int)=TextKey.hash(rows[position].key).take(15).toLong(16)
        override fun onCreateViewHolder(parent:ViewGroup,type:Int):Holder {
            val box=LinearLayout(context).apply{orientation=VERTICAL;isFocusable=true;isFocusableInTouchMode=true;setPadding(u(14f),u(11f),u(14f),u(11f));background=ClassicUi.focus(context);layoutParams=RecyclerView.LayoutParams(-1,-2).apply{bottomMargin=u(6f)}}
            val title=ClassicUi.text(context,"",14f,bold=true);val value=ClassicUi.text(context,"",12f,ClassicUi.gold);val help=ClassicUi.text(context,"",10f,ClassicUi.muted)
            box.addView(title);box.addView(value);box.addView(help)
            box.setOnKeyListener{_,key,e->if(e.action==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_LEFT){categoryViews[category]?.requestFocus();true}else false}
            return Holder(box,title,value,help)
        }
        override fun onBindViewHolder(h:Holder,position:Int){val r=rows[position];h.box.tag="setting:"+r.key;h.title.text=r.title;h.value.text=r.value;h.value.visibility=if(r.value.isBlank())GONE else VISIBLE;h.help.text=r.help;h.help.visibility=if(r.help.isBlank())GONE else VISIBLE;val scale=if(repository.value.largeText)1.15f else 1f;h.title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,u(14f*scale).toFloat());h.value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,u(12f*scale).toFloat());h.help.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,u(10f*scale).toFloat());h.box.setOnClickListener{rows.firstOrNull{it.key==r.key}?.action?.invoke()}}
        fun replace(next:List<Row>){val fontChanged=large!=repository.value.largeText;large=repository.value.largeText;val old=rows;val diff=DiffUtil.calculateDiff(object:DiffUtil.Callback(){override fun getOldListSize()=old.size;override fun getNewListSize()=next.size;override fun areItemsTheSame(a:Int,b:Int)=old[a].key==next[b].key;override fun areContentsTheSame(a:Int,b:Int)=old[a].title==next[b].title&&old[a].value==next[b].value&&old[a].help==next[b].help&&!fontChanged});rows=next;diff.dispatchUpdatesTo(this)}
    }
}
