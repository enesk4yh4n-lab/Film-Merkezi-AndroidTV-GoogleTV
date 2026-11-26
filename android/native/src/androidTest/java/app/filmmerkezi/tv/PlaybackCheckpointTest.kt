package app.filmmerkezi.tv

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class PlaybackCheckpointTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val movie=Card(ContentRef("filmcibaba","https://filmizle.ink/ux-checkpoint/"),"Checkpoint deneyi",year=2026,workId="work:ux-checkpoint")
    private val show=movie.copy(ref=movie.ref.copy(kind=ContentKind.SERIES),workId="work:ux-show")
    private fun episode(n:Int)=movie.copy(ref=movie.ref.copy(pageUrl="https://filmizle.ink/ux-episode-$n/",kind=ContentKind.EPISODE),workId="work:ux-episode-$n",seriesIdentity=show.workId,seriesRef=show.ref,episodeNumber=EpisodeNumber(1,n))
    private fun stores(block:suspend(LibraryStore,SeriesTrackingStore)->Unit)=runBlocking{
        val a="ux-checkpoint-test.db";val b="ux-series-test.db";context.deleteDatabase(a);context.deleteDatabase(b)
        try{LibraryStore(context,a).use{lib->SeriesTrackingStore(context,b).use{series->block(lib,series)}}}finally{context.deleteDatabase(a);context.deleteDatabase(b)}
    }
    @Test fun cancellingUnstartedPlaybackCannotOverwriteNewerProgress()=stores{lib,series->
        val ledger=PlaybackLedger(lib,series);val cp=ledger.begin(movie)
        lib.update(movie,listed=true,positionMs=25000,durationMs=600000)
        ledger.abandon(cp)
        assertEquals(25000L,lib.records().single().positionMs);assertTrue(lib.records().single().listed)
        assertTrue(lib.playbackJournals().isEmpty())
        ledger.save(cp,0,600000);assertEquals(25000L,lib.records().single().positionMs)
    }
    @Test fun rollbackRestoresPositionHistoryAndPreservesNewListAndIdentity()=stores{lib,series->
        lib.update(movie,positionMs=25000,durationMs=100000)
        val old=lib.records().single();val ledger=PlaybackLedger(lib,series);val cp=ledger.begin(movie)
        ledger.save(cp,95000,100000);lib.update(movie.copy(originalTitle="Verified title",imdbId="tt1234567"),listed=true)
        ledger.finish(cp,PlaybackFinish.DISCARD,99000,100000)
        val restored=lib.records().single();assertEquals(25000L,restored.positionMs);assertEquals(old.playbackUpdatedAt,restored.playbackUpdatedAt)
        assertTrue(restored.listed);assertEquals("tt1234567",restored.card.imdbId)
        ledger.save(cp,99000,100000);ledger.finish(cp,PlaybackFinish.SAVE,99999,100000)
        assertEquals(25000L,lib.records().single().positionMs);assertTrue(lib.playbackJournals().isEmpty())
    }
    @Test fun firstWatchDiscardRemovesHistoryButPreservesFavourite()=stores{lib,series->
        val ledger=PlaybackLedger(lib,series);var cp=ledger.begin(movie)
        ledger.save(cp,25000,100000);ledger.finish(cp,PlaybackFinish.DISCARD,30000,100000);assertTrue(lib.records().isEmpty())
        cp=ledger.begin(movie);ledger.save(cp,25000,100000);lib.update(movie,listed=true)
        ledger.finish(cp,PlaybackFinish.DISCARD,30000,100000)
        assertTrue(lib.records().single().listed);assertEquals(0L,lib.records().single().positionMs)
    }
    @Test fun automaticEpisodeMarkRollsBackButManualAndPriorEpisodeSurvive()=stores{lib,series->
        val ledger=PlaybackLedger(lib,series,parent={show});val previous=ledger.begin(episode(1))
        ledger.finish(previous,PlaybackFinish.SAVE,100000,100000)
        val cp=ledger.begin(episode(2));ledger.save(cp,98000,100000)
        assertTrue(series.state(show).marks["1:2"]==true)
        ledger.finish(cp,PlaybackFinish.DISCARD,99000,100000)
        assertNull(series.state(show).marks["1:2"]);assertTrue(series.state(show).marks["1:1"]==true)
        val manual=ledger.begin(episode(2));series.mark(show,listOf(EpisodeNumber(1,2)),false,true)
        ledger.save(manual,99000,100000);ledger.finish(manual,PlaybackFinish.DISCARD,99000,100000)
        assertEquals(false,series.state(show).marks["1:2"])
    }
    @Test fun durableDiscardRecoversWhileAbandonedOpenKeepsAutosave()=stores{lib,series->
        lib.update(movie,positionMs=20000,durationMs=100000)
        val ledger=PlaybackLedger(lib,series);val cp=ledger.begin(movie);ledger.save(cp,80000,100000)
        lib.discardIntent(cp.token);lib.restorePlayback(cp.token,movie) // process dies between two stores
        PlaybackLedger(lib,series).recover();assertEquals(20000L,lib.records().single().positionMs);assertTrue(lib.playbackJournals().isEmpty())
        val next=PlaybackLedger(lib,series);val cp2=next.begin(movie);next.save(cp2,60000,100000)
        PlaybackLedger(lib,series).recover();assertEquals(60000L,lib.records().single().positionMs)
    }
    @Test fun queuedAutosavesCannotRecreateDiscardedSession()=stores{lib,series->
        val ledger=PlaybackLedger(lib,series);val cp=ledger.begin(movie)
        coroutineScope{val jobs=(1..15).map{i->launch{ledger.save(cp,i*1000L,100000)}};ledger.finish(cp,PlaybackFinish.DISCARD,20000,100000);jobs.joinAll()}
        assertTrue(lib.records().isEmpty())
    }
    @Test fun seriesIdentityRedirectDuringSessionPreservesManualAndRollback()=stores{lib,series->
        var merged=false
        val canonical:suspend(Card)->Card={c->if(merged&&c.ref.kind==ContentKind.SERIES)c.copy(workId="work:merged-show")else c}
        val ledger=PlaybackLedger(lib,series,canonical,parent={show})
        val cp=ledger.begin(episode(1));merged=true
        ledger.save(cp,99000,100000);assertTrue(series.state(canonical(show)).marks["1:1"]==true)
        ledger.finish(cp,PlaybackFinish.DISCARD,99000,100000);assertNull(series.state(canonical(show)).marks["1:1"])
        merged=false;series.mark(show,listOf(EpisodeNumber(1,2)),false,true)
        val manual=ledger.begin(episode(2));merged=true;ledger.save(manual,99000,100000)
        ledger.finish(manual,PlaybackFinish.DISCARD,99000,100000);assertEquals(false,series.state(canonical(show)).marks["1:2"])
    }
    @Test fun versionTwoUpgradesWithoutDeletingRecords()=runBlocking{
        val name="ux-upgrade-test.db";context.deleteDatabase(name)
        try{
            val path=context.getDatabasePath(name);path.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(path,null).use{db->
                db.execSQL("CREATE TABLE library(identity TEXT PRIMARY KEY,card TEXT NOT NULL,listed INTEGER NOT NULL DEFAULT 0,position INTEGER NOT NULL DEFAULT 0,duration INTEGER NOT NULL DEFAULT 0,updated INTEGER NOT NULL,playback_updated INTEGER NOT NULL DEFAULT 0,listed_updated INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE TABLE searches(query TEXT PRIMARY KEY,updated INTEGER NOT NULL)")
                db.execSQL("INSERT INTO library VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>(ContentIdentity.key(movie),Contracts.card(movie).toString(),1,25000,100000,1234,1230,1234));db.version=2
            }
            LibraryStore(context,name).use{lib->val row=lib.records().single();assertTrue(row.listed);assertEquals(25000L,row.positionMs);lib.beginPlayback(movie,org.json.JSONObject());assertEquals(1,lib.playbackJournals().size)}
            SQLiteDatabase.openDatabase(path.path,null,SQLiteDatabase.OPEN_READONLY).use{assertEquals(3,it.version)}
        }finally{context.deleteDatabase(name)}
    }
}
