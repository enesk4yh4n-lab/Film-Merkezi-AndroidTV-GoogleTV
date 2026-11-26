package app.filmmerkezi.tv.tv

import android.view.*
import android.graphics.Bitmap
import androidx.media3.common.text.Cue
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import app.filmmerkezi.tv.*
import app.filmmerkezi.tv.settings.AppSettings
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class Settings110UiTest {
    @Test fun allEightCategoriesDpadLargeFontAndPreviewStayInClassic()=runBlocking {
        val f=Settings110Fixture()
        try{
            f.start();f.main{f.activity.settingsForTest()};f.waitFor{f.tag("settings-category:general")?.hasFocus()==true}
            for(category in listOf("general","catalog","playback","video","audio","remote","storage","about")){
                f.waitFor{f.tag("settings-category:$category")?.hasFocus()==true}
                f.ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
                f.waitFor{f.activity.currentFocus?.tag?.toString()?.startsWith("setting:")==true}
                f.ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);f.waitFor{f.tag("settings-category:$category")?.hasFocus()==true}
                if(category!="about")f.ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            }
            f.repo.update{it.copy(largeText=true,player=it.player.copy(customSubtitles=true,subtitleYellow=true,subtitleSize=2,subtitleBottom=12))}
            f.main{f.tag("settings-category:audio").requestFocus()};f.waitFor{f.tag("setting:caption-style")!=null}
            f.screenshot("settings110-audio-${f.context.resources.displayMetrics.widthPixels}.png")
            f.ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);f.waitFor{f.tag("setting:source-audio")?.hasFocus()==true}
            for(key in listOf("audio","second-audio","text","subtitles","caption-style","caption-size","caption-color","caption-outline","caption-background","caption-bottom")){
                f.ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);f.waitFor{f.tag("setting:$key")?.hasFocus()==true}
            }
            f.screenshot("settings110-caption-options-${f.context.resources.displayMetrics.widthPixels}.png")
            f.ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);f.waitFor{f.tag("settings-category:audio")?.hasFocus()==true}
            f.main{f.tag("settings-category:storage").requestFocus()};f.waitFor{f.tag("setting:buffer")!=null}
            f.screenshot("settings110-storage-${f.context.resources.displayMetrics.widthPixels}.png")
            // Scoped history clear is our own window, cancellation returns to the invoking row.
            f.main{f.tag("setting:history").requestFocus();f.tag("setting:history").performClick()}
            f.ins.waitForIdleSync();delay(200)
            f.ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);f.waitFor{f.tag("setting:history")?.hasFocus()==true}
            f.main{f.activity.onBackPressed()};f.waitFor{f.tag("settings-category:general")==null}
        }finally{f.close()}
    }
    @Test fun captionsSeekOverlayAndFullSettingsPreserveTheEngineAndPosition()=runBlocking {
        val f=Settings110Fixture(continuous=true);val report=JSONObject().put("fixture",true).put("continuousMedia",true)
        fun descendants(v:View):Sequence<View> = sequence{yield(v);if(v is ViewGroup)for(i in 0 until v.childCount)yieldAll(descendants(v.getChildAt(i)))}
        fun caption(view:SubtitleView):List<Cue>{val field=SubtitleView::class.java.getDeclaredField("cues");field.isAccessible=true;@Suppress("UNCHECKED_CAST") return field.get(view)as List<Cue>}
        try{
            f.start(AppSettings(entry="RESTART",player=PlaybackSettings(subtitles=SubtitlePolicy.ON,customSubtitles=true,subtitleYellow=true,subtitleBottom=12,seekSeconds=5,controlsSeconds=0)))
            f.play();f.waitFor{f.activity.snapshotForUiTest().optString("subtitleText").isNotBlank()}
            val start=f.snap();val engine=start.getInt("engineIdentity");var playerView:PlayerView?=null
            f.main{playerView=descendants(f.activity.window.decorView).filterIsInstance<PlayerView>().first();playerView!!.hideController()}
            f.ins.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            f.waitFor{f.activity.findViewById<View>(R.id.fm_seek_feedback)?.isShown==true}
            f.waitFor{caption(playerView!!.subtitleView!!).firstOrNull()?.line?.let{kotlin.math.abs(it-0.72f)<0.01f}==true}
            f.main{assertFalse(playerView!!.isControllerFullyVisible)}
            f.waitFor{f.activity.findViewById<View>(R.id.fm_seek_feedback)?.isShown==false}
            f.waitFor{caption(playerView!!.subtitleView!!).firstOrNull()?.line?.let{kotlin.math.abs(it-0.88f)<0.01f}==true}
            assertTrue(f.snap().getLong("positionMs")>=start.getLong("positionMs")+5000)
            f.main{f.activity.settingsForTest()};f.waitFor{f.tag("settings-category:general")!=null}
            f.repo.update{it.copy(player=it.player.copy(subtitleSize=3,subtitleBottom=16))}
            f.main{f.activity.onBackPressed()};f.waitFor{f.tag("settings-category:general")==null}
            f.main{playerView!!.hideController()};f.waitFor{caption(playerView!!.subtitleView!!).firstOrNull()?.line?.let{kotlin.math.abs(it-0.84f)<0.01f}==true}
            val after=f.snap();assertEquals(engine,after.getInt("engineIdentity"));val target=after.getLong("positionMs")+15000
            f.waitFor{f.activity.snapshotForUiTest().optLong("positionMs")>=target&&f.activity.snapshotForUiTest().optBoolean("isPlaying")}
            f.main{playerView!!.hideController()};f.screenshot("settings110-caption-playback.png")
            // Image captions retain their bitmap/position; top-positioned source captions stay at top.
            val bitmap=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);val image=Cue.Builder().setBitmap(bitmap).setPosition(0.5f).setLine(0.9f,Cue.LINE_TYPE_FRACTION).build()
            assertSame(image,SubtitleAppearance.adjust(image,0.3f,true,true));bitmap.recycle()
            val top=Cue.Builder().setText("Top").setLine(0.1f,Cue.LINE_TYPE_FRACTION).build();assertEquals(0.1f,SubtitleAppearance.adjust(top,0.3f,false,true).line,0.001f)
            report.put("start",start).put("end",f.snap()).put("status","PASS");f.stop()
        }catch(e:Throwable){report.put("status","FAIL").put("error",e.toString()).put("state",f.snap());throw e}
        finally{File(f.context.getExternalFilesDir(null),"settings110-player.json").writeText(report.toString(2));f.close()}
        Unit
    }
}
