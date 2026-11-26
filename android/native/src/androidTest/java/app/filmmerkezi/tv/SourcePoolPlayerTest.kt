package app.filmmerkezi.tv

import android.content.Intent
import android.os.Debug
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class SourcePoolPlayerTest {
    @Test fun dynamicSourcesManualFailureAndLongPlayback()=runBlocking{
        val ins=InstrumentationRegistry.getInstrumentation();val args=InstrumentationRegistry.getArguments()
        val activity=ins.startActivitySync(Intent(ins.targetContext,NativeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as NativeMainActivity
        val checks=JSONArray();val file=File(ins.targetContext.getExternalFilesDir(null),"source-pool-player.json")
        val prefix="pool-"+SystemClock.elapsedRealtime()
        fun main(block:()->Unit)=ins.runOnMainSync(block)
        fun snap():JSONObject{var s=JSONObject();main{s=activity.playbackSnapshotForTest()};return s}
        suspend fun await(test:(JSONObject)->Boolean):JSONObject{val end=SystemClock.elapsedRealtime()+35000;while(SystemClock.elapsedRealtime()<end){val s=snap();if(test(s))return s;delay(100)};error(snap().toString())}
        fun source(id:String,path:String="master.m3u8")=JSONObject().put("id",id).put("label",id).put("type","hls").put("url","http://10.0.2.2:8768/case/$prefix-${id.replace('|','-')}/$path?mode=healthy").put("referer","https://fixture.example.test/").put("userAgent","FM-Pool-Test")
        fun save(){file.writeText(JSONObject().put("fixture",true).put("checks",checks).toString(2))}
        try{
            main{activity.startSourcesForTest(listOf(source("filmcibaba|first")))}
            await{it.optBoolean("firstFrame")&&it.optLong("positionMs")>=1500}
            main{activity.playbackSessionForTest().appendCandidates((1..23).map{source("sinemacc|$it")},true)}
            main{assertEquals(24,activity.playbackSessionForTest().sourceEntries().size)}
            val before=await{it.optLong("positionMs")>=5000};assertEquals("filmcibaba|first",before.optString("sourceId"));assertEquals(1,before.optInt("enginesCreated"))
            main{assertTrue(activity.playbackSessionForTest().chooseSource("sinemacc|23"))}
            val changed=await{it.optString("sourceId")=="sinemacc|23"&&it.optBoolean("firstFrame")&&it.optLong("positionMs")>=15000}
            checks.put(JSONObject().put("name","24 sources, append does not restart, stable ID switch preserves position, 15 seconds").put("state",changed).put("passed",true));save()
            main{activity.startSourcesForTest(listOf(source("filmcibaba|broken","missing.m3u8")));activity.playbackSessionForTest().automaticSources=false;activity.playbackSessionForTest().appendCandidates(listOf(source("sinemacc|good")),false)}
            val failed=await{!it.isNull("error")};assertEquals(0,failed.optInt("sourceIndex"))
            main{activity.playbackSessionForTest().chooseSource("sinemacc|good")}
            await{it.optBoolean("firstFrame")&&it.optLong("positionMs")>=2000}
            checks.put(JSONObject().put("name","manual failure waits for selection").put("passed",true));save()
            if(args.getString("longPool")=="true"){
                main{activity.startSourcesForTest(listOf(source("filmcibaba|long","long.m3u8")));activity.playbackSessionForTest().automaticSources=true}
                val memory=JSONArray();val start=SystemClock.elapsedRealtime();var count=0
                while(SystemClock.elapsedRealtime()-start<930000){
                    delay(1000);val state=snap()
                    if(count<14){main{activity.playbackSessionForTest().appendCandidates(listOf(source("site$count|alternative")),count<13)};count++}
                    if((SystemClock.elapsedRealtime()-start)%30000<1100){val m=Debug.MemoryInfo();Debug.getMemoryInfo(m);memory.put(JSONObject().put("elapsedMs",SystemClock.elapsedRealtime()-start).put("pssKiB",m.totalPss).put("positionMs",state.optLong("positionMs")));save()
                        ins.sendStatus(0,android.os.Bundle().apply{putString("stream","LONG pool: position=${state.optLong("positionMs")/1000}s, PSS=${m.totalPss} KiB, rebuffer=${state.optInt("rebufferCount")}\n")})}
                    if(state.optLong("positionMs")>=900000&&state.optBoolean("isPlaying"))break
                    assertTrue("unexpected playback error",state.isNull("error"))
                }
                val state=snap();assertTrue(state.optLong("positionMs")>=900000);assertEquals(0,state.optInt("rebufferCount"));checks.put(JSONObject().put("name","15 minute playback with arriving source metadata").put("passed",true).put("state",state).put("memory",memory));save()
            }
        }finally{save();main{activity.stopSourcesForTest();activity.finish()}}
    }
}
