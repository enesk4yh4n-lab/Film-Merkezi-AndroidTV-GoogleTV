package app.filmmerkezi.tv

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlaybackVariantsTest {
    private fun source():JSONObject {
        val separate=JSONObject().put("id","720").put("url","https://cdn.example.test/v720").put("height",720)
            .put("audioStreams",JSONArray().put(JSONObject().put("url","https://cdn.example.test/audio")))
        val combined=JSONObject().put("id","360").put("url","https://cdn.example.test/v360").put("height",360).put("audioStreams",JSONArray())
        return JSONObject().put("id","ddizi|yt").put("label","DDizi · YouTube").put("referer","https://www.youtube.com/")
            .put("variants",JSONArray().put(separate).put(combined))
    }
    @Test fun qualityPreferenceAndManualVariantAreHonoured(){
        assertEquals("720",PlaybackVariants.prepare(source(),0).getString("variantId"))
        assertEquals("360",PlaybackVariants.prepare(source(),480).getString("variantId"))
        assertEquals("360",PlaybackVariants.prepare(source().put("variantId","360"),720).getString("variantId"))
    }
    @Test fun fallbackKeepsSourceIdentityHeadersAndStopsWhenExhausted(){
        val s=PlaybackVariants.prepare(source(),0);val fallback=PlaybackVariants.fallback(s,setOf("720"))!!
        assertEquals("360",fallback.getString("variantId"));assertEquals("ddizi|yt",fallback.getString("id"))
        assertEquals("https://www.youtube.com/",fallback.getString("referer"));assertEquals(0,fallback.getJSONArray("audioStreams").length())
        assertNull(PlaybackVariants.fallback(fallback,setOf("720","360")))
        assertEquals("720",s.getString("variantId"))
    }
    @Test fun ordinarySourcesAreUnchanged(){val source=JSONObject().put("url","https://cdn.example.test/master.m3u8");assertSame(source,PlaybackVariants.prepare(source,720));assertNull(PlaybackVariants.fallback(source,emptySet()))}
    @Test fun temporaryNetworkFailureOnlyUsesAnEligibleLowerBitrate(){
        val base=source()
        val variants=base.getJSONArray("variants")
        variants.getJSONObject(0).put("bitrate",1100000)
        variants.getJSONObject(1).put("bitrate",350000)
        variants.put(JSONObject().put("id","720-low").put("url","https://cdn.example.test/v720-low")
            .put("height",720).put("bitrate",600000))
        val active=PlaybackVariants.prepare(base,0)
        assertEquals("720-low",PlaybackVariants.lowerBitrate(active,emptySet(),700000){true}?.getString("variantId"))
        assertEquals("360",PlaybackVariants.lowerBitrate(active,emptySet(),400000){true}?.getString("variantId"))
        assertNull(PlaybackVariants.lowerBitrate(active,setOf("360"),700000){it.optInt("height")<720})
    }
    @Test fun equalHeightPrefersCombinedWhileExplicitSelectionRemainsStable(){
        val s=source();val a=s.getJSONArray("variants");val combined=a.getJSONObject(1)
        val separate=JSONObject(combined.toString()).put("id","360-separate").put("audioStreams",JSONArray().put(JSONObject().put("url","https://cdn.example.test/a")))
        s.put("variants",JSONArray().put(a.getJSONObject(0)).put(separate).put(combined))
        assertEquals("360",PlaybackVariants.prepare(s,360).getString("variantId"))
        assertEquals("360-separate",PlaybackVariants.prepare(s.put("variantId","360-separate"),360).getString("variantId"))
    }
    @Test fun highResolutionCandidatesSurviveAndOnlyDevicePredicateFiltersSelection(){
        val s=source();val values=s.getJSONArray("variants")
        for(h in listOf(1080,1440,2160,4320))values.put(JSONObject().put("id",h.toString()).put("height",h).put("fps",60)
            .put("codec","vp09.00.51.08").put("mimeType","video/webm").put("url","https://cdn.example.test/$h").put("audioStreams",JSONArray()))
        assertEquals(6,PlaybackVariants.all(s).size)
        assertEquals("4320",PlaybackVariants.prepare(s,0).getString("variantId"))
        val supported=PlaybackVariants.prepare(s,0){it.optInt("height")<=2160}
        assertEquals("2160",supported.getString("variantId"));assertEquals("webm",supported.getString("type"))
        assertEquals("2160",PlaybackVariants.prepare(s,2160).getString("variantId"))
    }
}
