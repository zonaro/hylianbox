/*
 * HylianBox - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.hylianbox.retroachievements.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import br.com.redclaw.hylianbox.utils.ScaledAppCompatActivity
import br.com.redclaw.hylianbox.HylianBoxApp
import br.com.redclaw.hylianbox.R
import br.com.redclaw.hylianbox.databinding.ActivityRaKeyCaptureBinding
import br.com.redclaw.hylianbox.retroachievements.auth.RaWebApiKeyExtractor
import br.com.redclaw.hylianbox.retroachievements.auth.RaWebApiKeyResolver
import br.com.redclaw.hylianbox.ui.switchui.SwitchImmersive
import org.json.JSONObject

/**
 * In-app browser fallback that captures the RetroAchievements Web API key.
 *
 * Used when the silent background fetch ([RaWebApiKeyResolver]) fails (e.g. Cloudflare challenge,
 * login throttle, unexpected page shape). The whole Window is marked FLAG_SECURE so the key never
 * appears in screenshots or screen recordings.
 *
 * Behavior:
 * - When launched with [EXTRA_USERNAME]/[EXTRA_PASSWORD], the page auto-fills and submits the login
 * form once, then navigates to the applications tab.
 * - On every finished page load under retroachievements.org, JavaScript reads
 * `document.documentElement.outerHTML` and hands it to [KeyBridge], which runs
 * [RaWebApiKeyExtractor.extract] off the UI thread.
 * - On success the key is persisted via the encrypted credential store and the Activity finishes
 * with [RESULT_OK]; the caller refreshes its status.
 * - Cookies created here are cleared on destroy so no website session lingers.
 *
 * The key material only exists inside the extractor call scope and the encrypted store — it is
 * never logged.
 */
class RaKeyCaptureActivity : ScaledAppCompatActivity() {

    private lateinit var binding: ActivityRaKeyCaptureBinding
    private var autoLoginAttempted = false
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        )
        binding = ActivityRaKeyCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        setSupportActionBar(binding.raKeyCaptureToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_ra_key_capture_title)
        binding.raKeyCaptureToolbar.setNavigationOnClickListener { finish() }

        setupWebView(
                savedInstanceState,
                intent.getStringExtra(EXTRA_USERNAME).orEmpty(),
                intent.getStringExtra(EXTRA_PASSWORD).orEmpty()
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    override fun onDestroy() {
        binding.raKeyCaptureWebview.apply {
            stopLoading()
            removeJavascriptInterface(BRIDGE_NAME)
            clearHistory()
            clearCache(true)
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun setupWebView(savedInstanceState: Bundle?, username: String, password: String) {
        val webView = binding.raKeyCaptureWebview
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.savePassword = false
        CookieManager.getInstance().setAcceptCookie(true)
        webView.addJavascriptInterface(KeyBridge(), BRIDGE_NAME)
        webView.webChromeClient =
                object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        binding.raKeyCaptureProgress.progress = newProgress
                        binding.raKeyCaptureProgress.visibility =
                                if (newProgress in 1..99) View.VISIBLE else View.GONE
                    }
                }
        webView.webViewClient =
                object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                    ): Boolean {
                        // Keep navigation inside this WebView; external schemes fall
                        // through to the system handler by returning false.
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val current = url.orEmpty()
                        if (!current.startsWith(RA_ORIGIN)) return
                        if (!autoLoginAttempted && isLoginPage(current) && username.isNotBlank()) {
                            autoLoginAttempted = true
                            binding.raKeyCaptureStatus.setText(
                                    R.string.settings_ra_key_capture_logging_in
                            )
                            view?.evaluateJavascript(autoLoginJs(username, password), null)
                        } else {
                            binding.raKeyCaptureStatus.setText(
                                    R.string.settings_ra_key_capture_scanning
                            )
                        }
                        // Hand the full HTML to the bridge for key extraction.
                        view?.evaluateJavascript(
                                "(function(){HylianBoxKeyBridge.onHtml(document.documentElement.outerHTML);})();",
                                null
                        )
                    }
                }
        if (savedInstanceState == null) {
            webView.loadUrl(RaWebApiKeyResolver.SETTINGS_APPLICATIONS_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.raKeyCaptureWebview.saveState(outState)
    }

    private fun isLoginPage(url: String): Boolean =
            url.startsWith("$RA_ORIGIN/login", ignoreCase = true)

    /**
     * Fills the Laravel login form and submits it. Values are escaped for a single-quoted JS
     * string; the password lives only in this evaluated snippet and is never persisted.
     */
    private fun autoLoginJs(username: String, password: String): String {
        val user = JSONObject.quote(username)
        val pass = JSONObject.quote(password)
        return "(function(){var u=document.querySelector('input[name=\"username\"]');" +
                "var p=document.querySelector('input[name=\"password\"]');" +
                "if(!u||!p){return;}" +
                "u.focus();document.execCommand('selectAll',false,null);" +
                "document.execCommand('insertText',false,$user);" +
                "u.dispatchEvent(new Event('input',{bubbles:true}));" +
                "p.focus();document.execCommand('selectAll',false,null);" +
                "document.execCommand('insertText',false,$pass);" +
                "p.dispatchEvent(new Event('input',{bubbles:true}));" +
                "var f=p.form||u.form;" +
                "if(f){f.submit();}})();"
    }

    /** Receives page HTML from JavaScript and extracts the Web API key. */
    private inner class KeyBridge {
        @JavascriptInterface
        fun onHtml(html: String?) {
            val body = html ?: return
            if (RaWebApiKeyExtractor.needsEmailVerification(body)) {
                runOnUiThread {
                    binding.raKeyCaptureStatus.setText(
                            R.string.settings_ra_key_capture_needs_verification
                    )
                }
                return
            }
            val key = RaWebApiKeyExtractor.extract(body) ?: return
            if (finished) return
            finished = true
            HylianBoxApp.raCredentialStore.setApiKey(key)
            runOnUiThread {
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_USERNAME = "extra_ra_username"
        const val EXTRA_PASSWORD = "extra_ra_password"

        private const val BRIDGE_NAME = "HylianBoxKeyBridge"
        private const val RA_ORIGIN = "https://retroachievements.org"

        /** Builds the capture intent; password is held in memory only. */
        fun intent(context: Context, username: String, password: String): Intent =
                Intent(context, RaKeyCaptureActivity::class.java)
                        .putExtra(EXTRA_USERNAME, username)
                        .putExtra(EXTRA_PASSWORD, password)
    }
}
