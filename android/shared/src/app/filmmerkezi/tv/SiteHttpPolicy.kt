package app.filmmerkezi.tv

import okhttp3.HttpUrl

/** Browser header verified on the listed catalog/embed origins. Explicit source headers win. */
object SiteHttpPolicy {
    const val BROWSER_USER_AGENT = "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"
    fun defaultUserAgent(url: HttpUrl): String = if (url.scheme == "https" && url.host in setOf(
        "www.dizibox.live", "dbx.molystream.org", "film.popcornvakti.net",
        "www.hdfilmcehennemi.nl", "www.fullhdfilmizlesene.now", "filmmakinesi.to",
        "closeload.filmmakinesi.to", "rapid.filmmakinesi.to", "rapidvid.org"
    )) BROWSER_USER_AGENT else "Mozilla/5.0"
}
