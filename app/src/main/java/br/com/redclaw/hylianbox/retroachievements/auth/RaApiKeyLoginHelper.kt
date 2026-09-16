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

import br.com.redclaw.hylianbox.retroachievements.api.RaUserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of the automatic Web API key fetch attempted right after a password login. */
sealed interface RaApiKeyFetchResult {
    /** The key was resolved and persisted via [RaCredentialStore.setApiKey]. */
    data object Fetched : RaApiKeyFetchResult

    /** A key was already stored; no network call was made. */
    data object AlreadyStored : RaApiKeyFetchResult

    /** The key could not be obtained; the user can retry via browser/manual. */
    data object Unavailable : RaApiKeyFetchResult
}

/**
 * Shared helper (DRY) for the automatic Web API key fetch.
 *
 * Both the dedicated credentials screen and the legacy settings section call [fetchAfterLogin]
 * right after [RaAuthService.login] succeeds: when no key is stored yet, it performs a website
 * login with the same credentials the user just typed and persists the resolved key. The password
 * is used only inside this call scope — never logged, never persisted.
 *
 * The fetch never fails the login: any error maps to [RaApiKeyFetchResult.Unavailable] so the UI
 * can offer the in-app browser capture or the manual paste fallback.
 */
object RaApiKeyLoginHelper {

    /**
     * Fetches and stores the Web API key after a successful password login.
     *
     * @param context Application context for the User-Agent device clause.
     * @param credentials Encrypted credential storage.
     * @param username The username just used to log in (trimmed).
     * @param password The password just used to log in (cleared by callers).
     */
    suspend fun fetchAfterLogin(
            context: android.content.Context,
            credentials: RaCredentialStore,
            username: String,
            password: String
    ): RaApiKeyFetchResult =
            withContext(Dispatchers.IO) {
                if (credentials.hasApiKey()) return@withContext RaApiKeyFetchResult.AlreadyStored
                if (username.isBlank() || password.isEmpty()) {
                    return@withContext RaApiKeyFetchResult.Unavailable
                }
                val resolver = RaWebApiKeyResolver(RaUserAgent.build(context.applicationContext))
                val key = resolver.resolve(username, password)
                if (key.isNullOrBlank()) {
                    RaApiKeyFetchResult.Unavailable
                } else {
                    credentials.setApiKey(key.trim())
                    RaApiKeyFetchResult.Fetched
                }
            }
}
