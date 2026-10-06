package com.example.videoshield

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import android.widget.ImageView
import android.widget.FrameLayout
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher

class LibraryActivity : LocalizedActivity() {
    private lateinit var store: LibraryStore
    private lateinit var preferences: ShieldPreferences
    private lateinit var snapshotStore: PlaybackSnapshotStore
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var title: TextView
    private lateinit var nowPlaying: TextView
    private lateinit var nowPlayingProgress: ProgressBar
    private lateinit var miniPlayerBar: SwipeDismissLayout
    private var mode: String = MODE_HISTORY
    private var relatedVideo: VideoItem? = null
    private val suggestionWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var refreshGeneration = 0
    private var loadedRows: List<LibraryRow> = emptyList()
    private lateinit var search: EditText
    private lateinit var adapter: LibraryAdapter
    private val thumbnails = VideoThumbnailLoader()
    private lateinit var collectionTitle: TextView
    private lateinit var collectionCount: TextView
    private lateinit var collectionImage: ImageView
    private lateinit var collectionActions: LinearLayout
    private lateinit var collectionHeader: LinearLayout
    private var actionSheet: android.app.Dialog? = null

    private val miniPlayerHandler = Handler(Looper.getMainLooper())
    private val searchFilter = Runnable { if (!isDestroyed && !isFinishing) displayRows() }
    private val miniPlayerTicker = object : Runnable {
        override fun run() {
            refreshNowPlaying()
            miniPlayerHandler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        setContentView(R.layout.activity_library)
        SystemBarInsets.install(this)
        preferences = ShieldPreferences(this)
        store = LibraryStore(this)
        snapshotStore = PlaybackSnapshotStore(this)
        list = findViewById(R.id.libraryList)
        empty = findViewById(R.id.libraryEmpty)
        title = findViewById(R.id.libraryTitle)
        search = findViewById(R.id.librarySearch)
        adapter = LibraryAdapter(emptyList())
        list.addHeaderView(createCollectionHeader(), null, false)
        list.adapter = adapter
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                miniPlayerHandler.removeCallbacks(searchFilter)
                if (s.isNullOrEmpty()) displayRows() else miniPlayerHandler.postDelayed(searchFilter, 150L)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        nowPlaying = findViewById(R.id.nowPlayingText)
        nowPlayingProgress = findViewById(R.id.nowPlayingProgress)
        miniPlayerBar = findViewById(R.id.miniPlayerBar)
        applyThemeSurface()

        mode = savedInstanceState?.getString(EXTRA_MODE) ?: intent.getStringExtra(EXTRA_MODE) ?: MODE_HISTORY
        savedInstanceState?.getString("related_id")?.let { id ->
            relatedVideo = VideoItem(id, savedInstanceState.getString("related_title").orEmpty(),
                savedInstanceState.getString("related_channel").orEmpty(), "https://m.youtube.com/watch?v=$id", 0L)
        }
        findViewById<Button>(R.id.forYouTab).setOnClickListener { mode = MODE_FOR_YOU; refresh() }
        findViewById<Button>(R.id.relatedTab).setOnClickListener {
            val s = snapshotStore.get()
            relatedVideo = if (s.videoId.isNotBlank()) VideoItem(s.videoId, s.title, s.channel, s.url, s.updatedAt, s.positionMs, s.durationMs) else null
            mode = MODE_RELATED
            search.setText("")
            refresh()
        }
        findViewById<Button>(R.id.continueTab).setOnClickListener { mode = MODE_CONTINUE; refresh() }
        findViewById<Button>(R.id.recommendationControlsButton).setOnClickListener {
            val options = mutableListOf(getString(R.string.tune_recommendations), getString(R.string.ui_settings))
            if (mode != MODE_RELATED) options += getString(R.string.clear_list)
            AlertDialog.Builder(this).setItems(options.toTypedArray()) { _, index -> when (index) {
                0 -> showRecommendationControls()
                1 -> startActivity(Intent(this, SettingsActivity::class.java))
                else -> clearCurrent()
            } }.show()
        }
        findViewById<Button>(R.id.librarySearchToggle).setOnClickListener {
            search.visibility = if (search.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (search.visibility == View.VISIBLE) search.requestFocus() else search.setText("")
        }
        findViewById<Button>(R.id.navHomeButton).setOnClickListener { openUrl(YouTubeRoute.HOME_URL) }
        findViewById<Button>(R.id.navShieldButton).setOnClickListener { openUrl(YouTubeRoute.SHORTS_URL) }
        findViewById<Button>(R.id.navSubscriptionsButton).setOnClickListener { openUrl(YouTubeRoute.SUBSCRIPTIONS_URL) }
        findViewById<Button>(R.id.navLibraryButton).setOnClickListener { mode = MODE_HISTORY; refresh() }
        findViewById<Button>(R.id.navCreateButton).setOnClickListener {
            actionSheet?.dismiss()
            actionSheet=ActionSheet.show(this,getString(R.string.your_library),listOf(
                ActionSheet.Action(getString(R.string.ui_for_you),getString(R.string.history_suggestions),R.drawable.ic_nav_home),
                ActionSheet.Action(getString(R.string.ui_queue),getString(R.string.next_videos),R.drawable.ic_ui_queue),
                ActionSheet.Action(getString(R.string.ui_favorites),getString(R.string.favorite_videos),R.drawable.ic_ui_bookmark)
            )) { index ->
                mode = listOf(MODE_FOR_YOU, MODE_QUEUE, MODE_FAVORITES)[index]; refresh()
            }
        }
        findViewById<Button>(R.id.historyTab).setOnClickListener { mode = MODE_HISTORY; refresh() }
        findViewById<Button>(R.id.aboutButton).setOnClickListener { showAbout() }
        findViewById<Button>(R.id.downloadsButton).setOnClickListener { startActivity(Intent(this,DownloadsActivity::class.java)) }
        findViewById<Button>(R.id.eqButton).setOnClickListener { EqDialog.show(this) }
        findViewById<Button>(R.id.favoritesTab).setOnClickListener { mode = MODE_FAVORITES; refresh() }
        findViewById<Button>(R.id.queueTab).setOnClickListener { mode = MODE_QUEUE; refresh() }
        findViewById<Button>(R.id.subscriptionsTab).setOnClickListener { mode = MODE_SUBSCRIPTIONS; refresh() }
        findViewById<Button>(R.id.closeLibraryButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.playPauseButton).setOnClickListener { sendPlaybackCommand(PlaybackService.CMD_PLAY_PAUSE) }
        findViewById<Button>(R.id.seekBackButton).setOnClickListener { sendPlaybackCommand(PlaybackService.CMD_SEEK_BACK) }
        findViewById<Button>(R.id.seekForwardButton).setOnClickListener { sendPlaybackCommand(PlaybackService.CMD_SEEK_FORWARD) }
        findViewById<Button>(R.id.stopPlaybackButton).setOnClickListener { dismissPlayback() }
        findViewById<Button>(R.id.clearLibraryButton).setOnClickListener { clearCurrent() }

        miniPlayerBar.onDismiss = {
            dismissPlayback()
            Toast.makeText(this, getString(R.string.ui_playback_dismissed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        applyThemeSurface()
        miniPlayerHandler.removeCallbacks(miniPlayerTicker)
        miniPlayerHandler.post(miniPlayerTicker)
        refresh()
    }

    override fun onPause() {
        miniPlayerHandler.removeCallbacks(searchFilter)
        miniPlayerHandler.removeCallbacks(miniPlayerTicker)
        super.onPause()
    }

    private fun applyThemeSurface() {
        AppTheme.applySystemBars(this)
        findViewById<View>(R.id.libraryRoot).setBackgroundColor(AppTheme.background(this))
        findViewById<View>(R.id.libraryTopBar).setBackgroundColor(AppTheme.surface(this))
        miniPlayerBar.setBackgroundColor(AppTheme.surface(this))
    }

    private fun dismissPlayback() {
        sendPlaybackCommand(PlaybackService.CMD_STOP)
        snapshotStore.clear()
        refreshNowPlaying()
    }

    private fun refreshNowPlaying() {
        val s = snapshotStore.get()
        val hasMedia = s.title.isNotBlank() && s.url.isNotBlank()
        miniPlayerBar.visibility = if (hasMedia) View.VISIBLE else View.GONE
        if (!hasMedia) return

        val position = s.predictedPositionMs()
        val time = if (s.durationMs > 0L) " • ${formatTime(position)} / ${formatTime(s.durationMs)}" else ""
        val summary = "${s.title}$time\n${s.channel}"
        if (nowPlaying.text.toString() != summary) nowPlaying.text = summary
        // Returning reveals the existing player without reloading its URL.
        nowPlaying.setOnClickListener { finish() }

        findViewById<IconButton>(R.id.playPauseButton).apply {
            isEnabled = true
            setIcon(if (s.playing) R.drawable.ic_ui_pause else R.drawable.ic_ui_play)
            contentDescription = if (s.playing) getString(R.string.ui_pause) else getString(R.string.ui_play)
        }
        findViewById<Button>(R.id.seekBackButton).isEnabled = true
        findViewById<Button>(R.id.seekForwardButton).isEnabled = true
        findViewById<Button>(R.id.stopPlaybackButton).isEnabled = true

        val progress = if (s.durationMs > 0L) {
            ((position.coerceIn(0L, s.durationMs) * 1000L) / s.durationMs).toInt().coerceIn(0, 1000)
        } else 0
        if (nowPlayingProgress.progress != progress) nowPlayingProgress.progress = progress
    }

    private fun refresh() {
        val generation = ++refreshGeneration
        val selected = mode
        loadedRows = emptyList()
        adapter.replace(emptyList())
        list.visibility = View.GONE
        empty.visibility = View.VISIBLE
        title.text = when (selected) {
            MODE_RELATED -> getString(R.string.ui_similar_videos)
            MODE_FOR_YOU -> getString(R.string.ui_for_you)
            MODE_CONTINUE -> getString(R.string.ui_continue_watching)
            MODE_FAVORITES -> getString(R.string.ui_favorites)
            MODE_QUEUE -> getString(R.string.ui_queue)
            MODE_SUBSCRIPTIONS -> getString(R.string.ui_subscriptions)
            else -> getString(R.string.ui_watch_history)
        }
        findViewById<Button>(R.id.clearLibraryButton).visibility = View.GONE
        val tabs = mapOf(R.id.forYouTab to MODE_FOR_YOU, R.id.relatedTab to MODE_RELATED, R.id.continueTab to MODE_CONTINUE,
            R.id.historyTab to MODE_HISTORY, R.id.favoritesTab to MODE_FAVORITES,
            R.id.queueTab to MODE_QUEUE, R.id.subscriptionsTab to MODE_SUBSCRIPTIONS)
        tabs.forEach { (id, value) -> findViewById<Button>(id).apply {
            setTextColor(if (selected == value) AppTheme.selectedText(this@LibraryActivity) else AppTheme.primary(this@LibraryActivity))
            backgroundTintList = android.content.res.ColorStateList.valueOf(if (selected == value) AppTheme.selectedSurface(this@LibraryActivity) else AppTheme.control(this@LibraryActivity))
        } }
        if (selected in setOf(MODE_FOR_YOU, MODE_RELATED) && (!preferences.personalizedSuggestions || !preferences.rememberHistory)) {
            empty.text = getString(R.string.ui_enable_local_suggestions_and_watch_history_in_settings)
            return
        }
        empty.text = getString(R.string.ui_loading)
        val since = preferences.recommendationsSince
        val focus = relatedVideo
        suggestionWorker.execute {
            val result = runCatching {
                val favorites = store.favoriteIds()
                when (selected) {
                    MODE_RELATED -> {
                        val seed = focus ?: store.history(1).firstOrNull()
                        if (seed == null) emptyList() else store.recommendations(since, focus = seed).map { LibraryRow.Suggestion(it) }
                    }
                    MODE_FOR_YOU -> store.recommendations(since).map { LibraryRow.Suggestion(it) }
                    MODE_CONTINUE -> store.history(1000).filter(LibraryPolicy::canResume).map { LibraryRow.Video(it, it.videoId in favorites, false) }
                    MODE_FAVORITES -> store.favorites().map { LibraryRow.Video(it, true, false) }
                    MODE_QUEUE -> store.queue().map { LibraryRow.Video(it, it.videoId in favorites, true) }
                    MODE_SUBSCRIPTIONS -> store.subscriptions().map { LibraryRow.Channel(it) }
                    else -> store.history().map { LibraryRow.Video(it, it.videoId in favorites, false) }
                }
            }
            runOnUiThread {
                if (isDestroyed || isFinishing || generation != refreshGeneration) return@runOnUiThread
                if (result.isFailure) {
                    empty.text = getString(R.string.ui_could_not_load_the_library_reopen_this_tab_to_retry)
                    return@runOnUiThread
                }
                loadedRows = result.getOrDefault(emptyList())
                displayRows()
            }
        }
    }

    private fun displayRows() {
        miniPlayerHandler.removeCallbacks(searchFilter)
        val query = search.text.toString()
        val rows = loadedRows.filter { row ->
            when (row) {
                is LibraryRow.Suggestion -> LibraryPolicy.matches(query, row.item.video.title, row.item.video.channel)
                is LibraryRow.Video -> LibraryPolicy.matches(query, row.item.title, row.item.channel)
                is LibraryRow.Channel -> LibraryPolicy.matches(query, row.item.name, "")
            }
        }
        empty.text = when {
            query.isNotBlank() -> getString(R.string.ui_no_matching_titles_or_channels)
            mode == MODE_FOR_YOU -> getString(R.string.ui_browse_home_or_search_to_discover_videos_nwatch_videos_favorite_t)
            mode == MODE_RELATED -> getString(R.string.ui_no_similar_videos_found_yet_nbrowse_home_or_search_to_discover_mo)
            mode == MODE_CONTINUE -> getString(R.string.ui_partly_watched_videos_appear_here_nvideos_near_the_end_are_exclud)
            else -> getString(R.string.ui_nothing_here_yet)
        }
        if (mode == MODE_QUEUE) title.text = getString(R.string.queue_count,loadedRows.size)
        collectionTitle.setTextIfChanged(title.text)
        collectionCount.setTextIfChanged(getString(if (mode == MODE_SUBSCRIPTIONS) R.string.channel_count else R.string.video_count,rows.size))
        val first = rows.firstOrNull().let { when (it) {
            is LibraryRow.Video -> it.item.videoId
            is LibraryRow.Suggestion -> it.item.video.videoId
            else -> ""
        } }
        val playlist = mode in setOf(MODE_QUEUE, MODE_FAVORITES)
        collectionImage.visibility = if (first.isBlank() || !playlist) View.GONE else View.VISIBLE
        collectionHeader.background = if(playlist) GradientDrawable(GradientDrawable.Orientation.TL_BR,
            if (AppTheme.isLight(this)) intArrayOf(Color.rgb(236,228,247),Color.rgb(224,234,247)) else intArrayOf(Color.rgb(65,39,70),Color.rgb(26,37,51))).apply { cornerRadius=dp(16).toFloat() }
            else android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        collectionActions.visibility = if (mode == MODE_SUBSCRIPTIONS) View.GONE else View.VISIBLE
        thumbnails.bind(collectionImage, if(playlist) first else "")
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
        adapter.replace(rows)
    }

    private fun mutate(message: String? = null, action: () -> Unit) {
        suggestionWorker.execute {
            val result = runCatching(action)
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (result.isFailure) Toast.makeText(this, getString(R.string.ui_could_not_save_the_change_please_retry), Toast.LENGTH_LONG).show()
                else {
                    if (message != null) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
        }
    }

    private fun showAbout() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name) + " • $version")
            .setMessage(R.string.about_body)
            .setNeutralButton(R.string.app_update_check) { _, _ ->
                startActivity(Intent(this, AppUpdateActivity::class.java))
            }
            .setPositiveButton(getString(R.string.ui_close), null)
            .show()
    }

    private fun clearCurrent() {
        val selected = mode
        AlertDialog.Builder(this).setTitle(getString(R.string.clear_section,title.text))
            .setMessage(if (selected == MODE_FOR_YOU) getString(R.string.ui_reset_learned_preferences_discovered_videos_and_hidden_suggestion) else getString(R.string.ui_this_removes_the_items_in_this_section_from_your_local_library))
            .setNegativeButton(getString(R.string.ui_cancel), null).setPositiveButton(getString(R.string.ui_clear)) { _, _ ->
                if (selected == MODE_FOR_YOU || selected == MODE_HISTORY) {
                    preferences.recommendationsSince = System.currentTimeMillis()
                    if (selected == MODE_HISTORY) preferences.historyClearedAt = preferences.recommendationsSince
                }
                mutate(getString(R.string.cleared)) {
                    when (selected) {
                        MODE_FOR_YOU -> store.clearPersonalization()
                        MODE_FAVORITES -> store.clearFavorites()
                        MODE_QUEUE -> store.clearQueue()
                        MODE_SUBSCRIPTIONS -> store.clearSubscriptions()
                        MODE_CONTINUE -> store.history(1000).filter(LibraryPolicy::canResume).forEach { store.removeHistory(it.videoId) }
                        else -> { store.clearHistory(); store.clearPersonalization() }
                    }
                }
            }.show()
    }

    private fun suggestionFeedback(video: VideoItem) {
        val choices = if (video.channel.isBlank()) arrayOf(getString(R.string.hide_video)) else arrayOf(getString(R.string.hide_video), getString(R.string.hide_channel,video.channel))
        AlertDialog.Builder(this).setTitle(getString(R.string.ui_improve_your_suggestions))
            .setItems(choices) { _, which ->
                mutate(getString(R.string.hidden_done)) {
                    if (which == 0) store.dismissSuggestion(video.videoId) else store.blockChannel(video.channel)
                }
            }.setNegativeButton(getString(R.string.ui_cancel), null).show()
    }

    private fun showRecommendationControls() {
        suggestionWorker.execute {
            val result = runCatching { store.blockedChannels().toTypedArray() }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (result.isFailure) {
                    Toast.makeText(this, getString(R.string.ui_could_not_load_hidden_channels), Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val channels = result.getOrThrow()
                val hidden = BooleanArray(channels.size) { true }
                val dialog = AlertDialog.Builder(this).setTitle(if (channels.isEmpty()) getString(R.string.ui_hidden_channels) else getString(R.string.ui_uncheck_channels_to_restore))
                    .setPositiveButton(getString(R.string.ui_save)) { _, _ -> mutate { channels.forEachIndexed { i, channel -> if (!hidden[i]) store.unblockChannel(channel) } } }
                    .setNegativeButton(getString(R.string.ui_cancel), null)
                    .setNeutralButton(getString(R.string.restore_videos)) { _, _ -> mutate(getString(R.string.restored_videos)) { store.restoreSuggestions() } }
                if (channels.isEmpty()) dialog.setMessage(getString(R.string.ui_no_hidden_channels_use_the_beside_a_suggestion_to_hide_a_video_or))
                else dialog.setMultiChoiceItems(channels, hidden) { _, which, checked -> hidden[which] = checked }
                dialog.show()
            }
        }
    }

    private fun openUrl(url: String) {
        if (url.isBlank()) return
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(url)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    private fun sendPlaybackCommand(command: String) {
        sendBroadcast(Intent(PlaybackService.ACTION_COMMAND).setPackage(packageName).putExtra(PlaybackService.EXTRA_COMMAND, command))
    }

    private fun formatTime(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private sealed class LibraryRow {
        data class Suggestion(val item: SuggestedVideo) : LibraryRow()
        data class Video(val item: VideoItem, val favorite: Boolean, val queued: Boolean) : LibraryRow()
        data class Channel(val item: ChannelItem) : LibraryRow()
    }

    private fun createCollectionHeader(): View = LinearLayout(this).apply {
        collectionHeader=this
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(18))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, if (AppTheme.isLight(this@LibraryActivity)) intArrayOf(Color.rgb(236, 228, 247), Color.rgb(224, 234, 247)) else intArrayOf(Color.rgb(65, 39, 70), Color.rgb(26, 37, 51))).apply { cornerRadius = dp(16).toFloat() }
        collectionImage = ImageView(this@LibraryActivity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.widthPixels - dp(32)) * 9 / 16)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { setColor(AppTheme.control(this@LibraryActivity)); cornerRadius = dp(12).toFloat() }
            clipToOutline = true
            contentDescription = getString(R.string.ui_collection_cover)
        }
        addView(collectionImage)
        collectionTitle = TextView(this@LibraryActivity).apply {
            textSize = 24f; setTextColor(AppTheme.primary(this@LibraryActivity)); setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(6))
        }
        collectionCount = TextView(this@LibraryActivity).apply { textSize = 12f; setTextColor(AppTheme.secondary(this@LibraryActivity)) }
        addView(collectionTitle); addView(collectionCount)
        collectionActions = LinearLayout(this@LibraryActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
            listOf(getString(R.string.ui_play_all) to false, getString(R.string.ui_shuffle) to true).forEach { (label, shuffle) ->
                addView(Button(this@LibraryActivity).apply {
                    text = label; isAllCaps = false; textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { if (shuffle) marginStart = dp(10) }
                    setTextColor(if (shuffle) Color.WHITE else AppTheme.selectedText(this@LibraryActivity))
                    setPadding(dp(16), 0, dp(16), 0)
                    setLeadingIcon(if (shuffle) R.drawable.ic_ui_shuffle else R.drawable.ic_ui_play, if (shuffle) Color.WHITE else AppTheme.selectedText(this@LibraryActivity))
                    background = GradientDrawable().apply { setColor(if (shuffle) Color.rgb(68, 74, 88) else AppTheme.selectedSurface(this@LibraryActivity)); cornerRadius = dp(24).toFloat() }
                    setOnClickListener { playCollection(shuffle) }
                })
            }
        }
        addView(collectionActions)
    }

    private fun playCollection(shuffle: Boolean) {
        val query = search.text.toString()
        val videos = loadedRows.mapNotNull { when (it) {
            is LibraryRow.Video -> it.item
            is LibraryRow.Suggestion -> it.item.video
            else -> null
        } }.filter { LibraryPolicy.matches(query, it.title, it.channel) }.distinctBy { it.videoId }.take(50).let { if (shuffle) it.shuffled() else it }
        if (videos.isEmpty()) { Toast.makeText(this, getString(R.string.ui_no_videos_to_play), Toast.LENGTH_SHORT).show(); return }
        suggestionWorker.execute {
            val result = runCatching {
                store.prepareCollection(videos)
            }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                if (result.isSuccess) openUrl(videos.first().url)
                else Toast.makeText(this, getString(R.string.ui_could_not_prepare_playback), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inner class LibraryAdapter(private var items: List<LibraryRow>) : BaseAdapter() {
        fun replace(rows: List<LibraryRow>) {
            if (items == rows) return
            items = rows
            notifyDataSetChanged()
        }
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        private inner class RowHolder {
            val root = LinearLayout(this@LibraryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(10), dp(10), dp(10))
            }
            val thumbnail = ImageView(this@LibraryActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(120), dp(68)).apply { marginEnd = dp(12) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply { setColor(AppTheme.control(this@LibraryActivity)); cornerRadius = dp(8).toFloat() }
                clipToOutline = true
                contentDescription = getString(R.string.ui_video_thumbnail)
            }
            val details = LinearLayout(this@LibraryActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(0, dp(4), dp(8), dp(4))
            }
            val text = TextView(this@LibraryActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(AppTheme.primary(this@LibraryActivity)); textSize = 14f; maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val channel = TextView(this@LibraryActivity).apply {
                textSize = 12f; setTextColor(AppTheme.secondary(this@LibraryActivity)); maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val reason = TextView(this@LibraryActivity).apply {
                textSize = 11f; setTextColor(AppTheme.tertiary(this@LibraryActivity)); maxLines = 2
            }
            val actions = LinearLayout(this@LibraryActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(54))
            }
            init {
                details.addView(text); details.addView(channel); details.addView(reason)
                root.addView(thumbnail); root.addView(details); root.addView(actions); root.tag = this
            }
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val holder = convertView?.tag as? RowHolder ?: RowHolder()
            val row = items[position]
            val root = holder.root
            val text = holder.text
            val actions = holder.actions
            root.alpha = 1f
            root.setOnLongClickListener(null); root.setOnDragListener(null)
            root.isLongClickable = false
            text.setOnLongClickListener(null); text.setOnClickListener(null)
            text.isClickable = false; text.isLongClickable = false
            holder.details.setOnClickListener(null)
            holder.details.isClickable = false
            holder.channel.visibility = View.GONE; holder.reason.visibility = View.GONE
            text.maxLines = 3
            actions.removeAllViews()
            holder.thumbnail.visibility = if (row is LibraryRow.Channel) View.GONE else View.VISIBLE
            val thumbnailId = when (row) { is LibraryRow.Video -> row.item.videoId; is LibraryRow.Suggestion -> row.item.video.videoId; else -> "" }
            thumbnails.bind(holder.thumbnail, thumbnailId)
            root.setBackgroundColor(if (AppTheme.isLight(this@LibraryActivity)) {
                if (position % 2 == 0) AppTheme.background(this@LibraryActivity) else Color.rgb(246, 247, 249)
            } else {
                if (position % 2 == 0) AppTheme.background(this@LibraryActivity) else Color.rgb(8, 8, 8)
            })
            when (row) {
                is LibraryRow.Suggestion -> {
                    text.text = row.item.video.title; text.maxLines = 2
                    holder.channel.text = row.item.video.channel
                    holder.channel.visibility = if (row.item.video.channel.isBlank()) View.GONE else View.VISIBLE
                    holder.reason.text = LocalizedPresentation.recommendation(this@LibraryActivity,row.item.reason); holder.reason.visibility = View.VISIBLE
                    holder.details.setOnClickListener { openUrl(row.item.video.url) }
                    actions.addView(queueActionButton("+Q") { mutate(getString(R.string.saved_queue)) { store.enqueue(row.item.video) } })
                    actions.addView(smallButton("✕") { suggestionFeedback(row.item.video) }.apply { contentDescription = getString(R.string.ui_not_interested) })
                }
                is LibraryRow.Video -> {
                    bindVideoRow(row, root, text, actions)
                    text.text = row.item.title; text.maxLines = 2
                    holder.channel.text = row.item.channel
                    holder.channel.visibility = if (row.item.channel.isBlank()) View.GONE else View.VISIBLE
                    if (row.item.durationMs > 0) {
                        holder.reason.text = "${formatTime(row.item.positionMs)} / ${formatTime(row.item.durationMs)}"
                        holder.reason.visibility = View.VISIBLE
                    }
                }
                is LibraryRow.Channel -> bindChannelRow(row, text, actions)
            }
            holder.thumbnail.setOnClickListener {
                if (row is LibraryRow.Video) text.performClick() else holder.details.performClick()
            }
            val buttons = (0 until actions.childCount).mapNotNull { actions.getChildAt(it) as? Button }.filter { it.isEnabled }
            if (buttons.isNotEmpty()) {
                val labels = buttons.map { when (it.text.toString()) {
                    "+Q" -> getString(R.string.add_queue); "NEXT", getString(R.string.ui_next) -> getString(R.string.play_next); "☆" -> getString(R.string.save_favorites); "★" -> getString(R.string.remove_favorites)
                    "↑" -> getString(R.string.move_up); "↓" -> getString(R.string.move_down); "✕" -> if (row is LibraryRow.Suggestion) getString(R.string.ui_not_interested) else getString(R.string.remove)
                    else -> it.text.toString()
                } }.toMutableList()
                if (row is LibraryRow.Video && !row.queued) labels += getString(R.string.ui_find_similar_videos)
                actions.removeAllViews()
                actions.addView(IconButton(this@LibraryActivity, null, android.R.attr.borderlessButtonStyle).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                    minWidth = 0; minHeight = 0
                    setIcon(R.drawable.ic_ui_more)
                    contentDescription = getString(R.string.ui_video_options)
                    setOnClickListener {
                        actionSheet?.dismiss()
                        actionSheet=ActionSheet.show(this@LibraryActivity,text.text.toString(),labels.map { label ->
                            ActionSheet.Action(label,icon=when {
                                label in setOf(getString(R.string.add_queue),getString(R.string.play_next)) -> R.drawable.ic_ui_queue
                                label in setOf(getString(R.string.save_favorites),getString(R.string.remove_favorites)) -> R.drawable.ic_ui_bookmark
                                label == getString(R.string.ui_find_similar_videos) -> R.drawable.ic_search
                                else -> R.drawable.ic_ui_more
                            },danger=label in setOf(getString(R.string.remove),getString(R.string.ui_not_interested),getString(R.string.remove_favorites)))
                        }) { index ->
                            if (index < buttons.size) buttons[index].performClick() else text.performLongClick()
                        }
                    }
                })
            }
            return root
        }

        private fun bindVideoRow(row: LibraryRow.Video, root: LinearLayout, text: TextView, actions: LinearLayout) {
            val progress = if (row.item.durationMs > 0) {
                " • ${(row.item.positionMs.toDouble() / row.item.durationMs * 100).toInt().coerceIn(0, 100)}%"
            } else ""
            text.text = "${row.item.title}\n${row.item.channel}$progress"
            text.setOnClickListener {
                if (row.queued) {
                    suggestionWorker.execute {
                        val removed = runCatching { store.removeFromQueue(row.item.videoId) }
                        runOnUiThread {
                            if (!isDestroyed && !isFinishing) {
                                if (removed.isSuccess) openUrl(row.item.url)
                                else Toast.makeText(this@LibraryActivity, getString(R.string.ui_could_not_update_queue), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else openUrl(row.item.url)
            }

            if (row.queued) {
                val dragStarter = View.OnLongClickListener {
                    val clip = ClipData.newPlainText("VoTuibe queue item", row.item.videoId)
                    root.startDragAndDrop(clip, View.DragShadowBuilder(root), row.item.videoId, 0)
                    root.alpha = 0.55f
                    true
                }
                root.setOnLongClickListener(dragStarter)
                text.setOnLongClickListener(dragStarter)
                root.setOnDragListener { view, event ->
                    when (event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> event.localState is String
                        DragEvent.ACTION_DRAG_ENTERED -> { view.alpha = 0.72f; true }
                        DragEvent.ACTION_DRAG_EXITED -> { view.alpha = 1f; true }
                        DragEvent.ACTION_DROP -> {
                            val videoId = event.localState as? String ?: return@setOnDragListener false
                            view.alpha = 1f
                            mutate {
                                val target = store.queue().indexOfFirst { it.videoId == row.item.videoId }
                                if (target >= 0) store.moveQueueTo(videoId, target)
                            }
                            true
                        }
                        DragEvent.ACTION_DRAG_ENDED -> { view.alpha = 1f; true }
                        else -> true
                    }
                }
                actions.addView(smallButton("↑") {
                    mutate { store.moveQueue(row.item.videoId, -1) }
                }.apply { isEnabled = loadedRows.indexOf(row) > 0 })
                actions.addView(smallButton("↓") {
                    mutate { store.moveQueue(row.item.videoId, +1) }
                }.apply { isEnabled = loadedRows.indexOf(row) < loadedRows.lastIndex })
                actions.addView(smallButton("✕") {
                    mutate { store.removeFromQueue(row.item.videoId) }
                })
            } else {
                text.setOnLongClickListener {
                    relatedVideo = row.item
                    mode = MODE_RELATED
                    search.setText("")
                    refresh()
                    true
                }
                actions.addView(queueActionButton(getString(R.string.ui_next)) {
                    mutate(getString(R.string.ui_queued_to_play_next)) { store.enqueueNext(row.item) }
                })
                actions.addView(queueActionButton("+Q") {
                    mutate(getString(R.string.saved_queue)) { store.enqueue(row.item) }
                })
                actions.addView(smallButton(if (row.favorite) "★" else "☆") {
                    mutate { store.toggleFavorite(row.item) }
                })
            }
        }

        private fun bindChannelRow(row: LibraryRow.Channel, text: TextView, actions: LinearLayout) {
            text.text = "${row.item.name}\n${getString(R.string.ui_local_subscription)}"
            text.setOnClickListener { openUrl(row.item.url) }
            actions.addView(wideButton(getString(R.string.remove)) {
                mutate { store.toggleSubscription(row.item.name, row.item.url) }
            })
        }

        private fun smallButton(label: String, action: () -> Unit): Button = Button(this@LibraryActivity, null, android.R.attr.borderlessButtonStyle).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(44))
            minWidth = 0
            textSize = 14f
            text = label
            setOnClickListener { action() }
        }

        private fun queueActionButton(label: String, action: () -> Unit): Button = Button(this@LibraryActivity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(44))
            minWidth = 0
            textSize = 9f
            text = label
            setOnClickListener { action() }
        }

        private fun wideButton(label: String, action: () -> Unit): Button = Button(this@LibraryActivity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(44))
            minWidth = 0
            textSize = 11f
            text = label
            setOnClickListener { action() }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(EXTRA_MODE, mode)
        relatedVideo?.let {
            outState.putString("related_id", it.videoId)
            outState.putString("related_title", it.title)
            outState.putString("related_channel", it.channel)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        actionSheet?.dismiss()
        EqDialog.dismiss(this)
        miniPlayerHandler.removeCallbacks(searchFilter)
        thumbnails.close()
        ++refreshGeneration
        suggestionWorker.execute { store.close() }
        suggestionWorker.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_CONTINUE = "continue"
        const val MODE_HISTORY = "history"
        const val MODE_FOR_YOU = "for_you"
        const val MODE_RELATED = "related"
        const val MODE_FAVORITES = "favorites"
        const val MODE_QUEUE = "queue"
        const val MODE_SUBSCRIPTIONS = "subscriptions"
    }
}
