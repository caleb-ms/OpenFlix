package com.calebms.openflix

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calebms.openflix.data.local.entities.MediaItem
import com.calebms.openflix.data.local.entities.Profile
import com.calebms.openflix.ui.screens.MainScaffold
import com.calebms.openflix.ui.screens.OnboardingScreen
import com.calebms.openflix.ui.screens.ProfileScreen
import com.calebms.openflix.ui.screens.AnimatedSplashScreen
import com.calebms.openflix.ui.theme.OpenFlixTheme
import com.calebms.openflix.viewmodel.ProfileViewModel
import com.calebms.openflix.viewmodel.ScannerViewModel
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenFlixTheme {
                val profileViewModel: ProfileViewModel = viewModel()
                val scannerViewModel: ScannerViewModel = viewModel()

                OpenFlixApp(profileViewModel, scannerViewModel)
            }
        }
    }
}

@Composable
fun OpenFlixApp(
    profileViewModel: ProfileViewModel,
    scannerViewModel: ScannerViewModel
) {
    // Splash State
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        AnimatedSplashScreen(onSplashFinished = { showSplash = false })
    } else {

        val profiles by profileViewModel.allProfiles.collectAsState()
        var selectedProfileId by remember { mutableStateOf<Int?>(null) }
        
        val selectedProfile = profiles.find { it.id == selectedProfileId }
        val mediaList by scannerViewModel.allMedia.collectAsState()
        val isScanning by scannerViewModel.isScanning.collectAsState()

        val folderPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            uri?.let { scannerViewModel.scanFolder(it) }
        }


        when {

            selectedProfile == null -> {
                ProfileScreen(
                    viewModel = profileViewModel,
                    onProfileSelected = { profile ->
                        selectedProfileId = profile.id
                    }
                )
            }


            mediaList.isEmpty() -> {
                OnboardingScreen(
                    isScanning = isScanning,
                    onSelectFolderClick = { folderPickerLauncher.launch(null) }
                )
            }


            else -> {
                MainScaffold(
                    activeProfile = selectedProfile,
                    scannerViewModel = scannerViewModel,
                    profileViewModel = profileViewModel,
                    onAddFolderClick = { folderPickerLauncher.launch(null) },
                    onSwitchProfileClick = { selectedProfileId = null }
                )
            }
        }
    }
}
