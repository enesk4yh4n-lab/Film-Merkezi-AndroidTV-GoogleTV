package app.filmmerkezi.tv.updates

import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DataTransferStoreTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun card(i:Int=0)=Card(ContentRef("filmcibaba","https://filmizle.ink/transfer-$i/"),"Aktarım $i",workId="work:transfer-$i")
    @Test fun roundTripPreservesAllListedRowsBeyondPresentationLimit()=runBlocking {
        val prefix="transfer-test-${System.nanoTime()}-";val store=DataTransferStore(context,prefix)
        LibraryStore(context,prefix+"film-library.db").use{library->for(i in 0..204)library.update(card(i),listed=true,positionMs=42000,durationMs=120000)}
        context.getSharedPreferences(prefix+"film-settings",0).edit().putBoolean("merge",false).putInt("schema",3).putString("siteAddresses","{}").commit()
        val file=store.export();val prepared=store.prepare(file.inputStream());assertTrue(prepared.getInt("rows")>=205)
        LibraryStore(context,prefix+"film-library.db").use{it.update(card(),positionMs=88000)}
        store.schedule(prepared);assertTrue(store.applyPending()!!.contains("başarıyla"))
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(prefix+"film-library.db").path,null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use{db->db.rawQuery("SELECT COUNT(*),MIN(position),MAX(position) FROM library",null).use{r->assertTrue(r.moveToFirst());assertEquals(205,r.getInt(0));assertEquals(42000,r.getLong(1));assertEquals(42000,r.getLong(2))}}
        assertFalse(context.getSharedPreferences(prefix+"film-settings",0).getBoolean("merge",true));assertNull(store.applyPending())
    }
    @Test fun interruptedApplyCompletesAndMalformedPreferencesRollBack()=runBlocking {
        val prefix="transfer-fail-${System.nanoTime()}-";val store=DataTransferStore(context,prefix)
        LibraryStore(context,prefix+"film-library.db").use{it.update(card(),listed=true,positionMs=42000,durationMs=120000)}
        val file=store.export();val prepared=store.prepare(file.inputStream())
        LibraryStore(context,prefix+"film-library.db").use{it.update(card(),positionMs=88000)}
        prepared.getJSONObject("preferences").put("film-settings",JSONObject().put("schema",JSONObject().put("t",99).put("v",3)))
        store.schedule(prepared);assertTrue(store.applyPending()!!.contains("geri alındı"))
        LibraryStore(context,prefix+"film-library.db").use{assertEquals(88000,it.records().single().positionMs)}
        val second=store.prepare(file.inputStream());store.schedule(second)
        val root=java.io.File(context.filesDir,prefix+"transfer");val name="film-library.db";val info=second.getJSONObject("files").getJSONObject(name)
        val target=context.getDatabasePath(prefix+name);val backup=java.io.File(root,second.getString("id")+"-$name.old")
        assertTrue(target.renameTo(backup));assertTrue(context.getDatabasePath(info.getString("stage")).renameTo(target))
        // Crash point: files moved, atomic journal still says installed=false.
        assertTrue(store.applyPending()!!.contains("başarıyla"));LibraryStore(context,prefix+name).use{assertEquals(42000,it.records().single().positionMs)}
    }
    @Test fun tamperedBackupIsRejectedBeforeAnyDatabaseChange()=runBlocking {
        val prefix="transfer-hash-${System.nanoTime()}-";val store=DataTransferStore(context,prefix)
        LibraryStore(context,prefix+"film-library.db").use{it.update(card(),listed=true,positionMs=42000,durationMs=120000)}
        val raw=JSONObject(store.export().readText()).put("sha256","0".repeat(64)).toString()
        try{store.prepare(raw.byteInputStream());fail()}catch(_:IllegalArgumentException){}
        LibraryStore(context,prefix+"film-library.db").use{assertEquals(42000,it.records().single().positionMs)}
    }
    @Test fun identityManualEpisodeMarksAndSemanticPreferencesSurvive()=runBlocking {
        val prefix="transfer-semantic-${System.nanoTime()}-";val transfer=DataTransferStore(context,prefix)
        val raw=card(301).copy(ref=ContentRef("filmcibaba","https://filmizle.ink/transfer-series/",ContentKind.SERIES),workId="")
        val canonical=WorkStore(context,prefix+"film-works.db").use{it.item(raw).card}
        val number=EpisodeNumber(2,3);val episode=Episode(card(302).copy(ref=ContentRef("filmcibaba","https://filmizle.ink/transfer-series-2-sezon-3-bolum/",ContentKind.EPISODE)),canonical.ref,number)
        SeriesTrackingStore(context,prefix+"film-series.db").use{it.follow(canonical,listOf(episode),true);it.mark(canonical,listOf(number),true,manual=true)}
        app.filmmerkezi.tv.settings.PlaybackOverridesStore(context,prefix+"film-playback-preferences.db").use{it.update(canonical.workId,setOf(canonical.workId)){v->v.copy(audio="en",text="tr",height=1080)}}
        LibraryStore(context,prefix+"film-library.db").use{it.update(canonical,listed=true,positionMs=42000);it.rememberSearch("Aktarım Türkçe")}
        val file=transfer.export();val prepared=transfer.prepare(file.inputStream());transfer.schedule(prepared);assertTrue(transfer.applyPending()!!.contains("başarıyla"))
        WorkStore(context,prefix+"film-works.db").use{assertEquals(canonical.workId,it.item(raw).identity)}
        SeriesTrackingStore(context,prefix+"film-series.db").use{val s=it.state(canonical);assertTrue(s.following);assertEquals(true,s.marks[number.trackingKey()])}
        app.filmmerkezi.tv.settings.PlaybackOverridesStore(context,prefix+"film-playback-preferences.db").use{val v=it.get(setOf(canonical.workId));assertEquals("en",v.audio);assertEquals("tr",v.text);assertEquals(1080,v.height)}
        LibraryStore(context,prefix+"film-library.db").use{assertTrue(it.records().single().listed);assertEquals("Aktarım Türkçe",it.searches().single())}
    }
}
