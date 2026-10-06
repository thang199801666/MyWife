package com.example.videoshield

/**
 * Playback capability boundary used by the client shell.
 * UI, gestures, queue, PiP and MediaSession commands depend on this contract rather than
 * knowing whether playback is currently hosted by WebView or a future native engine.
 */
interface PlaybackBackend {
    fun play()
    fun pause()
    fun toggle()
    fun seekBack()
    fun seekForward()
    fun seekToMs(positionMs: Long)
    fun setRepeatEnabled(enabled: Boolean)
    fun setPlaybackRate(rate: Float)
    fun setCommunitySegments(videoId: String, segments: List<CommunitySegment>)
    fun clearCommunitySegments()
}

class WebViewPlaybackBackend(
    private val controller: PlayerController
) : PlaybackBackend {
    override fun play() = controller.play()
    override fun pause() = controller.pause()
    override fun toggle() = controller.toggle()
    override fun seekBack() = controller.seekBack()
    override fun seekForward() = controller.seekForward()
    override fun seekToMs(positionMs: Long) = controller.seekToMs(positionMs)
    override fun setRepeatEnabled(enabled: Boolean) = controller.setRepeatEnabled(enabled)
    override fun setPlaybackRate(rate: Float) = controller.setPlaybackRate(rate)
    override fun setCommunitySegments(videoId: String, segments: List<CommunitySegment>) =
        controller.setCommunitySegments(videoId, segments)
    override fun clearCommunitySegments() = controller.clearCommunitySegments()
}
