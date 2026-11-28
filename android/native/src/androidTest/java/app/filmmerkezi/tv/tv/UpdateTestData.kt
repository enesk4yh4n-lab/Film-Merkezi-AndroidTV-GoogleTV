package app.filmmerkezi.tv.tv

import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.flow.flowOf

/** Empty metadata source used by update-only UI checks; never calls a site or player. */
internal object UpdateTestData {
    fun empty()=object:TvData {
        override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(emptyList(),emptyMap(),emptyList()))
        override fun search(query:String,cursors:Map<String,PageCursor>)=browse(ContentKind.MOVIE,cursors)
        override suspend fun detail(card:Card):Detail=error("No metadata expected")
        override suspend fun episodes(card:Card)=emptyList<Episode>()
        override suspend fun resolve(option:SourceOption):ResolvedSource=error("No playback expected")
        override suspend fun records()=emptyList<LibraryRecord>()
        override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){}
        override suspend fun remember(query:String){}
        override suspend fun searches()=emptyList<String>()
    }
}
