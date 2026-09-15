package br.com.redclaw.hylianbox.store

import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * Resolves a concrete patch download URL from a GitHub releases page at download time (not during
 * catalog load, to keep store refresh fast and offline-friendly).
 *
 * Given a `github.com/owner/repo` (or `.../releases`, `.../releases/tag/X`) URL it queries the
 * GitHub Releases API and **always returns an asset from the latest release** (newest
 * `published_at` / first in API order) that contains a patch-like file (`*.bps`, `*.ips`,
 * `*.xdelta`, `*.zip`, `*.7z`, `*.rar`, case-insensitive). This guarantees that hacks distributed
 * via GitHub Releases (e.g. Ocarina of Time DX) always download the newest patch, even when the
 * catalog's pinned URL or version string is stale.
 *
 * Within the latest release that has patch assets, the best asset is chosen by ranking: prefer
 * `.bps` > `.ips` > `.xdelta` > `.zip` > `.7z` > `.rar`, prefer `21-9`/`UWS` (OOT DX ultrawide) and
 * `n64` variants, de-prefer `wii`/`4-3`/`SD`, and prefer assets whose download path contains
 * `dist/`. On any failure or API rate-limit it returns null so the caller can fall back to opening
 * the page in a browser.
 *
 * Resolutions are cached in memory for the process lifetime.
 */
class GitHubPatchResolver(private val client: OkHttpClient = OkHttpClient.Builder().build()) {

    private val cache = mutableMapOf<String, String?>()
    private val patchNamePattern =
            Pattern.compile(".*\\.(bps|ips|xdelta|zip|7z|rar)$", Pattern.CASE_INSENSITIVE)
    private val ownerRepoPattern =
            Pattern.compile("github\\.com/([^/]+)/([^/?#]+)", Pattern.CASE_INSENSITIVE)

    /**
     * Resolve [repoUrl] to a direct patch asset URL from the latest release, or null if none found.
     */
    suspend fun resolve(repoUrl: String): String? =
            withContext(Dispatchers.IO) {
                cache[repoUrl]?.let {
                    return@withContext it
                }

                val match = ownerRepoPattern.matcher(repoUrl)
                if (!match.find()) return@withContext cacheAndReturn(repoUrl, null)
                val owner = match.group(1)!!
                val repo = match.group(2)!!.removeSuffix(".git")

                val apiUrl = "https://api.github.com/repos/$owner/$repo/releases"
                val request =
                        Request.Builder()
                                .url(apiUrl)
                                .header("Accept", "application/vnd.github+json")
                                .header("User-Agent", "HylianBox")
                                .build()

                val body =
                        try {
                            val response = client.newCall(request).execute()
                            try {
                                if (!response.isSuccessful)
                                        return@withContext cacheAndReturn(repoUrl, null)
                                response.body?.string().orEmpty()
                            } finally {
                                response.close()
                            }
                        } catch (_: Exception) {
                            return@withContext cacheAndReturn(repoUrl, null)
                        }

                val resolved = runCatching { pickLatestAsset(body) }.getOrNull()
                cacheAndReturn(repoUrl, resolved)
            }

    private data class AssetCandidate(val name: String, val url: String, val size: Long)

    private fun pickLatestAsset(releasesJson: String): String? {
        val releases = runCatching { JSONArray(releasesJson) }.getOrNull() ?: return null
        // GitHub API returns releases newest-first; we respect that order and
        // pick the first (latest) release that has any patch-like asset.
        for (i in 0 until releases.length()) {
            val release = runCatching { releases.getJSONObject(i) }.getOrNull() ?: continue
            if (release.optBoolean("draft", false)) continue
            val assets = runCatching { release.getJSONArray("assets") }.getOrNull() ?: continue
            val candidates = mutableListOf<AssetCandidate>()
            for (j in 0 until assets.length()) {
                val asset = runCatching { assets.getJSONObject(j) }.getOrNull() ?: continue
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                if (name.isBlank() || url.isBlank()) continue
                if (!patchNamePattern.matcher(name).matches()) continue
                val size = asset.optLong("size", 0L)
                candidates.add(AssetCandidate(name, url, size))
            }
            if (candidates.isEmpty()) continue
            return selectBest(candidates)?.url
        }
        return null
    }

    private fun selectBest(candidates: List<AssetCandidate>): AssetCandidate? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()
        // Prefer dist/ path first (legacy behavior)
        candidates.firstOrNull { it.url.contains("/dist/", ignoreCase = true) }?.let {
            return it
        }
        return candidates.maxByOrNull { score(it) }
    }

    private fun score(c: AssetCandidate): Int {
        var s = 0
        val lower = c.name.lowercase()
        // Extension preference: bps > ips > xdelta > zip > 7z > rar
        s +=
                when {
                    lower.endsWith(".bps") -> 40
                    lower.endsWith(".ips") -> 30
                    lower.endsWith(".xdelta") -> 20
                    lower.endsWith(".zip") -> 10
                    lower.endsWith(".7z") -> 5
                    lower.endsWith(".rar") -> 4
                    else -> 0
                }
        // OOT DX: prefer 21-9 UWS (ultrawide) which is the catalog's chosen variant
        if (lower.contains("21-9") || lower.contains("uws")) s += 25
        else if (lower.contains("16-9") || lower.contains("ws")) s += 10
        // N64 vs Wii: prefer n64 for HylianBox (N64 emulator)
        if (lower.contains("n64")) s += 15
        if (lower.contains("wii")) s -= 10
        // De-prefer SD / 4-3 for OOT DX when ultrawide is available
        if (lower.contains("4-3") || lower.contains(".sd.")) s -= 5
        // Slight tie-breaker by size (larger patch often means more content, but not decisive)
        s += (c.size / (1024 * 1024)).toInt().coerceIn(0, 5)
        return s
    }

    private fun cacheAndReturn(key: String, value: String?): String? {
        cache[key] = value
        return value
    }
}
