package app.filmmerkezi.tv.core
import org.junit.Test
import org.junit.Assert.*
class Stage3ContractTest {
    @Test fun continueRecordRetainsEpisodeParentWithoutDatabaseMigration(){
        val parent=ContentRef("filmcibaba","https://filmizle.ink/test-series/",ContentKind.SERIES)
        val card=Card(ContentRef("filmcibaba","https://filmizle.ink/bolum/test-s1-e1/",ContentKind.EPISODE),
            "Bölüm",seriesIdentity="SERIES:test",episodeNumber=EpisodeNumber(1,1),seriesRef=parent)
        val restored=Contracts.readCard(Contracts.card(card))
        assertEquals(parent,restored.seriesRef);assertEquals(card.episodeNumber,restored.episodeNumber)
        val legacy=Contracts.card(card);legacy.remove("seriesRef")
        assertNull(Contracts.readCard(legacy).seriesRef)
    }
}
