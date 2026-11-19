package com.chrononotes.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val tag: String,
    val name: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
)

object UpdateChecker {

    private const val LATEST_URL =
        "https://api.github.com/repos/masipenya1308/ChronoNotes/releases/latest"

    suspend fun latest(): AppRelease = withContext(Dispatchers.IO) {
        val connection = URL(LATEST_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.inputStream.use { stream ->
            val json = JSONObject(stream.bufferedReader().readText())
            val assets = json.optJSONArray("assets")
            var apkUrl = json.optString("html_url")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", apkUrl)
                        break
                    }
                }
            }
            AppRelease(
                tag = json.optString("tag_name"),
                name = json.optString("name").ifBlank { json.optString("tag_name") },
                notes = json.optString("body"),
                pageUrl = json.optString("html_url"),
                apkUrl = apkUrl,
            )
        }
    }

    /** Сравнивает `v1.2.0` и `1.2.0` по числовым частям. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = numbers(latest)
        val b = numbers(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun numbers(version: String): List<Int> =
        Regex("\\d+").findAll(version).map { it.value.toIntOrNull() ?: 0 }.toList()
}
