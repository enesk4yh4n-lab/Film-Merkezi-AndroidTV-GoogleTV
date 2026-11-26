package app.filmmerkezi.tv.core

import org.junit.Test
import org.junit.Assert.*

class PageIdentityTest {
    private val c=Card(ContentRef("sinemacc","https://sinemacc.com/film/example/"),"Example",year=2024)
    @Test fun recommendationImdbCannotBecomePrimaryIdentity(){
        val html="<h1 class='baslik'>Example</h1><script type='application/ld+json'>{\"@type\":\"Movie\",\"url\":\"${c.ref.pageUrl}\",\"name\":\"Example\",\"dateCreated\":\"2024\"}</script><aside><a href='https://www.imdb.com/title/tt9999999/'>Suggested film</a></aside>"
        val card=PageIdentity.attach(html,Detail(c.copy(imdbId="tt9999999")),c).card
        assertEquals("",ContentIdentity.facts(card).imdb);assertTrue(card.identityEvidence!!.yearVerified)
    }
    @Test fun conflictingPrimaryImdbFactsStayUncertain(){
        val html="<h1 class='baslik'>Example</h1><script type='application/ld+json'>{\"@type\":\"Movie\",\"url\":\"${c.ref.pageUrl}\",\"name\":\"Example\",\"sameAs\":[\"https://imdb.com/title/tt1111111/\",\"https://imdb.com/title/tt2222222/\"]}</script>"
        val card=PageIdentity.attach(html,Detail(c),c).card
        assertTrue(card.identityEvidence!!.imdbConflict);assertEquals(IdentityState.UNCERTAIN,ContentIdentity.decide(card,c.copy(imdbId="tt1111111")).state)
    }
    @Test fun pageCreationDateCannotOverwriteReleaseYear(){
        val old=c.copy(ref=ContentRef("filmizlehell","https://filmizlehell.net/film/example"),year=1999)
        val html="""<script type='application/ld+json'>{"@type":"Movie","url":"${old.ref.pageUrl}","dateCreated":"2026-05-03","datePublished":"1999-03-31"}</script>"""
        val result=PageIdentity.attach(html,Detail(old),old).card
        assertEquals(1999,result.year);assertEquals("primary-release-year",result.identityEvidence!!.reason)
        val generic=PageIdentity.attach("""<script type='application/ld+json'>{"@type":"Movie","url":"${c.ref.pageUrl}","datePublished":"2026-05-03"}</script>""",Detail(c),c).card
        assertEquals(2024,generic.year)
    }
    @Test fun schemaArraysAndPrimaryImdbSelectors(){
        val html="""<script type='application/ld+json'>{"@type":["Thing","Movie"],"url":"${c.ref.pageUrl}","alternateName":["Original","Another"],"releaseDate":"2024-01-01"}</script><a class='imdb_link' href='//imdb.com/title/tt1111111/'>IMDb</a><aside><a class='imdb_link' href='https://imdb.com/title/tt2222222/'>Suggestion</a></aside>"""
        val result=PageIdentity.attach(html,Detail(c),c).card
        assertEquals("tt1111111",result.imdbId);assertTrue(result.aliases.contains("Another"));assertTrue(result.identityEvidence!!.typeVerified)
    }
    @Test fun primaryYearCategoryDoesNotReadRecommendationYears(){
        val card=c.copy(ref=ContentRef("filmcibaba","https://filmizle.ink/example/"),year=null)
        val html="""<div class='meta-item'><a rel='category tag'>1999 Filmleri</a></div><aside><div class='meta-item'><a rel='category tag'>2026 Filmleri</a></div></aside>"""
        assertEquals(1999,PageIdentity.attach(html,Detail(card),card).card.year)
    }
    @Test fun refreshingADetailCannotTurnAnInferredYearIntoIndependentEvidence(){
        val raw=c.copy(title="Example 1 (The Example)",year=1999,originalTitle="The Example")
        val seed=raw.copy(identityEvidence=IdentityEvidence(raw.ref.pageUrl,ContentKind.MOVIE,1999,originalTitle="The Example",typeVerified=true,detailChecked=true,reason="original-title-and-primary-heading",referencePage="https://independent.example/"))
        val html="""<script type='application/ld+json'>{"@type":"Movie","url":"${c.ref.pageUrl}","name":"Example"}</script>"""
        val refreshed=PageIdentity.attach(html,Detail(raw),seed).card
        assertEquals(seed.identityEvidence!!.referencePage,refreshed.identityEvidence!!.referencePage)
        assertEquals(seed.identityEvidence!!.reason,refreshed.identityEvidence!!.reason)
        assertFalse(refreshed.identityEvidence!!.yearVerified)
    }
}
