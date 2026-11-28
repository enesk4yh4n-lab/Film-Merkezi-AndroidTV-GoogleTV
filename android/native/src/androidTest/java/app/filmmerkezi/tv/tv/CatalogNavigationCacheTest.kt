package app.filmmerkezi.tv.tv

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogNavigationCacheTest {
    @Test fun returningToCatalogReusesRunningAndCompletedPages()=runBlocking{
        val fixture=Settings110Fixture()
        val base=RemoteAuditData().apply{includeRecords=false}
        val initialAnimation=CompletableDeferred<Unit>()
        val installed=Sites.all
        Sites.installed(Sites.defaults.filter{it.id in setOf("filmcibaba","diziboxum")})
        var homeMovies=0;var homeSeries=0;var movieLoads=0;var animationLoads=0;var dramaLoads=0
        var detailLoads=0
        val animation=base.movies[0];val drama=base.series
        val data=object:TvData by base{
            override fun browseSite(siteId:String,kind:ContentKind,cursors:Map<String,PageCursor>):Flow<UnifiedPage> {
                if(kind==ContentKind.MOVIE)homeMovies++ else homeSeries++
                return base.browse(kind,cursors)
            }
            override fun filteredBrowse(siteId:String,query:CatalogQuery,cursors:Map<String,PageCursor>):Flow<UnifiedPage>{
                movieLoads++;return base.browse(query.kind,cursors)
            }
            override fun section(siteId:String,section:CatalogSection,cursors:Map<String,PageCursor>)=flow{
                val card=if(section==CatalogSection.ANIMATION){animationLoads++;initialAnimation.await();animation}else{dramaLoads++;drama}
                emit(UnifiedPage(listOf(base.item(card)),emptyMap(),emptyList(),completedSites=1,requestedSites=1))
            }
            override suspend fun detail(card:Card):Detail {detailLoads++;return base.detail(card)}
        }
        fun click(id:Int)=fixture.main{fixture.activity.findViewById<View>(id).performClick()}
        try{
            fixture.start(customData=data)
            fixture.waitFor{homeMovies==1&&homeSeries==1&&fixture.tag(animation.workId)!=null}
            click(R.id.tv_nav_animation);fixture.waitFor{animationLoads==1}
            click(R.id.tv_nav_movies);fixture.waitFor{fixture.tag(animation.workId)!=null}
            click(R.id.tv_nav_animation)
            initialAnimation.complete(Unit)
            fixture.waitFor{fixture.tag(animation.workId)!=null}
            fixture.main{fixture.tag(animation.workId).performClick()}
            fixture.waitFor{detailLoads==1&&fixture.activity.findViewById<View>(R.id.tv_play_action)!=null}
            fixture.ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            fixture.waitFor{fixture.tag(animation.workId)!=null}
            click(R.id.tv_nav_drama_short);fixture.waitFor{fixture.tag(drama.workId)!=null}
            click(R.id.tv_nav_home);fixture.waitFor{fixture.tag(animation.workId)!=null}
            click(R.id.tv_nav_movies);fixture.waitFor{fixture.tag(animation.workId)!=null}
            click(R.id.tv_nav_drama_short);fixture.waitFor{fixture.tag(drama.workId)!=null}
            fixture.main{assertEquals(listOf(1,1,0,1,1),listOf(homeMovies,homeSeries,movieLoads,animationLoads,dramaLoads))}
            fixture.main{fixture.tag("catalog:responses").performClick();fixture.activity.classicOverlayForTest()!!.select("refresh")}
            fixture.waitFor{dramaLoads==2&&fixture.tag(drama.workId)!=null}
            fixture.main{assertEquals(listOf(1,1,0,1,2),listOf(homeMovies,homeSeries,movieLoads,animationLoads,dramaLoads))}
        }finally{
            initialAnimation.complete(Unit);fixture.close();Sites.installed(installed)
        }
    }

    @Test fun pendingHomeReadFeedsMovieGridWithoutMovingFocusAndMoreUsesLocalCards()=runBlocking{
        val fixture=Settings110Fixture()
        val installed=Sites.all
        Sites.installed(Sites.defaults.filter{it.id=="filmcibaba"})
        val base=RemoteAuditData().apply{includeRecords=false}
        val late=CompletableDeferred<Unit>()
        val cursor=PageCursor("filmcibaba","https://filmizle.ink/page/2/")
        val cards=(1..60).map{i->
            val card=Card(ContentRef("filmcibaba","https://filmizle.ink/performance-$i/",ContentKind.MOVIE),"Film $i",workId="performance:$i")
            base.item(card)
        }
        var first=0;var next=0
        val data=object:TvData by base{
            override fun browseSite(siteId:String,kind:ContentKind,cursors:Map<String,PageCursor>)=flow {
                if(kind!=ContentKind.MOVIE){emit(UnifiedPage(emptyList(),emptyMap(),emptyList()));return@flow}
                if(cursors.isEmpty()){
                    first++
                    emit(UnifiedPage(cards.take(7),emptyMap(),emptyList(),completedSites=1,requestedSites=2))
                    late.await()
                    emit(UnifiedPage(cards.take(54),mapOf(cursor.siteId to cursor),emptyList(),completedSites=2,requestedSites=2))
                }else{
                    next++
                    emit(UnifiedPage(cards.drop(54),emptyMap(),emptyList(),mapOf(cursor.siteId to 6),1,1))
                }
            }
        }
        fun count():Int {
            val grid=fixture.activity.supportFragmentManager.findFragmentById(R.id.tv_content) as TvGridFragment
            return checkNotNull(grid.adapter).size()
        }
        try{
            fixture.start(customData=data)
            fixture.waitFor{fixture.tag("performance:1")!=null}
            fixture.main{
                assertNull(fixture.tag("home-row:2:previous"))
                fixture.activity.findViewById<View>(R.id.tv_nav_movies).performClick()
            }
            fixture.waitFor{count()==7}
            fixture.main{assertTrue(fixture.activity.findViewById<View>(R.id.tv_nav_movies).hasFocus())}
            late.complete(Unit)
            fixture.waitFor{count()==18&&fixture.tag("feed:action")?.isShown==true}
            fixture.main{assertEquals(1,first);assertTrue(fixture.activity.findViewById<View>(R.id.tv_nav_movies).hasFocus())}
            for(expected in listOf(36,54,60)){
                fixture.main{fixture.tag("feed:action").performClick()}
                fixture.waitFor{count()==expected}
                fixture.main{assertEquals(if(expected==60)1 else 0,next)}
            }
            fixture.screenshot("catalog-18-batch.png")
        }finally{late.complete(Unit);fixture.close();Sites.installed(installed)}
    }

    @Test fun categoryExplainsUnsupportedFailedAndEmptyResults()=runBlocking{
        val fixture=Settings110Fixture()
        val installed=Sites.all
        val base=RemoteAuditData().apply{includeRecords=false}
        Sites.installed(Sites.defaults.filter{it.id in setOf("filmcibaba","diziboxum")})
        var result=UnifiedPage(emptyList(),emptyMap(),emptyList())
        val data=object:TvData by base{
            override fun section(siteId:String,section:CatalogSection,cursors:Map<String,PageCursor>)=flowOf(result)
        }
        fun contains(view:View,expected:String):Boolean = view is TextView&&view.text.toString()==expected||
            view is ViewGroup&&(0 until view.childCount).any{contains(view.getChildAt(it),expected)}
        suspend fun expect(message:Int)=fixture.waitFor{contains(fixture.activity.window.decorView,fixture.activity.getString(message))}
        fun refresh(){fixture.main{fixture.tag("catalog:responses").performClick();fixture.activity.classicOverlayForTest()!!.select("refresh")}}
        try{
            fixture.start(customData=data)
            fixture.main{fixture.activity.findViewById<View>(R.id.tv_nav_animation).performClick()}
            expect(R.string.catalog_section_unavailable)
            fixture.main{result=UnifiedPage(emptyList(),emptyMap(),listOf(SiteFailure("filmcibaba",FailureKind.ACCESS,"HTTP 403",403)),completedSites=1,requestedSites=1)}
            refresh();expect(R.string.catalog_section_failed)
            fixture.main{result=UnifiedPage(emptyList(),emptyMap(),emptyList(),mapOf("filmcibaba" to 0),1,1)}
            refresh();expect(R.string.catalog_section_empty)
        }finally{fixture.close();Sites.installed(installed)}
    }
}
