package com.calebms.openflix.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import com.calebms.openflix.data.local.entities.MediaItem
import com.calebms.openflix.data.local.entities.Profile
import com.calebms.openflix.data.utils.AvatarUtils
import com.calebms.openflix.viewmodel.ProfileViewModel
import com.calebms.openflix.viewmodel.ScannerViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    scannerViewModel: ScannerViewModel? = null,
    onProfileSelected: (Profile) -> Unit
) {
    val profiles by viewModel.allProfiles.collectAsState()
    val allMedia by scannerViewModel?.allMedia?.collectAsState() ?: remember { mutableStateOf(emptyList()) }

    ProfileScreenContent(
        profiles = profiles,
        mediaList = allMedia,
        onProfileSelected = onProfileSelected,
        onCreateProfile = { name -> viewModel.createProfile(name) },
        onUpdateProfile = { profile -> viewModel.updateProfile(profile) },
        onDeleteProfile = { profile -> viewModel.deleteProfile(profile) }
    )
}

@Composable
fun ProfileScreenContent(
    profiles: List<Profile>,
    mediaList: List<MediaItem>,
    onProfileSelected: (Profile) -> Unit,
    onCreateProfile: (String) -> Unit,
    onUpdateProfile: (Profile) -> Unit = {},
    onDeleteProfile: (Profile) -> Unit = {}
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var isEditMode by remember { mutableStateOf(false) }

    // Rotating Backdrop Selection
    val mediaWithImages = remember(mediaList) {
        mediaList.filter { !it.backdropPath.isNullOrBlank() || !it.posterPath.isNullOrBlank() }
    }

    var currentBackdropIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(mediaWithImages) {
        if (mediaWithImages.isNotEmpty()) {
            while (isActive) {
                delay(4000L) // Rotate every 4 seconds
                currentBackdropIndex = (currentBackdropIndex + 1) % mediaWithImages.size
            }
        }
    }

    val currentMedia = mediaWithImages.getOrNull(currentBackdropIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
    ) {
        // 1. Full-screen Rotating Backdrop Image
        Crossfade(
            targetState = currentMedia,
            animationSpec = tween(durationMillis = 1000),
            modifier = Modifier.fillMaxSize()
        ) { media ->
            if (media != null) {
                val imgSource = media.backdropPath ?: media.posterPath
                if (imgSource != null) {
                    val file = File(imgSource)
                    val model = if (imgSource.startsWith("http")) imgSource else if (file.exists()) file else null

                    if (model != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = model,
                                contentDescription = media.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // 2. Heavy Dark Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.6f),
                            Color(0xFF141414).copy(alpha = 0.95f),
                            Color(0xFF141414)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // 3. Foreground Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Media Title Badge / OpenFlix Header
            Spacer(modifier = Modifier.height(16.dp))

            if (currentMedia != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 30.dp)
                ) {
                    Text(
                        text = "OPENFLIX",
                        color = Color(0xFFE50914),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = currentMedia.title,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(50.dp))
            }

            // Profiles & Action Buttons Section (Max 5 Profiles in 3-Column Grid)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Choose Your Profile",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                val displayedProfiles = remember(profiles) { profiles.take(5) }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(displayedProfiles) { profile ->
                        ProfileAvatarItem(
                            profile = profile,
                            isEditMode = isEditMode,
                            onClick = {
                                if (isEditMode) {
                                    editingProfile = profile
                                } else {
                                    onProfileSelected(profile)
                                }
                            }
                        )
                    }

                    if (profiles.size < 5) {
                        item {
                            ProfileSquareActionButton(
                                icon = Icons.Default.Add,
                                label = "Add",
                                isGridItem = true,
                                onClick = { showAddDialog = true }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Edit Button Centered Below Grid
                ProfileSquareActionButton(
                    icon = Icons.Default.Edit,
                    label = if (isEditMode) "Done" else "Edit",
                    isSelected = isEditMode,
                    isGridItem = false,
                    onClick = { isEditMode = !isEditMode }
                )
            }
        }
    }

    // New Profile Dialog
    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { if (profiles.isNotEmpty()) showAddDialog = false },
            containerColor = Color(0xFF222222),
            title = { Text("New Profile", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Profile Name", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFE50914),
                        unfocusedBorderColor = Color.Gray
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreateProfile(newName.trim())
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                if (profiles.isNotEmpty()) {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            }
        )
    }

    // Edit Profile Dialog
    editingProfile?.let { targetProfile ->
        var editName by remember { mutableStateOf(targetProfile.name) }
        var currentAvatarName by remember(targetProfile) { mutableStateOf(AvatarUtils.getAvatarName(targetProfile)) }
        var showAvatarPickerInEdit by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingProfile = null },
            containerColor = Color(0xFF222222),
            title = { Text("Edit Profile", color = Color.White) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(targetProfile.avatarColorHex))
                            .clickable { showAvatarPickerInEdit = true },
                        contentAlignment = Alignment.Center
                    ) {
                        ProfileAvatarImage(
                            avatarName = currentAvatarName,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Change Avatar",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Profile Name", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE50914),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (profiles.size > 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                onDeleteProfile(targetProfile)
                                editingProfile = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE50914))
                        ) {
                            Text("Delete Profile")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            onUpdateProfile(
                                targetProfile.copy(
                                    name = editName.trim(),
                                    avatarName = currentAvatarName
                                )
                            )
                            editingProfile = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProfile = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )

        if (showAvatarPickerInEdit) {
            AvatarSelectionDialog(
                selectedAvatarName = currentAvatarName,
                onAvatarSelected = { chosen ->
                    currentAvatarName = chosen
                    showAvatarPickerInEdit = false
                },
                onDismiss = { showAvatarPickerInEdit = false }
            )
        }
    }
}

@Composable
fun ProfileAvatarImage(
    avatarName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }

    AsyncImage(
        model = "file:///android_asset/$avatarName",
        imageLoader = imageLoader,
        contentDescription = "Profile Avatar",
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}

@Composable
fun AvatarSelectionDialog(
    selectedAvatarName: String,
    onAvatarSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF222222),
        title = {
            Text("Choose Avatar", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            ) {
                items(AvatarUtils.ALL_AVATARS) { avatarName ->
                    val isSelected = avatarName == selectedAvatarName
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFFE50914) else Color(0xFF333333))
                            .clickable { onAvatarSelected(avatarName) }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ProfileAvatarImage(
                            avatarName = avatarName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White)
            }
        }
    )
}

@Composable
fun ProfileAvatarItem(
    profile: Profile,
    isEditMode: Boolean = false,
    onClick: () -> Unit
) {
    val avatarName = remember(profile) { AvatarUtils.getAvatarName(profile) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(profile.avatarColorHex)),
            contentAlignment = Alignment.Center
        ) {
            ProfileAvatarImage(
                avatarName = avatarName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )

            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = profile.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ProfileSquareActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean = false,
    isGridItem: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(2.dp)
    ) {
        Box(
            modifier = if (isGridItem) {
                Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0xFFE50914) else Color(0xFF333333))
            } else {
                Modifier
                    .size(75.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0xFFE50914) else Color(0xFF333333))
            },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@com.calebms.openflix.ui.preview.PreviewDeviceSizes
@Composable
fun ProfileScreenPreview() {
    com.calebms.openflix.ui.theme.OpenFlixTheme {
        ProfileScreenContent(
            profiles = com.calebms.openflix.ui.preview.PreviewData.sampleProfiles,
            mediaList = com.calebms.openflix.ui.preview.PreviewData.sampleMediaList,
            onProfileSelected = {},
            onCreateProfile = {}
        )
    }
}
