package com.idlix.android.data.scraper

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
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
    companion object {
        private const val TAG = "IdlixScraper"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    fun updateBaseUrl(newUrl: String) {
        this.baseUrl = AppConfig.normalizeUrl(newUrl)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun getTargetBaseUrls(): List<String> {
        return listOf(webSiteBaseUrl, baseUrl, AppConfig.DEFAULT_API_BASE_URL).distinct()
    }

    /**
     * Mencoba mengambil stream URL langsung dari endpoint REST API v3 (/api/movie/:slug/stream atau /api/series/:slug/season/:s/episode/:e/stream)
     */
    private fun fetchNativeApiStream(vararg relativePaths: String): StreamResult? {
        val baseUrls = getTargetBaseUrls()
        for (base in baseUrls) {
            for (path in relativePaths) {
                try {
                    val fullUrl = "$base$path"
                    Log.d(TAG, "fetchNativeApiStream requesting: $fullUrl")
                    val req = Request.Builder()
                        .url(fullUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json, text/plain, */*")
                        .build()

                    val res = okHttpClient.newCall(req).execute()
                    val body = res.body?.string()
                    res.close()
                    Log.d(TAG, "fetchNativeApiStream res code=${res.code} body len=${body?.length ?: 0}")
                    if (!body.isNullOrEmpty() && body.trim().startsWith("{")) {
                        val json = JSONObject(body)
                        val data = if (json.has("data")) json.optJSONObject("data") ?: json else json
                        val streamUrl = extractPlayDataStreamUrl(data) ?: data.optString("streamUrl", data.optString("url", data.optString("stream_url", "")))
                        if (streamUrl.isNotEmpty() && streamUrl.startsWith("http") && !streamUrl.contains("/embed/") && !streamUrl.contains("/e/") && !streamUrl.endsWith(".html")) {
                            val subsList = parseSubtitles(data, base)
                            Log.d(TAG, "fetchNativeApiStream SUCCESS streamUrl=$streamUrl")
                            return StreamResult(streamUrl = streamUrl, subtitles = subsList)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "fetchNativeApiStream error on $base$path: ${e.message}")
                }
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
        Log.d(TAG, "getMovieStream started for slug: $slug")
        val nativeStream = fetchNativeApiStream("/api/movie/$slug/stream", "/api/movies/$slug/stream")
        if (nativeStream != null) return@withContext nativeStream

        var result = withContext(Dispatchers.Main) {
            val pageUrl = "$webSiteBaseUrl/movie/$slug"
            extractStreamChain(pageUrl, "movie", slug)
        }

        if (result.streamUrl.isNullOrEmpty()) {
            Log.w(TAG, "getMovieStream attempt 1 failed (${result.error}), auto retrying...")
            result = withContext(Dispatchers.Main) {
                val pageUrl = "$webSiteBaseUrl/movie/$slug"
                extractStreamChain(pageUrl, "movie", slug)
            }
        }

        result
    }

    suspend fun getEpisodeStream(slug: String, season: Int, episode: Int): StreamResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "getEpisodeStream started for slug: $slug S${season}E${episode}")
        val nativeStream = fetchNativeApiStream("/api/series/$slug/season/$season/episode/$episode/stream")
        if (nativeStream != null) return@withContext nativeStream

        var result = withContext(Dispatchers.Main) {
            val pageUrl = "$webSiteBaseUrl/series/$slug/season/$season/episode/$episode"
            extractStreamChain(pageUrl, "episode", slug, season, episode)
        }

        if (result.streamUrl.isNullOrEmpty()) {
            Log.w(TAG, "getEpisodeStream attempt 1 failed (${result.error}), auto retrying...")
            result = withContext(Dispatchers.Main) {
                val pageUrl = "$webSiteBaseUrl/series/$slug/season/$season/episode/$episode"
                extractStreamChain(pageUrl, "episode", slug, season, episode)
            }
        }

        result
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun extractStreamChain(
        pageUrl: String,
        type: String,
        slug: String,
        season: Int = 1,
        episode: Int = 1
    ): StreamResult = suspendCancellableCoroutine { continuation ->
        Log.d(TAG, "extractStreamChain pageUrl=$pageUrl type=$type slug=$slug S${season}E${episode}")
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = USER_AGENT

        var isFinished = false
        val cleanup = {
            mainHandler.post {
                webView.stopLoading()
                webView.destroy()
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "onPageFinished for $url")
                if (isFinished) return

                val cookieManager = CookieManager.getInstance()

                Thread {
                    try {
                        // Allow JS scripts on cold load to run and set cookies
                        Thread.sleep(400)

                        var uuid: String? = null
                        var playInfo: JSONObject? = null
                        var gateToken: String? = null
                        var currentCookies = ""

                        val apiContentType = if (type == "movie") "movie" else "series"

                        // Poll up to 8 attempts (8-10 seconds total) for JS environment & cookies to settle on cold load
                        var attempts = 0
                        while (attempts < 8) {
                            attempts++
                            mainHandler.post { cookieManager.flush() }
                            Thread.sleep(200)
                            currentCookies = cookieManager.getCookie(pageUrl) ?: ""
                            Log.d(TAG, "Polling attempt #$attempts | Cookies len: ${currentCookies.length}")

                            if (uuid.isNullOrEmpty()) {
                                uuid = if (type == "movie") {
                                    resolveMovieUuid(slug, currentCookies, pageUrl)
                                } else {
                                    resolveEpisodeUuid(slug, season, episode, currentCookies, pageUrl)
                                }
                            }

                            if (!uuid.isNullOrEmpty()) {
                                trackView(type, uuid, currentCookies, pageUrl)
                                val playInfoTypes = if (type == "movie") listOf("movie") else listOf("episode", "series", "tv", "tv_series")
                                for (pType in playInfoTypes) {
                                    val candidatePlayInfo = getPlayInfo(pType, uuid, currentCookies, pageUrl)
                                    val token = extractGateToken(candidatePlayInfo)
                                    if (!token.isNullOrEmpty()) {
                                        playInfo = candidatePlayInfo
                                        gateToken = token
                                        break
                                    }
                                }

                                if (!gateToken.isNullOrEmpty()) {
                                    Log.d(TAG, "gateToken obtained on attempt #$attempts: $gateToken")
                                    break
                                }
                            }

                            Thread.sleep(1000)
                        }

                        if (uuid.isNullOrEmpty()) {
                            Log.e(TAG, "Failed to resolve content ID for $slug")
                            if (!isFinished) {
                                isFinished = true
                                cleanup()
                                continuation.resume(StreamResult(null, error = "Failed to resolve content ID"))
                            }
                            return@Thread
                        }

                        if (gateToken.isNullOrEmpty()) {
                            Log.e(TAG, "Failed to obtain play token (invalid gateToken)")
                            if (!isFinished) {
                                isFinished = true
                                cleanup()
                                continuation.resume(StreamResult(null, error = "Failed to obtain play token (invalid gateToken)"))
                            }
                            return@Thread
                        }

                        val claimResult = claimSession(gateToken, currentCookies, pageUrl)
                        val claim = extractField(claimResult, "claim")
                        val redeemUrl = extractField(claimResult, "redeemUrl")

                        if (claim.isNullOrEmpty() || redeemUrl.isNullOrEmpty()) {
                            val errMsg = if (claimResult != null && claimResult.has("message")) claimResult.optString("message") else (if (claimResult != null && claimResult.has("error")) claimResult.optString("error") else "Failed to claim stream session")
                            Log.e(TAG, "Claim session failed: $errMsg")
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
                            Log.d(TAG, "Stream URL SUCCESS: $streamUrl")
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
                            Log.e(TAG, "Stream URL extraction returned empty, playData=$playData")
                            if (!isFinished) {
                                isFinished = true
                                cleanup()
                                continuation.resume(StreamResult(null, error = "Stream URL extraction returned empty"))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Scraping error: ${e.message}", e)
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
        val targets = getTargetBaseUrls()
        for (base in targets) {
            try {
                val url = "$base$path"
                Log.d(TAG, "resolveMovieUuid requesting: $url")
                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("Referer", referer)
                    .header("User-Agent", USER_AGENT)
                if (cookies.isNotEmpty()) reqBuilder.header("Cookie", cookies)

                val res = okHttpClient.newCall(reqBuilder.build()).execute()
                val body = res.body?.string()
                res.close()
                Log.d(TAG, "resolveMovieUuid res code=${res.code} body len=${body?.length ?: 0}")
                if (!body.isNullOrEmpty() && body.trim().startsWith("{")) {
                    val json = JSONObject(body)
                    val id = if (json.has("id")) json.optString("id") else (json.optJSONObject("data")?.optString("id") ?: "")
                    if (id.isNotEmpty()) {
                        Log.d(TAG, "resolveMovieUuid SUCCESS id=$id from $base")
                        return id
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "resolveMovieUuid error on $base: ${e.message}")
            }
        }
        return null
    }

    private fun resolveEpisodeUuid(slug: String, season: Int, episode: Int, cookies: String, referer: String): String? {
        val path = "/api/series/$slug/season/$season"
        val targets = getTargetBaseUrls()
        for (base in targets) {
            try {
                val url = "$base$path"
                Log.d(TAG, "resolveEpisodeUuid requesting: $url")
                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("Referer", referer)
                    .header("User-Agent", USER_AGENT)
                if (cookies.isNotEmpty()) reqBuilder.header("Cookie", cookies)

                val res = okHttpClient.newCall(reqBuilder.build()).execute()
                val body = res.body?.string()
                res.close()
                Log.d(TAG, "resolveEpisodeUuid res code=${res.code} body len=${body?.length ?: 0}")
                if (!body.isNullOrEmpty() && body.trim().startsWith("{")) {
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
                                if (epId.isNotEmpty()) {
                                    Log.d(TAG, "resolveEpisodeUuid SUCCESS epId=$epId from $base")
                                    return epId
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "resolveEpisodeUuid error on $base: ${e.message}")
            }
        }
        return null
    }

    private fun trackView(type: String, uuid: String, cookies: String, referer: String) {
        val targets = getTargetBaseUrls()
        val jsonBody = JSONObject()
        jsonBody.put("contentType", if (type == "movie") "movie" else "tv_series")
        jsonBody.put("contentId", uuid)
        val body = jsonBody.toString().toRequestBody("application/json".toMediaType())

        for (base in targets) {
            try {
                val reqBuilder = Request.Builder()
                    .url("$base/api/views/track")
                    .post(body)
                    .header("Referer", referer)
                    .header("Origin", base)
                    .header("User-Agent", USER_AGENT)
                if (cookies.isNotEmpty()) reqBuilder.header("Cookie", cookies)

                okHttpClient.newCall(reqBuilder.build()).execute().close()
            } catch (_: Exception) {}
        }
    }

    private fun getPlayInfo(type: String, uuid: String, cookies: String, referer: String): JSONObject? {
        val targets = getTargetBaseUrls()
        for (base in targets) {
            try {
                val url = "$base/api/watch/play-info/$type/$uuid"
                Log.d(TAG, "getPlayInfo requesting: $url | Cookies len: ${cookies.length}")
                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("Referer", referer)
                    .header("User-Agent", USER_AGENT)
                if (cookies.isNotEmpty()) reqBuilder.header("Cookie", cookies)

                val res = okHttpClient.newCall(reqBuilder.build()).execute()
                val body = res.body?.string()
                res.close()
                Log.d(TAG, "getPlayInfo res code=${res.code} body: $body")
                if (!body.isNullOrEmpty() && body.trim().startsWith("{")) {
                    val json = JSONObject(body)
                    val token = extractGateToken(json)
                    if (!token.isNullOrEmpty()) {
                        Log.d(TAG, "getPlayInfo SUCCESS gateToken found from $base")
                        return json
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "getPlayInfo error on $base: ${e.message}")
            }
        }
        return null
    }

    private fun claimSession(gateToken: String, cookies: String, referer: String): JSONObject? {
        val targets = getTargetBaseUrls()
        val jsonBody = JSONObject()
        jsonBody.put("gateToken", gateToken)
        val body = jsonBody.toString().toRequestBody("application/json".toMediaType())

        for (base in targets) {
            try {
                val url = "$base/api/watch/session/claim"
                Log.d(TAG, "claimSession requesting: $url")

                // Poll claim up to 12 times (~18 seconds max wait for 15s guest/preroll countdown)
                for (claimAttempt in 1..12) {
                    val reqBuilder = Request.Builder()
                        .url(url)
                        .post(body)
                        .header("Referer", referer)
                        .header("Origin", base)
                        .header("User-Agent", USER_AGENT)
                    if (cookies.isNotEmpty()) reqBuilder.header("Cookie", cookies)

                    val res = okHttpClient.newCall(reqBuilder.build()).execute()
                    val resBody = res.body?.string()
                    res.close()
                    Log.d(TAG, "claimSession attempt #$claimAttempt res code=${res.code} body: $resBody")

                    if (!resBody.isNullOrEmpty() && resBody.trim().startsWith("{")) {
                        val json = JSONObject(resBody)
                        val claim = extractField(json, "claim")
                        val redeemUrl = extractField(json, "redeemUrl")

                        if (!claim.isNullOrEmpty() && !redeemUrl.isNullOrEmpty()) {
                            Log.d(TAG, "claimSession SUCCESS claim found from $base")
                            return json
                        }

                        val kind = json.optString("kind", "")
                        val remainingMs = json.optLong("remainingMs", 0L)
                        if (kind == "pending" || remainingMs > 0) {
                            val waitMs = if (remainingMs > 0) (remainingMs + 250L).coerceIn(800L, 16000L) else 1200L
                            Log.d(TAG, "claimSession pending ($remainingMs ms remaining). Waiting exact ${waitMs}ms...")
                            Thread.sleep(waitMs)
                        } else {
                            break
                        }
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "claimSession error on $base: ${e.message}")
            }
        }
        return null
    }

    private fun redeemClaim(redeemUrl: String, claim: String): JSONObject? {
        try {
            Log.d(TAG, "redeemClaim to: $redeemUrl")
            val jsonBody = JSONObject()
            jsonBody.put("claim", claim)

            val body = jsonBody.toString().toRequestBody("text/plain".toMediaType())
            val req = Request.Builder()
                .url(redeemUrl)
                .post(body)
                .header("Origin", webSiteBaseUrl)
                .header("Referer", "$webSiteBaseUrl/")
                .header("User-Agent", USER_AGENT)
                .build()

            val res = okHttpClient.newCall(req).execute()
            val resBody = res.body?.string()
            res.close()
            Log.d(TAG, "redeemClaim res code=${res.code} body len=${resBody?.length ?: 0}")
            if (!resBody.isNullOrEmpty() && resBody.trim().startsWith("{")) {
                return JSONObject(resBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "redeemClaim error: ${e.message}")
        }
        return null
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
