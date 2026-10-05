package com.prempoddar.campfire.server

import com.prempoddar.campfire.core.LinkPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches link previews from the internet on the host phone, and the preview image too, so
 * members whose devices have no internet still see it. Only public addresses are fetched.
 */
class LinkPreviewer(private val files: FileStore) {

    private val cache = ConcurrentHashMap<String, LinkPreview>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    suspend fun preview(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        cache[url]?.let { return@withContext it }
        if (url in failed) return@withContext null
        val result = runCatching { build(url) }.getOrNull()
        if (result == null) failed += url else cache[url] = result
        result
    }

    private fun build(url: String): LinkPreview? {
        val page = fetch(url, accept = "text/html,application/xhtml+xml", maxBytes = MAX_HTML) ?: return null
        if (!page.contentType.contains("html")) return null
        val parsed = OgParser.parse(page.text(), page.finalUrl)
        if (parsed.title == null && parsed.description == null) return null
        val imageId = parsed.image?.let { imageUrl ->
            val img = runCatching { fetch(imageUrl, accept = "image/*", maxBytes = MAX_IMAGE) }.getOrNull()
            img?.takeIf { it.contentType.startsWith("image/") }
                ?.let { files.saveBytes(it.body, "preview", it.contentType).id }
        }
        return LinkPreview(url = url, title = parsed.title, description = parsed.description, imageId = imageId, site = parsed.site)
    }

    private class Fetched(val finalUrl: String, val contentType: String, val body: ByteArray, val charset: String?) {
        fun text(): String = String(body, runCatching { Charset.forName(charset ?: "UTF-8") }.getOrDefault(Charsets.UTF_8))
    }

    /** GET with manual redirects; every hop must resolve only to public addresses. */
    private fun fetch(start: String, accept: String, maxBytes: Int): Fetched? {
        var current = start
        repeat(MAX_REDIRECTS + 1) {
            val uri = URI(current)
            if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
            val addresses = InetAddress.getAllByName(uri.host)
            if (addresses.isEmpty() || !addresses.all(AddressGuard::isPublic)) return null
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Campfire link preview)")
                setRequestProperty("Accept", accept)
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: return null
                    current = uri.resolve(location).toString()
                    return@repeat
                }
                if (code != 200) return null
                val type = conn.contentType.orEmpty().lowercase()
                val charset = type.substringAfter("charset=", "").substringBefore(';').trim().ifEmpty { null }
                val body = conn.inputStream.use { readCapped(it, maxBytes) } ?: return null
                return Fetched(current, type.substringBefore(';').trim(), body, charset)
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    /** Reads at most [max] bytes; HTML is cut off there, anything else bigger is refused. */
    private fun readCapped(input: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() >= max) return if (max == MAX_HTML) out.toByteArray() else null
        }
        return out.toByteArray()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000
        const val MAX_REDIRECTS = 3
        const val MAX_HTML = 1024 * 1024
        const val MAX_IMAGE = 2 * 1024 * 1024
    }
}
