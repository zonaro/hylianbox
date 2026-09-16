/*
 * HylianBox - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.hylianbox.views

import android.content.Intent
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import br.com.redclaw.hylianbox.HylianBoxApp
import br.com.redclaw.hylianbox.R
import br.com.redclaw.hylianbox.databinding.ActivityLibraryBinding
import br.com.redclaw.hylianbox.gallery.GalleryActivity
import br.com.redclaw.hylianbox.retroachievements.ui.AchievementsActivity
import br.com.redclaw.hylianbox.retroachievements.ui.RaProfileActivity
import br.com.redclaw.hylianbox.settings.ui.SettingsActivity
import br.com.redclaw.hylianbox.shortcuts.GamePlayHistoryStore
import br.com.redclaw.hylianbox.shortcuts.GameShortcutsManager
import br.com.redclaw.hylianbox.store.ui.StoreActivity
import br.com.redclaw.hylianbox.ui.switchui.AccentManager
import br.com.redclaw.hylianbox.ui.switchui.SwitchDock
import br.com.redclaw.hylianbox.ui.switchui.SwitchGridActivity
import br.com.redclaw.hylianbox.ui.switchui.SwitchHomeRow
import br.com.redclaw.hylianbox.ui.switchui.SwitchImmersive
import br.com.redclaw.hylianbox.utils.ScaledAppCompatActivity
import br.com.redclaw.hylianbox.viewmodels.LibraryMenuController
import br.com.redclaw.hylianbox.viewmodels.LibraryMenuHostDelegate
import coil.load
import coil.transform.CircleCropTransformation
import java.io.File
import kotlinx.coroutines.launch

/**
 * Library home screen, rebuilt in Phase B to match the Nintendo Switch HOME menu aesthetic: a
 * horizontal row of landscape game cards showing the 5 most-recently played installed entries
 * (newest first), with a focused-game label above, a circular "Todos os Jogos" card at the end of
 * the row and a bottom dock of five circular buttons.
 *
 * The home row order is produced by [InstalledLibrary.recentEntries] (which ranks by last-played
 * timestamp descending, played-only, capped at 5, and falls back to the default
 * [InstalledLibrary.entries] order capped at 5 on a fresh install when nothing has ever been
 * played). The full, sortable library lives in
 * [br.com.redclaw.hylianbox.ui.switchui.SwitchGridActivity].
 *
 * All data flow is preserved from the previous grid implementation: the entry list is still
 * produced by [InstalledLibrary.entries] (which merges the vanilla and store sources in the
 * required order), and every existing behavior is kept — import/export save flows, the per-game
 * context menu (long-press / overflow / physical SELECT-X-Y), uninstall, RetroAchievements
 * deep-link, shortcut sync, empty state, and immersive mode. Only the presentation layer changed;
 * the [LibraryMenuController] and [LibraryMenuHost] contracts are untouched.
 *
 * Appearance options (theme, interface sounds, accent color) live in
 * [br.com.redclaw.hylianbox.settings.ui.SettingsActivity] ("Aparência" section); RetroAchievements
 * login lives in its own Settings section.
 */
class LibraryActivity : ScaledAppCompatActivity() {
    private lateinit var binding: ActivityLibraryBinding

    /* Stateless: rebuilt from the source on every (re)create, so process
    death / configuration changes need no saved instance state. */
    private lateinit var items: List<HackLibraryEntry>

    private lateinit var menuController: LibraryMenuController

    /* Shared host logic (launch, SAF save pickers, uninstall, pin,
    achievements). Extracted to [LibraryMenuHostDelegate] so the full-screen
    grid screen reuses the exact same context-menu actions (DRY). */
    private lateinit var menuHost: LibraryMenuHostDelegate

    companion object {
        private const val TAG = "LibraryActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.decorView.setOnApplyWindowInsetsListener { view, windowInsets ->
            view.post { SwitchImmersive.enterFullscreen(this) }
            windowInsets
        }

        menuHost = LibraryMenuHostDelegate(this) { onLibraryChanged() }
        menuController = LibraryMenuController(menuHost)

        items = InstalledLibrary.recentEntries(this)

        setupHomeRow()
        setupDock()
        setupProfileAvatar()
        updateEmptyState()
        syncShortcuts()

        registerInputListener()
    }

    override fun onResume() {
        super.onResume()
        // Rebuild the list so hacks installed in the Store appear on return
        // without needing to recreate the activity. Uses the shared recent-5
        // helper so the home row stays consistent with the context-menu refresh.
        items = InstalledLibrary.recentEntries(this)
        binding.libraryHomeRow.submitList(items)
        loadProfileAvatar()
        updateEmptyState()
        syncShortcuts()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    /**
     * Wire the home row: entry ordering comes from [InstalledLibrary.recentEntries] (the 5
     * most-recently-played entries, newest first; falls back to the default order on a fresh
     * install). Click launches, long-press opens the context menu, and the trailing "Todos os
     * Jogos" card opens the grid.
     */
    private fun setupHomeRow() {
        binding.libraryHomeRow.setOnEntryActivate { menuHost.launchGame(it) }
        binding.libraryHomeRow.setOnEntryMenu { menuController.openMenu(it) }
        binding.libraryHomeRow.setOnAllGamesActivate {
            startActivity(Intent(this, SwitchGridActivity::class.java))
        }
        binding.libraryHomeRow.submitList(items)
    }

    /** Build the dock destinations (Loja, Galeria, RetroAchievements, Controle, Configurações). */
    private fun setupDock() {
        val galleryIconColor = AccentManager.getAccentColor(this)
        val dockItems =
                listOf(
                        SwitchDock.DockItem(
                                R.drawable.ic_store,
                                R.string.dock_store,
                                R.color.switch_dock_icon_store,
                                { startActivity(Intent(this, StoreActivity::class.java)) }
                        ),
                        SwitchDock.DockItem(
                                R.drawable.ic_gallery,
                                R.string.dock_gallery,
                                R.color.switch_accent_focus, // fallback
                                { startActivity(Intent(this, GalleryActivity::class.java)) },
                                galleryIconColor // dynamic accent color
                        ),
                        SwitchDock.DockItem(
                                R.drawable.ic_trophy,
                                R.string.dock_achievements,
                                R.color.switch_accent_amber,
                                { startActivity(Intent(this, AchievementsActivity::class.java)) }
                        ),
                        SwitchDock.DockItem(
                                R.drawable.ic_gamepad,
                                R.string.dock_control,
                                R.color.switch_dock_icon_control,
                                {
                                    if (GamepadTesterActivity.hasConnectedController()) {
                                        startActivity(
                                                Intent(this, GamepadTesterActivity::class.java)
                                        )
                                    } else {
                                        Toast.makeText(
                                                        this,
                                                        R.string.gamepad_tester_connect_controller,
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    }
                                }
                        ),
                        SwitchDock.DockItem(
                                R.drawable.ic_settings,
                                R.string.dock_settings,
                                R.color.switch_text_primary,
                                { startActivity(Intent(this, SettingsActivity::class.java)) }
                        )
                )
        binding.libraryDock.setItems(dockItems)
    }

    /**
     * Puts the signed-in RetroAchievements player at the fixed account entry point in the
     * upper-left corner. The button remains available while logged out so its profile screen can
     * direct the player to sign in.
     */
    private fun setupProfileAvatar() {
        binding.libraryRaAvatar.setOnClickListener {
            HylianBoxApp.sfxManager.select()
            startActivity(Intent(this, RaProfileActivity::class.java))
        }
        binding.libraryRaAvatar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) HylianBoxApp.sfxManager.focusMove()
        }
        loadProfileAvatar()
    }

    /** Loads the cached avatar immediately, then refreshes it from RA. */
    private fun loadProfileAvatar() {
        val credentials = HylianBoxApp.raCredentialStore
        if (!credentials.hasCredentials()) {
            showPlaceholderAvatar()
            return
        }

        val repository = HylianBoxApp.raUserProfileRepository
        // Deterministic fallback (https://media.retroachievements.org/UserPic/<user>.png)
        // guarantees the avatar shows even when the Web API key is not configured
        // or the profile has never been fetched.
        repository.cachedAvatarUrlOrFallback()?.let(::displayProfileAvatar)
                ?: showPlaceholderAvatar()

        // Refresh from the Web API only when a key is available; otherwise the
        // deterministic URL above is already the best we can show.
        if (!credentials.hasApiKey()) return
        lifecycleScope.launch {
            repository.refreshProfile().getOrNull()?.avatarUrl?.let(::displayProfileAvatar)
        }
    }

    private fun showPlaceholderAvatar() {
        binding.libraryRaAvatar.setImageResource(R.drawable.ic_user)
        binding.libraryRaAvatar.setColorFilter(
                androidx.core.content.ContextCompat.getColor(this, R.color.switch_text_primary)
        )
    }

    private fun displayProfileAvatar(url: String) {
        // Clear the placeholder tint so the photo is shown with its original colors.
        binding.libraryRaAvatar.clearColorFilter()
        binding.libraryRaAvatar.load(url) {
            crossfade(true)
            placeholder(R.drawable.ic_user)
            error(R.drawable.ic_user)
            transformations(CircleCropTransformation())
            listener(
                    onError = { _, _ ->
                        // Restore theme-adaptive tint if the avatar fails to load.
                        binding.libraryRaAvatar.setColorFilter(
                                androidx.core.content.ContextCompat.getColor(
                                        this@LibraryActivity,
                                        R.color.switch_text_primary
                                )
                        )
                    },
                    onSuccess = { _, _ -> binding.libraryRaAvatar.clearColorFilter() }
            )
        }
    }

    /**
     * Keep the launcher's dynamic shortcuts in sync with the installed library (and disable any
     * stale pinned shortcuts) whenever the Library is shown.
     */
    private fun syncShortcuts() {
        val history = GamePlayHistoryStore(File(filesDir, "game_play_history.json"))
        GameShortcutsManager(this, history).sync(items)
    }

    /**
     * Rebuild the library list after a mutation performed by [menuHost] (uninstall) and refresh the
     * dependent UI: the home row, the empty state and the dynamic shortcuts. Centralized here so
     * the shared [LibraryMenuHostDelegate] only has to invoke this single callback (DRY).
     */
    private fun onLibraryChanged() {
        // Rebuild via the shared recent-5 helper so a delete/uninstall refresh
        // stays consistent with onCreate/onResume (DRY).
        items = InstalledLibrary.recentEntries(this)
        binding.libraryHomeRow.submitList(items)
        updateEmptyState()
        syncShortcuts()
    }

    private fun updateEmptyState() {
        val isEmpty = items.isEmpty()
        binding.libraryHomeRow.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.libraryEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    /**
     * Route physical gamepad keys when no menu is open: A activates the focused tile / dock button
     * / "Todos os Jogos" card; SELECT/X/Y open the context menu for the focused tile. While the
     * menu is showing, its own decor-view key listener handles keys, so we let those events fall
     * through to the system.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (menuController.isMenuShowing()) {
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val focused = currentFocus
            val tag = focused?.tag
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> {
                    when (tag) {
                        is HackLibraryEntry -> {
                            focused.performClick()
                            return true
                        }
                        is SwitchDock.DockItem -> {
                            focused.performClick()
                            return true
                        }
                        SwitchHomeRow.ALL_GAMES_TAG -> {
                            focused.performClick()
                            return true
                        }
                    }
                }
                KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_BUTTON_Y -> {
                    if (tag is HackLibraryEntry) {
                        menuController.openMenu(tag)
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun registerInputListener() {
        val inputManager = getSystemService(INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(
                object : InputManager.InputDeviceListener {
                    override fun onInputDeviceAdded(deviceId: Int) {
                        menuController.refreshBadges()
                    }
                    override fun onInputDeviceRemoved(deviceId: Int) {
                        menuController.refreshBadges()
                    }
                    override fun onInputDeviceChanged(deviceId: Int) {
                        menuController.refreshBadges()
                    }
                },
                null
        )
    }
}
