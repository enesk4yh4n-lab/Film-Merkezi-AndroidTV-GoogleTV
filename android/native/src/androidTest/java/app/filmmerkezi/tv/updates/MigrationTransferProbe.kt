package app.filmmerkezi.tv.updates

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import app.filmmerkezi.tv.core.*

/** Opt-in two-install signing migration; the host must save the export before uninstall. */
class MigrationTransferProbe {
    @Test fun seedVerificationData()=runBlocking{
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("seedMigration")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val raw=Card(ContentRef("filmcibaba","https://filmizle.ink/migration-verification/",ContentKind.SERIES),"Migration verification")
        val card=WorkStore(context).use{it.item(raw).card}
        val n=EpisodeNumber(1,2);val episode=Episode(raw.copy(ref=ContentRef("filmcibaba","https://filmizle.ink/migration-verification-1-sezon-2-bolum/",ContentKind.EPISODE)),card.ref,n)
        LibraryStore(context).use{it.update(card,listed=true,positionMs=42000,durationMs=120000);it.rememberSearch("Migration verification")}
        SeriesTrackingStore(context).use{it.follow(card,listOf(episode),true);it.mark(card,listOf(n),true,manual=true)}
        // The release probe references only the public core/wire ABI. Do not force
        // unshrunk settings implementation classes into the production APK for tests.
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("film-playback-preferences.db"),null).use{db->
            db.execSQL("CREATE TABLE IF NOT EXISTS overrides(work TEXT PRIMARY KEY,payload TEXT NOT NULL,updated INTEGER NOT NULL)")
            db.execSQL("INSERT OR REPLACE INTO overrides(work,payload,updated) VALUES(?,?,?)",arrayOf<Any>(card.workId,"{\"audio\":\"en\",\"text\":\"tr\",\"height\":1080}",System.currentTimeMillis()));db.version=1
        }
        Unit
    }
    @Test fun exportInstalledData()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=DataTransferStore(context).export()
        assertTrue(file.length()>0)
    }
    @Test fun importPreviouslyExportedData()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.getExternalFilesDir(null),"migration-input.fmdata")
        assertTrue("ADB must supply a verified external backup",file.exists())
        val store=DataTransferStore(context);val plan=store.prepare(file.inputStream());store.schedule(plan)
        assertTrue(store.applyPending()!!.contains("başarıyla"))
        store.export();Unit
    }
}
