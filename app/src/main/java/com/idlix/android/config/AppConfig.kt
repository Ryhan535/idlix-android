package com.idlix.android.config

import android.content.Context

object AppConfig {
    private const val PREFS_NAME = "idlix_app_prefs"
    private const val KEY_API_BASE_URL = "key_api_base_url"

    /**
     * Default Base URL jika belum diset oleh user.
     * Default diset ke domain VPS utama: https://api-movie.sasquadofficial.com
     */
    const val DEFAULT_API_BASE_URL: String = "https://api-movie.sasquadofficial.com"

    /**
     * Domain Website IDLIX Utama (digunakan untuk fallback/scraping web langsung)
     */
    const val WEBSITE_BASE_URL: String = "https://z2.idlixku.com"

    /**
     * Mengambil Base URL API dari SharedPreferences atau default.
     */
    fun getApiBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var savedUrl = prefs.getString(KEY_API_BASE_URL, null)
        if (savedUrl.isNullOrEmpty() || savedUrl == "http://192.168.1.69:3000") {
            savedUrl = DEFAULT_API_BASE_URL
            prefs.edit().putString(KEY_API_BASE_URL, DEFAULT_API_BASE_URL).apply()
        }
        return normalizeUrl(savedUrl)
    }

    /**
     * Menyimpan Base URL API baru ke SharedPreferences.
     */
    fun saveApiBaseUrl(context: Context, newUrl: String) {
        val normalized = normalizeUrl(newUrl)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_BASE_URL, normalized).apply()
    }

    /**
     * Normalisasi Base URL API agar tidak ada trailing slash '/' atau duplikasi path '/api'.
     */
    fun normalizeUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.isEmpty()) return DEFAULT_API_BASE_URL
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = if (url.startsWith("192.168.") || url.startsWith("10.0.") || url.startsWith("localhost") || url.contains(":")) {
                "http://$url"
            } else {
                "https://$url"
            }
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length - 1)
        }
        if (url.endsWith("/api")) {
            url = url.substring(0, url.length - 4)
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length - 1)
        }
        return url
    }

    /**
     * Helper fallback tanpa context
     */
    fun getNormalizedApiBaseUrl(): String {
        return normalizeUrl(DEFAULT_API_BASE_URL)
    }
}
