package com.calebms.openflix.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.calebms.openflix.data.local.entities.Profile
import com.calebms.openflix.data.local.entities.WatchHistoryEntry
import com.calebms.openflix.data.local.dao.WatchStats
import com.calebms.openflix.viewmodel.ScannerViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WatchHistoryScreen(
    activeProfile: Profile,
    viewModel: ScannerViewModel,
    onBackClick: () -> Unit
) {
    BackHandler { onBackClick() }

    val historyList by viewModel.getWatchHistory(activeProfile.id)
        .collectAsState(initial = emptyList())

    val startOfMonth = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

    val statsMonthly by viewModel.getWatchStatsForPeriod(activeProfile.id, startOfMonth)
        .collectAsState(initial = WatchStats(0, 0, 0L))

    val statsAllTime by viewModel.getWatchStatsForPeriod(activeProfile.id, 0L)
        .collectAsState(initial = WatchStats(0, 0, 0L))

    var showMonthlyStats by remember { mutableStateOf(true) }
    val currentStats = if (showMonthlyStats) statsMonthly else statsAllTime

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "Watch History & Stats",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
        ) {
            item {
                // Stats Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showMonthlyStats) "This Month's Watch Time" else "All-Time Watch Time",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = showMonthlyStats,
                                    onClick = { showMonthlyStats = true },
                                    label = { Text("Month", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFE50914),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF333333),
                                        labelColor = Color.LightGray
                                    )
                                )
                                FilterChip(
                                    selected = !showMonthlyStats,
                                    onClick = { showMonthlyStats = false },
                                    label = { Text("All Time", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFE50914),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF333333),
                                        labelColor = Color.LightGray
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            StatBox(
                                title = "Hours",
                                value = formatHours(currentStats.totalDurationMs),
                                icon = Icons.Default.History
                            )
                            StatBox(
                                title = "Movies",
                                value = currentStats.moviesCount.toString(),
                                icon = Icons.Default.Movie
                            )
                            StatBox(
                                title = "Episodes",
                                value = currentStats.episodesCount.toString(),
                                icon = Icons.Default.Tv
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "History Log (${historyList.size})",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (historyList.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearWatchHistory(activeProfile.id) }
                        ) {
                            Text("Clear All", color = Color(0xFFE50914), fontSize = 13.sp)
                        }
                    }
                }
            }

            if (historyList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No watch history recorded yet.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(historyList, key = { it.id }) { entry ->
                    WatchHistoryRow(
                        entry = entry,
                        onDeleteClick = { viewModel.deleteHistoryEntry(entry.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun StatBox(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = Color(0xFFE50914), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = title, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun WatchHistoryRow(
    entry: WatchHistoryEntry,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail or Procedural Gradient Fallback
        Box(
            modifier = Modifier
                .size(width = 110.dp, height = 65.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF262626)),
            contentAlignment = Alignment.Center
        ) {
            val imageSource = entry.cachedThumbPath ?: entry.posterPath
            var imageLoadedSuccessfully by remember { mutableStateOf(false) }

            if (imageSource != null) {
                val file = File(imageSource)
                val model = if (imageSource.startsWith("http")) imageSource else if (file.exists()) file else null

                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = entry.mediaTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { imageLoadedSuccessfully = true },
                        onError = { imageLoadedSuccessfully = false }
                    )
                }
            }

            if (!imageLoadedSuccessfully) {
                // Procedural Gradient Cover Fallback for deleted / offline items
                val gradientColors = remember(entry.mediaTitle) {
                    generateTitleGradient(entry.mediaTitle)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.mediaTitle.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.mediaTitle,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (entry.mediaType == "TV_SHOW") {
                val epText = if (entry.seasonNumber != null && entry.episodeNumber != null) {
                    "S${entry.seasonNumber} E${entry.episodeNumber}${if (!entry.episodeTitle.isNullOrBlank()) " • ${entry.episodeTitle}" else ""}"
                } else entry.episodeTitle ?: "Episode"

                Text(
                    text = epText,
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                Text(
                    text = "Movie",
                    color = Color(0xFF46D369),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Text(
                text = formatDate(entry.watchedAt),
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete History Entry",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun generateTitleGradient(title: String): List<Color> {
    val hash = title.hashCode()
    val hue1 = (Math.abs(hash) % 360).toFloat()
    val hue2 = ((hue1 + 40) % 360)
    val color1 = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue1, 0.7f, 0.5f)))
    val color2 = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue2, 0.8f, 0.3f)))
    return listOf(color1, color2)
}

private fun formatHours(durationMs: Long): String {
    val hours = durationMs / (1000.0 * 60.0 * 60.0)
    return String.format(Locale.US, "%.1fh", hours)
}

private fun formatDate(timestampMs: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    return sdf.format(Date(timestampMs))
}
