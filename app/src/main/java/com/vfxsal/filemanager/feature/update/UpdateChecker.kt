package com.vfxsal.filemanager.feature.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
)

/**
 * Talks directly to the GitHub REST API with the platform's own HttpURLConnection/JSONObject -
 * no networking dependency is otherwise pulled into this app, so adding Retrofit/OkHttp just
 * for one endpoint isn't worth it.
 */
object UpdateChecker {

    private const val RELEASES_API_URL =
        "https://api.github.com/repos/VK-VfX/My_File_Manager./releases/latest"

    fun fetchLatestRelease(): ReleaseInfo? = try {
        val connection = (URL(RELEASES_API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "WhatFiles-App")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val json = JSONObject(body)
        val tag = json.optString("tag_name").removePrefix("v")
        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
        }

        if (tag.isBlank() || apkUrl == null) {
            null
        } else {
            ReleaseInfo(versionName = tag, downloadUrl = apkUrl, releaseNotes = json.optString("body"))
        }
    } catch (e: Exception) {
        null
    }

    /** Compares dotted version strings like "2.7.2" numerically, segment by segment, so
     *  "2.10.0" correctly beats "2.9.0" (a plain string compare would get that backwards). */
    fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
