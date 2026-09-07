package com.calebms.openflix.ui.screens
import com.calebms.openflix.BuildConfig

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calebms.openflix.data.local.entities.Profile

@Composable
fun MyOpenFlixScreen(
    activeProfile: Profile,
    isScanning: Boolean,
    isSyncing: Boolean,
    onUpdateProfileName: (String) -> Unit,
    onAddFolderClick: () -> Unit,
    onForceRescanClick: () -> Unit,
    onSwitchProfileClick: () -> Unit,
    onSyncMetadataClick: () -> Unit,
    onForceSyncClick: () -> Unit,
    onCheckForUpdatesClick: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current


    var editName by remember(activeProfile.name) { mutableStateOf(activeProfile.name) }
    val isNameChanged = editName.trim() != activeProfile.name && editName.isNotBlank()


    val sharedPrefs = remember { context.getSharedPreferences("openflix_prefs", Context.MODE_PRIVATE) }
    var tmdbKey by remember { mutableStateOf(sharedPrefs.getString("tmdb_api_key", "") ?: "") }
    var tmdbSaveStatus by remember { mutableStateOf("") }


    var isApiKeyVisible by remember { mutableStateOf(false) }

    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
    }
    val currentVersionName = packageInfo?.versionName ?: "1.0.0"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentPadding = PaddingValues(top = 40.dp, bottom = 100.dp)
    ) {

        item {
            Text("My OpenFlix", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
        }


        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(activeProfile.avatarColorHex)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = editName.take(1).uppercase(),
                            color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Profile Name", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Gray,
                            unfocusedBorderColor = Color(0xFF333333)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isNameChanged) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onUpdateProfileName(editName.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Profile", color = Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }


        item {
            Text("Library", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            SettingsCard {
                SettingsActionRow(icon = Icons.Default.CreateNewFolder, title = "Add Media Folder", onClick = onAddFolderClick)
                Divider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { if (!isScanning) onForceRescanClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = if (isScanning) Color.Gray else Color.White)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(if (isScanning) "Scanning Library..." else "Force Rescan Library", color = if (isScanning) Color.Gray else Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (isScanning) {
                        CircularProgressIndicator(color = Color(0xFFE50914), modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }



        item {
            Text("Advanced", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            SettingsCard {
                Text("TMDB API Key (Global)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Provide your own API key to automatically fetch posters, synopses, and metadata.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))


                OutlinedTextField(
                    value = tmdbKey,
                    onValueChange = { tmdbKey = it; tmdbSaveStatus = "" },
                    placeholder = { Text("Paste API Key here", color = Color.Gray) },
                    singleLine = true,
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val icon = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Icon(imageVector = icon, contentDescription = "Toggle Key Visibility", tint = Color.Gray)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Gray,
                        unfocusedBorderColor = Color(0xFF333333)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = tmdbSaveStatus, color = Color(0xFF46D369), fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                sharedPrefs.edit().putString("tmdb_api_key", tmdbKey.trim()).apply()
                                tmdbSaveStatus = "Key Saved Successfully"
                                onSyncMetadataClick()
                            },
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                        ) {
                            Text("Save", color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                tmdbSaveStatus = "Syncing..."
                                onForceSyncClick()
                            },
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Sync Now", color = Color.White)
                            }
                        }
                    }
                }

                LaunchedEffect(isSyncing) {
                    if (!isSyncing && tmdbSaveStatus == "Syncing...") {
                        tmdbSaveStatus = "Library Sync Complete!"
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }


        item {
            Text("About", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            SettingsCard {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Version", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("OpenFlix for Android", color = Color.Gray, fontSize = 12.sp)
                    }
                    Text(
                        text = "v$currentVersionName",
                        color = Color(0xFF46D369),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Divider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 12.dp))

                if (BuildConfig.ENABLE_IN_APP_UPDATER) {
                    SettingsActionRow(
                        icon = Icons.Default.SystemUpdate,
                        title = "Check for Updates",
                        onClick = onCheckForUpdatesClick
                    )
                    Divider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 12.dp))
                }


                SettingsLinkRow(title = "Github", url="https://github.com/caleb-ms/OpenFlix.git", uriHandler = uriHandler)

                Divider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 12.dp))

                SettingsLinkRow(title = "Built by Caleb MS Group", url = "https://openflix.calebms.com", uriHandler = uriHandler)
                Divider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 12.dp))
                SettingsLinkRow(title = "Privacy Policy", url = "https://openflix.calebms.com/privacy", uriHandler = uriHandler)
                Divider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 12.dp))
                SettingsLinkRow(title = "Terms of Service", url = "https://openflix.calebms.com/terms", uriHandler = uriHandler)
                Divider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 12.dp))
                SettingsLinkRow(title = "Disclaimer", url = "https://openflix.calebms.com/disclaimer", uriHandler = uriHandler)
            }
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onSwitchProfileClick,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
            ) {
                Text("Switch Profile", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF222222))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun SettingsActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}

@Composable
fun SettingsLinkRow(title: String, url: String, uriHandler: androidx.compose.ui.platform.UriHandler) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(url) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.LightGray, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
    }
}