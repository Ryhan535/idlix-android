package com.idlix.android.data.model

data class MovieItem(
    val title: String,
    val slug: String,
    val poster: String?,
    val type: String = "movie", // "movie" or "series"
    val year: Int? = null,
    val quality: String? = null,
    val rating: String? = null,
    val overview: String? = null,
    val duration: String? = null
)

data class MovieDetail(
    val title: String,
    val slug: String,
    val year: Int?,
    val type: String,
    val runtime: String?,
    val overview: String?,
    val poster: String?,
    val backdrop: String?,
    val genres: List<String>,
    val country: String?,
    val rating: String?,
    val watchUrl: String?
)

data class SeasonData(
    val seasonNumber: Int,
    val episodes: List<EpisodeItem>
)

data class EpisodeItem(
    val id: String,
    val episodeNumber: Int,
    val title: String?,
    val overview: String?,
    val stillPath: String?
)

data class SeriesDetail(
    val title: String,
    val slug: String,
    val year: Int?,
    val overview: String?,
    val poster: String?,
    val backdrop: String?,
    val genres: List<String>,
    val totalSeasons: Int,
    val seasons: List<SeasonData>
)

data class SubtitleTrack(
    val lang: String,
    val label: String,
    val url: String
) : java.io.Serializable

data class StreamResult(
    val streamUrl: String?,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val videoId: String? = null,
    val title: String? = null,
    val durationSec: Int? = null,
    val error: String? = null
)
