package com.example.videoshield

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppReleaseTest {
    private fun manifest() = JSONObject().put("schemaVersion", 1)
        .put("packageName", "com.example.videoshield").put("versionCode", 41)
        .put("versionName", "0.1.0").put("minSdk", 26).put("abi", "arm64-v8a")
        .put("assetName", "VoTui-v0.1.0-release-arm64-v8a.apk")
        .put("size", 54344788).put("sha256", "a".repeat(64))

    private fun release(): JSONObject {
        val assets = JSONArray()
        for (name in listOf("update.json", "VoTui-v0.1.0-release-arm64-v8a.apk")) {
            assets.put(JSONObject().put("name", name).put("state", "uploaded").put("size", 54344788)
                .put("browser_download_url", "https://github.com/${AppRelease.REPOSITORY}/releases/download/v0.1.0/$name"))
        }
        return JSONObject().put("tag_name", "v0.1.0").put("draft", false).put("prerelease", false)
            .put("body", "New update").put("assets", assets)
    }

    private fun rejects(r: JSONObject = release(), m: JSONObject = manifest()) {
        try { AppRelease.parse(r, m, "com.example.videoshield"); fail("Unsafe release accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun stableReleaseMatchesItsPublishedAsset() {
        val parsed = AppRelease.parse(release(), manifest(), "com.example.videoshield")
        assertEquals(41L, parsed.versionCode)
        assertEquals("0.1.0", parsed.versionName)
        assertTrue(AppRelease.manifestUrl(release()).endsWith("/v0.1.0/update.json"))
    }

    @Test fun releaseBodyIsNotPartOfAppUpdateMetadata() {
        val noisy = release().put("body", "internal build diagnostics ".repeat(1000))
        val parsed = AppRelease.parse(noisy, manifest(), "com.example.videoshield")
        assertEquals("0.1.0", parsed.versionName)
        assertEquals(54344788L, parsed.size)
    }

    @Test fun refusesDraftsAndPrereleases() {
        rejects(release().put("draft", true))
        rejects(release().put("prerelease", true))
    }

    @Test fun refusesOtherAppsVersionsAndArchitectures() {
        rejects(m = manifest().put("packageName", "other.app"))
        rejects(m = manifest().put("versionName", "9.0.0"))
        rejects(m = manifest().put("abi", "x86_64"))
        rejects(m = manifest().put("schemaVersion", 2))
    }

    @Test fun refusesMissingDuplicateAndUnfinishedAssets() {
        rejects(release().put("assets", JSONArray()))
        val duplicate = release()
        duplicate.getJSONArray("assets").put(duplicate.getJSONArray("assets").getJSONObject(1))
        rejects(duplicate)
        val unfinished = release()
        unfinished.getJSONArray("assets").getJSONObject(1).put("state", "new")
        rejects(unfinished)
    }

    @Test fun refusesMismatchedSizeAndInvalidChecksums() {
        rejects(m = manifest().put("size", 1))
        rejects(m = manifest().put("size", AppRelease.MAX_APK_BYTES + 1))
        rejects(m = manifest().put("sha256", "not-a-checksum"))
        rejects(m = manifest().put("versionCode", 0))
    }

    @Test fun refusesApksFromAnotherRepository() {
        val wrong = release()
        wrong.getJSONArray("assets").getJSONObject(1).put("browser_download_url",
            "https://github.com/other/repo/releases/download/v0.1.0/VoTui-v0.1.0-release-arm64-v8a.apk")
        rejects(wrong)
    }

    @Test fun refusesUntrustedMetadataDownload() {
        val wrong = release()
        wrong.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://example.com/update.json")
        try { AppRelease.manifestUrl(wrong); fail("Unsafe metadata accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun downloadRedirectsStayOnHttpsGitHubHosts() {
        assertTrue(AppRelease.trustedDownloadHop("https://release-assets.githubusercontent.com/path?token=test"))
        assertTrue(AppRelease.trustedDownloadHop("https://objects.githubusercontent.com/path"))
        for (url in listOf("http://github.com/path", "https://github.com.evil.test/path",
            "https://evil.test/path", "https://user@github.com/path", "https://github.com:444/path",
            "https://github.com/path#fragment", "file:///tmp/a.apk")) {
            assertFalse(url, AppRelease.trustedDownloadHop(url))
        }
    }
}
