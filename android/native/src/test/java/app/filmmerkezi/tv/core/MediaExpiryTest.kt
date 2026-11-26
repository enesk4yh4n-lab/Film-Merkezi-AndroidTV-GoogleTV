package app.filmmerkezi.tv.core

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class MediaExpiryTest {
    @Test fun millisecondsAreNotMultipliedAgain(){assertEquals(1793776564836L,MediaExpiry.fromUrl("https://cdn.example/video.m3u8?expires=1793776564836".toHttpUrl()))}
    @Test fun secondBasedFieldsShareTheSameMillisecondsContract(){
        for(field in listOf("expires","expire","expire_at"))assertEquals(1793703600000L,MediaExpiry.fromUrl("https://cdn.example/video.mp4?$field=1793703600".toHttpUrl()))
    }
    @Test fun invalidAndRelativeExpiryAreNotInvented(){
        for(value in listOf(null,"","-1","0","bad","9999999999999999999999"))assertNull(MediaExpiry.fromValue(value))
        assertNull(MediaExpiry.fromUrl("https://cdn.example/video.mp4?expires_in=3600".toHttpUrl()))
    }
}
