package app.filmmerkezi.tv.tv

import android.view.*
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.settings.AppSettings
import app.filmmerkezi.tv.updates.PilotWorkerTest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CatalogRulesUiTest {
    @Test fun collectionOpensMembersWithoutResolutionAndUnknownYearIsNotZero()=runBlocking {PilotWorkerTest().withSet{
        val f=Settings110Fixture();var resolves=0
        val collection=Card(ContentRef("filmcibaba","https://filmizle.ink/haber/marvel/",ContentKind.COLLECTION),"Marvel Filmleri",workId="work:collection-rules")
        val data=object:TvData by UpdateTestData.empty(){
            override fun browse(kind:ContentKind,cursors:Map<String,PageCursor>)=flowOf(UnifiedPage(listOf(UnifiedItem(collection.workId,collection,listOf(collection))),emptyMap(),emptyList()))
            override fun search(query:String,cursors:Map<String,PageCursor>)=browse(ContentKind.MOVIE,cursors)
            override fun searchSite(siteId:String,query:String,cursors:Map<String,PageCursor>)=search(query,cursors)
            override fun browseSite(siteId:String,kind:ContentKind,cursors:Map<String,PageCursor>)=browse(kind,cursors)
            override fun filteredBrowse(siteId:String,query:CatalogQuery,cursors:Map<String,PageCursor>)=browse(query.kind,cursors)
            override suspend fun detail(card:Card)=Detail(collection,collectionItems=listOf(CollectionEntry(1,"Iron Man",2008),CollectionEntry(2,"Avengers",0)))
            override suspend fun resolve(option:SourceOption):ResolvedSource{resolves++;error("Collection must not resolve")}
        }
        try{
            f.start(AppSettings(),data);f.waitFor{f.activity.hasWindowFocus()}
            f.main{f.activity.findViewById<android.widget.EditText>(R.id.tv_query).setText("Marvel");f.activity.findViewById<View>(R.id.tv_search_submit).performClick()};f.waitFor{f.tag(collection.workId)!=null}
            f.main{f.tag(collection.workId).performClick()};f.waitFor{f.tag("collection:0")!=null&&f.tag("collection:1")!=null}
            f.main{val row=f.tag("collection:1");val text=if(row is android.widget.TextView)row.text.toString()else row.contentDescription?.toString().orEmpty();assertFalse(text.contains("(0)"));assertFalse(text.contains(" · 0"))}
            assertEquals(0,resolves);f.screenshot("catalog-rules-collection.png")
            File(f.context.getExternalFilesDir(null),"catalog-rules-ui.json").writeText(JSONObject().put("collectionMembers",2).put("unknownYearHidden",true).put("resolverCalls",resolves).put("passed",true).toString(2))
        }finally{f.close()}
    }}
}
