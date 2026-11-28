package app.filmmerkezi.tv.updates

import app.filmmerkezi.addon.pilot.PilotEntry
import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.pluginapi.PluginHost
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assume.assumeTrue
import java.io.File
import okhttp3.*
import okhttp3.FormBody
import okhttp3.Cookie
import java.util.concurrent.TimeUnit

/** Only P002–P005. Live capture is explicit and never part of ordinary test/CI runs. */
class PilotAddonTest {
    private var previousSites=emptyList<SiteSpec>()
    private var previousKnown=emptyList<SiteSpec>()
    @Before fun preserveSiteRegistry(){previousSites=Sites.all.toList();previousKnown=Sites.known.toList()}
    @After fun restoreSiteRegistry(){Sites.installed(previousSites);Sites.remember(previousKnown)}
    private fun spec(id: String)=when(id) {
        "p002"->SiteSpec(id,"P002 · PuhuTV","https://puhutv.com","/","/","a","h2")
        "p003"->SiteSpec(id,"P003 · NOW","https://www.nowtv.com.tr","/arama","/dizi-arsivi","a","h2")
        "p005"->SiteSpec(id,"P005 · Star TV","https://www.startv.com.tr","/dizi","/dizi","a","h2")
        else->Sites.defaults.first{it.id=="filmcibaba"}
    }
    private class Host(val handler: (JSONObject)->String):PluginHost {
        val calls=mutableListOf<JSONObject>()
        override fun request(payload: String): String {val q=JSONObject(payload);calls.add(q);return JSONObject().put("url",q.getString("url")).put("body",handler(q)).put("code",200).put("headers",JSONObject()).toString()}
        override fun resolver(id:String,op:String,payload:String):String=error("Unexpected dependency")
        override fun storage(op:String,key:String,value:String):String=error("Unexpected storage")
        override fun cancelled()=false
    }
    private fun call(entry: PilotEntry, id:String, op:String, host:PluginHost, data:JSONObject=JSONObject()):JSONObject {
        // The real worker registers signed SiteSpec metadata before dispatch as well.
        Sites.updateMetadata(spec(id))
        return JSONObject(entry.call(op,data.put("site",PluginWire.site(spec(id))).toString(),host))
    }
    private fun next(p:JSONObject)="<script id='__NEXT_DATA__' type='application/json'>"+JSONObject().put("props",JSONObject().put("pageProps",p))+"</script>"
    private fun flight(records:String)="<script>self.__next_f.push("+JSONArray().put(1).put(records)+")</script>"
    private fun card(id:String,url:String,title:String="Örnek",kind:ContentKind=ContentKind.SERIES)=Card(ContentRef(id,url,kind),title)
    private fun failure(block:()->Unit) {try{block();fail("Expected rejection")}catch(e:DataFailure){assertTrue(e.message.isNotBlank())}}

    @Test fun onlyFourPilotIdentitiesAndUnknownOperationsRejected() {
        for(id in listOf("p002","p003","filmcibaba","p005")) assertEquals(1,call(PilotEntry(),id,"describe",Host{error("No network expected")}).getInt("sdk"))
        failure{call(PilotEntry(),"p003","executeRemoteScript",Host{error("No network")})}
    }
    @Test fun filmciSearchDoesNotTreatPlotTextAsTitleMatch() {
        val html="<a class='v100-card' href='https://filmizle.ink/american-psycho/'><h2 class='v100-title'>American Psycho</h2></a><a class='v100-card' href='https://filmizle.ink/other/'><h2 class='v100-title'>Başka film</h2></a>"
        val page=call(PilotEntry(),"filmcibaba","search",Host{html},JSONObject().put("query","American Psycho"))
        assertEquals(1,page.getJSONArray("cards").length())
    }
    @Test fun puhuCatalogRejectsClipsLiveExternalAndDeduplicates() {
        fun item(id:Int,type:String,slug:String)=JSONObject().put("id",id).put("type",type).put("name","Şahsiyet").put("slug",slug).put("image","https://img-puhutv.mncdn.com/media/test.jpg").put("meta",JSONObject().put("year",2018))
        val good=item(1,"title_serie","/sahsiyet-detay")
        val body=next(JSONObject().put("data",JSONArray().put(good).put(good).put(item(2,"asset_episode","/clip-detay")).put(item(3,"title_movie","https://elsewhere.test/x-detay")).put(item(4,"title_movie","/canli-yayin-detay")).put(item(5,"title_movie","/external-detay").put("cta_external_url","https://elsewhere.test"))))
        val p=PluginWire.page(call(PilotEntry(),"p002","parseList",Host{error("No network")},JSONObject().put("body",body).put("url","https://puhutv.com/")))
        assertEquals(1,p.cards.size);assertEquals(2018,p.cards[0].year);assertTrue(p.cards[0].poster.contains("/mnresize/360/540/"))
        val empty=call(PilotEntry(),"p002","search",Host{"{\"recommendation\":true,\"total_count\":1,\"data\":[]}"},JSONObject().put("query","bilinmeyen"))
        assertEquals(0,empty.getJSONArray("cards").length())
    }
    @Test fun puhuWrongEpisodeRejectedBeforeMediaApiAndDrmRejected() {
        val asset=JSONObject().put("type","asset_episode").put("video_id","PUHU_test").put("meta",JSONObject().put("season_number",2).put("episode_number",3))
        val html=next(JSONObject().put("movieAssets",JSONObject().put("data",asset)))
        val ref=ContentRef("p002","https://puhutv.com/test-3-bolum-izle",ContentKind.EPISODE)
        val option=SourceOption("test",ref,"Puhu",ref.pageUrl,fields=mapOf("season" to "1","episode" to "3"))
        val host=Host{html}; failure{call(PilotEntry(),"p002","resolve",host,JSONObject().put("option",Contracts.option(option)))};assertEquals(1,host.calls.size)
        val drmHost=Host {q->if(q.getString("url").contains("video_info")) "{\"success\":true,\"data\":{\"drm\":true,\"flavors\":{\"hls\":\"https://puhu-p3.mncdn.com/x.m3u8\"}}}" else html}
        failure{call(PilotEntry(),"p002","resolve",drmHost,JSONObject().put("option",Contracts.option(option.copy(fields=mapOf("season" to "2","episode" to "3")))))}
        assertEquals(2,drmHost.calls.size)
    }
    @Test fun nowSearchUsesSeriesSectionOnlyAndProperPostFields() {
        val host=Host { q->assertEquals("POST",q.getString("method"));assertEquals("yeralti",q.getJSONObject("fields").getString("query"));
            "<div class='scrollable'><section class='container'><h2>Diziler</h2></section><div class='posters'><div class='poster'><a href='/yeralti/izle'><img data-src='/p.jpg' alt='Yeraltı'></a></div><div class='poster'><a href='/yeralti/fragman/1'><img alt='Yeraltı'></a></div></div><section class='container'><h2>Videolar</h2></section><div class='posters'><div class='poster'><a href='/yeralti-ozel/izle'><img alt='Yeraltı Özel'></a></div></div></div>" }
        val p=PluginWire.page(call(PilotEntry(),"p003","search",host,JSONObject().put("query","Yeraltı")))
        assertEquals(1,p.cards.size);assertEquals("https://www.nowtv.com.tr/yeralti/izle",p.cards[0].ref.pageUrl)
    }
    @Test fun nowSeasonsPaginateWithoutTrailersOrOtherSeries() {
        val host=Host { q->if(q.getString("url").endsWith("/bolumler")) "<h1>Yeraltı - BÖLÜMLER</h1><select id='video-filter-changer'><option value='1'>1</option><option value='2'>2</option></select><div class='ajax-load-more-video' data-program-id='10' data-type='2' data-count='1'></div>"
            else {val f=q.getJSONObject("fields");val s=f.getInt("season");val p=f.getInt("page");JSONObject().put("code",200).put("count",2).put("data","<a href='/yeralti/bolum/${(s-1)*2+p+1}'>Bölüm</a><a href='/other/bolum/1'>Diğer</a><a href='/yeralti/fragman/1'>Fragman</a>").toString()} }
        val d=PluginWire.detail(call(PilotEntry(),"p003","detail",host,JSONObject().put("card",Contracts.card(card("p003","https://www.nowtv.com.tr/yeralti/izle")))))
        assertEquals(listOf(1,2),d.seasons);assertEquals(listOf(1,2,3,4),d.episodes.map{it.number.number});assertEquals(5,host.calls.size)
    }
    @Test fun starFlightUnicodeReferencesAndCatalogScope() {
        val text="Çığ 🙂";val bytes=text.toByteArray().size.toString(16)
        val record="1:T$bytes,$text"+"2:{\"resourceType\":\"TvSeries\",\"_id\":\"123456789012345678901234\",\"name\":\"\$1\",\"url\":\"/dizi/test\"}\n3:{\"items\":[\"\$2\"]}\n"
        val p=PluginWire.page(call(PilotEntry(),"p005","parseList",Host{error("No network")},JSONObject().put("url","https://www.startv.com.tr/dizi").put("body",flight(record))))
        assertEquals(text,p.cards.single().title)
        failure {call(PilotEntry(),"p005","parseList",Host{error("No network")},JSONObject().put("url","https://www.startv.com.tr/dizi").put("body",flight("1:T4,x")))}
    }
    @Test fun starTrailerAndSourceEpisodeMismatchRejected() {
        val c=ContentRef("p005","https://www.startv.com.tr/dizi/test/fragmanlar/1",ContentKind.EPISODE)
        val host=Host{error("No network")};failure{call(PilotEntry(),"p005","resolve",host,JSONObject().put("option",Contracts.option(SourceOption("x",c,"Star",c.pageUrl))))};assertEquals(0,host.calls.size)
        val u="https://www.startv.com.tr/dizi/test/bolumler/1-bolum"
        val html=flight("1:{\"resourceType\":\"Episode\",\"_id\":\"123456789012345678901234\",\"url\":\"$u\",\"episodeNo\":1,\"seasonNo\":2,\"video\":{\"referenceId\":\"123\"}}\n")
        val wrong=Host{html};failure{call(PilotEntry(),"p005","resolve",wrong,JSONObject().put("option",Contracts.option(SourceOption("x",c.copy(pageUrl=u),"Star",u,fields=mapOf("season" to "1","episode" to "1")))))};assertEquals(1,wrong.calls.size)
    }
    @Test fun fullMasterHeadersAndSubtitlesPreservedWithoutQualityCap() {
        val c=ContentRef("p002","https://puhutv.com/movie-izle",ContentKind.MOVIE)
        val host=Host {q->when {q.getString("url").contains("video_info")->"{\"success\":true,\"data\":{\"flavors\":{\"hls\":\"https://puhu-p3.mncdn.com/master.m3u8\"},\"tracks\":[{\"kind\":\"subtitles\",\"file\":\"https://puhu-p3.mncdn.com/tr.vtt\",\"srclang\":\"tr\"}]}}"
            q.getString("url").endsWith(".m3u8")->"#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100000,RESOLUTION=3840x2160\n2160.m3u8\n"
            else->next(JSONObject().put("movieAssets",JSONObject().put("data",JSONObject().put("type","asset_movie").put("video_id","PUHU_movie"))))}}
        val s=PluginWire.source(call(PilotEntry(),"p002","resolve",host,JSONObject().put("option",Contracts.option(SourceOption("x",c,"Puhu",c.pageUrl)))))
        assertEquals("https://puhu-p3.mncdn.com/master.m3u8",s.url);assertEquals("https://puhutv.com/",s.referer);assertEquals("tr",s.subtitles.single().language);assertEquals("Mozilla/5.0",s.userAgent)
    }

    @Test fun liveFourSitesCapture() {
        assumeTrue("Explicit manual live mode",System.getenv("FM_PILOT_LIVE")=="1")
        val out=File(System.getenv("FM_PILOT_OUTPUT")?:error("FM_PILOT_OUTPUT required")).apply{mkdirs()}
        val cookies=mutableListOf<Cookie>()
        val http=OkHttpClient.Builder().cookieJar(object:CookieJar {
            override fun saveFromResponse(url:HttpUrl,values:List<Cookie>)=synchronized(cookies){for(c in values){cookies.removeAll{it.name==c.name&&it.domain==c.domain&&it.path==c.path};cookies.add(c)}}
            override fun loadForRequest(url:HttpUrl):List<Cookie> = synchronized(cookies){cookies.filter{it.expiresAt>System.currentTimeMillis()&&it.matches(url)}}
        }).connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build()
        val report=JSONArray();var failures=0
        val selected=System.getenv("FM_PILOT_SITES")?.split(',')
        for((id,term) in listOf("p002" to "Şahsiyet","p003" to "Yeraltı","filmcibaba" to (System.getenv("FM_PILOT_FILMCI_QUERY")?:"American Psycho 1"),"p005" to "Sevdiğim Sensin").filter{selected==null||it.first in selected}) {
            val entry=PilotEntry();val dir=File(out,id).apply{mkdirs()};val requests=JSONArray();var index=0;val started=System.nanoTime()
            val host=Host { q->
                val r=Request.Builder().url(q.getString("url")).header("Accept","*/*");val headers=q.optJSONObject("headers")?:JSONObject();for(k in headers.keys())r.header(k,headers.getString(k));r.header("Referer",q.optString("referer"))
                if(q.optString("method")=="POST"){val f=q.getJSONObject("fields");val form=FormBody.Builder();for(k in f.keys())form.add(k,f.getString(k));r.post(form.build())}
                http.newCall(r.build()).execute().use {response->
                    val body=response.body.string();val file="response-${index++}.txt";File(dir,file).writeText(body)
                    requests.put(JSONObject().put("request",q).put("code",response.code).put("file",file).put("sha256",UpdateProtocol.sha(body.toByteArray())))
                    if(!response.isSuccessful) throw DataFailure(FailureKind.HTTP,"HTTP ${response.code}","live",response.code)
                    body
                }
            }
            val row=JSONObject().put("id",id)
            try {
                val browse=call(entry,id,"browse",host,JSONObject().put("kind",if(id=="filmcibaba")"MOVIE" else "SERIES"));File(dir,"browse.json").writeText(browse.toString())
                val search=call(entry,id,"search",host,JSONObject().put("query",term));File(dir,"search.json").writeText(search.toString())
                val cards=PluginWire.page(search).cards;check(cards.isNotEmpty()){ "Search returned no exact candidates" }
                val target=cards.firstOrNull{TextKey.fold(it.title)==TextKey.fold(term)}?:cards.first()
                val detail=call(entry,id,"detail",host,JSONObject().put("card",Contracts.card(target)));File(dir,"detail.json").writeText(detail.toString())
                val d=PluginWire.detail(detail)
                val playable=if(d.episodes.isNotEmpty()) {
                    val ep=d.episodes.first().card
                    PluginWire.detail(call(entry,id,"detail",host,JSONObject().put("card",Contracts.card(ep))))
                } else d
                check(playable.options.isNotEmpty()){ "No playable options" }
                var resolved:JSONObject?=null;val sourceErrors=JSONArray();val sourceAudit=JSONArray()
                for((sourceIndex,option) in playable.options.withIndex()) {
                    val audit=JSONObject().put("index",sourceIndex).put("id",option.id).put("label",option.label).put("state",option.state.name)
                    File(dir,"option-$sourceIndex.json").writeText(Contracts.option(option).toString())
                    if(option.state!=OptionState.AVAILABLE){sourceAudit.put(audit.put("resolved",false).put("reason",option.reason));continue}
                    try { val source=call(entry,id,"resolve",host,JSONObject().put("option",Contracts.option(option)))
                        File(dir,"source-$sourceIndex.json").writeText(source.toString());audit.put("resolved",true).put("type",source.getString("type"))
                        if(resolved==null){resolved=source;File(dir,"option.json").writeText(Contracts.option(option).toString())}
                    }catch(e:Exception){sourceErrors.put(JSONObject().put("label",option.label).put("error",e.toString()));audit.put("resolved",false).put("error",e.toString())}
                    sourceAudit.put(audit)
                }
                File(dir,"sources-audit.json").writeText(sourceAudit.toString(2))
                row.put("sourceFailures",sourceErrors)
                val source=resolved?:error("All sources failed: $sourceErrors");File(dir,"source.json").writeText(source.toString())
                check(sourceErrors.length()==0){"Some discovered sources failed: $sourceErrors"}
                row.put("passed",true).put("browse",browse.getJSONArray("cards").length()).put("search",cards.size).put("episodes",d.episodes.size).put("seasons",JSONArray(d.seasons)).put("options",playable.options.size)
            } catch(e:Exception) {failures++;row.put("passed",false).put("error",e.toString())}
            finally {File(dir,"requests.json").writeText(requests.toString(2))}
            row.put("elapsedMs",(System.nanoTime()-started)/1_000_000);report.put(row)
        }
        val reportFile=File(out,"live-results.json")
        val previous=if(selected!=null&&reportFile.exists()) JSONArray(reportFile.readText()) else JSONArray()
        val combined=JSONArray();for(i in 0 until previous.length())if(previous.getJSONObject(i).getString("id") !in (selected?:emptyList()))combined.put(previous.getJSONObject(i))
        for(i in 0 until report.length())combined.put(report.getJSONObject(i))
        reportFile.writeText(combined.toString(2));println(report.toString(2))
        assertEquals("See live-results.json; availability failures are not fixture successes",0,failures)
    }
}
