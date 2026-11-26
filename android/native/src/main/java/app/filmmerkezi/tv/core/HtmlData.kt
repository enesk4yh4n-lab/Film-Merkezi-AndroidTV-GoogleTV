package app.filmmerkezi.tv.core

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import okhttp3.HttpUrl.Companion.toHttpUrl

object HtmlData {
    fun verify(body:String) {
        if(body.length>4*1024*1024)throw DataFailure(FailureKind.LIMIT,"HTML boyut sınırı","parse")
        if(Regex("CLOUDFLARE_ERROR_1000S_BOX|VPN veya Server ip|<title>\\s*(Just a moment|Attention Required)",RegexOption.IGNORE_CASE).containsMatchIn(body))
            throw DataFailure(FailureKind.ACCESS,"Site erişimi engelliyor","catalog")
    }
    fun document(body:String,url:String):Document {
        verify(body)
        var html=body
        if(body.trimStart().startsWith("{")) {
            val data=runCatching { JSONObject(body) }.getOrNull()
            if(data!=null) {
                if(data.has("html")) html=data.string("html")
                else if(data.has("ajaxpagi_filmler"))html=data.string("ajaxpagi_filmler")
                else if(data.optJSONArray("results")?.opt(0) is String) html=data.getJSONArray("results").values().joinToString("\n")
            }
        }
        return Jsoup.parse(html,url)
    }
    fun text(n:Element?,selector:String?=null):String = TextKey.clean((if(selector==null)n else n?.selectFirst(selector))?.text()?:"")
    fun image(n:Element?,base:String):String {
        val img=if(n?.normalName()=="img")n else n?.selectFirst("img")
        val value=listOf("data-src","data-litespeed-src","data-catalog-src","data-original","src").firstNotNullOfOrNull { img?.attr(it)?.takeIf { v->v.isNotBlank() && !v.startsWith("data:") } } ?:return ""
        return publicUrl(value,base)?:""
    }
    fun schemas(d:Document):List<JSONObject> = d.select("script[type=application/ld+json]").flatMap { tag ->
        val data=runCatching { JSONTokener(tag.data()).nextValue() }.getOrNull()
        when(data){is JSONObject->data.optJSONArray("@graph")?.values()?.filterIsInstance<JSONObject>()?:listOf(data);is JSONArray->data.values().filterIsInstance<JSONObject>();else->emptyList()}
    }
    fun flight(body:String):JSONArray {
        val values=JSONArray();val wire=StringBuilder()
        Regex("self\\.__next_f\\.push\\((\\[[\\s\\S]*?\\])\\)\\s*;?\\s*</script>").findAll(body).forEach {
            val chunk=runCatching{JSONArray(it.groupValues[1])}.getOrNull()
            if(chunk?.optInt(0)==1 && chunk.opt(1) is String)wire.append(chunk.optString(1))
        }
        if(wire.length>3*1024*1024)throw DataFailure(FailureKind.LIMIT,"Flight veri sınırı","parse")
        // Text frames may contain newlines; skip their declared UTF-8 byte length.
        var i=0;val s=wire.toString()
        while(i<s.length) {
            if(s[i]=='\n'){i++;continue}
            val head=Regex("^[0-9a-f]+:").find(s.substring(i,(i+24).coerceAtMost(s.length)))
            if(head==null){i=s.indexOf('\n',i).let{if(it<0)s.length else it+1};continue}
            i+=head.value.length
            val text=Regex("^T([0-9a-f]+),").find(s.substring(i,(i+24).coerceAtMost(s.length)))
            if(text!=null) {
                val size=text.groupValues[1].toInt(16);i+=text.value.length;var used=0
                while(i<s.length && used<size){val cp=Character.codePointAt(s,i);used+=when {cp<128->1;cp<2048->2;cp<65536->3;else->4};i+=Character.charCount(cp)}
                if(used!=size)throw DataFailure(FailureKind.PARSE,"Flight metin çerçevesi bozuk","parse")
                continue
            }
            val end=s.indexOf('\n',i).let{if(it<0)s.length else it}
            runCatching { JSONTokener(s.substring(i,end)).nextValue() }.getOrNull()?.let { if(it is JSONArray || it is JSONObject)values.put(it) }
            i=end+1
        }
        return values
    }
}

/** Site-specific selectors and typed output; no DOM handles cross a language boundary. */
