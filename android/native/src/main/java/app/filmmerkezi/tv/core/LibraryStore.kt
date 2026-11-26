package app.filmmerkezi.tv.core

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.database.Cursor
import org.json.JSONObject

data class LibraryRecord(val identity:String,val card:Card,val listed:Boolean,val positionMs:Long,val durationMs:Long,val updatedAt:Long,val playbackUpdatedAt:Long=updatedAt,val listedUpdatedAt:Long=updatedAt)

/** SQLite transactions; corruption is surfaced and the database is never deleted as recovery. */
class LibraryStore(context:Context,databaseName:String="film-library.db"):AutoCloseable {
    private val lock=Mutex()
    @Volatile var hasUnreadableRecords=false
        private set
    private val helper=object:SQLiteOpenHelper(context.applicationContext,databaseName,null,3,DatabaseErrorHandler {
        throw SQLiteDatabaseCorruptException("Film Merkezi kayıtları bozuk; özgün dosya korundu.")
    }) {
        override fun onCreate(db:SQLiteDatabase) {
            db.execSQL("CREATE TABLE library(identity TEXT PRIMARY KEY,card TEXT NOT NULL,listed INTEGER NOT NULL DEFAULT 0,position INTEGER NOT NULL DEFAULT 0,duration INTEGER NOT NULL DEFAULT 0,updated INTEGER NOT NULL,playback_updated INTEGER NOT NULL DEFAULT 0,listed_updated INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE searches(query TEXT PRIMARY KEY,updated INTEGER NOT NULL)")
            journal(db)
        }
        private fun journal(db:SQLiteDatabase){db.execSQL("CREATE TABLE playback_journal(token TEXT PRIMARY KEY,payload TEXT NOT NULL,state TEXT NOT NULL)")}
        override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){
            if(oldVersion in 1..2&&newVersion==3){
              if(oldVersion==1){
                db.execSQL("ALTER TABLE library ADD COLUMN playback_updated INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE library ADD COLUMN listed_updated INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE library SET playback_updated=updated,listed_updated=CASE WHEN listed=1 THEN updated ELSE 0 END")
              }
              journal(db)
            }else throw DataFailure(FailureKind.STORAGE,"Desteklenmeyen kayıt sürümü","storage")
        }
    }
    private suspend fun <T> io(block:()->T):T=withContext(Dispatchers.IO){lock.withLock{
        try{block()}catch(e:CancellationException){throw e}catch(e:DataFailure){throw e}
        catch(e:Exception){throw DataFailure(FailureKind.STORAGE,"Kayıt işlemi başarısız; veri korundu","storage",cause=e)}
    }}
    // A damaged row must not hide healthy records or be overwritten/deleted as recovery.
    private fun read(c:Cursor):LibraryRecord?=try {
        val raw=c.getString(c.getColumnIndexOrThrow("card"))
        require(raw.length<=1024*1024)
        val card=Contracts.readCard(JSONObject(raw));require(card.title.isNotBlank())
        LibraryRecord(c.getString(c.getColumnIndexOrThrow("identity")),card,c.getInt(c.getColumnIndexOrThrow("listed"))==1,
            c.getLong(c.getColumnIndexOrThrow("position")).coerceAtLeast(0),c.getLong(c.getColumnIndexOrThrow("duration")).coerceAtLeast(0),
            c.getLong(c.getColumnIndexOrThrow("updated")),c.getLong(c.getColumnIndexOrThrow("playback_updated")),c.getLong(c.getColumnIndexOrThrow("listed_updated")))
    }catch(e:Exception){hasUnreadableRecords=true;null}
    suspend fun records(listedOnly:Boolean=false,limit:Int=200,offset:Int=0,historyOnly:Boolean=false):List<LibraryRecord> = io {
        require(limit in 1..2000 && offset>=0)
        helper.readableDatabase.query("library",null,if(listedOnly)"listed=1"else if(historyOnly)"position>0"else null,null,null,null,(if(historyOnly)"playback_updated"else if(listedOnly)"listed_updated"else"updated")+" DESC, identity ASC").use{c->
            buildList { var skipped=0;while(size<limit&&c.moveToNext()) {
                val record=read(c)?:continue
                if(skipped++>=offset)add(record)
            }}
        }
    }
    suspend fun update(card:Card,listed:Boolean?=null,positionMs:Long?=null,durationMs:Long?=null)=io {
        val db=helper.writableDatabase;var key=ContentIdentity.key(card);var storedCard=card;db.beginTransaction()
        try{
            val exact=db.query("library",null,"identity=?",arrayOf(key),null,null,null).use{
                if(!it.moveToFirst())false else {
                    if(read(it)==null)throw DataFailure(FailureKind.STORAGE,"Bozuk kayıt üzerine yazılmadı; özgün veri korundu","storage")
                    true
                }
            }
            if(!exact) {
                val matches=db.query("library",null,null,null,null,null,"updated DESC","2000").use{c->
                    buildList {while(c.moveToNext()){
                        val previous=read(c)?:continue
                        if(ContentIdentity.equivalent(previous.card,card))add(previous)
                    }}
                }
                if(matches.isNotEmpty()){
                    val old=matches.first();val playback=matches.maxBy{it.playbackUpdatedAt};val listing=matches.maxBy{it.listedUpdatedAt}
                    if(card.imdbId.isBlank()&&old.card.imdbId.isNotBlank())key=old.identity
                    storedCard=card.copy(imdbId=card.imdbId.ifBlank{old.card.imdbId},originalTitle=card.originalTitle.ifBlank{old.card.originalTitle},aliases=(card.aliases+old.card.aliases).distinct())
                    db.update("library",ContentValues().apply{
                        put("identity",key);put("listed",if(listing.listed)1 else 0);put("listed_updated",listing.listedUpdatedAt)
                        put("position",playback.positionMs);put("duration",playback.durationMs);put("playback_updated",playback.playbackUpdatedAt)
                    },"identity=?",arrayOf(old.identity))
                    matches.drop(1).forEach{db.delete("library","identity=?",arrayOf(it.identity))}
                }
            }
            db.insertWithOnConflict("library",null,ContentValues().apply{put("identity",key);put("card",Contracts.card(storedCard).toString());put("updated",System.currentTimeMillis())},SQLiteDatabase.CONFLICT_IGNORE)
            val changed=db.update("library",ContentValues().apply {put("card",Contracts.card(storedCard).toString());put("updated",System.currentTimeMillis())
                listed?.let{put("listed",if(it)1 else 0);put("listed_updated",System.currentTimeMillis())};positionMs?.let{put("position",it.coerceAtLeast(0));put("playback_updated",System.currentTimeMillis())};durationMs?.let{put("duration",it.coerceAtLeast(0))}
            },"identity=?",arrayOf(key))
            check(changed==1)
            // Bound healthy, unlisted history only. Listed and unreadable rows are retained.
            val expired=db.query("library",null,"listed=0",null,null,null,"updated DESC, identity ASC").use{c->
                buildList{var valid=0;while(c.moveToNext()){val r=read(c)?:continue;if(valid++>=200)add(r.identity)}}
            }
            expired.forEach{db.delete("library","identity=?",arrayOf(it))}
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }
    suspend fun rememberSearch(query:String)=io {
        val q=TextKey.clean(query).take(100)
        if(q.isNotBlank()){
            val db=helper.writableDatabase
            db.beginTransaction();try {
                db.insertWithOnConflict("searches",null,ContentValues().apply{put("query",q);put("updated",System.currentTimeMillis())},SQLiteDatabase.CONFLICT_REPLACE)
                val expired=db.rawQuery("SELECT query FROM searches ORDER BY updated DESC, query ASC",null).use{c->
                    buildList{var valid=0;while(c.moveToNext()){
                        val text=c.getString(0)
                        if(text!=null&&text.isNotBlank()&&text.length<=100&&text==TextKey.clean(text)){
                            if(valid++>=20)add(text)
                        }else hasUnreadableRecords=true
                    }}
                }
                expired.forEach{db.delete("searches","query=?",arrayOf(it))}
                db.setTransactionSuccessful()
            }finally{db.endTransaction()}
        }
    }
    suspend fun searches():List<String> = io {helper.readableDatabase.rawQuery("SELECT query FROM searches ORDER BY updated DESC, query ASC",null).use{c->
        buildList{while(size<20&&c.moveToNext()){
            val q=c.getString(0)
            if(q!=null&&q.isNotBlank()&&q.length<=100&&q==TextKey.clean(q))add(q)else hasUnreadableRecords=true
        }}
    }}
    suspend fun beginPlayback(c:Card,mark:JSONObject):PlaybackCheckpoint=io{
        val db=helper.writableDatabase;db.beginTransaction();try{
            val rows=db.query("library",null,null,null,null,null,null).use{r->buildList{while(r.moveToNext()){
                val v=read(r)?:continue;if(v.identity==ContentIdentity.key(c)||ContentIdentity.equivalent(v.card,c))add(v)
            }}}
            val recent=rows.maxByOrNull{it.playbackUpdatedAt};val token=java.util.UUID.randomUUID().toString()
            val baseline=org.json.JSONArray(rows.map{v->JSONObject().put("identity",v.identity).put("position",v.positionMs).put("duration",v.durationMs).put("playbackUpdated",v.playbackUpdatedAt)})
            val payload=JSONObject().put("card",Contracts.card(c)).put("rows",baseline).put("mark",mark)
            db.insertOrThrow("playback_journal",null,ContentValues().apply{put("token",token);put("payload",payload.toString());put("state","open")})
            db.setTransactionSuccessful();PlaybackCheckpoint(token,c,recent?.positionMs?:0,recent?.durationMs?:0)
        }finally{db.endTransaction()}
    }
    suspend fun playbackOpen(token:String)=io{helper.readableDatabase.rawQuery("SELECT state FROM playback_journal WHERE token=?",arrayOf(token)).use{it.moveToFirst()&&it.getString(0)=="open"}}
    suspend fun discardIntent(token:String)=io{check(helper.writableDatabase.update("playback_journal",ContentValues().apply{put("state","discard")},"token=?",arrayOf(token))==1)}
    suspend fun cancelDiscard(token:String)=io{helper.writableDatabase.update("playback_journal",ContentValues().apply{put("state","open")},"token=?",arrayOf(token));Unit}
    suspend fun playbackJournals()=io{helper.readableDatabase.rawQuery("SELECT token,payload,state FROM playback_journal",null).use{r->buildList{while(r.moveToNext())add(Triple(r.getString(0),JSONObject(r.getString(1)),r.getString(2)))}}}
    suspend fun endPlayback(token:String)=io{helper.writableDatabase.delete("playback_journal","token=?",arrayOf(token));Unit}
    suspend fun restorePlayback(token:String,c:Card):JSONObject=io{
        val db=helper.writableDatabase;db.beginTransaction();try{
            val payload=db.rawQuery("SELECT payload FROM playback_journal WHERE token=? AND state='discard'",arrayOf(token)).use{check(it.moveToFirst());JSONObject(it.getString(0))}
            val baseline=payload.getJSONArray("rows").values().filterIsInstance<JSONObject>().maxByOrNull{it.getLong("playbackUpdated")}
            val keys=db.query("library",null,null,null,null,null,null).use{r->buildList{while(r.moveToNext()){
                val v=read(r)?:continue;if(v.identity==ContentIdentity.key(c)||ContentIdentity.equivalent(v.card,c))add(v)
            }}}
            for(v in keys){
                if(baseline==null&&!v.listed)db.delete("library","identity=?",arrayOf(v.identity))
                else db.update("library",ContentValues().apply{put("position",baseline?.getLong("position")?:0);put("duration",baseline?.getLong("duration")?:0);put("playback_updated",baseline?.getLong("playbackUpdated")?:0)},"identity=?",arrayOf(v.identity))
            }
            if(keys.isEmpty()&&baseline!=null)db.insertOrThrow("library",null,ContentValues().apply{
                put("identity",ContentIdentity.key(c));put("card",Contracts.card(c).toString());put("listed",0);put("position",baseline.getLong("position"));put("duration",baseline.getLong("duration"));put("updated",baseline.getLong("playbackUpdated"));put("playback_updated",baseline.getLong("playbackUpdated"));put("listed_updated",0)
            })
            db.setTransactionSuccessful();payload.getJSONObject("mark")
        }finally{db.endTransaction()}
    }
    suspend fun clearSearches()=io{helper.writableDatabase.delete("searches",null,null);Unit}
    /** Only healthy progress rows; unreadable payloads and all listing metadata survive. */
    suspend fun clearPlaybackHistory()=io{
        val db=helper.writableDatabase;db.beginTransaction();try{
            check(db.rawQuery("SELECT COUNT(*) FROM playback_journal",null).use{it.moveToFirst();it.getLong(0)}==0L){"Önce oynatıcıdan çık"}
            val rows=db.query("library",null,null,null,null,null,null).use{c->buildList{while(c.moveToNext())read(c)?.let{add(it)}}}
            for(r in rows)if(r.listed)db.update("library",ContentValues().apply{put("position",0);put("duration",0);put("playback_updated",0)},"identity=?",arrayOf(r.identity))
                else db.delete("library","identity=?",arrayOf(r.identity))
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }
    override fun close(){helper.close()}
}
