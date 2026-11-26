package app.filmmerkezi.tv.tv

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.SystemClock
import app.filmmerkezi.tv.BufferProfile
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Run explicitly on the owned 1024 MiB API24 emulator, never in the short test group. */
class Settings110BufferTest {
    @Test fun eachBufferProfilePlaysLocal720pForTwoMinutesWithinTheProcessBudget()=runBlocking {
        val f=Settings110Fixture();val report=JSONObject().put("fixture",true).put("profiles",JSONArray())
        val manager=f.context.getSystemService(Context.ACTIVITY_SERVICE)as ActivityManager
        val mem=ActivityManager.MemoryInfo().also{manager.getMemoryInfo(it)}
        report.put("totalMemoryKiB",mem.totalMem/1024).put("api",android.os.Build.VERSION.SDK_INT)
        assertTrue("Explicit 1 GiB device required",mem.totalMem in 900L*1024*1024..1100L*1024*1024)
        try{
            f.start()
            for(profile in BufferProfile.entries){
                f.repo.update{it.copy(player=it.player.copy(buffer=profile))};f.play()
                val start=f.snap();val engine=start.getInt("engineIdentity");assertEquals(profile.name,start.getString("bufferProfile"));assertEquals(720,start.getInt("height"))
                val row=JSONObject().put("profile",profile.name).put("start",start);report.getJSONArray("profiles").put(row)
                val samples=JSONArray();var peak=0;val until=SystemClock.elapsedRealtime()+120000;var latchChecked=false
                while(SystemClock.elapsedRealtime()<until){
                    val info=Debug.MemoryInfo();Debug.getMemoryInfo(info);peak=maxOf(peak,info.totalPss)
                    val state=f.snap();samples.put(JSONObject().put("at",SystemClock.elapsedRealtime()).put("pssKiB",info.totalPss).put("positionMs",state.optLong("positionMs")))
                    assertTrue("Player failed: $state",state.isNull("error"));assertEquals(engine,state.getInt("engineIdentity"))
                    if(!latchChecked&&samples.length()>=5){
                        val changed=if(profile==BufferProfile.LOW)BufferProfile.LONG else BufferProfile.LOW
                        f.repo.update{it.copy(player=it.player.copy(buffer=changed))};delay(300)
                        assertEquals(profile.name,f.snap().getString("bufferProfile"));assertEquals(engine,f.snap().getInt("engineIdentity"))
                        f.repo.update{it.copy(player=it.player.copy(buffer=profile))};latchChecked=true
                    }
                    delay(2000)
                }
                val end=f.snap();row.put("end",end).put("samples",samples).put("peakPssKiB",peak)
                assertTrue("220 MiB process budget exceeded: $peak KiB",peak<=220*1024)
                assertTrue("No two-minute progress: $end",end.getLong("positionMs")-start.getLong("positionMs")>=115000)
                f.stop()
                val cycles=JSONArray()
                repeat(3){f.play();delay(700);cycles.put(f.snap());f.stop()}
                row.put("shortOpenCloseCycles",cycles).put("status","PASS")
                File(f.context.getExternalFilesDir(null),"settings110-buffers.json").writeText(report.toString(2))
            }
            report.put("status","PASS")
        }catch(e:Throwable){report.put("status","FAIL").put("error",e.toString());throw e}
        finally{File(f.context.getExternalFilesDir(null),"settings110-buffers.json").writeText(report.toString(2));f.close()}
        Unit
    }
}
