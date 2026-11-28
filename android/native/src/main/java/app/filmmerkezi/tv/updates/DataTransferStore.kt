package app.filmmerkezi.tv.updates

import android.content.*
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.AtomicFile
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.settings.PlaybackOverridesStore
import kotlinx.coroutines.*
import org.json.*
import java.io.File
import java.util.Base64

/** Logical, bounded export; imported files never supply executable SQL or publisher trust. */
class DataTransferStore(private val context:Context,private val namespace:String="") {
    init{require(namespace.matches(Regex("[a-z0-9-]*")))}
    companion object {
        val DATABASES=listOf("film-library.db","film-works.db","film-series.db","film-playback-preferences.db")
        val PREFERENCES=listOf("film-settings","player-preferences","catalog-preferences","interface-preferences","recommendations")
        const val LIMIT=32*1024*1024
    }
    private val directory=File(context.filesDir,namespace+"transfer").apply{mkdirs()}
    private val journal=AtomicFile(File(directory,"pending.json"))
    private fun database(name:String)=context.getDatabasePath(namespace+name)
    private fun prefs(name:String)=context.getSharedPreferences(namespace+name,0)
    private fun writeJournal(o:JSONObject){val out=journal.startWrite();try{out.write(o.toString().toByteArray());journal.finishWrite(out)}catch(e:Throwable){journal.failWrite(out);throw e}}
    private fun typed(value:Any?):JSONObject=JSONObject().apply{when(value){null->put("t",0);is ByteArray->put("t",4).put("v",Base64.getEncoder().encodeToString(value));is Set<*>->put("t",5).put("v",JSONArray(value.toList()));is Int->put("t",6).put("v",value);is Long->put("t",1).put("v",value);is Float->put("t",7).put("v",value.toDouble());is Double->put("t",2).put("v",value);is Boolean->put("t",8).put("v",value);is String->put("t",3).put("v",value);else->error("Kayıt türü desteklenmiyor")}}
    private fun preferenceSnapshot()=JSONObject().apply{for(name in PREFERENCES)put(name,JSONObject().apply{prefs(name).all.forEach{(key,value)->put(key,typed(value))}})}
    private fun restorePreferences(values:JSONObject){for(name in PREFERENCES){val fields=values.optJSONObject(name)?:JSONObject();val e=prefs(name).edit().clear()
        val keepAdultFilter=name=="film-settings"&&prefs("adult-pin").contains("digest")&&prefs(name).all["hideAdult"]!=false
        for(k in fields.keys()){require(k.length<=200);val v=fields.getJSONObject(k);when(v.getInt("t")){1->e.putLong(k,v.getLong("v"));2,7->e.putFloat(k,v.getDouble("v").toFloat());3->e.putString(k,v.getString("v"));5->{val a=v.getJSONArray("v");e.putStringSet(k,(0 until a.length()).map{a.getString(it)}.toSet())};6->e.putInt(k,v.getInt("v"));8->e.putBoolean(k,v.getBoolean("v"));else->error("Tercih türü geçersiz")}}
        if(keepAdultFilter)e.putBoolean("hideAdult",true)
        check(e.commit()){ "Aktarılan ayarlar kaydedilemedi" }
    }}
    suspend fun export():File=withContext(Dispatchers.IO){
        val payload=JSONObject().put("preferences",preferenceSnapshot()).put("knownSites",JSONArray(Sites.known.map(PluginWire::site)));val databases=JSONObject();var cells=0
        for(name in DATABASES){val file=database(name);if(!file.exists())continue
            SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE).use{db->db.beginTransaction();try{
                val tables=JSONObject();db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name!='android_metadata'",null).use{names->while(names.moveToNext()){
                    val table=names.getString(0);require(table.matches(Regex("[a-z_]+")));val rows=JSONArray()
                    db.rawQuery("SELECT * FROM \"$table\"",null).use{r->while(r.moveToNext()){val row=JSONObject();for(i in 0 until r.columnCount){val v:Any?=when(r.getType(i)){Cursor.FIELD_TYPE_NULL->null;Cursor.FIELD_TYPE_INTEGER->r.getLong(i);Cursor.FIELD_TYPE_FLOAT->r.getDouble(i);Cursor.FIELD_TYPE_BLOB->r.getBlob(i);else->r.getString(i)};row.put(r.getColumnName(i),typed(v));require(++cells<=500000){"Aktarım kayıt sınırı"}};rows.put(row)}};tables.put(table,rows)
                }};databases.put(name,JSONObject().put("version",db.version).put("tables",tables));db.setTransactionSuccessful()
            }finally{db.endTransaction()}}
        }
        val raw=payload.put("databases",databases).toString().toByteArray();require(raw.size<=LIMIT*3/4){"Aktarım dosyası boyut sınırını aştı; kayıtlar değiştirilmedi"}
        val envelope=JSONObject().put("format","film-merkezi-data").put("version",1).put("sha256",UpdateProtocol.sha(raw)).put("payload",Base64.getEncoder().encodeToString(raw))
        File(context.getExternalFilesDir(null)?:directory,namespace+"film-merkezi-transfer.fmdata").also{it.writeText(envelope.toString())}
    }
    private suspend fun createSchema(original:String,stageName:String){when(original){
        "film-library.db"->LibraryStore(context,stageName).use{it.records()}
        "film-works.db"->WorkStore(context,stageName).use{it.observe(emptyList())}
        "film-series.db"->SeriesTrackingStore(context,stageName).use{it.followed()}
        "film-playback-preferences.db"->PlaybackOverridesStore(context,stageName).use{it.get(setOf("transfer-schema"))}
    }}
    suspend fun prepare(input:java.io.InputStream):JSONObject=withContext(Dispatchers.IO){
        val bytes=input.use{stream->val out=java.io.ByteArrayOutputStream();val b=ByteArray(16384);while(true){val n=stream.read(b);if(n<0)break;require(out.size()+n<=LIMIT){"Aktarım dosyası çok büyük"};out.write(b,0,n)};out.toByteArray()}
        val envelope=JSONObject(bytes.toString(Charsets.UTF_8));require(envelope.getString("format")=="film-merkezi-data"&&envelope.getInt("version")==1)
        val raw=Base64.getDecoder().decode(envelope.getString("payload"));require(UpdateProtocol.sha(raw)==envelope.getString("sha256")){"Yedek özeti eşleşmiyor"}
        val payload=JSONObject(raw.toString(Charsets.UTF_8));val dbs=payload.getJSONObject("databases");require(dbs.keys().asSequence().all{it in DATABASES})
        val known=payload.optJSONArray("knownSites")?:JSONArray();require(known.length()<=512);for(i in 0 until known.length()){val spec=PluginWire.site(known.getJSONObject(i));UpdateProtocol.id(spec.id);UpdateTransport.https(spec.base)}
        require(payload.getJSONObject("preferences").keys().asSequence().all{it in PREFERENCES})
        val id=java.util.UUID.randomUUID().toString();val files=JSONObject();var rows=0
        for(name in DATABASES){val stageName=namespace+"transfer-$id-$name";createSchema(name,stageName);val stage=context.getDatabasePath(stageName)
            SQLiteDatabase.openDatabase(stage.path,null,SQLiteDatabase.OPEN_READWRITE).use{db->
                val tables=mutableMapOf<String,Set<String>>();db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name!='android_metadata'",null).use{r->while(r.moveToNext()){val t=r.getString(0);val cols=mutableSetOf<String>();db.rawQuery("PRAGMA table_info(\"$t\")",null).use{c->while(c.moveToNext())cols.add(c.getString(1))};tables[t]=cols}}
                val source=dbs.optJSONObject(name);if(source!=null)require(source.getInt("version")==db.version){"Yedek veritabanı sürümü desteklenmiyor: $name"}
                val values=source?.getJSONObject("tables")?:JSONObject();require(values.keys().asSequence().all{it in tables}){"Yedekte bilinmeyen tablo"}
                db.beginTransaction();try{for((table,columns)in tables){db.delete(table,null,null);val entries=values.optJSONArray(table)?:JSONArray();for(i in 0 until entries.length()){
                    val row=entries.getJSONObject(i);require(row.keys().asSequence().toSet()==columns){"Yedek sütunları eşleşmiyor"};val content=ContentValues()
                    for(column in columns){val v=row.getJSONObject(column);when(v.getInt("t")){0->content.putNull(column);1,6->content.put(column,v.getLong("v"));2,7->content.put(column,v.getDouble("v"));3->content.put(column,v.getString("v"));4->content.put(column,Base64.getDecoder().decode(v.getString("v")));else->error("Kayıt türü geçersiz")}}
                    db.insertOrThrow(table,null,content);rows++
                }};db.setTransactionSuccessful()}finally{db.endTransaction()}
                db.rawQuery("PRAGMA quick_check",null).use{r->require(r.moveToFirst()&&r.getString(0)=="ok")}
            }
            files.put(name,JSONObject().put("stage",stageName).put("hash",UpdateProtocol.sha(stage.readBytes())).put("existed",database(name).exists()).put("installed",false))
        }
        JSONObject().put("id",id).put("phase","prepared").put("files",files).put("preferences",payload.getJSONObject("preferences")).put("beforePreferences",preferenceSnapshot()).put("knownSites",payload.optJSONArray("knownSites")?:JSONArray()).put("rows",rows)
    }
    fun schedule(prepared:JSONObject){require(prepared.getString("phase")=="prepared");writeJournal(prepared)}
    /** Called on the next process startup, before opening any application database. */
    fun applyPending():String? {
        if(!journal.baseFile.exists())return null
        val job=JSONObject(journal.openRead().use{it.readBytes().toString(Charsets.UTF_8)});if(job.optString("phase") in listOf("done","failed"))return null
        val id=job.getString("id");require(id.matches(Regex("[a-f0-9-]{36}")))
        try{
            job.put("phase","applying");writeJournal(job)
            for(name in DATABASES){val info=job.getJSONObject("files").getJSONObject(name);val stageName=info.getString("stage");require(stageName==namespace+"transfer-$id-$name")
                val target=database(name);val stage=context.getDatabasePath(stageName)
                if(!info.optBoolean("installed")){
                    for(suffix in listOf("","-wal","-shm")){val old=File(target.path+suffix);val backup=File(directory,"$id-$name$suffix.old");if(old.exists()&&!backup.exists()&&stage.exists())check(old.renameTo(backup)){"Eski kayıt korumaya alınamadı"}}
                    if(stage.exists())check(stage.renameTo(target)){"Aktarılan kayıt etkinleştirilemedi"}
                    require(UpdateProtocol.sha(target.readBytes())==info.getString("hash")){"Aktarılan veritabanı özeti eşleşmiyor"};info.put("installed",true);writeJournal(job)
                }
            }
            restorePreferences(job.getJSONObject("preferences"))
            // Import metadata for detached records, never a publisher key or active executable package.
            runBlocking(Dispatchers.IO){DistributionStore(context,namespace+"distribution").commit{it.put("knownSites",job.getJSONArray("knownSites"))}}
            job.put("phase","done");writeJournal(job);return "Kayıtlar başarıyla aktarıldı"
        }catch(e:Exception){
            for(name in DATABASES){val info=job.getJSONObject("files").getJSONObject(name);val target=database(name)
                for(suffix in listOf("","-wal","-shm")){val old=File(directory,"$id-$name$suffix.old");val file=File(target.path+suffix);if(old.exists()){if(file.exists())check(file.delete());check(old.renameTo(file))}else if(!info.optBoolean("existed")&&info.optBoolean("installed")&&file.exists())check(file.delete())}
            }
            restorePreferences(job.getJSONObject("beforePreferences"));job.put("phase","failed").put("error",e.message);writeJournal(job);return "Aktarım tamamlanamadı; önceki kayıtlar geri alındı: ${e.message}"
        }
    }
}
