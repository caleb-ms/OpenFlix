package com.calebms.openflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.calebms.openflix.data.local.entities.MediaItem
import com.calebms.openflix.viewmodel.ScannerViewModel
import java.io.File

@Composable
fun SearchScreen(
    viewModel: ScannerViewModel,
    onMediaClick: (MediaItem) -> Unit
) {
    val allMedia by viewModel.allMedia.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val searchResults = remember(searchQuery, allMedia) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            allMedia.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        (it.releaseYear?.toString()?.contains(searchQuery) == true)
            }
        }
    }


    val defaultSuggestions = remember(allMedia) {
        allMedia.reversed().take(15)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color(0xFF141414))
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(50.dp)
                .background(Color(0xFF333333), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray)

                Spacer(modifier = Modifier.width(8.dp))

                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color(0xFFE50914)),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text("Search movies, shows, or year...", color = Color.Gray, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                )

                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            }
        }


        if (searchQuery.isEmpty()) {

            Text(
                text = "Top Searches",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = 100.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(defaultSuggestions) { item ->
                    SearchRowItem(item = item, onClick = {
                        focusManager.clearFocus()
                        onMediaClick(item)
                    })
                }
            }
        } else if (searchResults.isEmpty()) {

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Oh no! We couldn't find \"$searchQuery\".",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {

            LazyColumn(
                contentPadding = PaddingValues(bottom = 100.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(searchResults) { item ->
                    SearchRowItem(item = item, onClick = {
                        focusManager.clearFocus()
                        onMediaClick(item)
                    })
                }
            }
        }
    }
}

@Composable
fun SearchRowItem(item: MediaItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(width = 140.dp, height = 80.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF262626)),
            contentAlignment = Alignment.Center
        ) {
            val imageSource = item.backdropPath ?: item.posterPath
            
            if (imageSource != null) {
                AsyncImage(
                    model = if (imageSource.startsWith("http")) imageSource else File(imageSource),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
            }
            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White)
        }

        Spacer(modifier = Modifier.width(16.dp))


        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${if (item.type == "MOVIE") "Movie" else "TV Show"} • ${item.releaseYear ?: "Unknown"}",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
    }
}