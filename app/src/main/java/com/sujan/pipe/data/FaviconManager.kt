package com.sujan.pipe.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Offline-first cached Favicon fetcher for URL bookmarks, search pipes, direct URLs, and search history.
 * Checks RAM LruCache -> local disk cache (`cacheDir/favicons/`) -> fetches 64px PNG on IO thread once per domain.
 */
object FaviconManager {

    private val memoryCache = LruCache<String, Bitmap>(128)
    private val failedHosts = mutableSetOf<String>()

    fun extractHost(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("/")) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val host = runCatching { Uri.parse(withScheme).host }.getOrNull()?.trim()?.lowercase()
        if (host.isNullOrBlank() || !host.contains('.')) return null
        return host
    }

    fun peekMemory(rawUrl: String): Bitmap? {
        val host = extractHost(rawUrl) ?: return null
        return memoryCache.get(host)
    }

    suspend fun loadFavicon(context: Context, rawUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        val host = extractHost(rawUrl) ?: return@withContext null
        memoryCache.get(host)?.let { return@withContext it }

        val dir = File(context.cacheDir, "favicons")
        val safeName = host.replace(Regex("[^a-z0-9._-]"), "_") + ".png"
        val diskFile = File(dir, safeName)

        if (diskFile.exists() && diskFile.length() > 0L) {
            val decoded = runCatching { BitmapFactory.decodeFile(diskFile.absolutePath) }.getOrNull()
            if (decoded != null) {
                memoryCache.put(host, decoded)
                return@withContext decoded
            }
        }

        if (host in failedHosts) return@withContext null

        val endpoints = listOf(
            "https://www.google.com/s2/favicons?domain=$host&sz=64",
            "https://icons.duckduckgo.com/ip3/$host.ico",
        )
        for (endpoint in endpoints) {
            val bmp = fetchBitmapFromUrl(endpoint)
            if (bmp != null) {
                memoryCache.put(host, bmp)
                runCatching {
                    if (!dir.exists()) dir.mkdirs()
                    FileOutputStream(diskFile).use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                }
                return@withContext bmp
            }
        }
        failedHosts.add(host)
        null
    }

    private fun fetchBitmapFromUrl(urlStr: String): Bitmap? = runCatching {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 4_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "pipe-android/2.9")
        }
        try {
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
