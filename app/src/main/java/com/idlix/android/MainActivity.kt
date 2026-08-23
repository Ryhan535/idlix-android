package com.idlix.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.idlix.android.config.AppConfig
import com.idlix.android.data.api.IdlixApiClient
import com.idlix.android.data.scraper.IdlixStreamScraper
import com.idlix.android.ui.components.BottomNavigationBar
import com.idlix.android.ui.screens.*
import com.idlix.android.ui.theme.DarkBackground
import com.idlix.android.ui.theme.IDLIXTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            IDLIXTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    val navController = rememberNavController()
                    val savedApiUrl = remember { AppConfig.getApiBaseUrl(this@MainActivity) }
                    val apiClient = remember { IdlixApiClient(baseUrl = savedApiUrl) }
                    val scraper = remember { IdlixStreamScraper(context = this@MainActivity, baseUrl = savedApiUrl) }

                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

                    val showBottomNav = currentRoute in listOf("home", "movies", "series", "watchlist", "profile")

                    Scaffold(
                        bottomBar = {
                            if (showBottomNav) {
                                BottomNavigationBar(
                                    currentTab = currentRoute,
                                    onTabSelected = { tabId ->
                                        if (currentRoute != tabId) {
                                            navController.navigate(tabId) {
                                                popUpTo("home") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                )
                            }
                        },
                        containerColor = DarkBackground
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("home") {
                                HomeScreen(
                                    apiClient = apiClient,
                                    onMovieClick = { movie ->
                                        navController.navigate("detail/${movie.slug}/${movie.type}")
                                    },
                                    onSearchClick = {
                                        navController.navigate("search")
                                    }
                                )
                            }

                            composable("movies") {
                                MoviesScreen(
                                    apiClient = apiClient,
                                    onMovieClick = { movie ->
                                        navController.navigate("detail/${movie.slug}/${movie.type}")
                                    }
                                )
                            }

                            composable("series") {
                                SeriesScreen(
                                    apiClient = apiClient,
                                    onMovieClick = { movie ->
                                        navController.navigate("detail/${movie.slug}/${movie.type}")
                                    }
                                )
                            }

                            composable("watchlist") {
                                WatchlistScreen(
                                    onMovieClick = { movie ->
                                        navController.navigate("detail/${movie.slug}/${movie.type}")
                                    }
                                )
                            }

                            composable("profile") {
                                ProfileScreen(
                                    apiClient = apiClient
                                )
                            }

                            composable(
                                route = "detail/{slug}/{type}",
                                arguments = listOf(
                                    navArgument("slug") { type = NavType.StringType },
                                    navArgument("type") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val slug = backStackEntry.arguments?.getString("slug") ?: ""
                                val type = backStackEntry.arguments?.getString("type") ?: "movie"

                                DetailScreen(
                                    slug = slug,
                                    type = type,
                                    apiClient = apiClient,
                                    scraper = scraper
                                )
                            }

                            composable("search") {
                                SearchScreen(
                                    apiClient = apiClient,
                                    onMovieClick = { movie ->
                                        navController.navigate("detail/${movie.slug}/${movie.type}")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

