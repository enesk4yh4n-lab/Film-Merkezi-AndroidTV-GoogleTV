package app.filmmerkezi.tv.tv

import android.content.DialogInterface
import android.view.*
import android.widget.*
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.settings.AppSettings
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RemoteCatalogAuditTest {
    @Test fun catalogDetailsFormsAndSettingsUseDirectionalRemote()=runBlocking {
        val f=Settings110Fixture();val data=RemoteAuditData();val results=JSONArray()
        fun key(code:Int){f.ins.sendKeyDownUpSync(code);f.ins.waitForIdleSync()}
        suspend fun ready(block:()->Boolean)=f.waitFor(6000,block)
        suspend fun nav(id:Int){f.main{f.activity.findViewById<View>(id).requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER);delay(120)}
        suspend fun focus(id:Int){ready{f.activity.findViewById<View>(id)?.isShown==true};f.main{f.activity.findViewById<View>(id).requestFocus()}}
        suspend fun check(name:String,block:suspend()->Unit){try{block();results.put(JSONObject().put("case",name).put("passed",true))}catch(e:Throwable){results.put(JSONObject().put("case",name).put("passed",false).put("error",e.toString()))}}
        fun formFields(v:View):List<ClassicChoice> = if(v is ClassicChoice)listOf(v)else if(v is ViewGroup)(0 until v.childCount).flatMap{formFields(v.getChildAt(it))}else emptyList()
        try {
            f.start(AppSettings(),data)
            for(id in listOf(R.id.tv_nav_home,R.id.tv_nav_movies,R.id.tv_nav_series,R.id.tv_nav_discover,R.id.tv_nav_library,R.id.tv_nav_history)){
                nav(id)
                check("header_down_up_$id") {key(KeyEvent.KEYCODE_DPAD_DOWN);delay(100);f.main{assertNotNull(f.activity.currentFocus);assertTrue(f.activity.currentFocus!!.isShown)};key(KeyEvent.KEYCODE_DPAD_UP);delay(100);f.main{assertNotNull(f.activity.currentFocus)}}
            }
            nav(R.id.tv_nav_movies);ready{f.tag(data.movies.first().workId)!=null}
            check("grid_left_right_up_down") {
                focus(R.id.tv_nav_movies);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN)
                ready{f.tag(data.movies[0].workId)?.hasFocus()==true}
                key(KeyEvent.KEYCODE_DPAD_RIGHT);ready{f.tag(data.movies[1].workId)?.hasFocus()==true}
                key(KeyEvent.KEYCODE_DPAD_DOWN);ready{f.tag(data.movies[7].workId)?.hasFocus()==true}
                key(KeyEvent.KEYCODE_DPAD_LEFT);ready{f.tag(data.movies[6].workId)?.hasFocus()==true}
                key(KeyEvent.KEYCODE_DPAD_UP);ready{f.tag(data.movies[0].workId)?.hasFocus()==true}
            }
            check("movie_first_action_left_stays_in_action_row") {
                f.main{f.tag(data.movies.first().workId).requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER);ready{f.activity.findViewById<View>(R.id.tv_play_action)!=null}
                focus(R.id.tv_play_action);key(KeyEvent.KEYCODE_DPAD_LEFT);delay(100);f.main{assertEquals(R.id.tv_play_action,f.activity.currentFocus?.id)}
            }
            nav(R.id.tv_nav_discover);focus(R.id.classic_filter);key(KeyEvent.KEYCODE_DPAD_CENTER)
            ready{f.activity.siteDialogForTest()?.isShowing==true}
            val form=f.activity.siteDialogForTest()!!;val fields=formFields(form.window!!.decorView)
            check("filter_fields_do_not_jump_to_footer_on_left_right") {
                val field=fields.first().getChildAt(0);f.main{field.requestFocus()};key(KeyEvent.KEYCODE_DPAD_LEFT);f.main{assertSame(field,form.currentFocus)}
                key(KeyEvent.KEYCODE_DPAD_RIGHT);f.main{assertSame(field,form.currentFocus)}
            }
            check("filter_nested_choice_back_returns_to_field") {
                val field=fields.first().getChildAt(0);f.main{field.requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER)
                ready{fields.first().popupForTest()?.isShowing==true};key(KeyEvent.KEYCODE_BACK);ready{form.currentFocus===field}
            }
            check("filter_footer_does_not_jump_sideways_into_full_width_field") {
                val button=form.getButton(DialogInterface.BUTTON_POSITIVE);f.main{button.requestFocus()};key(KeyEvent.KEYCODE_DPAD_LEFT);f.main{assertSame(button,form.currentFocus)}
            }
            f.main{form.dismiss()};delay(120);focus(R.id.classic_sort);key(KeyEvent.KEYCODE_DPAD_CENTER)
            ready{f.activity.siteDialogForTest()?.isShowing==true}
            val sort=f.activity.siteDialogForTest()!!
            check("sort_list_left_right_stay_on_selected_row") {
                val first=sort.window!!.decorView.findViewWithTag<View>("classic-dialog-row:0");f.main{first.requestFocus()};key(KeyEvent.KEYCODE_DPAD_LEFT);f.main{assertSame(first,sort.currentFocus)}
                key(KeyEvent.KEYCODE_DPAD_RIGHT);f.main{assertSame(first,sort.currentFocus)}
            }
            f.main{sort.dismiss()};delay(120);nav(R.id.tv_nav_series);ready{f.tag(data.series.workId)!=null}
            f.main{f.tag(data.series.workId).requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER);ready{f.tag("season:1")!=null}
            check("season_left_right_and_episode_down") {
                f.main{f.tag("season:1").requestFocus()};key(KeyEvent.KEYCODE_DPAD_RIGHT);ready{f.tag("season:2").hasFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER)
                key(KeyEvent.KEYCODE_DPAD_DOWN);ready{f.tag(data.eps[12].card.ref.id)?.hasFocus()==true}
            }
            check("episode_action_right_edge_stays_in_episode") {
                val more=f.tag(data.eps[12].card.ref.id+":options");f.main{more.requestFocus()};key(KeyEvent.KEYCODE_DPAD_RIGHT);f.main{assertSame(more,f.activity.currentFocus)}
            }
            check("episode_vertical_keeps_options_column") {
                f.main{f.tag(data.eps[12].card.ref.id+":options").requestFocus()};key(KeyEvent.KEYCODE_DPAD_DOWN)
                ready{f.tag(data.eps[13].card.ref.id+":options")?.hasFocus()==true}
                key(KeyEvent.KEYCODE_DPAD_UP);ready{f.tag(data.eps[12].card.ref.id+":options")?.hasFocus()==true}
            }
            nav(R.id.tv_nav_home)
            f.main{f.activity.findViewById<EditText>(R.id.tv_query).setText("Deneme");f.activity.findViewById<View>(R.id.tv_search_submit).requestFocus()};key(KeyEvent.KEYCODE_DPAD_CENTER)
            ready{f.tag("search-type:ALL")!=null}
            check("search_navigation_to_types_and_grid") {focus(R.id.tv_nav_home);key(KeyEvent.KEYCODE_DPAD_DOWN);ready{f.tag("search-type:ALL").hasFocus()};key(KeyEvent.KEYCODE_DPAD_RIGHT);ready{f.tag("search-type:MOVIES").hasFocus()};key(KeyEvent.KEYCODE_DPAD_DOWN);ready{f.tag(data.movies[0].workId)?.hasFocus()==true};key(KeyEvent.KEYCODE_DPAD_UP);ready{f.tag("search-type:ALL").hasFocus()}}
            check("settings_option_survives_category_roundtrip_without_player") {
                f.main{f.activity.settingsForTest();f.tag("settings-category:audio").requestFocus()}
                ready{f.activity.hasWindowFocus()&&f.tag("settings-category:audio").hasFocus()};key(KeyEvent.KEYCODE_DPAD_RIGHT);repeat(3){key(KeyEvent.KEYCODE_DPAD_DOWN)}
                ready{f.activity.currentFocus?.tag=="setting:text"}
                val target=f.activity.currentFocus?.tag;assertTrue(target.toString().startsWith("setting:"));key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_RIGHT);ready{f.activity.currentFocus?.tag==target}
            }
        }finally{File(f.context.getExternalFilesDir(null),"remote-catalog-audit.json").writeText(results.toString(2));f.close()}
        assertTrue(results.toString(2),(0 until results.length()).all{results.getJSONObject(it).getBoolean("passed")})
    }
}
