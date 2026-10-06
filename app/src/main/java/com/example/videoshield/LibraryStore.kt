package com.example.videoshield

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class VideoItem(
    val videoId: String,
    val title: String,
    val channel: String,
    val url: String,
    val lastPlayedAt: Long,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

data class ChannelItem(
    val channelKey: String,
    val name: String,
    val url: String,
    val addedAt: Long
)

data class PlaybackLibraryState(
    val history: VideoItem?, val favorite: Boolean, val queued: Boolean,
    val subscribed: Boolean, val queueCount: Int
)


data class LibraryIntegrityResult(
    val healthy: Boolean,
    val quickCheck: String,
    val queueRows: Int,
    val invalidQueueRows: Int,
    val invalidHistoryRows: Int
) {
    val summary: String
        get() = "quick_check=$quickCheck • queue=$queueRows • invalid queue=$invalidQueueRows • invalid history=$invalidHistoryRows"
}

class LibraryStore(context: Context) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {
    fun playbackState(videoId: String, channel: String, includeHistory: Boolean): PlaybackLibraryState =
        PlaybackLibraryState(
            if (includeHistory && videoId.isNotBlank()) historyItem(videoId) else null,
            videoId.isNotBlank() && isFavorite(videoId),
            videoId.isNotBlank() && isQueued(videoId),
            channel.isNotBlank() && isSubscribed(channel), queueCount()
        )
    init { setWriteAheadLoggingEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        createHistory(db)
        createFavorites(db)
        createSubscriptions(db)
        createQueue(db)
        createPersonalization(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createQueue(db)
        if (oldVersion < 3) createPersonalization(db)
        if (oldVersion < 4) createRecommendationControls(db)
    }

    fun recordWatched(videoId: String, watchedMs: Long, now: Long) {
        if (videoId.isBlank() || watchedMs <= 0) return
        val db = writableDatabase
        db.execSQL("INSERT OR IGNORE INTO viewing_interest(video_id,watched_ms,last_seen) VALUES(?,0,?)", arrayOf<Any>(videoId, now))
        db.execSQL("UPDATE viewing_interest SET watched_ms=MIN(watched_ms+?,7200000),last_seen=? WHERE video_id=?", arrayOf<Any>(watchedMs.coerceIn(0, 15_000), now, videoId))
        db.execSQL("DELETE FROM viewing_interest WHERE video_id NOT IN (SELECT video_id FROM viewing_interest ORDER BY last_seen DESC LIMIT 1000)")
    }

    fun saveCandidates(items: List<VideoItem>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            items.take(50).forEach { item ->
                if (!Regex("[A-Za-z0-9_-]{11}").matches(item.videoId) || item.title.isBlank()) return@forEach
                db.insertWithOnConflict("discovered_videos", null, ContentValues().apply {
                    put("video_id", item.videoId); put("title", item.title.take(240)); put("channel", item.channel.take(180))
                    put("url", "https://m.youtube.com/watch?v=${item.videoId}"); put("last_played_at", item.lastPlayedAt)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.execSQL("DELETE FROM discovered_videos WHERE video_id NOT IN (SELECT video_id FROM discovered_videos ORDER BY last_played_at DESC LIMIT 1000)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun recommendations(since: Long, now: Long = System.currentTimeMillis(), focus: VideoItem? = null): List<SuggestedVideo> {
        val watched = mutableMapOf<String, Long>()
        readableDatabase.rawQuery("SELECT video_id,watched_ms FROM viewing_interest", null).use { c -> while (c.moveToNext()) watched[c.getString(0)] = c.getLong(1) }
        val history = history(1000)
        val favorites = favorites(1000).associateBy { it.videoId }
        val evidence = (history.filter { it.lastPlayedAt >= since } + favorites.values).distinctBy { it.videoId }.map {
            InterestEvidence(it, watched[it.videoId] ?: 0, it.videoId in favorites)
        }
        val dismissed = mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT video_id FROM dismissed_suggestions", null).use { c -> while (c.moveToNext()) dismissed += c.getString(0) }
        val candidates = queryVideos("SELECT video_id,title,channel,url,last_played_at,0,0 FROM discovered_videos ORDER BY last_played_at DESC LIMIT 1000", emptyArray())
        return RecommendationEngine.rank(candidates, evidence, subscriptions().map { it.name }.toSet(), dismissed, history.map { it.videoId }.toSet(), now,
            blockedChannels = blockedChannels(), focus = focus)
    }

    fun dismissSuggestion(videoId: String) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO dismissed_suggestions(video_id) VALUES(?)", arrayOf(videoId))
        writableDatabase.execSQL("DELETE FROM dismissed_suggestions WHERE rowid NOT IN (SELECT rowid FROM dismissed_suggestions ORDER BY rowid DESC LIMIT 2000)")
    }

    fun restoreSuggestions() = writableDatabase.delete("dismissed_suggestions", null, null)

    fun blockedChannels(): Set<String> {
        val channels = linkedSetOf<String>()
        readableDatabase.rawQuery("SELECT channel_key FROM blocked_channels ORDER BY channel_key", null).use { c ->
            while (c.moveToNext()) channels += c.getString(0)
        }
        return channels
    }

    fun blockChannel(channel: String) {
        val key = LibraryPolicy.channelKey(channel)
        if (key.isBlank()) return
        writableDatabase.execSQL("INSERT OR IGNORE INTO blocked_channels(channel_key) VALUES(?)", arrayOf(key))
        writableDatabase.execSQL("DELETE FROM blocked_channels WHERE rowid NOT IN (SELECT rowid FROM blocked_channels ORDER BY rowid DESC LIMIT 500)")
    }

    fun unblockChannel(channel: String) = writableDatabase.delete("blocked_channels", "channel_key=?", arrayOf(LibraryPolicy.channelKey(channel)))

    fun favoriteIds(): Set<String> {
        val ids = mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT video_id FROM favorites", null).use { c -> while (c.moveToNext()) ids += c.getString(0) }
        return ids
    }

    fun clearPersonalization() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            listOf("viewing_interest", "discovered_videos", "dismissed_suggestions", "blocked_channels").forEach { db.delete(it, null, null) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun recordHistory(item: VideoItem, maxItems: Int = 300) {
        if (item.videoId.isBlank() || item.url.isBlank()) return
        val saved = if (LibraryPolicy.placeholderTitle(item.title) || item.channel.isBlank()) historyItem(item.videoId) else null
        val metadata = LibraryPolicy.preserveMetadata(item, saved)
        writableDatabase.insertWithOnConflict("history", null, ContentValues().apply {
            put("video_id", item.videoId.take(64))
            put("title", metadata.title.ifBlank { "YouTube video" }.take(240))
            put("channel", metadata.channel.take(180))
            put("url", item.url.take(1000))
            put("last_played_at", item.lastPlayedAt)
            put("position_ms", item.positionMs.coerceAtLeast(0L))
            put("duration_ms", item.durationMs.coerceAtLeast(0L))
        }, SQLiteDatabase.CONFLICT_REPLACE)
        writableDatabase.execSQL(
            "DELETE FROM history WHERE video_id NOT IN (SELECT video_id FROM history ORDER BY last_played_at DESC LIMIT ?)",
            arrayOf(maxItems.coerceIn(25, 2000))
        )
    }

    fun history(limit: Int = 200): List<VideoItem> = queryVideos(
        "SELECT video_id,title,channel,url,last_played_at,position_ms,duration_ms FROM history ORDER BY last_played_at DESC LIMIT ?",
        arrayOf(limit.coerceIn(1, 1000).toString())
    )

    fun historyItem(videoId: String): VideoItem? {
        if (videoId.isBlank()) return null
        return queryVideos(
            "SELECT video_id,title,channel,url,last_played_at,position_ms,duration_ms FROM history WHERE video_id=? LIMIT 1",
            arrayOf(videoId)
        ).firstOrNull()
    }

    fun clearHistory() = writableDatabase.delete("history", null, null)
    fun removeHistory(videoId: String) = writableDatabase.delete("history", "video_id=?", arrayOf(videoId))

    fun isFavorite(videoId: String): Boolean = exists("favorites", "video_id", videoId)

    fun toggleFavorite(item: VideoItem): Boolean {
        if (item.videoId.isBlank()) return false
        if (isFavorite(item.videoId)) {
            writableDatabase.delete("favorites", "video_id=?", arrayOf(item.videoId))
            return false
        }
        writableDatabase.insertWithOnConflict("favorites", null, ContentValues().apply {
            put("video_id", item.videoId.take(64))
            put("title", item.title.ifBlank { "YouTube video" }.take(240))
            put("channel", item.channel.take(180))
            put("url", item.url.take(1000))
            put("added_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        return true
    }

    fun favorites(limit: Int = 500): List<VideoItem> = queryVideos(
        "SELECT video_id,title,channel,url,added_at,0,0 FROM favorites ORDER BY added_at DESC LIMIT ?",
        arrayOf(limit.coerceIn(1, 1000).toString())
    )

    fun clearFavorites() = writableDatabase.delete("favorites", null, null)

    fun isSubscribed(channel: String): Boolean = exists("subscriptions", "channel_key", channelKey(channel))

    fun toggleSubscription(name: String, url: String): Boolean {
        val key = channelKey(name)
        if (key.isBlank()) return false
        if (isSubscribed(name)) {
            writableDatabase.delete("subscriptions", "channel_key=?", arrayOf(key))
            return false
        }
        writableDatabase.insertWithOnConflict("subscriptions", null, ContentValues().apply {
            put("channel_key", key)
            put("name", name.trim().take(180))
            put("url", url.trim().take(1000))
            put("added_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        return true
    }

    fun subscriptions(limit: Int = 500): List<ChannelItem> {
        val out = mutableListOf<ChannelItem>()
        readableDatabase.rawQuery(
            "SELECT channel_key,name,url,added_at FROM subscriptions ORDER BY name COLLATE NOCASE ASC LIMIT ?",
            arrayOf(limit.coerceIn(1, 1000).toString())
        ).use { c ->
            while (c.moveToNext()) out += ChannelItem(c.getString(0), c.getString(1), c.getString(2), c.getLong(3))
        }
        return out
    }

    fun clearSubscriptions() = writableDatabase.delete("subscriptions", null, null)

    fun isQueued(videoId: String): Boolean = exists("play_queue", "video_id", videoId)

    /** Adds a video once at the end of the queue. */
    fun enqueue(item: VideoItem): Boolean {
        if (item.videoId.isBlank() || item.url.isBlank()) return false
        if (isQueued(item.videoId)) return false
        val result = writableDatabase.insert("play_queue", null, queueValues(item, System.currentTimeMillis()))
        return result != -1L
    }

    /** Atomically prioritizes a selected collection, preserving other pending items. */
    fun prepareCollection(items: List<VideoItem>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            rewriteQueue(db, LibraryPolicy.collectionQueue(items, queueAllOn(db)))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Places a video at the front. Existing occurrences are moved, not duplicated. */
    fun enqueueNext(item: VideoItem): Boolean {
        if (item.videoId.isBlank() || item.url.isBlank()) return false
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val current = queueAllOn(db).filterNot { it.videoId == item.videoId }
            rewriteQueue(db, listOf(item.copy(lastPlayedAt = System.currentTimeMillis())) + current)
            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
        }
    }

    /** Moves an item one slot. Negative delta moves toward the front. */
    fun moveQueue(videoId: String, delta: Int): Boolean {
        if (videoId.isBlank() || delta == 0) return false
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val items = queueAllOn(db).toMutableList()
            val from = items.indexOfFirst { it.videoId == videoId }
            if (from < 0) return false
            val to = (from + delta).coerceIn(0, items.lastIndex)
            if (from == to) return false
            val item = items.removeAt(from)
            items.add(to, item)
            rewriteQueue(db, items)
            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
        }
    }

    /** Moves a queue item to an absolute adapter position; used by drag reorder. */
    fun moveQueueTo(videoId: String, targetPosition: Int): Boolean {
        if (videoId.isBlank()) return false
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val items = queueAllOn(db).toMutableList()
            val from = items.indexOfFirst { it.videoId == videoId }
            if (from < 0 || items.isEmpty()) return false
            val to = targetPosition.coerceIn(0, items.lastIndex)
            if (from == to) return false
            val item = items.removeAt(from)
            items.add(to, item)
            rewriteQueue(db, items)
            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
        }
    }

    fun removeFromQueue(videoId: String): Boolean {
        if (videoId.isBlank()) return false
        return writableDatabase.delete("play_queue", "video_id=?", arrayOf(videoId)) > 0
    }

    fun queue(limit: Int = 500): List<VideoItem> = queueOn(readableDatabase, limit)

    fun queueCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM play_queue", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun popNext(): VideoItem? {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val next = queryVideosOn(
                db,
                "SELECT video_id,title,channel,url,added_at,0,0 FROM play_queue ORDER BY queue_id ASC LIMIT 1",
                emptyArray<String>()
            ).firstOrNull()
            if (next != null) db.delete("play_queue", "video_id=?", arrayOf(next.videoId))
            db.setTransactionSuccessful()
            next
        } finally {
            db.endTransaction()
        }
    }

    fun clearQueue() = writableDatabase.delete("play_queue", null, null)

    /**
     * Validates queue rows after an interrupted app/process lifecycle. SQLite transactions
     * already protect reorder writes, so the common healthy path performs no rewrite; only
     * invalid legacy rows cause a transactional cleanup.
     */
    fun repairQueue(): Int {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val original = queueAllOn(db)
            val seen = HashSet<String>()
            val cleaned = original.filter { item ->
                item.videoId.isNotBlank() && item.url.isNotBlank() && seen.add(item.videoId)
            }
            val removed = original.size - cleaned.size
            if (removed > 0) rewriteQueue(db, cleaned)
            db.setTransactionSuccessful()
            removed
        } finally {
            db.endTransaction()
        }
    }

    private fun queueOn(db: SQLiteDatabase, limit: Int): List<VideoItem> = queryVideosOn(
        db,
        "SELECT video_id,title,channel,url,added_at,0,0 FROM play_queue ORDER BY queue_id ASC LIMIT ?",
        arrayOf(limit.coerceIn(1, 1000).toString())
    )

    private fun queueAllOn(db: SQLiteDatabase): List<VideoItem> = queryVideosOn(
        db,
        "SELECT video_id,title,channel,url,added_at,0,0 FROM play_queue ORDER BY queue_id ASC",
        emptyArray()
    )

    private fun rewriteQueue(db: SQLiteDatabase, items: List<VideoItem>) {
        db.delete("play_queue", null, null)
        items.forEachIndexed { index, item ->
            val values = queueValues(item, item.lastPlayedAt.takeIf { it > 0L } ?: System.currentTimeMillis())
            values.put("queue_id", index + 1L)
            db.insertOrThrow("play_queue", null, values)
        }
    }

    private fun queueValues(item: VideoItem, addedAt: Long): ContentValues = ContentValues().apply {
        put("video_id", item.videoId.take(64))
        put("title", item.title.ifBlank { "YouTube video" }.take(240))
        put("channel", item.channel.take(180))
        put("url", item.url.take(1000))
        put("added_at", addedAt)
    }

    private fun queryVideos(sql: String, args: Array<String>): List<VideoItem> = queryVideosOn(readableDatabase, sql, args)

    private fun queryVideosOn(db: SQLiteDatabase, sql: String, args: Array<String>): List<VideoItem> {
        val out = mutableListOf<VideoItem>()
        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) out += VideoItem(
                videoId = c.getString(0), title = c.getString(1), channel = c.getString(2), url = c.getString(3),
                lastPlayedAt = c.getLong(4), positionMs = c.getLong(5), durationMs = c.getLong(6)
            )
        }
        return out
    }

    private fun exists(table: String, column: String, value: String): Boolean {
        if (value.isBlank()) return false
        readableDatabase.query(table, arrayOf(column), "$column=?", arrayOf(value), null, null, null, "1").use {
            return it.moveToFirst()
        }
    }

    private fun createHistory(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS history (
                video_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                channel TEXT NOT NULL,
                url TEXT NOT NULL,
                last_played_at INTEGER NOT NULL,
                position_ms INTEGER NOT NULL DEFAULT 0,
                duration_ms INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }

    private fun createPersonalization(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS viewing_interest(video_id TEXT PRIMARY KEY, watched_ms INTEGER NOT NULL DEFAULT 0, last_seen INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS discovered_videos(video_id TEXT PRIMARY KEY,title TEXT NOT NULL,channel TEXT NOT NULL,url TEXT NOT NULL,last_played_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS dismissed_suggestions(video_id TEXT PRIMARY KEY)")
        createRecommendationControls(db)
    }

    private fun createRecommendationControls(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS blocked_channels(channel_key TEXT PRIMARY KEY)")
    }

    private fun createFavorites(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS favorites (
                video_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                channel TEXT NOT NULL,
                url TEXT NOT NULL,
                added_at INTEGER NOT NULL
            )
        """.trimIndent())
    }

    private fun createSubscriptions(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS subscriptions (
                channel_key TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                url TEXT NOT NULL,
                added_at INTEGER NOT NULL
            )
        """.trimIndent())
    }

    private fun createQueue(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS play_queue (
                queue_id INTEGER PRIMARY KEY AUTOINCREMENT,
                video_id TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                channel TEXT NOT NULL,
                url TEXT NOT NULL,
                added_at INTEGER NOT NULL
            )
        """.trimIndent())
    }


    /** Read-only corruption/invariant check used by the runtime hardening dashboard. */
    fun quickIntegrityCheck(): LibraryIntegrityResult {
        val db = readableDatabase
        val quick = try {
            db.rawQuery("PRAGMA quick_check(1)", null).use { c ->
                if (c.moveToFirst()) c.getString(0).orEmpty() else "no-result"
            }
        } catch (e: Exception) {
            "error:${e.javaClass.simpleName}"
        }
        val queueRows = scalarInt(db, "SELECT COUNT(*) FROM play_queue")
        val invalidQueue = scalarInt(
            db,
            "SELECT COUNT(*) FROM play_queue WHERE TRIM(video_id)='' OR TRIM(url)=''"
        )
        val invalidHistory = scalarInt(
            db,
            "SELECT COUNT(*) FROM history WHERE position_ms < 0 OR duration_ms < 0 OR TRIM(video_id)='' OR TRIM(url)=''"
        )
        val healthy = quick.equals("ok", true) && invalidQueue == 0 && invalidHistory == 0
        return LibraryIntegrityResult(healthy, quick.take(120), queueRows, invalidQueue, invalidHistory)
    }

    private fun scalarInt(db: SQLiteDatabase, sql: String): Int = try {
        db.rawQuery(sql, null).use { c -> if (c.moveToFirst()) c.getInt(0) else -1 }
    } catch (_: Exception) {
        -1
    }

    private fun channelKey(channel: String): String = channel.trim().lowercase().take(180)

    companion object {
        private const val DB_NAME = "videoshield_library.db"
        private const val DB_VERSION = 4
    }
}
