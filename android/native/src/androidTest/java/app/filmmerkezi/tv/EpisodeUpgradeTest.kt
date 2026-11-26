package app.filmmerkezi.tv

import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class EpisodeUpgradeTest {
    @Test fun oldEpisodeLabelIsRepairedWithoutChangingSavedWorkId()=runBlocking {
        val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext
        val name="episode-upgrade-test.db";context.deleteDatabase(name)
        try{WorkStore(context,name).use{works->
            val url="https://www.dizibox.live/diziler/12-monkeys-izle-1/"
            val old=Card(ContentRef("dizibox",url,ContentKind.EPISODE),"12 Monkeys",identityEvidence=IdentityEvidence(url,ContentKind.EPISODE,typeVerified=true,detailChecked=true))
            val before=works.item(old)
            val body=ins.context.assets.open("episodes/dizibox-multi.html").bufferedReader().use{it.readText()}
            val repository=CatalogRepository(object:WebTransport{override suspend fun request(request:WebRequest)=WebResponse(request.url,body)},works)
            val fixed=repository.detail(before.card)
            assertEquals(ContentKind.SERIES,fixed.card.ref.kind)
            assertEquals(before.identity,fixed.card.workId)
            assertEquals(13,fixed.episodes.size)
            val stored=checkNotNull(works.cached(before.identity));assertEquals(before.identity,stored.identity)
            assertEquals(ContentKind.SERIES,ContentIdentity.facts(stored.card).kind)
        }}finally{context.deleteDatabase(name)}
    }
}
