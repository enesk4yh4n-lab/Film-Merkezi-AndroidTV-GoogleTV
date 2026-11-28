package app.filmmerkezi.tv.core

/** Evidence is scoped to one public content page. Derived facts never verify another page. */
data class IdentityEvidence(
    val pageUrl:String, val kind:ContentKind?=null, val year:Int?=null, val imdbId:String="",
    val originalTitle:String="", val aliases:List<String> = emptyList(), val title:String="",
    val typeVerified:Boolean=false,val yearVerified:Boolean=false,val detailChecked:Boolean=false,
    val imdbConflict:Boolean=false,val reason:String="",val referencePage:String="",val season:Int?=null,val revision:Int=4
)
enum class IdentityState { SAME, DIFFERENT, UNCERTAIN }
data class IdentityDecision(val state:IdentityState,val reason:String)

/** Native counterpart of the Tizen evidence rules; presentation changes never edit source IDs. */
object ContentIdentity {
    private val imdbPattern=Regex("^tt\\d{7,10}$")
    private val trailing=Regex("(?:\\s+|\\s*[-–—|:]\\s*)(?:full(?:\\s+hd)?(?:\\s+film)?|hd(?:\\s+film)?|1080p|720p|2160p|4k|izle|(?:turkce|ingilizce)\\s+(?:dublaj(?:li)?|altyazi(?:li)?)(?:\\s*(?:&|ve)\\s*(?:dublaj|altyazili))?|dublaj(?:li)?|altyazi(?:li)?|turkce)\\s*$")
    private val brands="netflix|disney(?:\\s+plus|\\+)?|hbo(?:\\s+max)?|max|amazon(?:\\s+prime)?|prime\\s+video|apple(?:\\s+tv\\+?)?|marvel|dc|hulu|paramount|peacock|mubi|blutv|exxen|gain|tabii"
    private val seasonPattern=Regex("(?:\\s+|\\s*[-–—|:]\\s*)(?:\\d+\\.?\\s*(?:sezon|season)(?:\\s*\\d+\\.?\\s*(?:bolum|episode))?|(?:sezon|season)\\s*\\d+)(?:\\s+(?:$brands))?\\s*$")
    private val countries="hint|yerli|turk|turkce|yabanci|kore|korece|japon|japonca|cin|cince|fransiz|fransizca|ispanyol|ispanyolca|alman|almanca|italyan|italyanca|ingiliz|ingilizce|amerikan|rus|rusca"
    private val category=Regex("(?:\\s+|\\s*[-–—|:]\\s*)(?:$brands|$countries)\\s+(?:film(?:i|leri|lerini)?|dizi(?:si|leri|lerini)?)\\s*$")
    private val article=Regex("^(?:the|an|a)\\s+")
    private val firstNumber=Regex("\\s+(?:1|i)$")
    private val dottedInitials=Regex("\\b(?:[A-Za-z]\\.){2,}[A-Za-z]?\\.?")
    private val chapter=Regex("\\s+(?:chapter|bolum|part|kisim)\\s+(?=\\d+\\b)")
    private val edition=Regex("""(?i)\s*[\(\[]\s*(?:extended(?:\s+(?:version|versiyon))?|uzatilmis\s+versiyon|director'?s\s+cut|yonetmen\s+kurgusu|unrated|remastered)\s*[\)\]]\s*$""")
    private val platform=Regex("(?:\\s+|\\s*[-–—|:]\\s*)(?:disney\\s+plus|amazon\\s+prime|prime\\s+video|apple\\s+tv\\+?)\\s*$")
    private fun name(value:String)=TextKey.fold(dottedInitials.replace(value){it.value.replace(".","")}).replace(chapter," ")
    data class Facts(val names:Map<String,Boolean>,val kind:ContentKind,val year:Int?,val imdb:String,val checked:Boolean,val typeVerified:Boolean,val evidence:IdentityEvidence?,val candidates:Set<String>,val installments:Map<Int,Int>)
    private fun evidence(c:Card)=c.identityEvidence?.takeIf{it.pageUrl.substringBefore('#')==c.ref.pageUrl.substringBefore('#')}
    fun cleanTitle(value:String,year:Int?=null):String {
        var s=TextKey.clean(org.jsoup.parser.Parser.unescapeEntities(value,false).replace('\u00a0',' ')).take(300)
        repeat(12){
            val before=s
            // Match folded text while retaining the original displayed spelling.
            val folded=s.replace('İ','i').lowercase(java.util.Locale.ROOT).replace('ı','i').replace('ş','s').replace('ğ','g').replace('ü','u').replace('ö','o').replace('ç','c')
            val match=trailing.find(folded)?:seasonPattern.find(folded)?:category.find(folded)?:platform.find(folded)?:edition.find(folded)
            if(match!=null&&match.range.first>0)s=s.take(match.range.first).trim()
            if(year!=null)s=s.replace(Regex("(?:\\s*[-–—:(]\\s*|\\s+)$year\\s*\\)?$"),"").trim()
            if(s==before)return s
        }
        return s.ifBlank{value}
    }
    private val batch=ThreadLocal<java.util.IdentityHashMap<Card,Facts>?>()
    private fun <T> withBatch(action:()->T):T {
        if(batch.get()!=null)return action()
        batch.set(java.util.IdentityHashMap())
        try{return action()}finally{batch.remove()}
    }
    fun facts(c:Card):Facts {val cache=batch.get();return cache?.get(c)?:buildFacts(c).also{cache?.put(c,it)}}
    private fun buildFacts(c:Card):Facts {
        val e=evidence(c);val year=e?.year?:c.year
        val names=linkedMapOf<String,Boolean>()
        fun add(s:String,strong:Boolean=true){val n=name(cleanTitle(s,year));if(n.isNotBlank())names[n]=(names[n]==true)||strong}
        val title=cleanTitle(e?.title?.ifBlank{c.title}?:c.title,year)
        add(title);add(e?.originalTitle?.ifBlank{c.originalTitle}?:c.originalTitle)
        (c.aliases+e?.aliases.orEmpty()).take(16).forEach{add(it)}
        Regex("\\(([^()]+)\\)").find(title)?.takeUnless{it.groupValues[1].matches(Regex("\\d{4}"))}?.let{add(it.groupValues[1]);add(title.replace(it.value,""))}
        title.split(Regex("\\s+[–—|]\\s+|\\s+-\\s+")).takeIf{it.size==2}?.forEach{add(it)}
        names.keys.toList().forEach{n->
            val unbranded=n.replace(Regex("^(?:marvels?|dc)\\s+"),"");if(unbranded!=n)add(unbranded,false)
            val unarticled=n.replace(article,"");if(unarticled!=n)add(unarticled,false)
            // A first-installment notation is only a candidate alias. decide() still requires evidence.
            val first=unarticled.replace(firstNumber,"")
            if(first!=unarticled&&first.count{it.isLetter()}>=4)add(first,false)
            val subtitled=unarticled.replace(Regex("\\s+(?:[1-9]|1[0-9]|20)(?=\\s+[a-z])"),"")
            if(subtitled!=unarticled)add(subtitled,false)
        }
        val candidates=names.keys.flatMap{n->val bare=n.replace(article,"");listOf(n,bare,bare.replace(Regex("\\s+(?:[1-9]|1[0-9]|20|ii|iii|iv|v)(?=\\s+[a-z])"),""),bare.replace(Regex("\\s+(?:18|19|20)\\d{2}$"),""))}.filter{it.count(Char::isLetter)>=3}.toSet()
        val parts=name(title.substringBefore('(')).replace(article,"").split(' ').mapIndexedNotNull{i,s->s.toIntOrNull()?.takeIf{it in 1..20}?.let{i to it}}.toMap()
        return Facts(names,e?.kind?:c.ref.kind,year,if(e?.imdbConflict==true)""else (e?.imdbId?.ifBlank{c.imdbId}?:c.imdbId).takeIf{imdbPattern.matches(it)}.orEmpty(),e?.detailChecked==true,e?.typeVerified==true,e,candidates,parts)
    }
    /** Retrieval keys may be broad; they never constitute a SAME decision by themselves. */
    fun candidateNames(c:Card):Set<String> = facts(c).candidates
    fun likelyRelated(a:Card,b:Card):Boolean {
        val x=facts(a);val y=facts(b)
        if(x.kind in setOf(ContentKind.COLLECTION,ContentKind.EPISODE)||y.kind in setOf(ContentKind.COLLECTION,ContentKind.EPISODE))return false
        if(x.year!=null&&y.year!=null&&x.year!=y.year)return false
        val left=candidateNames(a);val right=candidateNames(b)
        return left.intersect(right).isNotEmpty()||left.any{x->right.any{y->
            val short=if(x.length<y.length)x else y;val long=if(x.length<y.length)y else x
            short.replace(article,"").split(' ').size>=2&&long.startsWith("$short ")
        }}
    }
    fun verificationCandidates(cards:List<Card>,query:String?=null,limit:Int=12):List<Card> = withBatch {
        val unique=cards.distinctBy{it.ref.id}.filter{it.ref.kind !in setOf(ContentKind.COLLECTION,ContentKind.EPISODE)}
        val groups=mergeBatch(unique)
        val sizes=groups.flatMap{g->g.alternatives.map{it.ref.id to g.alternatives.size}}.toMap()
        val membership=groups.flatMapIndexed{i,g->g.alternatives.map{it.ref.id to i}}.toMap()
        val q=query?.let{ name(cleanTitle(it)).replace(article,"") }.orEmpty()
        val ranked=unique.filter{!facts(it).checked}.map{c->
            val related=unique.filter{membership[it.ref.id]!=membership[c.ref.id]&&it.ref.siteId!=c.ref.siteId&&likelyRelated(c,it)}
            val uncertain=related.filter{decide(c,it).state==IdentityState.UNCERTAIN}
            val unresolved=uncertain.size
            val sameScope=uncertain.count{facts(it).kind==facts(c).kind}
            val queryMatch=q.isNotBlank()&&facts(c).names.keys.any{it.contains(q)}
            c to (sameScope*20+(unresolved-sameScope)*3+uncertain.count{facts(it).imdb.isNotEmpty()}*5+
                (if(unresolved>0&&facts(c).imdb.isNotBlank())15 else 0)+(if(facts(c).year==null)3 else 0)+
                (if(queryMatch)8 else 0)+(if(queryMatch&&sizes[c.ref.id]==1)12 else 0))
        }.sortedWith(compareByDescending<Pair<Card,Int>>{it.second}.thenBy{it.first.ref.id})
        // Reserve slots for each unresolved title family rather than spending all twelve on one film.
        val selected=mutableListOf<Card>();val perFamily=mutableMapOf<String,Int>()
        for((c,_) in ranked){val family=candidateNames(c).minByOrNull{it.length}?:c.ref.id
            if((perFamily[family]?:0)<3){selected.add(c);perFamily[family]=(perFamily[family]?:0)+1}
            if(selected.size==limit)return@withBatch selected
        }
        (selected+ranked.map{it.first}).distinctBy{it.ref.id}.take(limit)
    }
    fun key(c:Card):String {
        if(c.workId.isNotBlank())return c.workId
        if(c.ref.kind==ContentKind.EPISODE)return if(c.seriesIdentity.isNotBlank()&&c.episodeNumber!=null)"EPISODE:${c.seriesIdentity}:s${c.episodeNumber.season}:e${c.episodeNumber.number}"else c.ref.id
        if(c.ref.kind==ContentKind.COLLECTION)return c.ref.id
        val a=facts(c)
        return if(a.imdb.isNotBlank())"${a.kind}:${a.imdb}"else if(a.year!=null)"${a.kind}:${a.year}:"+TextKey.fold(cleanTitle(c.originalTitle.ifBlank{c.title},a.year))else c.ref.id
    }
    fun decide(left:Card,right:Card):IdentityDecision {
        fun answer(s:IdentityState,r:String)=IdentityDecision(s,r)
        val a=facts(left);val b=facts(right)
        if(a.kind==ContentKind.COLLECTION||b.kind==ContentKind.COLLECTION)return answer(IdentityState.DIFFERENT,"collection-scope")
        if(a.kind==ContentKind.EPISODE||b.kind==ContentKind.EPISODE){
            return answer(if(a.kind==b.kind&&left.seriesIdentity.isNotBlank()&&left.seriesIdentity==right.seriesIdentity&&left.episodeNumber!=null&&left.episodeNumber==right.episodeNumber)IdentityState.SAME else IdentityState.DIFFERENT,"episode-scope")
        }
        if(a.evidence?.imdbConflict==true||b.evidence?.imdbConflict==true)return answer(IdentityState.UNCERTAIN,"page-imdb-conflict")
        if(a.imdb.isNotBlank()&&b.imdb.isNotBlank()){
            if(a.imdb!=b.imdb)return answer(IdentityState.DIFFERENT,"imdb-conflict")
            if(a.typeVerified&&b.typeVerified&&a.kind!=b.kind)return answer(IdentityState.UNCERTAIN,"verified-type-conflict")
            return answer(IdentityState.SAME,"imdb")
        }
        if(a.kind!=b.kind)return answer(if(a.typeVerified&&b.typeVerified)IdentityState.DIFFERENT else IdentityState.UNCERTAIN,"type")
        val parentA=left.seriesRef;val parentB=right.seriesRef
        if(a.kind==ContentKind.SERIES&&parentA!=null&&parentB!=null&&parentA.kind==ContentKind.SERIES&&parentB.kind==ContentKind.SERIES&&
            parentA.siteId==left.ref.siteId&&parentB.siteId==right.ref.siteId&&parentA.siteId==parentB.siteId&&
            parentA.pageUrl.substringBefore('#').trimEnd('/')==parentB.pageUrl.substringBefore('#').trimEnd('/'))
            return answer(IdentityState.SAME,"explicit-series-parent")
        val common=a.names.keys.intersect(b.names.keys)
        val sa=left.sourceSeason?:a.evidence?.season;val sb=right.sourceSeason?:b.evidence?.season
        if(a.kind==ContentKind.SERIES&&left.ref.siteId==right.ref.siteId&&sa!=null&&sb!=null&&a.checked&&b.checked&&a.typeVerified&&b.typeVerified&&common.any{a.names[it]==true&&b.names[it]==true}){
            if(sa==1&&sb==1&&left.yearHint!=null&&right.yearHint!=null&&left.yearHint!=right.yearHint)return answer(IdentityState.DIFFERENT,"first-season-year-conflict")
            if(a.year!=null&&b.year!=null&&a.year!=b.year)return answer(IdentityState.DIFFERENT,"series-release-year")
            return answer(IdentityState.SAME,"same-site-series-seasons")
        }
        if(a.year!=null&&b.year!=null&&a.year!=b.year)return answer(IdentityState.DIFFERENT,"year")
        if(a.year==null||b.year==null)return answer(IdentityState.UNCERTAIN,"missing-year")
        if(common.isEmpty())return answer(IdentityState.UNCERTAIN,"name")
        if(common.any{a.names[it]==true&&b.names[it]==true})return answer(IdentityState.SAME,"name-year-type")
        val x=a.installments;val y=b.installments
        if(x.any{(position,number)->y[position]?.let{it!=number}==true})return answer(IdentityState.DIFFERENT,"installment")
        val supported=a.typeVerified&&b.typeVerified ||
            a.kind==ContentKind.MOVIE&&(a.imdb.isNotBlank()||b.imdb.isNotBlank())
        return answer(if(supported)IdentityState.SAME else IdentityState.UNCERTAIN,"presentation-alias")
    }
    fun equivalent(a:Card,b:Card)=a.ref.id==b.ref.id||decide(a,b).state==IdentityState.SAME
    fun displayTitle(c:Card)=if(c.ref.kind in setOf(ContentKind.EPISODE,ContentKind.COLLECTION))TextKey.clean(c.title)else cleanTitle(c.title,facts(c).year)
    fun relevant(c:Card,query:String):Boolean {val q=TextKey.fold(cleanTitle(query));return q.isNotBlank()&&facts(c).names.keys.any{it.contains(q)}}
    fun corroborate(input:Card,references:List<Card>):Card {
        var c=input
        val previous=evidence(c)
        if(previous?.reason in setOf("original-title-and-primary-heading","series-heading-and-reference-year","two-independent-primary-releases","verified-series-release")){
            val e=checkNotNull(previous);c=c.copy(year=null,identityEvidence=e.copy(year=null,yearVerified=false,referencePage="",reason="",kind=if(e.reason=="series-heading-and-reference-year")c.ref.kind else e.kind,typeVerified=if(e.reason=="series-heading-and-reference-year")false else e.typeVerified))
        }
        val a=facts(c);val e=a.evidence?:return c
        if(!a.checked||e.imdbConflict)return c
        val original=TextKey.fold(e.originalTitle.ifBlank{c.originalTitle})
        val primary=TextKey.fold(cleanTitle(c.title).replace(Regex("\\s*\\([^)]*\\)"),"")).trim()
        fun base(s:String)=s.replace(Regex("^(?:the|a|an) "),"")
        val originalHeading=original.isNotBlank()&&original!=primary&&base(primary).replace(Regex("(?:^| )1(?= |$)")," ").replace(Regex("\\s+")," ").trim()==base(original)
        val sourceSeason=c.sourceSeason?:e.season?:Regex("(?i)(\\d+)\\.?\\s*(?:sezon|season)").find(c.title)?.groupValues?.get(1)?.toIntOrNull()
        if(a.kind==ContentKind.SERIES&&sourceSeason!=null&&a.year==null&&(sourceSeason!=1||c.yearHint==null)){
            val matches=references.filter{r->val b=facts(r);r.ref.siteId!=c.ref.siteId&&b.kind==ContentKind.SERIES&&b.typeVerified&&b.year!=null&&
                b.evidence?.yearVerified==true&&b.evidence.referencePage.isBlank()&&
                (a.imdb.isBlank()||b.imdb.isBlank()||a.imdb==b.imdb)&&b.names.any{(n,strong)->strong&&a.names[n]==true}}
            val years=matches.mapNotNull{facts(it).year}.distinct();val ids=matches.map{facts(it).imdb}.filter{it.isNotBlank()}.distinct()
            if(years.size==1&&ids.size<=1)return c.copy(year=years.single(),sourceSeason=sourceSeason,identityEvidence=e.copy(year=years.single(),yearVerified=false,season=sourceSeason,referencePage=matches.first().ref.pageUrl,reason="verified-series-release"))
        }
        val seriesHint=sourceSeason!=null||Regex("\\bdizi(?:si|leri)?\\b").containsMatchIn(TextKey.fold(c.title))
        val originalYear=a.kind==ContentKind.MOVIE&&a.typeVerified&&a.year==null&&originalHeading
        val primaryYear=a.kind==ContentKind.MOVIE&&a.typeVerified&&a.year==null&&!originalYear
        val verifyYear=a.kind==ContentKind.SERIES&&a.typeVerified&&a.year==null&&sourceSeason==1&&c.yearHint!=null
        val correctType=a.kind==ContentKind.MOVIE&&!a.typeVerified&&seriesHint&&(a.year!=null||c.yearHint!=null)
        if(!originalYear&&!primaryYear&&!verifyYear&&!correctType)return c
        // A publication year is never the original movie's release year.
        val targetYear=if(originalYear||primaryYear)null else a.year?:c.yearHint
        val primaryName=name(cleanTitle(c.title))
        val matches=references.filter{r->val b=facts(r);r.ref.siteId!=c.ref.siteId&&b.typeVerified&&b.year!=null&&b.evidence?.referencePage.isNullOrBlank()&&b.kind==(if(originalYear||primaryYear)ContentKind.MOVIE else ContentKind.SERIES)&&
            (targetYear==null||b.year==targetYear)&&(a.imdb.isBlank()||b.imdb.isBlank()||a.imdb==b.imdb)&&
            if(originalYear)b.names[original]==true else if(primaryYear)b.evidence?.yearVerified==true&&b.names[primaryName]==true else b.names.any{(name,strong)->strong&&a.names[name]==true}}
        val years=matches.mapNotNull{facts(it).year}.distinct();val ids=matches.map{facts(it).imdb}.filter{it.isNotBlank()}.distinct()
        if(years.size!=1||ids.size>1||primaryYear&&(matches.map{it.ref.siteId}.distinct().size<2||ids.size!=1))return c
        return c.copy(year=years.single(),sourceSeason=sourceSeason,identityEvidence=e.copy(kind=if(originalYear||primaryYear)ContentKind.MOVIE else ContentKind.SERIES,typeVerified=true,
            year=years.single(),yearVerified=verifyYear,referencePage=matches.first().ref.pageUrl,season=sourceSeason,
            reason=if(originalYear)"original-title-and-primary-heading"else if(primaryYear)"two-independent-primary-releases"else if(verifyYear)"first-season-and-verified-series"else if(a.year!=null)"season-and-verified-series"else "series-heading-and-reference-year"))
    }
    fun merge(cards:List<Card>):List<UnifiedItem> = withBatch{mergeBatch(cards)}
    private class Group(val order:Int,val cards:MutableList<Card>){val tokens=linkedSetOf<String>()}
    private fun matchTokens(c:Card):List<String>{
        val f=facts(c)
        if(f.kind==ContentKind.COLLECTION)return listOf("ref:"+c.ref.id)
        if(f.kind==ContentKind.EPISODE)return listOf("ref:"+c.ref.id)+if(c.seriesIdentity.isNotBlank()&&c.episodeNumber!=null)listOf("episode:${c.seriesIdentity}:${c.episodeNumber}")else emptyList()
        val parent=c.seriesRef?.takeIf{f.kind==ContentKind.SERIES&&it.kind==ContentKind.SERIES&&it.siteId==c.ref.siteId}
        return f.names.keys.map{"name:$it"}+listOf("ref:"+c.ref.id)+(parent?.let{listOf("series-parent:${it.siteId}:"+it.pageUrl.substringBefore('#').trimEnd('/'))}?:emptyList())+(if(f.imdb.isBlank())emptyList()else listOf("imdb:"+f.imdb))
    }
    private fun mergeBatch(cards:List<Card>):List<UnifiedItem> {
        val input=cards.groupBy{it.ref.id}.map{(_,v)->v.maxBy{if(facts(it).checked)1 else 0}}
        val checked=input.map{corroborate(it,input)}
        val positions=input.mapIndexed{i,c->c.ref.id to i}.toMap()
        val groups=linkedSetOf<Group>();val index=mutableMapOf<String,MutableSet<Group>>();var sequence=0
        fun index(g:Group,c:Card){matchTokens(c).forEach{token->g.tokens.add(token);index.getOrPut(token){linkedSetOf()}.add(g)}}
        // Strong anchors first: a sparse card must never pick the first of two conflicting works.
        checked.sortedByDescending{if(facts(it).imdb.isNotBlank())2 else if(facts(it).checked)1 else 0}.forEach{c->
            val candidates=matchTokens(c).flatMap{index[it].orEmpty()}.distinct().sortedBy{it.order}
                .filter{g->g.cards.any{equivalent(c,it)}&&g.cards.none{decide(c,it).state==IdentityState.DIFFERENT}}
            val ids=candidates.flatMap{it.cards}.map{facts(it).imdb}.filter{it.isNotBlank()}.distinct()
            val conflict=candidates.indices.any{i->candidates.drop(i+1).any{b->candidates[i].cards.any{x->b.cards.any{decide(x,it).state==IdentityState.DIFFERENT}}}}
            if(candidates.isEmpty()||conflict||facts(c).imdb.isBlank()&&ids.size>1){val g=Group(sequence++,mutableListOf(c));groups.add(g);index(g,c)}
            else{val g=candidates.first();g.cards.add(c);index(g,c);candidates.drop(1).forEach{other->
                g.cards.addAll(other.cards);other.tokens.forEach{t->index[t]?.remove(other);index.getOrPut(t){linkedSetOf()}.add(g);g.tokens.add(t)};groups.remove(other)
            }}
        }
        return groups.sortedBy{g->g.cards.minOf{positions[it.ref.id]?:Int.MAX_VALUE}}.map{group->val g=group.cards
            val preferred=g.maxBy{(if(facts(it).imdb.isNotBlank())16 else 0)+(if(facts(it).checked)8 else 0)+(if(it.poster.isNotBlank())2 else 0)}
            UnifiedItem(key(preferred),preferred.copy(title=cleanTitle(preferred.title,facts(preferred).year)),g.sortedBy{positions[it.ref.id]})
        }
    }
}
