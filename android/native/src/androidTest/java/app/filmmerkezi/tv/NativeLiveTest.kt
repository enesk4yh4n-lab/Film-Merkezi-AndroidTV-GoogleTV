package app.filmmerkezi.tv

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** MANUAL ONLY. Site traffic is not part of deterministic Gradle unit tests. */
@RunWith(AndroidJUnit4::class)
class NativeLiveTest {
    @Test fun selectedSourcePlayback()=runBlocking {
        val ins=InstrumentationRegistry.getInstrumentation();val args=InstrumentationRegistry.getArguments()
        Assume.assumeTrue(args.getString("nativeLive")=="true")
        val context=ins.targetContext;val network=ProviderNetwork(context)
        val captureDir=args.getString("nativeCaptureDir")?:"native-live-capture"
        require(captureDir.matches(Regex("[a-zA-Z0-9_-]+")))
        val transport=object:WebTransport {
            override suspend fun request(request:WebRequest):WebResponse {
                val response=network.request(request)
                if(args.getString("captureNativeMetadata")=="true"){
                    val dir=File(context.getExternalFilesDir(null),captureDir);dir.mkdirs()
                    val key=TextKey.hash(request.method+request.url+request.fields.toSortedMap()+request.body)
                    File(dir,key+".json").writeText(JSONObject().put("url",request.url).put("method",request.method)
                        .put("fields",JSONObject(request.fields)).put("requestBody",request.body?:JSONObject.NULL)
                        .put("body",response.body).put("finalUrl",response.url).put("code",response.code).put("at",System.currentTimeMillis()).toString())
                }
                return response
            }
        }
        val resolver=SourceResolver(transport)
        val filter=args.getString("nativeLiveTargets")?.split(',')?.toSet()
        val playMs=(args.getString("livePlayMs")?.toLongOrNull()?:0L).coerceIn(0,20000)
        val targets=JSONArray(ins.context.assets.open("native-live-targets.json").bufferedReader().use{it.readText()})
        val report=JSONObject().put("api",android.os.Build.VERSION.SDK_INT).put("nativeOnly",true).put("fixture",false)
            .put("playTargetMs",playMs).put("targets",JSONArray()).put("at",System.currentTimeMillis())
        val out=File(context.getExternalFilesDir(null),"native-live.json")
        val activity=ins.startActivitySync(Intent(context,NativeMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as NativeMainActivity
        var passed=0;val played=mutableSetOf<String>()
        fun snapshot():JSONObject {var v=JSONObject();ins.runOnMainSync{v=activity.playbackSnapshotForTest()};return v}
        try{
            for(t in targets.values().filterIsInstance<JSONObject>().filter{filter==null||it.string("id") in filter}) {
                val row=JSONObject().put("id",t.string("id")).put("site",t.string("group")).put("url",t.string("url")).put("options",JSONArray())
                report.getJSONArray("targets").put(row)
                try{
                    val site=Sites.get(t.string("group"));val content=ContentRef(site.id,t.string("url"),site.kind(t.string("url")))
                    val response=transport.request(WebRequest(content.pageUrl,site.base+"/"))
                    val options=SourceMenus.parse(site,response.body,content)
                    row.put("optionCount",options.size)
                    if(options.isEmpty())row.put("error","Desteklenen seçenek yok")
                    for(option in options) {
                        val result=JSONObject().put("label",option.label).put("optionUrl",option.url);row.getJSONArray("options").put(result)
                        try{
                            val start=SystemClock.elapsedRealtime();val source=resolver.resolve(option)
                            result.put("resolveMs",SystemClock.elapsedRealtime()-start).put("source",Contracts.source(source))
                            if(playMs>0) {
                                // Query parameters can identify the movie (for example OK's
                                // id=), and identical URLs can require different source headers.
                                val unique=Contracts.source(source.copy(id="",label="")).toString()
                                if(unique in played)result.put("status","DUPLICATE_ALREADY_PLAYED")
                                else{
                                    ins.runOnMainSync{activity.startSourcesForTest(listOf(Contracts.source(source)),http=network.client)}
                                    val deadline=SystemClock.elapsedRealtime()+45000
                                    var state=JSONObject()
                                    do {delay(250);state=snapshot()
                                        if(!state.isNull("error") || state.optLong("positionMs")>=playMs)break
                                    }while(SystemClock.elapsedRealtime()<deadline)
                                    result.put("player",state)
                                    if(state.optBoolean("firstFrame")&&state.optLong("positionMs")>=playMs&&state.isNull("error")){
                                        result.put("status","PLAY_PASS");played.add(unique);passed++
                                    }else result.put("status","PLAY_FAIL")
                                    ins.runOnMainSync{activity.stopSourcesForTest()}
                                }
                            }else {result.put("status","RESOLVED_ONLY");passed++}
                        }catch(e:CancellationException){throw e}
                        catch(e:Exception){result.put("status","RESOLVE_FAIL").put("error",e.message)}
                        out.writeText(report.toString(2))
                        ins.sendStatus(0,android.os.Bundle().apply{putString("stream","NATIVE "+t.string("id")+" / "+option.label+": "+result.string("status")+"\n")})
                    }
                }catch(e:CancellationException){throw e}
                catch(e:Exception){row.put("error",e.message)}
                out.writeText(report.toString(2))
            }
            assertTrue("No native source passed; see native-live.json",passed>0)
        }finally{out.writeText(report.toString(2));ins.runOnMainSync{activity.finish()}}
        Unit
    }
}
