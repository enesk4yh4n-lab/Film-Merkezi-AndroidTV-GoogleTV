package app.filmmerkezi.tv.core

import org.junit.Test
import org.junit.Assert.*

class WorkIdentityTest {
    private fun c(title:String="Matrix",year:Int?=1999,site:String="filmcibaba",id:String=title)=Card(ContentRef(site,"https://filmizle.ink/"+TextKey.hash(id)+"/"),title,year=year)
    private fun checked(c:Card,kind:ContentKind=c.ref.kind,year:Int?=c.year,original:String=c.originalTitle)=c.copy(identityEvidence=IdentityEvidence(c.ref.pageUrl,kind,year,originalTitle=original,typeVerified=true,yearVerified=year!=null,detailChecked=true))
    private fun state(a:Card,b:Card)=ContentIdentity.decide(a,b).state
    @Test fun presentationSuffixes(){listOf("Loki 1. Sezon Türkçe Dublaj izle","Loki 2. Sezon Netflix Dizileri","Loki Disney Plus Dizisi izle","Loki HBO Max Dizileri","Loki Hint Filmleri").forEach{assertEquals("Loki",ContentIdentity.cleanTitle(it,2021))}}
    @Test fun meaningfulNumbersStay(){assertEquals("Matrix 2: Reloaded",ContentIdentity.cleanTitle("Matrix 2: Reloaded"));assertNotEquals(ContentIdentity.facts(c("Agents of SHIELD: Slingshot")).names,ContentIdentity.facts(c("Agents of SHIELD")).names)}
    @Test fun nestedMarketingAndEditionSuffixes(){
        for(title in listOf("Bir Eser Disney Plus Filmleri izle","Bir Eser Netflix Dizileri Full HD Film izle","Bir Eser Türkçe Dublaj ve Altyazılı 1080p izle","Bir Eser (Extended Versiyon)","Bir Eser Disney Plus izle"))assertEquals(title,"Bir Eser",ContentIdentity.cleanTitle(title))
        for(title in listOf("1917","1984","2001: A Space Odyssey","District 9","The One","Film","Scary Movie 2"))assertEquals(title,ContentIdentity.cleanTitle(title))
    }
    @Test fun firstInstallmentFormsUseAnIndependentAnchor(){
        val titles=listOf("A Work","The A Work","A Work 1","The A Work 1","A Work 1 (The A Work) izle")
        val anchor=checked(c("A Work").copy(imdbId="tt0133093"))
        val rows=listOf(anchor)+titles.mapIndexed{i,t->c(t,site="site$i")}
        assertEquals(1,ContentIdentity.merge(rows).size)
        assertNotEquals(IdentityState.SAME,state(c("A Work"),c("A Work 1",site="sinemacc")))
    }
    @Test fun sameYearSequelsCannotBeCollapsedByCandidateKeys(){
        val base=checked(c("A Work",2003).copy(imdbId="tt1111111"))
        val sequel=checked(c("A Work 2",2003,"sinemacc").copy(imdbId="tt2222222"))
        assertEquals(2,ContentIdentity.merge(listOf(base,sequel)).size)
        assertTrue(ContentIdentity.likelyRelated(c("A Work 2 Reloaded",2003),c("A Work Reloaded",2003,"sinemacc")))
        assertNotEquals(IdentityState.SAME,state(c("A Work 2 Reloaded",2003),c("A Work Reloaded",2003,"sinemacc")))
    }
    @Test fun chapterWordsAndDottedInitialsKeepMeaning(){
        assertEquals(IdentityState.SAME,state(c("John Wick: Chapter 2",2017),c("John Wick: Bölüm 2",2017,"sinemacc")))
        assertEquals(IdentityState.SAME,state(c("Agents of S.H.I.E.L.D.",2013),c("Agents of SHIELD",2013,"sinemacc")))
        assertNotEquals(IdentityState.SAME,state(c("John Wick: Chapter 2",2017),c("John Wick: Chapter 3",2017,"sinemacc")))
    }
    @Test fun sameYearNumberedSubtitlesCannotBridgeConflictingInstallments(){
        val a=checked(c("Example 2 Final Battle",2024));val b=checked(c("The Example 3 Final Battle",2024,"sinemacc"))
        assertEquals(IdentityState.DIFFERENT,state(a,b));assertEquals(2,ContentIdentity.merge(listOf(a,b)).size)
    }
    @Test fun verificationPrioritizesAnUnresolvedWorkOverAlreadyConnectedMembers(){
        val anchor=c("Example").copy(imdbId="tt1111111")
        val alreadyJoined=c("Example 1",site="sinemacc")
        val unresolved=c("Example Türkçe",year=null,site="fixfullfilmizle")
        assertEquals(unresolved.ref,ContentIdentity.verificationCandidates(listOf(anchor,alreadyJoined,unresolved),"Example").first().ref)
    }
    @Test fun firstInstallmentStillRejectsConflictingIdsAndRemakes(){
        val anchor=checked(c("A Work").copy(imdbId="tt1111111"))
        assertEquals(IdentityState.DIFFERENT,state(anchor,c("A Work 1",site="sinemacc").copy(imdbId="tt2222222")))
        assertEquals(IdentityState.DIFFERENT,state(anchor,c("A Work 1",2024,"sinemacc")))
        val unknown=c("A Work 1",site="ddizi")
        assertEquals(3,ContentIdentity.merge(listOf(anchor,anchor.copy(ref=anchor.ref.copy(siteId="sinemacc"),imdbId="tt2222222"),unknown)).size)
    }
    @Test fun imdbCorrectsWeakClassification(){val a=c().copy(imdbId="tt0133093");val b=checked(c(site="sinemacc").copy(ref=c().ref.copy(kind=ContentKind.SERIES),imdbId=a.imdbId));assertEquals(IdentityState.SAME,state(a,b))}
    @Test fun verifiedScopeConflictIsUncertain(){val a=checked(c().copy(imdbId="tt0133093"));val b=checked(c(site="sinemacc").copy(ref=c().ref.copy(kind=ContentKind.SERIES),imdbId=a.imdbId));assertEquals(IdentityState.UNCERTAIN,state(a,b))}
    @Test fun parentheticalOriginal(){assertEquals(IdentityState.SAME,state(c("Matrix 1 (The Matrix) izle"),c("The Matrix",site="sinemacc")))}
    @Test fun sequelStaysSeparate(){assertNotEquals(IdentityState.SAME,state(c("Matrix 2: Reloaded",2003),c("Matrix",2003,site="sinemacc")))}
    @Test fun missingYearIsNotGuessed(){assertEquals(IdentityState.UNCERTAIN,state(c(year=null),c(site="sinemacc")))}
    @Test fun checkedMissingMovieYearNeedsTwoIndependentPrimaryReleases(){
        val pending=checked(c("Example",null))
        val first=checked(c("Example",1999,"sinemacc").copy(imdbId="tt1111111"))
        val second=checked(c("Example",1999,"filmmakinesi"))
        assertNull(ContentIdentity.corroborate(pending,listOf(first)).year)
        val derived=ContentIdentity.corroborate(pending,listOf(first,second));assertEquals(1999,derived.year)
        assertEquals("two-independent-primary-releases",derived.identityEvidence!!.reason)
        assertNull(ContentIdentity.corroborate(derived,listOf(first,second,checked(c("Example",2024,"lovefilmizle")))).year)
        assertNull(ContentIdentity.corroborate(pending,listOf(first,derived.copy(ref=derived.ref.copy(siteId="ddizi")))) .year)
    }
    @Test fun checkedSeasonsShareSeries(){val a=checked(c("Loki",2021).copy(ref=c().ref.copy(kind=ContentKind.SERIES)));val b=checked(c("Loki 2. Sezon",2021,site="lovefilmizle"),ContentKind.SERIES);assertEquals(IdentityState.SAME,state(a,b))}
    @Test fun sparseCardCannotBridgeTwoIds(){val a=c("Yeraltı",2012).copy(imdbId="tt1961675");val b=c("Yeraltı",2012,"sinemacc").copy(imdbId="tt0114787");val unknown=c("Yeraltı",2012,"ddizi");assertEquals(3,ContentIdentity.merge(listOf(a,b,unknown)).size)}
    @Test fun orderingDoesNotChooseAmbiguousAnchor(){val a=c().copy(imdbId="tt1111111");val b=c(site="sinemacc").copy(imdbId="tt2222222");val u=c(site="ddizi");for(rows in listOf(listOf(a,b,u),listOf(u,b,a),listOf(b,u,a)))assertEquals(3,ContentIdentity.merge(rows).size)}
    @Test fun scoreIsNotIdentity(){assertNotEquals(IdentityState.SAME,state(c("A").copy(imdb=7.2),c("B").copy(imdb=7.2)))}
    @Test fun urlIsNotIdentity(){assertNotEquals(IdentityState.SAME,state(c("A"),c("B").copy(ref=c("A").ref.copy(siteId="sinemacc"))))}
    @Test fun pageEvidenceIsBound(){val a=c().copy(identityEvidence=IdentityEvidence("https://unrelated.example/",imdbId="tt0133093"));assertEquals("",ContentIdentity.facts(a).imdb)}
    @Test fun lateBilingualEvidence(){val a=c("Yerel Başlık");val b=c("Original Title",site="sinemacc");val bridge=c("Yerel Başlık",site="lovefilmizle").copy(originalTitle="Original Title");assertEquals(1,ContentIdentity.merge(listOf(a,b,bridge)).size)}
    @Test fun contradictoryPageImdb(){val a=c().copy(imdbId="tt0133093",identityEvidence=IdentityEvidence(c().ref.pageUrl,imdbConflict=true));assertEquals(IdentityState.UNCERTAIN,state(a,c(site="sinemacc")))}
    @Test fun correctionRebuildsRatherThanRetainingWrongMerge(){val a=c().copy(imdbId="tt0133093");val b=c(site="sinemacc");assertEquals(1,ContentIdentity.merge(listOf(a,b)).size);assertEquals(2,ContentIdentity.merge(listOf(a,b.copy(imdbId="tt9999999"))).size)}
    @Test fun originalHeadingCanCorroborateYear(){val a=checked(c("The Matrix"));val b=checked(c("Matrix 1 (The Matrix) izle",null,"sinemacc").copy(originalTitle="The Matrix"));assertEquals(1999,ContentIdentity.corroborate(b,listOf(a)).year)}
    @Test fun remakesBlockCorroboration(){val a=checked(c("The Example",1999));val b=checked(c("The Example",2024,"ddizi"));val u=checked(c("Example 1 (The Example)",null,"sinemacc").copy(originalTitle="The Example"));assertNull(ContentIdentity.corroborate(u,listOf(a,b)).year)}
    @Test fun sequelCannotBorrowOriginalYear(){val a=checked(c("The Example"));val b=checked(c("Example 2 (The Example)",null,"sinemacc").copy(originalTitle="The Example"));assertNull(ContentIdentity.corroborate(b,listOf(a)).year)}
    @Test fun derivedEvidenceDoesNotPropagate(){val a=checked(c("The Example"));val b=checked(c("Example 1 (The Example)",null,"sinemacc").copy(originalTitle="The Example"));val derived=ContentIdentity.corroborate(b,listOf(a));assertNull(ContentIdentity.corroborate(b.copy(ref=b.ref.copy(siteId="ddizi")),listOf(derived)).year)}
    @Test fun articleAliasNeedsCheckedPrimary(){val a=checked(c("Matrix 2 Reloaded",2003));assertEquals(IdentityState.SAME,state(a,checked(c("The Matrix 2 Reloaded",2003,"sinemacc"))));assertNotEquals(IdentityState.SAME,state(a,c("The Matrix 2 Reloaded",2003,"sinemacc")))}
    @Test fun episodeRequiresSeriesAndExactNumbers(){val a=c().copy(ref=c().ref.copy(kind=ContentKind.EPISODE),seriesIdentity="work:series",episodeNumber=EpisodeNumber(1,1));assertEquals(IdentityState.SAME,state(a,a.copy(ref=a.ref.copy(siteId="ddizi"))));assertEquals(IdentityState.DIFFERENT,state(a,a.copy(episodeNumber=EpisodeNumber(1,2))))}
    @Test fun displayVersionDoesNotChangeStableWork(){val a=c().copy(workId="work:fixed");assertEquals(ContentIdentity.key(a),ContentIdentity.key(a.copy(title="The Matrix",imdbId="tt0133093")))}
    @Test fun weakSeriesHeadingUsesIndependentReferenceAndPublicationHint(){
        val reference=checked(c("Example",2023,"sinemacc").copy(ref=c(site="sinemacc").ref.copy(kind=ContentKind.SERIES)))
        val raw=c("Example Disney Plus Dizisi izle",null).copy(yearHint=2023);val weak=raw.copy(identityEvidence=IdentityEvidence(raw.ref.pageUrl,ContentKind.MOVIE,detailChecked=true))
        val confirmed=ContentIdentity.corroborate(weak,listOf(reference));assertEquals(ContentKind.SERIES,ContentIdentity.facts(confirmed).kind);assertEquals(2023,confirmed.year)
        assertNull(ContentIdentity.corroborate(weak.copy(yearHint=2024),listOf(reference)).year)
    }
    @Test fun firstSeasonYearNeedsIndependentSeriesEvidence(){
        val reference=checked(c("Example",2023,"sinemacc").copy(ref=c(site="sinemacc").ref.copy(kind=ContentKind.SERIES)))
        val raw=c("Example 1. Sezon",null).copy(ref=c().ref.copy(kind=ContentKind.SERIES),yearHint=2023,sourceSeason=1)
        val confirmed=ContentIdentity.corroborate(checked(raw),listOf(reference));assertEquals(2023,confirmed.year);assertTrue(confirmed.identityEvidence!!.yearVerified)
    }
    @Test fun derivedYearIsRevokedWhenRemakeAppears(){
        val first=checked(c("The Example",1999,"sinemacc"));val unknown=checked(c("Example 1 (The Example)",null).copy(originalTitle="The Example"))
        val confirmed=ContentIdentity.corroborate(unknown,listOf(first));assertEquals(1999,confirmed.year)
        assertNull(ContentIdentity.corroborate(confirmed,listOf(first,checked(c("The Example",2024,"ddizi")))).year)
    }
}
