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
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class IdlixStreamScraper(
    private val context: Context,
    private val baseUrl: String = AppConfig.getNormalizedApiBaseUrl(),
    private val webSiteBaseUrl: String = AppConfig.WEBSITE_BASE_URL
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Extract stream URL for a movie or episode using background WebView + Native OkHttp
     */
    suspend fun getMovieStream(slug: String): StreamResult = withContext(Dispatchers.Main) {
        val pageUrl = "$webSiteBaseUrl/movie/$slug"
        return@withContext extractStreamChain(pageUrl, "movie", slug)
    }

    suspend fun getEpisodeStream(slug: String, season: Int, episode: Int): StreamResult = withContext(Dispatchers.Main) {
        val pageUrl = "$webSiteBaseUrl/series/$slug/season/$season/episode/$episode"
        return@withContext extractStreamChain(pageUrl, "episode", slug, season, episode)
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

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        var hasFinishedCF = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!hasFinishedCF) {
                    hasFinishedCF = true
                    // Give JS 2 seconds to ensure Cloudflare cookies are saved
                    mainHandler.postDelayed({
                        val cookies = CookieManager.getInstance().getCookie(url) ?: ""
                        executeApiChainInBackground(cookies, type, slug, season, episode, pageUrl) { result ->
                            mainHandler.post {
                                webView.destroy()
                                if (continuation.isActive) {
                                    continuation.resume(result)
                                }
                            }
                        }
                    }, 2000)
                }
            }
        }

        webView.loadUrl(pageUrl)

        continuation.invokeOnCancellation {
            mainHandler.post {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    private fun executeApiChainInBackground(
        cookies: String,
        type: String,
        slug: String,
        season: Int,
        episode: Int,
        refererUrl: String,
        onComplete: (StreamResult) -> Unit
    ) {
        Thread {
            try {
                // Step 1: Resolve UUID
                val uuid = if (type == "movie") {
                    resolveMovieUuid(slug, cookies, refererUrl)
                } else {
                    resolveEpisodeUuid(slug, season, episode, cookies, refererUrl)
                }

                if (uuid == null) {
                    onComplete(StreamResult(null, error = "Failed to resolve content UUID"))
                    return@Thread
                }

                // Step 2: Track View (Optional)
                trackView(type, uuid, cookies, refererUrl)

                // Step 3: Get Play Info
                val playInfoType = if (type == "movie") "movie" else "episode"
                val playInfo = getPlayInfo(playInfoType, uuid, cookies, refererUrl)
                if (playInfo == null) {
                    onComplete(StreamResult(null, error = "Failed to fetch play-info"))
                    return@Thread
                }

                val gateToken = playInfo.optString("gateToken", "")
                val unlockAt = playInfo.optLong("unlockAt", 0L)
                val serverNow = playInfo.optLong("serverNow", 0L)

                if (gateToken.isEmpty()) {
                    onComplete(StreamResult(null, error = "Invalid gateToken received"))
                    return@Thread
                }

                // Step 4: Mandatory wait for unlockAt timer (approx 15 seconds)
                val waitMs = (unlockAt - serverNow).coerceIn(0L, 20000L)
                if (waitMs > 0) {
                    Thread.sleep(waitMs + 500)
                }

                // Step 5: Claim Session
                val claimResult = claimSession(gateToken, cookies, refererUrl)
                if (claimResult == null) {
                    onComplete(StreamResult(null, error = "Failed to claim session token"))
                    return@Thread
                }

                val claim = claimResult.optString("claim", "")
                val redeemUrl = claimResult.optString("redeemUrl", "")

                if (claim.isEmpty() || redeemUrl.isEmpty()) {
                    onComplete(StreamResult(null, error = "Missing claim token or redeem URL"))
                    return@Thread
                }

                // Step 6: Redeem Claim at majorplay.net
                val playData = redeemClaim(redeemUrl, claim)
                if (playData == null) {
                    onComplete(StreamResult(null, error = "Failed to redeem stream link from majorplay.net"))
                    return@Thread
                }

                val streamUrl = playData.optString("url", null)
                val subsArray = playData.optJSONArray("subtitles")
                val subtitles = mutableListOf<SubtitleTrack>()

                if (subsArray != null) {
                    for (i in 0 until subsArray.length()) {
                        val sub = subsArray.getJSONObject(i)
                        subtitles.add(
                            SubtitleTrack(
                                lang = sub.optString("lang", "en"),
                                label = sub.optString("label", "Subtitle"),
                                url = sub.optString("path", "")
                            )
                        )
                    }
                }

                onComplete(
                    StreamResult(
                        streamUrl = streamUrl,
                        subtitles = subtitles,
                        videoId = playData.optString("videoId", null),
                        title = claimResult.optString("title", null)
                    )
                )

            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(StreamResult(null, error = e.localizedMessage ?: "Scraping error"))
            }
        }.start()
    }

    private fun resolveMovieUuid(slug: String, cookies: String, referer: String): String? {
        val req = Request.Builder()
            .url("$baseUrl/api/movies/$slug")
            .header("Cookie", cookies)
            .header("Referer", referer)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            .build()

        val res = okHttpClient.newCall(req).execute()
        val body = res.body?.string() ?: return null
        val json = JSONObject(body)
        return json.optString("id", if (json.has("data")) json.getJSONObject("data").optString("id") else null)
    }

    private fun resolveEpisodeUuid(slug: String, season: Int, episode: Int, cookies: String, referer: String): String? {
        val req = Request.Builder()
            .url("$baseUrl/api/serieses/$slug/season/$season")
            .header("Cookie", cookies)
            .header("Referer", referer)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            .build()

        val res = okHttpClient.newCall(req).execute()
        val body = res.body?.string() ?: return null
        val json = JSONObject(body)
        val seasonObj = json.optJSONObject("season") ?: return null
        val eps = seasonObj.optJSONArray("episodes") ?: return null

        for (i in 0 until eps.length()) {
            val ep = eps.getJSONObject(i)
            if (ep.optInt("episodeNumber") == episode) {
                return ep.optString("id", null)
            }
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
}
