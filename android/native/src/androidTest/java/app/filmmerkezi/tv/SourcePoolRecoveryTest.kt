package app.filmmerkezi.tv

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class SourcePoolRecoveryTest {
    @Test fun pendingDiscoveryDoesNotEndPlaybackBeforeLateSourceArrives()=check(true)
    @Test fun disabledCrossSiteRecoveryNeverLoadsAnotherSite()=check(false)
    private fun check(cross:Boolean)=runBlocking{
        val ins=InstrumentationRegistry.getInstrumentation();val activity=ins.startActivitySync(Intent(ins.targetContext,NativeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as NativeMainActivity
        fun main(action:()->Unit)=ins.runOnMainSync(action)
        fun snap():JSONObject{var s=JSONObject();main{s=activity.playbackSnapshotForTest()};return s}
        suspend fun await(test:(JSONObject)->Boolean):JSONObject{val end=SystemClock.elapsedRealtime()+30000;while(SystemClock.elapsedRealtime()<end){val s=snap();if(test(s))return s;delay(100)};error(snap().toString())}
        val token=SystemClock.elapsedRealtime()
        fun source(id:String,path:String)=JSONObject().put("id",id).put("label",id).put("type","hls").put("url","http://10.0.2.2:8768/case/policy-$token/$path?mode=healthy")
        try{
            main{activity.startSourcesForTest(listOf(source("filmcibaba|bad","missing.m3u8")));val session=activity.playbackSessionForTest();session.automaticSources=true;session.crossSiteSources=cross;session.appendCandidates(if(cross)emptyList()else listOf(source("sinemacc|good","master.m3u8")),cross)}
            if(cross){
                val waiting=await{!it.isNull("lastFailure")&&it.isNull("state")};assertTrue(waiting.isNull("error"))
                main{activity.playbackSessionForTest().appendCandidates(listOf(source("sinemacc|good","master.m3u8")),false)}
                val played=await{it.optBoolean("firstFrame")&&it.optLong("positionMs")>=1500};assertEquals("sinemacc|good",played.optString("sourceId"));assertEquals(1,played.optInt("sourceSwitches"))
            }else{val failed=await{!it.isNull("error")};assertEquals("filmcibaba|bad",failed.optString("sourceId"));assertFalse(failed.optBoolean("firstFrame"))}
        }finally{main{activity.stopSourcesForTest();activity.finish()}}
    }
}
