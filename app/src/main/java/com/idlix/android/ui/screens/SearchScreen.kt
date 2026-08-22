package com.idlix.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.idlix.android.data.api.IdlixApiClient
import com.idlix.android.data.model.MovieItem
import com.idlix.android.ui.components.LoadingView
import com.idlix.android.ui.components.MovieCard
import com.idlix.android.ui.theme.DarkBackground
import com.idlix.android.ui.theme.RedAccent
import com.idlix.android.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    apiClient: IdlixApiClient,
    onMovieClick: (MovieItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = "Search movies or TV series...", color = Color.Gray) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = RedAccent) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RedAccent,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (searchQuery.isNotBlank()) {
                    isSearching = true
                    coroutineScope.launch {
                        searchResults = apiClient.search(searchQuery)
                        isSearching = false
                    }
                }
            })
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isSearching) {
            LoadingView()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults) { movie ->
                    MovieCard(movie = movie, onClick = { onMovieClick(movie) })
                }
            }
        }
    }
}
