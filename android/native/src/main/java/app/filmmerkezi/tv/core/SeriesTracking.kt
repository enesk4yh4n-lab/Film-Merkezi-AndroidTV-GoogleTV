package app.filmmerkezi.tv.core

import android.content.Context
import android.content.ContentValues
import android.database.DatabaseErrorHandler
import android.database.sqlite.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import org.json.*

data class SeriesTracking(val card:Card,val following:Boolean=false,val known:Set<String> = emptySet(),val pending:Set<String> = emptySet(),val checkedAt:Long=0,val marks:Map<String,Boolean> = emptyMap())
fun EpisodeNumber.trackingKey()="$season:$number"

/** Watch marks do not manufacture playback positions. A failed list fetch cannot clear known episodes. */
class SeriesTrackingStore(context:Context,name:String="film-series.db"):AutoCloseable {
    private val lock=Mutex()
    private val helper=object:SQLiteOpenHelper(context.applicationContext,name,null,1,DatabaseErrorHandler{throw SQLiteDatabaseCorruptException("Dizi kaydı bozuk; özgün dosya korundu")}){
        override fun onCreate(db:SQLiteDatabase){
            db.execSQL("CREATE TABLE series(work TEXT PRIMARY KEY,card TEXT NOT NULL,following INTEGER NOT NULL,known TEXT NOT NULL,pending TEXT NOT NULL,checked INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE marks(work TEXT NOT NULL,episode TEXT NOT NULL,watched INTEGER NOT NULL,updated INTEGER NOT NULL,manual INTEGER NOT NULL,PRIMARY KEY(work,episode))")
        }
        override fun onUpgrade(db:SQLiteDatabase,a:Int,b:Int){error("Unsupported series version")}
    }
    private suspend fun <T> io(block:(SQLiteDatabase)->T):T=withContext(Dispatchers.IO){lock.withLock{try{block(helper.writableDatabase)}catch(e:CancellationException){throw e}catch(e:Exception){throw DataFailure(FailureKind.STORAGE,"Dizi kaydı okunamadı; veri korundu","series-storage",cause=e)}}}
    private fun set(raw:String)=JSONArray(raw).values().map{it.toString()}.toSet()
    private fun read(db:SQLiteDatabase,c:Card):SeriesTracking {
        val id=ContentIdentity.key(c)
        val marks=db.rawQuery("SELECT episode,watched FROM marks WHERE work=?",arrayOf(id)).use{r->buildMap{while(r.moveToNext())put(r.getString(0),r.getInt(1)==1)}}
        return db.rawQuery("SELECT following,known,pending,checked FROM series WHERE work=?",arrayOf(id)).use{r->if(r.moveToFirst())SeriesTracking(c,r.getInt(0)==1,set(r.getString(1)),set(r.getString(2)),r.getLong(3),marks)else SeriesTracking(c,marks=marks)}
    }
    private fun write(db:SQLiteDatabase,s:SeriesTracking){db.insertWithOnConflict("series",null,ContentValues().apply{put("work",ContentIdentity.key(s.card));put("card",Contracts.card(s.card).toString());put("following",if(s.following)1 else 0);put("known",JSONArray(s.known.toList()).toString());put("pending",JSONArray(s.pending.toList()).toString());put("checked",s.checkedAt)},SQLiteDatabase.CONFLICT_REPLACE)}
    suspend fun state(c:Card)=io{read(it,c)}
    suspend fun followed()=io{db->db.rawQuery("SELECT card FROM series WHERE following=1 ORDER BY checked ASC LIMIT 100",null).use{r->buildList{while(r.moveToNext())add(read(db,Contracts.readCard(JSONObject(r.getString(0)))))}}}
    suspend fun follow(c:Card,episodes:List<Episode>,enabled:Boolean)=io{db->
        if(enabled&&episodes.isEmpty())throw DataFailure(FailureKind.PARSE,"Takip için önce bölüm listesini yenileyin","following")
        val old=read(db,c);write(db,old.copy(following=enabled,known=if(enabled)episodes.map{it.number.trackingKey()}.toSet()else old.known,pending=emptySet(),checkedAt=if(enabled)System.currentTimeMillis()else old.checkedAt));Unit
    }
    suspend fun observe(c:Card,episodes:List<Episode>,complete:Boolean=true)=io{db->
        val old=read(db,c)
        if(!complete||episodes.isEmpty())return@io old
        val now=episodes.map{it.number.trackingKey()}.toSet()
        val added=if(old.following)now-old.known else emptySet()
        val value=old.copy(known=old.known+now,pending=(old.pending+added).filter{old.marks[it]!=true}.toSet(),checkedAt=System.currentTimeMillis())
        write(db,value);value
    }
    suspend fun mark(c:Card,numbers:List<EpisodeNumber>,watched:Boolean,manual:Boolean=true)=io{db->
        db.beginTransaction();try{
            val applied=numbers.distinct().filter{n->manual||!db.rawQuery("SELECT manual FROM marks WHERE work=? AND episode=?",arrayOf(ContentIdentity.key(c),n.trackingKey())).use{it.moveToFirst()&&it.getInt(0)==1}}
            for(n in applied)db.insertWithOnConflict("marks",null,ContentValues().apply{put("work",ContentIdentity.key(c));put("episode",n.trackingKey());put("watched",if(watched)1 else 0);put("updated",System.currentTimeMillis());put("manual",if(manual)1 else 0)},SQLiteDatabase.CONFLICT_REPLACE)
            val old=read(db,c);write(db,old.copy(pending=if(watched)old.pending-applied.map{it.trackingKey()}.toSet()else old.pending));db.setTransactionSuccessful()
        }finally{db.endTransaction()};Unit
    }
    suspend fun playbackMark(c:Card,n:EpisodeNumber):JSONObject=io{db->
        val state=read(db,c);val out=JSONObject().put("parent",Contracts.card(c)).put("episode",n.trackingKey()).put("pending",n.trackingKey() in state.pending)
        db.rawQuery("SELECT watched,updated,manual FROM marks WHERE work=? AND episode=?",arrayOf(ContentIdentity.key(c),n.trackingKey())).use{if(it.moveToFirst())out.put("row",JSONObject().put("watched",it.getInt(0)).put("updated",it.getLong(1)).put("manual",it.getInt(2)))};out
    }
    suspend fun restorePlaybackMark(snapshot:JSONObject)=io{db->
        if(!snapshot.has("parent"))return@io
        val c=Contracts.readCard(snapshot.getJSONObject("parent"));val episode=snapshot.getString("episode");val id=ContentIdentity.key(c)
        db.beginTransaction();try{
            val manual=db.rawQuery("SELECT manual FROM marks WHERE work=? AND episode=?",arrayOf(id,episode)).use{it.moveToFirst()&&it.getInt(0)==1}
            if(!manual){
                val row=snapshot.optJSONObject("row")
                if(row==null)db.delete("marks","work=? AND episode=?",arrayOf(id,episode))
                else db.insertWithOnConflict("marks",null,ContentValues().apply{put("work",id);put("episode",episode);put("watched",row.getInt("watched"));put("updated",row.getLong("updated"));put("manual",row.getInt("manual"))},SQLiteDatabase.CONFLICT_REPLACE)
                val old=read(db,c);write(db,old.copy(pending=if(snapshot.optBoolean("pending"))old.pending+episode else old.pending-episode))
            };db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }
    suspend fun clearNew(c:Card)=io{db->write(db,read(db,c).copy(pending=emptySet()));Unit}
    suspend fun rekey(old:String,c:Card)=io{db->
        val id=ContentIdentity.key(c);if(old==id)return@io
        db.beginTransaction();try{
            val previous=db.rawQuery("SELECT card FROM series WHERE work=?",arrayOf(old)).use{if(it.moveToFirst())Contracts.readCard(JSONObject(it.getString(0)))else null}
            if(previous!=null){val a=read(db,previous);val b=read(db,c);write(db,b.copy(following=a.following||b.following,known=a.known+b.known,pending=a.pending+b.pending,checkedAt=maxOf(a.checkedAt,b.checkedAt)))}
            db.execSQL("INSERT OR REPLACE INTO marks SELECT ?,m.episode,m.watched,m.updated,m.manual FROM marks m WHERE m.work=? AND NOT EXISTS(SELECT 1 FROM marks n WHERE n.work=? AND n.episode=m.episode AND n.updated>m.updated)",arrayOf(id,old,id))
            db.delete("series","work=?",arrayOf(old));db.delete("marks","work=?",arrayOf(old));db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }
    override fun close(){helper.close()}
}
