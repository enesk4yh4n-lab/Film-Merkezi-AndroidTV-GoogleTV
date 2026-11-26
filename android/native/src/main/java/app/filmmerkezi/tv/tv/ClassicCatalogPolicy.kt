package app.filmmerkezi.tv.tv

import app.filmmerkezi.tv.core.*

enum class ClassicSort { DEFAULT,NEWEST,RATING,TITLE }
data class ClassicFilter(val yearFrom:Int=0,val minimumRating:Double=0.0,val language:String="",val genre:String="")
object ClassicCatalogPolicy {
    fun apply(items:List<UnifiedItem>,filter:ClassicFilter,sort:ClassicSort):List<UnifiedItem>{
        val filtered=items.filter{item->item.alternatives.any{c->
            (filter.yearFrom==0||c.year==filter.yearFrom)&&
                (filter.minimumRating==0.0||(c.imdb?:0.0)>=filter.minimumRating)&&
                (filter.language.isBlank()||TextKey.fold(c.language).contains(filter.language))&&
                (filter.genre.isBlank()||c.genres.any{GenreKey.key(it)==GenreKey.key(filter.genre)})
        }}
        return when(sort){ClassicSort.DEFAULT->filtered;ClassicSort.NEWEST->filtered.sortedByDescending{it.card.year?:0}
            ClassicSort.RATING->filtered.sortedByDescending{it.card.imdb?:0.0};ClassicSort.TITLE->filtered.sortedBy{TextKey.fold(it.card.title)}}
    }
    fun recommendations(items:List<UnifiedItem>,records:List<LibraryRecord>,filter:ClassicFilter)=
        apply(items,filter,ClassicSort.RATING).filter{it.card.ref.kind==ContentKind.MOVIE&&records.none{r->r.positionMs>0&&ContentIdentity.equivalent(r.card,it.card)}}.take(3)
}
