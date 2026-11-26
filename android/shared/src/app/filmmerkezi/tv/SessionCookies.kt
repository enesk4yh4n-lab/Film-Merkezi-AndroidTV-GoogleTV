package app.filmmerkezi.tv

import okhttp3.*
import org.json.*

/** Resolver-to-player cookies stay in RAM and retain domain/path/expiry scoping. */
internal class SessionCookies(source:JSONObject):CookieJar {
    private val values=source.optJSONArray("sessionCookies")?.let{a->(0 until minOf(a.length(),64)).mapNotNull{i->runCatching{
        val o=a.getJSONObject(i);Cookie.Builder().name(o.getString("name")).value(o.getString("value")).path(o.getString("path")).expiresAt(o.getLong("expiresAt")).apply{
            if(o.getBoolean("hostOnly"))hostOnlyDomain(o.getString("domain"))else domain(o.getString("domain"));if(o.getBoolean("secure"))secure();if(o.getBoolean("httpOnly"))httpOnly()
        }.build()
    }.getOrNull()}}?.toMutableList()?:mutableListOf()
    @Synchronized override fun loadForRequest(url:HttpUrl)=values.filter{it.expiresAt>System.currentTimeMillis()&&it.matches(url)}
    @Synchronized override fun saveFromResponse(url:HttpUrl,cookies:List<Cookie>){for(c in cookies){values.removeAll{it.name==c.name&&it.domain==c.domain&&it.path==c.path};values.add(c)};values.removeAll{it.expiresAt<=System.currentTimeMillis()};while(values.size>64)values.removeAt(0)}
}
