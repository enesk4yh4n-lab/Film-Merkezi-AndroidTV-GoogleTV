package app.filmmerkezi.tv.tv
import app.filmmerkezi.tv.core.*
import org.junit.Test
import org.junit.Assert.*

class TvPolicyTest {
    @Test fun isoRuntimeHasReadableMinuteCountAndUnknownTextIsLeftAlone() {
        assertEquals(102L,TvPolicy.durationMinutes("PT102M"))
        assertEquals(145L,TvPolicy.durationMinutes("PT2H25M12S"))
        assertEquals(2L,TvPolicy.durationMinutes("PT120S"))
        for(v in listOf("PT","PT0M","102 dakika","", "P2D", "PT99999999999999H"))assertNull(v,TvPolicy.durationMinutes(v))
    }
    @Test fun continueShelfExcludesUnstartedAndAlmostFinishedItems() {
        val c=Card(ContentRef("filmcibaba","https://filmizle.ink/a/"),"A")
        fun r(p:Long,d:Long)=LibraryRecord("a",c,false,p,d,0)
        val result=TvPolicy.continuing(listOf(r(0,100000),r(5000,100000),r(10000,100000),r(95000,100000),r(50000,0)))
        assertEquals(listOf(10000L,50000L),result.map{it.positionMs})
    }
    @Test fun episodeCardKeepsIdentityAndResumeMetadata() {
        val c=Card(ContentRef("filmcibaba","https://filmizle.ink/show-1-sezon-1-bolum/",ContentKind.EPISODE),"Bölüm",seriesIdentity="show",episodeNumber=EpisodeNumber(1,1))
        val tile=TvPolicy.tile(c,25000,100000)
        assertEquals(ContentIdentity.key(c),tile.key)
        assertEquals(25000L,tile.positionMs)
        assertEquals(c,tile.item!!.card)
    }
}
