package com.calebms.openflix.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.calebms.openflix.data.local.entities.MediaEpisode
import com.calebms.openflix.data.local.entities.MediaItem
import com.calebms.openflix.data.local.entities.PlaybackStatus
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun MediaDetailScreen(
    item: MediaItem,
    episodes: List<MediaEpisode>,
    playbackStatuses: List<PlaybackStatus>,
    onBackClick: () -> Unit,
    onPlayClick: (videoUri: String, title: String, overview: String?, startPositionMs: Long, subtitleUri: String?) -> Unit,
    onPlayOnPcClick: (episode: MediaEpisode?, startPositionMs: Long) -> Unit
) {
    BackHandler { onBackClick() }

    if (item.type == "TV_SHOW" && episodes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF141414)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFE50914))
        }
        return
    }

    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(true) }

    val activeStatus = playbackStatuses
        .filter { !it.isFinished }
        .maxByOrNull { it.lastWatchedAt }

    val isResuming = activeStatus != null
    val lastPositionMs = activeStatus?.lastPositionMs ?: 0L

    val resumeEpisode = if (item.type == "TV_SHOW") {
        val targetEpisodeId = activeStatus?.episodeId ?: episodes.firstOrNull()?.id
        episodes.find { it.id == targetEpisodeId }
    } else null

    val previewPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val idealSeekMs = if (item.type == "MOVIE") 1800000L else 900000L
            val startMs = if (item.durationMs != null && item.durationMs < idealSeekMs) item.durationMs / 2 else idealSeekMs
            val endMs = startMs + 30000L

            val previewUri = if (item.type == "TV_SHOW") episodes.first().localFileUri else item.localUri

            val clippingConfig = ExoMediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build()

            val mediaItem = ExoMediaItem.Builder()
                .setUri(Uri.parse(previewUri))
                .setClippingConfiguration(clippingConfig)
                .build()

            setMediaItem(mediaItem)
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) { onDispose { previewPlayer.release() } }
    LaunchedEffect(isMuted) { previewPlayer.volume = if (isMuted) 0f else 1f }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF141414))) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {


            item {
                Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {

                    val imageSource = item.backdropPath ?: item.posterPath
                    
                    if (imageSource != null) {
                         AsyncImage(
                            model = if (imageSource.startsWith("http")) imageSource else File(imageSource),
                            contentDescription = item.title,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = previewPlayer
                                useController = false
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                        modifier = Modifier.fillMaxSize().background(Color.Transparent)
                    )
                    Box(modifier = Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF141414)), startY = 300f
                        )
                    ))
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.padding(16.dp).statusBarsPadding().size(40.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).align(Alignment.TopStart)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier.padding(16.dp).size(36.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape).align(Alignment.BottomEnd)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Toggle Audio", tint = Color.White, modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }


            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = item.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = if (item.type == "MOVIE") "Movie" else "TV Show", color = Color(0xFF46D369), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = item.releaseYear?.toString() ?: "Unknown Year", color = Color.Gray)
                        if (item.voteAverage != null && item.voteAverage > 0) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                color = Color(0xFF333333),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "★ ${String.format("%.1f", item.voteAverage)}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                    }

                    if (!item.genres.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item.genres.split(",").map { it.trim() }.forEach { genre ->
                                Surface(
                                    color = Color(0xFF262626),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = genre,
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (!item.overview.isNullOrBlank()) {
                        Text(
                            text = item.overview,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val targetEpisode = if (item.type == "TV_SHOW") {
                                    val targetEpisodeId = activeStatus?.episodeId ?: episodes.firstOrNull()?.id
                                    episodes.find { it.id == targetEpisodeId }
                                } else null

                                val targetUri = targetEpisode?.localFileUri ?: item.localUri
                                val targetSubtitleUri = targetEpisode?.subtitleUri ?: item.subtitleUri

                                val playTitle = if (item.type == "TV_SHOW") {
                                    if (targetEpisode != null) "${item.title} - S${targetEpisode.seasonNumber}E${targetEpisode.episodeNumber}" else item.title
                                } else item.title

                                onPlayClick(targetUri, playTitle, item.overview, lastPositionMs, targetSubtitleUri)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isResuming) "Resume" else "Play", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                onPlayOnPcClick(resumeEpisode, lastPositionMs)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
                        ) {
                            Icon(Icons.Default.Laptop, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play on PC", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }


                    if (isResuming && activeStatus?.totalDurationMs != null) {
                        val remainingMs = activeStatus.totalDurationMs - activeStatus.lastPositionMs
                        val progress = (activeStatus.lastPositionMs.toFloat() / activeStatus.totalDurationMs.toFloat()).coerceIn(0f, 1f)

                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 8.dp, end = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { progress },
                                color = Color(0xFFE50914),
                                trackColor = Color(0xFF333333),
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                            )
                            Text(
                                text = "${formatDuration(remainingMs)} remaining",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }


            if (item.type == "TV_SHOW" && episodes.isNotEmpty()) {
                item {
                    Text("Episodes", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                }
                items(episodes) { episode ->
                    val epStatus = playbackStatuses.find { it.episodeId == episode.id }
                    EpisodeRow(
                        episode = episode,
                        posterPath = item.posterPath,
                        status = epStatus,
                        onClick = {
                            onPlayClick(
                                episode.localFileUri,
                                "${item.title} - S${episode.seasonNumber}E${episode.episodeNumber}",
                                item.overview,
                                epStatus?.lastPositionMs ?: 0L,
                                episode.subtitleUri
                            )
                        },
                        onPlayOnPcClick = {
                            onPlayOnPcClick(episode, epStatus?.lastPositionMs ?: 0L)
                        }
                    )
                }
            }
            }
        }
    }


@Composable
fun EpisodeRow(
    episode: MediaEpisode,
    posterPath: String?,
    status: PlaybackStatus?,
    onClick: () -> Unit,
    onPlayOnPcClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 130.dp, height = 75.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF262626)),
            contentAlignment = Alignment.Center
        ) {
            val imageSource = episode.stillPath ?: posterPath
            if (imageSource != null) {
                AsyncImage(
                    model = if (imageSource.startsWith("http")) imageSource else File(imageSource),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White)

            if (status != null && status.totalDurationMs > 0L) {
                val progress = (status.lastPositionMs.toFloat() / status.totalDurationMs.toFloat()).coerceIn(0f, 1f)
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = Color(0xFFE50914),
                        trackColor = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${episode.episodeNumber}. ${episode.episodeTitle ?: "Episode ${episode.episodeNumber}"}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = "S${episode.seasonNumber} E${episode.episodeNumber}",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (!episode.episodeOverview.isNullOrBlank()) {
                Text(
                    text = episode.episodeOverview,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        IconButton(onClick = onPlayOnPcClick) {
            Icon(
                imageVector = Icons.Default.Laptop,
                contentDescription = "Play Episode on PC",
                tint = Color.LightGray
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"
}