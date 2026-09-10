package com.calebms.openflix.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.calebms.openflix.data.local.entities.MediaItem
import com.calebms.openflix.data.server.CommandAction
import com.calebms.openflix.data.server.RemoteMessage
import com.calebms.openflix.viewmodel.ScannerViewModel
import java.io.File
import com.calebms.openflix.data.server.TrackOption

@Composable
fun RemotePlayerScreen(
    mediaItem: MediaItem,
    episodeTitle: String?,
    hasNextEpisode: Boolean,
    viewModel: ScannerViewModel,
    onDisconnect: () -> Unit,
    onNextEpisode: () -> Unit
) {
    BackHandler { onDisconnect() }

    val remoteState by viewModel.remotePlaybackState.collectAsState()

    var isSeeking by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }
    var showTrackSheet by remember { mutableStateOf(false) }

    val currentPosition = if (isSeeking) scrubPosition else (remoteState?.positionMs ?: 0L)
    val totalDuration = remoteState?.durationMs ?: 0L
    val isPlaying = remoteState?.isPlaying ?: true

    val imageSource = mediaItem.backdropPath ?: mediaItem.posterPath

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
    ) {

        if (imageSource != null) {
            AsyncImage(
                model = if (imageSource.startsWith("http")) imageSource else File(imageSource),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.25f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF141414).copy(alpha = 0.7f),
                            Color(0xFF141414).copy(alpha = 0.95f),
                            Color(0xFF141414)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = Color(0xFF1E3A24),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Laptop,
                            contentDescription = null,
                            tint = Color(0xFF46D369),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Playing on PC",
                            color = Color(0xFF46D369),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showTrackSheet = true },
                        modifier = Modifier
                            .background(Color(0xFF262626), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = "Audio & Subtitles",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))


                    IconButton(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .background(Color(0xFF262626), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Disconnect",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                    modifier = Modifier.size(width = 200.dp, height = 280.dp)
                ) {
                    if (mediaItem.posterPath != null) {
                        AsyncImage(
                            model = if (mediaItem.posterPath.startsWith("http")) mediaItem.posterPath else File(mediaItem.posterPath),
                            contentDescription = mediaItem.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF262626)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = mediaItem.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!episodeTitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = episodeTitle,
                        color = Color(0xFFE50914),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = {
                        isSeeking = true
                        scrubPosition = it.toLong()
                    },
                    onValueChangeFinished = {
                        isSeeking = false
                        viewModel.sendRemoteCommand(
                            RemoteMessage(
                                action = CommandAction.SEEK,
                                positionMs = scrubPosition
                            )
                        )
                    },
                    valueRange = 0f..(if (totalDuration > 0) totalDuration.toFloat() else 1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFE50914),
                        activeTrackColor = Color(0xFFE50914),
                        inactiveTrackColor = Color(0xFF333333)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatDuration(currentPosition), color = Color.Gray, fontSize = 12.sp)
                    Text(text = formatDuration(totalDuration), color = Color.Gray, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val newTime = (currentPosition - 10000L).coerceAtLeast(0L)
                            viewModel.sendRemoteCommand(RemoteMessage(action = CommandAction.SEEK, positionMs = newTime))
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE50914),
                        modifier = Modifier.size(72.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val action = if (isPlaying) CommandAction.PAUSE else CommandAction.PLAY
                                viewModel.sendRemoteCommand(RemoteMessage(action = action))
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val newTime = (currentPosition + 10000L).coerceAtMost(totalDuration)
                            viewModel.sendRemoteCommand(RemoteMessage(action = CommandAction.SEEK, positionMs = newTime))
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                    }

                    if (hasNextEpisode) {
                        IconButton(
                            onClick = onNextEpisode,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next Episode", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

    }

    if (showTrackSheet) {
        TrackSelectionBottomSheet(
            audioTracks = remoteState?.audioTracks ?: emptyList(),
            subtitleTracks = remoteState?.subtitleTracks ?: emptyList(),
            onSelectAudio = { trackId ->
                viewModel.sendRemoteCommand(
                    RemoteMessage(action = CommandAction.SET_AUDIO_TRACK, selectedTrackId = trackId)
                )
            },
            onSelectSubtitle = { trackId ->
                viewModel.sendRemoteCommand(
                    RemoteMessage(action = CommandAction.SET_SUBTITLE_TRACK, selectedTrackId = trackId)
                )
            },
            onDismiss = { showTrackSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSelectionBottomSheet(
    audioTracks: List<TrackOption>,
    subtitleTracks: List<TrackOption>,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedTab),
                        color = Color(0xFFE50914)
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Audio", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Subtitles", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val currentList = if (selectedTab == 0) audioTracks else subtitleTracks

            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No alternate tracks available", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(currentList) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedTab == 0) onSelectAudio(track.id) else onSelectSubtitle(track.id)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = track.name,
                                color = if (track.isSelected) Color(0xFF46D369) else Color.White,
                                fontSize = 15.sp,
                                fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (track.isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF46D369))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}