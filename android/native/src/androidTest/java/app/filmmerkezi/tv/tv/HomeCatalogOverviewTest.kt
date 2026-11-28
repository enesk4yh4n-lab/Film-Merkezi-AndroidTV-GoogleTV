package app.filmmerkezi.tv.tv

import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.leanback.widget.ListRow
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.settings.AppSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HomeCatalogOverviewTest {
    @Test fun installedCodesRemainVisibleAndHomeSharesBothCategoryLoads()=runBlocking{
        val fixture=Settings110Fixture()
        val base=RemoteAuditData().apply{includeRecords=false}
        val previous=Sites.all
        val previousMetadata=previous.associate{site->site.id to CatalogPluginMetadata(
            Sites.catalogRevision(site.id),Sites.declaredSections(site.id),Sites.catalogPackageId(site.id))}
        val installed=(1..17).map{i->
            SiteSpec("fixture-$i","Site display name $i","https://site-$i.example","/",
                if(i<=12)"/films"else"/series","a","h2",seriesPath=if(i>=8)"/series"else null)
        }
        val animation=CatalogSection.ANIMATION
        val drama=CatalogSection.DRAMA_SHORT
        val course=mapOf(animation to installed[0].id,drama to installed[12].id)
        val metadata=installed.mapIndexed{i,site->
            site.id to CatalogPluginMetadata("fixture",course.filterValues{it==site.id}.mapValues{
                listOf(SectionRoute("/section",setOf(ContentKind.SERIES)))
            },"p${(i+1).toString().padStart(3,'0')}")
        }.toMap()
        val release=CompletableDeferred<Unit>()
        val reads=mutableMapOf<CatalogSection,Int>()
        Sites.installed(installed,metadata)
        fun page(ids:List<String>,items:List<UnifiedItem> = emptyList())=
            UnifiedPage(items,emptyMap(),emptyList(),ids.associateWith{id->items.count{it.card.ref.siteId==id}},ids.size,ids.size)
        val data=object:TvData by base{
            override fun browseSite(siteId:String,kind:ContentKind,cursors:Map<String,PageCursor>)=flow{
                emit(page(installed.filter{it.supports(kind)&&(siteId.isEmpty()||it.id==siteId)}.map{it.id}))
            }
            override fun section(siteId:String,section:CatalogSection,cursors:Map<String,PageCursor>)=flow{
                reads[section]=(reads[section]?:0)+1
                if(section==drama)release.await()
                val id=course.getValue(section)
                val site=Sites.get(id)
                val card=Card(ContentRef(id,site.base+"/catalog-item/",ContentKind.SERIES),
                    "Fixture ${section.name}",workId="work:${section.name}")
                emit(page(listOf(id),listOf(base.item(card))))
            }
        }
        fun click(id:Int)=fixture.main{fixture.activity.findViewById<View>(id).performClick()}
        fun showResponses()=fixture.main{fixture.tag("catalog:responses").performClick()}
        fun closeOverlay()=fixture.ins.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        fun overlay()=checkNotNull(fixture.activity.classicOverlayForTest())
        fun summary(inScope:Int,done:Int)=fixture.activity.getString(R.string.catalog_response_summary,17,inScope,done)
        fun shelf(id:Long):Pair<TvRowsFragment,ListRow> {
            val fragment=fixture.activity.supportFragmentManager.findFragmentById(R.id.tv_content) as TvRowsFragment
            val adapter=checkNotNull(fragment.adapter)
            return fragment to (0 until adapter.size()).map{adapter[it] as ListRow}.single{it.headerItem.id==id}
        }
        fun shelfKeys(id:Long):List<String>{
            val row=shelf(id).second
            return (0 until row.adapter.size()).map{(row.adapter[it] as TvTile).key}
        }
        try{
            fixture.start(AppSettings(showPosters=false),data)
            fixture.waitFor{reads[drama]==1&&shelfKeys(7).contains("work:ANIMATION")}
            click(R.id.tv_site_picker)
            fixture.main{
                assertEquals(18,overlay().titles().size)
                assertEquals("P004",overlay().titles()[4])
                assertTrue(overlay().titles().drop(1).all{it.matches(Regex("P[0-9]{3}"))})
            }
            closeOverlay()
            showResponses()
            fixture.waitFor{overlay().messageForTest()==summary(17,16)}
            fixture.main{assertEquals(17,overlay().titles().size);assertEquals("P017",overlay().titles().last())}
            release.complete(Unit)
            fixture.waitFor{overlay().messageForTest()==summary(17,17)}
            fixture.screenshot("home-site-responses.png")
            closeOverlay()
            fixture.waitFor{shelfKeys(8).contains("work:DRAMA_SHORT")}
            fixture.main{
                val (fragment,_)=shelf(7)
                fragment.restore(7,"more:animation")
                fragment.focusContent()
            }
            fixture.waitFor{fixture.tag("more:animation")?.isShown==true}
            fixture.screenshot("home-category-shelves.png")
            fixture.main{fixture.tag("more:animation").performClick()}
            fixture.waitFor{fixture.activity.snapshotForUiTest().getString("screen")=="ANIMATION"&&fixture.tag("work:ANIMATION")!=null}
            showResponses()
            fixture.waitFor{overlay().messageForTest()==summary(1,1)}
            fixture.main{
                assertEquals(17,overlay().rowKeys().size)
                assertEquals(1,reads[animation])
                assertEquals(1,reads[drama])
                overlay().focus(installed[1].id)
            }
            fixture.waitFor{overlay().navigationRootForTest().findViewWithTag<View>(installed[1].id)?.contentDescription
                ?.contains(fixture.activity.getString(R.string.catalog_site_unsupported))==true}
            closeOverlay()
            click(R.id.tv_nav_movies)
            showResponses()
            fixture.waitFor{overlay().messageForTest()==summary(12,12)}
            closeOverlay()
            click(R.id.tv_nav_series)
            showResponses()
            fixture.waitFor{overlay().messageForTest()==summary(10,10)}
            closeOverlay()
            fixture.main{
                val status=fixture.activity.findViewById<TextView>(R.id.tv_site_status)
                assertTrue(status.text.contains(summary(10,10)))
            }
        }finally{
            release.complete(Unit)
            fixture.close()
            Sites.installed(previous,previousMetadata)
        }
    }
}
