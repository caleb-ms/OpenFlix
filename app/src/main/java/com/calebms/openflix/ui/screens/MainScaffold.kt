package com.calebms.openflix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ripple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.calebms.openflix.data.local.entities.Profile
import com.calebms.openflix.data.local.entities.MediaItem
import com.calebms.openflix.data.local.entities.MediaEpisode
import com.calebms.openflix.data.local.entities.PlaybackStatus
import com.calebms.openflix.viewmodel.ScannerViewModel
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.calebms.openflix.data.remote.updater.UpdateManager
import com.calebms.openflix.data.remote.updater.AppUpdater
import com.calebms.openflix.data.remote.updater.UpdateCheckState
import android.widget.Toast
import kotlinx.coroutines.launch

@Composable
fun MainScaffold(
    activeProfile: Profile,
    scannerViewModel: ScannerViewModel,
    profileViewModel: com.calebms.openflix.viewmodel.ProfileViewModel,
    onAddFolderClick: () -> Unit,
    onSwitchProfileClick: () -> Unit
) {
    var currentTab by remember { mutableStateOf("Home") }


    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }


    var activeVideoUri by remember { mutableStateOf<String?>(null) }
    var activeVideoTitle by remember { mutableStateOf("") }
    var activeVideoOverview by remember { mutableStateOf<String?>(null) }
    var activeStartPositionMs by remember { mutableLongStateOf(0L) }

    val context = LocalContext.current
    val updateManager = remember { UpdateManager(context) }
    val appUpdater = remember { AppUpdater(context) }
    var updateState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }




    val episodes by remember(selectedMedia?.id) {
        scannerViewModel.getEpisodesForShow(selectedMedia?.id ?: "")
    }.collectAsState(initial = emptyList())

    if (activeVideoUri != null && selectedMedia != null) {

        val nextEpisode = if (selectedMedia?.type == "TV_SHOW") {
            val currentIndex = episodes.indexOfFirst { it.localFileUri == activeVideoUri }
            if (currentIndex != -1 && currentIndex < episodes.size - 1) {
                episodes[currentIndex + 1]
            } else null
        } else null

        val activeEpisodeId = episodes.find { it.localFileUri == activeVideoUri }?.id

        VideoPlayerScreen(
            videoUri = activeVideoUri!!,
            title = activeVideoTitle,
            overview = activeVideoOverview,
            startPositionMs = activeStartPositionMs,
            onNavigateBack = { activeVideoUri = null },
            onNextEpisode = if (nextEpisode != null) {
                {
                    activeVideoUri = nextEpisode.localFileUri
                    activeVideoTitle = nextEpisode.episodeTitle ?: "Episode ${nextEpisode.episodeNumber}"
                }
            } else null,


            onSaveProgress = { positionMs, durationMs, isFinished ->
                scannerViewModel.savePlaybackProgress(
                    profileId = activeProfile.id,
                    mediaId = selectedMedia!!.id,
                    episodeId = activeEpisodeId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isFinished = isFinished
                )
            }
        )
    }

    else if (selectedMedia != null) {
        val currentItem = selectedMedia!!
        

        val playbackStatuses by remember(currentItem.id, activeProfile.id) {
            scannerViewModel.getStatusesForMedia(activeProfile.id, currentItem.id)
        }.collectAsState(initial = emptyList<PlaybackStatus>())

        MediaDetailScreen(
            item = currentItem,
            episodes = episodes,
            playbackStatuses = playbackStatuses,
            onBackClick = { selectedMedia = null },
            onPlayClick = { uri, title, overview, startPositionMs ->
                activeVideoUri = uri
                activeVideoTitle = title
                activeVideoOverview = overview
                activeStartPositionMs = startPositionMs
            }
        )
    } else {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141414))
        ) {

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when (currentTab) {
                    "Home" -> FilteredHomeScreen(
                        profile = activeProfile,
                        viewModel = scannerViewModel,
                        onMediaClick = { clickedItem ->
                            selectedMedia = clickedItem
                        }
                    )
                    "Search" -> {

                        SearchScreen(
                            viewModel = scannerViewModel,
                            onMediaClick = { clickedItem ->
                                selectedMedia = clickedItem
                            }
                        )
                    }
                    "My Openflix" -> {
                        val isScanning by scannerViewModel.isScanning.collectAsState()
                        val isSyncing by scannerViewModel.isSyncing.collectAsState()

                        MyOpenFlixScreen(
                            activeProfile = activeProfile,
                            isScanning = isScanning,
                            isSyncing = isSyncing,
                            onUpdateProfileName = { newName ->
                                profileViewModel.updateProfile(activeProfile.copy(name = newName))
                            },
                            onAddFolderClick = onAddFolderClick,
                            onForceRescanClick = {
                                scannerViewModel.forceScan()
                            },
                            onSwitchProfileClick = onSwitchProfileClick,
                            onSyncMetadataClick = {
                                scannerViewModel.syncWithTmdb(force = false)
                            },
                            onForceSyncClick = {
                                scannerViewModel.syncWithTmdb(force = true)
                            },
                            onCheckForUpdatesClick = {
                                CoroutineScope(Dispatchers.Main).launch {
                                    updateState = UpdateCheckState.Checking
                                    updateState = updateManager.checkForUpdates()
                                    if (updateState is UpdateCheckState.UpToDate) {
                                        Toast.makeText(context, "You are on the latest version!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }


                if (updateState is UpdateCheckState.UpdateAvailable) {
                    val info = (updateState as UpdateCheckState.UpdateAvailable).info
                    UpdateDialog(
                        updateInfo = info,
                        onDismiss = { updateState = UpdateCheckState.Idle },
                        onDownloadConfirm = {
                            appUpdater.downloadAndInstall(info.downloadUrl, info.versionName)
                        }
                    )
                }
            }


            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 40.dp, vertical = 20.dp)
                    .height(64.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color(0xFF262626).copy(alpha = 0.95f),
                tonalElevation = 0.dp
            ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home Tab
                NavBarItem(
                    title = "Home",
                    icon = Icons.Default.Home,
                    isSelected = currentTab == "Home",
                    onClick = { currentTab = "Home" },
                    modifier = Modifier.weight(1f)
                )

                // Search Tab
                NavBarItem(
                    title = "Search",
                    icon = Icons.Default.Search,
                    isSelected = currentTab == "Search",
                    onClick = { currentTab = "Search" },
                    modifier = Modifier.weight(1f)
                )

                // Profile Tab
                NavBarItem(
                    title = "My Openflix",
                    icon = Icons.Default.Person,
                    isSelected = currentTab == "My Openflix",
                    onClick = { currentTab = "My Openflix" },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
}

@Composable
private fun NavBarItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (isSelected) Color.White else Color(0xFF8E8E93)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.2f)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}