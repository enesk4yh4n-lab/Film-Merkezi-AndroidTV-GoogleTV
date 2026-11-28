package app.filmmerkezi.tv.updates

import app.filmmerkezi.tv.core.*
import app.filmmerkezi.tv.settings.*
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.Assert.*

class InstalledSitesTest {
    private var before=emptyList<SiteSpec>();private var known=emptyList<SiteSpec>()
    private val a=SiteSpec("plugin-a","Eklenti A","https://a.example","/search","/movies","a","h2",seriesPath="/series")
    private val b=a.copy(id="plugin-b",name="Eklenti B",base="https://b.example")
    private val retired=a.copy(id="retired",name="Kaldırılan eklenti",base="https://retired.example")
    @Before fun save(){before=Sites.all.toList();known=Sites.known.toList();Sites.installed(listOf(a,b));Sites.remember(listOf(retired))}
    @After fun restore(){Sites.configure(emptyMap());Sites.installed(before);Sites.remember(known)}
    @Test fun historicalMetadataNeverBecomesASelectableOrNetworkProvider() {
        val s=SettingsCodec.read(mapOf("siteOrder" to "retired|plugin-b|plugin-a","selectedSite" to "retired"))
        assertEquals(listOf(b.id,a.id),s.installedSites().map{it.id})
        assertEquals(listOf(b.id,a.id),s.catalog().sites);assertEquals("",s.selectedSite)
        assertFalse(s.sourceAllowed(retired.id));assertTrue(Sites.known.any{it.id==retired.id})
    }
    @Test fun cachedPreferencesFollowRemovalAndReinstallWithoutLosingUserChoices() {
        val s=AppSettings(siteOrder=listOf(retired.id,b.id,a.id),disabledSources=setOf(b.id),disabledCatalog=setOf(b.id))
        Sites.installed(listOf(a));Sites.remember(listOf(b,retired))
        assertEquals(listOf(a.id),s.installedSites().map{it.id});assertEquals(listOf(a.id),s.catalog().sites)
        assertFalse(s.sourceAllowed(b.id))
        Sites.installed(listOf(a,b));assertEquals(listOf(b.id,a.id),s.installedSites().map{it.id})
        assertFalse(s.sourceAllowed(b.id));assertEquals(listOf(a.id),s.catalog().sites)
    }
    @Test fun noInstalledSiteMeansEmptyMenusAndNoMetadataRequests()=runBlocking {
        Sites.installed(emptyList());Sites.remember(listOf(retired))
        val s=AppSettings(siteOrder=listOf(retired.id));var calls=0
        val repo=CatalogRepository(object:WebTransport{override suspend fun request(request:WebRequest):WebResponse{calls++;error("No request expected")}},requestOptions={s.catalog()})
        assertTrue(s.installedSites().isEmpty());assertTrue(repo.providers.isEmpty());assertTrue(repo.search("film").items.isEmpty());assertEquals(0,calls)
    }
    @Test fun providerSnapshotRefreshesWhenTheInstalledSetChanges() {
        val repo=CatalogRepository(object:WebTransport{override suspend fun request(request:WebRequest):WebResponse=error("No request expected")})
        assertEquals(setOf(a.id,b.id),repo.providers.keys)
        Sites.installed(listOf(b));assertEquals(setOf(b.id),repo.providers.keys)
        Sites.installed(emptyList());assertTrue(repo.providers.isEmpty())
    }
    @Test fun savedAddressForADynamicPluginDoesNotNeedAnEmbeddedDefault() {
        val raw="""{"future-plugin":{"base":"https://new.example","name":"Yeni ad","previousHosts":["old.example"]}}"""
        val values=SiteAddresses.decode(raw);assertEquals("Yeni ad",values.getValue("future-plugin").name)
        assertEquals(values,SiteAddresses.decode(SiteAddresses.encode(values)));assertFalse(Sites.isInstalled("future-plugin"))
    }
    @Test fun oldLibraryCardCanBeReadWithoutReactivatingItsProvider() {
        Sites.installed(emptyList())
        val card=Card(ContentRef("archived-plugin","https://archive.example/movie/"),"Kayıtlı film",workId="work:saved")
        val copy=Contracts.readCard(Contracts.card(card))
        assertEquals(card,copy);assertFalse(Sites.isInstalled(card.ref.siteId));assertTrue(Sites.all.isEmpty())
    }
    @Test fun aDynamicProviderCanUseAYouTubeWrapperWithoutABuiltinProviderId() {
        val path="/player/telif/index.php?id=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3Dabcdefghijk"
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk",YoutubeLinks.frameUrl(a.base+path,a.base+"/episode/"))
        assertNull(YoutubeLinks.frameUrl(b.base+path,a.base+"/episode/"))
    }
    @Test fun oldProviderHeaderCompatibilityUsesInstalledMetadataAfterDomainChanges() {
        val moved=a.copy(id="dizibox",name="Taşınmış eklenti",base="https://relocated.example")
        Sites.installed(listOf(moved))
        assertEquals(app.filmmerkezi.tv.SiteHttpPolicy.BROWSER_USER_AGENT,SiteAddresses.userAgent(okhttp3.HttpUrl.Builder().scheme("https").host("relocated.example").build()))
        Sites.installed(emptyList())
        assertEquals("Mozilla/5.0",SiteAddresses.userAgent(okhttp3.HttpUrl.Builder().scheme("https").host("relocated.example").build()))
    }
    @Test fun disabledResolverHidesItsDependentProviderAndDisabledProviderStaysHidden() {
        val fp="a".repeat(64);val resolverKey="$fp/r101";val siteKey="$fp/p101"
        fun module(id:String,type:String,deps:List<UpdateProtocol.Dependency> = emptyList())=UpdateProtocol.Module(id,"1.0.0",type,id,38,23,"app.filmmerkezi.addon.Entry",if(type=="site")PluginWire.site(a)else null,deps,listOf(a.base),"packages/$id/1.0.0/plugin.jar","0".repeat(64),1)
        val modules=mapOf(resolverKey to module("r101","resolver"),siteKey to module("p101","site",listOf(UpdateProtocol.Dependency(fp,"r101","1.0.0","2.0.0"))))
        val records=JSONObject().put(resolverKey,JSONObject().put("enabled",true)).put(siteKey,JSONObject().put("enabled",true));val state=JSONObject().put("packages",records)
        assertEquals(listOf(a.id),PackageSets.activeSites(state,modules).map{it.id})
        records.getJSONObject(resolverKey).put("enabled",false);assertTrue(PackageSets.activeSites(state,modules).isEmpty())
        records.getJSONObject(resolverKey).put("enabled",true);records.getJSONObject(siteKey).put("enabled",false);assertTrue(PackageSets.activeSites(state,modules).isEmpty())
    }
}
