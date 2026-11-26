package app.filmmerkezi.tv

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Protocol
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** Observed HDF Rapidrame header requirement; one bounded retry, same Media3 engine/URL.
 * The alternate origin must exist in this source's actual subtitle metadata. It is never
 * inferred for unrelated hosts, and is not forwarded to other media or subtitle hosts. */
internal object PlaybackHttpPolicy {
    internal fun mediaTransport(source:JSONObject,base:OkHttpClient,apiLevel:Int):OkHttpClient {
        val host=source.optString("url").toHttpUrlOrNull()?.host.orEmpty()
        val youtube=source.optJSONArray("playbackRequirements")?.let{a->(0 until a.length()).any{a.optString(it)=="youtube"}}==true
        // Nougat media transfers stalled over HTTP/2 in the device regression.
        // The codec/quality inventory is unchanged; cookies and the pool remain shared.
        return if(apiLevel<=25&&youtube&&(host=="googlevideo.com"||host.endsWith(".googlevideo.com")))
            base.newBuilder().protocols(listOf(Protocol.HTTP_1_1)).build()else base
    }
    fun client(source: JSONObject, base: OkHttpClient, selected: (String) -> Unit): OkHttpClient {
        val extra=source.optJSONObject("headers")
        val cookieKey=extra?.keys()?.asSequence()?.firstOrNull{it.equals("Cookie",true)}
        val explicit=(cookieKey?.let{extra?.optString(it)}?:source.optString("cookie")).orEmpty().takeIf{it.isNotBlank()&&it!="null"}
        // BridgeInterceptor would otherwise replace an explicit source Cookie with its jar.
        val mediaBase=mediaTransport(source,base,android.os.Build.VERSION.SDK_INT)
        val transport=if(explicit==null){if((source.optJSONArray("sessionCookies")?.length()?:0)>0)mediaBase.newBuilder().cookieJar(SessionCookies(source)).build()else mediaBase}else mediaBase.newBuilder().cookieJar(object:CookieJar{
            override fun loadForRequest(url:HttpUrl)=emptyList<Cookie>()
            override fun saveFromResponse(url:HttpUrl,cookies:List<Cookie>){base.cookieJar.saveFromResponse(url,cookies)}
        }).build()
        if (!source.optString("id").startsWith("rapidrame-")) return transport
        val original = source.optString("referer").toHttpUrlOrNull() ?: return transport
        val url = source.optString("url").toHttpUrlOrNull() ?: return transport
        fun cdn(host: String) = Regex("^srv[0-9]+\\.cdnimages[0-9]+\\.cyou$").matches(host)
        if (original.host != "www.hdfilmcehennemi.nl" || !cdn(url.host)) return transport
        val subtitles = source.optJSONArray("subtitles") ?: return transport
        val origin = (0 until subtitles.length()).asSequence().mapNotNull {
            subtitles.optJSONObject(it)?.optString("url")?.toHttpUrlOrNull()
        }.firstOrNull { it.scheme == "https" && it.host == "hdfilmcehennemi.mobi" } ?: return transport
        val alternate = origin.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
        val accepted = AtomicReference<String?>(null)
        return transport.newBuilder().addInterceptor { chain ->
            var request = chain.request()
            if (cdn(request.url.host)) accepted.get()?.let { request = request.newBuilder().header("Referer", it).build() }
            var response = chain.proceed(request)
            if (accepted.get() == null && cdn(request.url.host) && request.header("Referer") == original.toString() && response.code in listOf(403, 404)) {
                response.close()
                response = chain.proceed(request.newBuilder().header("Referer", alternate).build())
                if (response.isSuccessful) { accepted.set(alternate); selected(alternate) }
            }
            response
        }.build()
    }
}
