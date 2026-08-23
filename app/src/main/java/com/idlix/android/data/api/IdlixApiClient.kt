package com.idlix.android.data.api

import com.idlix.android.config.AppConfig
import com.idlix.android.data.model.EpisodeItem
import com.idlix.android.data.model.MovieDetail
import com.idlix.android.data.model.MovieItem
import com.idlix.android.data.model.SeasonData
import com.idlix.android.data.model.SeriesDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class IdlixApiClient(
    var baseUrl: String = AppConfig.getNormalizedApiBaseUrl()
) {
    fun updateBaseUrl(newUrl: String) {
        this.baseUrl = AppConfig.normalizeUrl(newUrl)
    }

    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "$baseUrl/")
            .build()
    }

    /**
     * Mencoba mengeksekusi request ke beberapa rute alternatif (sesuai spesifikasi IDLIX API v3 Postman Collection).
     * Jika gagal (404/error), secara otomatis fallback ke website utama IDLIX.
     */
    private fun tryExecute(vararg relativePaths: String): String? {
        val baseUrls = listOf(baseUrl, AppConfig.WEBSITE_BASE_URL).distinct()
        for (base in baseUrls) {
            for (path in relativePaths) {
                try {
                    val fullUrl = "$base$path"
                    val req = buildRequest(fullUrl)
                    val res = client.newCall(req).execute()
                    if (res.isSuccessful) {
                        val body = res.body?.string()
                        res.close()
                        if (!body.isNullOrEmpty()) {
                            return body
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
     * Helper cerdas untuk menentukan apakah sebuah item JSON adalah TV Series atau Movie
     */
    private fun isSeriesType(item: JSONObject): Boolean {
        val rawType = item.optString("type", item.optString("media_type", "")).lowercase().trim()
        if (rawType == "series" || rawType == "tv" || rawType == "tv_series" || rawType == "tvshow" || rawType == "show" || rawType == "season") {
            return true
        }
        if (item.has("episodes") || item.has("seasons") || item.has("totalSeasons") || item.has("season_number")) {
            return true
        }
        val slug = item.optString("slug", "").lowercase()
        if (slug.contains("season-") || slug.contains("-season")) {
            return true
        }
        return false
    }

    private fun parseItemList(array: JSONArray?): List<MovieItem> {
        val list = mutableListOf<MovieItem>()
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val title = item.optString("title", item.optString("name", ""))
                val slug = item.optString("slug", "")
                val poster = item.optString("poster", item.optString("posterPath", ""))
                val type = if (isSeriesType(item)) "series" else "movie"
                val year = item.optInt("year", 0)

                if (title.isNotEmpty() && slug.isNotEmpty()) {
                    list.add(MovieItem(title, slug, fixImageUrl(poster), type, if (year > 0) year else null))
                }
            }
        }
        return list
    }

    suspend fun fetchHomepageSections(): Map<String, List<MovieItem>> = withContext(Dispatchers.IO) {
        val result = LinkedHashMap<String, List<MovieItem>>()

        // 1. Fetch /api/home/sections (Grouped Sections)
        val sectionsBody = tryExecute("/api/home/sections")
        if (!sectionsBody.isNullOrEmpty()) {
            try {
                val json = JSONObject(sectionsBody)
                val dataObj = if (json.has("data") && json.optJSONObject("data") != null) json.optJSONObject("data") else json
                if (dataObj != null) {
                    val keys = dataObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val arr = dataObj.optJSONArray(key)
                        if (arr != null) {
                            val items = parseItemList(arr)
                            if (items.isNotEmpty()) {
                                val sectionTitle = when (key.lowercase()) {
                                    "featured" -> "Featured Content"
                                    "cinemaxxi" -> "Cinema XXI"
                                    "trending" -> "Trending Now"
                                    "latestmovies", "latest_movies" -> "Latest Movies"
                                    "latestseries", "latest_series" -> "Latest TV Series"
                                    else -> key.replace("_", " ").replaceFirstChar { it.uppercase() }
                                }
                                result[sectionTitle] = items
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Fetch /api/cinemaxxi (CinemaXXI Movies)
        if (!result.containsKey("Cinema XXI") && !result.containsKey("CinemaXXI")) {
            val cxxiBody = tryExecute("/api/cinemaxxi")
            if (!cxxiBody.isNullOrEmpty()) {
                try {
                    val json = JSONObject(cxxiBody)
                    val arr = json.optJSONArray("data") ?: (if (json.has("results")) json.optJSONArray("results") else null)
                    val items = parseItemList(arr)
                    if (items.isNotEmpty()) {
                        result["Cinema XXI"] = items
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 3. Fetch /api/home (Flat Feed fallback)
        if (result.isEmpty()) {
            val homeBody = tryExecute("/api/home", "/api/featured", "/api/homepage")
            if (!homeBody.isNullOrEmpty()) {
                try {
                    val json = JSONObject(homeBody)
                    val arr = json.optJSONArray("data") ?: (if (json.has("results")) json.optJSONArray("results") else null)
                    val items = parseItemList(arr)
                    if (items.isNotEmpty()) {
                        result["Home Feed"] = items
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return@withContext result
    }

    suspend fun fetchMovies(page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MovieItem>()
        try {
            val body = tryExecute(
                "/api/movie?page=$page&limit=36&sort=createdAt",
                "/api/movie/trending",
                "/api/movies?page=$page&limit=36&sort=createdAt"
            ) ?: return@withContext list

            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: (if (json.has("movies")) json.optJSONArray("movies") else null) ?: (if (json.has("results")) json.optJSONArray("results") else null) ?: return@withContext list

            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val title = item.optString("title", item.optString("name", ""))
                val slug = item.optString("slug", "")
                val poster = item.optString("poster", item.optString("posterPath", ""))
                val year = item.optInt("year", 0)

                if (title.isNotEmpty() && slug.isNotEmpty()) {
                    list.add(MovieItem(title, slug, fixImageUrl(poster), "movie", if (year > 0) year else null))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    suspend fun fetchSeries(page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MovieItem>()
        try {
            val body = tryExecute(
                "/api/series?page=$page&limit=36&sort=createdAt",
                "/api/series/trending"
            ) ?: return@withContext list

            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: (if (json.has("series")) json.optJSONArray("series") else null) ?: (if (json.has("results")) json.optJSONArray("results") else null) ?: return@withContext list

            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val title = item.optString("title", item.optString("name", ""))
                val slug = item.optString("slug", "")
                val poster = item.optString("poster", item.optString("posterPath", ""))
                val year = item.optInt("year", 0)

                if (title.isNotEmpty() && slug.isNotEmpty()) {
                    list.add(MovieItem(title, slug, fixImageUrl(poster), "series", if (year > 0) year else null))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    suspend fun search(query: String): List<MovieItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MovieItem>()
        try {
            val body = tryExecute("/api/search?q=${query.trim()}") ?: return@withContext list
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: (if (json.has("results")) json.optJSONArray("results") else null) ?: return@withContext list

            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val title = item.optString("title", item.optString("name", ""))
                val slug = item.optString("slug", "")
                val poster = item.optString("poster", item.optString("posterPath", ""))
                val type = if (isSeriesType(item)) "series" else "movie"

                if (title.isNotEmpty() && slug.isNotEmpty()) {
                    list.add(MovieItem(title, slug, fixImageUrl(poster), type))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    suspend fun fetchMovieDetail(slug: String): MovieDetail? = withContext(Dispatchers.IO) {
        try {
            val body = tryExecute("/api/movie/$slug") ?: return@withContext null

            val json = JSONObject(body)
            val data = if (json.has("data")) json.getJSONObject("data") else json

            val title = data.optString("title", "")
            if (title.isEmpty()) return@withContext null

            val year = data.optInt("year", 0)
            val overview = data.optString("overview", "")
            val poster = data.optString("poster", data.optString("posterPath", ""))
            val backdrop = data.optString("backdrop", data.optString("backdropPath", ""))
            val watchUrl = data.optString("watchUrl", "$baseUrl/movie/$slug?play=1")
            
            val genresArr = data.optJSONArray("genres")
            val genres = mutableListOf<String>()
            if (genresArr != null) {
                for (i in 0 until genresArr.length()) {
                    val g = genresArr.opt(i)
                    if (g is JSONObject) genres.add(g.optString("name", ""))
                    else if (g is String) genres.add(g)
                }
            }

            val runtimeStr = if (data.has("runtime")) data.optString("runtime") else null
            val countryStr = if (data.has("country")) data.optString("country") else null
            val ratingStr = if (data.has("rating")) data.optString("rating") else null

            return@withContext MovieDetail(
                title = title,
                slug = slug,
                year = if (year > 0) year else null,
                type = "movie",
                runtime = runtimeStr,
                overview = overview,
                poster = fixImageUrl(poster),
                backdrop = fixImageUrl(backdrop),
                genres = genres,
                country = countryStr,
                rating = ratingStr,
                watchUrl = watchUrl
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun fetchSeriesDetail(slug: String): SeriesDetail? = withContext(Dispatchers.IO) {
        try {
            val body = tryExecute("/api/series/$slug") ?: return@withContext null

            val json = JSONObject(body)
            val data = if (json.has("data")) json.getJSONObject("data") else json

            val title = data.optString("title", data.optString("name", ""))
            if (title.isEmpty()) return@withContext null

            val year = data.optInt("year", 0)
            val overview = data.optString("overview", "")
            val poster = data.optString("poster", data.optString("posterPath", ""))
            val backdrop = data.optString("backdrop", data.optString("backdropPath", ""))

            val seasonsArr = data.optJSONArray("seasons")
            val seasonsList = mutableListOf<SeasonData>()

            if (seasonsArr != null) {
                for (i in 0 until seasonsArr.length()) {
                    val sObj = seasonsArr.getJSONObject(i)
                    val sNum = sObj.optInt("seasonNumber", i + 1)
                    val epsArr = sObj.optJSONArray("episodes")
                    val epList = mutableListOf<EpisodeItem>()
                    if (epsArr != null) {
                        for (j in 0 until epsArr.length()) {
                            val ep = epsArr.getJSONObject(j)
                            epList.add(
                                EpisodeItem(
                                    id = ep.optString("id", ""),
                                    episodeNumber = ep.optInt("episodeNumber", j + 1),
                                    title = ep.optString("title", "Episode ${j + 1}"),
                                    overview = ep.optString("overview", ""),
                                    stillPath = fixImageUrl(ep.optString("stillPath", ep.optString("still_path", "")))
                                )
                            )
                        }
                    }
                    seasonsList.add(SeasonData(sNum, epList))
                }
            }

            return@withContext SeriesDetail(
                title = title,
                slug = slug,
                year = if (year > 0) year else null,
                overview = overview,
                poster = fixImageUrl(poster),
                backdrop = fixImageUrl(backdrop),
                genres = emptyList(),
                totalSeasons = seasonsList.size,
                seasons = seasonsList
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun fixImageUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        if (url.startsWith("http")) return url
        if (url.startsWith("//")) return "https:$url"
        if (url.startsWith("/")) return "https://image.tmdb.org/t/p/w500$url"
        return url
    }
}
