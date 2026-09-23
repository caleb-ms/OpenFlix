package com.calebms.openflix.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext


data class MediaTrack(
    val group: androidx.media3.common.Tracks.Group,
    val trackIndex: Int,
    val name: String,
    val isSelected: Boolean
)

@OptIn(UnstableApi::class)
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



    // Basic States
    var isPlaying by remember { mutableStateOf(false) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var showPauseOverlay by remember { mutableStateOf(false) }
    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var showUpNextPrompt by remember { mutableStateOf(false) }
    var upNextCancelled by remember { mutableStateOf(false) }

    // PiP State
    var isInPipMode by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity != null) activity.isInPictureInPictureMode else false) }

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

    // Gesture & Animation States
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
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

    var audioTracks by remember { mutableStateOf<List<MediaTrack>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<MediaTrack>>(emptyList()) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var externalSubtitleUri by remember { mutableStateOf<Uri?>(null) }
    var currentLoadedVideoUri by remember { mutableStateOf<String?>(null) }

    val subtitlePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                externalSubtitleUri = uri
            }
        }
    )


    val exoPlayer = remember {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .build().apply {
                playWhenReady = true
            }
    }

    val currentOnNextEpisode by rememberUpdatedState(onNextEpisode)

    val forwardingPlayer = remember(exoPlayer) {
        object : ForwardingPlayer(exoPlayer) {
            override fun isCommandAvailable(command: Int): Boolean {
                if ((command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) && currentOnNextEpisode != null) {
                    return true
                }
                return super.isCommandAvailable(command)
            }

            override fun getAvailableCommands(): Player.Commands {
                val commands = super.getAvailableCommands().buildUpon()
                if (currentOnNextEpisode != null) {
                    commands.add(Player.COMMAND_SEEK_TO_NEXT)
                    commands.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                }
                return commands.build()
            }

            override fun hasNextMediaItem(): Boolean {
                return currentOnNextEpisode != null || super.hasNextMediaItem()
            }

            override fun seekToNext() {
                val callback = currentOnNextEpisode
                if (callback != null) {
                    callback()
                } else {
                    super.seekToNext()
                }
            }

            override fun seekToNextMediaItem() {
                val callback = currentOnNextEpisode
                if (callback != null) {
                    callback()
                } else {
                    super.seekToNextMediaItem()
                }
            }
        }
    }

    val mediaSession = remember(exoPlayer) {
        MediaSession.Builder(context, forwardingPlayer)
            .setId("OpenFlixMediaSession_${System.currentTimeMillis()}")
            .build()
    }

    DisposableEffect(mediaSession) {
        onDispose {
            mediaSession.release()
        }
    }


    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(tracks: Tracks) {
                val aTracks = mutableListOf<MediaTrack>()
                val sTracks = mutableListOf<MediaTrack>()

                tracks.groups.forEach { group ->
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val name = format.language?.uppercase() ?: format.label ?: "Track ${i + 1}"
                        val track = MediaTrack(group, i, name, group.isTrackSelected(i))

                        if (group.type == C.TRACK_TYPE_AUDIO) aTracks.add(track)
                        if (group.type == C.TRACK_TYPE_TEXT) sTracks.add(track)
                    }
                }
                audioTracks = aTracks
                subtitleTracks = sTracks
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    isPlayerReady = true
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }


    LaunchedEffect(videoUri, artworkUri, externalSubtitleUri, autoDetectedSubtitleUri) {
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

        val mediaMetadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setDescription(overview)

        parseArtworkUri(artworkUri)?.let { uri ->
            mediaMetadataBuilder.setArtworkUri(uri)
        }

        val mediaMetadata = mediaMetadataBuilder.build()

        val mediaItemBuilder = androidx.media3.common.MediaItem.Builder()
            .setUri(Uri.parse(videoUri))
            .setMediaMetadata(mediaMetadata)

        val activeSubtitleUri: Any? = externalSubtitleUri ?: autoDetectedSubtitleUri

        activeSubtitleUri?.let { subUriStr ->
            val subUri = if (subUriStr is Uri) subUriStr else Uri.parse(subUriStr.toString())
            val isVtt = subUri.toString().lowercase().endsWith(".vtt")
            val mimeType = if (isVtt) androidx.media3.common.MimeTypes.TEXT_VTT else androidx.media3.common.MimeTypes.APPLICATION_SUBRIP

            val subtitleConfig = androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(subUri)
                .setMimeType(mimeType)
                .setLanguage("English")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.seekTo(currentTargetPosition.coerceAtLeast(0L))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun selectTrack(trackType: @C.TrackType Int, track: MediaTrack?) {
        val parametersBuilder = exoPlayer.trackSelectionParameters.buildUpon()
        if (track == null) {
            parametersBuilder.setTrackTypeDisabled(trackType, true)
        } else {
            parametersBuilder.setTrackTypeDisabled(trackType, false)
            parametersBuilder.setOverrideForType(
                TrackSelectionOverride(track.group.mediaTrackGroup, listOf(track.trackIndex))
            )
        }
        exoPlayer.trackSelectionParameters = parametersBuilder.build()
    }

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

    LaunchedEffect(isPlaying, isPlayerReady) {
        while (isActive && isPlaying && isPlayerReady) {
            delay(10000)
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration.coerceAtLeast(1L)
            onSaveProgress(pos, dur, pos >= (dur * 0.95))
        }
    }

    // Lifecycle Observer: Catches Exits & Saves
    DisposableEffect(lifecycleOwner, isPlayerReady) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (isPlayerReady) {
                    exoPlayer.pause()
                    val pos = exoPlayer.currentPosition
                    val dur = exoPlayer.duration.coerceAtLeast(1L)
                    onSaveProgress(pos, dur, pos >= (dur * 0.95))
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (isPlayerReady) {
                val pos = exoPlayer.currentPosition
                val dur = exoPlayer.duration.coerceAtLeast(1L)
                onSaveProgress(pos, dur, pos >= (dur * 0.95))
            }
        }
    }

    LaunchedEffect(isPlaying, isSeeking) {
        while (isActive && isPlaying && !isSeeking) {
            currentTimeMs = exoPlayer.currentPosition

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
        exoPlayer.setPlaybackSpeed(currentSpeed)
    }


    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                }
            },
            update = { view -> view.resizeMode = resizeMode },
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
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    } else if (zoom < 0.95f) {
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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
                                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                                                seekAnimationText = "<< -10s"
                                            } else {
                                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(durationMs))
                                                seekAnimationText = ">> +10s"
                                            }
                                            currentTimeMs = exoPlayer.currentPosition
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

        // Volume & Brightness Gesture Indicator Overlay
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
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(text = seekAnimationText, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Pause Overlay
        AnimatedVisibility(
            visible = showPauseOverlay && !isInPipMode,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).padding(40.dp),
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

        // Custom Controls
        AnimatedVisibility(
            visible = showControls && !showPauseOverlay && !isInPipMode,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Exit", tint = Color.White, modifier = Modifier.size(32.dp)) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                    modifier = Modifier.align(Alignment.Center).size(80.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(48.dp))
                }

                Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = formatPlayerTime(currentTimeMs), color = Color.White, fontSize = 14.sp)
                        Slider(
                            value = currentTimeMs.toFloat(),
                            onValueChange = { isSeeking = true; currentTimeMs = it.toLong() },
                            onValueChangeFinished = { isSeeking = false; exoPlayer.seekTo(currentTimeMs) },
                            valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFE50914), activeTrackColor = Color(0xFFE50914), inactiveTrackColor = Color.Gray)
                        )
                        Text(text = formatPlayerTime(durationMs - currentTimeMs), color = Color.LightGray, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        PlayerActionButton(Icons.Default.Speed, "${currentSpeed}x") { showSpeedDialog = true }

                        PlayerActionButton(Icons.Default.ClosedCaption, "Subtitles") {
                            showSubtitleDialog = true
                        }
                        if (audioTracks.isNotEmpty()) {
                            PlayerActionButton(Icons.Default.Audiotrack, "Audio") { showAudioDialog = true }
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

        // Up Next Prompt (Floating Pill)
        androidx.compose.animation.AnimatedVisibility(
            visible = showUpNextPrompt && !isInPipMode,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + fadeOut(),
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

        if (showAudioDialog && !isInPipMode) {
            TrackSelectionDialog(
                title = "Audio Tracks",
                options = audioTracks.map { it.name },
                selectedIndex = audioTracks.indexOfFirst { it.isSelected },
                onDismiss = { showAudioDialog = false },
                onSelect = { index ->
                    selectTrack(C.TRACK_TYPE_AUDIO, audioTracks[index])
                    showAudioDialog = false
                }
            )
        }

        if (showSubtitleDialog && !isInPipMode) {
            TrackSelectionDialog(
                title = "Subtitles",
                options = listOf("Off") + subtitleTracks.map { it.name },
                selectedIndex = if (subtitleTracks.none { it.isSelected }) 0 else subtitleTracks.indexOfFirst { it.isSelected } + 1,
                onDismiss = { showSubtitleDialog = false },
                onSelect = { index ->
                    if (index == 0) selectTrack(C.TRACK_TYPE_TEXT, null)
                    else selectTrack(C.TRACK_TYPE_TEXT, subtitleTracks[index - 1])
                    showSubtitleDialog = false
                },
                onLoadExternal = {
                    showSubtitleDialog = false

                    subtitlePicker.launch(arrayOf("application/x-subrip", "text/vtt", "application/octet-stream"))
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
            Column {
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
                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
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
fun PlayerActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
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

private fun parseArtworkUri(path: String?): Uri? {
    if (path.isNullOrBlank()) return null
    return try {
        when {
            path.startsWith("http://") || path.startsWith("https://") ||
            path.startsWith("content://") || path.startsWith("file://") -> Uri.parse(path)
            else -> Uri.fromFile(java.io.File(path))
        }
    } catch (e: Exception) {
        null
    }
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
                .padding(
                    horizontal = 14.dp,
                    vertical = 8.dp
                )
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
                        val remainingWidth =
                            size.width * (1f - progress.value)

                        drawRect(
                            color = Color(0xFFBDBDBD),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                x = size.width - remainingWidth,
                                y = 0f
                            ),
                            size = size.copy(
                                width = remainingWidth
                            )
                        )
                    }
            )

            Row(
                modifier = Modifier.padding(
                    horizontal = 14.dp,
                    vertical = 8.dp
                ),
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