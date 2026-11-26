package app.filmmerkezi.tv

import app.filmmerkezi.tv.core.*
import androidx.test.platform.app.InstrumentationRegistry
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Test
import org.junit.Assert.*

class WorkStorageTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun card(site:String="filmcibaba",slug:String="matrix")=Card(ContentRef(site,Sites.get(site).base+"/"+slug+"/"),"Matrix",year=1999)
    @Test fun idsSurviveReopenMergingAndContradictions()=runBlocking {
        val name="work-test.db";context.deleteDatabase(name)
        try{
            val a=card();val b=card("sinemacc")
            var id=""
            WorkStore(context,name).use{store->id=store.item(a).identity;assertEquals(id,store.item(a.copy(imdbId="tt0133093")).identity);assertEquals(id,store.item(b.copy(originalTitle="The Matrix")).identity)}
            WorkStore(context,name).use{store->assertEquals(id,store.item(a).identity);val separated=store.item(b.copy(imdbId="tt9999999",identityEvidence=IdentityEvidence(b.ref.pageUrl,ContentKind.MOVIE,1999,"tt9999999",detailChecked=true,typeVerified=true)));assertNotEquals(id,separated.identity);assertEquals(1,store.cached(id)!!.alternatives.size)}
        }finally{context.deleteDatabase(name)}
    }
    @Test fun libraryV1UpgradePreservesDataAndNewCardContract()=runBlocking {
        val name="work-library-v1.db";context.deleteDatabase(name)
        val c=card();val raw=Contracts.card(c).put("schemaVersion",1).toString()
        try{
            val path=context.getDatabasePath(name);path.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(path,null).use{db->
                db.execSQL("CREATE TABLE library(identity TEXT PRIMARY KEY,card TEXT NOT NULL,listed INTEGER NOT NULL DEFAULT 0,position INTEGER NOT NULL DEFAULT 0,duration INTEGER NOT NULL DEFAULT 0,updated INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE searches(query TEXT PRIMARY KEY,updated INTEGER NOT NULL)")
                db.execSQL("INSERT INTO library VALUES(?,?,?,?,?,?)",arrayOf<Any>(ContentIdentity.key(c),raw,1,45000,90000,12345));db.version=1
            }
            LibraryStore(context,name).use{db->val r=db.records().single();assertTrue(r.listed);assertEquals(45000L,r.positionMs);db.update(c.copy(workId="work:stable"),positionMs=46000);assertTrue(db.records().single().listed)}
        }finally{context.deleteDatabase(name)}
    }
    @Test fun newerListTouchCannotReplaceNewerPlaybackFromAnotherSite()=runBlocking{
        val name="work-library-times.db";context.deleteDatabase(name)
        val a=card();val b=card("sinemacc");val merged=card("lovefilmizle").copy(workId="work:merged")
        try{
            LibraryStore(context,name).use{it.update(a,listed=true,positionMs=10000,durationMs=100000)}
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READWRITE).use{db->
                db.execSQL("UPDATE library SET updated=9000,listed_updated=9000,playback_updated=100")
                db.execSQL("INSERT INTO library VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>("legacy:"+b.ref.id,Contracts.card(b).toString(),0,45000,100000,200,200,0))
            }
            LibraryStore(context,name).use{s->s.update(merged);val r=s.records().single();assertEquals(45000L,r.positionMs);assertTrue(r.listed);s.update(merged,listed=false);assertFalse(s.records().single().listed);assertEquals(45000L,s.records().single().positionMs)}
        }finally{context.deleteDatabase(name)}
    }
    @Test fun fiftyEvidenceUpdatesKeepStableIdsWithoutRetainingHtml()=runBlocking{
        val name="work-memory-test.db";context.deleteDatabase(name)
        try{WorkStore(context,name).use{store->
            val cards=(1..20).map{n->card(slug="batch-$n").copy(title="Batch Film $n",year=2024)}
            val ids=store.observe(cards).map{it.identity};repeat(5){store.observe(cards)}
            System.gc();delay(150);val before=android.os.Debug.MemoryInfo().also{android.os.Debug.getMemoryInfo(it)}.totalPss
            repeat(50){assertEquals(ids,store.observe(cards).map{it.identity})}
            System.gc();delay(150);val after=android.os.Debug.MemoryInfo().also{android.os.Debug.getMemoryInfo(it)}.totalPss
            java.io.File(context.getExternalFilesDir(null),"experience-identity-memory.json").writeText(org.json.JSONObject().put("fixture",true).put("cycles",50).put("cards",20).put("beforePssKiB",before).put("afterPssKiB",after).put("growthKiB",after-before).toString(2))
            assertTrue("PSS growth exceeds 10 MiB: ${after-before}",after-before<=10240)
        }}finally{context.deleteDatabase(name)}
    }
    @Test fun largeCatalogKeepsDistinctWorksAndVariants(){
        val providers=listOf("filmcibaba","sinemacc","filmifullizle","lovefilmizle")
        val cards=(0 until 210).flatMap{n->providers.mapIndexed{i,site->
            Card(ContentRef(site,Sites.get(site).base+"/identity-benchmark-$n/"),"Catalog Work${n}"+if(i==1)" 1"else "",year=2024,imdbId=if(i==0)"tt"+(1000000+n)else "")
        }}
        repeat(5){assertEquals(210,ContentIdentity.merge(cards).size)}
        val elapsed=(1..20).map{
            val started=android.os.SystemClock.elapsedRealtimeNanos();val result=ContentIdentity.merge(cards)
            val ms=(android.os.SystemClock.elapsedRealtimeNanos()-started)/1000000.0
            assertEquals(210,result.size);assertTrue(result.all{it.alternatives.size==4});ms
        }
        java.io.File(context.getExternalFilesDir(null),"identity-large-catalog.json").writeText(org.json.JSONObject()
            .put("fixture",true).put("cards",cards.size).put("works",210).put("warmups",5).put("runsMs",org.json.JSONArray(elapsed))
            .put("note","In-memory matching only; no HTTP, SQLite or poster loading. API26 x86_64 emulator.").toString(2))
    }
}
