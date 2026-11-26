package app.filmmerkezi.tv.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DetailMetadata104Test {
    @Test fun filmMakinesiGenresBelongToTheFilmInsteadOfTheWholeMenu() {
        val index=JSONArray(checkNotNull(javaClass.getResource("/sources/index.json")).readText())
        val row=index.values().filterIsInstance<JSONObject>().first{it.string("id")=="filmmakinesi-spider"}
        val reply=row.getJSONArray("responses").getJSONObject(0)
        val html=checkNotNull(javaClass.getResource("/sources/"+reply.string("file"))).readText()
        val detail=CatalogParser(Sites.get("filmmakinesi")).detail(html,Card(ContentRef("filmmakinesi",row.string("page")),"Fixture"))
        assertEquals(listOf("Aksiyon","Macera","Bilim Kurgu"),detail.genres)
        assertEquals("PT145M",detail.duration)
    }
}
