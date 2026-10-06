package com.example.videoshield

import org.junit.Assert.*
import org.junit.Test

class DownloadPolicyTest {
    @Test fun normalizesLibraryVersionBeforeComparingEngineDates() {
        assertEquals("2025.11.12", DownloadPolicy.engineVersion("yt-dlp 2025.11.12"))
        assertTrue(DownloadPolicy.engineVersion("yt-dlp 2025.11.12") < "2026.08.19")
        assertEquals("2026.08.19", DownloadPolicy.engineVersion("2026.08.19"))
        assertEquals("", DownloadPolicy.engineVersion("unknown"))
    }
    @Test fun offersSourceMaximumWithoutCappingAndSkipsDrm() {
        val source=DownloadPolicy.source("""{"title":"Demo","formats":[{"height":4320,"vcodec":"av1"},{"height":1080,"vcodec":"h264"},{"height":720,"vcodec":"vp9"},{"height":9999,"vcodec":"vp9","has_drm":true},{"vcodec":"none"}]}""")
        assertEquals("Highest source (4320p)",source.qualities.first().label)
        assertEquals("bestvideo*+bestaudio/best[vcodec!=none]/bestvideo",source.qualities.first().selector)
        assertEquals(listOf("4320p","1080p","720p"),source.qualities.drop(1).map { it.label })
    }
    @Test fun expirationStartsAtCompletionAndNamesAreSafe() {
        assertFalse(DownloadPolicy.expired(0,Long.MAX_VALUE))
        assertFalse(DownloadPolicy.expired(1000,1000+DownloadPolicy.TEMPORARY_MS-1))
        assertTrue(DownloadPolicy.expired(1000,1000+DownloadPolicy.TEMPORARY_MS))
        assertEquals("a_b_c",DownloadPolicy.safeName("a/b:c"))
        assertEquals("VoTuibe",DownloadPolicy.safeName("..."))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsLiveSource() { DownloadPolicy.source("""{"is_live":true,"formats":[]}""") }
    @Test fun unknownHeightStillOffersRealVideoWithoutInventingResolution() {
        val source=DownloadPolicy.source("""{"formats":[{"vcodec":"h264","height":null},{"vcodec":"av1","height":1080}]}""")
        assertEquals(listOf("Highest source","1080p"),source.qualities.map { it.label })
    }
    @Test(expected=IllegalArgumentException::class) fun storyboardsAndAudioAreNotVideoSources() {
        DownloadPolicy.source("""{"formats":[{"height":180,"vcodec":"images","protocol":"mhtml"},{"vcodec":"none","acodec":"opus"},{"height":2160,"vcodec":"av1","has_drm":true}]}""")
    }
    @Test fun oldSavedSelectorsRetainTheirCeilingAndNewOnesAreIdempotent() {
        val selector=DownloadPolicy.videoSelector("bestvideo[height<=2160]+bestaudio/best[height<=2160]")
        assertEquals("bestvideo*[height<=2160]+bestaudio/best[height<=2160][vcodec!=none]/bestvideo[height<=2160]",selector)
        assertEquals(selector,DownloadPolicy.videoSelector(selector))
        assertEquals("bestaudio/best",DownloadPolicy.videoSelector("bestaudio/best"))
    }
}
