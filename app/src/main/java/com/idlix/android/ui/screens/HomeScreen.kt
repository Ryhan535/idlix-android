package com.idlix.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlix.android.config.AppConfig
import com.idlix.android.data.api.IdlixApiClient
import com.idlix.android.data.model.MovieItem
import com.idlix.android.ui.components.ApiSettingsDialog
import com.idlix.android.ui.components.HeroBanner
import com.idlix.android.ui.components.LoadingView
import com.idlix.android.ui.components.MovieRow
import com.idlix.android.ui.theme.DarkBackground
import com.idlix.android.ui.theme.FocusOutline
import com.idlix.android.ui.theme.RedAccent

@Composable
fun HomeScreen(
    apiClient: IdlixApiClient,
    onMovieClick: (MovieItem) -> Unit,
    onSearchClick: () -> Unit
) {
    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    var currentApiUrl by remember { mutableStateOf(AppConfig.getApiBaseUrl(context)) }

    var isLoading by remember { mutableStateOf(true) }
    var homepageSections by remember { mutableStateOf<Map<String, List<MovieItem>>>(emptyMap()) }
    var moviesList by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var seriesList by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var fetchTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(fetchTrigger) {
        isLoading = true
        homepageSections = apiClient.fetchHomepageSections()
        if (homepageSections.isEmpty()) {
            moviesList = apiClient.fetchMovies()
            seriesList = apiClient.fetchSeries()
        }
        isLoading = false
    }

    if (showSettingsDialog) {
        ApiSettingsDialog(
            currentUrl = currentApiUrl,
            onDismiss = { showSettingsDialog = false },
            onSave = { newUrl ->
                AppConfig.saveApiBaseUrl(context, newUrl)
                currentApiUrl = AppConfig.getApiBaseUrl(context)
                apiClient.updateBaseUrl(currentApiUrl)
                showSettingsDialog = false
                fetchTrigger++
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IDLIX",
                    color = RedAccent,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var isSearchFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSearchFocused) FocusOutline else Color.DarkGray)
                            .onFocusChanged { isSearchFocused = it.isFocused }
                            .clickable { onSearchClick() }
                            .focusable()
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchFocused) Color.Black else Color.White
                        )
                    }

                    var isSettingsFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSettingsFocused) FocusOutline else Color.DarkGray)
                            .onFocusChanged { isSettingsFocused = it.isFocused }
                            .clickable { showSettingsDialog = true }
                            .focusable()
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (isSettingsFocused) Color.Black else Color.White
                        )
                    }
                }
            }
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        if (isLoading) {
            LoadingView()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val featuredMovie = homepageSections.values.firstOrNull()?.firstOrNull()
                    ?: moviesList.firstOrNull()

                if (featuredMovie != null) {
                    item {
                        HeroBanner(movie = featuredMovie, onClick = { onMovieClick(featuredMovie) })
                    }
                }

                if (homepageSections.isNotEmpty()) {
                    homepageSections.forEach { (sectionTitle, items) ->
                        item {
                            MovieRow(
                                title = sectionTitle,
                                movies = items,
                                onMovieClick = onMovieClick
                            )
                        }
                    }
                } else {
                    if (moviesList.isNotEmpty()) {
                        item {
                            MovieRow(
                                title = "Movies",
                                movies = moviesList,
                                onMovieClick = onMovieClick
                            )
                        }
                    }

                    if (seriesList.isNotEmpty()) {
                        item {
                            MovieRow(
                                title = "TV Series",
                                movies = seriesList,
                                onMovieClick = onMovieClick
                            )
                        }
                    }
                }
            }
        }
    }
}
