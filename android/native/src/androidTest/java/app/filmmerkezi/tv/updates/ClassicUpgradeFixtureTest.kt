package app.filmmerkezi.tv.updates

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.Card
import app.filmmerkezi.tv.core.ContentIdentity
import app.filmmerkezi.tv.core.ContentKind
import app.filmmerkezi.tv.core.ContentRef
import app.filmmerkezi.tv.core.LibraryStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Seeds two records in the isolated app. The host installs the old and new shrunk APKs over them. */
class ClassicUpgradeFixtureTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences("interface-preferences", 0)
    private val backup = File(context.filesDir, "classic-upgrade-ui.json")
    private val listed = Card(ContentRef("classic-qa", "https://classic-upgrade.example/listed/", ContentKind.MOVIE), "Classic QA Listed")
    private val continuing = Card(ContentRef("classic-qa", "https://classic-upgrade.example/continuing/", ContentKind.MOVIE), "Classic QA Continue")
    private val keys = listOf("configured", "style", "language")

    @Before fun requireUpgradeHarness() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("classicUpgrade") == "true")
        check(context.packageName.endsWith(".qa"))
    }

    @Test fun seedFixtures() = runBlocking {
        check(!backup.exists())
        val all = preferences.all
        backup.writeText(JSONObject(all.filterKeys { it in keys }).toString())
        check(preferences.edit().putBoolean("configured", true).putString("style", "classic").putString("language", "tr").commit())
        LibraryStore(context).use {
            it.update(listed, listed = true)
            it.update(continuing, listed = false, positionMs = 42_000, durationMs = 120_000)
        }
    }

    @Test fun verifyFixturesAndRestorePreferences() = runBlocking {
        try {
            LibraryStore(context).use {
                assertTrue(it.records(listedOnly = true).any { row -> row.card.title == listed.title && row.listed })
                val row = it.records(historyOnly = true).single { row -> row.card.title == continuing.title }
                assertEquals(42_000L, row.positionMs)
                assertEquals(120_000L, row.durationMs)
            }
        } finally {
            SQLiteDatabase.openDatabase(context.getDatabasePath("film-library.db").path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.delete("library", "identity IN (?,?)", arrayOf(ContentIdentity.key(listed), ContentIdentity.key(continuing)))
            }
            val original = JSONObject(backup.readText())
            val edit = preferences.edit()
            for (key in keys) when (val value = original.opt(key)) {
                is Boolean -> edit.putBoolean(key, value)
                is String -> edit.putString(key, value)
                is Int -> edit.putInt(key, value)
                else -> edit.remove(key)
            }
            check(edit.commit())
            check(backup.delete())
        }
    }
}
