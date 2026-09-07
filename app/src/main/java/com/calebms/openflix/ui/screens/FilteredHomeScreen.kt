package com.calebms.openflix.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calebms.openflix.data.local.entities.MediaItem
import com.calebms.openflix.data.local.entities.PlaybackStatus
import com.calebms.openflix.data.local.entities.Profile
import com.calebms.openflix.viewmodel.ScannerViewModel

@Composable
fun FilteredHomeScreen(
    profile: Profile,
    viewModel: ScannerViewModel,
    onMediaClick: (MediaItem) -> Unit
) {
    val allMedia by viewModel.allMedia.collectAsState()
    val criticallyAcclaimed by viewModel.criticallyAcclaimed.collectAsState()
    val throwbacks by viewModel.throwbacks.collectAsState()
    val quickWatches by viewModel.quickWatches.collectAsState()

    val unplayedGems by remember(profile.id) {
        viewModel.getUnplayedGems(profile.id)
    }.collectAsState(initial = emptyList())

    val continueWatchingStatuses by viewModel.getContinueWatching(profile.id)
        .collectAsState(initial = emptyList<PlaybackStatus>())

    val continueWatchingMedia = remember(continueWatchingStatuses, allMedia) {
        continueWatchingStatuses.mapNotNull { status ->
            allMedia.find { it.id == status.mediaItemId }
        }.distinctBy { it.id }
    }

    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Shows", "Movies")

    fun filterList(items: List<MediaItem>): List<MediaItem> {
        return when (selectedFilter) {
            "Movies" -> items.filter { it.type == "MOVIE" }
            "Shows" -> items.filter { it.type == "TV_SHOW" }
            else -> items
        }
    }

    val displayedRecentlyAdded = remember(selectedFilter, allMedia) { filterList(allMedia) }
    val displayedUnplayedGems = remember(selectedFilter, unplayedGems) { filterList(unplayedGems) }
    val displayedCriticallyAcclaimed = remember(selectedFilter, criticallyAcclaimed) { filterList(criticallyAcclaimed) }
    val displayedQuickWatches = remember(selectedFilter, quickWatches) { filterList(quickWatches) }
    val displayedThrowbacks = remember(selectedFilter, throwbacks) { filterList(throwbacks) }

    val featuredItem = displayedRecentlyAdded.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("Home", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { filter ->
                FilterPill(
                    label = filter,
                    isSelected = selectedFilter == filter,
                    onClick = { selectedFilter = filter }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (featuredItem != null) {
            HeroBillboard(
                item = featuredItem,
                onPlayClick = { onMediaClick(featuredItem) }
            )

            Spacer(modifier = Modifier.height(16.dp))


            if (continueWatchingMedia.isNotEmpty()) {
                MediaSectionRow(
                    sectionTitle = "Continue Watching for ${profile.name}",
                    items = filterList(continueWatchingMedia),
                    onItemClick = onMediaClick
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (displayedUnplayedGems.isNotEmpty()) {
                MediaSectionRow(
                    sectionTitle = "Unplayed Gems",
                    items = displayedUnplayedGems,
                    onItemClick = onMediaClick
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (displayedRecentlyAdded.isNotEmpty()) {
                MediaSectionRow(
                    sectionTitle = if (selectedFilter == "All") "Recently Added" else selectedFilter,
                    items = displayedRecentlyAdded,
                    onItemClick = onMediaClick
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (displayedCriticallyAcclaimed.isNotEmpty()) {
                MediaSectionRow(
                    sectionTitle = "Critically Acclaimed ★",
                    items = displayedCriticallyAcclaimed,
                    onItemClick = onMediaClick
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (displayedQuickWatches.isNotEmpty()) {
                MediaSectionRow(
                    sectionTitle = "Quick Watches (< 30 mins)",
                    items = displayedQuickWatches,
                    onItemClick = onMediaClick
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (displayedThrowbacks.isNotEmpty()) {
                MediaSectionRow(
                    sectionTitle = "Throwbacks & Classics",
                    items = displayedThrowbacks,
                    onItemClick = onMediaClick
                )
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun FilterPill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color.White else Color(0xFF333333),
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
