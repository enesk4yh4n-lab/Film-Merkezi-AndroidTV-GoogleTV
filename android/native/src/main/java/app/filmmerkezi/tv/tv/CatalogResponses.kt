package app.filmmerkezi.tv.tv

import app.filmmerkezi.tv.core.SiteFailure
import app.filmmerkezi.tv.core.UnifiedPage

internal class CatalogResponses(
    val installed:List<String>,
    val eligible:Map<String,List<TvScreen>>,
    private val pages:Map<TvScreen,UnifiedPage>,
){
    data class Answer(val counts:Int?,val failure:SiteFailure?,val partial:Boolean,val complete:Boolean)

    fun answer(id:String):Answer {
        val requests=eligible[id].orEmpty()
        val received=requests.mapNotNull(pages::get)
        val counts=received.mapNotNull{it.siteCounts[id]}
        val failure=received.firstNotNullOfOrNull{page->page.failures.firstOrNull{it.siteId==id}}
        return Answer(
            counts.takeIf{it.isNotEmpty()}?.sum(),failure,
            received.any{page->page.coverage.any{it.siteId==id&&it.partial}},
            requests.isNotEmpty()&&requests.all{screen->
                pages[screen]?.let{page->id in page.siteCounts||page.failures.any{it.siteId==id}}==true
            },
        )
    }
    val completed get()=eligible.keys.count{answer(it).complete}
}
