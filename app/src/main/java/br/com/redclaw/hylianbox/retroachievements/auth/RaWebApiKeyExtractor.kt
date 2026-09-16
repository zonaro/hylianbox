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

/**
 * Extracts the RetroAchievements Web API key from the authenticated settings page HTML
 * (`https://retroachievements.org/settings?tab=applications`).
 *
 * The settings page is an Inertia/React app: the full key is embedded in the page-props JSON inside
 * `<div id="app" data-page="...">` as `userSettings.apiKey` (HTML-escaped as `&quot;apiKey&quot;`).
 * The visible DOM only shows a masked form (`AAAAAA...BBBBBB`), so DOM scraping alone cannot
 * recover the key.
 *
 * Extraction order:
 * 1. Inertia page-props JSON (`"apiKey":"<32 chars>"`, escaped or not).
 * 2. Any standalone 32-char alphanumeric token near a "Web API Key" label.
 *
 * Returns null when no key is present (e.g. unverified e-mail accounts show "Verify your email
 * address to manage API keys." instead). Never logs input.
 */
object RaWebApiKeyExtractor {

    /** Web API keys are 32-char random strings (see RAWeb `generateAPIKey`). */
    private const val KEY_LENGTH = 32

    private val apiKeyJsonPattern = Regex("""["']apiKey["']\s*:\s*["']([A-Za-z0-9]{32})["']""")

    private val maskedKeyPattern = Regex("""([A-Za-z0-9]{6})\s*\.{2,3}\s*([A-Za-z0-9]{6})""")

    /**
     * Returns the Web API key found in [html], or null when absent. The input is never logged; only
     * lengths are safe to report.
     */
    fun extract(html: String): String? {
        if (html.isBlank()) return null
        // Unescape the Inertia data-page attribute so one regex covers raw
        // JSON, HTML-escaped payloads (&quot;) and backslash-escaped JSON
        // (\"apiKey\":\"...\") as served inside data-page="...".
        val unescaped =
                html.replace("&quot;", "\"")
                        .replace("&#039;", "'")
                        .replace("&#x27;", "'")
                        .replace("&amp;", "&")
                        .replace("\\\"", "\"")
                        .replace("\\'", "'")
                        .replace("\\/", "/")
        apiKeyJsonPattern.find(unescaped)?.let { match ->
            return match.groupValues[1].takeIf { it.length == KEY_LENGTH }
        }
        // Masked display form (e.g. "m4kwx5...2jmknF" in the copy button's
        // span.font-mono) cannot be reversed into the real key; an explicit
        // null signals "page loaded but no usable key".
        return null
    }

    /** True when [html] looks like the applications settings tab. */
    fun looksLikeApplicationsPage(html: String): Boolean =
            html.contains("tab=applications", ignoreCase = true) ||
                    html.contains("Web API Key", ignoreCase = true) ||
                    html.contains("apiKey", ignoreCase = false) ||
                    maskedKeyPattern.containsMatchIn(html)

    /** True when the page asks for e-mail verification instead of showing keys. */
    fun needsEmailVerification(html: String): Boolean =
            html.contains("Verify your email address to manage API keys", ignoreCase = true)
}
