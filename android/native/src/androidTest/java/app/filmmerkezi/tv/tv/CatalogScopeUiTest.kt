package app.filmmerkezi.tv.tv

import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class CatalogScopeUiTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private fun main(block:()->Unit)=ins.runOnMainSync(block)
    private fun waitFor(test:()->Boolean){val until=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<until){var ok=false;main{ok=test()};if(ok)return;SystemClock.sleep(50)};error("Site UI timeout")}
    @Test fun pickerScopesHomeSearchAndNavigationAndKeepsCommonLibrary(){
        val prefs=ins.targetContext.getSharedPreferences("catalog-preferences",0);val old=prefs.all["siteId"]as? String
        prefs.edit().clear().commit();val fake=Fake();TvActivity.dataFactoryForTests={fake}
        val activity=ins.startActivitySync(Intent(ins.targetContext,TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))as TvActivity
        fun choose(index:Int){
            main{activity.findViewById<View>(R.id.tv_site_picker).performClick()};ins.waitForIdleSync()
            main{val dialog=checkNotNull(activity.classicOverlayForTest());assertEquals(Sites.all.size+1,dialog.rowKeys().size)
                dialog.select(dialog.rowKeys()[index])};ins.waitForIdleSync()
        }
        try{
            waitFor{fake.scopes.contains("")}
            choose(Sites.all.indexOfFirst{it.id=="filmcibaba"}+1)
            waitFor{fake.scopes.contains("filmcibaba")}
            assertEquals("filmcibaba",CatalogPreferences(ins.targetContext).siteId)
            main{assertTrue(activity.findViewById<View>(R.id.tv_nav_movies).isShown);assertNotNull(activity.currentFocus)}
            choose(Sites.all.indexOfFirst{it.id=="ddizi"}+1);waitFor{fake.scopes.contains("ddizi")}
            main{assertTrue(activity.findViewById<View>(R.id.tv_nav_movies)?.isShown!=true)
                assertTrue(activity.findViewById<View>(R.id.tv_nav_series).isShown)
                activity.findViewById<View>(R.id.tv_query).requestFocus()}
            ins.waitForIdleSync()
            main{activity.findViewById<android.widget.EditText>(R.id.tv_query).setText("Ortak");activity.findViewById<View>(R.id.tv_search_submit).performClick()}
            waitFor{fake.searches.contains("ddizi:Ortak")}
            choose(0);waitFor{CatalogPreferences(ins.targetContext).siteId.isEmpty()}
            main{assertTrue(activity.findViewById<View>(R.id.tv_nav_movies).isShown);activity.findViewById<View>(R.id.tv_nav_library).performClick()}
            ins.waitForIdleSync();assertTrue(fake.libraryReads>0)
            assertEquals("Ortak",fake.saved.single().card.title)
        }finally{
            main{activity.finish()};TvActivity.dataFactoryForTests=null
            prefs.edit().clear().apply{old?.let{putString("siteId",it)}}.commit()
        }
    }
    private class Fake:TvData {
        val scopes=CopyOnWriteArrayList<String>();val searches=CopyOnWriteArrayList<String>();var libraryReads=0
        private val card=Card(ContentRef("filmcibaba","https://filmizle.ink/scope/"),"Ortak",year=2025)
        val saved=listOf(LibraryRecord("common",card,true,23000,100000,0))
        private fun page(siteId:String,kind:ContentKind)=UnifiedPage(listOf(UnifiedItem("common",card.copy(ref=card.ref.copy(kind=kind)),listOf(card))),emptyMap(),emptyList(),mapOf((siteId.ifBlank{"filmcibaba"})to 1),1,1)
        override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=browseSite("",kind,cursors)
        override fun search(query:String,cursors:Map<String,PageCursor>)=searchSite("",query,cursors)
        override fun browseSite(siteId:String,kind:ContentKind,cursors:Map<String,PageCursor>):Flow<UnifiedPage>{scopes.add(siteId);return flowOf(page(siteId,kind))}
        override fun searchSite(siteId:String,query:String,cursors:Map<String,PageCursor>):Flow<UnifiedPage>{searches.add("$siteId:$query");return flowOf(page(siteId,ContentKind.MOVIE))}
        override suspend fun detail(card:Card)=Detail(card)
        override suspend fun episodes(card:Card)=emptyList<Episode>()
        override suspend fun resolve(option:SourceOption):ResolvedSource=error("No playback")
        override suspend fun records():List<LibraryRecord>{libraryReads++;return saved}
        override suspend fun update(card:Card,listed:Boolean?,position:Long?,duration:Long?){}
        override suspend fun remember(query:String){}
        override suspend fun searches()=emptyList<String>()
    }
}
