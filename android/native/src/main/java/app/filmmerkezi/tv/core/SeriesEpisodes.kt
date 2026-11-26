package app.filmmerkezi.tv.core

object EpisodeMerge {
    /** Filmci Baba's numeric WordPress parts do not independently prove episode numbers. */
    fun explicit(e:Episode)=e.card.ref.siteId!="filmcibaba"||EpisodeNumbers.episodeNumber("",e.card.ref.pageUrl)==e.number
    fun combine(groups:List<List<Episode>>,parent:String,preferredSite:String,failures:List<SiteFailure> = emptyList()):SeriesEpisodes {
        val all=groups.flatten()
        val verified=all.filter(::explicit).groupBy{it.number.season}.mapValues{(_,v)->v.map{it.number.number}.toSet()}
        val usable=all.filter{e->explicit(e)||verified[e.number.season]?.let{e.number.number in it}!=false}
        val rows=usable.groupBy{it.number}.map{(number,values)->
            val chosen=values.maxBy{(if(explicit(it))4 else 0)+(if(it.card.ref.siteId==preferredSite)2 else 0)}
            fun attach(c:Card,series:ContentRef)=c.copy(workId="",seriesIdentity=parent,seriesRef=series,episodeNumber=number)
            val cards=values.flatMap{listOf(attach(it.card,it.series))+it.alternatives.map{c->attach(c,c.seriesRef?:it.series)}}.distinctBy{SeriesIdentity.sourceKey(it)}
            chosen.copy(card=attach(chosen.card,chosen.series),alternatives=cards)
        }.sortedWith(compareBy({it.number.season},{it.number.number}))
        return SeriesEpisodes(rows,failures,all.size-usable.size)
    }
}
