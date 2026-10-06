package com.example.videoshield

data class RuntimeSelfTestResult(val passed: Boolean, val summary: String)

object RuntimeSelfTest {
    fun run(library: LibraryStore): RuntimeSelfTestResult {
        val checks = linkedMapOf<String, Boolean>()

        var latest = PlaybackHealthSnapshot(PlaybackHealthState.IDLE)
        val machine = PlaybackHealthStateMachine { latest = it }
        machine.setOnline(true, false)
        machine.navigationStarted()
        checks["health navigating"] = latest.state == PlaybackHealthState.NAVIGATING
        machine.pageReady(false)
        checks["non-watch settles"] = latest.state == PlaybackHealthState.IDLE
        machine.navigationStarted()
        machine.heartbeat(true, "abcdefghijk")
        checks["heartbeat healthy"] = latest.state == PlaybackHealthState.HEALTHY
        machine.setOnline(false, true)
        checks["offline transition"] = latest.state == PlaybackHealthState.OFFLINE
        machine.setOnline(true, true)
        checks["online session stalls"] = latest.state == PlaybackHealthState.STALLED
        machine.recoveryStarted("fixture", 1)
        checks["recovery transition"] = latest.state == PlaybackHealthState.RECOVERING
        machine.recoveryExhausted("fixture exhausted")
        checks["bounded failure"] = latest.state == PlaybackHealthState.FAILED
        machine.userRetry()
        checks["manual retry"] = latest.state == PlaybackHealthState.NAVIGATING

        val snapshot = PlaybackSnapshot(
            playing = true,
            title = "Fixture",
            channel = "Fixture",
            url = "https://m.youtube.com/watch?v=abcdefghijk",
            videoId = "abcdefghijk",
            positionMs = 10_000L,
            durationMs = 12_000L,
            updatedAt = System.currentTimeMillis() - 10_000L
        )
        checks["snapshot clamps duration"] = snapshot.predictedPositionMs() == 12_000L

        val db = library.quickIntegrityCheck()
        checks["library quick_check"] = db.healthy
        checks["queue count nonnegative"] = db.queueRows >= 0

        val failed = checks.filterValues { !it }.keys
        val summary = if (failed.isEmpty()) {
            "${checks.size}/${checks.size} runtime checks passed • ${db.summary}"
        } else {
            "${checks.size - failed.size}/${checks.size} passed • failed: ${failed.joinToString(", ")} • ${db.summary}"
        }
        return RuntimeSelfTestResult(failed.isEmpty(), summary)
    }
}
