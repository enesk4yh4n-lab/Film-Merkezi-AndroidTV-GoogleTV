package app.filmmerkezi.tv

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.last
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject

class SeriesWork097Test {
    private val ins=InstrumentationRegistry.getInstrumentation();private val context=ins.targetContext
    private fun html(name:String)=ins.context.assets.open("series097/$name.html").bufferedReader().use{it.readText()}
    private fun houseCards():List<Card>{
        val p=CatalogParser(Sites.get("filmcibaba"))
        val seasons=p.list(html("baba-search"),Sites.get("filmcibaba").base+"/?s=House+of+the+Dragon").cards.mapIndexed{i,c->PageIdentity.attach(html("baba-house-${i+1}"),p.detail(html("baba-house-${i+1}"),c),c).card}
        val c=Card(ContentRef("hdfilmdelisi","https://hdfilmdelisi.one/dizi/house-of-the-dragon-izle",ContentKind.SERIES),"House of the Dragon")
        return seasons+PageIdentity.attach(html("delisi-house"),CatalogParser(Sites.get(c.ref.siteId)).detail(html("delisi-house"),c),c).card
    }
    private fun repository(works:WorkStore)=CatalogRepository(object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
        val name=when{
            request.url.contains("?s=")->"baba-search"
            request.url.contains("3-sezon-hbo")->"baba-house-1"
            request.url.contains("2-sezon-izlett")->"baba-house-2"
            request.url.contains("1-sezon-izlet")->"baba-house-3"
            request.url.contains("hdfilmdelisi.one/dizi/")->"delisi-house"
            else->error("Unexpected fixture request ${request.url}")
        };return WebResponse(request.url,html(name))
    }},works)
    @Test fun everySeasonEntryOpensTheSameThreeSeasonCatalog()=runBlocking{
        val name="series-work-097.db";context.deleteDatabase(name)
        try{WorkStore(context,name).use{works->
            val cards=houseCards();val groups=works.observe(cards);assertEquals(1,groups.size);val id=groups.single().identity
            assertEquals(4,groups.single().alternatives.size)
            val repository=repository(works)
            for(c in cards){val result=repository.episodeCatalog(c)
                assertEquals(listOf(10,8,8),result.episodes.groupBy{it.number.season}.values.map{it.size})
                assertEquals(6,result.unverifiedParts)
                assertTrue(result.complete)
                assertTrue(result.episodes.all{it.card.seriesIdentity==id})
                assertTrue(result.episodes.filter{it.number.season==3}.all{it.alternatives.map{c->c.ref.siteId}.toSet()==setOf("filmcibaba","hdfilmdelisi")})
            }
        }}finally{context.deleteDatabase(name)}
    }
    @Test fun seasonAndItsFirstEpisodeMayShareUrlButNeverAWorkRow()=runBlocking{
        val name="series-same-url-097.db";context.deleteDatabase(name)
        try{WorkStore(context,name).use{works->
            val parent=works.item(houseCards().first());val number=EpisodeNumber(3,1)
            val ep=parent.card.copy(ref=parent.card.ref.copy(kind=ContentKind.EPISODE),workId="",identityEvidence=null,seriesIdentity=parent.identity,seriesRef=parent.card.ref,episodeNumber=number)
            val episode=works.item(ep);assertNotEquals(parent.identity,episode.identity)
            assertEquals(ContentKind.SERIES,ContentIdentity.facts(works.cached(parent.identity)!!.card).kind)
            assertEquals(ContentKind.EPISODE,works.item(ep).card.ref.kind)
            assertEquals(parent.identity,works.item(parent.card).identity)
        }}finally{context.deleteDatabase(name)}
    }
    @Test fun declaredEmptySeasonsRemainVisibleWithoutInventingEpisodes()=runBlocking{
        val name="series-empty-seasons-097.db";context.deleteDatabase(name)
        try{WorkStore(context,name).use{works->
            val cards=houseCards().filter{it.ref.siteId=="filmcibaba"};works.observe(cards)
            val catalog=repository(works).episodeCatalog(cards.first())
            assertEquals(listOf(1,2,3),catalog.seasons)
            assertEquals(setOf(3),catalog.episodes.map{it.number.season}.toSet())
            assertEquals(14,catalog.episodes.size)
        }}finally{context.deleteDatabase(name)}
    }
    @Test fun otherSiteDiscoveryCanVerifyAPreviouslyUnknownSeasonYear()=runBlocking{
        val name="series-discovery-097.db";context.deleteDatabase(name)
        try{WorkStore(context,name).use{works->
            val seed=houseCards().first();val initial=works.item(seed)
            val repository=CatalogRepository(object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{
                val body=when{
                    request.url.startsWith("https://filmizle.ink/")->when{request.url.contains("?s=")->html("baba-search");request.url.contains("3-sezon")->html("baba-house-1");request.url.contains("2-sezon")->html("baba-house-2");else->html("baba-house-3")}
                    request.url.startsWith("https://hdfilmdelisi.one/api/search")->"""{"series":[{"baslik":"House of the Dragon","slug":"house-of-the-dragon-izle","yayinYili":2022}]}"""
                    request.url.startsWith("https://hdfilmdelisi.one/dizi/")->html("delisi-house")
                    else->throw DataFailure(FailureKind.NETWORK,"Simulated provider unavailable")
                };return WebResponse(request.url,body)
            }},works)
            val found=AlternativeDiscovery(repository,works).discover(initial.card,true).last()
            assertEquals(initial.identity,found.item.identity)
            assertTrue(found.item.alternatives.any{it.ref.siteId=="hdfilmdelisi"})
            assertEquals(listOf(10,8,8),repository.episodeCatalog(found.item.card).episodes.groupBy{it.number.season}.values.map{it.size})
        }}finally{context.deleteDatabase(name)}
    }
    @Test fun aSecondVerifiedRemakeRevokesAnAmbiguousSeasonJoin()=runBlocking{
        val name="series-remake-097.db";context.deleteDatabase(name)
        try{WorkStore(context,name).use{works->
            val cards=houseCards();val seed=cards.first();val id=works.item(seed).identity
            val raw=cards.last();val first=raw.copy(imdbId="tt1111111",identityEvidence=raw.identityEvidence!!.copy(imdbId="tt1111111"))
            works.observe(listOf(first));assertEquals(2,works.cached(id)!!.alternatives.size)
            val url="https://filmmakinesi.to/dizi/house-remake/"
            val other=first.copy(ref=ContentRef("filmmakinesi",url,ContentKind.SERIES),year=2024,imdbId="tt2222222",identityEvidence=first.identityEvidence!!.copy(pageUrl=url,year=2024,imdbId="tt2222222"))
            works.observe(listOf(other));val uncertain=works.item(seed)
            assertEquals(id,uncertain.identity);assertEquals(1,uncertain.alternatives.size);assertNull(uncertain.card.year)
            assertEquals(3,works.observe(listOf(seed,first,other)).size)
        }}finally{context.deleteDatabase(name)}
    }
    @Test fun oldUnscopedEpisodeRowRecoversWithoutLosingProgressBytes()=runBlocking{
        val name="series-legacy-url-097.db";val libraryName="series-legacy-library-097.db";context.deleteDatabase(name);context.deleteDatabase(libraryName)
        val seed=houseCards().first();val oldId="work:legacy-parent"
        val ep=seed.copy(ref=seed.ref.copy(kind=ContentKind.EPISODE),workId=oldId,identityEvidence=null,seriesIdentity=oldId,seriesRef=seed.ref,episodeNumber=EpisodeNumber(3,1))
        try{
            var created="";WorkStore(context,name).use{created=it.item(ep).identity}
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path,null,SQLiteDatabase.OPEN_READWRITE).use{db->
                val root=seed.ref.siteId+":"+seed.ref.pageUrl.trimEnd('/')
                db.execSQL("UPDATE works SET id=?,anchor=? WHERE id=?",arrayOf(oldId,root,created))
                db.execSQL("UPDATE sources SET source=?,work=?,card=?",arrayOf(root,oldId,Contracts.card(ep).toString()))
                db.execSQL("UPDATE tokens SET source=?",arrayOf(root))
            }
            LibraryStore(context,libraryName).use{it.update(ep,positionMs=45000,durationMs=100000)}
            WorkStore(context,name).use{works->
                val parent=works.item(seed);assertEquals(oldId,parent.identity)
                val episode=works.item(ep);assertNotEquals(parent.identity,episode.identity)
                assertEquals(ContentKind.SERIES,ContentIdentity.facts(works.cached(parent.identity)!!.card).kind)
                assertEquals(ContentKind.EPISODE,works.refresh(listOf(ep)).single().card.ref.kind)
            }
            LibraryStore(context,libraryName).use{assertEquals(45000L,it.records().single().positionMs)}
        }finally{context.deleteDatabase(name);context.deleteDatabase(libraryName)}
    }
}
