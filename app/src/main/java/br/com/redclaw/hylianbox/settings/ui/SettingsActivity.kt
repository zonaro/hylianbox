package br.com.redclaw.hylianbox.settings.ui

import android.accounts.AccountManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.hylianbox.HylianBoxApp
import br.com.redclaw.hylianbox.R
import br.com.redclaw.hylianbox.dashboard.server.DashboardManager
import br.com.redclaw.hylianbox.data.local.SaveBackupManager
import br.com.redclaw.hylianbox.data.model.BaseRom
import br.com.redclaw.hylianbox.databinding.ActivitySettingsBinding
import br.com.redclaw.hylianbox.databinding.SettingsBaseRomItemBinding
import br.com.redclaw.hylianbox.databinding.SettingsCatalogUrlItemBinding
import br.com.redclaw.hylianbox.drive.BackupCategory
import br.com.redclaw.hylianbox.drive.ConflictResolveActivity
import br.com.redclaw.hylianbox.drive.ConflictStore
import br.com.redclaw.hylianbox.drive.GoogleDriveAuth
import br.com.redclaw.hylianbox.drive.GoogleDriveBackup
import br.com.redclaw.hylianbox.repositories.Storage
import br.com.redclaw.hylianbox.retroachievements.auth.RaApiKeyFetchResult
import br.com.redclaw.hylianbox.retroachievements.auth.RaApiKeyLoginHelper
import br.com.redclaw.hylianbox.retroachievements.auth.RaCredentialStore
import br.com.redclaw.hylianbox.retroachievements.ui.RaKeyCaptureActivity
import br.com.redclaw.hylianbox.settings.SettingsViewModel
import br.com.redclaw.hylianbox.store.CatalogFetcher
import br.com.redclaw.hylianbox.ui.switchui.AccentManager
import br.com.redclaw.hylianbox.ui.switchui.SwitchBackButton
import br.com.redclaw.hylianbox.ui.switchui.SwitchDialog
import br.com.redclaw.hylianbox.ui.switchui.SwitchImmersive
import br.com.redclaw.hylianbox.utils.CorePrefs
import br.com.redclaw.hylianbox.utils.LanguageManager
import br.com.redclaw.hylianbox.utils.ScaledAppCompatActivity
import br.com.redclaw.hylianbox.utils.UiScaleManager
import br.com.redclaw.hylianbox.views.InstalledLibrary
import com.google.android.gms.auth.UserRecoverableAuthException
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ScaledAppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var viewModel: SettingsViewModel

    /** Shared Switch UI sound-effects manager (null-safe if not yet ready). */
    private val sfx = runCatching { HylianBoxApp.sfxManager }.getOrNull()

    private val backHelper = SwitchBackButton()

    private lateinit var baseRomAdapter: BaseRomAdapter
    private lateinit var catalogUrlAdapter: CatalogUrlAdapter

    /** Portrait-only conventional navigation drawer and its tap-to-dismiss scrim. */
    private var settingsDrawerScrim: View? = null
    private var settingsDrawer: View? = null
    private var isSettingsDrawerOpen = false

    private val pickRomLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode != RESULT_OK) return@registerForActivityResult
                val data = result.data ?: return@registerForActivityResult
                val uris = mutableListOf<Uri>()
                data.data?.let { uris.add(it) }
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
                }
                if (uris.isNotEmpty()) viewModel.importRomsFromUris(uris)
            }

    private val exportBackupLauncher =
            registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
                    uri ->
                if (uri != null) runExportBackup(uri)
            }

    private val importBackupLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) runImportBackup(uri)
            }

    /** Google account chooser for the Drive backup feature. */
    private val pickDriveAccountLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode != RESULT_OK) return@registerForActivityResult
                val name = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                if (name != null) {
                    CorePrefs.setGdriveAccountName(this, name)
                    updateGdriveAccountUi()
                    rescheduleDriveBackup()
                }
            }

    /** OAuth consent recovery intent launched when the token needs user approval. */
    private val driveConsentLauncher =
            registerForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
            ) { /* User can tap "Back up now" again after granting consent. */}

    /** Last typed RA credentials, kept in memory only to feed the key capture. */
    private var lastRaUsername: String = ""
    private var lastRaPassword: String = ""

    private val raKeyCaptureLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                lastRaPassword = ""
                if (result.resultCode == RESULT_OK) {
                    binding.settingsRaApiKey.text.clear()
                    binding.settingsRaStatus.setText(R.string.settings_ra_api_key_auto_saved)
                }
                updateRaStatus(HylianBoxApp.raCredentialStore)
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        backHelper.attach(this, binding.settingsBack.root, onBack = { finish() })
        setupSwitchNavigation()
        applyDynamicAccentToSwitches()

        viewModel = SettingsViewModel(application)

        setupImportSection()
        setupBaseRomList()
        setupCatalogSection()
        setupRetroAchievementsSection()
        setupBackupSection()
        setupGdriveSection()
        setupLanguageSection()
        setupAboutSection()
        setupCaptureSection()
        setupDashboardSection()
        setupAppearanceSection()
        setupDisplaySection()
        wireSettingsSfx()
        observeImport()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        backHelper.onTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        // Refresh the cloud-sync status (e.g. after resolving a conflict in the
        // resolver activity) so the pending-conflict count stays accurate.
        updateCloudSyncStatus()
    }

    /**
     * Matches the System Settings information architecture: landscape keeps the categories
     * permanently visible on the left, while a portrait phone gets a familiar hamburger drawer. The
     * same navigation view is moved at runtime, so both modes always expose exactly the same
     * localized categories.
     */
    private fun setupSwitchNavigation() {
        val navigationTargets =
                listOf(
                        binding.settingsNavImport to binding.settingsSectionImport,
                        binding.settingsNavBaseroms to binding.settingsSectionBaseroms,
                        binding.settingsNavCatalog to binding.settingsSectionCatalog,
                        binding.settingsNavRa to binding.settingsSectionRa,
                        binding.settingsNavBackup to binding.settingsSectionBackup,
                        binding.settingsNavLanguage to binding.settingsSectionLanguage,
                        binding.settingsNavAbout to binding.settingsSectionAbout,
                        binding.settingsNavCapture to binding.settingsSectionCapture,
                        binding.settingsNavDashboard to binding.settingsSectionDashboard,
                        binding.settingsNavAppearance to binding.settingsSectionAppearance,
                        binding.settingsNavDisplay to binding.settingsSectionDisplay
                )

        navigationTargets.forEach { (row, target) ->
            row.setOnClickListener {
                sfx?.select()
                selectSettingsSection(row)
                // Delay until async section content (e.g. the base-ROM RecyclerView)
                // has laid out AND settled; scrolling earlier gets reset to top when
                // that list populates and changes the layout height.
                binding.settingsScroll.postDelayed(
                        { binding.settingsScroll.scrollTo(0, target.top) },
                        600
                )
                if (isPortraitSettings()) closeSettingsDrawer()
            }
            row.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) sfx?.focusMove() }
        }
        selectSettingsSection(binding.settingsNavImport)

        binding.settingsNavigationButton.visibility =
                if (isPortraitSettings()) View.VISIBLE else View.GONE
        if (isPortraitSettings()) installPortraitNavigationDrawer()
    }

    private fun isPortraitSettings(): Boolean =
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private fun selectSettingsSection(selectedRow: View) {
        val rows =
                listOf(
                        binding.settingsNavImport,
                        binding.settingsNavBaseroms,
                        binding.settingsNavCatalog,
                        binding.settingsNavRa,
                        binding.settingsNavBackup,
                        binding.settingsNavLanguage,
                        binding.settingsNavAbout,
                        binding.settingsNavCapture,
                        binding.settingsNavDashboard,
                        binding.settingsNavAppearance,
                        binding.settingsNavDisplay
                )
        val accentColor = AccentManager.getAccentColor(this)
        rows.forEach { row ->
            row.isSelected = row === selectedRow
            // Apply dynamic accent color to selected row background
            if (row === selectedRow) {
                row.background = createSelectedNavBackground(accentColor)
            } else {
                row.background = createDefaultNavBackground()
            }
        }
    }

    /** Creates a background drawable for the selected navigation row (left accent bar). */
    private fun createSelectedNavBackground(
            accentColor: Int
    ): android.graphics.drawable.LayerDrawable {
        val accentBar =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(accentColor)
                }
        val transparent =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(android.graphics.Color.TRANSPARENT)
                }
        val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(transparent, accentBar))
        // Inset the accent bar to be a 3dp wide bar on the left
        layerDrawable.setLayerInset(1, 0, 0, 0, 0)
        return layerDrawable
    }

    /** Creates a default transparent background for non-selected navigation rows. */
    private fun createDefaultNavBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.TRANSPARENT)
        }
    }

    /** Applies the dynamic accent color to all Switch widgets in the settings. */
    private fun applyDynamicAccentToSwitches() {
        val switches =
                listOf(
                        binding.settingsRaEnabledSwitch,
                        binding.settingsGdriveEnabled,
                        binding.settingsGdriveSaves,
                        binding.settingsGdriveImages,
                        binding.settingsGdriveVideos,
                        binding.settingsGdriveAuto,
                        binding.settingsCloudsyncWifi,
                        binding.settingsCloudsyncNotify,
                        binding.settingsCaptureIncludeMicrophone,
                        binding.settingsAppearanceSfx
                )
        switches.forEach { switch ->
            switch.thumbTintList = AccentManager.createSwitchThumbStateList(this)
            switch.trackTintList = AccentManager.createSwitchTrackStateList(this)
        }
    }

    private fun installPortraitNavigationDrawer() {
        val navigation = binding.settingsNavigation
        // The navigation itself is inside a ScrollView. Move that container to
        // the overlay root so the drawer keeps its scrolling behavior and the
        // child is detached before it is re-parented.
        val drawer = navigation.parent as? ViewGroup ?: return
        binding.settingsBody.removeView(drawer)
        binding.settingsNavigationDivider.visibility = View.GONE

        val drawerWidth =
                minOf(
                        (resources.displayMetrics.widthPixels * 0.86f).toInt(),
                        (320 * resources.displayMetrics.density).toInt()
                )
        val scrim =
                View(this).apply {
                    background = ColorDrawable(Color.TRANSPARENT)
                    contentDescription = getString(R.string.settings_close_navigation)
                    isClickable = true
                    isFocusable = true
                    visibility = View.GONE
                    setOnClickListener { closeSettingsDrawer() }
                }
        settingsDrawerScrim = scrim
        binding.settingsRoot.addView(
                scrim,
                FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        )
        drawer.visibility = View.GONE
        drawer.elevation = 12f * resources.displayMetrics.density
        settingsDrawer = drawer
        binding.settingsRoot.addView(
                drawer,
                FrameLayout.LayoutParams(
                        drawerWidth,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.START
                )
        )
        binding.settingsNavigationButton.setOnClickListener { openSettingsDrawer() }
    }

    private fun openSettingsDrawer() {
        if (!isPortraitSettings() || isSettingsDrawerOpen) return
        val drawer = settingsDrawer ?: return
        val scrim = settingsDrawerScrim ?: return
        isSettingsDrawerOpen = true
        scrim.apply {
            setBackgroundColor(getColor(R.color.switch_scrim))
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(220L).start()
        }
        drawer.apply {
            visibility = View.VISIBLE
            translationX =
                    if (width > 0) {
                        -width.toFloat()
                    } else {
                        -resources.displayMetrics.widthPixels.toFloat()
                    }
            animate().translationX(0f).setDuration(220L).start()
            binding.settingsNavImport.requestFocus()
        }
        sfx?.panelOpen()
    }

    private fun closeSettingsDrawer() {
        if (!isSettingsDrawerOpen) return
        val drawer = settingsDrawer ?: return
        val scrim = settingsDrawerScrim ?: return
        isSettingsDrawerOpen = false
        drawer.animate()
                .translationX(-drawer.width.toFloat())
                .setDuration(180L)
                .withEndAction { drawer.visibility = View.GONE }
                .start()
        scrim.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction { scrim.visibility = View.GONE }
                .start()
        sfx?.panelClose()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isSettingsDrawerOpen) {
            closeSettingsDrawer()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Wires the Switch UI sound effects to the primary Settings controls so the screen stays
     * consistent with the other Switch-style surfaces (home row, dock, grid, dialogs). Focus
     * traversal plays the focus-move "toc" and activation plays the select blip. This is additive
     * only — no control flow is changed.
     */
    private fun wireSettingsSfx() {
        val focusViews =
                listOf(
                        binding.settingsImportButton,
                        binding.settingsBackupExport,
                        binding.settingsBackupImport,
                        binding.settingsCatalogAdd,
                        binding.settingsRaLogin,
                        binding.settingsRaLogout,
                        binding.settingsGdriveConnect,
                        binding.settingsGdriveBackupNow,
                        binding.settingsGdriveRestore,
                        binding.settingsGdriveView,
                        binding.settingsGdriveFrequency,
                        binding.settingsLanguageButton,
                        binding.settingsAboutRepo,
                        binding.settingsAboutCatalog,
                        binding.settingsAppearanceTheme,
                        binding.settingsAppearanceAccent
                )
        for (view in focusViews) {
            view.onFocusChangeListener =
                    View.OnFocusChangeListener { _, hasFocus -> if (hasFocus) sfx?.focusMove() }
        }
    }

    private fun setupBackupSection() {
        binding.settingsBackupExport.setOnClickListener {
            sfx?.select()
            val name =
                    "hylianbox_saves_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.zip"
            exportBackupLauncher.launch(name)
        }
        binding.settingsBackupImport.setOnClickListener {
            sfx?.select()
            importBackupLauncher.launch(arrayOf("application/zip"))
        }
    }

    private fun runExportBackup(uri: Uri) {
        val installed = InstalledLibrary.entries(this).map { it.romId }.distinct()
        if (installed.isEmpty()) {
            showBackupResult(getString(R.string.backup_export_empty))
            return
        }
        val storage = Storage.getInstance(this)
        val saves = installed.associateWith { storage.saveFiles(it) }
        val version =
                runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
                        .getOrNull()
                        ?: "?"
        binding.settingsBackupProgress.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val summary =
                    try {
                        contentResolver.openOutputStream(uri)?.use { out ->
                            SaveBackupManager.export(out, saves, version)
                        }
                                ?: SaveBackupManager.BackupSummary(
                                        0,
                                        0,
                                        0,
                                        listOf(getString(R.string.backup_error_stream))
                                )
                    } catch (e: Exception) {
                        SaveBackupManager.BackupSummary(0, 0, 0, listOf(e.message ?: "error"))
                    }
            withContext(Dispatchers.Main) {
                binding.settingsBackupProgress.visibility = View.GONE
                showBackupResult(
                        getString(R.string.backup_export_summary, summary.hacks, summary.files)
                )
            }
        }
    }

    private fun runImportBackup(uri: Uri) {
        val storage = Storage.getInstance(this)
        val resolver: (String, String) -> File? = { hackId, fileName ->
            when {
                fileName.startsWith("sram_") -> storage.sram(hackId)
                fileName.startsWith("state_") -> storage.state(hackId)
                else -> null
            }
        }
        binding.settingsBackupProgress.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val summary =
                    try {
                        contentResolver.openInputStream(uri)?.use { input ->
                            SaveBackupManager.restore(input, resolver)
                        }
                                ?: SaveBackupManager.BackupSummary(
                                        0,
                                        0,
                                        0,
                                        listOf(getString(R.string.backup_error_stream))
                                )
                    } catch (e: Exception) {
                        SaveBackupManager.BackupSummary(0, 0, 0, listOf(e.message ?: "error"))
                    }
            withContext(Dispatchers.Main) {
                binding.settingsBackupProgress.visibility = View.GONE
                val message = buildString {
                    append(
                            getString(
                                    R.string.backup_import_summary,
                                    summary.hacks,
                                    summary.files,
                                    summary.skipped
                            )
                    )
                    if (summary.errors.isNotEmpty()) {
                        append("\n\n")
                        append(summary.errors.joinToString("\n"))
                    }
                }
                showBackupResult(message)
            }
        }
    }

    private fun showBackupResult(message: String) {
        SwitchDialog(this)
                .title(getString(R.string.backup_title))
                .message(message)
                .positiveButton(getString(android.R.string.ok))
                .show()
    }

    // ---- Google Drive cloud backup section ----

    private fun setupGdriveSection() {
        binding.settingsGdriveEnabled.isChecked = CorePrefs.getGdriveEnabled(this)
        binding.settingsGdriveEnabled.setOnCheckedChangeListener { _, checked ->
            CorePrefs.setGdriveEnabled(this, checked)
            updateGdriveEnabledUi()
            rescheduleDriveBackup()
        }
        binding.settingsGdriveSaves.isChecked = CorePrefs.getGdriveBackupSaves(this)
        binding.settingsGdriveSaves.setOnCheckedChangeListener { _, c ->
            CorePrefs.setGdriveBackupSaves(this, c)
        }
        binding.settingsGdriveImages.isChecked = CorePrefs.getGdriveBackupImages(this)
        binding.settingsGdriveImages.setOnCheckedChangeListener { _, c ->
            CorePrefs.setGdriveBackupImages(this, c)
        }
        binding.settingsGdriveVideos.isChecked = CorePrefs.getGdriveBackupVideos(this)
        binding.settingsGdriveVideos.setOnCheckedChangeListener { _, c ->
            CorePrefs.setGdriveBackupVideos(this, c)
        }
        binding.settingsGdriveAuto.isChecked = CorePrefs.getGdriveAutoBackup(this)
        binding.settingsGdriveAuto.setOnCheckedChangeListener { _, c ->
            CorePrefs.setGdriveAutoBackup(this, c)
            rescheduleDriveBackup()
        }

        binding.settingsGdriveConnect.setOnClickListener {
            sfx?.select()
            val intent = GoogleDriveAuth.accountPickerIntent()
            if (intent != null) {
                pickDriveAccountLauncher.launch(intent)
            } else {
                showGdriveDialog(getString(R.string.gdrive_need_account))
            }
        }
        binding.settingsGdriveBackupNow.setOnClickListener { startManualDriveBackup() }
        binding.settingsGdriveRestore.setOnClickListener { confirmDriveRestore() }
        binding.settingsGdriveView.setOnClickListener { viewDriveBackups() }
        binding.settingsGdriveFrequency.setOnClickListener {
            sfx?.select()
            showFrequencyDialog()
        }

        binding.settingsCloudsyncWifi.isChecked = CorePrefs.getCloudSyncWifiOnly(this)
        binding.settingsCloudsyncWifi.setOnCheckedChangeListener { _, checked ->
            CorePrefs.setCloudSyncWifiOnly(this, checked)
            rescheduleDriveBackup()
        }
        binding.settingsCloudsyncNotify.isChecked = CorePrefs.getCloudSyncNotifications(this)
        binding.settingsCloudsyncNotify.setOnCheckedChangeListener { _, checked ->
            CorePrefs.setCloudSyncNotifications(this, checked)
        }
        binding.settingsCloudsyncViewConflicts.setOnClickListener {
            sfx?.select()
            startActivity(Intent(this, ConflictResolveActivity::class.java))
        }

        updateGdriveAccountUi()
        updateGdriveStatus()
        updateGdriveEnabledUi()
        updateGdriveFrequencyUi()
    }

    /** Enable/disable the dependent controls based on the master switch. */
    private fun updateGdriveEnabledUi() {
        val enabled = CorePrefs.getGdriveEnabled(this)
        binding.settingsGdriveGroup.isEnabled = enabled
        for (i in 0 until binding.settingsGdriveGroup.childCount) {
            binding.settingsGdriveGroup.getChildAt(i).isEnabled = enabled
        }
    }

    private fun rescheduleDriveBackup() {
        (application as? HylianBoxApp)?.scheduleDriveBackup()
    }

    /** Reflect the connected account name (or the "none" hint) in the status. */
    private fun updateGdriveAccountUi() {
        val name = CorePrefs.getGdriveAccountName(this)
        binding.settingsGdriveAccount.text =
                if (name != null) {
                    getString(R.string.gdrive_connected, name)
                } else {
                    getString(R.string.gdrive_not_connected)
                }
    }

    /** Show the last successful backup time, or "Nunca" when never run. */
    private fun updateGdriveStatus() {
        val last = CorePrefs.getGdriveLastBackup(this)
        binding.settingsGdriveStatus.text =
                if (last > 0) {
                    getString(R.string.gdrive_last_backup, formatBackupDate(last))
                } else {
                    getString(R.string.gdrive_last_backup_never)
                }
    }

    private fun updateGdriveFrequencyUi() {
        binding.settingsGdriveFrequency.text =
                frequencyLabel(CorePrefs.getGdriveBackupFrequency(this))
    }

    private fun frequencyLabel(value: String): String =
            when (value) {
                CorePrefs.GDRIVE_FREQ_WEEKLY -> getString(R.string.gdrive_frequency_weekly)
                CorePrefs.GDRIVE_FREQ_MANUAL -> getString(R.string.gdrive_frequency_manual)
                else -> getString(R.string.gdrive_frequency_daily)
            }

    /**
     * "Back up now": runs the same orchestrator as the periodic worker, inline, so the progress bar
     * can reflect real upload progress.
     */
    private fun startManualDriveBackup() {
        sfx?.select()
        if (!CorePrefs.getGdriveEnabled(this)) {
            showGdriveDialog(getString(R.string.gdrive_need_enable))
            return
        }
        val accountName = CorePrefs.getGdriveAccountName(this)
        if (accountName == null) {
            showGdriveDialog(getString(R.string.gdrive_need_account))
            return
        }
        val categories = gdriveCategories()
        if (categories.isEmpty()) {
            showGdriveDialog(getString(R.string.gdrive_backup_none))
            return
        }
        val storage = Storage.getInstance(this)
        val hackIds = InstalledLibrary.entries(this).map { it.romId }
        val since = CorePrefs.getGdriveLastBackup(this)

        binding.settingsGdriveProgress.visibility = View.VISIBLE
        binding.settingsGdriveProgress.isIndeterminate = true
        binding.settingsGdriveBackupNow.isEnabled = false
        binding.settingsGdriveView.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val summary =
                    try {
                        GoogleDriveBackup.run(
                                context = this@SettingsActivity,
                                accountName = accountName,
                                saveDir = File(storage.storagePath),
                                galleryDir = storage.galleryDir(),
                                hackIds = hackIds,
                                categories = categories,
                                sinceMillis = since,
                                onItemProgress = { done, total ->
                                    runOnUiThread {
                                        binding.settingsGdriveProgress.isIndeterminate = false
                                        binding.settingsGdriveProgress.max = total.coerceAtLeast(1)
                                        binding.settingsGdriveProgress.progress = done
                                    }
                                }
                        )
                    } catch (e: UserRecoverableAuthException) {
                        runOnUiThread { driveConsentLauncher.launch(e.intent) }
                        null
                    } catch (e: Exception) {
                        runOnUiThread {
                            showGdriveDialog(
                                    getString(R.string.gdrive_backup_failed, e.message ?: "error")
                            )
                        }
                        null
                    }
            withContext(Dispatchers.Main) {
                binding.settingsGdriveProgress.visibility = View.GONE
                binding.settingsGdriveBackupNow.isEnabled = true
                binding.settingsGdriveView.isEnabled = true
                if (summary != null) {
                    // Do not advance the incremental cutoff after a partial failure: failed files
                    // must remain eligible for the next retry.
                    if (summary.errors.isEmpty()) {
                        CorePrefs.setGdriveLastBackup(
                                this@SettingsActivity,
                                System.currentTimeMillis()
                        )
                        updateGdriveStatus()
                        updateCloudSyncStatus()
                    }
                    val msg =
                            if (summary.errors.isNotEmpty()) {
                                getString(
                                        R.string.gdrive_backup_failed,
                                        summary.errors.first()
                                )
                            } else if (summary.uploaded == 0 && summary.deleted == 0) {
                                getString(R.string.gdrive_backup_none)
                            } else {
                                getString(R.string.gdrive_backup_summary, summary.uploaded)
                            }
                    showGdriveDialog(msg)
                }
            }
        }
    }

    private fun confirmDriveRestore() {
        sfx?.select()
        if (!CorePrefs.getGdriveEnabled(this)) {
            showGdriveDialog(getString(R.string.gdrive_need_enable))
            return
        }
        if (CorePrefs.getGdriveAccountName(this) == null) {
            showGdriveDialog(getString(R.string.gdrive_need_account))
            return
        }
        SwitchDialog(this)
                .title(getString(R.string.gdrive_restore))
                .message(getString(R.string.gdrive_restore_confirm))
                .positiveButton(getString(R.string.gdrive_restore_confirm_action)) {
                    startDriveRestore()
                }
                .negativeButton(getString(android.R.string.cancel))
                .show()
    }

    private fun startDriveRestore() {
        val accountName = CorePrefs.getGdriveAccountName(this) ?: return
        val storage = Storage.getInstance(this)
        val installedHackIds = InstalledLibrary.entries(this).map { it.romId }.toSet()
        binding.settingsGdriveProgress.visibility = View.VISIBLE
        binding.settingsGdriveProgress.isIndeterminate = true
        binding.settingsGdriveBackupNow.isEnabled = false
        binding.settingsGdriveRestore.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val summary: Result<GoogleDriveBackup.RestoreSummary>? =
                    try {
                        Result.success(
                                GoogleDriveBackup.restoreSaves(
                                        this@SettingsActivity,
                                        accountName
                                ) { hackId, name ->
                                    if (hackId !in installedHackIds) {
                                        null
                                    } else {
                                        when (name) {
                                            "sram_$hackId" -> storage.sram(hackId)
                                            "state_$hackId" -> storage.state(hackId)
                                            else -> null
                                        }
                                    }
                                }
                        )
                    } catch (error: UserRecoverableAuthException) {
                        runOnUiThread { driveConsentLauncher.launch(error.intent) }
                        null
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
            withContext(Dispatchers.Main) {
                binding.settingsGdriveProgress.visibility = View.GONE
                binding.settingsGdriveBackupNow.isEnabled = true
                binding.settingsGdriveRestore.isEnabled = true
                summary?.fold(
                        onSuccess = {
                            updateCloudSyncStatus()
                            val message = if (it.errors.isEmpty()) {
                                getString(R.string.gdrive_restore_summary, it.restored, it.skipped)
                            } else {
                                getString(R.string.gdrive_restore_summary_errors, it.restored, it.skipped, it.errors.size)
                            }
                            showGdriveDialog(message)
                        },
                        onFailure = {
                            showGdriveDialog(getString(R.string.gdrive_restore_failed, it.message ?: "error"))
                        }
                )
            }
        }
    }

    /** Open the app backup folder in the Drive app / browser. */
    private fun viewDriveBackups() {
        sfx?.select()
        val accountName = CorePrefs.getGdriveAccountName(this)
        if (accountName == null) {
            showGdriveDialog(getString(R.string.gdrive_need_account))
            return
        }
        val service = GoogleDriveBackup.buildService(this, accountName)
        if (service == null) {
            showGdriveDialog(getString(R.string.gdrive_need_account))
            return
        }
        binding.settingsGdriveProgress.visibility = View.VISIBLE
        binding.settingsGdriveProgress.isIndeterminate = true
        lifecycleScope.launch(Dispatchers.IO) {
            val link =
                    try {
                        val id =
                                service.ensureAppFolder { folderId ->
                                    CorePrefs.setGdriveFolderId(this@SettingsActivity, folderId)
                                }
                        service.folderLink(id)
                    } catch (e: UserRecoverableAuthException) {
                        runOnUiThread { driveConsentLauncher.launch(e.intent) }
                        null
                    } catch (e: Exception) {
                        runOnUiThread { showGdriveDialog(getString(R.string.gdrive_open_failed)) }
                        null
                    }
            withContext(Dispatchers.Main) {
                binding.settingsGdriveProgress.visibility = View.GONE
                if (link != null) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                    } catch (_: Exception) {
                        showGdriveDialog(getString(R.string.gdrive_open_failed))
                    }
                }
            }
        }
    }

    private fun gdriveCategories(): Set<BackupCategory> {
        val set = mutableSetOf<BackupCategory>()
        if (CorePrefs.getGdriveBackupSaves(this)) set.add(BackupCategory.SAVES)
        if (CorePrefs.getGdriveBackupImages(this)) set.add(BackupCategory.IMAGES)
        if (CorePrefs.getGdriveBackupVideos(this)) set.add(BackupCategory.VIDEOS)
        return set
    }

    private fun showFrequencyDialog() {
        val values =
                listOf(
                        CorePrefs.GDRIVE_FREQ_DAILY,
                        CorePrefs.GDRIVE_FREQ_WEEKLY,
                        CorePrefs.GDRIVE_FREQ_MANUAL
                )
        val labels = values.map { frequencyLabel(it) }
        val current = values.indexOf(CorePrefs.getGdriveBackupFrequency(this)).coerceAtLeast(0)
        SwitchDialog(this)
                .title(getString(R.string.gdrive_frequency))
                .singleChoice(labels, current) { which ->
                    CorePrefs.setGdriveBackupFrequency(this, values[which])
                    updateGdriveFrequencyUi()
                    rescheduleDriveBackup()
                }
                .negativeButton(getString(android.R.string.cancel))
                .show()
    }

    private fun showGdriveDialog(message: String) {
        SwitchDialog(this)
                .title(getString(R.string.gdrive_subtitle))
                .message(message)
                .positiveButton(getString(android.R.string.ok))
                .show()
    }

    /**
     * Reflect sync state: master switch, connected account, last sync time and pending conflict
     * count. Also toggles the "view conflicts" button.
     */
    private fun updateCloudSyncStatus() {
        val enabled = CorePrefs.getGdriveEnabled(this)
        val account = CorePrefs.getGdriveAccountName(this)
        val conflicts = ConflictStore(this).count()

        binding.settingsCloudsyncViewConflicts.isEnabled = conflicts > 0

        val text =
                when {
                    !enabled -> getString(R.string.cloudsync_status_disabled)
                    account == null -> getString(R.string.cloudsync_status_no_account)
                    conflicts > 0 -> getString(R.string.cloudsync_conflicts_count, conflicts)
                    else -> {
                        val last = maxOf(
                                CorePrefs.getCloudSyncLastSync(this),
                                CorePrefs.getGdriveLastBackup(this)
                        )
                        if (last > 0) {
                            getString(R.string.cloudsync_status_last, formatBackupDate(last))
                        } else {
                            getString(R.string.cloudsync_status_never)
                        }
                    }
                }
        binding.settingsCloudsyncStatus.text = text
    }

    private fun formatBackupDate(epoch: Long): String =
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(epoch))

    private fun setupImportSection() {
        binding.settingsImportButton.setOnClickListener {
            sfx?.select()
            val intent =
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        // .z64 has no registered MIME type, so accept everything and let
                        // RomNormalizer reject non-N64 files during import.
                        type = "*/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
            pickRomLauncher.launch(intent)
        }
    }

    private fun observeImport() {
        viewModel.importState.observe(this) { state ->
            when (state) {
                is SettingsViewModel.ImportUiState.Idle -> {
                    binding.settingsImportProgress.visibility = View.GONE
                }
                is SettingsViewModel.ImportUiState.Importing -> {
                    binding.settingsImportProgress.visibility = View.VISIBLE
                }
                is SettingsViewModel.ImportUiState.Batch -> {
                    binding.settingsImportProgress.visibility = View.GONE
                    showImportResult(state.result)
                    refreshBaseRomList()
                }
            }
        }
    }

    private fun showImportResult(result: SettingsViewModel.ImportBatchResult) {
        val lines = mutableListOf<String>()
        result.successes.forEach { rom ->
            lines.add(
                    getString(
                            R.string.settings_import_success,
                            rom.displayName,
                            rom.gameCode,
                            rom.versionByte.toString(),
                            rom.crc32
                    )
            )
        }
        result.duplicates.forEach { rom ->
            lines.add(getString(R.string.settings_import_duplicate, rom.displayName, rom.crc32))
        }
        result.invalids.forEach { reason ->
            lines.add(getString(R.string.settings_import_invalid, reason))
        }
        val summary =
                getString(
                        R.string.settings_import_summary,
                        result.successes.size,
                        result.duplicates.size,
                        result.invalids.size
                )
        SwitchDialog(this)
                .title(getString(R.string.settings_import_result_title))
                .message("$summary\n\n${lines.joinToString("\n")}")
                .positiveButton(getString(android.R.string.ok))
                .show()
    }

    private fun setupBaseRomList() {
        baseRomAdapter = BaseRomAdapter(mutableListOf()) { rom -> confirmDeleteBaseRom(rom) }
        binding.settingsBaseromList.layoutManager = LinearLayoutManager(this)
        binding.settingsBaseromList.adapter = baseRomAdapter
        refreshBaseRomList()
    }

    private fun refreshBaseRomList() {
        val roms = viewModel.getBaseRoms()
        baseRomAdapter.update(roms)
        binding.settingsBaseromEmpty.visibility = if (roms.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDeleteBaseRom(rom: BaseRom) {
        SwitchDialog(this)
                .title(getString(R.string.settings_baserom_delete_confirm_title))
                .message(
                        getString(
                                R.string.settings_baserom_delete_confirm_message,
                                rom.displayName,
                                rom.crc32
                        )
                )
                .positiveButton(getString(android.R.string.ok)) {
                    viewModel.deleteBaseRom(rom.id)
                    refreshBaseRomList()
                }
                .negativeButton(getString(android.R.string.cancel))
                .show()
    }

    private fun setupCatalogSection() {
        catalogUrlAdapter =
                CatalogUrlAdapter(mutableListOf()) { url -> confirmDeleteCatalogUrl(url) }
        binding.settingsCatalogList.layoutManager = LinearLayoutManager(this)
        binding.settingsCatalogList.adapter = catalogUrlAdapter
        refreshCatalogList()

        binding.settingsCatalogAdd.setOnClickListener {
            sfx?.select()
            val url = binding.settingsCatalogInput.text.toString()
            binding.settingsCatalogError.visibility = View.GONE
            if (viewModel.addCatalogUrl(url)) {
                binding.settingsCatalogInput.text.clear()
                refreshCatalogList()
            } else {
                binding.settingsCatalogError.setText(R.string.settings_catalog_invalid)
                binding.settingsCatalogError.visibility = View.VISIBLE
            }
        }
    }

    private fun refreshCatalogList() {
        val urls = viewModel.getCatalogUrls()
        catalogUrlAdapter.update(urls)
        binding.settingsCatalogEmpty.visibility = if (urls.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDeleteCatalogUrl(url: String) {
        SwitchDialog(this)
                .title(getString(R.string.settings_catalog_remove_confirm_title))
                .message(url)
                .positiveButton(getString(android.R.string.ok)) {
                    viewModel.removeCatalogUrl(url)
                    refreshCatalogList()
                }
                .negativeButton(getString(android.R.string.cancel))
                .show()
    }

    /**
     * RetroAchievements section: master enable switch plus username/password login. The password is
     * used once for the credential exchange and never persisted; only the issued token is stored
     * (encrypted). A separate Web API key (from RetroAchievements Settings > Applications) is
     * fetched automatically after login (with an in-app browser fallback) and stored for the
     * profile screen, which reads the public Web API.
     */
    private fun setupRetroAchievementsSection() {
        val credentials = HylianBoxApp.raCredentialStore
        val authService = HylianBoxApp.raAuthService

        binding.settingsRaEnabledSwitch.isChecked = CorePrefs.getRetroAchievementsEnabled(this)
        binding.settingsRaEnabledSwitch.setOnCheckedChangeListener { _, checked ->
            CorePrefs.setRetroAchievementsEnabled(this, checked)
            updateRaStatus(credentials)
        }

        // Prefill the stored Web API key (masked by the password input type).
        credentials.getApiKey()?.takeIf { it.isNotBlank() }?.let {
            binding.settingsRaApiKey.setText(it)
        }

        binding.settingsRaLogin.setOnClickListener {
            sfx?.select()
            val username = binding.settingsRaUsername.text.toString().trim()
            val password = binding.settingsRaPassword.text.toString()
            if (username.isEmpty() || password.isEmpty()) {
                binding.settingsRaStatus.setText(R.string.settings_ra_error_missing)
                return@setOnClickListener
            }
            // Kept in memory only so the capture fallback can auto-login.
            lastRaUsername = username
            lastRaPassword = password
            binding.settingsRaLogin.isEnabled = false
            binding.settingsRaStatus.setText(R.string.settings_ra_logging_in)
            lifecycleScope.launch {
                val result = authService.login(username, password)
                binding.settingsRaLogin.isEnabled = true
                // Never keep the secret visible on screen after use.
                binding.settingsRaPassword.text.clear()
                result.fold(
                        onSuccess = {
                            binding.settingsRaUsername.text.clear()
                            // Try to fetch the Web API key automatically with
                            // the credentials just typed; never blocks login.
                            binding.settingsRaStatus.setText(R.string.settings_ra_fetching_api_key)
                            when (RaApiKeyLoginHelper.fetchAfterLogin(
                                            applicationContext,
                                            credentials,
                                            username,
                                            password
                                    )
                            ) {
                                RaApiKeyFetchResult.Fetched ->
                                        binding.settingsRaStatus.setText(
                                                R.string.settings_ra_api_key_auto_saved
                                        )
                                RaApiKeyFetchResult.AlreadyStored,
                                RaApiKeyFetchResult.Unavailable -> Unit
                            }
                            updateRaStatus(credentials)
                        },
                        onFailure = { e ->
                            // Surface the sanitized server detail; do NOT call
                            // updateRaStatus here or it would overwrite the error.
                            val detail = e.message?.takeIf { it.isNotBlank() }
                            binding.settingsRaStatus.text =
                                    if (detail != null) {
                                        getString(R.string.settings_ra_status_failed_detail, detail)
                                    } else {
                                        getString(R.string.settings_ra_status_failed)
                                    }
                        }
                )
            }
        }

        binding.settingsRaLogout.setOnClickListener {
            sfx?.select()
            authService.logout()
            lastRaUsername = ""
            lastRaPassword = ""
            updateRaStatus(credentials)
        }

        binding.settingsRaSaveApiKey.setOnClickListener {
            sfx?.select()
            val key = binding.settingsRaApiKey.text.toString().trim()
            if (key.isEmpty()) {
                binding.settingsRaStatus.setText(R.string.settings_ra_api_key_error_missing)
                return@setOnClickListener
            }
            credentials.setApiKey(key)
            binding.settingsRaApiKey.text.clear()
            binding.settingsRaStatus.setText(R.string.settings_ra_api_key_saved)
        }

        binding.settingsRaCaptureApiKey.setOnClickListener {
            sfx?.select()
            val username = lastRaUsername.ifBlank { credentials.getUsername()?.trim().orEmpty() }
            if (username.isBlank() || !credentials.hasCredentials()) {
                binding.settingsRaStatus.setText(R.string.settings_ra_error_missing)
                return@setOnClickListener
            }
            raKeyCaptureLauncher.launch(RaKeyCaptureActivity.intent(this, username, lastRaPassword))
        }

        updateRaStatus(credentials)
    }

    /** Refreshes the RA status line from stored credential state + toggle. */
    private fun updateRaStatus(credentials: RaCredentialStore) {
        val enabled = CorePrefs.getRetroAchievementsEnabled(this)
        binding.settingsRaStatus.setText(
                when {
                    !enabled -> R.string.settings_ra_status_disabled
                    credentials.hasCredentials() && credentials.hasApiKey() ->
                            R.string.settings_ra_status_logged_in
                    credentials.hasCredentials() -> R.string.settings_ra_status_logged_in_no_key
                    else -> R.string.settings_ra_status_logged_out
                }
        )
    }

    private fun setupLanguageSection() {
        updateLanguageLabel()
        binding.settingsLanguageButton.setOnClickListener {
            sfx?.select()
            showLanguageDialog()
        }
    }

    private fun updateLanguageLabel() {
        val code = LanguageManager.getLanguage(this)
        binding.settingsLanguageCurrent.text = LanguageManager.labelFor(this, code)
    }

    private fun showLanguageDialog() {
        val codes = LanguageManager.CODES
        val currentIndex = codes.indexOf(LanguageManager.getLanguage(this)).coerceAtLeast(0)
        val labels = codes.map { LanguageManager.labelFor(this, it) }
        SwitchDialog(this)
                .title(getString(R.string.settings_language_title))
                .singleChoice(labels, currentIndex) { which ->
                    LanguageManager.setLanguage(this, codes[which])
                    updateLanguageLabel()
                }
                .negativeButton(getString(android.R.string.cancel))
                .show()
    }

    private fun setupAboutSection() {
        val versionName =
                runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
                        .getOrNull()
                        ?: "?"
        binding.settingsAboutVersion.text = getString(R.string.settings_about_version, versionName)
        binding.settingsAboutRepo.setOnClickListener {
            sfx?.select()
            openLink("https://github.com/zonaro/hylianbox")
        }
        binding.settingsAboutCatalog.setOnClickListener {
            sfx?.select()
            openLink(CatalogFetcher.DEFAULT_CATALOG_URL)
        }
    }

    /** Configures optional voice mixing for future gameplay recordings. */
    private fun setupCaptureSection() {
        binding.settingsCaptureIncludeMicrophone.isChecked =
                CorePrefs.getCaptureIncludeMicrophone(this)
        binding.settingsCaptureIncludeMicrophone.setOnCheckedChangeListener { _, checked ->
            CorePrefs.setCaptureIncludeMicrophone(this, checked)
        }
    }

    // ---- Dashboard (Self-Hosted Server) section ----

    private fun setupDashboardSection() {
        val enabled = CorePrefs.isDashboardEnabled(this)
        binding.settingsDashboardEnabled.isChecked = enabled
        updateDashboardEnabledUi()

        binding.settingsDashboardEnabled.setOnCheckedChangeListener { _, checked ->
            CorePrefs.setDashboardEnabled(this, checked)
            if (checked) {
                DashboardManager.start(this)
            } else {
                DashboardManager.stop()
            }
            updateDashboardEnabledUi()
            updateDashboardStatus()
        }

        binding.settingsDashboardPort.setText(CorePrefs.getDashboardPort(this).toString())
        binding.settingsDashboardPort.setOnFocusChangeListener { view, hasFocus ->
            if (!hasFocus) {
                val port = (view as? android.widget.EditText)?.text?.toString()?.toIntOrNull()
                if (port != null && port in 1024..65535 && port != CorePrefs.getDashboardPort(this)
                ) {
                    CorePrefs.setDashboardPort(this, port)
                    if (CorePrefs.isDashboardEnabled(this)) DashboardManager.restart(this)
                    updateDashboardStatus()
                }
            }
        }

        binding.settingsDashboardPassword.setText(CorePrefs.getDashboardPassword(this) ?: "")
        binding.settingsDashboardPassword.setOnFocusChangeListener { view, hasFocus ->
            if (!hasFocus) {
                val password = (view as? android.widget.EditText)?.text?.toString().orEmpty()
                if (password != CorePrefs.getDashboardPassword(this)) {
                    CorePrefs.setDashboardPassword(this, password)
                    if (CorePrefs.isDashboardEnabled(this)) DashboardManager.restart(this)
                    updateDashboardStatus()
                }
            }
        }

        updateDashboardStatus()
    }

    private fun updateDashboardEnabledUi() {
        val enabled = CorePrefs.isDashboardEnabled(this)
        binding.settingsDashboardGroup.isEnabled = enabled
        for (i in 0 until binding.settingsDashboardGroup.childCount) {
            binding.settingsDashboardGroup.getChildAt(i).isEnabled = enabled
        }
    }

    private fun updateDashboardStatus() {
        val enabled = CorePrefs.isDashboardEnabled(this)
        binding.settingsDashboardStatus.text =
                if (DashboardManager.isRunning) {
                    getString(R.string.dashboard_status_running, CorePrefs.getDashboardPort(this))
                } else if (enabled && DashboardManager.isStarting) {
                    binding.root.postDelayed(
                            { updateDashboardStatus() },
                            DASHBOARD_STATUS_REFRESH_DELAY_MS
                    )
                    getString(R.string.dashboard_status_starting)
                } else {
                    getString(R.string.dashboard_status_stopped)
                }
        binding.settingsDashboardUrl.text =
                if (DashboardManager.isRunning) {
                    DashboardManager.address
                            ?.let { address ->
                                getString(R.string.dashboard_url_hint, "http://$address")
                            }
                            .orEmpty()
                } else {
                    ""
                }
    }

    private companion object {
        const val DASHBOARD_STATUS_REFRESH_DELAY_MS = 250L
    }

    // ---- Appearance (theme, interface sounds, accent color) section ----

    private fun setupAppearanceSection() {
        updateAppearanceThemeLabel()
        binding.settingsAppearanceTheme.setOnClickListener {
            sfx?.select()
            br.com.redclaw.hylianbox.ui.switchui.ThemeManager.toggle(this)
            updateAppearanceThemeLabel()
        }

        binding.settingsAppearanceSfx.setOnCheckedChangeListener(null)
        binding.settingsAppearanceSfx.isChecked = CorePrefs.getSwitchSfxEnabled(this)
        binding.settingsAppearanceSfx.setOnCheckedChangeListener { _, checked ->
            CorePrefs.setSwitchSfxEnabled(this, checked)
            runCatching { HylianBoxApp.sfxManager }.getOrNull()?.setEnabled(checked)
            if (checked) sfx?.select()
        }

        updateAppearanceAccentLabel()
        binding.settingsAppearanceAccent.setOnClickListener {
            sfx?.select()
            showAccentColorDialog()
        }

        setupAppearanceScale()
    }

    private fun setupAppearanceScale() {
        val initial = CorePrefs.getUiScale(this)
        binding.settingsAppearanceScale.progress = initial - CorePrefs.UI_SCALE_MIN
        updateAppearanceScaleLabel(initial)
        binding.settingsAppearanceScale.setOnSeekBarChangeListener(
                object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                            seekBar: android.widget.SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                    ) {
                        if (fromUser) sfx?.select()
                        val scale =
                                (progress + CorePrefs.UI_SCALE_MIN).coerceIn(
                                        CorePrefs.UI_SCALE_MIN,
                                        CorePrefs.UI_SCALE_MAX
                                )
                        updateAppearanceScaleLabel(scale)
                    }

                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                        val scale =
                                ((seekBar?.progress ?: 0) + CorePrefs.UI_SCALE_MIN).coerceIn(
                                        CorePrefs.UI_SCALE_MIN,
                                        CorePrefs.UI_SCALE_MAX
                                )
                        if (scale == CorePrefs.getUiScale(this@SettingsActivity)) return
                        CorePrefs.setUiScale(this@SettingsActivity, scale)
                        UiScaleManager.recreateToApply(this@SettingsActivity)
                    }
                }
        )
    }

    private fun updateAppearanceScaleLabel(scale: Int) {
        binding.settingsAppearanceScaleValue.text =
                getString(R.string.settings_appearance_scale_value, scale)
    }

    private fun updateAppearanceThemeLabel() {
        val isLight = br.com.redclaw.hylianbox.ui.switchui.ThemeManager.isLight(this)
        binding.settingsAppearanceTheme.text =
                getString(
                        if (isLight) R.string.settings_appearance_theme_to_dark
                        else R.string.settings_appearance_theme_to_light
                )
    }

    private fun updateAppearanceAccentLabel() {
        binding.settingsAppearanceAccentCurrent.text = AccentManager.getCurrentAccentLabel(this)
    }

    private fun showAccentColorDialog() {
        val options = AccentManager.options
        val labels = options.map { getString(it.labelRes) }
        val currentKey = AccentManager.getCurrentAccentKey(this)
        val checkedIndex = options.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
        SwitchDialog(this)
                .title(getString(R.string.settings_appearance_accent_label))
                .singleChoice(labels, checkedIndex) { index ->
                    val chosen = options[index]
                    AccentManager.setAccent(this, chosen.key)
                    updateAppearanceAccentLabel()
                    applyDynamicAccentToSwitches()
                    selectSettingsSection(binding.settingsNavAppearance)
                    recreate()
                }
                .negativeButton(getString(android.R.string.cancel))
                .show()
    }

    // ---- Display (Multi-Monitor) section ----

    private fun setupDisplaySection() {
        // Display output mode spinner
        val outputOptions =
                listOf(
                        getString(R.string.settings_display_auto) to CorePrefs.DISPLAY_AUTO,
                        getString(R.string.settings_display_primary) to CorePrefs.DISPLAY_PRIMARY,
                        getString(R.string.settings_display_secondary) to
                                CorePrefs.DISPLAY_SECONDARY
                )
        val outputAdapter =
                android.widget.ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        outputOptions.map { it.first }
                )
        binding.settingsDisplayOutput.adapter = outputAdapter
        val currentOutput = CorePrefs.getDisplayOutput(this)
        val outputIndex = outputOptions.indexOfFirst { it.second == currentOutput }
        if (outputIndex >= 0) binding.settingsDisplayOutput.setSelection(outputIndex)
        binding.settingsDisplayOutput.onItemSelectedListener =
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                            parent: android.widget.AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long
                    ) {
                        val selected = outputOptions[position].second
                        CorePrefs.setDisplayOutput(this@SettingsActivity, selected)
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }

        // Touch controls placement spinner
        val touchOptions =
                listOf(
                        getString(R.string.settings_display_touch_primary) to
                                CorePrefs.TOUCH_CONTROLS_PRIMARY,
                        getString(R.string.settings_display_touch_secondary) to
                                CorePrefs.TOUCH_CONTROLS_SECONDARY,
                        getString(R.string.settings_display_touch_both) to
                                CorePrefs.TOUCH_CONTROLS_BOTH
                )
        val touchAdapter =
                android.widget.ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        touchOptions.map { it.first }
                )
        binding.settingsDisplayTouch.adapter = touchAdapter
        val currentTouch = CorePrefs.getDisplayTouchControls(this)
        val touchIndex = touchOptions.indexOfFirst { it.second == currentTouch }
        if (touchIndex >= 0) binding.settingsDisplayTouch.setSelection(touchIndex)
        binding.settingsDisplayTouch.onItemSelectedListener =
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                            parent: android.widget.AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long
                    ) {
                        val selected = touchOptions[position].second
                        CorePrefs.setDisplayTouchControls(this@SettingsActivity, selected)
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }

        // Available displays info
        val displayManager =
                getSystemService(android.content.Context.DISPLAY_SERVICE) as
                        android.hardware.display.DisplayManager
        val displays = displayManager.displays
        val secondaryCount = displays.count { it.displayId != android.view.Display.DEFAULT_DISPLAY }
        binding.settingsDisplayDisplaysInfo.text =
                if (secondaryCount > 0) {
                    getString(R.string.settings_display_displays_found, secondaryCount)
                } else {
                    getString(R.string.settings_display_no_external)
                }
    }

    private fun openLink(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private class BaseRomAdapter(
            private val items: MutableList<BaseRom>,
            private val onDelete: (BaseRom) -> Unit
    ) : RecyclerView.Adapter<BaseRomAdapter.ViewHolder>() {

        class ViewHolder(val binding: SettingsBaseRomItemBinding) :
                RecyclerView.ViewHolder(binding.root)

        fun update(newItems: List<BaseRom>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding =
                    SettingsBaseRomItemBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val rom = items[position]
            holder.binding.itemBaseromName.text = rom.displayName
            holder.binding.itemBaseromDetails.text =
                    holder.itemView.context.getString(
                            R.string.settings_baserom_details,
                            rom.gameCode,
                            rom.versionByte.toString(),
                            rom.crc32,
                            formatSize(rom.sizeBytes),
                            rom.sourceName ?: rom.displayName
                    )
            holder.binding.itemBaseromDelete.setOnClickListener {
                runCatching { HylianBoxApp.sfxManager }.getOrNull()?.select()
                onDelete(rom)
            }
        }

        override fun getItemCount() = items.size

        private fun formatSize(bytes: Long): String {
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb >= 1.0) "${DecimalFormat("#0.0").format(mb)} MB" else "$bytes B"
        }
    }

    private class CatalogUrlAdapter(
            private val items: MutableList<String>,
            private val onDelete: (String) -> Unit
    ) : RecyclerView.Adapter<CatalogUrlAdapter.ViewHolder>() {

        class ViewHolder(val binding: SettingsCatalogUrlItemBinding) :
                RecyclerView.ViewHolder(binding.root)

        fun update(newItems: List<String>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding =
                    SettingsCatalogUrlItemBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val url = items[position]
            holder.binding.itemCatalogUrl.text = url
            holder.binding.itemCatalogDelete.setOnClickListener {
                runCatching { HylianBoxApp.sfxManager }.getOrNull()?.select()
                onDelete(url)
            }
        }

        override fun getItemCount() = items.size
    }
}
