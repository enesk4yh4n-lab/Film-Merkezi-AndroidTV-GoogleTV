package app.filmmerkezi.tv.core

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import okio.ByteString.Companion.decodeBase64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Bounded data reader, not a JavaScript interpreter. Function calls are rejected. */
object Literals {
    data class Read(val value: Any?, val end: Int)
    fun read(input: String, start: Int, depth: Int = 0): Read {
        if(input.length>4*1024*1024 || depth>50) throw DataFailure(FailureKind.LIMIT,"Veri ayrıştırma sınırı","literal")
        var i=start
        while(i<input.length && input[i].isWhitespace())i++
        if(i>=input.length) throw DataFailure(FailureKind.PARSE,"Eksik sabit veri","literal")
        val quote=input[i]
        if(quote=='\'' || quote=='"') {
            val result=StringBuilder();i++
            while(i<input.length) {
                var c=input[i++]
                if(c==quote)return Read(result.toString(),i)
                if(c=='\\') {
                    if(i>=input.length)break
                    c=input[i++]
                    when(c) {
                        'x','u' -> { val n=if(c=='x')2 else 4; val hex=input.substring(i,(i+n).coerceAtMost(input.length));if(hex.length!=n||!hex.all { it.isDigit()||it.lowercaseChar() in 'a'..'f' })throw DataFailure(FailureKind.PARSE,"Geçersiz veri kaçışı","literal");result.append(hex.toInt(16).toChar());i+=n }
                        'n'->result.append('\n');'r'->result.append('\r');'t'->result.append('\t');'b'->result.append('\b');'f'->result.append('\u000c');'v'->result.append('\u000b')
                        '\\','/','\'','"'->result.append(c)
                        '\n'->Unit
                        else->throw DataFailure(FailureKind.PARSE,"Desteklenmeyen veri kaçışı","literal")
                    }
                } else result.append(c)
                if(result.length>1024*1024)throw DataFailure(FailureKind.LIMIT,"Sabit dize boyut sınırı","literal")
            }
            throw DataFailure(FailureKind.PARSE,"Eksik veri dizesi","literal")
        }
        if(quote=='[' || quote=='{') {
            val array=quote=='['; val close=if(array)']' else '}'; val list=JSONArray();val obj=JSONObject();i++
            var count=0
            while(i<input.length) {
                while(i<input.length && (input[i].isWhitespace() || input[i]==','))i++
                if(i<input.length && input[i]==close)return Read(if(array)list else obj,i+1)
                if(++count>10000)throw DataFailure(FailureKind.LIMIT,"Sabit veri öğe sınırı","literal")
                if(array) { val r=read(input,i,depth+1);list.put(r.value?:JSONObject.NULL);i=r.end }
                else {
                    val key=if(input.getOrNull(i)=='\''||input.getOrNull(i)=='"') read(input,i,depth+1)
                        else Regex("[A-Za-z_$][A-Za-z0-9_$]*").find(input,i)?.takeIf { it.range.first==i }?.let { Read(it.value,it.range.last+1) }
                            ?:throw DataFailure(FailureKind.PARSE,"Geçersiz nesne anahtarı","literal")
                    i=key.end;while(input.getOrNull(i)?.isWhitespace()==true)i++
                    if(input.getOrNull(i++)!=':')throw DataFailure(FailureKind.PARSE,"Eksik veri ayıracı","literal")
                    val r=read(input,i,depth+1);obj.put(key.value.toString(),r.value?:JSONObject.NULL);i=r.end
                }
            }
            throw DataFailure(FailureKind.PARSE,"Tamamlanmamış sabit veri","literal")
        }
        val token=Regex("(?:true|false|null|-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)").find(input,i)?.takeIf { it.range.first==i }
            ?:throw DataFailure(FailureKind.PARSE,"Çalıştırılabilir ifade sabit veri değildir","literal")
        return Read(when(token.value) { "true"->true;"false"->false;"null"->JSONObject.NULL;else->token.value.toDouble() },token.range.last+1)
    }
    fun after(input:String, pattern:String):Any? { val m=Regex(pattern).find(input)?:return null;return read(input,m.range.last+1).value }
    fun base64(value:String):ByteArray {
        if(value.length>2*1024*1024 || !Regex("^[A-Za-z0-9+/_-]*={0,2}$").matches(value))throw DataFailure(FailureKind.PARSE,"Geçersiz base64","decode")
        return value.replace('-','+').replace('_','/').decodeBase64()?.toByteArray()
            ?:throw DataFailure(FailureKind.PARSE,"Base64 çözülemedi","decode")
    }
    fun b64(value:String)=base64(value).toString(Charsets.UTF_8)
    fun hex(value:String):ByteArray {
        require(value.length%2==0 && value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
        return ByteArray(value.length/2) { value.substring(it*2,it*2+2).toInt(16).toByte() }
    }
    /** OpenSSL/CryptoJS passphrase AES-256-CBC, EVP_BytesToKey MD5. */
    fun aesPassword(cipher:String,password:String):String {
        val json=runCatching { JSONObject(cipher) }.getOrNull()
        val decoded=if(json==null)base64(cipher) else base64(json.getString("ct"))
        val salt=if(json!=null)hex(json.getString("s")) else {
            if(decoded.size<16 || decoded.copyOfRange(0,8).toString(Charsets.ISO_8859_1)!="Salted__")throw DataFailure(FailureKind.PARSE,"Şifreli veri tuzu eksik","decode")
            decoded.copyOfRange(8,16)
        }
        val cipherBytes=if(json!=null)decoded else decoded.copyOfRange(16,decoded.size)
        var previous=ByteArray(0);var material=ByteArray(0)
        while(material.size<48) { previous=MessageDigest.getInstance("MD5").digest(previous+password.toByteArray(Charsets.UTF_8)+salt);material+=previous }
        val iv=if(json!=null && json.has("iv"))hex(json.getString("iv")) else material.copyOfRange(32,48)
        return Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE,SecretKeySpec(material.copyOfRange(0,32),"AES"),IvParameterSpec(iv))
            doFinal(cipherBytes).toString(Charsets.UTF_8)
        }
    }
    fun unpack(input:String):String {
        val decoded=Regex("\\beval\\(function\\(p,a,c,k,e,[dr]\\)[\\s\\S]*?return p\\}\\(").findAll(input).take(8).map { marker ->
        val payload=read(input,marker.range.last+1)
        val tail=Regex("^\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*").find(input.substring(payload.end))
            ?:throw DataFailure(FailureKind.PARSE,"Paketli veri sözlüğü eksik","decode")
        val radix=tail.groupValues[1].toInt();val count=tail.groupValues[2].toInt()
        if(radix !in 2..62 || count !in 1..10000)throw DataFailure(FailureKind.LIMIT,"Paketli veri sözlük sınırı","decode")
        val dictionary=read(input,payload.end+tail.value.length).value.toString().split('|')
        if(dictionary.size!=count)throw DataFailure(FailureKind.PARSE,"Paketli veri sözlüğü bozuk","decode")
        Regex("\\b[0-9A-Za-z]+\\b").replace(payload.value.toString()) { match ->
            var index=0L
            for(c in match.value) { val n=when(c){in '0'..'9'->c-'0';in 'a'..'z'->c-'a'+10;else->c-'A'+36}
                if(n>=radix || index>=count){ index=-1;break };index=index*radix+n }
            if(index in 0 until count.toLong() && dictionary[index.toInt()].isNotEmpty()) dictionary[index.toInt()] else match.value
        }
        }.toList()
        return if(decoded.isEmpty())input else (listOf(input)+decoded).joinToString("\n")
    }
}

fun JSONArray.values():List<Any?> = (0 until length()).map { opt(it) }
fun JSONObject.string(key:String):String = opt(key)?.takeUnless { it===JSONObject.NULL }?.toString()?:""
fun findJson(root:Any?, predicate:(JSONObject)->Boolean):JSONObject? {
    var visited=0
    fun walk(value:Any?,depth:Int):JSONObject? {
        if(depth>60 || ++visited>50000)return null
        if(value is JSONObject) { if(predicate(value))return value
            for(key in value.keys()) { val found=walk(value.opt(key),depth+1);if(found!=null)return found } }
        if(value is JSONArray) for(v in value.values()){val found=walk(v,depth+1);if(found!=null)return found}
        return null
    }
    return walk(root,0)
}
