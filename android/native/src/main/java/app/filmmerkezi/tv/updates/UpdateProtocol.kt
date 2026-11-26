package app.filmmerkezi.tv.updates

import org.json.JSONObject
import org.json.JSONArray
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** All signatures cover the exact downloaded UTF-8 bytes, never re-serialized JSON. */
object UpdateProtocol {
    const val PLATFORM="android"
    const val RUNTIME="kotlin-dex-v1"
    const val INDEX="filmmerkezi-android-repository.json"
    const val SDK=1
    const val INDEX_LIMIT=1024*1024
    const val MODULE_LIMIT=4*1024*1024
    fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt()and 255)}
    fun fingerprint(key:String)=sha(Base64.getDecoder().decode(key))
    fun verify(bytes:ByteArray,signature:String,key:String){
        require(signature.length<=2048){"İmza boyutu geçersiz"}
        val publicKey=KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key)))
        require((publicKey as java.security.interfaces.RSAPublicKey).modulus.bitLength()>=2048){"Yayıncı anahtarı en az RSA-2048 olmalı"}
        require(Signature.getInstance("SHA256withRSA").run{initVerify(publicKey);update(bytes);verify(Base64.getMimeDecoder().decode(signature))}){"Yayıncı imzası doğrulanamadı"}
    }
    fun id(s:String):String {require(s.matches(Regex("[a-z][a-z0-9-]{0,63}"))){"Paket kimliği geçersiz"};return s}
    fun hash(s:String):String {require(s.matches(Regex("[a-f0-9]{64}"))){"Dosya özeti geçersiz"};return s}
    fun version(s:String):String {require(s.matches(Regex("[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}"))){"Paket sürümü geçersiz"};return s}
    fun compare(a:String,b:String):Int {val x=version(a).split('.').map(String::toInt);val y=version(b).split('.').map(String::toInt);return (0..2).map{x[it].compareTo(y[it])}.firstOrNull{it!=0}?:0}
    fun path(s:String):String {require(s.length<=220&&s.matches(Regex("[A-Za-z0-9_./-]+"))&&!s.startsWith('/')&&s.split('/').all{it.isNotEmpty()&&it!="."&&it!=".."}){"Paket yolu geçersiz"};return s}
    private fun text(s:String,max:Int=200):String {require(s.isNotBlank()&&s.length<=max&&!s.any{it<' '&&it!='\n'}){"Paket metni geçersiz"};return s}
    private fun json(bytes:ByteArray,limit:Int):JSONObject {require(bytes.size<=limit){"Bildirim boyut sınırını aştı"};return JSONObject(bytes.toString(Charsets.UTF_8))}
    private fun positive(o:JSONObject,k:String)=o.getLong(k).also{require(it in 1..Int.MAX_VALUE.toLong()){"Geçersiz $k"}}.toInt()
    data class Entry(val id:String,val version:String,val type:String,val name:String,val manifest:String,val hash:String,val minApp:Int)
    data class Index(val id:String,val name:String,val publicKey:String,val revision:Int,val packages:List<Entry>)
    fun index(bytes:ByteArray):Index {
        val o=json(bytes,INDEX_LIMIT);require(o.getInt("schemaVersion")==1&&o.getString("platform")==PLATFORM&&o.getString("runtime")==RUNTIME){"Bu depo Android Kotlin eklenti deposu değil"}
        val all=o.getJSONArray("packages");require(all.length()<=512){"Depo paket sınırını aştı"}
        val entries=(0 until all.length()).map{i->val p=all.getJSONObject(i);val pid=id(p.getString("id"));val v=version(p.getString("version"));val type=p.getString("type")
            require(type in listOf("site","resolver"));val manifest=path(p.getString("manifest"));require(manifest=="packages/$pid/$v/manifest.json")
            Entry(pid,v,type,text(p.getString("name")),manifest,hash(p.getString("manifestSha256")),positive(p,"minAppVersionCode"))}
        require(entries.map{it.id+"@"+it.version}.distinct().size==entries.size){"Depoda yinelenen sürüm"}
        val key=o.getString("publicKey");require(key.length in 300..1500);fingerprint(key)
        return Index(id(o.getString("id")),text(o.getString("name")),key,positive(o,"revision"),entries)
    }
    data class Dependency(val publisher:String,val id:String,val min:String,val max:String){fun accepts(v:String)=compare(v,min)>=0&&compare(v,max)<0}
    data class Module(val id:String,val version:String,val type:String,val name:String,val minApp:Int,val minSdk:Int,val entry:String,val site:JSONObject?,val dependencies:List<Dependency>,val origins:List<String>,val artifact:String,val hash:String,val size:Int)
    fun module(bytes:ByteArray):Module {
        val o=json(bytes,65536);require(o.getInt("schemaVersion")==1&&o.getString("platform")==PLATFORM&&o.getString("runtime")==RUNTIME)
        require(o.getInt("sdkMin")<=SDK&&o.getInt("sdkMax")>SDK){"Eklenti için daha yeni bir uygulama gerekiyor"}
        val pid=id(o.getString("id"));val v=version(o.getString("version"));val type=o.getString("type");require(type in listOf("site","resolver"))
        val entry=o.getString("entryClass");require(entry.matches(Regex("app\\.filmmerkezi\\.addon\\.[A-Za-z0-9_.$]+")))
        val site=o.optJSONObject("site");require((type=="site")== (site!=null));site?.let{id(it.getString("id"));text(it.getString("name"));java.net.URI(it.getString("base")).let{u->require(u.scheme=="https"&&u.host!=null&&u.rawUserInfo==null&&u.port==-1&&(u.rawPath.isNullOrEmpty()||u.rawPath=="/"))}}
        val deps=o.optJSONArray("dependencies")?:JSONArray();require(deps.length()<=16)
        val dependencies=(0 until deps.length()).map{i->val d=deps.getJSONObject(i);Dependency(hash(d.getString("publisher")),id(d.getString("id")),version(d.getString("minVersion")),version(d.getString("maxVersion"))).also{require(compare(it.min,it.max)<0)}}
        require(dependencies.map{it.publisher+"/"+it.id}.distinct().size==dependencies.size)
        val a=o.getJSONObject("artifact");val p=path(a.getString("path"));require(p=="packages/$pid/$v/plugin.jar");val size=positive(a,"size");require(size<=MODULE_LIMIT)
        val origins=o.getJSONArray("origins");require(origins.length() in 1..256)
        val allowed=(0 until origins.length()).map{origins.getString(it).also{origin->require(origin.matches(Regex("https://(?:\\*\\.)?[a-z0-9][a-z0-9.{}-]+")))}}
        return Module(pid,v,type,text(o.getString("name")),positive(o,"minAppVersionCode"),positive(o,"minSdk"),entry,site,dependencies,allowed,p,hash(a.getString("sha256")),size)
    }
    data class Release(val code:Int,val name:String,val minSdk:Int,val url:String,val size:Int,val hash:String,val certificate:String,val notes:String)
    fun release(bytes:ByteArray):Release {
        val o=json(bytes,65536);require(o.getInt("schemaVersion")==1&&o.getString("packageName")=="app.filmmerkezi.tv")
        val size=positive(o,"size");require(size<=64*1024*1024)
        val url=o.getString("url");require(url.startsWith("https://github.com/enesk4yh4n-lab/Film-Merkezi-AndroidTV-GoogleTV/releases/download/"))
        return Release(positive(o,"versionCode"),text(o.getString("versionName"),40),positive(o,"minSdk"),url,size,hash(o.getString("sha256")),hash(o.getString("certificateSha256")),text(o.getString("notes"),4000))
    }
}
