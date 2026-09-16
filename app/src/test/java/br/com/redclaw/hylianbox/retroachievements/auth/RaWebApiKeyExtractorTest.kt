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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaWebApiKeyExtractorTest {

    private val fullKey = "m4kwx5AbCdEfGhIjKlMnOpQrSt2jmknF"

    @Test
    fun extractsRawJsonApiKey() {
        val html = """<div>{"userSettings":{"apiKey":"$fullKey"}}</div>"""
        assertEquals(fullKey, RaWebApiKeyExtractor.extract(html))
    }

    @Test
    fun extractsHtmlEscapedApiKey() {
        val html =
                """<div id="app" data-page="{&quot;userSettings&quot;:{&quot;apiKey&quot;:&quot;$fullKey&quot;}}"></div>"""
        assertEquals(fullKey, RaWebApiKeyExtractor.extract(html))
    }

    @Test
    fun extractsBackslashEscapedApiKey() {
        val html =
                """<div id="app" data-page="{\"userSettings\":{\"apiKey\":\"$fullKey\"}}"></div>"""
        assertEquals(fullKey, RaWebApiKeyExtractor.extract(html))
    }

    @Test
    fun maskedButtonAloneYieldsNull() {
        // The copy button only shows "m4kwx5...2jmknF"; it must never be
        // mistaken for the real key.
        val html = """<button><span class="font-mono">m4kwx5...2jmknF</span></button>"""
        assertNull(RaWebApiKeyExtractor.extract(html))
    }

    @Test
    fun blankAndUnrelatedYieldNull() {
        assertNull(RaWebApiKeyExtractor.extract(""))
        assertNull(RaWebApiKeyExtractor.extract("<html><body>hello</body></html>"))
    }

    @Test
    fun detectsApplicationsPage() {
        assertTrue(
                RaWebApiKeyExtractor.looksLikeApplicationsPage(
                        "<title>settings?tab=applications</title><span>Web API Key</span>"
                )
        )
        assertFalse(RaWebApiKeyExtractor.looksLikeApplicationsPage("<body>login</body>"))
    }

    @Test
    fun detectsEmailVerificationGate() {
        assertTrue(
                RaWebApiKeyExtractor.needsEmailVerification(
                        "Verify your email address to manage API keys."
                )
        )
        assertFalse(RaWebApiKeyExtractor.needsEmailVerification(fullKey))
    }
}
