package app.filmmerkezi.tv.core

import okhttp3.HttpUrl.Companion.toHttpUrl

object SeriesIdentity {
    fun season(title:String):Int? {
        val text=TextKey.fold(title)
        return (Regex("(?:^| )([1-9]\\d{0,2})\\s*(?:sezon|season)(?: |$)").find(text)?.groupValues?.get(1)
            ?:Regex("(?:^| )(?:sezon|season)\\s*([1-9]\\d{0,2})(?: |$)").find(text)?.groupValues?.get(1))?.toIntOrNull()
    }
    fun archive(ref:ContentRef):Boolean {
        val path=ref.pageUrl.toHttpUrl().encodedPath
        return when(ref.siteId){
            "dizibox","dizipal"->Regex("^/diziler/[^/]+/?$").matches(path)
            "ddizi"->Regex("^/diziler/\\d+/[^/]+(?:/sayfa-\\d+)?/?$").matches(path)
            "fixfullfilmizle"->Regex("^/diziler/[^/]+/?$").matches(path)
            "hdfilmcehennemi","hdfilmdelisi","filmizlebabam","filmmakinesi","diziboxum"->Regex("^/dizi/[^/]+/?$").matches(path)
            "lovefilmizle"->Regex("^/tv-dizisi/[^/]+/?$").matches(path)
            else->false
        }
    }
    fun sourceKey(c:Card):String {
        val base=c.ref.siteId+":"+c.ref.pageUrl.substringBefore('#').trimEnd('/')
        val n=c.episodeNumber
        return if(c.ref.kind==ContentKind.EPISODE&&n!=null)"$base|episode:${n.season}:${n.number}"else base
    }
}
