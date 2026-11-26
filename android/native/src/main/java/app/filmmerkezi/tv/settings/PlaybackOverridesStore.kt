package app.filmmerkezi.tv.settings

import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.content.ContentValues
import app.filmmerkezi.tv.*
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class PlaybackOverrides(val audio:String?=null,val audioRole:Int?=null,val text:String?=null,val textRole:Int?=null,
    val subtitles:SubtitlePolicy?=null,val height:Int?=null,val sizing:VideoSizing?=null) {
    fun apply(base:PlaybackSettings)=base.copy(audio=audio?:base.audio,sourceAudio=if(audio!=null)false else base.sourceAudio,
        text=text?:base.text,subtitles=subtitles?:base.subtitles,preferredHeight=height?:base.preferredHeight,sizing=sizing?:base.sizing,
        audioRole=audioRole,textRole=textRole)
}

/** Semantic preferences only. Empty newer rows are reset tombstones across identity redirects. */
class PlaybackOverridesStore(context:Context,name:String="film-playback-preferences.db"):AutoCloseable {
    private val lock=Mutex()
    private val helper=object:SQLiteOpenHelper(context.applicationContext,name,null,1,DatabaseErrorHandler{throw SQLiteDatabaseCorruptException("Tercih verisi korunuyor")}){
        override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE overrides(work TEXT PRIMARY KEY,payload TEXT NOT NULL,updated INTEGER NOT NULL)")}
        override fun onUpgrade(db:SQLiteDatabase,a:Int,b:Int){throw DataFailure(FailureKind.STORAGE,"Desteklenmeyen tercih sürümü","playback-settings")}
    }
    private fun json(v:PlaybackOverrides)=JSONObject().apply{
        put("audio",v.audio?:JSONObject.NULL);put("audioRole",v.audioRole?:JSONObject.NULL);put("text",v.text?:JSONObject.NULL);put("textRole",v.textRole?:JSONObject.NULL)
        put("subtitles",v.subtitles?.name?:JSONObject.NULL);put("height",v.height?:JSONObject.NULL);put("sizing",v.sizing?.name?:JSONObject.NULL)
    }
    private fun read(v:JSONObject):PlaybackOverrides {
        fun lang(k:String)=v.optString(k).takeIf{it.matches(Regex("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*"))}
        return PlaybackOverrides(lang("audio"),v.optInt("audioRole").takeIf{v.has("audioRole")&&!v.isNull("audioRole")},lang("text"),v.optInt("textRole").takeIf{v.has("textRole")&&!v.isNull("textRole")},
            runCatching{SubtitlePolicy.valueOf(v.getString("subtitles"))}.getOrNull(),v.optInt("height").takeIf{v.opt("height")is Number&&it in 0..8640},runCatching{VideoSizing.valueOf(v.getString("sizing"))}.getOrNull())
    }
    private fun current(db:SQLiteDatabase,keys:Set<String>):PlaybackOverrides {
        var stamp=Long.MIN_VALUE;var result=PlaybackOverrides()
        for(part in keys.toList().chunked(400))db.rawQuery("SELECT payload,updated FROM overrides WHERE work IN ("+part.joinToString(","){"?"}+") ORDER BY updated DESC",part.toTypedArray()).use{r->
            while(r.moveToNext())if(r.getLong(1)>stamp){result=read(JSONObject(r.getString(0)));stamp=r.getLong(1)}
        }
        return result
    }
    private suspend fun <T> io(block:(SQLiteDatabase)->T)=withContext(Dispatchers.IO){lock.withLock{try{block(helper.writableDatabase)}catch(e:CancellationException){throw e}catch(e:DataFailure){throw e}catch(e:Exception){throw DataFailure(FailureKind.STORAGE,"İçeriğe özel tercih kaydedilemedi; veri korundu","playback-settings",cause=e)}}}
    suspend fun get(keys:Set<String>)=io{current(it,keys)}
    suspend fun update(work:String,keys:Set<String>,change:(PlaybackOverrides)->PlaybackOverrides)=io{db->
        require(work.isNotBlank()&&work.length<=512);db.beginTransaction();try{
            val v=change(current(db,keys+work));val stamp=db.rawQuery("SELECT MAX(updated) FROM overrides",null).use{if(it.moveToFirst())maxOf(System.currentTimeMillis(),it.getLong(0)+1)else System.currentTimeMillis()}
            check(db.insertWithOnConflict("overrides",null,ContentValues().apply{put("work",work);put("payload",json(v).toString());put("updated",stamp)},SQLiteDatabase.CONFLICT_REPLACE)!=-1L)
            db.setTransactionSuccessful();v
        }finally{db.endTransaction()}
    }
    suspend fun clear()=io{it.delete("overrides",null,null);Unit}
    override fun close(){helper.close()}
}
