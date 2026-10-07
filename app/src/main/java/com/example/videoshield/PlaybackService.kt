package com.example.videoshield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class PlaybackService : Service() {
    private lateinit var mediaSession: MediaSession
    private val artworkExecutor = ThreadPoolExecutor(
        1, 1, 15L, TimeUnit.SECONDS, ArrayBlockingQueue(1),
        ThreadPoolExecutor.DiscardOldestPolicy()
    ).apply { allowCoreThreadTimeOut(true) }
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var buffering = false
    private var title = "Vợ Tui"
    private var channel = "YouTube"
    private var videoId = ""
    private var positionMs = 0L
    private var durationMs = 0L
    private var playbackRate = 1f
    private var artwork: Bitmap? = null
    private var artworkVideoId = ""
    private var lastPlayerUpdateElapsed = 0L
    private var foregroundStarted = false

    private val staleSessionCheck = object : Runnable {
        override fun run() {
            if (!isPlaying || lastPlayerUpdateElapsed <= 0L) return
            val remaining = WakeSchedulingPolicy.staleDeadlineDelayMs(
                lastPlayerUpdateElapsed, SystemClock.elapsedRealtime(), STALE_PLAYBACK_TIMEOUT_MS
            ) ?: return
            if (remaining <= 0L) {
                RuntimeDiagnosticsStore(this@PlaybackService).recordStaleServiceStop()
                sendCommand(CMD_STOP)
                stopSelf()
                return
            }
            // The callback can run a little early after scheduler jitter. Re-arm only for the
            // exact remaining deadline instead of returning to a fixed polling loop.
            handler.postDelayed(this, remaining)
        }
    }

    private fun markPlayerUpdate() {
        lastPlayerUpdateElapsed = SystemClock.elapsedRealtime()
    }

    private fun syncStaleCheck() {
        handler.removeCallbacks(staleSessionCheck)
        if (!isPlaying || lastPlayerUpdateElapsed <= 0L) return
        val delay = WakeSchedulingPolicy.staleDeadlineDelayMs(
            lastPlayerUpdateElapsed, SystemClock.elapsedRealtime(), STALE_PLAYBACK_TIMEOUT_MS
        ) ?: return
        handler.postDelayed(staleSessionCheck, delay.coerceAtLeast(1L))
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession = MediaSession(this, "YouTooBeePlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = sendCommand(CMD_PLAY)
                override fun onPause() = sendCommand(CMD_PAUSE)
                override fun onFastForward() = sendCommand(CMD_SEEK_FORWARD)
                override fun onRewind() = sendCommand(CMD_SEEK_BACK)
                override fun onSkipToNext() = sendCommand(CMD_QUEUE_NEXT)
                override fun onSeekTo(pos: Long) = sendCommand(CMD_SEEK_TO, pos.coerceAtLeast(0L))
                override fun onStop() {
                    sendCommand(CMD_STOP)
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var metadataChanged = !foregroundStarted
        var notificationChanged = !foregroundStarted

        when (intent?.action) {
            ACTION_UPDATE -> {
                markPlayerUpdate()

                val newPlaying = intent.getBooleanExtra(EXTRA_PLAYING, false)
                val newBuffering = newPlaying && intent.getBooleanExtra(EXTRA_BUFFERING, false)
                val newTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "YouTube" }
                val newChannel = intent.getStringExtra(EXTRA_CHANNEL).orEmpty().ifBlank { getString(R.string.app_name) }
                val newDuration = intent.getLongExtra(EXTRA_DURATION_MS, durationMs).coerceAtLeast(0L)
                val newPosition = PlaybackProgressPolicy.normalize(
                    intent.getLongExtra(EXTRA_POSITION_MS, positionMs),
                    newDuration
                )
                val newRate = intent.getFloatExtra(EXTRA_PLAYBACK_RATE, playbackRate).coerceIn(0.25f, 4f)
                val newVideoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty().take(64)

                metadataChanged = metadataChanged || newTitle != title || newChannel != channel ||
                    newDuration != durationMs || (newVideoId.isNotBlank() && newVideoId != videoId)
                notificationChanged = notificationChanged || metadataChanged || newPlaying != isPlaying ||
                    newBuffering != buffering || kotlin.math.abs(newRate - playbackRate) > 0.01f

                isPlaying = newPlaying
                buffering = newBuffering
                title = newTitle
                channel = newChannel
                durationMs = newDuration
                positionMs = newPosition
                playbackRate = newRate

                if (newVideoId.isNotBlank() && newVideoId != videoId) {
                    videoId = newVideoId
                    artwork = null
                    requestArtwork(newVideoId)
                }
            }
            ACTION_PLAY -> {
                markPlayerUpdate()
                if (!isPlaying) notificationChanged = true
                isPlaying = true
                buffering = false
                sendCommand(CMD_PLAY)
            }
            ACTION_PAUSE -> {
                if (isPlaying) notificationChanged = true
                isPlaying = false
                buffering = false
                sendCommand(CMD_PAUSE)
            }
            ACTION_BACK -> sendCommand(CMD_SEEK_BACK)
            ACTION_FORWARD -> sendCommand(CMD_SEEK_FORWARD)
            ACTION_NEXT -> sendCommand(CMD_QUEUE_NEXT)
            ACTION_STOP -> {
                sendCommand(CMD_STOP)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                isPlaying = !isPlaying
                buffering = false
                notificationChanged = true
                if (isPlaying) markPlayerUpdate()
                sendCommand(if (isPlaying) CMD_PLAY else CMD_PAUSE)
            }
        }

        syncStaleCheck()
        updateSession(metadataChanged)

        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, buildNotification())
            foregroundStarted = true
        } else if (notificationChanged) {
            notifyPlaybackChanged()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        RuntimeDiagnosticsStore(this).recordServiceTaskRemoved()
        // The actual HTML5 player lives in MainActivity. Once its task is removed,
        // keeping only a foreground notification would create a ghost media session.
        sendCommand(CMD_STOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val tier = MemoryPressurePolicy.fromTrimLevel(level)
        if (!tier.atLeast(MemoryPressureTier.LOW)) return

        // Notification artwork is entirely reproducible and should never compete with the
        // active media renderer. Drop queued artwork work and the retained bitmap first.
        artworkExecutor.queue.clear()
        artworkExecutor.purge()
        if (artwork != null) {
            artwork = null
            updateSession(metadataChanged = true)
            if (foregroundStarted) notifyPlaybackChanged()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        artworkExecutor.shutdownNow()
        foregroundStarted = false
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateSession(metadataChanged: Boolean = false) {
        val state = when {
            isPlaying && buffering -> PlaybackState.STATE_BUFFERING
            isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        if (metadataChanged) {
            mediaSession.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, channel)
                    .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, channel)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                    .apply {
                        artwork?.let {
                            putBitmap(MediaMetadata.METADATA_KEY_ART, it)
                            putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
                        }
                    }
                    .build()
            )
        }
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP
                )
                .setState(state, positionMs, if (isPlaying && !buffering) playbackRate else 0f)
                .build()
        )
    }

    private fun notifyPlaybackChanged() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val back = actionPendingIntent(ACTION_BACK, 2)
        val toggle = actionPendingIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY, 3)
        val forward = actionPendingIntent(ACTION_FORWARD, 4)
        val next = actionPendingIntent(ACTION_NEXT, 5)
        val stop = actionPendingIntent(ACTION_STOP, 6)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setLargeIcon(artwork)
            .setContentTitle(title)
            .setContentText(channel)
            .setSubText(formatProgress())
            .setContentIntent(openIntent)
            .setOngoing(isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(0, "−10", back).build())
            .addAction(Notification.Action.Builder(0, if (isPlaying) AppLanguage.wrap(this).getString(R.string.ui_pause) else AppLanguage.wrap(this).getString(R.string.ui_play), toggle).build())
            .addAction(Notification.Action.Builder(0, "+10", forward).build())
            .addAction(Notification.Action.Builder(0, AppLanguage.wrap(this).getString(R.string.ui_next), next).build())
            .addAction(Notification.Action.Builder(0, AppLanguage.wrap(this).getString(R.string.ui_stop), stop).build())
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun requestArtwork(id: String) {
        if (!VIDEO_ID.matches(id) || artworkVideoId == id) return
        artworkVideoId = id
        artworkExecutor.execute {
            val bitmap = runCatching {
                val connection = URL("https://i.ytimg.com/vi/$id/mqdefault.jpg").openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 4_000
                    connection.readTimeout = 4_000
                    connection.instanceFollowRedirects = true
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                    if (connection.contentLengthLong > 512L * 1024L) return@runCatching null
                    connection.inputStream.use { input ->
                        BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                            // Notification art is small; RGB_565 + sampling keeps the retained
                            // bitmap around ~30 KiB instead of allocating a full ARGB frame.
                            inSampleSize = 2
                            inPreferredConfig = Bitmap.Config.RGB_565
                            inDither = true
                        })
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
            if (bitmap != null) {
                // Service fields and MediaSession/notification publication are owned by the
                // main thread. A stale artwork download is simply dropped after a fast skip.
                handler.post {
                    if (videoId == id) {
                        artwork = bitmap
                        updateSession(metadataChanged = true)
                        if (foregroundStarted) notifyPlaybackChanged()
                    }
                }
            }
        }
    }

    private fun formatProgress(): String {
        if (durationMs <= 0L) return if (playbackRate != 1f) "${formatRate(playbackRate)}" else ""
        return "${formatTime(positionMs)} / ${formatTime(durationMs)} • ${formatRate(playbackRate)}"
    }

    private fun formatTime(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun formatRate(rate: Float): String = if (rate % 1f == 0f) "${rate.toInt()}×" else "${rate}×"

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode, Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun sendCommand(command: String, positionMs: Long = -1L) {
        sendBroadcast(Intent(ACTION_COMMAND).setPackage(packageName).apply {
            putExtra(EXTRA_COMMAND, command)
            if (positionMs >= 0L) putExtra(EXTRA_POSITION_MS, positionMs)
        })
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, AppLanguage.wrap(this).getString(R.string.playback_notification), NotificationManager.IMPORTANCE_LOW).apply {
                    description = AppLanguage.wrap(this@PlaybackService).getString(R.string.playback_controls)
                    setShowBadge(false)
                }
            )
        }
    }

    companion object {
        const val ACTION_UPDATE = "com.example.videoshield.playback.UPDATE"
        const val ACTION_PLAY = "com.example.videoshield.playback.PLAY"
        const val ACTION_PAUSE = "com.example.videoshield.playback.PAUSE"
        const val ACTION_BACK = "com.example.videoshield.playback.BACK"
        const val ACTION_FORWARD = "com.example.videoshield.playback.FORWARD"
        const val ACTION_NEXT = "com.example.videoshield.playback.NEXT"
        const val ACTION_STOP = "com.example.videoshield.playback.STOP"
        const val ACTION_TOGGLE = "com.example.videoshield.playback.TOGGLE"
        const val ACTION_COMMAND = "com.example.videoshield.playback.COMMAND"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_PLAYING = "playing"
        const val EXTRA_BUFFERING = "buffering"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_POSITION_MS = "position_ms"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_PLAYBACK_RATE = "playback_rate"
        const val CMD_PLAY = PlaybackCommandIds.PLAY
        const val CMD_PAUSE = PlaybackCommandIds.PAUSE
        const val CMD_SEEK_BACK = PlaybackCommandIds.SEEK_BACK
        const val CMD_SEEK_FORWARD = PlaybackCommandIds.SEEK_FORWARD
        const val CMD_SEEK_TO = PlaybackCommandIds.SEEK_TO
        const val CMD_QUEUE_NEXT = PlaybackCommandIds.QUEUE_NEXT
        const val CMD_STOP = PlaybackCommandIds.STOP
        const val CMD_PLAY_PAUSE = PlaybackCommandIds.PLAY_PAUSE
        private const val CHANNEL_ID = "videoshield_playback"
        private const val NOTIFICATION_ID = 201
        private const val STALE_PLAYBACK_TIMEOUT_MS = 75_000L
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{6,20}")
    }
}
