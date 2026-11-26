package app.filmmerkezi.tv

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Cookie
import org.json.*
import org.junit.Assert.*
import org.junit.Test

class SessionCookiesTest {
    @Test fun transferredCookiesKeepHostPathSecureAndExpiry(){
        val source=JSONObject().put("sessionCookies",JSONArray().put(JSONObject().put("name","token").put("value","secret").put("domain","media.example.com").put("path","/video/").put("expiresAt",System.currentTimeMillis()+60000).put("secure",true).put("httpOnly",true).put("hostOnly",true)))
        val jar=SessionCookies(source)
        assertEquals("token",jar.loadForRequest("https://media.example.com/video/master.m3u8".toHttpUrl()).single().name)
        for(url in listOf("https://other.example.com/video/a.ts","https://media.example.com/subtitles/a.vtt","http://media.example.com/video/a.ts","https://sub.media.example.com/video/a.ts"))assertTrue(jar.loadForRequest(url.toHttpUrl()).isEmpty())
        jar.saveFromResponse("https://media.example.com/video/".toHttpUrl(),listOf(Cookie.Builder().name("token").value("removed").hostOnlyDomain("media.example.com").path("/video/").expiresAt(0).build()))
        assertTrue(jar.loadForRequest("https://media.example.com/video/a.ts".toHttpUrl()).isEmpty())
    }
}
