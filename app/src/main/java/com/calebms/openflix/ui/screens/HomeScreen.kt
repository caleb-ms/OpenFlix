package com.calebms.openflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.calebms.openflix.data.local.entities.MediaItem

@Composable
fun NetflixHomeScreen(
    mediaList: List<MediaItem>,
    onMediaClick: (MediaItem) -> Unit,
    onPickFolderClick: () -> Unit
) {
    val movies = mediaList.filter { it.type == "MOVIE" }
    val tvShows = mediaList.filter { it.type == "TV_SHOW" }
    val featuredItem = mediaList.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
    ) {

        item {
            if (featuredItem != null) {
                HeroBillboard(item = featuredItem, onPlayClick = { onMediaClick(featuredItem) })
            } else {
                EmptyLibraryHeader(onPickFolderClick)
            }
        }


        if (movies.isNotEmpty()) {
            item {
                MediaSectionRow(
                    sectionTitle = "Movies",
                    items = movies,
                    onItemClick = onMediaClick
                )
            }
        }


        if (tvShows.isNotEmpty()) {
            item {
                MediaSectionRow(
                    sectionTitle = "TV Shows",
                    items = tvShows,
                    onItemClick = onMediaClick
                )
            }
        }


        if (mediaList.isNotEmpty()) {
            item {
                MediaSectionRow(
                    sectionTitle = "Recently Added",
                    items = mediaList.reversed(),
                    onItemClick = onMediaClick
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun HeroBillboard(item: MediaItem, onPlayClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF262626)),
            contentAlignment = Alignment.Center
        ) {
            val imageSource = item.backdropPath ?: item.posterPath
            
            if (imageSource != null) {
                AsyncImage(
                    model = if (imageSource.startsWith("http")) imageSource else java.io.File(imageSource),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (item.type == "TV_SHOW") Icons.Default.LiveTv else Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color(0xFF444444),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = item.title,
                        color = Color(0xFF444444),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }


        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF141414)),
                        startY = 300f
                    )
                )
        )


        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Text(
                text = if (item.type == "TV_SHOW") "TV Show • ${item.releaseYear ?: "Unknown"}" else "Movie • ${item.releaseYear ?: "Unknown"}",
                color = Color.LightGray,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Play", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MediaSectionRow(
    sectionTitle: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = sectionTitle,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items) { media ->
                MediaPosterCard(item = media, onClick = { onItemClick(media) })
            }
        }
    }
}

@Composable
fun MediaPosterCard(item: MediaItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(115.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(115.dp)
                .height(165.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2B2B2B)),
            contentAlignment = Alignment.Center
        ) {
            if (item.posterPath != null) {
                AsyncImage(
                    model = if (item.posterPath.startsWith("http")) item.posterPath else java.io.File(item.posterPath),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = item.title.take(1),
                    color = Color.DarkGray,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = item.title,
            color = Color(0xFFCCCCCC),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EmptyLibraryHeader(onPickFolderClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No Media Found",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onPickFolderClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
        ) {
            Text("Select Movies Folder")
        }
    }
}