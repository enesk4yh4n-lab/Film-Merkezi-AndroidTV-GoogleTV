package app.filmmerkezi.tv

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DiziboxIdentity103MigrationTest {
    @Test fun oldDiziboxEvidenceIsRecheckedWithoutLosingWorkOrLibrary()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="identity-dizibox-103.db";val library="identity-dizibox-103-library.db"
        context.deleteDatabase(name);context.deleteDatabase(library)
        val raw=Card(ContentRef("dizibox",Sites.get("dizibox").base+"/diziler/visionquest/",ContentKind.SERIES),"VisionQuest")
        val stale=raw.copy(identityEvidence=IdentityEvidence(raw.ref.pageUrl,ContentKind.SERIES,typeVerified=true,detailChecked=true,revision=3))
        try {
            val id=WorkStore(context,name).use{it.item(raw).identity}
            LibraryStore(context,library).use{it.update(raw.copy(workId=id),listed=true,positionMs=45000,durationMs=100000)}
            // A genuine v2 DB written by 0.10.2: do not exercise only onUpgrade(v1).
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READWRITE).use{db->
                assertEquals(2,db.version)
                db.execSQL("UPDATE sources SET card=?",arrayOf(Contracts.card(stale.copy(workId=id)).toString()))
            }
            WorkStore(context,name).use{s->
                val pending=s.item(raw);assertEquals(id,pending.identity)
                assertFalse(pending.card.identityEvidence!!.detailChecked)
                assertEquals(4,pending.card.identityEvidence!!.revision)
                assertTrue(ContentIdentity.verificationCandidates(listOf(pending.card),"vision").any{it.ref.id==raw.ref.id})
                val html="""<div class="tv-overview"><div class="title-terms"><h1>VisionQuest</h1><div class="terms"><a href="/yil/2026/">2026</a></div></div></div><div id="seasons-list"></div>"""
                val fresh=PageIdentity.attach(html,CatalogParser(Sites.get("dizibox")).detail(html,pending.card),pending.card).card
                assertEquals(id,s.item(fresh).identity)
                val other=Card(ContentRef("hdfilmcehennemi",Sites.get("hdfilmcehennemi").base+"/dizi/visionquest/",ContentKind.SERIES),"VisionQuest",year=2026)
                val joined=s.observe(listOf(raw,other)).single();assertEquals(id,joined.identity);assertEquals(2,joined.alternatives.size)
                val late=s.item(stale).alternatives.single{it.ref.siteId=="dizibox"}
                assertEquals(2026,late.year);assertTrue(late.identityEvidence!!.yearVerified);assertTrue(late.identityEvidence!!.detailChecked)
            }
            WorkStore(context,name).use{s->assertTrue(s.item(raw).alternatives.single{it.ref.siteId=="dizibox"}.identityEvidence!!.detailChecked)}
            LibraryStore(context,library).use{s->val r=s.records().single();assertTrue(r.listed);assertEquals(45000L,r.positionMs);assertEquals(id,r.card.workId)}
        } finally {context.deleteDatabase(name);context.deleteDatabase(library)}
        Unit
    }

    @Test fun unrelatedAndAlreadyVerifiedEvidenceIsNotInvalidated()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val name="identity-dizibox-103-scope.db"
        context.deleteDatabase(name)
        try {WorkStore(context,name).use{s->
            for(site in listOf("dizibox","hdfilmcehennemi")){
                val c=Card(ContentRef(site,Sites.get(site).base+"/diziler/example/",ContentKind.SERIES),"Example",year=2026)
                val e=IdentityEvidence(c.ref.pageUrl,ContentKind.SERIES,2026,typeVerified=true,yearVerified=site=="dizibox",detailChecked=true,revision=3)
                assertTrue(s.item(c.copy(identityEvidence=e)).card.identityEvidence!!.detailChecked)
            }
        }} finally {context.deleteDatabase(name)}
        Unit
    }
}
