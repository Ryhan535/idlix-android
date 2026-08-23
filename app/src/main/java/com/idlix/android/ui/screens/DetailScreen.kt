package com.idlix.android.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.idlix.android.data.api.IdlixApiClient
import com.idlix.android.data.model.MovieDetail
import com.idlix.android.data.model.SeriesDetail
import com.idlix.android.data.model.SubtitleTrack
import com.idlix.android.data.scraper.IdlixStreamScraper
import com.idlix.android.ui.components.LoadingView
import com.idlix.android.ui.player.PlayerActivity
import com.idlix.android.ui.theme.CardBackground
import com.idlix.android.ui.theme.DarkBackground
import com.idlix.android.ui.theme.FocusOutline
import com.idlix.android.ui.theme.RedAccent
import com.idlix.android.ui.theme.TextPrimary
import com.idlix.android.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    slug: String,
    type: String, // "movie" or "series"
    apiClient: IdlixApiClient,
    scraper: IdlixStreamScraper
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var isExtractingStream by remember { mutableStateOf(false) }
    var extractionProgressText by remember { mutableStateOf("") }

    var movieDetail by remember { mutableStateOf<MovieDetail?>(null) }
    var seriesDetail by remember { mutableStateOf<SeriesDetail?>(null) }
    var selectedSeasonIndex by remember { mutableStateOf(0) }

    LaunchedEffect(slug, type) {
        isLoading = true
        val normalizedType = type.lowercase().trim()
        if (normalizedType == "series" || normalizedType == "tv" || normalizedType == "tv_series" || normalizedType == "show") {
            seriesDetail = apiClient.fetchSeriesDetail(slug)
            if (seriesDetail == null) {
                movieDetail = apiClient.fetchMovieDetail(slug)
            }
        } else {
            movieDetail = apiClient.fetchMovieDetail(slug)
            if (movieDetail == null) {
                seriesDetail = apiClient.fetchSeriesDetail(slug)
            }
        }
        isLoading = false
    }

    if (isLoading) {
        LoadingView()
    } else if (isExtractingStream) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = RedAccent)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = extractionProgressText.ifEmpty { "Extracting stream link... (Please wait)" },
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Bypassing Cloudflare & redeeming stream token",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            val title = movieDetail?.title ?: seriesDetail?.title ?: ""
            val backdrop = movieDetail?.backdrop ?: seriesDetail?.backdrop
            val poster = movieDetail?.poster ?: seriesDetail?.poster
            val overview = movieDetail?.overview ?: seriesDetail?.overview ?: ""
            val year = movieDetail?.year ?: seriesDetail?.year

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    AsyncImage(
                        model = backdrop ?: poster,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, DarkBackground)
                                )
                            )
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (year != null) {
                        Text(
                            text = "Release Year: $year",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (type == "movie") {
                        var isPlayFocused by remember { mutableStateOf(false) }
                        val playScale by animateFloatAsState(if (isPlayFocused) 1.05f else 1.0f, label = "PlayScale")

                        Button(
                            onClick = {
                                isExtractingStream = true
                                extractionProgressText = "Connecting to stream server..."
                                coroutineScope.launch {
                                    val streamRes = scraper.getMovieStream(slug)
                                    isExtractingStream = false
                                    if (!streamRes.streamUrl.isNullOrEmpty()) {
                                        launchPlayer(context, title, streamRes.streamUrl, streamRes.subtitles)
                                    } else {
                                        Toast.makeText(context, "Error: ${streamRes.error ?: "Stream unavailable"}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .scale(playScale)
                                .onFocusChanged { isPlayFocused = it.isFocused }
                                .border(
                                    width = if (isPlayFocused) 2.dp else 0.dp,
                                    color = if (isPlayFocused) FocusOutline else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .focusable()
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "WATCH MOVIE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Synopsis",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = overview.ifEmpty { "No synopsis available." },
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (type == "series" && seriesDetail != null) {
                val seasons = seriesDetail!!.seasons
                if (seasons.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = "Seasons",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            LazyRow(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(seasons.size) { index ->
                                    val seasonNum = seasons[index].seasonNumber
                                    val isSelected = index == selectedSeasonIndex
                                    var isFocused by remember { mutableStateOf(false) }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) RedAccent else CardBackground)
                                            .onFocusChanged { isFocused = it.isFocused }
                                            .border(
                                                width = if (isFocused) 2.dp else 0.dp,
                                                color = if (isFocused) FocusOutline else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedSeasonIndex = index }
                                            .focusable()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "Season $seasonNum",
                                            color = Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val currentSeason = seasons.getOrNull(selectedSeasonIndex)
                    if (currentSeason != null && currentSeason.episodes.isNotEmpty()) {
                        item {
                            Text(
                                text = "Episodes",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        items(currentSeason.episodes.size) { epIndex ->
                            val episode = currentSeason.episodes[epIndex]
                            var isEpFocused by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CardBackground)
                                    .onFocusChanged { isEpFocused = it.isFocused }
                                    .border(
                                        width = if (isEpFocused) 2.dp else 0.dp,
                                        color = if (isEpFocused) FocusOutline else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        isExtractingStream = true
                                        extractionProgressText = "Extracting Episode ${episode.episodeNumber}..."
                                        coroutineScope.launch {
                                            val streamRes = scraper.getEpisodeStream(slug, currentSeason.seasonNumber, episode.episodeNumber)
                                            isExtractingStream = false
                                            if (!streamRes.streamUrl.isNullOrEmpty()) {
                                                launchPlayer(context, "$title - S${currentSeason.seasonNumber}E${episode.episodeNumber}", streamRes.streamUrl, streamRes.subtitles)
                                            } else {
                                                Toast.makeText(context, "Error: ${streamRes.error ?: "Episode stream unavailable"}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    .focusable()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play", tint = RedAccent)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Episode ${episode.episodeNumber}${if (!episode.title.isNullOrEmpty()) ": ${episode.title}" else ""}",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (!episode.overview.isNullOrEmpty()) {
                                        Text(
                                            text = episode.overview,
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun launchPlayer(context: Context, title: String, videoUrl: String, subtitles: List<SubtitleTrack>) {
    val intent = Intent(context, PlayerActivity::class.java).apply {
        putExtra(PlayerActivity.EXTRA_TITLE, title)
        putExtra(PlayerActivity.EXTRA_STREAM_URL, videoUrl)
        putExtra(PlayerActivity.EXTRA_SUBTITLES, ArrayList(subtitles))
    }
    context.startActivity(intent)
}
