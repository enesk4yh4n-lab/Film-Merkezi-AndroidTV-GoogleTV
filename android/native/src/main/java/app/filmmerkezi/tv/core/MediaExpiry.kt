package app.filmmerkezi.tv.core

import okhttp3.HttpUrl

/** Public media URLs use both Unix seconds (Mail/YouTube) and milliseconds (OK). */
object MediaExpiry {
    fun fromUrl(url:HttpUrl):Long?=sequenceOf("expires","expire","expire_at").mapNotNull{fromValue(url.queryParameter(it))}.firstOrNull()
    fun fromValue(value:String?):Long? {
        val n=value?.toLongOrNull()?.takeIf{it>0}?:return null
        return if(n<100_000_000_000L)n*1000 else n
    }
}
