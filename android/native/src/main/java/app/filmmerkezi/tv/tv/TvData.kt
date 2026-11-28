package app.filmmerkezi.tv.tv

import android.content.Context
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import app.filmmerkezi.tv.settings.PlaybackOverrides

/** UI receives immutable typed results. Test fixtures replace this boundary, never production URLs. */
interface TvData {
    suspend fun clearSearches(){}
    suspend fun clearPlaybackHistory(){}
    suspend fun clearPlaybackPreferences(){}
    suspend fun playbackPreferences(card:Card):PlaybackOverrides=PlaybackOverrides()
    suspend fun updatePlaybackPreferences(card:Card,change:(PlaybackOverrides)->PlaybackOverrides):PlaybackOverrides=change(playbackPreferences(card))
    val storageWarning:Boolean get()=false
    fun browse(kind:ContentKind,cursors:Map<String,PageCursor> = emptyMap()):Flow<UnifiedPage>
    fun search(query:String,cursors:Map<String,PageCursor> = emptyMap()):Flow<UnifiedPage>
    fun browseSite(siteId:String,kind:ContentKind,cursors:Map<String,PageCursor> = emptyMap()):Flow<UnifiedPage> = browse(kind,cursors)
    fun searchSite(siteId:String,query:String,cursors:Map<String,PageCursor> = emptyMap()):Flow<UnifiedPage> = search(query,cursors)
    fun filteredBrowse(siteId:String,query:CatalogQuery,cursors:Map<String,PageCursor> = emptyMap()):Flow<UnifiedPage> = browseSite(siteId,query.kind,cursors)
    fun section(siteId:String,section:CatalogSection,cursors:Map<String,PageCursor> = emptyMap()):Flow<UnifiedPage> = flowOf(UnifiedPage(emptyList(),emptyMap(),emptyList()))
    suspend fun suggestions(siteId:String,query:String):List<Card> = emptyList()
    fun pausedFor(siteId:String):Long=0L
    fun retrySite(siteId:String){}
    fun invalidateCatalog(){}
    suspend fun catalogCacheBytes():Long=0L
    suspend fun clearCatalogCache(){}
    suspend fun filterOptions(siteId:String):FilterOptions=FilterOptions()
    suspend fun detail(card:Card):Detail
    suspend fun episodes(card:Card):List<Episode>
    suspend fun episodeCatalog(card:Card):SeriesEpisodes=SeriesEpisodes(episodes(card))
    fun recommendations(criteria:CatalogQuery,siteId:String):Flow<RecommendationResult> = flowOf(RecommendationResult(emptyList(),0,0,0,true))
    suspend fun collections():List<Card> = emptyList()
    suspend fun collectionMatches(entry:CollectionEntry,search:Boolean=false):List<UnifiedItem> = emptyList()
    suspend fun hiddenCollectionEntry(entry:CollectionEntry):Boolean=false
    suspend fun tracking(card:Card):SeriesTracking=SeriesTracking(card)
    suspend fun followed():List<SeriesTracking> = emptyList()
    suspend fun follow(card:Card,episodes:List<Episode>,enabled:Boolean){}
    suspend fun markEpisodes(card:Card,numbers:List<EpisodeNumber>,watched:Boolean){}
    suspend fun clearNew(card:Card){}
    suspend fun refreshFollowing(force:Boolean=false):List<SeriesTracking> = followed()
    suspend fun work(card:Card):UnifiedItem=UnifiedItem(ContentIdentity.key(card),card,listOf(card))
    fun discoverAlternatives(card:Card,force:Boolean=false):Flow<DiscoverySnapshot> = flow{emit(DiscoverySnapshot(work(card),running=false))}
    fun sourcePool(card:Card,initial:List<SourceOption>,crossSite:Boolean,gate:suspend()->Unit={}):Flow<SourcePool> = flowOf(SourcePool(initial.map{it.scoped()},false))
    fun episodeSourcePool(item:UnifiedItem,crossSite:Boolean,gate:suspend()->Unit={}):Flow<SourcePool> =
        EpisodeSourcePool.observe(item,crossSite,::detail,{sourcePool(item.card,emptyList(),crossSite,gate)},gate)
    fun refreshSourcePool(card:Card,item:UnifiedItem?,initial:List<SourceOption>,crossSite:Boolean,gate:suspend()->Unit):Flow<SourcePool> =
        if(item!=null)episodeSourcePool(item,crossSite,gate)else sourcePool(card,initial,crossSite,gate)
    suspend fun resolve(option:SourceOption):ResolvedSource
    suspend fun records():List<LibraryRecord>
    suspend fun recordsPage(listedOnly:Boolean,offset:Int,limit:Int):List<LibraryRecord> =
        records().filter{if(listedOnly)it.listed else it.positionMs>0}.drop(offset).take(limit)
    suspend fun update(card:Card,listed:Boolean?=null,position:Long?=null,duration:Long?=null)
    suspend fun beginPlayback(card:Card):PlaybackCheckpoint {
        val previous=records().filter{ContentIdentity.equivalent(it.card,card)}.maxByOrNull{it.playbackUpdatedAt}
        return PlaybackCheckpoint(java.util.UUID.randomUUID().toString(),card,previous?.positionMs?:0,previous?.durationMs?:0)
    }
    suspend fun savePlayback(cp:PlaybackCheckpoint,position:Long,duration:Long){update(cp.card,position=position,duration=duration)}
    suspend fun abandonPlayback(cp:PlaybackCheckpoint){}
    suspend fun finishPlayback(cp:PlaybackCheckpoint,mode:PlaybackFinish,position:Long,duration:Long){
        update(cp.card,position=if(mode==PlaybackFinish.SAVE)position else cp.positionMs,duration=if(mode==PlaybackFinish.SAVE)duration else cp.durationMs)
    }
    suspend fun cancelPlaybackFinish(cp:PlaybackCheckpoint){}
    suspend fun remember(query:String)
    suspend fun searches():List<String>
}

internal class TvServices private constructor(context:Context):TvData {
    val settings=app.filmmerkezi.tv.settings.SettingsRepository.get(context)
    val network=ProviderNetwork(context)
    private val works=WorkStore(context)
    private val snapshots=CatalogSnapshots(java.io.File(context.cacheDir,"catalog-pages-v1"))
    private val repository=CatalogRepository(network,works,{settings.value.catalog()},{settings.value.installedSites().map{it.id}.filter{it !in settings.value.disabledCatalog||settings.value.sourceAllowed(it)}.toSet()},snapshots)
    private val discovery=AlternativeDiscovery(repository,works){settings.value.installedSites().map{it.id}.filter{settings.value.sourceAllowed(it)}}
    private val pools=SourcePoolService(repository,works,discovery,{settings.value.sourceAllowed(it)},{settings.value.hideAdult})
    val resolver=SourceResolver(network)
    private val library=LibraryStore(context)
    private val seriesTracking=SeriesTrackingStore(context)
    private val playbackOverrides=app.filmmerkezi.tv.settings.PlaybackOverridesStore(context)
    private suspend fun preferenceKey(card:Card):String {
        if(card.ref.kind==ContentKind.EPISODE&&card.seriesIdentity.isNotBlank())works.cached(card.seriesIdentity)?.let{parent->
            if(parent.card.identityEvidence?.typeVerified==true&&ContentIdentity.facts(parent.card).kind==ContentKind.SERIES)return parent.identity
        }
        return works.item(card).identity
    }
    override suspend fun playbackPreferences(card:Card)=playbackOverrides.get(works.aliasesFor(preferenceKey(card)))
    override suspend fun updatePlaybackPreferences(card:Card,change:(PlaybackOverrides)->PlaybackOverrides):PlaybackOverrides {
        val key=preferenceKey(card);return playbackOverrides.update(key,works.aliasesFor(key),change)
    }
    private val playbackLedger=PlaybackLedger(library,seriesTracking,{works.item(it).card},{c->works.cached(c.seriesIdentity)?.card?:c.seriesRef?.let{works.item(Card(it,c.title)).card}})
    override suspend fun beginPlayback(card:Card)=playbackLedger.begin(card)
    override suspend fun abandonPlayback(cp:PlaybackCheckpoint)=playbackLedger.abandon(cp)
    override suspend fun savePlayback(cp:PlaybackCheckpoint,position:Long,duration:Long)=playbackLedger.save(cp,position,duration)
    override suspend fun finishPlayback(cp:PlaybackCheckpoint,mode:PlaybackFinish,position:Long,duration:Long)=playbackLedger.finish(cp,mode,position,duration)
    override suspend fun cancelPlaybackFinish(cp:PlaybackCheckpoint)=playbackLedger.cancelFinish(cp)
    private val recommendationPrefs=context.applicationContext.getSharedPreferences("recommendations",Context.MODE_PRIVATE)
    private val recommendationService=RecommendationService(repository,works){settings.value.catalog()}
    val storageScope=CoroutineScope(SupervisorJob()+Dispatchers.IO.limitedParallelism(1))
    fun prioritizePlayback(active:Boolean)=repository.prioritizePlayback(active)
    override val storageWarning:Boolean get()=library.hasUnreadableRecords
    override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=repository.observeBrowse(kind,cursors)
    override fun search(query:String,cursors:Map<String,PageCursor>)=repository.observeSearch(query,cursors)
    override fun browseSite(siteId:String,kind:ContentKind,cursors:Map<String,PageCursor>)=repository.observeBrowse(kind,cursors,siteId.ifBlank{null})
    override fun searchSite(siteId:String,query:String,cursors:Map<String,PageCursor>)=repository.observeSearch(query,cursors,siteId.ifBlank{null})
    override fun filteredBrowse(siteId:String,query:CatalogQuery,cursors:Map<String,PageCursor>)=repository.observeFiltered(query,cursors,siteId.ifBlank{null})
    override fun section(siteId:String,section:CatalogSection,cursors:Map<String,PageCursor>)=repository.observeSection(section,cursors,siteId.ifBlank{null})
    override suspend fun suggestions(siteId:String,query:String)=repository.suggest(siteId,query)
    override fun pausedFor(siteId:String)=repository.pausedFor(siteId)
    override fun retrySite(siteId:String)=repository.retrySite(siteId)
    override fun invalidateCatalog()=repository.invalidatePages()
    override suspend fun catalogCacheBytes()=snapshots.bytes()
    override suspend fun clearCatalogCache(){repository.invalidatePages();snapshots.clear()}
    override suspend fun filterOptions(siteId:String):FilterOptions=coroutineScope{
        val providers=settings.value.catalog().sites.map(repository::provider).filter{siteId.isBlank()||it.site.id==siteId}
        val options=providers.map{p->async{try{withTimeoutOrNull(10000){p.options()}?:FilterOptions()}catch(e:CancellationException){throw e}catch(_:Exception){FilterOptions()}}}.awaitAll()
        FilterOptions(options.flatMap{it.values.keys}.distinct().associateWith{key->options.flatMap{it.values[key].orEmpty()}.distinctBy{if(key=="genre")GenreKey.key(it.label)else TextKey.fold(it.label)}})
    }
    private suspend fun checkVisible(card:Card){if(settings.value.hideAdult&&(AdultContent.blocked(works.item(card))||card.seriesIdentity.isNotBlank()&&works.cached(card.seriesIdentity)?.let(AdultContent::blocked)==true))throw DataFailure(FailureKind.ACCESS,"Bu içerik yetişkin filtresiyle gizleniyor","adult-filter")}
    override suspend fun detail(card:Card):Detail {val d=repository.detail(card);checkVisible(d.card);return d}
    override suspend fun episodes(card:Card):List<Episode> = episodeCatalog(card).episodes
    override suspend fun episodeCatalog(card:Card):SeriesEpisodes{checkVisible(card);val c=card.copy(workId=works.item(card).identity);val result=repository.episodeCatalog(c);seriesTracking.observe(c,result.episodes,result.complete);return result}
    override suspend fun tracking(card:Card):SeriesTracking{playbackLedger.recover();return seriesTracking.state(works.item(card).card)}
    override suspend fun followed():List<SeriesTracking>{
        playbackLedger.recover();val rows=seriesTracking.followed();return rows.mapNotNull{r->val group=works.item(r.card);if(settings.value.hideAdult&&AdultContent.blocked(group))null else{val c=group.card;if(c.workId!=r.card.workId)seriesTracking.rekey(ContentIdentity.key(r.card),c);seriesTracking.state(c)}}
    }
    override suspend fun follow(card:Card,episodes:List<Episode>,enabled:Boolean)=seriesTracking.follow(works.item(card).card,episodes,enabled)
    override suspend fun markEpisodes(card:Card,numbers:List<EpisodeNumber>,watched:Boolean)=seriesTracking.mark(works.item(card).card,numbers,watched)
    override suspend fun clearNew(card:Card)=seriesTracking.clearNew(works.item(card).card)
    override suspend fun refreshFollowing(force:Boolean):List<SeriesTracking>{
        for(r in followed().take(100))if(force||System.currentTimeMillis()-r.checkedAt>=1800000){try{episodes(r.card)}catch(e:CancellationException){throw e}catch(_:Exception){}}
        return followed()
    }
    override fun recommendations(criteria:CatalogQuery,siteId:String):Flow<RecommendationResult> = flow{
        val saved=runCatching{org.json.JSONArray(recommendationPrefs.getString("seen","[]"))}.getOrDefault(org.json.JSONArray())
        val seen=(0 until saved.length()).map{saved.getString(it)}.takeLast(30)
        val rotation=runCatching{recommendationPrefs.getInt("rotation",0)}.getOrDefault(0).coerceAtLeast(0)
        recommendationPrefs.edit().putInt("rotation",(rotation+1)%10000).apply()
        recommendationService.generate(criteria,siteId,records(),seen,rotation).collect{result->
            if(result.finished)recommendationPrefs.edit().putString("seen",org.json.JSONArray((seen+result.items.map{it.identity}).takeLast(30)).toString()).apply()
            emit(result)
        }
    }
    override suspend fun collections()=repository.collections().filter{!settings.value.hideAdult||!AdultContent.blocked(it)}
    override suspend fun collectionMatches(entry:CollectionEntry,search:Boolean):List<UnifiedItem>{
        val known=works.matches(entry).filter{!settings.value.hideAdult||!AdultContent.blocked(it)};if(known.isNotEmpty()||!search)return known
        val found=mutableListOf<UnifiedItem>();val seen=mutableSetOf<String>();var checked=0
        withTimeoutOrNull(30000){repository.observeSearch(entry.title.take(100)).takeWhile{found.isEmpty()}.collect{page->
            for(item in page.items)for(raw in item.alternatives){
                if(raw.ref.kind!=ContentKind.MOVIE||!seen.add(raw.ref.id)||checked>=12)continue
                if(entry.knownYear!=null&&raw.year!=null&&raw.year!=entry.year)continue
                checked++;val c=try{repository.detail(raw).card}catch(e:CancellationException){throw e}catch(_:Exception){continue}
                if(entry.candidate(c)){val g=works.item(c);if(!settings.value.hideAdult||!AdultContent.blocked(g))found.add(g)}
            }
        }}
        return found.distinctBy{it.identity}
    }
    override suspend fun hiddenCollectionEntry(entry:CollectionEntry)=settings.value.hideAdult&&works.matches(entry).any(AdultContent::blocked)
    override suspend fun work(card:Card)=works.item(card)
    override fun discoverAlternatives(card:Card,force:Boolean)=discovery.discover(card,force)
    override fun sourcePool(card:Card,initial:List<SourceOption>,crossSite:Boolean,gate:suspend()->Unit)=pools.observe(card,initial,crossSite,gate)
    override fun episodeSourcePool(item:UnifiedItem,crossSite:Boolean,gate:suspend()->Unit)=pools.episode(item,crossSite,gate)
    override fun refreshSourcePool(card:Card,item:UnifiedItem?,initial:List<SourceOption>,crossSite:Boolean,gate:suspend()->Unit)=
        if(item!=null)pools.episode(item,crossSite,gate,force=true)else flow{
            val local=if(settings.value.sourceAllowed(card.ref.siteId))repository.detail(card).let{detail->
                if(settings.value.hideAdult&&AdultContent.blocked(detail.card))emptyList()else detail.options
            }else emptyList()
            pools.observe(card,(local+initial).distinctBy{it.scoped().id},crossSite,gate,force=true).collect{emit(it)}
        }
    override suspend fun resolve(option:SourceOption):ResolvedSource {
        if(!settings.value.sourceAllowed(option.content.siteId))throw DataFailure(FailureKind.ACCESS,"Bu sitenin kaynakları ayarlardan kapatılmış","site-settings")
        works.sourceCard(option.content)?.let{checkVisible(it)}
        return resolver.resolve(option)
    }
    override suspend fun records():List<LibraryRecord>{playbackLedger.recover();val rows=(library.records()+library.records(true)).distinctBy{it.identity}.sortedByDescending{it.updatedAt};return canonicalRecords(rows)}
    private suspend fun canonicalRecords(rows:List<LibraryRecord>):List<LibraryRecord>{
        val groups=works.refresh(rows.map{it.card});val blockedIds=if(settings.value.hideAdult)groups.filter(AdultContent::blocked).map{it.identity}.toSet()else emptySet();val map=groups.flatMap{g->g.alternatives.map{SeriesIdentity.sourceKey(it) to it}}.toMap()
        return rows.map{r->val c=map[SeriesIdentity.sourceKey(r.card)]?:r.card;r.copy(identity=ContentIdentity.key(c),card=c.copy(ref=c.ref.copy(kind=ContentIdentity.facts(c).kind)))}.groupBy{it.identity}.values.map{g->val recent=g.maxBy{it.updatedAt};val playback=g.maxBy{it.playbackUpdatedAt};val listing=g.maxBy{it.listedUpdatedAt};recent.copy(listed=listing.listed,positionMs=playback.positionMs,durationMs=playback.durationMs,playbackUpdatedAt=playback.playbackUpdatedAt,listedUpdatedAt=listing.listedUpdatedAt)}.filter{it.identity !in blockedIds}
    }
    override suspend fun recordsPage(listedOnly:Boolean,offset:Int,limit:Int):List<LibraryRecord>{playbackLedger.recover();return canonicalRecords(library.records(listedOnly,limit,offset,historyOnly=!listedOnly))}
    override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){
        val c=works.item(card).card;library.update(c,listed,position,duration)
        if(c.ref.kind==ContentKind.EPISODE&&c.seriesRef!=null&&c.episodeNumber!=null&&duration!=null&&duration>0&&position!=null&&position>=duration*95/100){
            val parent=works.cached(c.seriesIdentity)?.card?:works.item(Card(c.seriesRef,c.title)).card
            seriesTracking.mark(parent,listOf(c.episodeNumber),true,false)
        }
    }
    override suspend fun clearSearches()=library.clearSearches()
    override suspend fun clearPlaybackHistory(){playbackLedger.recover();library.clearPlaybackHistory()}
    override suspend fun clearPlaybackPreferences()=playbackOverrides.clear()
    override suspend fun remember(query:String)=library.rememberSearch(query)
    override suspend fun searches()=library.searches()
    fun trim(){repository.trim();app.filmmerkezi.tv.updates.ModuleRuntime.trim()}
    companion object {
        @Volatile private var instance:TvServices?=null
        fun get(context:Context):TvServices=instance?:synchronized(this){instance?:run{if(TvActivity.dataFactoryForTests==null)app.filmmerkezi.tv.updates.ModuleRuntime.prepare(context);TvServices(context.applicationContext).also{instance=it}}}
    }
}

enum class TvScreen { HOME,MOVIES,SERIES,DISCOVER,SEARCH,LIBRARY,HISTORY,DETAIL,ABOUT,ANIMATION,DRAMA_SHORT,FOLLOWING;
    val section get()=when(this){ANIMATION->CatalogSection.ANIMATION;DRAMA_SHORT->CatalogSection.DRAMA_SHORT;else->null}
}
data class TvNav(val screen:TvScreen=TvScreen.HOME,val item:UnifiedItem?=null,val query:String="",val season:Int=-1,val episodePage:Int=0,val history:Boolean=false,val libraryPage:Int=0,val searchType:SearchType=SearchType.ALL,val searchPage:Int=0)
data class TvTile(val key:String,val label:String,val item:UnifiedItem?=null,val action:String="",val positionMs:Long=0,val durationMs:Long=0)
data class TvSection(val id:Long,val title:String,val tiles:List<TvTile>)

object TvPolicy {
    fun durationMinutes(value:String):Long? {
        val m=Regex("^PT(?:(\\d{1,5})H)?(?:(\\d{1,5})M)?(?:(\\d{1,5})S)?$").matchEntire(value)?:return null
        if(m.groupValues.drop(1).all{it.isBlank()})return null
        val h=m.groupValues[1].toLongOrNull()?:0;val min=m.groupValues[2].toLongOrNull()?:0
        val seconds=m.groupValues[3].toLongOrNull()?:0
        return (h*60+min+seconds/60).takeIf{it>0}
    }
    const val CATALOG_BATCH=18
    const val ROW_CARDS=CATALOG_BATCH
    const val GRID_CARDS=120
    const val EPISODE_PAGE=24
    fun continuing(records:List<LibraryRecord>)=records.filter{it.positionMs>=10000&&(it.durationMs<=0||it.positionMs<it.durationMs*95/100)}.sortedByDescending{it.playbackUpdatedAt}
    fun tile(card:Card,position:Long=0,duration:Long=0)=TvTile(ContentIdentity.key(card),ContentIdentity.displayTitle(card),UnifiedItem(ContentIdentity.key(card),card,listOf(card)),positionMs=position,durationMs=duration)
}
