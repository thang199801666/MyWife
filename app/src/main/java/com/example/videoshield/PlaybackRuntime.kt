package com.example.videoshield

/**
 * Facade for playback state + commands. MainActivity talks to this runtime instead of
 * independently mutating the WebView backend, session store and foreground service.
 */
class PlaybackRuntime(
    private val session: PlaybackSessionCoordinator,
    sink: PlaybackCommandSink,
    onQueueNext: () -> Unit,
    onStop: (clearSnapshot: Boolean) -> Unit,
    private val beforeDispatch: (PlaybackCommand) -> Unit = {},
    private val afterDispatch: (PlaybackCommand) -> Unit = {}
) {
    private val dispatcher = PlaybackCommandDispatcher(
        sink = sink,
        onQueueNext = onQueueNext,
        onStop = onStop,
        onControlStateChanged = { command: PlaybackCommand ->
            when (command) {
                is PlaybackCommand.SetRate -> session.setPlaybackRate(command.rate)
                is PlaybackCommand.SetRepeat -> session.setRepeatEnabled(command.enabled)
                else -> Unit
            }
        }
    )

    val state: PlaybackSessionState get() = session.state

    fun dispatch(command: PlaybackCommand) {
        beforeDispatch(command)
        dispatcher.dispatch(command)
        afterDispatch(command)
    }

    fun syncControls(playbackRate: Float, repeatEnabled: Boolean) {
        session.syncControls(playbackRate, repeatEnabled)
    }

    fun onNavigationStarted(targetVideoId: String = "") = session.onNavigationStarted(targetVideoId)

    fun acceptBridgeUpdate(
        playing: Boolean,
        buffering: Boolean,
        title: String,
        channel: String,
        channelUrl: String,
        videoId: String,
        positionMs: Long,
        durationMs: Long,
        pageUrl: String
    ): PlaybackSessionDelta = session.acceptBridgeUpdate(
        playing, buffering, title, channel, channelUrl, videoId, positionMs, durationMs, pageUrl
    )

    fun overridePosition(positionMs: Long) = session.overridePosition(positionMs)
    fun estimatedPositionMs(at: Long = System.currentTimeMillis()): Long = session.estimatedPositionMs(at = at)
    fun publish(backgroundControls: Boolean) = session.publish(backgroundControls)
    fun stopService() = session.stopService()
    fun stop(clearSnapshot: Boolean = true) = session.stop(clearSnapshot)
    fun recoverableUrl(resumeEnabled: Boolean, maxAgeMs: Long, at: Long = System.currentTimeMillis()): String? =
        session.recoverableUrl(resumeEnabled, maxAgeMs, at)
}
