package br.com.redclaw.hylianbox.store.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import br.com.redclaw.hylianbox.R
import br.com.redclaw.hylianbox.data.local.AppRepositories
import br.com.redclaw.hylianbox.data.local.InstalledHacksRepository
import br.com.redclaw.hylianbox.data.model.Checksums
import br.com.redclaw.hylianbox.data.model.HackEntry
import br.com.redclaw.hylianbox.data.model.PatchRef
import br.com.redclaw.hylianbox.databinding.ActivityWebviewDownloadBinding
import br.com.redclaw.hylianbox.patcher.PatcherFacade
import br.com.redclaw.hylianbox.repositories.Storage
import br.com.redclaw.hylianbox.store.DownloadQueueManager
import br.com.redclaw.hylianbox.store.DownloadRequestHeaders
import br.com.redclaw.hylianbox.store.DownloadTarget
import br.com.redclaw.hylianbox.store.ImportPatchInvalid
import br.com.redclaw.hylianbox.store.ImportPatchNoCompatibleRom
import br.com.redclaw.hylianbox.store.ImportPatchSuccess
import br.com.redclaw.hylianbox.store.ImportPatchUnsupported
import br.com.redclaw.hylianbox.store.ImportRomDuplicate
import br.com.redclaw.hylianbox.store.ImportRomInvalid
import br.com.redclaw.hylianbox.store.ImportRomSuccess
import br.com.redclaw.hylianbox.store.ImportedPatchInstaller
import br.com.redclaw.hylianbox.store.ImportedRomInstaller
import br.com.redclaw.hylianbox.ui.switchui.SwitchImmersive
import br.com.redclaw.hylianbox.utils.ScaledAppCompatActivity
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Embedded WebView for hacks whose catalog entry has no direct patch URL (
 * [br.com.redclaw.hylianbox.store.DownloadTarget.ExternalLink]).
 *
 * Instead of opening the system browser, this Activity loads the source page inside the app and
 * intercepts any download whose URL or Content-Disposition filename ends with a supported
 * extension:
 *
 * - Patch files: `.bps`, `.ips`, `.xdelta` (also inside `.zip`/`.7z`/`.rar` archives)
 * - Direct ROMs: `.n64`, `.z64`, `.v64`
 *
 * When such a file is detected the WebView navigation is cancelled, the file is downloaded with
 * OkHttp (reusing cookies from the WebView), and then installed through the appropriate shared
 * pipeline:
 *
 * - Patches/archives → [DownloadQueueManager], the same process-lifetime download, extraction,
 * validation and patch pipeline used by direct Store links. The browser closes as soon as the item
 * is queued.
 * - ROMs → [ImportedRomInstaller] (normalizes and registers as a base ROM).
 *
 * Archives (`.zip`, `.7z`, `.rar`) are extracted automatically when they contain a supported inner
 * file, via [br.com.redclaw.hylianbox.store.ArchiveExtractor] (shared with the direct download
 * pipeline).
 *
 * The Activity is launched from [HackDetailDialog] with the hack JSON and the initial URL. On
 * success it finishes and the Library will show the new entry.
 */
class WebViewDownloadActivity : ScaledAppCompatActivity() {

    private lateinit var binding: ActivityWebviewDownloadBinding
    private lateinit var hack: HackEntry
    private var isHandlingDownload = false

    companion object {
        const val EXTRA_HACK_JSON = "extra_hack_json"
        const val EXTRA_URL = "extra_url"

        private const val TAG = "WebViewDownload"

        /** Extensions that trigger interception (lowercase, with dot). */
        private val PATCH_EXTS = setOf(".bps", ".ips", ".xdelta")
        private val ROM_EXTS = setOf(".n64", ".z64", ".v64")
        private val ARCHIVE_EXTS = setOf(".zip", ".7z", ".rar")
        private val ALL_INTERCEPT_EXTS = PATCH_EXTS + ROM_EXTS + ARCHIVE_EXTS

        /** Also intercept when Content-Disposition suggests a patch/ROM filename. */
        private fun isInterceptableUrl(url: String): Boolean {
            val lower = url.lowercase().substringBefore('?').substringBefore('#')
            return ALL_INTERCEPT_EXTS.any { lower.endsWith(it) }
        }

        private fun isInterceptableFilename(name: String): Boolean {
            val lower = name.lowercase()
            return ALL_INTERCEPT_EXTS.any { lower.endsWith(it) }
        }

        private fun isPatchFile(name: String): Boolean {
            val lower = name.lowercase()
            return PATCH_EXTS.any { lower.endsWith(it) }
        }

        private fun isRomFile(name: String): Boolean {
            val lower = name.lowercase()
            return ROM_EXTS.any { lower.endsWith(it) }
        }

        private fun isArchiveFile(name: String): Boolean {
            val lower = name.lowercase()
            return ARCHIVE_EXTS.any { lower.endsWith(it) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        val hackJson = intent.getStringExtra(EXTRA_HACK_JSON)
        val initialUrl = intent.getStringExtra(EXTRA_URL)
        if (hackJson.isNullOrBlank() || initialUrl.isNullOrBlank()) {
            finish()
            return
        }
        hack = HackEntry.fromJson(JSONObject(hackJson))

        binding.webviewToolbarTitle.text = hack.name
        binding.webviewBack.setOnClickListener { handleBack() }
        binding.webviewClose.setOnClickListener { finish() }

        val webView = binding.webview
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        CookieManager.getInstance().setAcceptCookie(true)

        webView.webViewClient =
                object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        Log.d(TAG, "shouldOverrideUrlLoading: $url")
                        if (isInterceptableUrl(url)) {
                            val filename = URLUtil.guessFileName(url, null, null)
                            if (isInterceptableFilename(filename) || isInterceptableUrl(url)) {
                                handleDownload(url, filename, null, null)
                                return true
                            }
                        }
                        return false
                    }

                    override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                    ): WebResourceResponse? {
                        // Let the WebView handle normal page loads; download interception
                        // is done via shouldOverrideUrlLoading + DownloadListener.
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        binding.webviewProgress.visibility = View.GONE
                        binding.webviewProgressText.visibility = View.GONE
                    }
                }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
            Log.d(TAG, "DownloadListener: url=$url filename=$filename mime=$mimeType")
            if (isInterceptableFilename(filename) || isInterceptableUrl(url)) {
                handleDownload(url, filename, contentDisposition, mimeType)
            } else {
                // Not a patch/ROM — let the system handle it or ignore.
                Log.d(TAG, "Ignoring non-patch download: $filename")
            }
        }

        webView.loadUrl(initialUrl)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            handleBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleBack() {
        if (binding.webview.canGoBack()) {
            binding.webview.goBack()
        } else {
            finish()
        }
    }

    private fun handleDownload(
            url: String,
            filename: String,
            contentDisposition: String?,
            mimeType: String?
    ) {
        if (isHandlingDownload) {
            Log.d(TAG, "Already handling a download, ignoring: $url")
            return
        }

        if (isPatchFile(filename) ||
                        isArchiveFile(filename) ||
                        PATCH_EXTS.any { url.lowercase().substringBefore('?').endsWith(it) } ||
                        ARCHIVE_EXTS.any { url.lowercase().substringBefore('?').endsWith(it) }
        ) {
            enqueuePatchDownload(url, filename)
            return
        }

        isHandlingDownload = true
        binding.webviewProgress.visibility = View.VISIBLE
        binding.webviewProgress.isIndeterminate = true
        binding.webviewProgressText.visibility = View.VISIBLE
        binding.webviewProgressText.text = getString(R.string.webview_downloading, filename)
        val cookie = CookieManager.getInstance().getCookie(url)
        val userAgent = binding.webview.settings.userAgentString
        val referer = binding.webview.url

        lifecycleScope.launch {
            val result =
                    withContext(Dispatchers.IO) {
                        downloadAndInstall(url, filename, cookie, userAgent, referer)
                    }
            isHandlingDownload = false
            binding.webviewProgress.visibility = View.GONE
            binding.webviewProgressText.visibility = View.GONE

            when (result) {
                is WebViewInstallResult.Success -> {
                    Toast.makeText(
                                    this@WebViewDownloadActivity,
                                    getString(R.string.webview_install_success, result.title),
                                    Toast.LENGTH_LONG
                            )
                            .show()
                    setResult(RESULT_OK)
                    finish()
                }
                is WebViewInstallResult.Error -> {
                    AlertDialog.Builder(this@WebViewDownloadActivity)
                            .setTitle(R.string.webview_install_error_title)
                            .setMessage(result.message)
                            .setPositiveButton(R.string.dialog_ok, null)
                            .show()
                }
            }
        }
    }

    /**
     * Hands a browser-originated patch/archive to the process-lifetime Store queue and closes the
     * WebView immediately. Values that belong to WebView/CookieManager are captured on the main
     * thread before the queue moves network and patch work to its IO scope.
     */
    private fun enqueuePatchDownload(url: String, filename: String) {
        isHandlingDownload = true
        val safeName =
                filename.substringAfterLast('/').substringAfterLast('\\').ifBlank {
                    URLUtil.guessFileName(url, null, null)
                }
        val patch = PatchRef(url, safeName, 0L, Checksums("", null, null))
        val queuedHack =
                hack.copy(patch = patch, downloadTarget = DownloadTarget.DirectPatch(patch))
        val headers =
                DownloadRequestHeaders(
                        cookie = CookieManager.getInstance().getCookie(url),
                        userAgent = binding.webview.settings.userAgentString,
                        referer = binding.webview.url
                )
        val accepted = DownloadQueueManager.enqueueFromBrowser(queuedHack, headers)
        Toast.makeText(
                        this,
                        if (accepted) R.string.webview_download_queued
                        else R.string.webview_download_already_queued,
                        Toast.LENGTH_SHORT
                )
                .show()
        setResult(RESULT_OK)
        finish()
    }

    /**
     * Download [url] to a temp file, then install it as a patch or ROM. Runs on [Dispatchers.IO].
     */
    private suspend fun downloadAndInstall(
            url: String,
            filename: String,
            cookie: String?,
            userAgent: String,
            referer: String?
    ): WebViewInstallResult =
            withContext(Dispatchers.IO) {
                val client =
                        OkHttpClient.Builder()
                                .connectTimeout(30, TimeUnit.SECONDS)
                                .readTimeout(60, TimeUnit.SECONDS)
                                .followRedirects(true)
                                .followSslRedirects(true)
                                .build()

                val requestBuilder = Request.Builder().url(url)
                if (!cookie.isNullOrBlank()) {
                    requestBuilder.header("Cookie", cookie)
                }
                // Some hosts require a Referer or User-Agent to serve downloads.
                requestBuilder.header("User-Agent", userAgent)
                referer?.takeIf { it.isNotBlank() }?.let { requestBuilder.header("Referer", it) }

                val tempFile: File
                try {
                    val response = client.newCall(requestBuilder.build()).execute()
                    if (!response.isSuccessful) {
                        return@withContext WebViewInstallResult.Error(
                                getString(R.string.webview_download_failed, "HTTP ${response.code}")
                        )
                    }
                    val body =
                            response.body
                                    ?: return@withContext WebViewInstallResult.Error(
                                            getString(
                                                    R.string.webview_download_failed,
                                                    "empty body"
                                            )
                                    )

                    // Use the filename from Content-Disposition if available, otherwise the guessed
                    // one.
                    val contentDisp = response.header("Content-Disposition")
                    val finalName =
                            when {
                                contentDisp != null -> URLUtil.guessFileName(url, contentDisp, null)
                                else -> filename
                            }

                    // If the response is HTML (e.g. a landing page), don't treat it as a patch.
                    val contentType = response.header("Content-Type")?.lowercase() ?: ""
                    if (contentType.contains("text/html") && !isInterceptableFilename(finalName)) {
                        return@withContext WebViewInstallResult.Error(
                                getString(
                                        R.string.webview_download_failed,
                                        "unexpected HTML response"
                                )
                        )
                    }

                    tempFile = File(cacheDir, "webview_${System.currentTimeMillis()}_${finalName}")
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    Log.d(
                            TAG,
                            "Downloaded ${tempFile.length()} bytes to ${tempFile.absolutePath} (finalName=$finalName)"
                    )

                    // Handle archives (.zip/.7z/.rar): extract the first patch/ROM inside.
                    val fileToInstall: File
                    val displayName: String
                    if (br.com.redclaw.hylianbox.store.ArchiveExtractor.isArchive(finalName)) {
                        val extracted = tryExtractFromArchive(tempFile, finalName)
                        if (extracted == null) {
                            tempFile.delete()
                            return@withContext WebViewInstallResult.Error(
                                    getString(R.string.webview_no_patch_in_zip, finalName)
                            )
                        }
                        // extracted is a temp file with the inner patch/ROM bytes.
                        tempFile.delete()
                        fileToInstall = extracted
                        displayName = extracted.name
                    } else {
                        fileToInstall = tempFile
                        displayName = finalName
                    }

                    // Dispatch to the correct installer based on file type.
                    val lowerDisplay = displayName.lowercase()
                    val result: WebViewInstallResult =
                            when {
                                PATCH_EXTS.any { lowerDisplay.endsWith(it) } -> {
                                    installPatch(fileToInstall, displayName)
                                }
                                ROM_EXTS.any { lowerDisplay.endsWith(it) } -> {
                                    installRom(fileToInstall, displayName)
                                }
                                else -> {
                                    // Try to detect by content (e.g. BPS magic) even if extension
                                    // is odd.
                                    val format = PatcherFacade.detectPatchFormat(fileToInstall)
                                    if (format != PatcherFacade.PatchFormat.UNKNOWN) {
                                        installPatch(fileToInstall, displayName)
                                    } else {
                                        WebViewInstallResult.Error(
                                                getString(
                                                        R.string.webview_unsupported_file,
                                                        displayName
                                                )
                                        )
                                    }
                                }
                            }

                    // Clean up temp file if not already deleted.
                    if (fileToInstall.exists() && fileToInstall != tempFile) {
                        fileToInstall.delete()
                    }
                    if (tempFile.exists()) tempFile.delete()

                    result
                } catch (e: Exception) {
                    Log.e(TAG, "Download/install failed for $url", e)
                    WebViewInstallResult.Error(
                            getString(
                                    R.string.webview_download_failed,
                                    e.message ?: "unknown error"
                            )
                    )
                }
            }

    /**
     * Extract the first patch/ROM entry from a downloaded archive (`.zip`, `.7z`, `.rar`). Returns
     * a temp file with the extracted bytes, or null if no suitable entry was found.
     */
    private fun tryExtractFromArchive(archiveFile: File, archiveName: String): File? {
        return try {
            val extractor = br.com.redclaw.hylianbox.store.ArchiveExtractor
            // The temp file may carry a generic name: detect the container
            // format from the download's file name instead.
            val format = extractor.formatOf(archiveName)
            val bytes =
                    try {
                        // Try to find a patch file first, then a ROM.
                        extractor.extractFirstMatching(
                                archiveFile,
                                extractor.PATCH_ENTRY_REGEX,
                                format
                        )
                    } catch (_: Exception) {
                        try {
                            extractor.extractFirstMatching(
                                    archiveFile,
                                    ".*\\.(n64|z64|v64)$",
                                    format
                            )
                        } catch (_: Exception) {
                            return null
                        }
                    }
            // Write extracted bytes to a temp file with the correct extension.
            // We need to know the inner filename to preserve the extension.
            val innerName =
                    extractor.findFirstMatchingName(
                            archiveFile,
                            extractor.INSTALLABLE_ENTRY_REGEX,
                            format
                    )
                            ?: "extracted_patch.bps"
            val out =
                    File(
                            cacheDir,
                            "webview_extracted_${System.currentTimeMillis()}_${innerName.substringAfterLast('/')}"
                    )
            out.writeBytes(bytes)
            out
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract from archive: $archiveName", e)
            null
        }
    }

    private suspend fun installPatch(patchFile: File, displayName: String): WebViewInstallResult =
            withContext(Dispatchers.IO) {
                // For patches, we install as a hack tied to the catalog entry's id.
                // Use the hack's id so the Library shows the correct title/cover.
                val baseRomRepo = AppRepositories.baseRomRepository(this@WebViewDownloadActivity)
                val installedRepo = InstalledHacksRepository(File(filesDir, "installed_hacks.json"))
                val userHacksRepo =
                        AppRepositories.userHacksRepository(this@WebViewDownloadActivity)
                val storage = Storage.getInstance(this@WebViewDownloadActivity)

                // Detect format and handle archive-wrapped patches that slipped through.
                val format = PatcherFacade.detectPatchFormat(patchFile)
                if (format == PatcherFacade.PatchFormat.UNKNOWN) {
                    // Maybe it's an archive that wasn't caught earlier (e.g. .bps.zip double
                    // extension).
                    if (br.com.redclaw.hylianbox.store.ArchiveExtractor.isArchive(displayName)) {
                        val extracted = tryExtractFromArchive(patchFile, displayName)
                        if (extracted != null) {
                            val result = installPatch(extracted, extracted.name)
                            extracted.delete()
                            return@withContext result
                        }
                    }
                    return@withContext WebViewInstallResult.Error(
                            getString(R.string.webview_unsupported_file, displayName)
                    )
                }

                // Install via ImportedPatchInstaller but force the hack id to match
                // the catalog entry so the Store/Library dedup works.
                val installer =
                        ImportedPatchInstaller(
                                this@WebViewDownloadActivity,
                                baseRomRepo,
                                installedRepo,
                                userHacksRepo,
                                storage
                        )

                // We need to install with the catalog hack's id, not a generated one.
                // ImportedPatchInstaller generates an id from the filename, so we
                // install normally and then rename the installed entry if needed.
                val result = installer.install(patchFile, hack.name)

                when (result) {
                    is ImportPatchSuccess -> {
                        // If the generated id differs from the catalog id, migrate
                        // the installed ROM and repository entries to the catalog id.
                        if (result.hackId != hack.id) {
                            migrateToCatalogId(
                                    result.hackId,
                                    hack.id,
                                    storage,
                                    installedRepo,
                                    userHacksRepo
                            )
                        }
                        WebViewInstallResult.Success(result.title)
                    }
                    is ImportPatchNoCompatibleRom ->
                            WebViewInstallResult.Error(
                                    getString(
                                            R.string.webview_no_base_rom,
                                            result.expectedCrc32,
                                            result.targetDescription
                                                    ?: getString(R.string.game_unknown)
                                    )
                            )
                    is ImportPatchInvalid -> WebViewInstallResult.Error(result.message)
                    is ImportPatchUnsupported -> WebViewInstallResult.Error(result.message)
                    else -> WebViewInstallResult.Error(getString(R.string.webview_install_failed))
                }
            }

    private suspend fun installRom(romFile: File, displayName: String): WebViewInstallResult =
            withContext(Dispatchers.IO) {
                val baseRomRepo = AppRepositories.baseRomRepository(this@WebViewDownloadActivity)
                val installer = ImportedRomInstaller(this@WebViewDownloadActivity, baseRomRepo)
                val result = installer.install(romFile, displayName)
                when (result) {
                    is ImportRomSuccess -> WebViewInstallResult.Success(result.title)
                    is ImportRomDuplicate -> WebViewInstallResult.Success(result.title)
                    is ImportRomInvalid -> WebViewInstallResult.Error(result.message)
                    else -> WebViewInstallResult.Error(getString(R.string.webview_install_failed))
                }
            }

    /**
     * Migrate an imported hack from [generatedId] to [catalogId] so the Library and Store show it
     * under the catalog entry's identity.
     */
    private fun migrateToCatalogId(
            generatedId: String,
            catalogId: String,
            storage: Storage,
            installedRepo: InstalledHacksRepository,
            userHacksRepo: br.com.redclaw.hylianbox.data.local.UserHacksRepository
    ) {
        try {
            val srcRom = storage.rom(generatedId)
            val dstRom = storage.rom(catalogId)
            if (srcRom.exists() && !dstRom.exists()) {
                srcRom.renameTo(dstRom) ||
                        run {
                            srcRom.copyTo(dstRom, overwrite = true)
                            srcRom.delete()
                        }
            }
            // Migrate installed record.
            val installed = installedRepo.load()[generatedId]
            if (installed != null) {
                installedRepo.unmarkInstalled(generatedId)
                installedRepo.markInstalled(
                        catalogId,
                        installed.version,
                        installed.fileName,
                        installed.canonicalId,
                        installed.patchChecksums
                )
            }
            // Migrate user hack entry.
            val userHacks = AppRepositories.userHacksRepository(this)
            val entry = userHacks.getById(generatedId)
            if (entry != null) {
                userHacks.remove(generatedId)
                userHacks.add(entry.copy(id = catalogId))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate $generatedId -> $catalogId", e)
        }
    }

    private sealed class WebViewInstallResult {
        data class Success(val title: String) : WebViewInstallResult()
        data class Error(val message: String) : WebViewInstallResult()
    }
}
