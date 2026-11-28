package app.filmmerkezi.tv.settings

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal class AdultPin(context:Context,private val now:()->Long=System::currentTimeMillis) {
    private val store=context.applicationContext.getSharedPreferences("adult-pin",0)
    val configured get()=store.contains("digest")
    val locked get()=store.getLong("lockedUntil",0L)>now()

    enum class Result { ACCEPTED, WRONG, LOCKED }

    private fun encode(value:ByteArray)=Base64.encodeToString(value,Base64.NO_WRAP)
    private fun derive(pin:String,salt:ByteArray):ByteArray {
        require(pin.matches(Regex("[0-9]{4}")))
        val spec=PBEKeySpec(pin.toCharArray(),salt,75000,256)
        return try{SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded}
        finally{spec.clearPassword()}
    }

    fun replace(pin:String)=synchronized(lock) {
        val salt=ByteArray(16).also{SecureRandom().nextBytes(it)}
        check(store.edit().putString("salt",encode(salt)).putString("digest",encode(derive(pin,salt)))
            .remove("attempts").remove("lockedUntil").commit())
    }

    fun verify(pin:String):Result=synchronized(lock) {
        if(locked)return@synchronized Result.LOCKED
        val salt=Base64.decode(store.getString("salt",""),Base64.NO_WRAP)
        val expected=Base64.decode(store.getString("digest",""),Base64.NO_WRAP)
        val accepted=configured&&pin.matches(Regex("[0-9]{4}"))&&MessageDigest.isEqual(expected,derive(pin,salt))
        val attempts=if(store.getLong("lockedUntil",0L)>0L)0 else store.getInt("attempts",0)
        val edit=store.edit()
        val result=when{
            accepted->{edit.remove("attempts").remove("lockedUntil");Result.ACCEPTED}
            attempts>=4->{edit.remove("attempts").putLong("lockedUntil",now()+300_000L);Result.LOCKED}
            else->{edit.putInt("attempts",attempts+1).remove("lockedUntil");Result.WRONG}
        }
        check(edit.commit())
        result
    }

    companion object { private val lock=Any() }
}
