package com.example.videoshield

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Lightweight renderer for the You-tab overview.
 *
 * The overview deliberately consumes only small preview lists. Full history, saved items and
 * queue data are queried only after the user opens a collection, keeping the common You-tab path
 * cheap even when the local library contains hundreds of rows.
 */
internal class YouLibraryOverviewController(
    private val activity: Activity,
    private val root: LinearLayout,
    private val thumbnails: VideoThumbnailLoader,
    private val onOpenVideo: (VideoItem) -> Unit,
    private val onOpenSection: (Section) -> Unit,
    private val onOpenDownloads: () -> Unit
) {
    enum class Section { HISTORY, CONTINUE, WATCH_LATER, QUEUE, SUBSCRIPTIONS }

    data class Snapshot(
        val history: List<VideoItem>,
        val continueWatching: List<VideoItem>,
        val watchLater: List<VideoItem>,
        val queue: List<VideoItem>,
        val historyCount: Int,
        val watchLaterCount: Int,
        val queueCount: Int,
        val subscriptionCount: Int,
        val downloadCount: Int
    )

    private val ui = UiMetrics(activity)
    private var renderedFingerprint: Int = 0

    fun showLoading() {
        renderedFingerprint = 0
        root.removeAllViews()
        repeat(3) { index ->
            root.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.spaceXl, if (index == 0) dp(10) else ui.pillRadius, ui.spaceXl, ui.spaceSm)
                addView(skeleton(dp(126), ui.pillRadius))
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, ui.spaceLg, 0, 0)
                    repeat(2) {
                        addView(skeleton(ui.libraryCollectionWidth, dp(122)).apply {
                            layoutParams = LinearLayout.LayoutParams(ui.libraryCollectionWidth, dp(122)).apply { marginEnd = ui.libraryCardGap }
                        })
                    }
                })
            })
        }
    }

    fun render(snapshot: Snapshot) {
        val fingerprint = fingerprint(snapshot)
        if (renderedFingerprint == fingerprint && root.childCount > 0) return
        renderedFingerprint = fingerprint
        root.removeAllViews()

        if (snapshot.history.isNotEmpty()) {
            addVideoSection(activity.getString(R.string.ui_history), snapshot.history, Section.HISTORY)
        }
        if (snapshot.continueWatching.isNotEmpty()) {
            addVideoSection(activity.getString(R.string.ui_continue_watching), snapshot.continueWatching, Section.CONTINUE)
        }

        addPlaylistSection(snapshot)
        addLibraryShortcuts(snapshot)
    }

    fun clear() {
        renderedFingerprint = 0
        root.removeAllViews()
    }

    private fun addVideoSection(title: String, items: List<VideoItem>, section: Section) {
        val sectionRoot = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, ui.spaceXs)
        }
        sectionRoot.addView(sectionHeader(title) { onOpenSection(section) })
        sectionRoot.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(ui.spaceXl, ui.spaceMd, ui.spaceXs, ui.spaceXs)
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                items.take(PREVIEW_LIMIT).forEach { addView(videoCard(it)) }
            })
        })
        root.addView(sectionRoot)
    }

    private fun addPlaylistSection(snapshot: Snapshot) {
        val sectionRoot = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, ui.spaceXs)
        }
        sectionRoot.addView(TextView(activity).apply {
            text = activity.getString(R.string.ui_playlists)
            setTextAppearance(R.style.YouTextSectionTitle)
            setPadding(ui.spaceXl, 0, ui.spaceXl, ui.spaceMd)
        })
        sectionRoot.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(ui.spaceXl, 0, ui.spaceXs, ui.spaceXs)
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(collectionCard(
                    title = activity.getString(R.string.ui_watch_later),
                    subtitle = activity.getString(R.string.ui_items_count, snapshot.watchLaterCount),
                    coverVideoId = snapshot.watchLater.firstOrNull()?.videoId.orEmpty(),
                    icon = R.drawable.ic_ui_bookmark,
                    action = { onOpenSection(Section.WATCH_LATER) }
                ))
                addView(collectionCard(
                    title = activity.getString(R.string.ui_queue),
                    subtitle = activity.getString(R.string.ui_items_count, snapshot.queueCount),
                    coverVideoId = snapshot.queue.firstOrNull()?.videoId.orEmpty(),
                    icon = R.drawable.ic_ui_queue,
                    action = { onOpenSection(Section.QUEUE) }
                ))
            })
        })
        root.addView(sectionRoot)
    }

    private fun addLibraryShortcuts(snapshot: Snapshot) {
        root.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.spaceLg, ui.spaceXl, ui.spaceLg, dp(20))
            addView(shortcutRow(
                icon = R.drawable.ic_ui_download,
                title = activity.getString(R.string.ui_downloads),
                subtitle = activity.getString(R.string.ui_items_count, snapshot.downloadCount),
                action = onOpenDownloads
            ))
            addView(shortcutRow(
                icon = R.drawable.ic_nav_subscriptions,
                title = activity.getString(R.string.ui_subscriptions),
                subtitle = activity.getString(R.string.ui_channels_count, snapshot.subscriptionCount),
                action = { onOpenSection(Section.SUBSCRIPTIONS) }
            ))
        })
    }

    private fun sectionHeader(title: String, onViewAll: () -> Unit): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.spaceXl, 0, ui.spaceMd, 0)
        addView(TextView(activity).apply {
            text = title
            setTextAppearance(R.style.YouTextSectionTitle)
            layoutParams = LinearLayout.LayoutParams(0, ui.librarySectionHeaderHeight, 1f)
            gravity = Gravity.CENTER_VERTICAL
        })
        addView(TextView(activity).apply {
            text = activity.getString(R.string.ui_view_all)
            setTextAppearance(R.style.YouTextViewAll)
            gravity = Gravity.CENTER
            setPadding(ui.spaceLg, 0, ui.spaceLg, 0)
            minHeight = ui.librarySectionHeaderHeight
            background = rounded(AppTheme.control(activity), ui.pillRadius.toFloat())
            setOnClickListener { onViewAll() }
        })
    }

    private fun videoCard(item: VideoItem): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(ui.libraryVideoCardWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = ui.libraryCardGap }
        isClickable = true
        isFocusable = true
        foreground = activity.getDrawable(android.R.drawable.list_selector_background)
        setOnClickListener { onOpenVideo(item) }

        val media = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ui.libraryVideoCardWidth, ui.libraryVideoCardHeight)
            background = rounded(AppTheme.control(activity), ui.cardRadius.toFloat())
            clipToOutline = true
        }
        val image = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = activity.getString(R.string.ui_video_thumbnail)
        }
        media.addView(image)
        thumbnails.bind(image, item.videoId)
        if (item.durationMs > 0L && item.positionMs > 0L) {
            media.addView(ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progress = ((item.positionMs.coerceIn(0L, item.durationMs) * 1000L) / item.durationMs).toInt().coerceIn(0, 1000)
                progressTintList = android.content.res.ColorStateList.valueOf(AppTheme.progressAccent(activity))
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.argb(110, 255, 255, 255))
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.libraryProgressHeight, Gravity.BOTTOM)
            })
        }
        addView(media)
        addView(TextView(activity).apply {
            text = item.title.ifBlank { activity.getString(R.string.ui_video) }
            setTextAppearance(R.style.YouTextCardTitle)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, ui.spaceMd, 0, 0)
        })
        addView(TextView(activity).apply {
            text = item.channel
            visibility = if (item.channel.isBlank()) View.GONE else View.VISIBLE
            setTextAppearance(R.style.YouTextMetadata)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, ui.spaceXxs, 0, 0)
        })
    }

    private fun collectionCard(title: String, subtitle: String, coverVideoId: String, icon: Int, action: () -> Unit): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(ui.libraryCollectionWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = ui.libraryCardGap }
        setOnClickListener { action() }
        isClickable = true
        isFocusable = true
        foreground = activity.getDrawable(android.R.drawable.list_selector_background)

        val cover = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ui.libraryCollectionWidth, ui.libraryCollectionHeight)
            background = rounded(AppTheme.control(activity), ui.cardRadius.toFloat())
            clipToOutline = true
        }
        if (coverVideoId.isNotBlank()) {
            val image = ImageView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            cover.addView(image)
            thumbnails.bind(image, coverVideoId)
            cover.addView(View(activity).apply {
                setBackgroundColor(Color.argb(72, 0, 0, 0))
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            })
        }
        cover.addView(ImageView(activity).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
        })
        addView(cover)
        addView(TextView(activity).apply {
            text = title
            setTextAppearance(R.style.YouTextCardTitle)
            setPadding(0, ui.spaceMd, 0, 0)
        })
        addView(TextView(activity).apply {
            text = subtitle
            setTextAppearance(R.style.YouTextMetadata)
            setPadding(0, ui.spaceXxs, 0, 0)
        })
    }

    private fun shortcutRow(icon: Int, title: String, subtitle: String, action: () -> Unit): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.libraryShortcutMinHeight
        setPadding(dp(10), dp(5), ui.spaceMd, dp(5))
        background = rounded(AppTheme.control(activity), ui.cardRadius.toFloat())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ui.spaceMd }
        setOnClickListener { action() }
        addView(ImageView(activity).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(AppTheme.icon(activity))
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginStart = ui.spaceSm; marginEnd = ui.spaceXl }
        })
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(activity).apply {
                text = title; setTextAppearance(R.style.YouTextShortcutTitle)
            })
            addView(TextView(activity).apply {
                text = subtitle; setTextAppearance(R.style.YouTextMetadata); setPadding(0, ui.spaceXxs, 0, 0)
            })
        })
        addView(TextView(activity).apply {
            text = "›"
            textSize = 26f
            setTextColor(AppTheme.secondary(activity))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(36), ui.librarySectionHeaderHeight)
        })
    }

    private fun skeleton(width: Int, height: Int): View = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(width, height)
        background = rounded(AppTheme.control(activity), dp(10).toFloat())
        alpha = 0.72f
    }

    private fun fingerprint(snapshot: Snapshot): Int {
        var hash = if (AppTheme.isLight(activity)) 1 else 0
        fun mix(value: Int) { hash = 31 * hash + value }
        fun mixVideos(items: List<VideoItem>, includeProgress: Boolean) {
            items.forEach { item ->
                mix(item.videoId.hashCode())
                if (includeProgress) mix((item.positionMs xor (item.positionMs ushr 32)).toInt())
            }
        }
        mixVideos(snapshot.history, true)
        mixVideos(snapshot.continueWatching, true)
        mixVideos(snapshot.watchLater, false)
        mixVideos(snapshot.queue, false)
        mix(snapshot.historyCount); mix(snapshot.watchLaterCount); mix(snapshot.queueCount)
        mix(snapshot.subscriptionCount); mix(snapshot.downloadCount)
        return hash
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    companion object {
        private const val PREVIEW_LIMIT = 8
    }
}
