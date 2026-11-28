package app.filmmerkezi.tv.core

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A bounded, disposable cache of first-page cards. No search query, cursor or playback data is stored. */
class CatalogSnapshots(private val directory:File,private val now:()->Long={System.currentTimeMillis()}) {
    @Volatile internal var generation=0L
        private set
    private val lock=Any()

    private fun file(key:String)=AtomicFile(File(directory,"$key.json"))
    private fun files()=directory.listFiles{_,name->name.endsWith(".json")}.orEmpty()

    internal suspend fun read(key:String,sites:Set<String>):List<UnifiedItem> = withContext(Dispatchers.IO){
        synchronized(lock){
            runCatching {
                val file=file(key)
                val raw=file.openRead().use{input->
                    require(file.baseFile.length() in 1..MAX_BYTES.toLong())
                    input.readBytes()
                }
                require(raw.size in 1..MAX_BYTES)
                val doc=JSONObject(raw.toString(Charsets.UTF_8))
                require(doc.getInt("version")==1&&now()-doc.getLong("savedAt") in 0..MAX_AGE)
                val items=doc.getJSONArray("items")
                require(items.length()<=MAX_ITEMS)
                (0 until items.length()).map{index->
                    val item=items.getJSONObject(index)
                    fun card(o:JSONObject):Card {
                        require(o.getJSONObject("ref").getString("siteId") in sites)
                        require(o.optJSONObject("seriesRef")?.getString("siteId")?.let{it in sites}!=false)
                        return Contracts.readCard(o)
                    }
                    val variants=item.getJSONArray("alternatives")
                    require(variants.length() in 1..MAX_ALTERNATIVES)
                    UnifiedItem(item.getString("identity"),card(item.getJSONObject("card")),(0 until variants.length()).map{card(variants.getJSONObject(it))})
                }
            }.getOrDefault(emptyList())
        }
    }

    internal suspend fun write(key:String,items:List<UnifiedItem>,expectedGeneration:Long)=withContext(Dispatchers.IO){
        synchronized(lock){
            if(generation!=expectedGeneration)return@synchronized
            val file=file(key)
            if(items.isEmpty()){file.delete();return@synchronized}
            val rows=JSONArray()
            var remaining=MAX_BYTES-128
            for(item in items.take(MAX_ITEMS)){
                val alternatives=(listOf(item.card)+item.alternatives).distinctBy{it.ref.id}.take(MAX_ALTERNATIVES)
                val row=JSONObject().put("identity",item.identity).put("card",Contracts.card(item.card))
                    .put("alternatives",JSONArray(alternatives.map(Contracts::card)))
                remaining-=row.toString().toByteArray(Charsets.UTF_8).size+1
                if(remaining<0)break
                rows.put(row)
            }
            if(rows.length()==0)return@synchronized
            val raw=JSONObject().put("version",1).put("savedAt",now()).put("items",rows).toString().toByteArray(Charsets.UTF_8)
            runCatching {
                if(!directory.isDirectory&&!directory.mkdirs())throw IOException("Catalog cache directory unavailable")
                val stream=file.startWrite()
                try{stream.write(raw);file.finishWrite(stream)}catch(e:IOException){file.failWrite(stream);throw e}
                files().sortedByDescending(File::lastModified).drop(MAX_FILES).forEach{AtomicFile(it).delete()}
            }
        }
        Unit
    }

    internal suspend fun bytes():Long=withContext(Dispatchers.IO){synchronized(lock){files().sumOf(File::length)}}
    internal suspend fun clear()=withContext(Dispatchers.IO){synchronized(lock){
        generation++
        files().forEach{AtomicFile(it).delete()}
    }}

    companion object {
        private const val MAX_BYTES=384*1024
        private const val MAX_FILES=8
        private const val MAX_ITEMS=120
        private const val MAX_ALTERNATIVES=16
        private const val MAX_AGE=24*60*60*1000L
    }
}
