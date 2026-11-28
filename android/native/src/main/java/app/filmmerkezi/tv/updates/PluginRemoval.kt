package app.filmmerkezi.tv.updates

import org.json.JSONObject

data class PluginRemoval(val key:String,val name:String,val baseHash:String,val unused:List<InstalledModule>,val shared:List<InstalledModule>)

object PluginRemovals {
    fun plan(set:JSONObject,key:String):PluginRemoval {
        val all=PluginInventory.modules(set);val root=all[key]?:error("Eklenti değişti; listeyi yeniden açın.")
        fun dependencies(start:String):Set<String>{val found=linkedSetOf<String>()
            fun walk(id:String){all[id]?.module?.dependencies.orEmpty().forEach{d->val k=d.publisher+"/"+d.id;if(found.add(k))walk(k)}}
            walk(start);return found
        }
        val users=all.values.filter{it.key!=key&&key in dependencies(it.key)}
        require(users.isEmpty()){"Bu çözümleyiciyi kullanan eklentiler var: "+users.joinToString{it.module.name}}
        val candidates=dependencies(key)
        val remaining=all.keys-key
        val roots=remaining.filter{all.getValue(it).module.type=="site"||it !in candidates}
        val used=roots.flatMap{dependencies(it)+it}.toSet()
        return PluginRemoval(key,root.module.name,UpdateProtocol.sha(set.toString().toByteArray(Charsets.UTF_8)),
            candidates.filter{it !in used}.mapNotNull(all::get).sortedBy{it.module.id},
            candidates.filter{it in used}.mapNotNull(all::get).sortedBy{it.module.id})
    }
    fun apply(set:JSONObject,plan:PluginRemoval,includeUnused:Boolean):JSONObject {
        require(UpdateProtocol.sha(set.toString().toByteArray(Charsets.UTF_8))==plan.baseHash){"Eklentiler değişti; kaldırma planını yeniden açın."}
        val checked=plan(set,plan.key)
        require(checked.unused.map{it.key}==plan.unused.map{it.key})
        return JSONObject(set.toString()).apply{remove(plan.key);if(includeUnused)plan.unused.forEach{remove(it.key)}}
    }
}
