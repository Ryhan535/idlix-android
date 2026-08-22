package com.idlix.android.data.api

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
    val baseUrl: String = "https://z2.idlixku.com"
) {
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

    suspend fun fetchHomepageSections(): Map<String, List<MovieItem>> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, List<MovieItem>>()
        try {
            val req = buildRequest("$baseUrl/api/homepage")
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: return@withContext result
            val json = JSONObject(body)

            val parseSection = { sectionName: String, array: JSONArray? ->
                if (array != null) {
                    val list = mutableListOf<MovieItem>()
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val title = item.optString("title", item.optString("name", ""))
                        val slug = item.optString("slug", "")
                        val poster = item.optString("poster", item.optString("posterPath", ""))
                        val type = if (item.has("episodes") || item.optString("type") == "tv_series") "series" else "movie"
                        val year = item.optInt("year", 0)

                        if (title.isNotEmpty() && slug.isNotEmpty()) {
                            list.add(MovieItem(title, slug, fixImageUrl(poster), type, if (year > 0) year else null))
                        }
                    }
                    if (list.isNotEmpty()) {
                        result[sectionName] = list
                    }
                }
            }

            if (json.has("above")) {
                val above = json.getJSONArray("above")
                for (i in 0 until above.length()) {
                    val sec = above.getJSONObject(i)
                    val secTitle = sec.optString("title", "Section $i")
                    val items = sec.optJSONArray("data")
                    parseSection(secTitle, items)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext result
    }

    suspend fun fetchMovies(page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MovieItem>()
        try {
            val req = buildRequest("$baseUrl/api/movies?page=$page&limit=36&sort=createdAt")
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: return@withContext list
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@withContext list

            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val title = item.optString("title", "")
                val slug = item.optString("slug", "")
                val poster = item.optString("poster", "")
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
            val req = buildRequest("$baseUrl/api/serieses?page=$page&limit=36&sort=createdAt")
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: return@withContext list
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@withContext list

            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val title = item.optString("title", item.optString("name", ""))
                val slug = item.optString("slug", "")
                val poster = item.optString("poster", "")
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
            val req = buildRequest("$baseUrl/api/search?q=${query.trim()}")
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: return@withContext list
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@withContext list

            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val title = item.optString("title", item.optString("name", ""))
                val slug = item.optString("slug", "")
                val poster = item.optString("poster", "")
                val type = if (item.optString("type") == "tv_series") "series" else "movie"

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
            val req = buildRequest("$baseUrl/api/movies/$slug")
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val data = if (json.has("data")) json.getJSONObject("data") else json

            val title = data.optString("title", "")
            val year = data.optInt("year", 0)
            val overview = data.optString("overview", "")
            val poster = data.optString("poster", "")
            val backdrop = data.optString("backdrop", "")
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

            return@withContext MovieDetail(
                title = title,
                slug = slug,
                year = if (year > 0) year else null,
                type = "movie",
                runtime = data.optString("runtime", null),
                overview = overview,
                poster = fixImageUrl(poster),
                backdrop = fixImageUrl(backdrop),
                genres = genres,
                country = data.optString("country", null),
                rating = data.optString("rating", null),
                watchUrl = watchUrl
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun fetchSeriesDetail(slug: String): SeriesDetail? = withContext(Dispatchers.IO) {
        try {
            val req = buildRequest("$baseUrl/api/serieses/$slug")
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val data = if (json.has("data")) json.getJSONObject("data") else json

            val title = data.optString("title", "")
            val year = data.optInt("year", 0)
            val overview = data.optString("overview", "")
            val poster = data.optString("poster", "")
            val backdrop = data.optString("backdrop", "")

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
                                    stillPath = fixImageUrl(ep.optString("stillPath", ""))
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
