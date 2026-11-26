package app.filmmerkezi.tv.core

data class CatalogRequestOptions(val merge:Boolean=true,val hideAdult:Boolean=false,val sites:List<String> = Sites.all.map{it.id}) {
    val signature get()="$merge:$hideAdult:${sites.joinToString(",")}"
}

object CatalogPresentation {
    fun project(items:List<UnifiedItem>,options:CatalogRequestOptions,refs:Set<String>?=null):List<UnifiedItem> = items.flatMap { group->
        if(options.hideAdult&&AdultContent.blocked(group))return@flatMap emptyList()
        val cards=group.alternatives.filter{it.ref.siteId in options.sites&&(refs==null||it.ref.id in refs)}.distinctBy{it.ref.id}
        if(options.merge){if(cards.isEmpty())emptyList()else listOf(group.copy(card=cards.firstOrNull{it.ref.id==group.card.ref.id}?:cards.first(),alternatives=cards,displayKey=""))}
        else cards.map{c->UnifiedItem(group.identity,c,listOf(c),"source:"+c.ref.id)}
    }
}
