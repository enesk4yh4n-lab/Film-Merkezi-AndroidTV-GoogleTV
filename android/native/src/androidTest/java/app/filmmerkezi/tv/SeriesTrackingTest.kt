package app.filmmerkezi.tv

import app.filmmerkezi.tv.core.*
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class SeriesTrackingTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val series=Card(ContentRef("dizibox","https://www.dizibox.live/diziler/fixture/",ContentKind.SERIES),"Fixture",workId="work:fixture")
    private fun episodes(n:Int)=(1..n).map{v->val pair=EpisodeNumber(1,v);Episode(Card(ContentRef("dizibox","https://www.dizibox.live/fixture-1-sezon-$v-bolum-izle/",ContentKind.EPISODE),"Episode $v",seriesIdentity=series.workId,episodeNumber=pair),series.ref,pair)}
    @Test fun baselineNewEpisodesFailureAndReopen()=runBlocking{
        val name="series-test.db";context.deleteDatabase(name)
        try{
            SeriesTrackingStore(context,name).use{s->s.follow(series,episodes(2),true);assertTrue(s.state(series).pending.isEmpty());s.observe(series,episodes(3));assertEquals(setOf("1:3"),s.state(series).pending);s.observe(series,emptyList(),false);assertEquals(setOf("1:3"),s.state(series).pending)}
            SeriesTrackingStore(context,name).use{s->assertTrue(s.state(series).following);assertEquals(3,s.state(series).known.size);s.mark(series,listOf(EpisodeNumber(1,3)),true);assertTrue(s.state(series).pending.isEmpty());s.mark(series,listOf(EpisodeNumber(1,3)),false);assertEquals(false,s.state(series).marks["1:3"])}
        }finally{context.deleteDatabase(name)}
    }
    @Test fun bulkMarksDoNotCreateLibraryProgressAndSurviveCanonicalMerge()=runBlocking{
        val name="series-marks-test.db";context.deleteDatabase(name)
        try{SeriesTrackingStore(context,name).use{s->s.follow(series,episodes(3),true);s.mark(series,episodes(3).map{it.number},true);assertEquals(3,s.state(series).marks.count{it.value});val canonical=series.copy(workId="work:merged");s.rekey(series.workId,canonical);assertTrue(s.state(canonical).following);assertEquals(3,s.state(canonical).marks.size);s.follow(canonical,episodes(3),false);assertEquals(3,s.state(canonical).marks.size)}}finally{context.deleteDatabase(name)}
    }
}
