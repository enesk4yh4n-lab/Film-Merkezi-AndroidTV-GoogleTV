package app.filmmerkezi.tv.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DiziboxIdentity103Test {
    private fun fixture()=JSONObject(checkNotNull(javaClass.getResource("/identity103/dizibox-pages.json")).readText())
    private fun checked(page:JSONObject):Card {
        val seed=Contracts.readCard(page.getJSONObject("seed"));val body=page.getString("html")
        return PageIdentity.attach(body,CatalogParser(Sites.get("dizibox")).detail(body,seed),seed).card
    }
    @Test fun recordedSeriesUsePrimaryReleaseYearInsteadOfWebPagePublicationDate(){
        val pages=fixture().getJSONArray("pages");assertEquals(5,pages.length())
        for(i in 0 until pages.length()){
            val p=pages.getJSONObject(i);val c=checked(p)
            assertEquals(c.title,p.getInt("expectedYear"),c.year)
            assertEquals(ContentKind.SERIES,ContentIdentity.facts(c).kind)
            val evidence=checkNotNull(c.identityEvidence)
            assertTrue(evidence.yearVerified)
            assertTrue(evidence.typeVerified)
        }
    }
    @Test fun recordedVisionSearchMergesAllFourMatchingDiziboxShows(){
        val f=fixture();val raw=f.getJSONArray("groupsBefore").values().filterIsInstance<JSONObject>()
            .flatMap{it.getJSONArray("alternatives").values().filterIsInstance<JSONObject>().map(Contracts::readCard)}
        assertEquals(38,ContentIdentity.merge(raw).size)
        val refreshed=f.getJSONArray("pages").values().filterIsInstance<JSONObject>().map(::checked).associateBy{it.ref.pageUrl}
        val result=ContentIdentity.merge(raw.map{refreshed[it.ref.pageUrl]?:it})
        assertEquals(34,result.size)
        for((title,count) in listOf("VisionQuest" to 4,"WandaVision" to 4,"Star Wars: Visions" to 3,"Star Wars: Visions Presents – The Ninth Jedi" to 3)){
            val g=result.single{it.alternatives.any{c->c.ref.siteId=="dizibox"&&c.title==title}}
            assertEquals(title,count,g.alternatives.size)
        }
        // The parent anthology and its spin-off remain separate works.
        assertFalse(result.any{g->g.alternatives.any{it.imdbId=="tt13622982"}&&g.alternatives.any{it.imdbId=="tt43337681"}})
        assertEquals(1,result.single{it.alternatives.any{c->c.ref.pageUrl.endsWith("/division-palermo/")}}.alternatives.size)
    }
    @Test fun unrelatedYearsAndInvalidYearLinksCannotBecomeEvidence(){
        val c=Card(ContentRef("dizibox",Sites.get("dizibox").base+"/diziler/example/",ContentKind.SERIES),"Example")
        fun check(inner:String):Card {
            val html="""<script type="application/ld+json">{"@type":"WebPage","url":"${c.ref.pageUrl}","datePublished":"1998-02-03"}</script><div class="tv-overview"><div class="title-terms"><h1>Example</h1><div class="terms">$inner</div></div></div><div id="seasons-list"></div><aside><div class="tv-overview"><div class="title-terms"><div class="terms"><a href="/yil/1980/">1980</a></div></div></div></aside>"""
            return PageIdentity.attach(html,CatalogParser(Sites.get("dizibox")).detail(html,c),c).card
        }
        assertNull(check("").year)
        assertNull(check("<a href='https://unrelated.invalid/yil/2026/'>2026</a>").year)
        assertNull(check("<a href='/yil/2026/'>1998</a>").year)
        assertNull(check("<a href='/yil/2026/'>2026</a><a href='/yil/2021/'>2021</a>").year)
        assertEquals(2026,check("<a href='/yil/2026/'>2026</a>").year)
    }
    @Test fun releaseYearDoesNotBypassRemakeOrImdbConflictGuards(){
        val c=checked(fixture().getJSONArray("pages").values().filterIsInstance<JSONObject>().first{it.getJSONObject("seed").getString("title")=="VisionQuest"})
        val other=Card(ContentRef("hdfilmcehennemi",Sites.get("hdfilmcehennemi").base+"/dizi/example/",ContentKind.SERIES),c.title,year=1985)
        assertEquals(IdentityState.DIFFERENT,ContentIdentity.decide(c,other).state)
        assertEquals(IdentityState.DIFFERENT,ContentIdentity.decide(c.copy(imdbId="tt1111111"),other.copy(year=2026,imdbId="tt2222222")).state)
    }
}
