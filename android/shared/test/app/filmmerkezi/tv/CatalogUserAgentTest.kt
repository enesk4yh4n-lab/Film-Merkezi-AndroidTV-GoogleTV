package app.filmmerkezi.tv

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class CatalogUserAgentTest {
    @Test fun hostDefaultsDoNotDependOnAnEmbeddedWebsiteList(){
        for(host in listOf("provider.example","player.example","media.example","new-plugin.example"))
            assertEquals("Mozilla/5.0",SiteHttpPolicy.defaultUserAgent(("https://"+host+"/").toHttpUrl()))
    }
}
