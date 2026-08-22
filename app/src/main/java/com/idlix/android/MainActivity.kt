package com.idlix.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.idlix.android.data.api.IdlixApiClient
import com.idlix.android.data.scraper.IdlixStreamScraper
import com.idlix.android.ui.screens.DetailScreen
import com.idlix.android.ui.screens.HomeScreen
import com.idlix.android.ui.screens.SearchScreen
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
                    val apiClient = remember { IdlixApiClient() }
                    val scraper = remember { IdlixStreamScraper(this@MainActivity) }

                    NavHost(
                        navController = navController,
                        startDestination = "home"
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
