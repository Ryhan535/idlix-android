package com.idlix.android.data.scraper

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.idlix.android.config.AppConfig
import com.idlix.android.data.model.StreamResult
import com.idlix.android.data.model.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class IdlixStreamScraper(
    private val context: Context,
    var baseUrl: String = AppConfig.getNormalizedApiBaseUrl(),
    private val webSiteBaseUrl: String = AppConfig.WEBSITE_BASE_URL
) {
    fun updateBaseUrl(newUrl: String) {
        this.baseUrl = AppConfig.normalizeUrl(newUrl)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Mencoba mengambil stream URL langsung dari endpoint REST API v3 (/api/movie/:slug/stream atau /api/series/:slug/season/:s/episode/:e/stream)
     */
    private fun fetchNativeApiStream(vararg relativePaths: String): StreamResult? {
        val baseUrls = listOf(baseUrl, AppConfig.WEBSITE_BASE_URL).distinct()
        for (base in baseUrls) {
            for (path in relativePaths) {
                try {
                    val req = Request.Builder()
                        .url("$base$path")
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                        .header("Accept", "application/json, text/plain, */*")
                        .build()

                    val res = okHttpClient.newCall(req).execute()
                    if (res.isSuccessful) {
                        val body = res.body?.string()
                        res.close()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val data = if (json.has("data")) json.getJSONObject("data") else json
                            val streamUrl = extractPlayDataStreamUrl(data) ?: data.optString("streamUrl", data.optString("url", data.optString("stream_url", "")))
                            if (streamUrl.isNotEmpty() && streamUrl.startsWith("http") && !streamUrl.contains("/embed/") && !streamUrl.contains("/e/") && !streamUrl.endsWith(".html")) {
                                val subsList = parseSubtitles(data, base)
                                return StreamResult(streamUrl = streamUrl, subtitles = subsList)
                            }
                        }
                    } else {
                        res.close()
                    }
                } catch (_: Exception) {}
            }
        }
        return null
    }

    /**
     * Helper untuk mengekstrak subtitle secara komprehensif dari JSON
     */
    private fun parseSubtitles(jsonObj: JSONObject?, fallbackBaseUrl: String): List<SubtitleTrack> {
        if (jsonObj == null) return emptyList()
        val subsList = mutableListOf<SubtitleTrack>()

        val keysToTry = listOf("subtitles", "captions", "tracks", "subs")
        for (key in keysToTry) {
            val subsArr = jsonObj.optJSONArray(key)
            if (subsArr != null) {
                for (i in 0 until subsArr.length()) {
                    val item = subsArr.opt(i)
                    if (item is JSONObject) {
                        val subUrl = item.optString("url", item.optString("file", item.optString("src", item.optString("link", item.optString("path", "")))))
                        if (subUrl.isNotEmpty()) {
                            val fixedUrl = formatSubUrl(subUrl, fallbackBaseUrl)
                            val lang = item.optString("lang", item.optString("language", item.optString("srclang", item.optString("id", "id"))))
                            val label = item.optString("label", item.optString("name", item.optString("title", item.optString("lang", "Indonesia"))))
                            subsList.add(SubtitleTrack(lang = lang, label = label, url = fixedUrl))
                        }
                    } else if (item is String && item.isNotEmpty()) {
                        val fixedUrl = formatSubUrl(item, fallbackBaseUrl)
                        subsList.add(SubtitleTrack(lang = "id", label = "Indonesia", url = fixedUrl))
                    }
                }
                if (subsList.isNotEmpty()) return subsList
            }

            val subsObj = jsonObj.optJSONObject(key)
            if (subsObj != null) {
                val keys = subsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val valObj = subsObj.opt(k)
                    if (valObj is String && valObj.isNotEmpty()) {
                        val fixedUrl = formatSubUrl(valObj, fallbackBaseUrl)
                        subsList.add(SubtitleTrack(lang = k, label = k.replaceFirstChar { it.uppercase() }, url = fixedUrl))
                    } else if (valObj is JSONObject) {
                        val subUrl = valObj.optString("url", valObj.optString("file", valObj.optString("src", "")))
                        if (subUrl.isNotEmpty()) {
                            val fixedUrl = formatSubUrl(subUrl, fallbackBaseUrl)
                            val label = valObj.optString("label", k.replaceFirstChar { it.uppercase() })
                            val lang = valObj.optString("lang", k)
                            subsList.add(SubtitleTrack(lang = lang, label = label, url = fixedUrl))
                        }
                    }
                }
                if (subsList.isNotEmpty()) return subsList
            }
        }
        return subsList
    }

    private fun formatSubUrl(rawUrl: String, fallbackBaseUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (trimmed.startsWith("//")) return "https:$trimmed"
        val base = AppConfig.normalizeUrl(fallbackBaseUrl)
        return if (trimmed.startsWith("/")) "$base$trimmed" else "$base/$trimmed"
    }

    /**
     * Extract stream URL for a movie or episode using native REST API v3 or background WebView fallback
     */
    suspend fun getMovieStream(slug: String): StreamResult = withContext(Dispatchers.IO) {
        val nativeStream = fetchNativeApiStream("/api/movie/$slug/stream", "/api/movies/$slug/stream")
        if (nativeStream != null) return@withContext nativeStream

        withContext(Dispatchers.Main) {
            val pageUrl = "$webSiteBaseUrl/movie/$slug"
            extractStreamChain(pageUrl, "movie", slug)
        }
    }

    suspend fun getEpisodeStream(slug: String, season: Int, episode: Int): StreamResult = withContext(Dispatchers.IO) {
        val nativeStream = fetchNativeApiStream("/api/series/$slug/season/$season/episode/$episode/stream")
        if (nativeStream != null) return@withContext nativeStream

        withContext(Dispatchers.Main) {
            val pageUrl = "$webSiteBaseUrl/series/$slug/season/$season/episode/$episode"
            extractStreamChain(pageUrl, "episode", slug, season, episode)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun extractStreamChain(
        pageUrl: String,
        type: String,
        slug: String,
        season: Int = 1,
        episode: Int = 1
    ): StreamResult = suspendCancellableCoroutine { continuation ->
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        var isFinished = false
        val cleanup = {
            mainHandler.post {
                webView.stopLoading()
                webView.destroy()
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (isFinished) return

                val cookieManager = CookieManager.getInstance()
                val cookies = cookieManager.getCookie(pageUrl) ?: ""

                Thread {
                    try {
                        val uuid = if (type == "movie") {
                            resolveMovieUuid(slug, cookies, pageUrl)
                        } else {
                            resolveEpisodeUuid(slug, season, episode, cookies, pageUrl)
                        }

                        if (uuid.isNullOrEmpty()) {
                            if (!isFinished) {
                                isFinished = true
                                cleanup()
                                continuation.resume(StreamResult(null, error = "Failed to resolve content ID"))
                            }
                            return@Thread
                        }

                        trackView(type, uuid, cookies, pageUrl)
                        
                        val apiContentType = if (type == "movie") "movie" else "series"
                        var playInfo = getPlayInfo(apiContentType, uuid, cookies, pageUrl)
                        var gateToken = extractGateToken(playInfo)
                        
                        if (gateToken.isNullOrEmpty() && type != "movie") {
                            playInfo = getPlayInfo("tv", uuid, cookies, pageUrl)
                            gateToken = extractGateToken(playInfo)
                        }

                        if (gateToken.isNullOrEmpty()) {
                            if (!isFinished) {
                                isFinished = true
                                cleanup()
                                continuation.resume(StreamResult(null, error = "Failed to obtain play token (invalid gateToken)"))
                            }
                            return@Thread
                        }

                        val claimResult = claimSession(gateToken, cookies, pageUrl)
                        val claim = extractField(claimResult, "claim")
                        val redeemUrl = extractField(claimResult, "redeemUrl")

                        if (claim.isNullOrEmpty() || redeemUrl.isNullOrEmpty()) {
                            val errMsg = if (claimResult != null && claimResult.has("message")) claimResult.optString("message") else (if (claimResult != null && claimResult.has("error")) claimResult.optString("error") else "Failed to claim stream session")
                            if (!isFinished) {
                                isFinished = true
                                cleanup()
                                continuation.resume(StreamResult(null, error = errMsg))
                            }
                            return@Thread
                        }

                        val playData = redeemClaim(redeemUrl!!, claim!!)
                        val streamUrl = extractPlayDataStreamUrl(playData)

                        if (!streamUrl.isNullOrEmpty()) {
                            var subsList = parseSubtitles(playData, baseUrl)
                            if (subsList.isEmpty()) {
                                subsList = parseSubtitles(claimResult, baseUrl)
                            }
                            if (subsList.isEmpty()) {
                                subsList = parseSubtitles(playInfo, baseUrl)
                            }

                            if (!isFinished) {
                                isFinished = true
                                cleanup()
                                continuation.resume(
                                    StreamResult(
                                        streamUrl = streamUrl,
                                        subtitles = subsList,
                                        videoId = extractField(playData, "videoId"),
                                        title = extractField(claimResult, "title")
                                    )
                                )
                            }
                        } else {
                            if (!isFinished) {
                                isFinished = true
                                cleanup()
                                continuation.resume(StreamResult(null, error = "Stream URL extraction returned empty"))
                            }
                        }
                    } catch (e: Exception) {
                        if (!isFinished) {
                            isFinished = true
                            cleanup()
                            continuation.resume(StreamResult(null, error = e.localizedMessage ?: "Scraping error"))
                        }
                    }
                }.start()
            }
        }

        webView.loadUrl(pageUrl)
    }

    private fun resolveMovieUuid(slug: String, cookies: String, referer: String): String? {
        val path = "/api/movie/$slug"
        val baseUrls = listOf(baseUrl, AppConfig.WEBSITE_BASE_URL).distinct()
        for (base in baseUrls) {
            try {
                val req = Request.Builder()
                    .url("$base$path")
                    .header("Cookie", cookies)
                    .header("Referer", referer)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                    .build()

                val res = okHttpClient.newCall(req).execute()
                if (res.isSuccessful) {
                    val body = res.body?.string()
                    res.close()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val id = if (json.has("id")) json.optString("id") else (json.optJSONObject("data")?.optString("id") ?: "")
                        if (id.isNotEmpty()) return id
                    }
                } else {
                    res.close()
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun resolveEpisodeUuid(slug: String, season: Int, episode: Int, cookies: String, referer: String): String? {
        val path = "/api/series/$slug/season/$season"
        val baseUrls = listOf(baseUrl, AppConfig.WEBSITE_BASE_URL).distinct()
        for (base in baseUrls) {
            try {
                val req = Request.Builder()
                    .url("$base$path")
                    .header("Cookie", cookies)
                    .header("Referer", referer)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                    .build()

                val res = okHttpClient.newCall(req).execute()
                if (res.isSuccessful) {
                    val body = res.body?.string()
                    res.close()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        var eps: JSONArray? = null
                        if (json.has("data")) {
                            eps = json.optJSONArray("data")
                            if (eps == null) {
                                eps = json.optJSONObject("data")?.optJSONArray("episodes")
                            }
                        }
                        if (eps == null && json.has("episodes")) {
                            eps = json.optJSONArray("episodes")
                        }
                        if (eps == null && json.has("season")) {
                            eps = json.optJSONObject("season")?.optJSONArray("episodes")
                        }

                        if (eps != null) {
                            for (i in 0 until eps.length()) {
                                val ep = eps.getJSONObject(i)
                                val epNum = ep.optInt("episodeNumber", ep.optInt("episode", i + 1))
                                if (epNum == episode) {
                                    val epId = ep.optString("id", ep.optString("_id", ""))
                                    if (epId.isNotEmpty()) return epId
                                }
                            }
                        }
                    }
                } else {
                    res.close()
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun trackView(type: String, uuid: String, cookies: String, referer: String) {
        try {
            val jsonBody = JSONObject()
            jsonBody.put("contentType", if (type == "movie") "movie" else "tv_series")
            jsonBody.put("contentId", uuid)

            val body = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$baseUrl/api/views/track")
                .post(body)
                .header("Cookie", cookies)
                .header("Referer", referer)
                .header("Origin", baseUrl)
                .build()

            okHttpClient.newCall(req).execute().close()
        } catch (_: Exception) {}
    }

    private fun getPlayInfo(type: String, uuid: String, cookies: String, referer: String): JSONObject? {
        val req = Request.Builder()
            .url("$baseUrl/api/watch/play-info/$type/$uuid")
            .header("Cookie", cookies)
            .header("Referer", referer)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            .build()

        val res = okHttpClient.newCall(req).execute()
        val body = res.body?.string() ?: return null
        return JSONObject(body)
    }

    private fun claimSession(gateToken: String, cookies: String, referer: String): JSONObject? {
        val jsonBody = JSONObject()
        jsonBody.put("gateToken", gateToken)

        val body = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$baseUrl/api/watch/session/claim")
            .post(body)
            .header("Cookie", cookies)
            .header("Referer", referer)
            .header("Origin", baseUrl)
            .build()

        val res = okHttpClient.newCall(req).execute()
        val resBody = res.body?.string() ?: return null
        return JSONObject(resBody)
    }

    private fun redeemClaim(redeemUrl: String, claim: String): JSONObject? {
        val jsonBody = JSONObject()
        jsonBody.put("claim", claim)

        val body = jsonBody.toString().toRequestBody("text/plain".toMediaType())
        val req = Request.Builder()
            .url(redeemUrl)
            .post(body)
            .header("Origin", baseUrl)
            .header("Referer", "$baseUrl/")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            .build()

        val res = okHttpClient.newCall(req).execute()
        val resBody = res.body?.string() ?: return null
        return JSONObject(resBody)
    }

    private fun extractGateToken(playInfo: JSONObject?): String? {
        if (playInfo == null) return null
        var token = playInfo.optString("gateToken", playInfo.optString("token", playInfo.optString("playToken", "")))
        if (token.isEmpty() && playInfo.has("data")) {
            val data = playInfo.optJSONObject("data")
            if (data != null) {
                token = data.optString("gateToken", data.optString("token", data.optString("playToken", "")))
            }
        }
        return if (token.isNotEmpty()) token else null
    }

    private fun extractField(json: JSONObject?, key: String): String? {
        if (json == null) return null
        var valStr = json.optString(key, "")
        if (valStr.isEmpty() && json.has("data")) {
            val data = json.optJSONObject("data")
            if (data != null) {
                valStr = data.optString(key, "")
            }
        }
        return if (valStr.isNotEmpty()) valStr else null
    }

    private fun extractPlayDataStreamUrl(playData: JSONObject?): String? {
        if (playData == null) return null
        var url = playData.optString("url", playData.optString("streamUrl", playData.optString("file", playData.optString("stream_url", "")))).trim()
        if (url.isEmpty() && playData.has("data")) {
            val data = playData.optJSONObject("data")
            if (data != null) {
                url = data.optString("url", data.optString("streamUrl", data.optString("file", data.optString("stream_url", "")))).trim()
            }
        }
        if (url.isEmpty()) return null
        if (url.startsWith("//")) return "https:$url"
        if (url.startsWith("/")) {
            val normalizedBase = AppConfig.normalizeUrl(baseUrl)
            return "$normalizedBase$url"
        }
        return if (url.startsWith("http")) url else null
    }
}
