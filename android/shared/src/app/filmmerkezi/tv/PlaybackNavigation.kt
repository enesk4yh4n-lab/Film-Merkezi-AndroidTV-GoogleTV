package app.filmmerkezi.tv

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

internal data class Chapter(val time:Long,val end:Long?,val title:String,val intro:Boolean)
internal data class VideoPreview(val start:Long,val end:Long,val url:String,val region:android.graphics.Rect?)
internal data class PlaybackNavigation(val chapters:List<Chapter>,val previews:List<VideoPreview>) {
    companion object{
        private const val DAY=86_400_000L
        val empty=PlaybackNavigation(emptyList(),emptyList())

        fun read(source:JSONObject):PlaybackNavigation {
            val nav=source.optJSONObject("navigation")?:return empty
            val origin=source.optString("url").toHttpUrlOrNull()
            val chapters=nav.optJSONArray("chapters")?.let{rows->
                (0 until minOf(rows.length(),200)).mapNotNull{i->
                    val row=rows.optJSONObject(i)?:return@mapNotNull null
                    val start=row.optLong("startMs",-1L).takeIf{it in 0..DAY}?:return@mapNotNull null
                    val end=row.optLong("endMs",-1L).takeIf{it in start+1..DAY}
                    val intro=row.optString("kind")=="intro"&&end!=null
                    val title=row.optString("title").trim().take(100)
                    if(title.isEmpty()&&!intro)null else Chapter(start,end,title,intro)
                }.distinct().sortedBy{it.time}
            }.orEmpty()
            val previews=nav.optJSONArray("previews")?.let{rows->
                (0 until minOf(rows.length(),2000)).mapNotNull{i->
                    val row=rows.optJSONObject(i)?:return@mapNotNull null
                    val start=row.optLong("startMs",-1L).takeIf{it in 0..DAY}?:return@mapNotNull null
                    val end=row.optLong("endMs",-1L).takeIf{it in start+1..DAY}?:return@mapNotNull null
                    val url=row.optString("url").toHttpUrlOrNull()?.takeIf{origin!=null&&it.isHttps&&it.host==origin.host&&it.port==origin.port&&it.username.isEmpty()&&it.password.isEmpty()}
                        ?:return@mapNotNull null
                    val region=row.optJSONObject("region")?.let{r->
                        val x=r.optInt("x",-1);val y=r.optInt("y",-1);val width=r.optInt("width");val height=r.optInt("height")
                        if(x !in 0..4095||y !in 0..4095||width !in 1..(4096-x)||height !in 1..(4096-y))return@mapNotNull null
                        android.graphics.Rect(x,y,x+width,y+height)
                    }
                    VideoPreview(start,end,url.toString(),region)
                }.sortedBy{it.start}
            }.orEmpty()
            return PlaybackNavigation(chapters,previews)
        }
    }
}
