package app.filmmerkezi.tv.updates

import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Test

class UpdateHubPolicyTest {
    private val fp="a".repeat(64);private val repo="$fp/example"
    private val now=1000000L
    private fun record(id:String,version:String="1.0.0")=JSONObject().put("repo",repo).put("enabled",true).put("manifest",JSONObject()
        .put("schemaVersion",1).put("platform","android").put("runtime",UpdateProtocol.RUNTIME).put("sdkMin",1).put("sdkMax",2)
        .put("id",id).put("version",version).put("type","site").put("name",id).put("minAppVersionCode",38).put("minSdk",23).put("entryClass","app.filmmerkezi.addon.Entry")
        .put("site",JSONObject().put("id",id).put("name",id).put("base","https://fixture.example"))
        .put("origins",JSONArray().put("https://fixture.example")).put("dependencies",JSONArray())
        .put("artifact",JSONObject().put("path","packages/$id/$version/plugin.jar").put("sha256","0".repeat(64)).put("size",1)).toString())
    private fun entry(id:String,version:String="1.0.0",min:Int=38,type:String="site")=UpdateProtocol.Entry(id,version,type,id,"packages/$id/$version/manifest.json",fp,min)
    private fun catalog(entries:List<UpdateProtocol.Entry> = listOf(entry("p002")),active:JSONObject=JSONObject(),pending:JSONObject?=null,enabled:Boolean=true,flags:JSONObject=JSONObject()):PluginCatalog {
        val state=JSONObject(flags.toString()).put("packages",active);pending?.let{state.put("pending",it)}
        return PluginCatalog(state,listOf(JSONObject().put("key",repo).put("fingerprint",fp).put("name","Example").put("enabled",enabled)),mapOf(repo to UpdateProtocol.Index("example","Example","",1,entries)),38)
    }
    private fun release(code:Int=39,sdk:Int=23)=UpdateProtocol.Release(code,"test-$code",sdk,"https://fixture.example/test.apk",1,fp,fp,"")
    private fun next(c:PluginCatalog=catalog(),s:DistributionStatus=DistributionStatus(),p:NoticeState=NoticeState(),time:Long=now)=UpdateNoticePolicy.next(c,s,p,time,38,29)

    @Test fun catalogIsProviderOnlySortedAndCountsLatestOnce(){
        val c=catalog(listOf(entry("r002",type="resolver"),entry("p005"),entry("p002"),entry("p002","2.0.0")))
        assertEquals(listOf("p002","p005"),c.plugins.map{it.entry.id});assertEquals("2.0.0",c.plugins.first().entry.version);assertEquals(2,c.availableCount);assertEquals(2,c.fresh.size)
    }
    @Test fun installedUpdatesAndPreparedPackagesAreDifferent(){
        val active=JSONObject().put("$fp/p002",record("p002"));val c=catalog(listOf(entry("p002","2.0.0"),entry("p003")),active)
        assertEquals(1,c.installedCount);assertEquals("p003",c.fresh.single().entry.id);assertEquals("p002",c.updates.single().entry.id)
        val prepared=catalog(listOf(entry("p002","2.0.0")),active,JSONObject().put("$fp/p002",record("p002","2.0.0")))
        assertTrue(prepared.pending);assertTrue(prepared.plugins.single().ready);assertTrue(prepared.updates.isEmpty());assertNull(next(prepared))
    }
    @Test fun disabledAndNewerHostAreNotInstallable(){
        assertTrue(catalog(enabled=false).fresh.isEmpty());assertEquals(0,catalog(listOf(entry("p002",min=99))).availableCount)
        assertNull(next(catalog(enabled=false)));assertNull(next(catalog(listOf(entry("p002",min=99)))))
    }
    @Test fun duplicateRepositoryIdentityDoesNotDuplicateCardsOrNotices(){
        val repos=listOf(repo,repo+"2").map{JSONObject().put("key",it).put("fingerprint",fp).put("name",it)}
        val c=PluginCatalog(JSONObject().put("packages",JSONObject()),repos,mapOf(repo to UpdateProtocol.Index("one","One","",1,listOf(entry("p002"))),repo+"2" to UpdateProtocol.Index("two","Two","",1,listOf(entry("p002","2.0.0")))),38)
        assertEquals(1,c.availableCount);assertEquals("2.0.0",c.plugins.single().entry.version);assertEquals(1,next(c)!!.tokens.size)
    }
    @Test fun applicationNoticeHasPriorityButUnsupportedOrOldReleaseDoesNot(){
        val s=DistributionStatus(release=release(),appCheck=CheckState.AVAILABLE)
        assertEquals(NoticeKind.APPLICATION,next(s=s)!!.kind)
        assertEquals(NoticeKind.NEW_PLUGINS,next(s=s.copy(release=release(38)))!!.kind)
        assertEquals(NoticeKind.NEW_PLUGINS,next(s=s.copy(release=release(sdk=35)))!!.kind)
        assertEquals(NoticeKind.NEW_PLUGINS,next(s=s.copy(appCheck=CheckState.FAILED))!!.kind)
    }
    @Test fun ignoreOnlyTheNotifiedApplicationVersion(){
        val s=DistributionStatus(release=release(),appCheck=CheckState.AVAILABLE)
        val p=NoticeState().decide(listOf("app:39"),NoticeDecision.IGNORE,now)
        assertEquals(NoticeKind.NEW_PLUGINS,next(s=s,p=p)!!.kind)
        assertEquals("app:40",next(s=s.copy(release=release(40)),p=p)!!.tokens.single())
    }
    @Test fun ignoreOnlyTheOfferedOptionalPluginsNotFuturePlugins(){
        val p=NoticeState().decide(next()!!.tokens,NoticeDecision.IGNORE,now)
        assertNull(next(p=p));assertNull(next(catalog(listOf(entry("p002","2.0.0"))),p=p))
        assertEquals("p003",next(catalog(listOf(entry("p002"),entry("p003"))),p=p)!!.plugins.single().entry.id)
    }
    @Test fun installedPluginUpdateIgnoreIsVersionSpecific(){
        val active=JSONObject().put("$fp/p002",record("p002"));val c=catalog(listOf(entry("p002","2.0.0")),active)
        val p=NoticeState().decide(next(c)!!.tokens,NoticeDecision.IGNORE,now)
        assertNull(next(c,p=p));assertEquals(NoticeKind.PLUGIN_UPDATES,next(catalog(listOf(entry("p002","3.0.0")),active),p=p)!!.kind)
    }
    @Test fun laterPersistsUntilExactlyTwentyFourHours(){
        val p=NoticeState().decide(next()!!.tokens,NoticeDecision.LATER,now)
        assertNull(next(p=p,time=now+24*60*60*1000L-1));assertNotNull(next(p=p,time=now+24*60*60*1000L))
        assertEquals(p,NoticeState.decode(p.encode()))
    }
    @Test fun individualCategoryCanBeDisabledWithoutBlockingOtherKinds(){
        assertNull(next(p=NoticeState(disabled=setOf(NoticeKind.NEW_PLUGINS))))
        assertEquals(NoticeKind.APPLICATION,next(s=DistributionStatus(release=release(),appCheck=CheckState.AVAILABLE),p=NoticeState(disabled=setOf(NoticeKind.NEW_PLUGINS)))!!.kind)
    }
    @Test fun corruptPreferencesDoNotCrashAndExpiredSnoozesArePruned(){
        assertEquals(NoticeState(),NoticeState.decode("broken"));assertEquals(NoticeState(),NoticeState.decode("{\"schemaVersion\":2}"))
        val p=NoticeState(later=mapOf("expired" to 1,"future" to now+100)).decide(listOf("new"),NoticeDecision.LATER,now)
        assertFalse("expired" in p.later);assertTrue("future" in p.later)
    }
    @Test fun failedRepositoriesDoNotProduceNewNotices(){
        assertNull(next(s=DistributionStatus(repositoryChecks=mapOf(repo to RepositoryCheck(repo,"Example",CheckState.FAILED)))))
        assertNotNull(next(s=DistributionStatus(repositoryChecks=mapOf(repo to RepositoryCheck(repo,"Example",CheckState.CURRENT)))))
        val bad=PluginCatalog(JSONObject().put("packages",JSONObject()),emptyList(),emptyMap(),38,mapOf("bad" to "Invalid signature"))
        assertTrue(bad.plugins.isEmpty());assertNull(next(bad))
    }
    @Test fun busyAndRestartRequiredDeferAllNotices(){
        assertNull(next(s=DistributionStatus(busy=true)))
        assertNull(next(catalog(flags=JSONObject().put("needsBoot",true))))
        assertNull(next(catalog(flags=JSONObject().put("bootPending",true))))
    }
    @Test fun installationActivatesThenRestartsOnlyWhileForeground()=runBlocking {
        val calls=mutableListOf<String>();InstallCompletion.finish({true},{calls.add("activate")},{calls.add("restart")},{calls.add("defer")})
        assertEquals(listOf("activate","restart"),calls)
        calls.clear();InstallCompletion.finish({false},{calls.add("activate")},{calls.add("restart")},{calls.add("defer")});assertEquals(listOf("defer"),calls)
    }
    @Test fun leavingForegroundAfterActivationKeepsVisibleFinishAction()=runBlocking {
        val calls=mutableListOf<String>();var foreground=true
        InstallCompletion.finish({foreground},{calls.add("activate");foreground=false},{calls.add("restart")},{calls.add("defer")})
        assertEquals(listOf("activate","defer"),calls)
    }
    @Test fun failedActivationNeverRestarts()=runBlocking {
        var restarted=false;val failure=runCatching{InstallCompletion.finish({true},{error("disk full")},{restarted=true},{})}.exceptionOrNull()
        assertNotNull(failure);assertFalse(restarted)
    }
}
