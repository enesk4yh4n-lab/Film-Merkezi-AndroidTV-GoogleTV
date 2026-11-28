package app.filmmerkezi.tv.core

import app.filmmerkezi.tv.PlaybackNavigation
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlaybackNavigationTest {
    @Test fun navigationOnlyAcceptsTimedEntriesAndSameOriginImages(){
        fun row(uri:String)=JSONObject().put("url",uri).put("startMs",0).put("endMs",2000)
        val source=JSONObject().put("url","https://media.example/video.m3u8").put("navigation",JSONObject()
            .put("chapters",JSONArray(listOf(
                JSONObject().put("startMs",0).put("endMs",5000).put("kind","intro"),
                JSONObject().put("startMs",-2).put("title","Invalid"),
                JSONObject().put("startMs",7000).put("title","Scene"),
                JSONObject().put("startMs",8000).put("endMs",7000).put("kind","intro")
            )))
            .put("previews",JSONArray(listOf(
                row("http://media.example/preview.jpg"),row("https://other.example/preview.jpg"),
                row("https://media.example/preview.jpg"),row("https://media.example:444/preview.jpg")
            ))))
        val nav=PlaybackNavigation.read(source)
        assertEquals(listOf(0L,7000L),nav.chapters.map{it.time})
        assertEquals(5000L,nav.chapters.single{it.intro}.end)
        assertEquals("https://media.example/preview.jpg",nav.previews.single().url)
    }
}
