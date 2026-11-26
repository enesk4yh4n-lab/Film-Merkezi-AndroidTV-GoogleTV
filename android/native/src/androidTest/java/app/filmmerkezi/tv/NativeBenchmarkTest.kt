package app.filmmerkezi.tv

import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Local captured parsing cost only; no network and no claim about ARM TV speed. */
@RunWith(AndroidJUnit4::class)
class NativeBenchmarkTest {
    @Test fun repeatedCapturedSearchAndDetail() {
        val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext
        val cases=JSONArray(ins.context.assets.open("native-benchmark.json").bufferedReader().use{it.readText()})
        var peakPss=0
        val report=JSONObject().put("scope","debug captured parsing; not live latency").put("abi",android.os.Build.SUPPORTED_ABIS.first())
            .put("api",android.os.Build.VERSION.SDK_INT).put("iterations",50).put("cases",JSONArray())
        for(c in cases.values().filterIsInstance<JSONObject>()){
            val parser=CatalogParser(Sites.get(c.string("site")));val times=mutableListOf<Double>()
            val original=c.optJSONObject("original")
            val card=Card(ContentRef(c.string("site"),c.string("page")),original?.string("title")?:"Fixture")
            repeat(5){if(c.string("method")=="detail")parser.detail(c.string("body"),card)else parser.list(c.string("body"),c.string("page"))}
            repeat(50){
                val start=SystemClock.elapsedRealtimeNanos()
                if(c.string("method")=="detail")parser.detail(c.string("body"),card)else assertTrue(parser.list(c.string("body"),c.string("page")).cards.isNotEmpty())
                times.add((SystemClock.elapsedRealtimeNanos()-start)/1e6)
                val memory=Debug.MemoryInfo();Debug.getMemoryInfo(memory);peakPss=maxOf(peakPss,memory.totalPss)
            }
            val sorted=times.sorted()
            report.getJSONArray("cases").put(JSONObject().put("site",c.string("site")).put("method",c.string("method")).put("p50Ms",sorted[24]).put("p95Ms",sorted[47]).put("samplesMs",JSONArray(times)))
        }
        report.put("sampledPeakPssKiB",peakPss)
        File(context.getExternalFilesDir(null),"native-benchmark.json").writeText(report.toString(2))
    }
}
