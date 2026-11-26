package app.filmmerkezi.tv.core

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.*
import android.database.DatabaseErrorHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

/** Persistent page evidence and stable work IDs. No media URLs, cookies or HTML are stored. */
class WorkStore(context:Context,name:String="film-works.db"):AutoCloseable {
    private val mutex=Mutex()
    private val helper=object:SQLiteOpenHelper(context.applicationContext,name,null,2,DatabaseErrorHandler{throw SQLiteDatabaseCorruptException("İçerik kimliği deposu bozuk; özgün dosya korundu")}){
        override fun onCreate(db:SQLiteDatabase){
            db.execSQL("CREATE TABLE works(id TEXT PRIMARY KEY,anchor TEXT NOT NULL,created INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE sources(source TEXT PRIMARY KEY,work TEXT NOT NULL,card TEXT NOT NULL,updated INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX source_work ON sources(work)")
            db.execSQL("CREATE TABLE tokens(token TEXT NOT NULL,source TEXT NOT NULL,PRIMARY KEY(token,source))")
            db.execSQL("CREATE TABLE redirects(old TEXT PRIMARY KEY,current TEXT NOT NULL)")
            db.execSQL("CREATE TABLE discovery(work TEXT PRIMARY KEY,checked INTEGER NOT NULL)")
        }
        override fun onUpgrade(db:SQLiteDatabase,a:Int,b:Int){
            require(a==1&&b==2){"Unsupported works version"}
            // Reindex, never recreate user works/redirects. SQLiteOpenHelper owns the transaction.
            db.query("sources",arrayOf("source","card"),null,null,null,null,null).use{r->while(r.moveToNext()){
                val old=Contracts.readCard(JSONObject(r.getString(1)))
                val c=validateLegacy(old)
                if(c!=old)db.execSQL("UPDATE sources SET card=? WHERE source=?",arrayOf(Contracts.card(c).toString(),r.getString(0)))
                tokens(c).forEach{t->db.insertWithOnConflict("tokens",null,ContentValues().apply{put("token",t);put("source",r.getString(0))},SQLiteDatabase.CONFLICT_IGNORE)}
            }}
        }
    }
    private suspend fun <T> io(block:(SQLiteDatabase)->T):T=withContext(Dispatchers.IO){mutex.withLock{try{block(helper.writableDatabase)}catch(e:CancellationException){throw e}catch(e:Exception){throw DataFailure(FailureKind.STORAGE,"İçerik kimliği kaydı okunamadı; veri korundu","identity-storage",cause=e)}}}
    private fun source(c:Card)=SeriesIdentity.sourceKey(c)
    private fun validateLegacy(c:Card):Card {
        val e=c.identityEvidence?:return c
        val seasonal=c.sourceSeason?:e.season?:SeriesIdentity.season(c.title)
        val needsSeriesCheck=e.revision<3&&(seasonal!=null||e.kind==ContentKind.SERIES||SeriesIdentity.archive(c.ref))
        val needsDiziboxYearCheck=e.revision<4&&c.ref.siteId=="dizibox"&&!e.yearVerified&&(e.kind==ContentKind.SERIES||SeriesIdentity.archive(c.ref))
        if(e.revision>=2&&!needsSeriesCheck&&!needsDiziboxYearCheck)return c
        val suspectYear=(e.revision<2&&e.kind==ContentKind.MOVIE&&c.ref.siteId!="sinemacc"&&e.yearVerified&&e.reason.isBlank())||needsSeriesCheck&&seasonal!=null
        // A previous parser's checked flag must not suppress the new metadata/alias extraction.
        return c.copy(year=if(suspectYear)null else c.year,identityEvidence=e.copy(year=if(suspectYear)null else e.year,
            yearVerified=e.yearVerified&&!suspectYear,detailChecked=false,
            reason=if(suspectYear)"legacy-release-needs-check"else e.reason,revision=if(needsDiziboxYearCheck)4 else if(needsSeriesCheck)3 else 2),sourceSeason=seasonal)
    }
    private fun tokens(c:Card)=ContentIdentity.facts(c).let{f->f.names.keys.map{"name:$it"}+ContentIdentity.candidateNames(c).map{"candidate:$it"}+listOfNotNull(f.imdb.takeIf{it.isNotBlank()}?.let{"imdb:$it"},"source:"+source(c))}
    private fun read(db:SQLiteDatabase,where:String,args:Array<String>)=db.query("sources",null,where,args,null,null,null).use{r->buildList{while(r.moveToNext())add(Contracts.readCard(JSONObject(r.getString(r.getColumnIndexOrThrow("card")))).copy(workId=r.getString(r.getColumnIndexOrThrow("work"))))}}
    private fun canonical(db:SQLiteDatabase,id:String):String {var key=id;val seen=mutableSetOf<String>();while(seen.add(key)){val next=db.rawQuery("SELECT current FROM redirects WHERE old=?",arrayOf(key)).use{if(it.moveToFirst())it.getString(0)else null}?:break;key=next};return key}
    suspend fun observe(cards:List<Card>):List<UnifiedItem> = io{db->
        if(cards.isEmpty())return@io emptyList()
        db.beginTransaction();try{
            val all=linkedMapOf<String,Card>();val old=mutableMapOf<String,String>()
            val prepared=cards.map{input->val c=validateLegacy(input);val prior=read(db,"source=?",arrayOf(source(c))).singleOrNull()?.let(::validateLegacy)
                val value=if(prior!=null&&input.identityEvidence!=null&&(input.identityEvidence.revision< (prior.identityEvidence?.revision?:0)))prior
                    else if(prior!=null&&c.identityEvidence?.detailChecked!=true)c.copy(year=c.year?:prior.year,imdbId=c.imdbId.ifBlank{prior.imdbId},originalTitle=c.originalTitle.ifBlank{prior.originalTitle},aliases=(c.aliases+prior.aliases).distinct(),identityEvidence=prior.identityEvidence,genres=c.genres.ifEmpty{prior.genres})else c
                value.copy(workId=prior?.workId.orEmpty(),adultEvidence=value.adultEvidence?:prior?.adultEvidence)}
            val keys=prepared.flatMap(::tokens).distinct()
            keys.chunked(400).forEach{part->
                db.rawQuery("SELECT DISTINCT s.card,s.work FROM sources s JOIN tokens t ON t.source=s.source WHERE t.token IN ("+part.joinToString(","){"?"}+") LIMIT 512",part.toTypedArray()).use{r->while(r.moveToNext()){val c=Contracts.readCard(JSONObject(r.getString(0))).copy(workId=r.getString(1));all[source(c)]=c}}
            }
            (all.values.map{it.workId}+prepared.map{it.workId}).filter{it.isNotBlank()}.distinct().forEach{id->read(db,"work=?",arrayOf(canonical(db,id))).forEach{all[source(it)]=it}}
            all.values.forEach{old[source(it)]=it.workId};prepared.forEach{if(it.workId.isNotBlank())old[source(it)]=it.workId;all[source(it)]=it}
            val anchors=mutableMapOf<String,String>();val created=mutableMapOf<String,Long>();old.values.distinct().forEach{id->db.rawQuery("SELECT anchor,created FROM works WHERE id=?",arrayOf(id)).use{if(it.moveToFirst()){anchors[id]=it.getString(0);created[id]=it.getLong(1)}}}
            val grouped=ContentIdentity.merge(all.values.map{it.copy(workId="")})
            val used=mutableSetOf<String>();val assigned=grouped.map{g->
                val refs=g.alternatives.map(::source).toSet()
                val id=old.values.distinct().sortedWith(compareBy<String>{created[it]?:Long.MAX_VALUE}.thenBy{it}).firstOrNull{it !in used&&anchors[it] in refs}?:"work:"+UUID.randomUUID().toString()
                used.add(id)
                db.insertWithOnConflict("works",null,ContentValues().apply{put("id",id);put("anchor",source(g.alternatives.first()));put("created",System.currentTimeMillis())},SQLiteDatabase.CONFLICT_IGNORE)
                g.copy(identity=id,card=g.card.copy(workId=id,ref=g.card.ref.copy(kind=ContentIdentity.facts(g.card).kind)),alternatives=g.alternatives.map{it.copy(workId=id)})
            }
            assigned.forEach{g->g.alternatives.forEach{c->val sk=source(c)
                db.insertWithOnConflict("sources",null,ContentValues().apply{put("source",sk);put("work",g.identity);put("card",Contracts.card(c).toString());put("updated",System.currentTimeMillis())},SQLiteDatabase.CONFLICT_REPLACE)
                db.delete("tokens","source=?",arrayOf(sk));tokens(c).forEach{t->db.insertWithOnConflict("tokens",null,ContentValues().apply{put("token",t);put("source",sk)},SQLiteDatabase.CONFLICT_IGNORE)}
                old[sk]?.takeIf{it!=g.identity&&it !in used}?.let{id->db.insertWithOnConflict("redirects",null,ContentValues().apply{put("old",id);put("current",g.identity)},SQLiteDatabase.CONFLICT_REPLACE)}
            }}
            db.setTransactionSuccessful()
            val incoming=prepared.mapIndexed{i,c->source(c) to i}.toMap()
            assigned.filter{g->g.alternatives.any{source(it) in incoming}}.sortedBy{g->g.alternatives.minOf{incoming[source(it)]?:Int.MAX_VALUE}}
        }finally{db.endTransaction()}
    }
    suspend fun item(card:Card):UnifiedItem=observe(listOf(card)).single()
    /** Rebase a catalog cache on current page evidence without writing its older facts back. */
    suspend fun refresh(cards:List<Card>):List<UnifiedItem> = observe(io{db->cards.map{c->read(db,"source=?",arrayOf(source(c))).singleOrNull()?:c}})
    suspend fun cached(id:String)=io{db->val key=canonical(db,id);read(db,"work=?",arrayOf(key)).takeIf{it.isNotEmpty()}?.let{UnifiedItem(key,it.first(),it)}}
    suspend fun sourceCard(ref:ContentRef)=io{db->read(db,"source=?",arrayOf(source(Card(ref,"")))).singleOrNull()}
    suspend fun canonicalId(id:String)=io{db->canonical(db,id)}
    suspend fun aliasesFor(id:String)=io{db->val target=canonical(db,id);val names=mutableSetOf(target)
        db.rawQuery("SELECT old FROM redirects",null).use{r->while(r.moveToNext()){val old=r.getString(0);if(canonical(db,old)==target)names.add(old)}};names.toSet()}
    suspend fun fresh(id:String,scope:String="")=io{db->db.rawQuery("SELECT checked FROM discovery WHERE work=?",arrayOf(canonical(db,id)+scope)).use{it.moveToFirst()&&System.currentTimeMillis()-it.getLong(0) in 0..86400000}}
    suspend fun discovered(id:String,scope:String="")=io{db->db.insertWithOnConflict("discovery",null,ContentValues().apply{put("work",canonical(db,id)+scope);put("checked",System.currentTimeMillis())},SQLiteDatabase.CONFLICT_REPLACE);Unit}
    suspend fun matches(entry:CollectionEntry):List<UnifiedItem> = io{db->
        val names=(listOf(entry.title)+entry.aliases).map{"name:"+TextKey.fold(ContentIdentity.cleanTitle(it,entry.year))}.distinct()
        val cards=names.flatMap{token->db.rawQuery("SELECT s.card FROM sources s JOIN tokens t ON t.source=s.source WHERE t.token=? LIMIT 200",arrayOf(token)).use{r->buildList{while(r.moveToNext())add(Contracts.readCard(JSONObject(r.getString(0))))}}}.filter{entry.matches(it)}
        cards.groupBy{canonical(db,it.workId)}.map{(id,values)->UnifiedItem(id,values.first().copy(workId=id),values)}
    }
    override fun close(){helper.close()}
}
