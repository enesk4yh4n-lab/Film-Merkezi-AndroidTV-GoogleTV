package app.filmmerkezi.tv

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Only launcher discovery and resource rendering; no catalog/player requests. */
class LauncherArtworkTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val context=ins.targetContext
    private fun activity()=context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(context.packageName),0).single().activityInfo
    @Test fun tvLauncherActivityResolvesWithExplicitIconAndBanner(){
        val info=activity();assertTrue(info.exported);assertEquals("app.filmmerkezi.tv.tv.TvActivity",info.name)
        assertEquals("Film Merkezi",info.loadLabel(context.packageManager).toString())
        assertTrue(info.icon!=0&&info.banner!=0);assertNotNull(context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName))
        assertEquals(1,context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName),0).size)
    }
    private fun draw(drawable:Drawable,width:Int,height:Int):Bitmap{
        val image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);drawable.setBounds(0,0,width,height);drawable.draw(Canvas(image));return image
    }
    private fun branded(image:Bitmap){
        var colored=0
        for(y in 0 until image.height step 2)for(x in 0 until image.width step 2){val c=image.getPixel(x,y);if(Color.alpha(c)>200&&Color.red(c)>100&&Color.blue(c)>100)colored++}
        assertTrue("Logo must render colored pixels, not a blank/default tile",colored>100)
    }
    @Test fun systemLoadsAdaptiveIconAndOpaqueBitmapBanner(){
        val info=activity();val icon=info.loadIcon(context.packageManager)
        if(Build.VERSION.SDK_INT>=26)assertTrue(icon is AdaptiveIconDrawable)else assertTrue(icon is BitmapDrawable)
        val banner=context.resources.getDrawableForDensity(info.banner,DisplayMetrics.DENSITY_XHIGH,null)
        assertTrue("TV banner must be a bitmap",banner is BitmapDrawable)
        banner as BitmapDrawable;assertEquals(320,banner.bitmap.width);assertEquals(180,banner.bitmap.height)
        val iconImage=draw(icon,320,320);val bannerImage=draw(banner,320,180)
        try{
            branded(iconImage);branded(bannerImage)
            assertEquals(255,Color.alpha(bannerImage.getPixel(0,0)));assertEquals(255,Color.alpha(bannerImage.getPixel(319,179)))
            val folder=context.getExternalFilesDir(null)
            File(folder,"launcher-icon-api${Build.VERSION.SDK_INT}.png").outputStream().use{iconImage.compress(Bitmap.CompressFormat.PNG,100,it)}
            File(folder,"launcher-banner-api${Build.VERSION.SDK_INT}.png").outputStream().use{bannerImage.compress(Bitmap.CompressFormat.PNG,100,it)}
        }finally{iconImage.recycle();bannerImage.recycle()}
    }
}
