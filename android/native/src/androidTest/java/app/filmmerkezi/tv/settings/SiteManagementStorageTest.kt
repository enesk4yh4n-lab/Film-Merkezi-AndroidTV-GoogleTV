package app.filmmerkezi.tv.settings

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SiteManagementStorageTest {
    private val base=InstrumentationRegistry.getInstrumentation().targetContext
    private fun isolated(prefix:String)=object:ContextWrapper(base){override fun getApplicationContext():Context=this
        override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("$prefix-$name",mode)}
    @Test fun schemaTwoMigrationAndSavedProgressSurviveMoveAndReturn()=runBlocking {
        val original=SettingsRepository.get(base).value;val c=isolated("sites113-storage");val db="sites113-library.db"
        c.getSharedPreferences("film-settings",0).edit().clear().putInt("schema",2).putBoolean("merge",false).putString("selectedSite","ddizi").commit()
        base.deleteDatabase(db)
        try{LibraryStore(base,db).use{library->
            val initial=Sites.default("diziboxum")
            val card=Card(ContentRef(initial.id,initial.base+"/test-1-sezon-2-bolum/",ContentKind.EPISODE),"Test",workId="work:site-management",seriesRef=ContentRef(initial.id,initial.base+"/dizi/test/",ContentKind.SERIES),episodeNumber=EpisodeNumber(1,2))
            library.update(card,true,42000,100000)
            val repo=SettingsRepository(c);repo.migrate();assertEquals(3,c.getSharedPreferences("film-settings",0).getInt("schema",0))
            repo.update{it.copy(siteAddresses=SiteAddresses.change(it.siteAddresses,initial.id,"series-updated.example","Deneme adı"))}
            val moved=SettingsRepository(c);assertEquals("https://series-updated.example",Sites.get(initial.id).base)
            assertFalse(moved.value.merge);assertEquals("ddizi",moved.value.selectedSite)
            val read=library.records(true).single();assertEquals(card.workId,read.card.workId);assertEquals(42000L,read.positionMs);assertTrue(read.card.ref.pageUrl.startsWith("https://series-updated.example/"))
            assertEquals(card.episodeNumber,read.card.episodeNumber);assertTrue(read.listed)
            library.update(read.card,positionMs=45000,durationMs=100000)
            moved.update{it.copy(siteAddresses=SiteAddresses.reset(it.siteAddresses,initial.id))}
            SettingsRepository(c);val restored=library.records(true).single();assertEquals(45000L,restored.positionMs);assertEquals(initial.base+"/test-1-sezon-2-bolum/",restored.card.ref.pageUrl)
            assertEquals(card.ref.id,restored.card.ref.id);assertTrue(restored.listed)
        }}finally{Sites.configure(original.siteAddresses);base.deleteDatabase(db)}
    }
    @Test fun aFailedCommitDoesNotChangeActiveAddressesOrSavedPreferences()=runBlocking {
        val original=SettingsRepository.get(base).value;val c=isolated("sites113-failure")
        c.getSharedPreferences("film-settings",0).edit().clear().commit()
        try{val repo=SettingsRepository(c,persist={false});val current=Sites.get("diziboxum");val before=repo.value
            try{repo.update{it.copy(siteAddresses=SiteAddresses.change(it.siteAddresses,current.id,"failed.example","Failed"))};fail()}catch(e:DataFailure){assertEquals(FailureKind.STORAGE,e.kind)}
            assertEquals(current,Sites.get(current.id));assertEquals(before,repo.value);assertFalse(c.getSharedPreferences("film-settings",0).contains("siteAddresses"))
        }finally{Sites.configure(original.siteAddresses)}
    }
}
