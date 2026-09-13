package br.com.redclaw.hylianbox.store

/**
 * Phases reported while installing a hack. [DOWNLOADING] streams the patch from
 * the network; [PATCHING] applies it against the base ROM (no byte progress).
 *
 * Kept in its own file (no Android dependencies) so the pure-Kotlin scheduling
 * core ([DownloadQueueEngine]) can reference it from JVM unit tests.
 */
enum class InstallPhase { DOWNLOADING, PATCHING }
