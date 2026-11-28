package app.filmmerkezi.tv

import okhttp3.HttpUrl

/** Neutral host default. Provider-specific headers belong to the installed plugin. */
object SiteHttpPolicy {
    const val BROWSER_USER_AGENT = "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"
    @Suppress("UNUSED_PARAMETER")
    fun defaultUserAgent(url: HttpUrl): String = "Mozilla/5.0"
    /** Compatibility for previously issued provider IDs; no provider names or addresses. */
    fun forProvider(id:String):String = if(id in setOf("dizibox","hdfilmcehennemi","fullhdfilmizlesene","filmmakinesi")) BROWSER_USER_AGENT else "Mozilla/5.0"
}
