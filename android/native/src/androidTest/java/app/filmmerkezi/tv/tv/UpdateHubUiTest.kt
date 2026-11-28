package app.filmmerkezi.tv.tv

import android.view.*
import androidx.test.platform.app.InstrumentationRegistry
import app.filmmerkezi.tv.R
import app.filmmerkezi.tv.settings.AppSettings
import app.filmmerkezi.tv.updates.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Signed pilot fixtures; update-only UI tests never request a film or a stream. */
class UpdateHubUiTest {
    private val results=JSONArray()
    private fun passed(name:String){results.put(JSONObject().put("case",name).put("passed",true))}
    private fun write(f:Settings110Fixture,file:String){File(f.context.getExternalFilesDir(null),file).writeText(results.toString(2))}
    @Suppress("UNCHECKED_CAST") private fun status(m:DistributionManager)=DistributionManager::class.java.getDeclaredField("state").apply{isAccessible=true}.get(m) as MutableStateFlow<DistributionStatus>
    private fun press(f:Settings110Fixture,key:Int){f.ins.sendKeyDownUpSync(key);f.ins.waitForIdleSync()}
    private suspend fun focused(f:Settings110Fixture,key:String){f.waitFor(6000){f.tag("updates:$key")?.hasFocus()==true}}
    private suspend fun threeInstalled(m:DistributionManager){m.store.commit{s->
        val set=s.getJSONObject("packages");set.keys().asSequence().filter{it.endsWith("/p005")||it.endsWith("/r005")}.toList().forEach(set::remove)
        s.remove("pending");s.remove("needsBoot");s.put("bootPending",false)
    };ModuleRuntime.register(m.store.snapshot())}

    @Test fun simplifiedCatalogAndPreferencesUseRemote()=runBlocking {PilotWorkerTest().withSet{
        val f=Settings110Fixture();val m=DistributionManager.get(f.context);val state=status(m);val old=state.value;val prefs=m.notices.value
        val large=InstrumentationRegistry.getArguments().getString("largeText")=="true"
        try{
            threeInstalled(m);state.value=DistributionStatus();m.notices.update{NoticeState()}
            f.start(AppSettings(largeText=large),UpdateTestData.empty());f.waitFor{f.activity.hasWindowFocus()}
            f.main{f.activity.settingsForTest();f.tag("settings-category:updates").requestFocus()}
            f.waitFor{f.activity.updateCenterForTest()?.keys()?.contains("add")==true}
            fun center()=checkNotNull(f.activity.updateCenterForTest())
            assertEquals(listOf("add","installed","updates","management","repositories","technical"),center().keys())
            f.main{assertTrue(center().titles().any{it.endsWith("3")})};passed("root_five_clear_choices_and_installed_count")
            press(f,KeyEvent.KEYCODE_DPAD_RIGHT);focused(f,"add")
            press(f,KeyEvent.KEYCODE_DPAD_UP);f.main{assertSame(f.tag("settings:back"),f.activity.currentFocus)}
            press(f,KeyEvent.KEYCODE_DPAD_DOWN);focused(f,"add");passed("header_back_down_returns_to_first_action")
            press(f,KeyEvent.KEYCODE_DPAD_CENTER)
            f.waitFor{center().keys().count{it.startsWith("catalog:")}==4}
            val c=withContext(Dispatchers.IO){m.catalog()};assertEquals(4,c.availableCount);assertEquals(3,c.installedCount)
            val fresh=c.fresh.single();assertEquals("p005",fresh.entry.id)
            f.main{center().focusFirst()};focused(f,"catalog:"+fresh.key)
            f.screenshot("update-hub-catalog-${f.context.resources.displayMetrics.widthPixels}.png")
            press(f,KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.tag("updates:install-selected") is android.widget.Button&&(f.tag("updates:install-selected") as android.widget.Button).text.toString().contains("1")}
            focused(f,"catalog:"+fresh.key);passed("flat_catalog_only_uninstalled_site_selectable_focus_kept")
            press(f,KeyEvent.KEYCODE_DPAD_DOWN);focused(f,"install-selected")
            press(f,KeyEvent.KEYCODE_DPAD_RIGHT);focused(f,"select-all")
            press(f,KeyEvent.KEYCODE_DPAD_RIGHT);focused(f,"add-repo")
            press(f,KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{center().keys()==listOf("search","search-user","address")}
            press(f,KeyEvent.KEYCODE_BACK);focused(f,"add-repo");passed("repository_add_is_optional_footer_and_back_keeps_origin")
            press(f,KeyEvent.KEYCODE_BACK);focused(f,"add")
            f.main{center().select("installed")};f.waitFor{center().keys().size==3};assertFalse(center().keys().any{it.endsWith("/r002")})
            press(f,KeyEvent.KEYCODE_BACK);focused(f,"installed");passed("installed_page_groups_resolvers_under_sites")
            f.main{center().select("updates")};f.waitFor{center().keys()==listOf("application")}
            f.main{center().select("application")};f.waitFor{f.tag("updates:update-application")!=null}
            f.main{assertFalse(f.tag("updates:update-application").isEnabled)}
            press(f,KeyEvent.KEYCODE_BACK);focused(f,"application");press(f,KeyEvent.KEYCODE_BACK);focused(f,"updates");passed("updates_page_single_application_action_disabled_without_release")
            f.main{center().select("technical")};f.waitFor{center().keys().contains("notifications")}
            f.main{center().focusFirst()};focused(f,"notifications")
            press(f,KeyEvent.KEYCODE_DPAD_UP);focused(f,"back");press(f,KeyEvent.KEYCODE_DPAD_UP);f.main{assertSame(f.tag("settings:back"),f.activity.currentFocus)}
            press(f,KeyEvent.KEYCODE_DPAD_DOWN);focused(f,"back");passed("advanced_header_up_and_down_reachable")
            f.main{center().select("notifications")};f.waitFor{center().keys().size==3}
            f.main{center().requestKey("notice:NEW_PLUGINS")};focused(f,"notice:NEW_PLUGINS");press(f,KeyEvent.KEYCODE_DPAD_CENTER)
            f.waitFor{NoticeKind.NEW_PLUGINS in m.notices.value.disabled};assertTrue(NoticeKind.NEW_PLUGINS in UpdateNoticeStore(f.context).value.disabled)
            f.main{center().select("reset-notices")};f.waitFor{m.notices.value==NoticeState()};passed("notification_category_and_reset_persist")
            press(f,KeyEvent.KEYCODE_BACK);focused(f,"notifications");press(f,KeyEvent.KEYCODE_BACK);focused(f,"technical")
            f.screenshot("update-hub-home-${f.context.resources.displayMetrics.widthPixels}.png")
            // Run the real signed package staging/activation with cached DEX. Deliberately replace
            // only process restart so instrumentation can inspect and restore its fixture set.
            val ins=f.ins;fun asset(path:String)=ins.context.assets.open("pilot-test/$path").use{it.readBytes()}
            val repository=m.repositories().single();val index=m.index(repository);val plan=listOf("r005","p005").map{id->
                val e=index.packages.single{it.id==id};val raw=asset(e.manifest)
                PackageInstaller.Candidate(repository,UpdateProtocol.module(raw),raw,asset(e.manifest.replace(".json",".sig")).toString(Charsets.US_ASCII))}
            val installer=PackageInstaller(m);withContext(Dispatchers.IO){installer.install(plan)};assertTrue(m.store.snapshot().has("pending"))
            val calls=mutableListOf<String>();InstallCompletion.finish({true},{installer.activate();calls.add("activate")},{calls.add("restart")},{calls.add("defer")})
            assertEquals(listOf("activate","restart"),calls);assertEquals(8,m.store.snapshot().getJSONObject("packages").length());assertTrue(m.store.restartRequired)
            ModuleRuntime.register(m.store.snapshot());assertEquals(4,app.filmmerkezi.tv.core.Sites.all.size);passed("signed_cached_install_activates_all_dependencies_and_requests_restart")
            f.waitFor{center().keys().contains("apply")};f.main{center().select("add")};f.waitFor{center().keys().size==4};f.main{center().focusFirst()};focused(f,"install-selected")
            passed("prepared_install_keeps_finish_action_instead_of_reinstalling")
        }finally{state.value=old;m.notices.update{NoticeState()};m.notices.update{prefs};write(f,"update-hub-ui.json");f.close()}
    }}

    @Test fun noticesHaveFourChoicesAndRespectForegroundAndSavedDecisions()=runBlocking {PilotWorkerTest().withSet{
        val f=Settings110Fixture();val m=DistributionManager.get(f.context);val state=status(m);val old=state.value;val prefs=m.notices.value
        val uiScope=CoroutineScope(SupervisorJob()+Dispatchers.Main);var popup:UpdateNoticePopup?=null
        val large=InstrumentationRegistry.getArguments().getString("largeText")=="true"
        try{
            threeInstalled(m);state.value=DistributionStatus();m.notices.update{NoticeState()}
            f.start(AppSettings(largeText=large),UpdateTestData.empty());f.waitFor{f.activity.hasWindowFocus()}
            // Exercise the Activity's real visibility/setup/settings/player gate, not an always-true test gate.
            f.main{f.activity.settingsForTest();f.activity.noticesForTest()};delay(1800)
            f.main{assertNull(f.activity.noticePopupForTest());f.activity.onBackPressed()};f.waitFor{f.activity.noticePopupForTest()?.showing==true}
            passed("notice_waits_until_settings_are_closed")
            var panel=f.activity.noticePopupForTest()!!.panelForTest()
            val choices=listOf("notice-install","notice-inspect","notice-later","notice-ignore")
            f.waitFor{panel.focusedKey()==choices.first()}
            for(key in choices.drop(1)){press(f,KeyEvent.KEYCODE_DPAD_RIGHT);f.waitFor{panel.focusedKey()==key}}
            f.main{val hint=panel.navigationRootForTest().findViewWithTag<View>("classic-panel:hint");val visible=android.graphics.Rect()
                assertTrue(hint.getGlobalVisibleRect(visible));assertEquals(hint.height,visible.height())};passed("notice_hint_not_clipped_at_current_text_size")
            f.screenshot("update-hub-notice-${f.context.resources.displayMetrics.widthPixels}.png");passed("four_notice_choices_reachable_with_dpad")
            press(f,KeyEvent.KEYCODE_DPAD_CENTER);f.waitFor{f.activity.noticePopupForTest()==null}
            val catalog=withContext(Dispatchers.IO){m.catalog()};val token=catalog.fresh.single().token
            assertTrue(token in m.notices.value.ignored);assertTrue(token in UpdateNoticeStore(f.context).value.ignored)
            f.main{f.activity.noticesForTest(false);f.activity.noticesForTest()};delay(1800);f.main{assertNull(f.activity.noticePopupForTest())};passed("ignore_only_offered_plugins_survives_store_reopen")
            m.notices.update{NoticeState()};f.main{f.activity.noticesForTest(false);f.activity.noticesForTest()};f.waitFor{f.activity.noticePopupForTest()?.showing==true}
            panel=f.activity.noticePopupForTest()!!.panelForTest();f.main{panel.select("notice-later")};f.waitFor{f.activity.noticePopupForTest()==null}
            val until=UpdateNoticeStore(f.context).value.later.getValue(token);assertTrue(until-System.currentTimeMillis() in 86390000L..86400000L)
            f.main{f.activity.noticesForTest(false);f.activity.noticesForTest()};delay(1600);f.main{assertNull(f.activity.noticePopupForTest())};passed("later_saves_twenty_four_hour_snooze")
            f.main{f.activity.noticesForTest(false)};m.notices.update{NoticeState()}
            val release=UpdateProtocol.Release(39,"Local UI test",23,"https://fixture.example/update.apk",1,"0".repeat(64),"1".repeat(64),"")
            val offer=UpdateNotice(NoticeKind.APPLICATION,listOf("app:39"),release=release);var routed:UpdateRoute?=null
            f.main{popup=UpdateNoticePopup(f.activity,uiScope,m.notices,offer,{routed=it})}
            f.waitFor{popup?.panelForTest()?.focusedKey()=="notice-install"};f.main{popup!!.panelForTest().select("notice-install")}
            f.waitFor{routed!=null};assertEquals(UpdatePage.APPLICATION,routed!!.page);assertTrue(routed!!.downloadApplication);assertEquals(39,routed!!.applicationCode)
            passed("application_update_choice_routes_exact_version_without_fabricated_download")
            delay(200);f.waitFor{f.activity.hasWindowFocus()};routed=null;m.notices.update{NoticeState()}
            val pluginOffer=UpdateNotice(NoticeKind.NEW_PLUGINS,listOf(token),catalog.fresh)
            f.main{popup=UpdateNoticePopup(f.activity,uiScope,m.notices,pluginOffer,{routed=it})};f.waitFor{popup?.showing==true}
            f.main{popup!!.panelForTest().select("notice-install")};f.waitFor{routed!=null}
            assertEquals(UpdatePage.CATALOG,routed!!.page);assertEquals(listOf(catalog.fresh.single().key),routed!!.install);passed("install_choice_carries_only_offered_plugin_ids")
            delay(200);f.waitFor{f.activity.hasWindowFocus()};m.notices.update{NoticeState()}
            state.value=DistributionStatus(release=release,appCheck=CheckState.AVAILABLE)
            f.main{f.activity.noticesForTest()};f.waitFor{f.activity.noticePopupForTest()?.showing==true}
            f.main{f.activity.noticePopupForTest()!!.panelForTest().select("notice-ignore")};f.waitFor{f.activity.noticePopupForTest()==null}
            assertTrue("app:39" in UpdateNoticeStore(f.context).value.ignored);assertFalse(token in m.notices.value.ignored);passed("application_ignore_does_not_disable_plugin_notices")
            m.notices.update{NoticeState()};f.main{f.activity.noticesForTest(false);f.activity.noticesForTest()};f.waitFor{f.activity.noticePopupForTest()?.showing==true}
            f.main{f.activity.noticePopupForTest()!!.panelForTest().select("notice-inspect")};f.waitFor{f.tag("updates:update-application")!=null}
            f.main{assertTrue(f.tag("updates:update-application").isEnabled);assertNull(f.activity.noticePopupForTest())};passed("inspect_opens_single_update_action_inside_classic_settings")
            f.main{f.activity.noticesForTest(false)};m.notices.update{NoticeState()};state.value=DistributionStatus()
            val screen=TvActivity::class.java.getDeclaredField("settingsScreen").apply{isAccessible=true}.get(f.activity)
            val panelOwner=SettingsScreen::class.java.getDeclaredField("updates").apply{isAccessible=true}.get(screen) as DistributionPanel
            val repository=m.repositories().single()
            val probe=RepositoryProbe(repository.getString("url"),repository.getString("index").toByteArray(Charsets.UTF_8),repository.getString("signature"),m.index(repository))
            f.main{DistributionPanel::class.java.getDeclaredMethod("trust",RepositoryProbe::class.java).apply{isAccessible=true}.invoke(panelOwner,probe)}
            f.waitFor{panelOwner.dialogForTest()?.isShowing==true}
            f.main{panelOwner.dialogForTest()!!.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()}
            val popupField=DistributionPanel::class.java.getDeclaredField("noticePopup").apply{isAccessible=true}
            f.waitFor{(popupField.get(panelOwner) as? UpdateNoticePopup)?.showing==true}
            f.main{(popupField.get(panelOwner) as UpdateNoticePopup).panelForTest().select("notice-inspect")}
            f.waitFor{f.activity.updateCenterForTest()?.keys()?.count{it.startsWith("catalog:")}==4}
            passed("trusted_signed_repository_offers_uninstalled_plugins_and_inspect_opens_flat_catalog")
        }finally{f.main{popup?.close();f.activity.noticesForTest(false)};uiScope.cancel();state.value=old;m.notices.update{NoticeState()};m.notices.update{prefs};write(f,"update-notices-ui.json");f.close()}
    }}
}
