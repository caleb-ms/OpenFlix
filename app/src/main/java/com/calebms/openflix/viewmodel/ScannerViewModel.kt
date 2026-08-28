package com.calebms.openflix.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calebms.openflix.data.local.AppDatabase
import com.calebms.openflix.data.local.entities.MediaItem
import com.calebms.openflix.data.local.entities.PlaybackStatus
import com.calebms.openflix.data.remote.tmdb.TmdbSyncManager
import com.calebms.openflix.data.scanner.MediaScanner
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val scanner = MediaScanner(application)

    val allMedia: StateFlow<List<MediaItem>> = db.mediaDao().getAllMedia()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )


    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun scanFolder(treeUri: Uri) {
        viewModelScope.launch {
            _isScanning.value = true

            getApplication<Application>().contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            scanner.scanDirectory(treeUri)
            scanner.verifyLibraryAvailability()

            _isScanning.value = false
            syncWithTmdb()
        }
    }

    private val tmdbSyncManager = TmdbSyncManager(application)

    fun syncWithTmdb(force: Boolean = false) {
        viewModelScope.launch {
            val sharedPrefs = getApplication<Application>().getSharedPreferences("openflix_prefs", Context.MODE_PRIVATE)
            val apiKey = sharedPrefs.getString("tmdb_api_key", "") ?: ""

            android.util.Log.d("ScannerViewModel", "syncWithTmdb called. Key found: ${apiKey.isNotBlank()}, force: $force")

            if (apiKey.isNotBlank()) {
                _isSyncing.value = true
                try {
                    tmdbSyncManager.syncLibrary(apiKey, force)
                } finally {
                    _isSyncing.value = false
                }
            }
        }
    }



    fun getEpisodesForShow(mediaId: String) = db.mediaDao().getEpisodesForShow(mediaId)


    fun getContinueWatching(profileId: Int): Flow<List<PlaybackStatus>> {
        return db.playbackDao().getContinueWatching(profileId)
    }


    fun getStatusesForMedia(profileId: Int, mediaId: String): Flow<List<PlaybackStatus>> {
        return db.playbackDao().getStatusesForMedia(profileId, mediaId)
    }

    init {
        scanPersistedFolders()
    }

    fun forceScan() {
        scanPersistedFolders(showLoading = true)
    }

    private fun scanPersistedFolders(showLoading: Boolean = false) {
        viewModelScope.launch {
            val persistedUris = getApplication<Application>().contentResolver.persistedUriPermissions

            if (persistedUris.isNotEmpty()) {
                if (showLoading) _isScanning.value = true


                persistedUris.forEach { permission ->
                    scanner.scanDirectory(permission.uri)
                }

                scanner.verifyLibraryAvailability()

                if (showLoading) _isScanning.value = false


                syncWithTmdb()
            } else {

                if (showLoading) _isScanning.value = false
            }
        }
    }

    fun savePlaybackProgress(
        profileId: Int,
        mediaId: String,
        episodeId: String?,
        positionMs: Long,
        durationMs: Long,
        isFinished: Boolean
    ) {

        if (durationMs <= 0L || positionMs < 0L) return

        viewModelScope.launch {
            db.playbackDao().upsertPlayback(
                profileId = profileId,
                mediaId = mediaId,
                episodeId = episodeId,
                position = positionMs,
                duration = durationMs,
                isFinished = isFinished
            )
        }
    }
}