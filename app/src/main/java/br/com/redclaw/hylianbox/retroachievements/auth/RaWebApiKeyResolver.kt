/*
 * HylianBox - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.hylianbox.retroachievements.auth

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolves the RetroAchievements Web API key (`y` query parameter) using the user's website
 * credentials.
 *
 * Background: the rcheevos `login2` exchange only returns a connect token (see
 * `rc_api_login_response_t.api_token`) — never the Web API key. The key lives on the authenticated
 * settings page (`https://retroachievements.org/settings?tab=applications`, an Inertia/React app
 * embedding `userSettings.apiKey` in its page props).
 *
 * Flow: `GET /login` (CSRF `_token` + session cookie) -> `POST /login` (username, password,
 * `_token`) -> `GET /settings?tab=applications` -> [RaWebApiKeyExtractor.extract].
 *
 * A dedicated OkHttp client with its own in-memory cookie jar is used so the website session never
 * leaks into other clients. The password is kept only in the POST body scope and never logged or
 * persisted. Returns null on any failure (network, Cloudflare challenge, throttle, unverified
 * e-mail); callers fall back to the in-app browser capture or manual paste.
 */
class RaWebApiKeyResolver(
        private val userAgent: String,
        private val client: OkHttpClient = defaultClient()
) {

    /**
     * Attempts a website login with [username]/[password] and returns the Web API key, or null when
     * it cannot be obtained. Never throws.
     */
    suspend fun resolve(username: String, password: String): String? =
            withContext(Dispatchers.IO) {
                runCatching { resolveInternal(username, password) }.getOrNull()
            }

    private fun resolveInternal(username: String, password: String): String? {
        if (username.isBlank() || password.isEmpty()) return null

        val loginPage = get(LOGIN_URL) ?: return null
        val csrf = csrfToken(loginPage) ?: return null
        if (!postLogin(username, password, csrf)) return null

        // Legacy docs point at controlpanel.php, which now redirects to
        // /settings (default profile tab). Request the applications tab
        // directly; accept the legacy page as a fallback source.
        get(SETTINGS_APPLICATIONS_URL)?.let { html ->
            if (RaWebApiKeyExtractor.needsEmailVerification(html)) return null
            RaWebApiKeyExtractor.extract(html)?.let {
                return it
            }
        }
        get(LEGACY_CONTROLPANEL_URL)?.let { html ->
            RaWebApiKeyExtractor.extract(html)?.let {
                return it
            }
        }
        return null
    }

    private fun get(url: String): String? {
        val request =
                Request.Builder()
                        .url(url)
                        .header("User-Agent", userAgent)
                        .header("Accept", "text/html,application/xhtml+xml")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .get()
                        .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()?.takeIf { it.isNotBlank() }
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun postLogin(username: String, password: String, csrf: String): Boolean {
        val form =
                FormBody.Builder()
                        .add("_token", csrf)
                        .add("username", username)
                        .add("password", password)
                        .build()
        val request =
                Request.Builder()
                        .url(LOGIN_URL)
                        .header("User-Agent", userAgent)
                        .header("Referer", LOGIN_URL)
                        .header("Accept", "text/html,application/xhtml+xml")
                        .post(form)
                        .build()
        return try {
            client.newCall(request).execute().use { response ->
                // Laravel redirects to the intended page on success (302 to /
                // or /settings); a 200 re-render means validation failed.
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    // Successful logins redirect away from /login; if we land
                    // back on the login form, credentials were rejected.
                    !body.contains("name=\"password\"", ignoreCase = true) ||
                            RaWebApiKeyExtractor.looksLikeApplicationsPage(body)
                } else {
                    response.code in 300..399
                }
            }
        } catch (e: IOException) {
            false
        }
    }

    private fun csrfToken(loginHtml: String): String? {
        val patterns =
                listOf(
                        Regex("""name="_token"\s+value="([^"]+)""""),
                        Regex("""value="([^"]+)"\s+name="_token""""),
                        Regex("""name='_token'\s+value='([^']+)'""")
                )
        for (pattern in patterns) {
            pattern.find(loginHtml)?.let {
                return it.groupValues[1]
            }
        }
        return null
    }

    companion object {
        const val LOGIN_URL = "https://retroachievements.org/login"
        const val SETTINGS_APPLICATIONS_URL =
                "https://retroachievements.org/settings?tab=applications"
        const val LEGACY_CONTROLPANEL_URL = "https://retroachievements.org/controlpanel.php"

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                    .cookieJar(InMemoryCookieJar())
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .build()
        }

        /**
         * Minimal in-memory cookie jar: keeps the website session scoped to this resolver without
         * touching the shared cookie stores.
         */
        private class InMemoryCookieJar : CookieJar {
            private val store = mutableListOf<Cookie>()

            @Synchronized
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                for (cookie in cookies) {
                    store.removeAll { it.name == cookie.name }
                    store.add(cookie)
                }
            }

            @Synchronized
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val now = System.currentTimeMillis()
                store.removeAll { it.expiresAt < now }
                return store.filter { it.matches(url) }
            }
        }
    }
}
