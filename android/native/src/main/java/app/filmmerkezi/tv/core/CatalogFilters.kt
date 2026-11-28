package app.filmmerkezi.tv.core

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray

enum class CatalogOrder { DEFAULT, YEAR, RATING, TITLE }
data class CatalogQuery(val kind:ContentKind=ContentKind.MOVIE,val genre:String="",val year:Int?=null,val language:String="",val minRating:Double=0.0,val order:CatalogOrder=CatalogOrder.DEFAULT){
    val filtered get()=genre.isNotBlank()||year!=null||language.isNotBlank()||minRating>0
    val signature get()=TextKey.hash(listOf(kind,genre,year,language,minRating,order).joinToString("|"))
    fun accepts(c:Card,remote:Set<String> = emptySet())= (year==null||c.year==year||c.year==null&&"year" in remote)&&(minRating<=0||(c.imdb?:-1.0)>=minRating||c.imdb==null&&"rating" in remote)&&
        (language.isBlank()||TextKey.fold(c.language).contains(language)||c.language.isBlank()&&"language" in remote)&&(genre.isBlank()||c.genres.any{GenreKey.key(it)==GenreKey.key(genre)}||c.genres.isEmpty()&&"genre" in remote)
}
object GenreKey {
    fun key(s:String):String {val n=TextKey.fold(s.replace(Regex("\\s*\\(\\d+\\)\\s*$"),"")).removeSuffix(" izle").removeSuffix(" filmleri").removeSuffix(" filmi").removeSuffix(" filmler").removeSuffix(" dizileri").removeSuffix(" diziler");return when(n){"action"->"aksiyon";"drama"->"dram";"comedy"->"komedi";"horror"->"korku";"thriller"->"gerilim";"romance","romantic"->"romantik";"science fiction","sci fi","bilimkurgu"->"bilim kurgu";"adventure"->"macera";"fantasy"->"fantastik";"animation"->"animasyon";"family"->"aile";"documentary"->"belgesel";"mystery"->"gizem";"crime"->"suc";"war"->"savas";else->n}}
}
data class FilterValue(val value:String,val label:String)
data class FilterOptions(val values:Map<String,List<FilterValue>> = emptyMap())
data class FilterCoverage(val siteId:String,val remote:Set<String>,val local:Set<String>,val checked:Int=0,val omitted:Int=0){val partial get()=local.isNotEmpty()}

