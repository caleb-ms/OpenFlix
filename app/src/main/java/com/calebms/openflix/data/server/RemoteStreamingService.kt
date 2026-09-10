package com.calebms.openflix.data.server

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.calebms.openflix.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RemoteStreamingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var currentTitle: String = "OpenFlix Streaming"
    private var isPlaying: Boolean = true
    private var currentPosterPath: String? = null
    private var currentBitmap: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenFlix::StreamWakeLock").apply {
            acquire(8 * 60 * 60 * 1000L)
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "OpenFlix::StreamWifiLock").apply {
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val server = LocalMediaServer.getInstance(applicationContext)

        intent?.getStringExtra(EXTRA_TITLE)?.let { currentTitle = it }
        if (intent?.hasExtra(EXTRA_IS_PLAYING) == true) {
            isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
        }

        val newPosterPath = intent?.getStringExtra(EXTRA_POSTER_PATH)
        if (!newPosterPath.isNullOrBlank() && newPosterPath != currentPosterPath) {
            currentPosterPath = newPosterPath
            currentBitmap = null
            loadPosterBitmap(newPosterPath)
        }

        when (intent?.action) {
            ACTION_STOP -> {
                CoroutineScope(Dispatchers.IO).launch {
                    server.sendCommand(RemoteMessage(action = CommandAction.DISCONNECT))
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY_PAUSE -> {
                val nextAction = if (isPlaying) CommandAction.PAUSE else CommandAction.PLAY
                isPlaying = !isPlaying
                CoroutineScope(Dispatchers.IO).launch {
                    server.sendCommand(RemoteMessage(action = nextAction))
                }
            }
            ACTION_REWIND -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val currentPos = server.lastReportedPositionMs
                    val newPos = (currentPos - 10000L).coerceAtLeast(0L)
                    server.sendCommand(RemoteMessage(action = CommandAction.SEEK, positionMs = newPos))
                }
            }
            ACTION_FORWARD -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val currentPos = server.lastReportedPositionMs
                    val newPos = currentPos + 10000L
                    server.sendCommand(RemoteMessage(action = CommandAction.SEEK, positionMs = newPos))
                }
            }
        }

        val notification = buildNotification(currentTitle, isPlaying)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun loadPosterBitmap(path: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val bitmap = try {
                if (path.startsWith("http://") || path.startsWith("https://")) {
                    val loader = coil.ImageLoader(applicationContext)
                    val request = coil.request.ImageRequest.Builder(applicationContext)
                        .data(path)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    if (result is coil.request.SuccessResult) {
                        (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    } else null
                } else {
                    BitmapFactory.decodeFile(path)
                }
            } catch (e: Exception) {
                null
            }

            if (bitmap != null) {
                currentBitmap = bitmap
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildNotification(currentTitle, isPlaying))
            }
        }
    }

    private fun buildNotification(title: String, playing: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, RemoteStreamingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val playPauseIntent = PendingIntent.getService(
            this, 2, Intent(this, RemoteStreamingService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val rewindIntent = PendingIntent.getService(
            this, 3, Intent(this, RemoteStreamingService::class.java).apply { action = ACTION_REWIND },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val forwardIntent = PendingIntent.getService(
            this, 4, Intent(this, RemoteStreamingService::class.java).apply { action = ACTION_FORWARD },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playbackState = if (playing) "Playing on your PC" else "Paused on your PC"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(playbackState)
            .setSubText("Desktop streaming")
            .setSmallIcon(R.drawable.ic_stat_openflix)
            .setContentIntent(openAppIntent)
            .setColor(ContextCompat.getColor(this, R.color.openflix_notification_accent))
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_media_rew, "Rewind 10s", rewindIntent)
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                playPauseIntent
            )
            .addAction(android.R.drawable.ic_media_ff, "Forward 10s", forwardIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stopIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )

        currentBitmap?.let {
            builder.setLargeIcon(it)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenFlix PC Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls media playback streaming to PC companion"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 8081
        const val CHANNEL_ID = "openflix_pc_streaming"
        const val ACTION_STOP = "com.calebms.openflix.ACTION_STOP"
        const val ACTION_PLAY_PAUSE = "com.calebms.openflix.ACTION_PLAY_PAUSE"
        const val ACTION_REWIND = "com.calebms.openflix.ACTION_REWIND"
        const val ACTION_FORWARD = "com.calebms.openflix.ACTION_FORWARD"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_IS_PLAYING = "EXTRA_IS_PLAYING"
        const val EXTRA_POSTER_PATH = "EXTRA_POSTER_PATH"
    }
}
