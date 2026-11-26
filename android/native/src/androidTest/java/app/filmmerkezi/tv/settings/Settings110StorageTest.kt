package app.filmmerkezi.tv.settings

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class Settings110StorageTest {
    @Test fun failedCommitCannotLeakARejectedValueIntoANewRepository()=runBlocking{
        val c=InstrumentationRegistry.getInstrumentation().targetContext;val name="settings110-rejected"
        val prefs=c.getSharedPreferences(name,0);prefs.edit().clear().commit()
        try{
            val repo=SettingsRepository(c,name){editor->editor.commit();false};val old=repo.value
            try{repo.update{it.copy(largeText=!it.largeText,merge=!it.merge)};fail("Commit failure must surface")}catch(_:DataFailure){}
            assertEquals(old,repo.value);assertEquals(old,SettingsRepository(c,name).value)
        }finally{prefs.edit().clear().commit()}
        Unit
    }
    @Test fun scopedClearsPreserveFavouritesManualMarksUnreadableRowsAndActiveCheckpoints()=runBlocking{
        val c=InstrumentationRegistry.getInstrumentation().targetContext;val a="settings110-library.db";val b="settings110-series.db";val d="settings110-overrides.db"
        listOf(a,b,d).forEach{c.deleteDatabase(it)}
        val card=Card(ContentRef("filmcibaba","https://filmizle.ink/settings110-store/"),"Settings store",workId="work:settings110-store")
        try{LibraryStore(c,a).use{lib->SeriesTrackingStore(c,b).use{series->PlaybackOverridesStore(c,d).use{overrides->
            lib.update(card,listed=true,positionMs=30000,durationMs=100000);lib.rememberSearch("yeraltı")
            lib.update(card.copy(workId="work:other",ref=card.ref.copy(pageUrl="https://filmizle.ink/other/"),title="Other"),positionMs=50000,durationMs=100000)
            series.mark(card,listOf(EpisodeNumber(1,1)),true,true)
            overrides.update(card.workId,setOf(card.workId)){it.copy(audio="en",height=1080)}
            overrides.update(card.workId,setOf(card.workId)){it.copy(height=540)}
            assertEquals(540,overrides.get(setOf(card.workId)).height)
            val cp=lib.beginPlayback(card,JSONObject());try{lib.clearPlaybackHistory();fail("Active checkpoint must block history clearing")}catch(_:DataFailure){}
            assertEquals(2,lib.records().size);lib.endPlayback(cp.token)
            SQLiteDatabase.openDatabase(c.getDatabasePath(a).path,null,SQLiteDatabase.OPEN_READWRITE).use{db->db.execSQL("INSERT INTO library VALUES('broken','{broken',0,9000,10000,1,1,0)")}
            lib.clearSearches();assertTrue(lib.searches().isEmpty());assertEquals(30000L,lib.records().first{it.listed}.positionMs)
            lib.clearPlaybackHistory();val remaining=lib.records();assertEquals(1,remaining.size);assertTrue(remaining.single().listed);assertEquals(0L,remaining.single().positionMs)
            assertEquals(true,series.state(card).marks["1:1"])
            SQLiteDatabase.openDatabase(c.getDatabasePath(a).path,null,SQLiteDatabase.OPEN_READONLY).use{db->assertEquals(1L,db.rawQuery("SELECT COUNT(*) FROM library WHERE identity='broken'",null).use{it.moveToFirst();it.getLong(0)})}
            overrides.clear();assertEquals(PlaybackOverrides(),overrides.get(setOf(card.workId)));assertTrue(lib.records().single().listed)
        }}}}finally{listOf(a,b,d).forEach{c.deleteDatabase(it)}}
        Unit
    }
}
