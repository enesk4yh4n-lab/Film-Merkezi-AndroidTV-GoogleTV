package app.filmmerkezi.tv.settings

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.*
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Settings105StorageTest {
    private val base=InstrumentationRegistry.getInstrumentation().targetContext
    private fun isolated(prefix:String)=object:ContextWrapper(base){override fun getApplicationContext():Context=this
        override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("$prefix-$name",mode)}
    @Test fun migrationIsDurableAndDoesNotTouchLibrary()=runBlocking {
        val c=isolated("settings105-migration")
        for(n in listOf("film-settings","player-preferences","catalog-preferences"))c.getSharedPreferences(n,0).edit().clear().commit()
        c.getSharedPreferences("player-preferences",0).edit().putString("audio","de").putInt("height",1080).putBoolean("automaticSources",false).putBoolean("textEnabled",false).commit()
        c.getSharedPreferences("catalog-preferences",0).edit().putString("siteId","dizibox").commit()
        val db="settings105-library.db";base.deleteDatabase(db)
        try{LibraryStore(base,db).use{library->val card=Card(ContentRef("filmcibaba","https://filmizle.ink/settings-fixture/"),"Fixture")
            library.update(card,true,42000,100000)
            val repo=SettingsRepository(c);repo.migrate()
            val reloaded=SettingsRepository(c).value
            assertEquals(1080,reloaded.player.preferredHeight);assertEquals(0,reloaded.player.autoMaxHeight);assertEquals(SubtitlePolicy.OFF,reloaded.player.subtitles)
            assertEquals("de",reloaded.player.audio);assertFalse(reloaded.automaticSources);assertEquals("dizibox",reloaded.selectedSite)
            repo.update{AppSettings()};assertTrue(library.records(true).single().listed);assertEquals(42000L,library.records(true).single().positionMs)
        }}finally{base.deleteDatabase(db)}
    }
    @Test fun failedWriteDoesNotPublishSuccessOrMigrationMarker()=runBlocking {
        val c=isolated("settings105-failure");c.getSharedPreferences("film-settings",0).edit().clear().putString("largeText","bad-type").commit()
        val repo=SettingsRepository(c,persist={false});val before=repo.value
        try{repo.update{it.copy(merge=false)};fail("Write failure ignored")}catch(e:DataFailure){assertEquals(FailureKind.STORAGE,e.kind)}
        assertEquals(before,repo.value);assertFalse(c.getSharedPreferences("film-settings",0).contains("schema"))
        assertEquals("bad-type",c.getSharedPreferences("film-settings",0).all["largeText"])
    }
}
