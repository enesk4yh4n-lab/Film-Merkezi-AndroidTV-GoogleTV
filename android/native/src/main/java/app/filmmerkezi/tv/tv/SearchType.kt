package app.filmmerkezi.tv.tv

import app.filmmerkezi.tv.core.*
enum class SearchType { ALL,MOVIES,SERIES,COLLECTIONS;
    fun accepts(item:UnifiedItem)=when(this){ALL->true;MOVIES->ContentIdentity.facts(item.card).kind==ContentKind.MOVIE
        SERIES->ContentIdentity.facts(item.card).kind in setOf(ContentKind.SERIES,ContentKind.EPISODE)
        COLLECTIONS->ContentIdentity.facts(item.card).kind==ContentKind.COLLECTION}
}
