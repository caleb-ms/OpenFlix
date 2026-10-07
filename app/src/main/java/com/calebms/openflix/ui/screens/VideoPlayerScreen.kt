package com.calebms.openflix.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class MediaTrackItem(
    val id: Int,
    val name: String,
    val isSelected: Boolean
)

@Composable
fun VideoPlayerScreen(
    videoUri: String,
    title: String,
    overview: String?,
    artworkUri: String? = null,
    startPositionMs: Long = 0L,
    autoDetectedSubtitleUri: String? = null,
    onNavigateBack: () -> Unit,
    onNextEpisode: (() -> Unit)? = null,
    onSaveProgress: (positionMs: Long, durationMs: Long, isFinished: Boolean) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // Basic Playback States
    var isPlaying by remember { mutableStateOf(false) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var showPauseOverlay by remember { mutableStateOf(false) }
    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var showUpNextPrompt by remember { mutableStateOf(false) }
    var upNextCancelled by remember { mutableStateOf(false) }

    // Helper to copy content:// URIs to local cache so LibVLC native C parser can read them
    fun copyUriToTempFile(uri: Uri): Uri {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return uri
            val tempFile = File(context.cacheDir, "temp_sub_${System.currentTimeMillis()}.srt")
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            Uri.fromFile(tempFile)
        } catch (e: Exception) {
            e.printStackTrace()
            uri
        }
    }

    // LibVLC Initialization
    val libVLC = remember {
        val args = arrayListOf(
            "--video-filter=deinterlace",
            "--deinterlace=1",
            "--aout=audiotrack",
            "-vvv"
        )
        LibVLC(context, args)
    }

    val mediaPlayer = remember(libVLC) {
        MediaPlayer(libVLC)
    }

    // PiP Mode
    var isInPipMode by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity != null) {
                activity.isInPictureInPictureMode
            } else false
        )
    }

    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    DisposableEffect(isPlaying, isPlayerReady) {
        val canAutoPip = isPlaying && isPlayerReady
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
            val builder = PictureInPictureParams.Builder()
                .setAutoEnterEnabled(canAutoPip)
            activity.setPictureInPictureParams(builder.build())
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
                val builder = PictureInPictureParams.Builder()
                    .setAutoEnterEnabled(false)
                activity.setPictureInPictureParams(builder.build())
            }
        }
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            val builder = PictureInPictureParams.Builder()
            activity.enterPictureInPictureMode(builder.build())
        }
    }

    // Zoom / Aspect Ratio Mode (0 = FIT, 1 = ZOOM / FILL)
    var isZoomMode by remember { mutableStateOf(false) }
    var seekAnimationText by remember { mutableStateOf("") }
    var showSeekAnimation by remember { mutableStateOf(false) }

    // Volume & Brightness Gesture States
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember(audioManager) { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }

    var gestureIndicatorText by remember { mutableStateOf("") }
    var gestureIndicatorIcon by remember { mutableStateOf<ImageVector>(Icons.Default.VolumeUp) }
    var showGestureIndicator by remember { mutableStateOf(false) }

    // Media Track States
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    var audioTracks by remember { mutableStateOf<List<MediaTrackItem>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<MediaTrackItem>>(emptyList()) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var externalSubtitleUri by remember { mutableStateOf<Uri?>(null) }
    var currentLoadedVideoUri by remember { mutableStateOf<String?>(null) }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val localSubUri = if (uri.scheme == "content") copyUriToTempFile(uri) else uri
                externalSubtitleUri = localSubUri
                mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, localSubUri, true)
            }
        }
    )

    val currentOnNextEpisode by rememberUpdatedState(onNextEpisode)

    // MediaSessionCompat Setup for OS Notifications, Headsets & Lock Screen
    val mediaSession = remember {
        MediaSessionCompat(context, "OpenFlixVLCSession_${System.currentTimeMillis()}").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
    }

    DisposableEffect(mediaSession) {
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                mediaPlayer.play()
            }

            override fun onPause() {
                mediaPlayer.pause()
            }

            override fun onSkipToNext() {
                currentOnNextEpisode?.invoke()
            }

            override fun onSeekTo(pos: Long) {
                mediaPlayer.time = pos
            }
        })

        onDispose {
            mediaSession.isActive = false
            mediaSession.release()
        }
    }

    fun updateMediaSessionState(playing: Boolean, position: Long) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SEEK_TO or
                if (currentOnNextEpisode != null) PlaybackStateCompat.ACTION_SKIP_TO_NEXT else 0L

        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, position, 1.0f)
                .build()
        )
    }

    // Refresh audio and subtitle tracks from LibVLC
    fun refreshTracks() {
        val vlcAudio = mediaPlayer.audioTracks ?: emptyArray()
        val currentAudioId = mediaPlayer.audioTrack
        audioTracks = vlcAudio.filter { it.id != -1 }.map {
            MediaTrackItem(
                id = it.id,
                name = it.name.ifBlank { "Track ${it.id}" },
                isSelected = it.id == currentAudioId
            )
        }

        val vlcSpu = mediaPlayer.spuTracks ?: emptyArray()
        val currentSpuId = mediaPlayer.spuTrack
        subtitleTracks = vlcSpu.filter { it.id != -1 }.map {
            MediaTrackItem(
                id = it.id,
                name = it.name.ifBlank { "Subtitle ${it.id}" },
                isSelected = it.id == currentSpuId
            )
        }
    }

    // LibVLC Event Listener
    DisposableEffect(mediaPlayer) {
        val listener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    isPlayerReady = true
                    updateMediaSessionState(true, mediaPlayer.time)
                }
                MediaPlayer.Event.Paused -> {
                    isPlaying = false
                    updateMediaSessionState(false, mediaPlayer.time)
                }
                MediaPlayer.Event.Stopped -> {
                    isPlaying = false
                    updateMediaSessionState(false, 0L)
                }
                MediaPlayer.Event.TimeChanged -> {
                    if (!isSeeking) {
                        currentTimeMs = event.timeChanged
                    }
                    if (durationMs <= 0L && mediaPlayer.length > 0L) {
                        durationMs = mediaPlayer.length
                    }
                }
                MediaPlayer.Event.LengthChanged -> {
                    durationMs = event.lengthChanged
                }
                MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> {
                    refreshTracks()
                }
                MediaPlayer.Event.EndReached -> {
                    isPlaying = false
                    isPlayerReady = false
                    currentOnNextEpisode?.invoke()
                }
            }
        }

        mediaPlayer.setEventListener(listener)

        onDispose {
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
            libVLC.release()
        }
    }

    var artworkBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(artworkUri) {
        if (!artworkUri.isNullOrBlank()) {
            try {
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(if (artworkUri.startsWith("http")) artworkUri else File(artworkUri))
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    artworkBitmap = (result.drawable as? BitmapDrawable)?.bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            artworkBitmap = null
        }
    }

    LaunchedEffect(title, overview, artworkBitmap, durationMs) {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, overview)

        if (durationMs > 0L) {
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
        }

        if (artworkBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artworkBitmap)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artworkBitmap)
        }

        mediaSession.setMetadata(metadataBuilder.build())
    }

    // Load video
    LaunchedEffect(videoUri, externalSubtitleUri, autoDetectedSubtitleUri) {
        val isNewVideo = currentLoadedVideoUri != videoUri
        currentLoadedVideoUri = videoUri

        val currentTargetPosition = if (isNewVideo) {
            currentTimeMs = startPositionMs
            startPositionMs
        } else {
            if (isPlayerReady && currentTimeMs > 0L) currentTimeMs else startPositionMs
        }

        if (isNewVideo) {
            externalSubtitleUri = null
            durationMs = 0L
        }

        isPlayerReady = false
        showUpNextPrompt = false
        upNextCancelled = false

        try {
            val parsedUri = Uri.parse(videoUri)
            val media = if (parsedUri.scheme == "content") {
                val pfd = context.contentResolver.openFileDescriptor(parsedUri, "r")
                if (pfd != null) Media(libVLC, pfd.fileDescriptor) else Media(libVLC, parsedUri)
            } else {
                Media(libVLC, parsedUri)
            }

            val activeSubtitleUri: Any? = externalSubtitleUri ?: autoDetectedSubtitleUri
            activeSubtitleUri?.let { subUriObj ->
                val subUri = if (subUriObj is Uri) subUriObj else Uri.parse(subUriObj.toString())
                val localSubUri = if (subUri.scheme == "content") copyUriToTempFile(subUri) else subUri
                media.addSlave(IMedia.Slave(IMedia.Slave.Type.Subtitle, 4, localSubUri.toString()))
            }

            mediaPlayer.media = media
            media.release()

            mediaPlayer.play()
            if (currentTargetPosition > 0L) {
                mediaPlayer.time = currentTargetPosition
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Aspect Ratio / Zoom Handler
    LaunchedEffect(isZoomMode) {
        if (isZoomMode) {
            mediaPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_FILL
            mediaPlayer.aspectRatio = null
        } else {
            mediaPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
            mediaPlayer.aspectRatio = null
        }
    }

    // System Orientation & Bars Setup
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler { onNavigateBack() }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Periodic Progress Saver
    LaunchedEffect(isPlaying, isPlayerReady) {
        while (isActive && isPlaying && isPlayerReady) {
            delay(10000)
            val pos = mediaPlayer.time
            val dur = mediaPlayer.length.coerceAtLeast(1L)
            onSaveProgress(pos, dur, pos >= (dur * 0.95))
        }
    }

    var vlcVideoLayout by remember { mutableStateOf<VLCVideoLayout?>(null) }

    // Lifecycle Observer
    DisposableEffect(lifecycleOwner, isPlayerReady) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    if (isPlayerReady) {
                        mediaPlayer.pause()
                        val pos = mediaPlayer.time
                        val dur = mediaPlayer.length.coerceAtLeast(1L)
                        onSaveProgress(pos, dur, pos >= (dur * 0.95))
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    vlcVideoLayout?.let { layout ->
                        layout.post {
                            mediaPlayer.detachViews()
                            mediaPlayer.attachViews(layout, null, true, false)
                            val pos = if (currentTimeMs > 0L) currentTimeMs else mediaPlayer.time
                            if (pos > 0L) {
                                mediaPlayer.time = pos
                            }
                            if (isPlaying) {
                                mediaPlayer.play()
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (isPlayerReady) {
                val pos = mediaPlayer.time
                val dur = mediaPlayer.length.coerceAtLeast(1L)
                onSaveProgress(pos, dur, pos >= (dur * 0.95))
            }
        }
    }

    // Up-next trigger check
    LaunchedEffect(isPlaying, isSeeking) {
        while (isActive && isPlaying && !isSeeking) {
            currentTimeMs = mediaPlayer.time

            if (onNextEpisode != null && durationMs > 0) {
                val threshold = (durationMs * 0.96).toLong()
                if (currentTimeMs >= threshold) {
                    if (!upNextCancelled) {
                        showUpNextPrompt = true
                    }
                } else {
                    showUpNextPrompt = false
                    upNextCancelled = false
                }
            }
            delay(1000)
        }
    }

    // Controls visibility fade
    LaunchedEffect(isPlaying, showControls) {
        if (!isPlaying) {
            delay(5000)
            showPauseOverlay = true
            showControls = false
        } else {
            showPauseOverlay = false
            if (showControls) {
                delay(4000)
                showControls = false
            }
        }
    }

    LaunchedEffect(showSeekAnimation) {
        if (showSeekAnimation) {
            delay(600)
            showSeekAnimation = false
        }
    }

    LaunchedEffect(showGestureIndicator) {
        if (showGestureIndicator) {
            delay(1200)
            showGestureIndicator = false
        }
    }

    LaunchedEffect(currentSpeed) {
        mediaPlayer.rate = currentSpeed
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    keepScreenOn = true
                    vlcVideoLayout = this
                }
            },
            update = { layout ->
                vlcVideoLayout = layout
                layout.post {
                    mediaPlayer.detachViews()
                    // Passing false uses SurfaceView, ensuring standard hardware-overlay decode
                    mediaPlayer.attachViews(layout, null, true, false)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture Interceptor
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var lastTapTime = 0L
                    var lastTapX = 0f

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        val startX = down.position.x
                        val startY = down.position.y
                        val viewWidth = size.width.toFloat()
                        val viewHeight = size.height.toFloat()

                        var isMultiTouch = false
                        var isDragStarted = false
                        var isDraggingLeftLocal = false

                        var startBrightnessVal = 0.5f
                        var startVolumeVal = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressedPointers = event.changes.filter { it.pressed }

                            if (pressedPointers.size > 1) {
                                isMultiTouch = true
                                val p1 = pressedPointers[0].position
                                val p2 = pressedPointers[1].position
                                val currentDist = hypot(p2.x - p1.x, p2.y - p1.y)

                                val prevP1 = pressedPointers[0].previousPosition
                                val prevP2 = pressedPointers[1].previousPosition
                                val prevDist = hypot(prevP2.x - prevP1.x, prevP2.y - prevP1.y)

                                if (prevDist > 0f) {
                                    val zoom = currentDist / prevDist
                                    if (zoom > 1.05f) {
                                        isZoomMode = true
                                    } else if (zoom < 0.95f) {
                                        isZoomMode = false
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            } else if (pressedPointers.size == 1 && !isMultiTouch) {
                                val change = pressedPointers[0]
                                val deltaX = change.position.x - startX
                                val deltaY = change.position.y - startY
                                val dist = sqrt(deltaX * deltaX + deltaY * deltaY)

                                if (!isDragStarted && dist > viewConfiguration.touchSlop) {
                                    if (abs(deltaY) > abs(deltaX)) {
                                        isDragStarted = true
                                        isDraggingLeftLocal = startX < (viewWidth / 2f)

                                        if (isDraggingLeftLocal) {
                                            val currentBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                                            startBrightnessVal = if (currentBrightness < 0f) 0.5f else currentBrightness
                                            gestureIndicatorIcon = Icons.Default.WbSunny
                                            gestureIndicatorText = "Brightness ${(startBrightnessVal * 100).toInt()}%"
                                        } else {
                                            val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                                            startVolumeVal = currentVol.toFloat()
                                            val volPercent = ((currentVol.toFloat() / maxVolume.toFloat()) * 100).toInt()
                                            gestureIndicatorIcon = if (currentVol == 0) Icons.Default.VolumeOff else Icons.Default.VolumeUp
                                            gestureIndicatorText = "Volume $volPercent%"
                                        }
                                        showGestureIndicator = true
                                    }
                                }

                                if (isDragStarted) {
                                    change.consume()
                                    val totalDragY = change.position.y - startY
                                    val fraction = -totalDragY / viewHeight

                                    if (isDraggingLeftLocal) {
                                        val window = activity?.window
                                        if (window != null) {
                                            val newBrightness = (startBrightnessVal + fraction).coerceIn(0.01f, 1.0f)
                                            val layoutParams = window.attributes
                                            layoutParams.screenBrightness = newBrightness
                                            window.attributes = layoutParams

                                            gestureIndicatorIcon = Icons.Default.WbSunny
                                            gestureIndicatorText = "Brightness ${(newBrightness * 100).toInt()}%"
                                        }
                                    } else {
                                        if (audioManager != null) {
                                            val volDelta = fraction * maxVolume.toFloat()
                                            val targetVol = (startVolumeVal + volDelta).coerceIn(0f, maxVolume.toFloat())
                                            val newVolInt = targetVol.roundToInt()
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolInt, 0)

                                            val volPercent = ((newVolInt.toFloat() / maxVolume.toFloat()) * 100).toInt()
                                            gestureIndicatorIcon = if (newVolInt == 0) Icons.Default.VolumeOff else Icons.Default.VolumeUp
                                            gestureIndicatorText = "Volume $volPercent%"
                                        }
                                    }
                                    showGestureIndicator = true
                                }
                            } else if (pressedPointers.isEmpty()) {
                                if (isDragStarted) {
                                    showGestureIndicator = false
                                } else if (!isMultiTouch) {
                                    val duration = System.currentTimeMillis() - downTime
                                    val deltaX = down.position.x - startX
                                    val deltaY = down.position.y - startY
                                    val dist = sqrt(deltaX * deltaX + deltaY * deltaY)

                                    if (duration < 500 && dist < viewConfiguration.touchSlop) {
                                        val tapTime = System.currentTimeMillis()
                                        if (tapTime - lastTapTime < 300L && abs(startX - lastTapX) < 150f) {
                                            lastTapTime = 0L
                                            if (startX < (viewWidth / 2f)) {
                                                val target = (mediaPlayer.time - 10000).coerceAtLeast(0)
                                                mediaPlayer.time = target
                                                seekAnimationText = "<< -10s"
                                            } else {
                                                val target = (mediaPlayer.time + 10000).coerceAtMost(durationMs)
                                                mediaPlayer.time = target
                                                seekAnimationText = ">> +10s"
                                            }
                                            currentTimeMs = mediaPlayer.time
                                            showSeekAnimation = true
                                        } else {
                                            lastTapTime = tapTime
                                            lastTapX = startX
                                            if (showUpNextPrompt) {
                                                upNextCancelled = true
                                                showUpNextPrompt = false
                                            } else if (showPauseOverlay) {
                                                showPauseOverlay = false
                                                showControls = true
                                            } else {
                                                showControls = !showControls
                                            }
                                        }
                                    }
                                }
                                break
                            }
                        }
                    }
                }
        )

        // Volume & Brightness Overlay
        AnimatedVisibility(
            visible = showGestureIndicator && !isInPipMode,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(10f)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = gestureIndicatorIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = gestureIndicatorText,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Seek Animation Bubble
        AnimatedVisibility(
            visible = showSeekAnimation && !isInPipMode,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(text = seekAnimationText, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Pause Overlay
        AnimatedVisibility(
            visible = showPauseOverlay && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(40.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(modifier = Modifier.fillMaxWidth(0.6f)) {
                    Text(text = title, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = overview ?: "", color = Color.LightGray, fontSize = 16.sp, lineHeight = 24.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Tap anywhere to resume", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }

        // Custom Overlay Controls
        AnimatedVisibility(
            visible = showControls && !showPauseOverlay && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Exit", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = {
                        if (isPlaying) {
                            mediaPlayer.pause()
                        } else {
                            mediaPlayer.play()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(80.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = formatPlayerTime(currentTimeMs), color = Color.White, fontSize = 14.sp)
                        Slider(
                            value = currentTimeMs.toFloat(),
                            onValueChange = {
                                isSeeking = true
                                currentTimeMs = it.toLong()
                            },
                            onValueChangeFinished = {
                                isSeeking = false
                                mediaPlayer.time = currentTimeMs
                            },
                            valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFE50914),
                                activeTrackColor = Color(0xFFE50914),
                                inactiveTrackColor = Color.Gray
                            )
                        )
                        Text(text = formatPlayerTime((durationMs - currentTimeMs).coerceAtLeast(0L)), color = Color.LightGray, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        PlayerActionButton(Icons.Default.Speed, "${currentSpeed}x") { showSpeedDialog = true }

                        PlayerActionButton(Icons.Default.ClosedCaption, "Subtitles") {
                            refreshTracks()
                            showSubtitleDialog = true
                        }

                        if (audioTracks.isNotEmpty()) {
                            PlayerActionButton(Icons.Default.Audiotrack, "Audio") {
                                refreshTracks()
                                showAudioDialog = true
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                            PlayerActionButton(Icons.Default.PictureInPicture, "PiP") {
                                enterPipMode()
                            }
                        }

                        if (onNextEpisode != null) {
                            PlayerActionButton(Icons.Default.SkipNext, "Next Ep.") { onNextEpisode() }
                        }
                    }
                }
            }
        }

        // Up Next Floating Pill
        AnimatedVisibility(
            visible = showUpNextPrompt && !isInPipMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 48.dp, end = 48.dp)
        ) {
            UpNextPill(
                episodeKey = videoUri,
                onNextEpisode = {
                    showUpNextPrompt = false
                    onNextEpisode?.invoke()
                },
                onCancel = {
                    upNextCancelled = true
                    showUpNextPrompt = false
                }
            )
        }

        // Speed Dialog
        if (showSpeedDialog && !isInPipMode) {
            TrackSelectionDialog(
                title = "Playback Speed",
                options = listOf("0.5x", "0.75x", "1.0x (Normal)", "1.25x", "1.5x"),
                selectedIndex = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f).indexOf(currentSpeed),
                onDismiss = { showSpeedDialog = false },
                onSelect = { index ->
                    currentSpeed = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)[index]
                    showSpeedDialog = false
                }
            )
        }

        // Audio Tracks Dialog
        if (showAudioDialog && !isInPipMode) {
            TrackSelectionDialog(
                title = "Audio Tracks",
                options = audioTracks.map { it.name },
                selectedIndex = audioTracks.indexOfFirst { it.isSelected },
                onDismiss = { showAudioDialog = false },
                onSelect = { index ->
                    val chosen = audioTracks[index]
                    mediaPlayer.audioTrack = chosen.id
                    refreshTracks()
                    showAudioDialog = false
                }
            )
        }

        // Subtitles Dialog
        if (showSubtitleDialog && !isInPipMode) {
            TrackSelectionDialog(
                title = "Subtitles",
                options = listOf("Off") + subtitleTracks.map { it.name },
                selectedIndex = if (subtitleTracks.none { it.isSelected }) 0 else subtitleTracks.indexOfFirst { it.isSelected } + 1,
                onDismiss = { showSubtitleDialog = false },
                onSelect = { index ->
                    if (index == 0) {
                        mediaPlayer.spuTrack = -1
                    } else {
                        val chosen = subtitleTracks[index - 1]
                        mediaPlayer.spuTrack = chosen.id
                    }
                    refreshTracks()
                    showSubtitleDialog = false
                },
                onLoadExternal = {
                    showSubtitleDialog = false
                    subtitlePicker.launch(arrayOf("application/x-subrip", "text/vtt", "application/octet-stream", "*/*"))
                }
            )
        }
    }
}

@Composable
fun TrackSelectionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onLoadExternal: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF222222),
        title = { Text(title, color = Color.White) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 350.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEachIndexed { index, name ->
                    TextButton(
                        onClick = { onSelect(index) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = name,
                            color = if (index == selectedIndex) Color(0xFFE50914) else Color.White,
                            fontSize = 16.sp
                        )
                    }
                }

                if (onLoadExternal != null) {
                    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(
                        onClick = { onLoadExternal() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Load External Subtitle...", color = Color.LightGray, fontSize = 16.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun PlayerActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = Color.LightGray, fontSize = 12.sp)
    }
}

fun formatPlayerTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun UpNextPill(
    episodeKey: String,
    onNextEpisode: () -> Unit,
    onCancel: () -> Unit
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(episodeKey) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 10_000,
                easing = LinearEasing
            )
        )
        onNextEpisode()
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF555555))
                .clickable { onCancel() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Watch Credits",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White)
                .clickable { onNextEpisode() }
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val remainingWidth = size.width * (1f - progress.value)
                        drawRect(
                            color = Color(0xFFBDBDBD),
                            topLeft = Offset(x = size.width - remainingWidth, y = 0f),
                            size = size.copy(width = remainingWidth)
                        )
                    }
            )

            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )

                Text(
                    text = "Next Episode",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}