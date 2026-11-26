package app.filmmerkezi.tv

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage4StorageTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun card(n:Int)=Card(ContentRef("filmcibaba","https://filmizle.ink/stage4-$n/"),"Kayıt $n",year=2025,imdbId="tt"+(8000000+n))
    @Test fun healthyRowsSurviveBadJsonAndPaginationAndPruning()=runBlocking {
        val name="stage4-rows.db";context.deleteDatabase(name)
        try {
            LibraryStore(context,name).use{s->repeat(4){s.update(card(it),listed=true,positionMs=23000+it.toLong(),durationMs=100000)}}
            val damagedKey=ContentIdentity.key(card(99));val raw="{broken preserved payload"
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READWRITE).use{db->
                db.insertOrThrow("library",null,ContentValues().apply{
                    put("identity",damagedKey);put("card",raw);put("updated",Long.MAX_VALUE);put("listed",0)
                })
                db.insertOrThrow("searches",null,ContentValues().apply{put("query"," ");put("updated",0)})
            }
            LibraryStore(context,name).use{s->
                val one=s.records(true,2,0);val two=s.records(true,2,2)
                assertEquals(4,(one+two).map{it.identity}.distinct().size)
                assertEquals(4,s.records().size);assertTrue(s.hasUnreadableRecords)
                try{s.update(card(99),listed=true);fail("Damaged exact row must not be overwritten")}
                catch(e:DataFailure){assertEquals(FailureKind.STORAGE,e.kind)}
                repeat(205){s.update(card(1000+it),positionMs=it.toLong()+1)}
                repeat(25){s.rememberSearch("Arama $it")}
                assertEquals(200,s.records(historyOnly=true).count{!it.listed})
                assertEquals(4,s.records(true).size);assertEquals(20,s.searches().size)
            }
            LibraryStore(context,name).use{s->
                assertEquals(204,s.records(limit=500).size)
                assertEquals(20,s.searches().size)
                s.records(true).forEach{assertTrue(it.positionMs>=23000);assertEquals(100000L,it.durationMs)}
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READONLY).use{db->
                db.rawQuery("SELECT card FROM library WHERE identity=?",arrayOf(damagedKey)).use{assertTrue(it.moveToFirst());assertEquals(raw,it.getString(0))}
                db.rawQuery("SELECT query FROM searches WHERE query=' '",null).use{assertTrue(it.moveToFirst())}
            }
        }finally{context.deleteDatabase(name)}
        Unit
    }
    @Test fun wholeDatabaseCorruptionIsReportedWithoutDeletingOriginal()=runBlocking {
        val name="stage4-damaged.db";context.deleteDatabase(name)
        val file=context.getDatabasePath(name);file.parentFile?.mkdirs()
        val bytes="stage4 damaged database original bytes".toByteArray();file.writeBytes(bytes)
        try {
            try{LibraryStore(context,name).use{it.records()};fail("Must surface whole database corruption")}
            catch(e:DataFailure){assertEquals(FailureKind.STORAGE,e.kind)}
            assertArrayEquals(bytes,file.readBytes())
        }finally{context.deleteDatabase(name)}
        Unit
    }
    @Test fun concurrentListAndProgressUpdatesKeepBothAcrossReopen()=runBlocking {
        val name="stage4-concurrent.db";context.deleteDatabase(name)
        val episode=card(10).copy(ref=card(10).ref.copy(kind=ContentKind.EPISODE),episodeNumber=EpisodeNumber(1,2),
            seriesRef=ContentRef("filmcibaba","https://filmizle.ink/stage4-series/",ContentKind.SERIES),seriesIdentity="series-stage4")
        try{
            LibraryStore(context,name).use{s->
                coroutineScope{
                    launch(Dispatchers.Default){s.update(episode,listed=true)}
                    launch(Dispatchers.Default){s.update(episode,positionMs=34567,durationMs=500000)}
                }
                s.rememberSearch("Türkçe Işık");s.rememberSearch("Türkçe Işık")
            }
            LibraryStore(context,name).use{s->
                val r=s.records().single();assertTrue(r.listed);assertEquals(34567L,r.positionMs)
                assertEquals(episode.seriesRef,r.card.seriesRef);assertEquals(episode.episodeNumber,r.card.episodeNumber)
                assertEquals(listOf("Türkçe Işık"),s.searches())
            }
        }finally{context.deleteDatabase(name)}
        Unit
    }
}
