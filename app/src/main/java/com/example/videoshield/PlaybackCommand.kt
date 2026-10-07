package com.example.videoshield

/** Canonical commands shared by UI, PiP and PlaybackService. */
sealed interface PlaybackCommand {
    data object Play : PlaybackCommand
    data object Pause : PlaybackCommand
    data object Toggle : PlaybackCommand
    data object SeekBack : PlaybackCommand
    data object SeekForward : PlaybackCommand
    data class SeekTo(val positionMs: Long) : PlaybackCommand
    data object QueueNext : PlaybackCommand
    data class SetRepeat(val enabled: Boolean) : PlaybackCommand
    data class SetRate(val rate: Float) : PlaybackCommand
    data class Stop(val clearSnapshot: Boolean = true) : PlaybackCommand
}

/** String ids are kept in one pure-Kotlin contract instead of duplicated by service/UI code. */
object PlaybackCommandIds {
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val SEEK_BACK = "seekBack"
    const val SEEK_FORWARD = "seekForward"
    const val SEEK_TO = "seekTo"
    const val QUEUE_NEXT = "queueNext"
    const val STOP = "stop"
    const val PLAY_PAUSE = "playPause"
}

object PlaybackServiceCommandCodec {
    fun decode(id: String?, positionMs: Long? = null): PlaybackCommand? = when (id) {
        PlaybackCommandIds.PLAY -> PlaybackCommand.Play
        PlaybackCommandIds.PAUSE -> PlaybackCommand.Pause
        PlaybackCommandIds.PLAY_PAUSE -> PlaybackCommand.Toggle
        PlaybackCommandIds.SEEK_BACK -> PlaybackCommand.SeekBack
        PlaybackCommandIds.SEEK_FORWARD -> PlaybackCommand.SeekForward
        PlaybackCommandIds.SEEK_TO -> positionMs?.takeIf { it >= 0L }?.let(PlaybackCommand::SeekTo)
        PlaybackCommandIds.QUEUE_NEXT -> PlaybackCommand.QueueNext
        PlaybackCommandIds.STOP -> PlaybackCommand.Stop()
        else -> null
    }
}

/** Small pure-Kotlin boundary that is easy to fake in command-routing tests. */
interface PlaybackCommandSink {
    fun play()
    fun pause()
    fun toggle()
    fun seekBack()
    fun seekForward()
    fun seekToMs(positionMs: Long)
    fun setRepeatEnabled(enabled: Boolean)
    fun setPlaybackRate(rate: Float)
}

class PlaybackCommandDispatcher(
    private val sink: PlaybackCommandSink,
    private val onQueueNext: () -> Unit,
    private val onStop: (clearSnapshot: Boolean) -> Unit,
    private val onControlStateChanged: (PlaybackCommand) -> Unit = {}
) {
    fun dispatch(command: PlaybackCommand) {
        when (command) {
            PlaybackCommand.Play -> sink.play()
            PlaybackCommand.Pause -> sink.pause()
            PlaybackCommand.Toggle -> sink.toggle()
            PlaybackCommand.SeekBack -> sink.seekBack()
            PlaybackCommand.SeekForward -> sink.seekForward()
            is PlaybackCommand.SeekTo -> sink.seekToMs(command.positionMs.coerceAtLeast(0L))
            PlaybackCommand.QueueNext -> onQueueNext()
            is PlaybackCommand.SetRepeat -> {
                sink.setRepeatEnabled(command.enabled)
                onControlStateChanged(command)
            }
            is PlaybackCommand.SetRate -> {
                sink.setPlaybackRate(command.rate.coerceIn(0.25f, 4f))
                onControlStateChanged(command)
            }
            is PlaybackCommand.Stop -> {
                sink.pause()
                onStop(command.clearSnapshot)
            }
        }
    }
}
