package app.filmmerkezi.tv

import app.filmmerkezi.tv.core.*
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class IdentityMigrationTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun card(site:String,title:String)=Card(ContentRef(site,Sites.get(site).base+"/migration-example/"),title,year=1999)
    private fun legacy(name:String,rows:List<Pair<String,Card>>){
        context.getDatabasePath(name).parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null).use{db->
            db.execSQL("CREATE TABLE works(id TEXT PRIMARY KEY,anchor TEXT NOT NULL,created INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE sources(source TEXT PRIMARY KEY,work TEXT NOT NULL,card TEXT NOT NULL,updated INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX source_work ON sources(work)")
            db.execSQL("CREATE TABLE tokens(token TEXT NOT NULL,source TEXT NOT NULL,PRIMARY KEY(token,source))")
            db.execSQL("CREATE TABLE redirects(old TEXT PRIMARY KEY,current TEXT NOT NULL)")
            db.execSQL("CREATE TABLE discovery(work TEXT PRIMARY KEY,checked INTEGER NOT NULL)")
            rows.forEachIndexed{i,(id,c)->val source=c.ref.siteId+":"+c.ref.pageUrl.trimEnd('/')
                db.execSQL("INSERT INTO works VALUES(?,?,?)",arrayOf<Any>(id,source,100+i))
                db.execSQL("INSERT INTO sources VALUES(?,?,?,?)",arrayOf<Any>(source,id,Contracts.card(c.copy(workId=id)).toString(),100+i))
                db.execSQL("INSERT INTO tokens VALUES(?,?)",arrayOf("name:"+TextKey.fold(c.title),source))
            };db.version=1
        }
    }
    @Test fun reindexFindsOldFirstInstallmentAndPreservesIdsLibraryAndProgress()=runBlocking{
        val name="identity-migration-v1.db";val libraryName="identity-migration-library.db"
        context.deleteDatabase(name);context.deleteDatabase(libraryName)
        val a=card("sinemacc","The Example").copy(imdbId="tt1111111",workId="work:old-first")
        val b=card("fullhdfilmizlesene","Example 1").copy(workId="work:old-second")
        try{
            legacy(name,listOf(a.workId to a,b.workId to b))
            LibraryStore(context,libraryName).use{it.update(a,listed=true,positionMs=45000,durationMs=100000);it.rememberSearch("Example")}
            WorkStore(context,name).use{s->
                val joined=s.item(b);assertEquals(a.workId,joined.identity);assertEquals(2,joined.alternatives.size)
                assertEquals(a.workId,s.cached(b.workId)!!.identity)
                assertEquals(a.workId,s.item(a.copy(title="Example HD izle")).identity)
            }
            WorkStore(context,name).use{assertEquals(a.workId,it.item(b).identity)}
            LibraryStore(context,libraryName).use{val row=it.records().single();assertTrue(row.listed);assertEquals(45000L,row.positionMs);assertEquals(listOf("Example"),it.searches())}
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READONLY).use{assertEquals(2,it.version)}
        }finally{context.deleteDatabase(name);context.deleteDatabase(libraryName)}
    }
    @Test fun legacyWebCreationYearIsInvalidatedWithoutChangingWorkOrImdb()=runBlocking{
        val name="identity-legacy-year.db";context.deleteDatabase(name)
        val c=card("filmizlehell","Example").copy(year=2026,imdbId="tt1111111",identityEvidence=IdentityEvidence(card("filmizlehell","Example").ref.pageUrl,ContentKind.MOVIE,2026,"tt1111111",typeVerified=true,yearVerified=true,detailChecked=true,revision=0))
        try{
            legacy(name,listOf("work:preserved" to c))
            WorkStore(context,name).use{s->
                val pending=s.cached("work:preserved")!!.card;assertNull(pending.year);assertEquals("tt1111111",pending.imdbId)
                assertFalse(pending.identityEvidence!!.detailChecked)
                val refreshed=c.copy(year=1999,identityEvidence=c.identityEvidence!!.copy(year=1999,reason="primary-release-year",revision=2))
                assertEquals("work:preserved",s.item(refreshed).identity);assertEquals(1999,s.cached("work:preserved")!!.card.year)
                assertEquals(1999,s.item(c).card.year) // Old library/navigation payload cannot restore 2026.
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READWRITE).use{db->
                db.execSQL("UPDATE sources SET card=?",arrayOf(Contracts.card(c).toString()))
            }
            WorkStore(context,name).use{s->assertNull(s.item(c).card.year);assertEquals("work:preserved",s.item(c).identity)}
        }finally{context.deleteDatabase(name)}
    }
    @Test fun brokenLegacyRowRollsBackUpgradeAndRetainsOriginalBytes()=runBlocking{
        val name="identity-damaged-v1.db";context.deleteDatabase(name)
        try{
            legacy(name,listOf("work:kept" to card("sinemacc","Example")))
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READWRITE).use{it.execSQL("UPDATE sources SET card='broken-original'")}
            var failed=false
            try{WorkStore(context,name).use{it.item(card("sinemacc","Example"))}}catch(e:DataFailure){failed=true}
            assertTrue(failed)
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READONLY).use{db->
                assertEquals(1,db.version);db.rawQuery("SELECT card FROM sources",null).use{assertTrue(it.moveToFirst());assertEquals("broken-original",it.getString(0))}
            }
        }finally{context.deleteDatabase(name)}
    }
    @Test fun cachedCatalogCannotReplaceNewerPrimaryIdentityEvidence()=runBlocking{
        val name="identity-cache-rebase.db";context.deleteDatabase(name)
        try{WorkStore(context,name).use{s->
            val old=card("sinemacc","The Example");val id=s.item(old).identity
            val fresh=old.copy(imdbId="tt1111111",identityEvidence=IdentityEvidence(old.ref.pageUrl,ContentKind.MOVIE,1999,"tt1111111",originalTitle="Independent Original",typeVerified=true,yearVerified=true,detailChecked=true))
            s.item(fresh)
            val rebound=s.refresh(listOf(old)).single()
            assertEquals(id,rebound.identity);assertEquals("Independent Original",rebound.alternatives.single().identityEvidence!!.originalTitle)
            assertEquals("tt1111111",s.cached(id)!!.card.imdbId)
        }}finally{context.deleteDatabase(name)}
    }
    @Test fun previousParserCheckedFlagDoesNotHideMissingAliasesOrYear()=runBlocking{
        val name="identity-legacy-checked.db";context.deleteDatabase(name)
        val raw=card("filmcibaba","The Example 4 Long Local Title").copy(year=null)
        val old=raw.copy(identityEvidence=IdentityEvidence(raw.ref.pageUrl,ContentKind.MOVIE,typeVerified=true,detailChecked=true,revision=0))
        try{
            legacy(name,listOf("work:unchanged" to old))
            WorkStore(context,name).use{s->
                val pending=s.item(old);assertEquals("work:unchanged",pending.identity)
                assertFalse(pending.card.identityEvidence!!.detailChecked);assertEquals(2,pending.card.identityEvidence!!.revision)
                val current=raw.copy(year=2021,identityEvidence=IdentityEvidence(raw.ref.pageUrl,ContentKind.MOVIE,2021,aliases=listOf("The Example 4"),typeVerified=true,yearVerified=true,detailChecked=true))
                assertEquals("work:unchanged",s.item(current).identity)
                assertTrue(s.cached("work:unchanged")!!.card.identityEvidence!!.detailChecked)
            }
        }finally{context.deleteDatabase(name)}
    }
}
