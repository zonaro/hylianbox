package br.com.redclaw.hylianbox.store

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadRequestHeadersTest {
    @Test
    fun browserContextIsForwardedToTheSharedDownloadRequest() {
        val request =
                Request.Builder()
                        .url("https://example.com/download")
                        .applyDownloadHeaders(
                                DownloadRequestHeaders(
                                        cookie = "session=private",
                                        userAgent = "HylianBox-WebView",
                                        referer = "https://example.com/release"
                                )
                        )
                        .build()

        assertEquals("session=private", request.header("Cookie"))
        assertEquals("HylianBox-WebView", request.header("User-Agent"))
        assertEquals("https://example.com/release", request.header("Referer"))
    }

    @Test
    fun blankBrowserContextDoesNotCreateHeaders() {
        val request =
                Request.Builder()
                        .url("https://example.com/download")
                        .applyDownloadHeaders(
                                DownloadRequestHeaders(cookie = "", userAgent = " ", referer = null)
                        )
                        .build()

        assertNull(request.header("Cookie"))
        assertNull(request.header("User-Agent"))
        assertNull(request.header("Referer"))
    }
}
