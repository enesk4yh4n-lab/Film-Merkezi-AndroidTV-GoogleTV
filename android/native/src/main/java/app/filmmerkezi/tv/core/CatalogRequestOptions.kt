package app.filmmerkezi.tv.core

enum class MatchingMode { STRICT, SMART, FLEXIBLE }

data class CatalogRequestOptions(val merge:Boolean=true,val hideAdult:Boolean=false,val sites:List<String> = Sites.all.map{it.id},val mode:MatchingMode=MatchingMode.SMART) {
    val signature get()="$merge:$hideAdult:$mode:${sites.joinToString(",")}"
}

object CatalogPresentation {
    fun project(items:List<UnifiedItem>,options:CatalogRequestOptions,refs:Set<String>?=null):List<UnifiedItem> = items.flatMap { group->
        if(options.hideAdult&&AdultContent.blocked(group))return@flatMap emptyList()
        val cards=group.alternatives.filter{it.ref.siteId in options.sites&&(refs==null||it.ref.id in refs)}.distinctBy{it.ref.id}
        val partitions=when{
            !options.merge->cards.map(::listOf)
            options.mode==MatchingMode.STRICT->cards.groupBy{card->
                val facts=ContentIdentity.facts(card)
                if(facts.imdb.isNotEmpty()&&facts.evidence?.imdbConflict!=true&&card.ref.kind in setOf(ContentKind.MOVIE,ContentKind.SERIES))
                    "imdb:${card.ref.kind}:${facts.imdb}" else "ref:"+card.ref.id
            }.values.toList()
            else->listOf(cards)
        }
        partitions.filter{it.isNotEmpty()}.map{partition->
            val card=partition.firstOrNull{it.ref.id==group.card.ref.id}?:partition.first()
            group.copy(card=card,alternatives=partition,displayKey=if(!options.merge||options.mode==MatchingMode.STRICT)"source:"+card.ref.id else "")
        }
    }

    fun possible(anchor:Card,items:List<UnifiedItem>):List<UnifiedItem> {
        if(anchor.ref.kind!=ContentKind.MOVIE&&anchor.ref.kind!=ContentKind.SERIES)return emptyList()
        return items.asSequence().filter{it.card.ref.id!=anchor.ref.id&&it.card.ref.kind==anchor.ref.kind}
            .filter{ContentIdentity.likelyRelated(anchor,it.card)&&ContentIdentity.decide(anchor,it.card).state==IdentityState.UNCERTAIN}
            .distinctBy{it.identity}.take(8).toList()
    }
}
