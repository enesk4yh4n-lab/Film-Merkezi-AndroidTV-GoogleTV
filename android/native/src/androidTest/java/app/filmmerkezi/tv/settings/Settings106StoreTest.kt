package app.filmmerkezi.tv.settings

import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Settings106StoreTest {
    @Test fun semanticOverridesSurviveReloadAndRedirectResetWithoutChangingDefaults()=runBlocking {
        val c=InstrumentationRegistry.getInstrumentation().targetContext;val name="settings106-overrides.db";c.deleteDatabase(name)
        try{
            PlaybackOverridesStore(c,name).use{s->
                s.update("work:old",setOf("work:old")){it.copy(audio="en",audioRole=0,height=1080,text="tr",subtitles=SubtitlePolicy.ON)}
                val base=PlaybackSettings(autoMaxHeight=720,autoMaxFps=30)
                val v=s.get(setOf("work:new","work:old")).apply(base)
                assertEquals(1080,v.preferredHeight);assertEquals(720,v.autoMaxHeight);assertEquals("en",v.audio)
                assertEquals("tr",base.audio);assertEquals(0,base.preferredHeight)
                assertEquals(PlaybackOverrides(),s.get(setOf("work:unrelated")))
                s.update("work:new",setOf("work:old","work:new")){PlaybackOverrides()}
            }
            PlaybackOverridesStore(c,name).use{s->assertEquals(PlaybackOverrides(),s.get(setOf("work:old","work:new")))}
        }finally{c.deleteDatabase(name)}
    }
}
