package app.filmmerkezi.tv

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class CatalogUserAgentTest {
    @Test fun observed403OriginsUseBrowserAgentWithoutLeakingItToUnrelatedPlayers(){
        for(host in listOf("www.hdfilmcehennemi.nl","www.fullhdfilmizlesene.now","filmmakinesi.to",
            "closeload.filmmakinesi.to","rapid.filmmakinesi.to","rapidvid.org"))
            assertEquals(SiteHttpPolicy.BROWSER_USER_AGENT,SiteHttpPolicy.defaultUserAgent(("https://"+host+"/").toHttpUrl()))
        for(host in listOf("hotstream.club","ok.ru","matrudas.com","srv1.cdnimages1.cyou"))
            assertEquals("Mozilla/5.0",SiteHttpPolicy.defaultUserAgent(("https://"+host+"/").toHttpUrl()))
    }
}
