package app.filmmerkezi.tv.tv

import app.filmmerkezi.tv.core.UnifiedItem

/** Keep loaded cards in place while the remote is browsing; append newly returned works. */
internal fun stableContentOrder(previous:List<UnifiedItem>,incoming:List<UnifiedItem>):List<UnifiedItem>{
    val index=HashMap<String,Int>()
    incoming.forEachIndexed{i,item->index[item.displayKey.ifBlank{item.identity}]=i;item.alternatives.forEach{index[it.ref.id]=i}}
    val used=HashSet<Int>()
    return buildList{
        previous.forEach{old->val i=index[old.displayKey.ifBlank{old.identity}]?:old.alternatives.firstNotNullOfOrNull{index[it.ref.id]}
            if(i!=null&&used.add(i))add(incoming[i])}
        incoming.forEachIndexed{i,item->if(used.add(i))add(item)}
    }
}
