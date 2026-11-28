package app.filmmerkezi.tv.updates

import org.json.JSONArray
import org.json.JSONObject

/** Validates the whole next set before switching any active package. */
object PackageSets {
    /** A publisher can rename its provider package while preserving the stable site id. */
    fun replacedProviders(set:JSONObject,next:Map<String,UpdateProtocol.Module>):Map<String,List<String>> {
        val existing=set.keys().asSequence().associateWith{UpdateProtocol.module(set.getJSONObject(it).getString("manifest").toByteArray(Charsets.UTF_8))}
        return next.filterValues{it.type=="site"}.mapValues{(key,module)->
            existing.filter{(oldKey,old)->oldKey!=key&&old.type=="site"&&old.site!!.getString("id")==module.site!!.getString("id")}.keys.onEach{oldKey->
                require(oldKey.substringBefore('/')==key.substringBefore('/')){"Bu site başka bir yayıncının eklentisiyle kurulu. Önce mevcut site eklentisini kaldırın."}
                require(oldKey !in next){"Aynı site için iki farklı eklenti seçildi."}
            }.toList()
        }
    }
    /** Provider codes define default order, independently of dependency/install order. */
    fun orderedSites(modules:Collection<UpdateProtocol.Module>) = modules.filter{it.type=="site"}
        .sortedWith(compareBy<UpdateProtocol.Module>{Regex("^p(\\d{3})$").matchEntire(it.id)?.groupValues?.get(1)?.toIntOrNull()?:Int.MAX_VALUE}.thenBy{it.id})
        .map{app.filmmerkezi.tv.core.PluginWire.site(it.site!!)}
    fun activeSites(state:JSONObject,modules:Map<String,UpdateProtocol.Module>):List<app.filmmerkezi.tv.core.SiteSpec> {
        val packages=state.getJSONObject("packages")
        val enabled=modules.keys.filter{packages.optJSONObject(it)?.optBoolean("enabled",true)==true}.toMutableSet()
        do {val unavailable=enabled.filter{key->modules.getValue(key).dependencies.any{it.publisher+"/"+it.id !in enabled}}
            enabled.removeAll(unavailable.toSet())
        } while(unavailable.isNotEmpty())
        return orderedSites(modules.filterKeys{it in enabled}.values)
    }
    fun validate(state:JSONObject, set:JSONObject, appCode:Int, api:Int):Map<String,UpdateProtocol.Module> {
        require(set.length()<=64){"64 kurulu paket sınırı"}
        val modules=set.keys().asSequence().associateWith { key ->
            val record=set.getJSONObject(key)
            val repo=state.getJSONObject("repositories").getJSONObject(record.getString("repo"))
            val raw=record.getString("manifest").toByteArray(Charsets.UTF_8)
            UpdateProtocol.verify(raw,record.getString("signature"),repo.getString("publicKey"))
            val m=UpdateProtocol.module(raw)
            require(key==repo.getString("fingerprint")+"/"+m.id && m.hash==record.getString("artifact")){"Kurulu paket kimliği uyuşmuyor"}
            require(m.minApp<=appCode&&m.minSdk<=api){"${m.name} için daha yeni uygulama/Android gerekiyor"}
            m
        }
        val siteIds=modules.values.filter{it.type=="site"}.map{it.site!!.getString("id")}
        require(siteIds.distinct().size==siteIds.size){"İki eklenti aynı site kimliğini kullanıyor"}
        modules.values.forEach { m -> m.dependencies.forEach { d ->
            val dependency=modules[d.publisher+"/"+d.id]
            require(dependency!=null&&dependency.type=="resolver"&&d.accepts(dependency.version)){"${m.name} için uyumlu ${d.id} gerekli"}
        }}
        val visiting=mutableSetOf<String>();val visited=mutableSetOf<String>()
        fun visit(key:String){if(key in visited)return;require(visiting.add(key)){"Bağımlılık döngüsü"};modules.getValue(key).dependencies.forEach{visit(it.publisher+"/"+it.id)};visiting.remove(key);visited.add(key)}
        modules.keys.forEach(::visit)
        return modules
    }

    fun rememberSites(state:JSONObject, vararg sets:JSONObject) {
        val known=linkedMapOf<String,JSONObject>()
        state.optJSONArray("knownSites")?.let{a->for(i in 0 until a.length()){val s=a.getJSONObject(i);known[s.getString("id")]=s}}
        sets.forEach{set->set.keys().forEach{key->UpdateProtocol.module(set.getJSONObject(key).getString("manifest").toByteArray()).site?.let{known[it.getString("id")]=it}}}
        state.put("knownSites",JSONArray(known.values.toList()))
    }
}
