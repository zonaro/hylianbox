package br.com.redclaw.hylianbox.ui.switchui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchCloseLayoutContractTest {
    private val layoutDir = File("src/main/res/layout")

    @Test
    fun screenAndDialogLayoutsReuseTheSharedCloseComponent() {
        val surfaceLayouts =
                layoutDir.listFiles().orEmpty().filter {
                    it.name.startsWith("activity_") ||
                            it.name.startsWith("dialog_") ||
                            it.name == "tracker_dialog.xml" ||
                            it.name == "switch_dialog.xml"
                }
        val offenders = surfaceLayouts.filter { "@drawable/ic_close" in it.readText() }

        assertTrue(
                "Close icons must come from switch_back_button.xml: ${offenders.map { it.name }}",
                offenders.isEmpty()
        )
    }

    @Test
    fun closeIncludePrecedesTheHeaderTitleOnEveryExplicitHeader() {
        val contracts =
                mapOf(
                        "activity_settings.xml" to "settings_toolbar_title",
                        "activity_store.xml" to "store_toolbar_title",
                        "activity_download_queue.xml" to "download_queue_toolbar_title",
                        "activity_achievements.xml" to "achievements_toolbar_title",
                        "activity_ra_profile.xml" to "ra_profile_toolbar_title",
                        "activity_webview_download.xml" to "webview_toolbar_title",
                        "dialog_game_menu.xml" to "menu_title",
                        "dialog_achievements.xml" to "dialog_achievements_title",
                        "tracker_dialog.xml" to "tracker_title",
                        "dialog_hack_detail.xml" to "detail_name",
                        "switch_dialog.xml" to "dialog_title"
                )

        contracts.forEach { (fileName, titleId) ->
            val xml = File(layoutDir, fileName).readText()
            val closeIndex = xml.indexOf("@layout/switch_back_button")
            val titleIndex = xml.indexOf(titleId)
            assertTrue("$fileName must include the shared close button", closeIndex >= 0)
            assertTrue("$fileName close must precede $titleId", closeIndex < titleIndex)
        }
    }

    @Test
    fun settingsDoesNotMixNativeAndManualToolbarTitles() {
        val layout = File(layoutDir, "activity_settings.xml").readText()
        val activity =
                File(
                                "src/main/java/br/com/redclaw/hylianbox/settings/ui/SettingsActivity.kt"
                        )
                        .readText()

        assertTrue("Settings keeps one explicit title", "settings_toolbar_title" in layout)
        assertFalse("Settings toolbar must not declare a native title", "app:title=" in layout)
        assertFalse("Settings must not install the toolbar as ActionBar", "setSupportActionBar" in activity)
    }
}
