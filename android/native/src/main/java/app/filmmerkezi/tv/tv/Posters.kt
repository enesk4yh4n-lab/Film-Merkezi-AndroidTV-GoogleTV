package app.filmmerkezi.tv.tv

import android.content.Context
import android.widget.ImageView
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.Card
import app.filmmerkezi.tv.core.Sites
import com.bumptech.glide.*
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestOptions
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import java.io.InputStream
import java.util.concurrent.TimeUnit

internal object Posters {
    private var initialized=false
    private val memory=LruResourceCache(8L*1024*1024)
    private val pool=LruBitmapPool(2L*1024*1024)
    @Synchronized fun initialize(context:Context,services:TvServices) {
        if(initialized)return
        Glide.init(context.applicationContext,GlideBuilder()
            .setMemoryCache(memory)
            .setBitmapPool(pool)
            .setDiskCache(InternalCacheDiskCacheFactory(context.applicationContext,"tv-posters",24L*1024*1024))
            .setDefaultRequestOptions(RequestOptions().format(DecodeFormat.PREFER_RGB_565).disallowHardwareConfig().dontAnimate()))
        val dispatch=Dispatcher().apply{maxRequests=2;maxRequestsPerHost=2}
        val client=services.network.client.newBuilder().dispatcher(dispatch).connectionPool(ConnectionPool(2,30,TimeUnit.SECONDS))
            .callTimeout(10,TimeUnit.SECONDS).build()
        Glide.get(context).registry.replace(GlideUrl::class.java,InputStream::class.java,OkHttpUrlLoader.Factory(client))
        initialized=true
    }
    fun load(view:ImageView,card:Card?,width:Int,height:Int) {
        val model=card?.poster?.takeIf{it.startsWith("https://")}?.let{
            val site=Sites.get(card.ref.siteId)
            GlideUrl(app.filmmerkezi.tv.core.SiteAddresses.rebase(it,site),LazyHeaders.Builder().addHeader("Referer",site.base+"/").build())
        }
        Glide.with(view).asBitmap().load(model).override(width,height).centerCrop()
            .format(DecodeFormat.PREFER_RGB_565).disallowHardwareConfig()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE).dontAnimate()
            .placeholder(R.drawable.tv_poster).error(R.drawable.tv_poster).into(view)
    }
    // RecyclerView may unbind after Activity.onDestroy; use the application manager.
    // Glide.clear locates the request's owning manager even when it is another one.
    fun clear(view:ImageView){Glide.with(view.context.applicationContext).clear(view);view.setImageResource(R.drawable.tv_poster)}
    fun trim(context:Context,level:Int){if(initialized){
        if(level==android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW||
            level==android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL||
            level>=android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)Glide.get(context).clearMemory()
        else Glide.get(context).trimMemory(level)
    }}
    internal fun memoryForTest()=longArrayOf(memory.currentSize,pool.currentSize)
    suspend fun diskBytes(context:Context):Long=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){
        java.io.File(context.cacheDir,"tv-posters").walkTopDown().filter{it.isFile}.sumOf{it.length()}
    }
    suspend fun clearCache(context:Context){
        if(initialized){
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main){Glide.get(context).clearMemory()}
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){Glide.get(context).clearDiskCache()}
        }
    }
}
