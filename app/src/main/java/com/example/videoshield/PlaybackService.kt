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
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class PlaybackService : Service() {
    private lateinit var mediaSession: MediaSession
    private val artworkExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var title = "Vợ Tui"
    private var channel = "YouTube"
    private var videoId = ""
    private var positionMs = 0L
    private var durationMs = 0L
    private var playbackRate = 1f
    private var artwork: Bitmap? = null
    private var artworkVideoId = ""
    private var lastPlayerUpdateAt = 0L

    private val staleSessionCheck = object : Runnable {
        override fun run() {
            if (isPlaying && lastPlayerUpdateAt > 0L &&
                System.currentTimeMillis() - lastPlayerUpdateAt >= STALE_PLAYBACK_TIMEOUT_MS
            ) {
                RuntimeDiagnosticsStore(this@PlaybackService).recordStaleServiceStop()
                sendCommand(CMD_STOP)
                stopSelf()
                return
            }
            handler.postDelayed(this, STALE_CHECK_INTERVAL_MS)
        }
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
        handler.postDelayed(staleSessionCheck, STALE_CHECK_INTERVAL_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE -> {
                lastPlayerUpdateAt = System.currentTimeMillis()
                isPlaying = intent.getBooleanExtra(EXTRA_PLAYING, false)
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "YouTube" }
                channel = intent.getStringExtra(EXTRA_CHANNEL).orEmpty().ifBlank { getString(R.string.app_name) }
                durationMs = intent.getLongExtra(EXTRA_DURATION_MS, durationMs).coerceAtLeast(0L)
                positionMs = PlaybackProgressPolicy.normalize(
                    intent.getLongExtra(EXTRA_POSITION_MS, positionMs),
                    durationMs
                )
                playbackRate = intent.getFloatExtra(EXTRA_PLAYBACK_RATE, playbackRate).coerceIn(0.25f, 4f)
                val newVideoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty().take(64)
                if (newVideoId.isNotBlank() && newVideoId != videoId) {
                    videoId = newVideoId
                    artwork = null
                    requestArtwork(newVideoId)
                }
            }
            ACTION_PLAY -> { lastPlayerUpdateAt = System.currentTimeMillis(); isPlaying = true; sendCommand(CMD_PLAY) }
            ACTION_PAUSE -> { isPlaying = false; sendCommand(CMD_PAUSE) }
            ACTION_BACK -> sendCommand(CMD_SEEK_BACK)
            ACTION_FORWARD -> sendCommand(CMD_SEEK_FORWARD)
            ACTION_NEXT -> sendCommand(CMD_QUEUE_NEXT)
            ACTION_STOP -> { sendCommand(CMD_STOP); stopSelf(); return START_NOT_STICKY }
            ACTION_TOGGLE -> {
                isPlaying = !isPlaying
                if (isPlaying) lastPlayerUpdateAt = System.currentTimeMillis()
                sendCommand(if (isPlaying) CMD_PLAY else CMD_PAUSE)
            }
        }
        updateSession()
        startForeground(NOTIFICATION_ID, buildNotification())
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

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        artworkExecutor.shutdownNow()
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateSession() {
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
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
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP
                )
                .setState(state, positionMs, if (isPlaying) playbackRate else 0f)
                .build()
        )
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
        if (!id.matches(Regex("[A-Za-z0-9_-]{6,20}")) || artworkVideoId == id) return
        artworkVideoId = id
        artworkExecutor.execute {
            val bitmap = try {
                val connection = URL("https://i.ytimg.com/vi/$id/hqdefault.jpg").openConnection() as HttpURLConnection
                connection.connectTimeout = 4_000
                connection.readTimeout = 4_000
                connection.instanceFollowRedirects = true
                connection.inputStream.use { BitmapFactory.decodeStream(it) }.also { connection.disconnect() }
            } catch (_: Exception) { null }
            if (bitmap != null && videoId == id) {
                artwork = bitmap
                updateSession()
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildNotification())
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
        const val EXTRA_TITLE = "title"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_POSITION_MS = "position_ms"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_PLAYBACK_RATE = "playback_rate"
        const val CMD_PLAY = "play"
        const val CMD_PAUSE = "pause"
        const val CMD_SEEK_BACK = "seekBack"
        const val CMD_SEEK_FORWARD = "seekForward"
        const val CMD_SEEK_TO = "seekTo"
        const val CMD_QUEUE_NEXT = "queueNext"
        const val CMD_STOP = "stop"
        const val CMD_PLAY_PAUSE = "playPause"
        private const val CHANNEL_ID = "videoshield_playback"
        private const val NOTIFICATION_ID = 201
        private const val STALE_CHECK_INTERVAL_MS = 30_000L
        private const val STALE_PLAYBACK_TIMEOUT_MS = 75_000L
    }
}
