package com.example.videoshield

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Intent
import android.content.ComponentCallbacks2
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.net.URLEncoder

class MainActivity : LocalizedActivity() {
    private var quickActionSheet: android.app.Dialog? = null
    private val libraryTasks = SerialTaskQueue("library", 30L)
    private val habitTracker = ViewingHabitTracker()
    private lateinit var browseSession: BrowseSessionCoordinator
    private lateinit var browseHistory: BrowseHistoryCoordinator
    private val longSessionResources = LongSessionResourcePolicy()
    private lateinit var homeRecommendations: HomeRecommendationCoordinator
    private var browseNavigationBridge: BrowseNavigationBridge? = null
    private var historyPersistVideoId = ""
    private var pendingWatchedMs = 0L
    private var lastHistoryPersistAt = 0L

    private fun persistBrowseUrl(url: String, route: YouTubeRoute, immediate: Boolean = false) {
        if (::browseSession.isInitialized) browseSession.persistBrowseUrl(url, route, immediate)
    }

    private fun flushPendingBrowseUrl() {
        if (::browseSession.isInitialized) browseSession.flushBrowseUrl()
    }

    private fun noteShortsTransition(route: YouTubeRoute) {
        if (::browseSession.isInitialized) browseSession.onBrowseRoute(route, if (::browseWebView.isInitialized) browseWebView else null)
    }

    private fun refreshHomeRecommendations() {
        if (::homeRecommendations.isInitialized) homeRecommendations.refresh()
    }

    private fun writeLibrary(action: () -> Unit) {
        if (isDestroyed || libraryTasks.isShutdown) return
        libraryTasks.execute { try { action() } catch (e: Exception) { android.util.Log.w("YouTooBee", "Local library write failed", e) } }
    }

    private lateinit var webView: WebView
    private lateinit var browseWebView: WebView
    private lateinit var homePullRefreshLayout: HomePullRefreshLayout
    private lateinit var playerSurfaceController: PlayerSurfaceController
    private lateinit var playerRecoveryVisual: PlayerRecoveryVisualController
    private var warmResumeTask: Runnable? = null
    private var miniSurfaceApplied = false
    private var earlyPlayerScript: androidx.webkit.ScriptHandler? = null
    private var earlyBrowseScript: androidx.webkit.ScriptHandler? = null
    private var earlyScriptPolicy: String? = null
    // The Watch WebView is present in the stable layout, but its bridge/client/document-start
    // bootstrap stays lazy on browse-only cold starts so Home owns the first renderer work.
    private var playerWebViewConfigured = false
    private lateinit var playbackScreenOn: PlaybackScreenOnController
    private lateinit var playbackInactivity: PlaybackInactivityController
    private var uiRefreshPending = false
    private var renderedNavigation: YouTubeDestination? = null
    private val uiRefreshFrame = Runnable {
        uiRefreshPending = false
        if (!isDestroyed && !isFinishing) renderUi()
    }
    private lateinit var urlInput: EditText
    private lateinit var searchSuggestions: SearchSuggestionsController
    private lateinit var searchFilterRail: View
    private lateinit var searchFilterAll: Button
    private lateinit var searchFilterVideos: Button
    private lateinit var searchFilterShorts: Button
    private lateinit var searchFilterChannels: Button
    private lateinit var searchFilterPlaylists: Button
    private lateinit var searchFilterMore: Button
    private var renderedSearchFilterKey = ""
    private lateinit var saveVideo: SaveVideoController
    private lateinit var filterToggle: ToggleButton
    private lateinit var statusText: TextView
    private lateinit var channelButton: Button
    private lateinit var subscribeButton: Button
    private lateinit var favoriteButton: Button
    private lateinit var queueButton: Button
    private lateinit var speedButton: Button
    private lateinit var qualityButton: Button
    private lateinit var playbackMoreButton: IconButton
    private lateinit var autoNextButton: Button
    private lateinit var repeatButton: Button
    private lateinit var sleepButton: Button
    private lateinit var miniPlayerTitle: TextView
    private lateinit var miniPlayerChannel: TextView
    private lateinit var miniPlayPauseButton: IconButton
    private lateinit var miniPlayerProgress: SeekBar
    private lateinit var miniChromeController: MiniPlayerChromeController
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var playbackStatusRow: View
    private lateinit var playbackActionStrip: View
    private lateinit var navHomeButton: Button
    private lateinit var navShortsButton: Button
    private lateinit var navSubscriptionsButton: Button
    private lateinit var navLibraryButton: Button
    private lateinit var fullscreenContainer: FullscreenGestureLayout
    private lateinit var uiMetrics: UiMetrics

    // Building the full DOM/ad policy produces a large JavaScript string. Cache the stable
    // base bundles and rebuild only when an input that actually changes the policy changes.
    private var cachedPlayerPolicyKey = ""
    private var cachedPlayerPolicyScript = ""
    private var cachedBrowsePolicyKey = ""
    private var cachedBrowsePolicyScript = ""

    private lateinit var preferences: ShieldPreferences
    private lateinit var rulePackManager: RulePackManager
    private lateinit var filterEngine: FilterEngine
    private lateinit var browseFilterEngine: FilterEngine
    private lateinit var compatibilityMonitor: CompatibilityMonitor
    private lateinit var stats: ShieldStats
    private lateinit var libraryStore: LibraryStore
    private lateinit var searchHistoryStore: SearchHistoryStore
    private lateinit var playerController: PlayerController
    private lateinit var playbackBackend: RebindablePlaybackBackend
    private lateinit var playbackRuntime: PlaybackRuntime
    private lateinit var sleepTimerController: SleepTimerController
    private lateinit var playbackRecovery: PlaybackRecoveryController
    private lateinit var recoveryDiagnostics: RecoveryDiagnosticsStore
    private lateinit var compatibilityDiagnostics: CompatibilityDiagnosticsStore
    private lateinit var networkStateMonitor: NetworkStateMonitor
    private lateinit var networkRecovery: NetworkRecoveryCoordinator
    private lateinit var playbackHealth: PlaybackHealthStateMachine
    private lateinit var runtimeDiagnostics: RuntimeDiagnosticsStore
    private lateinit var deviceRuntimeMonitor: DeviceRuntimeMonitor
    private lateinit var playbackWakeLock: PlaybackWakeLockController
    private lateinit var commandRouter: PlayerCommandRouter
    private lateinit var playbackSession: PlaybackSessionCoordinator
    private lateinit var webViewLifecycle: WebViewLifecycleCoordinator
    private lateinit var devicePolicy: DeviceCompatibilityPolicy
    private lateinit var rendererCrashGuard: RendererCrashLoopGuard
    private lateinit var communitySegmentClient: CommunitySegmentClient
    private lateinit var videoShieldBridge: VideoShieldBridge
    private lateinit var startupWork: StartupWorkCoordinator
    private var activityForeground = false
    private var activityTearingDown = false
    private var startupRuntimeObserversReady = false
    private var initialResumeHandled = false

    private lateinit var errorOverlay: View
    private lateinit var errorTitle: TextView
    private lateinit var errorMessage: TextView

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var suppressShieldToggle = false
    private var lastPolicyFingerprint = ""
    private var currentSubscribed = false
    private var renderedSubscribedStyle: Boolean? = null
    private var currentFavorite = false
    private var currentQueued = false
    private var queueCountCache = 0
    private var resumeAppliedVideoId = ""
    private var pendingResumeRead: PlaybackReadKey? = null
    private var libraryFlagsRequest = 0L
    private var queueAdvanceRequest = 0L
    private var playerNavigationGeneration = 0L
    private val playbackEndGuard = PlaybackEndGuard()
    private val activityRecreationGate = ActivityRecreationGate()
    private var rendererRecreateRunnable: Runnable? = null
    private var browseRendererRehydrateRunnable: Runnable? = null
    private var playerRendererRehydrateRunnable: Runnable? = null
    private var playerRendererAutoRecoveryAllowed = false
    private var browseRendererAutoRecoveryAllowed = false
    private var lastRendererExitEvidenceAt = 0L
    private var lastRendererExitEvidenceDidCrash = false
    private var lastRendererExitEnabledSafeMode = false
    private var speedAppliedVideoId = ""
    private var communitySegmentRequestKey = ""
    private var networkOnline = true
    private var networkMetered = false
    private var networkLinkState = NetworkLinkState.OFFLINE
    private var shortsMemoryPressure = ShortsMemoryPressure.NORMAL
    private var shortsPowerConstrained = false
    private var shortsLowRamDevice = false
    private var shortsRuntimePolicyKey = ""
    private var rendererGone = false
    private var browseRendererGone = false
    private var browseRoute = YouTubeRoute.parse(ShieldPreferences.HOME_URL)
    private var playerRoute = YouTubeRoute.parse(ShieldPreferences.HOME_URL)
    // Logical browse target currently being loaded. This makes repeated bridge/WebView
    // callbacks for the same transition idempotent without suppressing a later user tap.
    private var pendingBrowseNavigationUrl = ""
    private var surfaceBeforePip: String? = null
    private var playingBeforePip = false
    private var pipPlaybackWanted = false
    private var pipEnterRequested = false
    private var browseWebViewPaused = false
    private var browseSurfaceSuppressed = false
    private var savedHomeScrollY = 0
    private var playerImagesEnabled = true
    private var browseImagesEnabled = true
    private var playerImageResumeTask: Runnable? = null
    private var browseImageResumeTask: Runnable? = null
    private var playerMediaTrimTask: Runnable? = null
    private var playerMediaRetentionMode = PlayerMediaRetentionMode.ACTIVE
    private var longSessionMaintenanceTask: Runnable? = null
    private var browseHistoryCompactionTask: Runnable? = null
    private var browseHistoryCompactionPressure = MemoryPressureTier.NORMAL
    private var rendererRecoveryBrowseUrl = ""
    private var rendererRecoveryBrowseScrollY = 0
    private var rendererRecoveryPlayerUrl = ""
    private var rendererRecoveryPlayerVideoId = ""
    private var rendererRecoveryPlayerPositionMs = 0L
    private var rendererRecoveryPlayerWasPlaying = false
    private var rendererRecoveryPlayerSurface = ""
    private var pendingPlayerRendererRestore = false
    private var pendingBrowseRecoveryScrollY = 0
    private var pendingBrowseRecoveryUrl = ""
    private var memoryPressureTier = MemoryPressureTier.NORMAL
    private var renderedMiniProgress = -1
    private var miniSeekTracking = false
    private var miniChromeActive = false
    private var browseChromeHidden = false
    private lateinit var feedChromeMotionPolicy: FeedChromeMotionPolicy


    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackScreenOn = PlaybackScreenOnController(window)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        setContentView(R.layout.activity_main)
        uiMetrics = UiMetrics(this)
        feedChromeMotionPolicy = FeedChromeMotionPolicy(
            thresholdPx = dp(28),
            topRevealPx = dp(8),
            deadbandPx = dp(2)
        )
        startupWork = StartupWorkCoordinator(
            root = findViewById(android.R.id.content),
            alive = { !isDestroyed && !isFinishing },
            foreground = { activityForeground }
        )
        SystemBarInsets.install(this) { isInPictureInPictureMode }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                OnBackInvokedCallback { handleBackNavigation() }
            )
        }

        preferences = ShieldPreferences(this)
        shortsLowRamDevice = runCatching {
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
        }.getOrDefault(false)
        browseSession = BrowseSessionCoordinator(preferences)
        browseHistory = BrowseHistoryCoordinator()
        restoreBrowseHistorySnapshot(savedInstanceState)
        rulePackManager = RulePackManager(this)
        filterEngine = FilterEngine(preferences, rulePackManager)
        browseFilterEngine = FilterEngine(preferences, rulePackManager)
        stats = ShieldStats(this)
        libraryStore = LibraryStore(this)
        searchHistoryStore = SearchHistoryStore(this)
        playbackSession = PlaybackSessionCoordinator(PlaybackSnapshotStore(this), PlaybackServicePublisher(this))
        recoveryDiagnostics = RecoveryDiagnosticsStore(this)
        compatibilityDiagnostics = CompatibilityDiagnosticsStore(this)
        runtimeDiagnostics = RuntimeDiagnosticsStore(this)
        devicePolicy = DeviceCompatibilityPolicy.detect()
        rendererCrashGuard = RendererCrashLoopGuard(this)
        communitySegmentClient = CommunitySegmentClient()
        if (rendererCrashGuard.isGuardActive()) {
            preferences.safeMode = true
            preferences.safeModeReason = "Renderer crash-loop protection is active; automatic playback reload recovery is temporarily paused."
        }
        compatibilityMonitor = CompatibilityMonitor(preferences, rulePackManager) { reason, rolledBack ->
            runOnUiThread {
                val localizedReason = LocalizedPresentation.safeModeReason(this, reason)
                val suffix = if (rolledBack) " ${getString(R.string.previous_rules_restored)}" else ""
                Toast.makeText(this, localizedReason.trimEnd('.') + "." + suffix, Toast.LENGTH_LONG).show()
                val channel = playbackRuntime.state.channel
                filterEngine.pageWhitelisted = channel.isNotBlank() && preferences.isChannelWhitelisted(channel)
                refreshFilterPolicies()
                lastPolicyFingerprint = policyFingerprint()
                webView.reload()
                refreshUi()
            }
        }
        lastPolicyFingerprint = policyFingerprint()

        webView = findViewById(R.id.webView)
        browseWebView = findViewById(R.id.browseWebView)
        homeRecommendations = HomeRecommendationCoordinator(
            context = this,
            preferences = preferences,
            store = libraryStore,
            searchHistory = searchHistoryStore,
            tasks = libraryTasks,
            webView = { if (::browseWebView.isInitialized && !browseRendererGone) browseWebView else null },
            route = { browseRoute },
            alive = { !isDestroyed && !isFinishing }
        )
        homePullRefreshLayout = findViewById(R.id.homePullRefresh)
        playerSurfaceController = PlayerSurfaceController(
            this,
            findViewById(R.id.playerSurface),
            findViewById(R.id.miniPlayerChrome)
        ) {
            // Re-enable deferred Watch-page imagery only after the FLIP transition has settled.
            // Decoder/video rendering keeps priority while the surface is physically moving.
            if (!isDestroyed && !isFinishing) syncPlayerResourcePolicy()
        }
        playerRecoveryVisual = PlayerRecoveryVisualController(
            overlay = findViewById(R.id.playerRecoveryVisual),
            thumbnail = findViewById(R.id.playerRecoveryThumbnail)
        )
        homePullRefreshLayout.canStartRefresh = {
            !browseRendererGone && !isInPictureInPictureMode &&
                browseWebView.visibility == View.VISIBLE &&
                browseRoute.destination == YouTubeDestination.HOME &&
                !playerSurfaceController.expanded
        }
        homePullRefreshLayout.onRefresh = {
            if (!isDestroyed && !isFinishing && browseRoute.destination == YouTubeDestination.HOME) {
                if (browseRendererGone) {
                    rendererRecoveryBrowseUrl = YouTubeRoute.HOME_URL
                    rendererRecoveryBrowseScrollY = 0
                    browseRendererAutoRecoveryAllowed = true
                    scheduleBrowseRendererRehydrate(delayMs = 0L, reason = "pull to refresh")
                    homePullRefreshLayout.finishRefresh()
                } else {
                    // Rotate the local recommendations as well as asking YouTube for a fresh Home feed.
                    homeRecommendations.rotatePage()
                    if (YouTubeAdapter.isTrustedBridgeUrl(browseWebView.url)) browseWebView.reload()
                    else browseWebView.loadUrl(AppLanguage.youtubeUrl(this, YouTubeRoute.HOME_URL))
                }
            } else {
                homePullRefreshLayout.finishRefresh()
            }
        }
        findViewById<PlayerSurfaceLayout>(R.id.playerSurface).apply {
            reservedBottomInsetPx = uiMetrics.px(R.dimen.ui_bottom_nav_height)
            isMini = { playerSurfaceController.minimized && !isInPictureInPictureMode }
            onVideoTap = {
                if (!miniChromeController.revealForTap()) expandPlayer(animated = true)
            }
            onFlickExpand = { expandPlayer(animated = true) }
            onFlickDismiss = { closePlayer() }
            onInteractionStart = { if (::miniChromeController.isInitialized) miniChromeController.onInteractionStart() }
            onInteractionEnd = { if (::miniChromeController.isInitialized) miniChromeController.onInteractionEnd() }
        }
        errorOverlay = findViewById(R.id.errorOverlay)
        errorTitle = findViewById(R.id.errorTitle)
        errorMessage = findViewById(R.id.errorMessage)
        playerController = PlayerController(webView)
        playbackBackend = RebindablePlaybackBackend(WebViewPlaybackBackend(playerController))
        playbackRuntime = PlaybackRuntime(
            session = playbackSession,
            sink = playbackBackend,
            onQueueNext = { playNextFromQueue(manual = true) },
            onStop = { clearSnapshot ->
                playbackSession.stop(clearSnapshot)
                if (::webViewLifecycle.isInitialized) webViewLifecycle.release("stop command")
            },
            beforeDispatch = { command -> wakePlayerRendererForCommand(command) },
            afterDispatch = { command -> schedulePlayerMediaRetentionAfterCommand(command) }
        ).also { it.syncControls(preferences.playbackSpeed, preferences.autoRepeat) }
        playbackHealth = PlaybackHealthStateMachine { snapshot ->
            runOnUiThread {
                renderPlaybackHealth(snapshot)
                refreshUi()
            }
        }
        playbackRecovery = PlaybackRecoveryController(
            enabled = { preferences.playbackRecovery && networkOnline && !rendererCrashGuard.isGuardActive() },
            onRecover = { reason, attempt ->
                recoveryDiagnostics.recordRecovery(reason, attempt)
                playbackHealth.recoveryStarted(reason, attempt)
                if (!rendererGone && !isFinishing && networkOnline && YouTubeAdapter.isTrustedBridgeUrl(currentPlayerUrl())) {
                    Toast.makeText(this, getString(R.string.recovery_restore,reason), Toast.LENGTH_SHORT).show()
                    webView.reload()
                }
            },
            onExhausted = { reason ->
                recoveryDiagnostics.recordExhausted(reason)
                playbackHealth.recoveryExhausted(reason)
            },
            recoveryDelayPaddingMs = devicePolicy.recoveryGraceMs
        )
        networkRecovery = NetworkRecoveryCoordinator(
            enabled = {
                preferences.playbackRecovery && networkOnline && activityForeground &&
                    !rendererCrashGuard.isGuardActive() && !rendererGone
            },
            checkpointProvider = { buildNetworkRecoveryCheckpoint() },
            onSoftRecover = { checkpoint -> applyNetworkRecoveryCheckpoint(checkpoint) },
            onEscalate = { _, attempt ->
                playbackRecovery.requestRecovery(
                    reason = "Network transition did not restore playback",
                    delayMs = if (attempt <= 1) 250L else 1_000L
                )
            }
        )
        networkStateMonitor = NetworkStateMonitor(this) { state ->
            runOnUiThread { onNetworkChanged(state) }
        }
        networkLinkState = networkStateMonitor.currentState()
        networkOnline = networkLinkState.online
        networkMetered = networkLinkState.metered
        playbackWakeLock = PlaybackWakeLockController(this) { held, reason ->
            runtimeDiagnostics.recordWakeLock(held, reason)
        }
        webViewLifecycle = WebViewLifecycleCoordinator(
            webView = webView,
            preferences = preferences,
            runtimeDiagnostics = runtimeDiagnostics,
            wakeLock = playbackWakeLock,
            devicePolicy = devicePolicy,
            sessionState = { playbackRuntime.state },
            isInPictureInPicture = { isInPictureInPictureMode },
            isRendererGone = { rendererGone },
            rendererActive = { playerWebViewConfigured && !rendererGone }
        )
        deviceRuntimeMonitor = DeviceRuntimeMonitor(this) { state, reason ->
            runOnUiThread {
                val constrained = state.powerSaveMode || state.deviceIdleMode
                if (shortsPowerConstrained != constrained) {
                    shortsPowerConstrained = constrained
                    // Do not re-parse the full policy on a power-state flip. Update the active
                    // runtimes with a tiny command and rebuild cached source only for a future page.
                    invalidatePolicyScriptCache()
                    syncPowerConstrainedPolicy()
                }
                webViewLifecycle.onDeviceRuntimeChanged(state, reason)
                syncShortsRuntimePolicy(forceTrim = false)
            }
        }
        val initialRuntimeState = deviceRuntimeMonitor.currentState()
        shortsPowerConstrained = initialRuntimeState.powerSaveMode || initialRuntimeState.deviceIdleMode
        webViewLifecycle.onDeviceRuntimeChanged(initialRuntimeState, "initial-state")
        sleepTimerController = SleepTimerController(
            preferences = preferences,
            onExpired = {
                playbackRuntime.dispatch(PlaybackCommand.Pause)
                Toast.makeText(this, getString(R.string.ui_sleep_timer_finished), Toast.LENGTH_SHORT).show()
            },
            onTick = { updateSleepTimerChrome() }
        )
        playbackInactivity = PlaybackInactivityController(
            activity = this,
            preferences = preferences,
            isPlaybackActive = {
                val foreground = !::webViewLifecycle.isInitialized || webViewLifecycle.foreground
                val shortsActive = ::browseWebView.isInitialized && browseWebView.visibility == View.VISIBLE &&
                    browseRoute.destination == YouTubeDestination.SHORTS
                foreground && (playbackRuntime.state.playing || shortsActive)
            },
            pausePlayback = {
                if (::playbackRuntime.isInitialized) playbackRuntime.dispatch(PlaybackCommand.Pause)
                if (::browseWebView.isInitialized && browseRoute.destination == YouTubeDestination.SHORTS) {
                    browseWebView.evaluateJavascript(
                        "document.querySelectorAll('video').forEach(v=>{try{v.pause()}catch(_){}})",
                        null
                    )
                }
            },
            releaseScreenOn = {
                if (::playbackScreenOn.isInitialized) playbackScreenOn.update(false)
                if (::playbackWakeLock.isInitialized) playbackWakeLock.release("inactivity timeout")
                if (::webView.isInitialized && !rendererGone) webView.keepScreenOn = false
                if (::browseWebView.isInitialized) browseWebView.keepScreenOn = false
                window.decorView.keepScreenOn = false
            }
        )
        urlInput = findViewById(R.id.urlInput)
        saveVideo = SaveVideoController(this)
        searchSuggestions = SearchSuggestionsController(
            input = urlInput,
            container = findViewById(R.id.browserContainer),
            canUseNetwork = { networkOnline }
        ) { query ->
            urlInput.setText(query)
            navigateFromAddressBar()
        }
        filterToggle = findViewById(R.id.filterToggle)
        statusText = findViewById(R.id.statusText)
        channelButton = findViewById(R.id.channelButton)
        subscribeButton = findViewById(R.id.subscribeButton)
        favoriteButton = findViewById(R.id.favoriteButton)
        queueButton = findViewById(R.id.queueButton)
        speedButton = findViewById(R.id.speedButton)
        qualityButton = findViewById(R.id.qualityButton)
        playbackMoreButton = findViewById(R.id.playbackMoreButton)
        autoNextButton = findViewById(R.id.autoNextButton)
        repeatButton = findViewById(R.id.repeatButton)
        sleepButton = findViewById(R.id.sleepButton)
        miniPlayerTitle = findViewById(R.id.miniPlayerTitle)
        miniPlayerChannel = findViewById(R.id.miniPlayerChannel)
        miniPlayPauseButton = findViewById(R.id.miniPlayPauseButton)
        miniPlayPauseButton.setIconTint(Color.WHITE)
        val miniCloseButton = findViewById<IconButton>(R.id.miniCloseButton).also { it.setIconTint(Color.WHITE) }
        miniPlayerProgress = findViewById(R.id.miniPlayerProgress)
        miniChromeController = MiniPlayerChromeController(
            controls = listOf(miniPlayPauseButton, miniCloseButton, miniPlayerProgress),
            beforeReveal = { updateMiniProgress(playbackRuntime.state, force = true) }
        )
        configureMiniPlayerSeekBar()
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        searchFilterRail = findViewById(R.id.searchFilterRail)
        searchFilterAll = findViewById(R.id.searchFilterAll)
        searchFilterVideos = findViewById(R.id.searchFilterVideos)
        searchFilterShorts = findViewById(R.id.searchFilterShorts)
        searchFilterChannels = findViewById(R.id.searchFilterChannels)
        searchFilterPlaylists = findViewById(R.id.searchFilterPlaylists)
        searchFilterMore = findViewById(R.id.searchFilterMore)
        playbackStatusRow = findViewById(R.id.playbackStatusRow)
        playbackActionStrip = findViewById(R.id.playbackActionStrip)
        navHomeButton = findViewById(R.id.navHomeButton)
        navShortsButton = findViewById(R.id.navShieldButton)
        navSubscriptionsButton = findViewById(R.id.navSubscriptionsButton)
        navLibraryButton = findViewById(R.id.navLibraryButton)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        applyNativeTheme()
        playbackHealth.setOnline(networkOnline, false)
        fullscreenContainer.gesturesEnabled = preferences.fullscreenGestures
        fullscreenContainer.sensitivity = preferences.gestureSensitivity
        fullscreenContainer.doubleTapSeekSeconds = preferences.doubleTapSeekSeconds
        fullscreenContainer.bindFeedback(findViewById(R.id.fullscreenGestureFeedback))
        fullscreenContainer.callback = object : FullscreenGestureLayout.Callback {
            override fun exitFullscreen() {
                animateFullscreenMinimize()
            }
            override fun previewExit(distancePx: Float) {
                previewFullscreenMinimize(distancePx)
            }
            override fun cancelExitPreview() {
                cancelFullscreenMinimizePreview()
            }
            override fun currentPositionMs(): Long = playbackRuntime.estimatedPositionMs()
            override fun currentDurationMs(): Long = playbackRuntime.state.durationMs
            override fun seekToMs(positionMs: Long) {
                ++playerNavigationGeneration
                playbackRuntime.overridePosition(positionMs)
                playbackRuntime.dispatch(PlaybackCommand.SeekTo(positionMs))
            }
        }

        // Browse is the only visible renderer on a normal cold launch. Keep the hidden Watch
        // WebView unconfigured until a deep link/session restore/user tap actually needs it.
        configureBrowseWebView()
        bindUi()
        commandRouter = PlayerCommandRouter(this) { command -> handlePlaybackCommand(command) }
        commandRouter.start()
        sleepTimerController.restore()

        val incoming = extractIncomingUrl(intent)
        val sameLanguage = savedInstanceState?.getString("app_language_tag") == AppLanguage.tag(this)
        val browseRendererRecoveryRequested = savedInstanceState?.getBoolean(STATE_BROWSE_RENDERER_RECOVERY, false) == true
        val playerRendererRecoveryRequested = savedInstanceState?.getBoolean(STATE_PLAYER_RENDERER_RECOVERY, false) == true
        surfaceBeforePip = savedInstanceState?.getString(STATE_SURFACE_BEFORE_PIP)
        pipPlaybackWanted = savedInstanceState?.getBoolean(STATE_PIP_PLAYBACK_WANTED, false) == true
        val savedBrowseUrl = savedInstanceState?.getString(STATE_BROWSE_URL)
        val savedBrowseRoute = YouTubeRoute.parse(savedBrowseUrl)
        pendingBrowseRecoveryScrollY = savedInstanceState?.getInt(STATE_BROWSE_RECOVERY_SCROLL_Y, 0)?.coerceAtLeast(0) ?: 0
        pendingBrowseRecoveryUrl = savedInstanceState?.getString(STATE_BROWSE_RECOVERY_URL).orEmpty()
            .takeIf { YouTubeAdapter.isTrustedBridgeUrl(it) }
            ?: savedBrowseUrl.orEmpty()
        // Rich WebView state contains the complete Chromium back/forward list. Restore it only
        // when the previous session stayed within the bounded history policy and no renderer died.
        val restoredBrowse = if (savedInstanceState != null && sameLanguage && !browseRendererRecoveryRequested &&
            savedBrowseRoute.destination != YouTubeDestination.SHORTS) {
            try {
                savedInstanceState.getBundle(STATE_BROWSE_WEBVIEW)?.let { browseWebView.restoreState(it) != null } == true
            } catch (_: Exception) { false }
        } else false
        val savedPlayerUrl = savedInstanceState?.getString(STATE_PLAYER_URL)
        if (playerRendererRecoveryRequested && incoming == null) {
            ensurePlayerWebViewConfigured("renderer state restore")
            rendererRecoveryPlayerUrl = savedPlayerUrl.orEmpty()
            rendererRecoveryPlayerVideoId = savedInstanceState?.getString(STATE_PLAYER_RECOVERY_VIDEO_ID).orEmpty()
            rendererRecoveryPlayerPositionMs = savedInstanceState?.getLong(STATE_PLAYER_RECOVERY_POSITION_MS, 0L)?.coerceAtLeast(0L) ?: 0L
            rendererRecoveryPlayerWasPlaying = savedInstanceState?.getBoolean(STATE_PLAYER_RECOVERY_PLAYING, false) == true
            rendererRecoveryPlayerSurface = savedInstanceState?.getString(STATE_PLAYER_SURFACE).orEmpty()
            pendingPlayerRendererRestore = rendererRecoveryPlayerVideoId.isNotBlank() || rendererRecoveryPlayerPositionMs > 0L
            if (pendingPlayerRendererRestore) {
                playbackBackend.beginRecovery(
                    PlaybackRecoveryHandoff(
                        playing = rendererRecoveryPlayerWasPlaying,
                        positionMs = rendererRecoveryPlayerPositionMs,
                        playbackRate = playbackRuntime.state.playbackRate,
                        repeatEnabled = playbackRuntime.state.repeatEnabled
                    )
                )
                // onCreate already owns a fresh PlaybackWebView. Treat it as the replacement host
                // but keep commands gated until its bridge reports the expected video.
                playbackBackend.rebind(WebViewPlaybackBackend(playerController), ready = false)
            }
        }
        val savedPlayerRoute = YouTubeRoute.parse(savedPlayerUrl)
        // v0.1.47 and older could serialize an empty hidden Watch WebView. Ignore that stale
        // bundle unless the saved URL is an actual playback route, otherwise a browse-only
        // rotation would defeat lazy player bootstrap on the next launch.
        val playerStateBundle = savedInstanceState?.getBundle(STATE_PLAYER_WEBVIEW)
            ?.takeIf { savedPlayerRoute.isNativePlayback }
        if (playerStateBundle != null && sameLanguage && !playerRendererRecoveryRequested && !rendererGone) {
            ensurePlayerWebViewConfigured("WebView state restore")
        }
        val restoredPlayer = if (playerStateBundle != null && sameLanguage && !playerRendererRecoveryRequested && !rendererGone) {
            try { webView.restoreState(playerStateBundle) != null } catch (_: Exception) { false }
        } else false
        if (savedInstanceState != null) {
            runtimeDiagnostics.recordRestore("browse WebView instance state", restoredBrowse)
            runtimeDiagnostics.recordRestore("player WebView instance state", restoredPlayer)
            // If Chromium history itself was intentionally not restored, the bounded native ring
            // becomes authoritative. Otherwise a newly loaded WebView entry could make goBack()
            // point in the opposite direction from the recovered logical history.
            if (!restoredBrowse && ::browseHistory.isInitialized && browseHistory.size() > 1) {
                browseHistory.markCompacted()
            }
        }
        if (!restoredBrowse) {
            val restoreBrowseUrl = pendingBrowseRecoveryUrl.takeIf { YouTubeAdapter.isTrustedBridgeUrl(it) }
                ?: savedBrowseUrl?.takeIf { YouTubeAdapter.isTrustedBridgeUrl(it) }
                ?: preferences.lastBrowseUrl
            browseWebView.loadUrl(AppLanguage.youtubeUrl(this, restoreBrowseUrl))
        }

        if (restoredPlayer) {
            playerSurfaceController.restore(savedInstanceState?.getString(STATE_PLAYER_SURFACE))
            playerRoute = YouTubeRoute.parse(webView.url)
        } else {
            val savedPlaybackTarget = savedPlayerUrl
                ?.takeIf { YouTubeAdapter.isTrustedBridgeUrl(it) && YouTubeRoute.parse(it).isNativePlayback }
            val playbackRestore = if (incoming == null && savedPlaybackTarget == null) {
                playbackRuntime.recoverableUrl(preferences.resumePlayback, RESTORE_SNAPSHOT_MAX_AGE_MS)
            } else null
            if (playbackRestore != null) runtimeDiagnostics.recordRestore("playback snapshot", true)
            val initialTarget = incoming ?: savedPlaybackTarget ?: playbackRestore
            if (initialTarget != null) {
                navigateClient(initialTarget, expandPlayback = true)
                if (incoming == null && savedPlaybackTarget != null) {
                    playerSurfaceController.restore(savedInstanceState?.getString(STATE_PLAYER_SURFACE))
                    syncBrowseWebViewActivity()
                }
            } else {
                playerSurfaceController.hide()
            }
        }
        if (incoming != null && restoredPlayer) navigateClient(incoming, expandPlayback = true)
        refreshUi()
        scheduleDeferredStartup(playerSurfaceController.visible)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIncomingUrl(intent)?.let { navigateClient(it, expandPlayback = true) }
    }

    private fun ensurePlayerWebViewConfigured(reason: String = "playback requested") {
        if (playerWebViewConfigured || rendererGone || isDestroyed || isFinishing) return
        configureWebView()
        // The lifecycle coordinator was created before first-frame staging. Rebinding the same
        // now-configured host activates its foreground/background policy without recreating it.
        if (::webViewLifecycle.isInitialized) webViewLifecycle.rebindWebView(webView, "player bootstrap")
        if (::runtimeDiagnostics.isInitialized) {
            runtimeDiagnostics.recordWebViewLifecycle(false, "player bootstrap • $reason")
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun configureWebView() {
        if (playerWebViewConfigured) return
        // A freshly configured/rebound renderer always starts in active retention mode. Native
        // pressure policy will re-apply lean/cold state after the bridge is ready if still needed.
        cancelPlayerMediaTrim()
        playerMediaRetentionMode = PlayerMediaRetentionMode.ACTIVE
        // Mark configured before callbacks are attached so synchronous re-entry stays idempotent.
        playerWebViewConfigured = true
        installEarlyPlayerDocumentScript(sharedEarlyScriptPolicy())
        (webView as PlaybackWebView).apply {
            canSwipeMinimize = {
                playerSurfaceController.expanded &&
                    customView == null && !isInPictureInPictureMode && playerRoute.destination == YouTubeDestination.WATCH
            }
            onSwipeMinimize = { minimizePlayer() }
            onMinimizeDrag = { playerSurfaceController.previewMinimize(it) }
            onMinimizeCancel = { playerSurfaceController.cancelMinimizePreview(animated = true) }
        }
        (webView as PlaybackWebView).keepActiveWhenHidden = {
            preferences.backgroundControls && preferences.screenOffPlayback &&
                playbackRuntime.state.hasSession && !isFinishing && !rendererGone
        }
        webView.setBackgroundColor(Color.BLACK)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
            userAgentString = userAgentString.replace("; wv", "")
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        videoShieldBridge = VideoShieldBridge(
                originAllowed = { isTrustedBridgeOrigin() },
                onPageAdsHidden = { count -> stats.pageAdsHidden(count) },
                onAdSkipped = { stats.adSkipped() },
                onSegmentSkipped = { category, durationMs ->
                    stats.segmentSkipped()
                    runOnUiThread {
                        if (::webViewLifecycle.isInitialized && webViewLifecycle.foreground) {
                            val seconds = (durationMs / 1000L).coerceAtLeast(1L)
                            Toast.makeText(this, getString(R.string.segment_skipped,segmentCategoryLabel(category),seconds), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onPlaybackState = { playing, buffering, title, channel, channelUrl, videoId, positionMs, durationMs ->
                    onPlaybackState(playing, buffering, title, channel, channelUrl, videoId, positionMs, durationMs)
                },
                onPlaybackEnded = { videoId -> onPlaybackEnded(videoId) },
                onDownloadRequested = { showCurrentDownload() },
                onPlayerOptionsRequested = { showPlaybackMoreSheet() },
                onQualitySelected = { quality -> saveManualQuality(quality); refreshUi() },
                onRepeatSelected = { enabled ->
                    // YouTube's in-player Repeat switch is a first-class control too.
                    // Persist it immediately so reopening the menu/activity reflects
                    // the same state and native queue advancement cannot race it.
                    if (preferences.autoRepeat != enabled) preferences.autoRepeat = enabled
                    playbackRuntime.dispatch(PlaybackCommand.SetRepeat(enabled))
                    refreshUi()
                },
                onPlaybackRateSelected = { selectedRate ->
                    val safeRate = selectedRate.coerceIn(0.25f, 4.0f)
                    if (kotlin.math.abs(preferences.playbackSpeed - safeRate) > 0.01f) {
                        preferences.playbackSpeed = safeRate
                    }
                    speedAppliedVideoId = playbackRuntime.state.videoId
                    // Run after YouTube's own menu handler has settled. This makes the
                    // website menu and the app control converge on the same media rate.
                    webView.postDelayed({
                        if (!isDestroyed && !isFinishing &&
                            kotlin.math.abs(preferences.playbackSpeed - safeRate) <= 0.01f) {
                            playbackRuntime.dispatch(PlaybackCommand.SetRate(safeRate))
                        }
                    }, 180L)
                    refreshUi()
                },
                onCompatibilityReport = { playerFound, videoFound, scriptErrors, ruleVersion ->
                    val activeRuleVersion = rulePackManager.active().ruleVersion
                    compatibilityDiagnostics.recordReport(
                        currentPlayerUrl(),
                        playerFound,
                        videoFound,
                        scriptErrors,
                        ruleVersion,
                        activeRuleVersion
                    )
                    compatibilityMonitor.onReport(currentPlayerUrl(), playerFound, videoFound, scriptErrors, ruleVersion)
                }
            )
        webView.addJavascriptInterface(videoShieldBridge, "VideoShieldBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                fullscreenContainer.gesturesEnabled = preferences.fullscreenGestures
                fullscreenContainer.sensitivity = preferences.gestureSensitivity
                fullscreenContainer.doubleTapSeekSeconds = preferences.doubleTapSeekSeconds
                fullscreenContainer.addView(
                    view,
                    0,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                fullscreenContainer.animate().cancel()
                fullscreenContainer.translationX = 0f
                fullscreenContainer.translationY = 0f
                fullscreenContainer.scaleX = 1f
                fullscreenContainer.scaleY = 1f
                fullscreenContainer.alpha = 1f
                fullscreenContainer.visibility = View.VISIBLE
                webView.visibility = View.GONE
                topBar.visibility = View.GONE
                bottomBar.visibility = View.GONE
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            override fun onHideCustomView() = exitFullscreen()
        }

        webView.webViewClient = ShieldWebViewClient(
            filterEngine = filterEngine,
            scriptProvider = {
                playerBasePolicyScript() +
                    (if (isInPictureInPictureMode) "\n" + ClientSurfaceScript.pip(true, pipPlaybackWanted) else "") +
                    (if (playerSurfaceController.minimized) "\n" + ClientSurfaceScript.mini(true, playbackRuntime.state.playing) else "")
            },
            onBlocked = { stats.networkBlocked() },
            onUrlChanged = { url ->
                runOnUiThread {
                    preferences.lastUrl = url
                    playerRoute = YouTubeRoute.parse(url)
                    updateBrowserChromeVisibility(url)
                    refreshUi()
                }
            },
            onNavigationStarted = { url ->
                ++playerNavigationGeneration
                playbackEndGuard.reset()
                playerRoute = YouTubeRoute.parse(url)
                if (::networkRecovery.isInitialized) networkRecovery.onNavigationStarted(playerRoute.videoId)
                playbackRuntime.onNavigationStarted(playerRoute.videoId)
                // Keep background controls continuous while Chromium replaces an actively
                // playing Watch document. The session is held in BUFFERING until the bridge
                // confirms the new media element instead of publishing a synthetic Pause.
                if (playbackRuntime.state.hasSession) playbackRuntime.publish(preferences.backgroundControls)
                currentSubscribed = false
                currentFavorite = false
                currentQueued = false
                resumeAppliedVideoId = ""
                speedAppliedVideoId = ""
                communitySegmentRequestKey = ""
                playbackBackend.clearCommunitySegments()
                filterEngine.pageWhitelisted = false
                compatibilityMonitor.onNavigationStarted(url)
                playbackRecovery.onNavigationStarted()
                playbackHealth.navigationStarted()
                // onPageStarted is immediately followed by the URL callback for main-frame
                // navigation. Let that callback render once instead of invalidating native
                // chrome twice for the same transition.
            },
            onPageReady = { url ->
                runOnUiThread { playbackHealth.pageReady(YouTubeRoute.parse(url).isPlayback) }
            },
            onMainFrameError = { message ->
                runOnUiThread {
                    val recoverable = networkOnline && preferences.playbackRecovery &&
                        playbackRuntime.state.hasSession && YouTubeAdapter.isTrustedBridgeUrl(webView.url)
                    playbackHealth.mainFrameError(message, recoverable)
                    if (recoverable) {
                        playbackRecovery.onMainFrameError(message, true)
                    } else if (!networkOnline) {
                        statusText.text = getString(R.string.ui_offline_waiting_for_network)
                    } else if (message.isNotBlank()) {
                        statusText.text = getString(R.string.page_error,message)
                    }
                }
            },
            onNavigationBlocked = { target, reason ->
                runtimeDiagnostics.recordBlockedNavigation(target, reason)
            },
            onRendererGone = { exit ->
                runOnUiThread {
                    val session = playbackRuntime.state
                    rendererRecoveryPlayerUrl = exit.url.takeIf { YouTubeAdapter.isTrustedBridgeUrl(it) }
                        ?: playerRoute.url
                    rendererRecoveryPlayerVideoId = session.videoId.ifBlank { playerRoute.videoId }
                    rendererRecoveryPlayerPositionMs = playbackRuntime.estimatedPositionMs().coerceAtLeast(0L)
                    rendererRecoveryPlayerWasPlaying = session.playing
                    rendererRecoveryPlayerSurface = playerSurfaceController.state.name
                    pendingPlayerRendererRestore = session.hasSession || playerSurfaceController.visible
                    showPlayerRecoveryVisualIfNeeded()

                    // Break every Java -> dead renderer path immediately. PlaybackRuntime keeps a
                    // stable rebindable sink; while the replacement renderer warms, transport,
                    // seek, speed and repeat commands are coalesced in native state instead of
                    // being dropped or sent into the dead WebView.
                    runCatching { earlyPlayerScript?.remove() }
                    earlyPlayerScript = null
                    playerWebViewConfigured = false
                    cancelPlayerMediaTrim()
                    playerMediaRetentionMode = PlayerMediaRetentionMode.ACTIVE
                    playerImageResumeTask?.let(window.decorView::removeCallbacks)
                    playerImageResumeTask = null
                    if (::videoShieldBridge.isInitialized) videoShieldBridge.close()
                    if (::playerController.isInitialized) playerController.release()
                    if (::playbackBackend.isInitialized) {
                        playbackBackend.beginRecovery(
                            PlaybackRecoveryHandoff(
                                playing = rendererRecoveryPlayerWasPlaying,
                                positionMs = rendererRecoveryPlayerPositionMs,
                                playbackRate = session.playbackRate,
                                repeatEnabled = session.repeatEnabled
                            )
                        )
                    }
                    if (::webViewLifecycle.isInitialized) webViewLifecycle.onRendererGone("player renderer gone")
                }
                handleRendererGone(source = "player", didCrash = exit.didCrash, affectsPlayer = true, delayMs = 250L)
            },
            navigationInterceptor = { target ->
                val normalized = YouTubeAdapter.normalizeIncomingUrl(target) ?: target
                val route = YouTubeRoute.parse(normalized)
                if (YouTubeAdapter.isTrustedBridgeUrl(normalized) && !route.isNativePlayback) {
                    runOnUiThread { showBrowseDestination(normalized) }
                    true
                } else {
                    false
                }
            }
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureBrowseWebView() {
        installEarlyBrowseDocumentScript()
        browseWebView.setBackgroundColor(AppTheme.background(this))
        browseWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = true
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
            userAgentString = userAgentString.replace("; wv", "")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // When the browse surface is hidden behind the dedicated player, allow
                // Chromium to reclaim its renderer before the whole app is pressured.
                browseWebView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
            } catch (_: Exception) {}
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(browseWebView, true)
        }
        homeRecommendations.installBridge(browseWebView)
        browseNavigationBridge?.close()
        browseNavigationBridge = BrowseNavigationBridge(
            navigate = { target ->
                runOnUiThread {
                    if (!isDestroyed && !isFinishing && YouTubeAdapter.isTrustedBridgeUrl(browseWebView.url)) {
                        promoteBrowsePlayback(target)
                    }
                }
            },
            shortsChromeChanged = { hidden ->
                runOnUiThread {
                    if (!isDestroyed && !isFinishing && browseRoute.destination == YouTubeDestination.SHORTS &&
                        YouTubeAdapter.isTrustedBridgeUrl(browseWebView.url)) {
                        setBrowseChromeHidden(hidden, animated = true)
                    }
                }
            }
        ).also { browseWebView.addJavascriptInterface(it, "VoTuibeNavigation") }
        browseWebView.webChromeClient = WebChromeClient()
        browseWebView.webViewClient = ShieldWebViewClient(
            filterEngine = browseFilterEngine,
            scriptProvider = { browseBasePolicyScript() },
            onBlocked = { stats.networkBlocked() },
            onUrlChanged = { url ->
                runOnUiThread {
                    val route = YouTubeRoute.parse(url)
                    if (route.isNativePlayback) {
                        promoteBrowsePlayback(url)
                        return@runOnUiThread
                    }
                    // Any committed browse URL completes the outstanding logical request.
                    pendingBrowseNavigationUrl = ""
                    val previousRoute = browseRoute
                    val previousDestination = previousRoute.destination
                    val previousUiKey = NavigationTransitionPolicy.browseUiKey(
                        previousDestination.name,
                        previousRoute.query,
                        if (previousDestination == YouTubeDestination.SEARCH) previousRoute.url else ""
                    )
                    browseRoute = route
                    if (::browseHistory.isInitialized) {
                        val historyUrl = logicalBrowseHistoryUrl(route, url)
                        if (route.destination == YouTubeDestination.SHORTS && previousDestination == YouTubeDestination.SHORTS) {
                            // Keep one Shorts slot in the bounded history while preserving the
                            // exact latest Short so Back can return to the user's last position.
                            browseHistory.replaceCurrent(historyUrl)
                        } else {
                            browseHistory.commit(historyUrl)
                        }
                    }
                    val chromeChanged = previousDestination != route.destination
                    val uiChanged = previousUiKey != NavigationTransitionPolicy.browseUiKey(
                        route.destination.name,
                        route.query,
                        if (route.destination == YouTubeDestination.SEARCH) route.url else ""
                    )
                    if (::playbackInactivity.isInitialized && chromeChanged) {
                        playbackInactivity.onPlaybackContextChanged()
                    }
                    persistBrowseUrl(url, route)
                    noteShortsTransition(route)
                    if (route.destination == YouTubeDestination.SHORTS && previousDestination != YouTubeDestination.SHORTS) {
                        syncShortsRuntimePolicy(forceTrim = false)
                    }
                    if (route.destination == YouTubeDestination.HOME) {
                        // Start local ranking as soon as the route is known, but hold the DOM write
                        // until Chromium has committed a visible frame. This overlaps local I/O with
                        // network loading without competing with the first content paint.
                        homeRecommendations.prepareForFirstPaint()
                    } else {
                        homeRecommendations.onDestinationChanged(route.destination)
                        if (previousDestination == YouTubeDestination.HOME) {
                            // Release the 24 local Home recommendation cards/thumbnails when
                            // entering Shorts or another surface; the SPA root itself persists.
                            browseWebView.evaluateJavascript(HomeRecommendationsScript.clear(), null)
                        }
                    }
                    // A Shorts swipe changes only the media id, not app chrome. Avoid a
                    // full native UI refresh and preference write for every vertical swipe.
                    if (chromeChanged) applyLongSessionMaintenance(longSessionResources.onBrowseDestinationChanged())
                    if (!urlInput.hasFocus() && (route.destination != YouTubeDestination.SHORTS || chromeChanged)) {
                        urlInput.setText("")
                        urlInput.hint = ClientChromePolicy.forRoute(
                            browseRoute,
                            fullscreen = false,
                            pictureInPicture = false
                        ).searchHint.let { if(it == "Search YouTube") getString(R.string.search_youtube) else it }
                    }
                    if (uiChanged) refreshUi()
                }
            },
            onNavigationStarted = { url ->
                val route = YouTubeRoute.parse(url)
                runOnUiThread {
                    cancelBrowseHistoryCompaction()
                    homeRecommendations.onNavigationStarted()
                    // Do not commit browseRoute or invalidate native chrome here. onPageStarted
                    // immediately reports the same URL through onUrlChanged; that callback owns
                    // the logical route commit so each transition renders once.
                    if (route.destination != YouTubeDestination.HOME) homePullRefreshLayout.finishRefresh()
                }
            },
            onPageCommitVisible = { committedUrl ->
                runOnUiThread {
                    homeRecommendations.onFirstVisualCommit(committedUrl)
                }
            },
            onPageReady = { readyUrl ->
                runOnUiThread {
                    homePullRefreshLayout.finishRefresh()
                    maybeRestoreBrowseAfterRendererRecovery(readyUrl)
                    maybeScheduleBrowseHistoryCompaction(readyUrl)
                    if (browseRoute.destination == YouTubeDestination.SHORTS) {
                        // A full document navigation recreates the JS world even if the native
                        // runtime profile did not change. Force one configure on the new page.
                        shortsRuntimePolicyKey = ""
                        syncShortsRuntimePolicy(forceTrim = false)
                    }
                    homeRecommendations.publishPreparedOrRefresh()
                }
            },
            onMainFrameError = { message ->
                runOnUiThread {
                    pendingBrowseNavigationUrl = ""
                    homePullRefreshLayout.finishRefresh()
                    if (message.isNotBlank() && !playerSurfaceController.expanded) {
                        statusText.text = getString(R.string.browse_error,message)
                    }
                }
            },
            onNavigationBlocked = { target, reason ->
                runtimeDiagnostics.recordBlockedNavigation(target, "browse: $reason")
            },
            onRendererGone = { exit ->
                runOnUiThread {
                    val inFlightBrowseTarget = pendingBrowseNavigationUrl
                    pendingBrowseNavigationUrl = ""
                    browseRendererGone = true
                    cancelBrowseHistoryCompaction()
                    browseImageResumeTask?.let(window.decorView::removeCallbacks)
                    browseImageResumeTask = null
                    runCatching { earlyBrowseScript?.remove() }
                    earlyBrowseScript = null
                    browseNavigationBridge?.close()
                    browseNavigationBridge = null
                    if (::homeRecommendations.isInitialized) homeRecommendations.onRendererGone()
                    val recoveringInFlightTarget = inFlightBrowseTarget.takeIf { YouTubeAdapter.isTrustedBridgeUrl(it) }
                    rendererRecoveryBrowseUrl = recoveringInFlightTarget
                        ?: exit.url.takeIf { YouTubeAdapter.isTrustedBridgeUrl(it) }
                        ?: browseRoute.url
                    rendererRecoveryBrowseScrollY = if (recoveringInFlightTarget != null) 0 else exit.scrollY.coerceAtLeast(0)
                    if (browseRoute.destination == YouTubeDestination.HOME && rendererRecoveryBrowseScrollY > 0) {
                        savedHomeScrollY = rendererRecoveryBrowseScrollY
                    }
                    // Preserve the current logical route + native scroll snapshot. Chromium's
                    // dead back/forward list is deliberately not resurrected after recovery.
                    persistBrowseUrl(rendererRecoveryBrowseUrl, YouTubeRoute.parse(rendererRecoveryBrowseUrl), immediate = true)
                }
                handleRendererGone(source = "browse", didCrash = exit.didCrash, affectsPlayer = false, delayMs = 180L)
            },
            navigationInterceptor = { target ->
                val normalized = YouTubeAdapter.normalizeIncomingUrl(target) ?: target
                val route = YouTubeRoute.parse(normalized)
                if (route.isNativePlayback) {
                    runOnUiThread { openPlayer(normalized, expand = true) }
                    true
                } else {
                    false
                }
            }
        )
    }

    private fun bindUi() {
        bindSearchFilterRail()
        // Keep the watch page quiet like the current YouTube app: only the primary actions
        // stay visible; speed/quality and the less frequent controls live in More.
        findViewById<IconButton>(R.id.minimizePlayerButton).setIcon(R.drawable.ic_ui_minimize)
        findViewById<Button>(R.id.sleepButton).setLeadingIcon(R.drawable.ic_ui_timer)
        findViewById<Button>(R.id.pipButton).setLeadingIcon(R.drawable.ic_ui_pip)
        findViewById<View>(R.id.brandContainer).setOnClickListener { showBrowseDestination(YouTubeRoute.HOME_URL) }
        findViewById<TextView>(R.id.appBrand).setOnClickListener { showBrowseDestination(YouTubeRoute.HOME_URL) }
        findViewById<Button>(R.id.backButton).setOnClickListener {
            if (urlInput.visibility == View.VISIBLE) exitSearchMode() else navigateBrowseBack()
        }
        findViewById<Button>(R.id.forwardButton).setOnClickListener { navigateBrowseForward() }
        findViewById<Button>(R.id.reloadButton).setOnClickListener {
            if (browseRendererGone) {
                rendererCrashGuard.clear()
                browseRendererAutoRecoveryAllowed = true
                scheduleBrowseRendererRehydrate(delayMs = 0L, reason = "manual retry")
            } else {
                browseWebView.reload()
            }
        }
        findViewById<Button>(R.id.goButton).setOnClickListener {
            if (urlInput.visibility != View.VISIBLE) enterSearchMode() else navigateFromAddressBar()
        }
        findViewById<Button>(R.id.notificationButton).setOnClickListener {
            Toast.makeText(this, getString(R.string.no_new_notifications), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.pipButton).setOnClickListener { enterPip() }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.subscribeButton).setOnClickListener { toggleCurrentSubscription() }
        findViewById<Button>(R.id.favoriteButton).setOnClickListener { toggleCurrentFavorite() }
        findViewById<Button>(R.id.favoriteButton).setOnLongClickListener(null)
        findViewById<Button>(R.id.queueButton).setOnClickListener { toggleCurrentQueue() }
        findViewById<Button>(R.id.speedButton).setOnClickListener { cyclePlaybackSpeed() }
        findViewById<Button>(R.id.qualityButton).setOnClickListener { showQualityDialog() }
        findViewById<IconButton>(R.id.playbackMoreButton).setOnClickListener { showPlaybackMoreSheet() }
        findViewById<Button>(R.id.repeatButton).setOnClickListener {
            val enabled = !preferences.autoRepeat
            preferences.autoRepeat = enabled
            playbackRuntime.dispatch(PlaybackCommand.SetRepeat(enabled))
            refreshUi()
            Toast.makeText(this, if (enabled) getString(R.string.ui_repeat_enabled) else getString(R.string.ui_repeat_disabled), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.sleepButton).setOnClickListener { showSleepTimerDialog() }
        findViewById<Button>(R.id.minimizePlayerButton).setOnClickListener { minimizePlayer() }
        findViewById<Button>(R.id.autoNextButton).setOnClickListener {
            preferences.autoAdvanceQueue = !preferences.autoAdvanceQueue
            // Queue auto-advance is native-only; do not re-evaluate the large WebView policy.
            refreshUi()
        }
        findViewById<Button>(R.id.miniExpandButton).setOnClickListener { expandPlayer(animated = true) }
        findViewById<Button>(R.id.miniPlayPauseButton).setOnClickListener {
            miniChromeController.onInteractionStart()
            playbackRuntime.dispatch(PlaybackCommand.Toggle)
            miniChromeController.onInteractionEnd()
        }
        findViewById<Button>(R.id.miniCloseButton).setOnClickListener { closePlayer() }
        findViewById<SwipeDismissLayout>(R.id.miniPlayerChrome).apply {
            // The modern floating card is dragged as one surface; a dedicated close button
            // replaces the old swipe-to-dismiss title strip.
            enabledForDismiss = false
            onDismiss = null
        }
        findViewById<TextView>(R.id.miniPlayerTitle).setOnClickListener { expandPlayer() }
        findViewById<TextView>(R.id.miniPlayerChannel).setOnClickListener { expandPlayer() }
        bindBrowseScrollListener()
        navHomeButton.setOnClickListener { showBrowseDestination(YouTubeRoute.HOME_URL) }
        navShortsButton.setOnClickListener { showBrowseDestination(YouTubeRoute.SHORTS_URL) }
        navSubscriptionsButton.setOnClickListener { showBrowseDestination(YouTubeRoute.SUBSCRIPTIONS_URL) }
        navLibraryButton.setOnClickListener { openLibrary(LibraryActivity.MODE_OVERVIEW) }
        findViewById<Button>(R.id.navCreateButton).setOnClickListener {
            quickActionSheet?.dismiss()
            quickActionSheet=ActionSheet.show(this,getString(R.string.app_name),listOf(
                ActionSheet.Action(getString(R.string.ui_for_you),getString(R.string.history_suggestions),R.drawable.ic_nav_home),
                ActionSheet.Action(getString(R.string.ui_queue),getString(R.string.next_videos),R.drawable.ic_ui_queue),
                ActionSheet.Action(getString(R.string.ui_watch_later),getString(R.string.ui_watch_later_videos),R.drawable.ic_ui_bookmark),
                ActionSheet.Action(getString(R.string.ui_downloads),getString(R.string.offline_videos),R.drawable.ic_ui_download),
                ActionSheet.Action("EQ",getString(R.string.offline_eq),R.drawable.ic_ui_eq),
                ActionSheet.Action(getString(R.string.ui_settings),getString(R.string.customize_experience),R.drawable.ic_ui_settings)
            )) { index ->
                when (index) {
                    0 -> openLibrary(LibraryActivity.MODE_FOR_YOU)
                    1 -> openLibrary(LibraryActivity.MODE_QUEUE)
                    2 -> openLibrary(LibraryActivity.MODE_FAVORITES)
                    3 -> startActivity(Intent(this,DownloadsActivity::class.java))
                    4 -> EqDialog.show(this)
                    else -> startActivity(Intent(this, SettingsActivity::class.java))
                }
            }
        }
        channelButton.setOnClickListener { toggleCurrentChannelAllowlist() }
        statusText.setOnClickListener {
            startActivity(Intent(this, ShieldDashboardActivity::class.java))
        }
        findViewById<Button>(R.id.errorRetryButton).setOnClickListener {
            recoveryDiagnostics.recordManualRetry()
            playbackRecovery.resetAttempts()
            if (rendererGone) {
                rendererCrashGuard.clear()
                playerRendererAutoRecoveryAllowed = true
                if (rendererRecoveryPlayerUrl.isBlank()) {
                    rendererRecoveryPlayerUrl = playerRoute.url
                }
                showPlayerRecoveryVisualIfNeeded()
                schedulePlayerRendererRehydrate(0L, "manual retry")
                return@setOnClickListener
            }
            playbackHealth.userRetry()
            if (networkOnline) webView.reload() else renderPlaybackHealth(playbackHealth.snapshot)
        }
        findViewById<Button>(R.id.errorHomeButton).setOnClickListener {
            playbackRecovery.resetAttempts()
            playbackHealth.reset()
            if (rendererGone) {
                rendererCrashGuard.clear()
                playerRendererAutoRecoveryAllowed = false
                pendingPlayerRendererRestore = false
                playbackBackend.cancelRecovery()
                if (::playerRecoveryVisual.isInitialized) playerRecoveryVisual.hide(animated = false)
                playbackRuntime.stop(clearSnapshot = true)
                playerRoute = YouTubeRoute.parse(ShieldPreferences.HOME_URL)
                playerSurfaceController.hide()
                syncBrowseWebViewActivity()
                showBrowseDestination(ShieldPreferences.HOME_URL, minimizePlayer = false)
                refreshUi()
            } else {
                closePlayer()
                showBrowseDestination(ShieldPreferences.HOME_URL, minimizePlayer = false)
            }
        }

        suppressShieldToggle = true
        filterToggle.isChecked = preferences.shieldEnabled
        suppressShieldToggle = false
        filterToggle.setOnCheckedChangeListener { _, checked ->
            if (suppressShieldToggle) return@setOnCheckedChangeListener
            if (preferences.shieldEnabled == checked) return@setOnCheckedChangeListener
            preferences.shieldEnabled = checked
            if (!checked) preferences.safeModeReason = ""
            refreshFilterPolicies()
            lastPolicyFingerprint = policyFingerprint()
            updateEarlyPlayerScripts()
            if (playerSurfaceController.visible && !rendererGone) webView.reload()
            if (!browseRendererGone) browseWebView.reload()
            refreshUi()
        }

        urlInput.setOnEditorActionListener { _, actionId, event ->
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO || enterPressed) {
                navigateFromAddressBar(); true
            } else false
        }
    }

    override fun onResume() {
        super.onResume()
        activityForeground = true
        if (!isInPictureInPictureMode) pipEnterRequested = false
        val initialStartupResume = !initialResumeHandled
        initialResumeHandled = true
        if (::startupWork.isInitialized) startupWork.onForeground()
        // A foreground resume is our recovery boundary after transient trim callbacks.
        // Components can rebuild disposable caches lazily from here.
        memoryPressureTier = MemoryPressureTier.NORMAL
        syncFeedMemoryPressure(MemoryPressureTier.NORMAL)
        val browseChromeCanReveal = ::playerSurfaceController.isInitialized &&
            !playerSurfaceController.expanded && !isInPictureInPictureMode && customView == null &&
            browseRoute.destination != YouTubeDestination.SHORTS
        if (::bottomBar.isInitialized && browseChromeCanReveal) {
            setBrowseChromeHidden(false, animated = false)
        }
        if (::feedChromeMotionPolicy.isInitialized) feedChromeMotionPolicy.reset()
        if (::searchSuggestions.isInitialized) searchSuggestions.resume()
        scheduleWarmResumeHomeRefresh(initialStartupResume)
        if (::webViewLifecycle.isInitialized) webViewLifecycle.onActivityResumed()
        cancelPlayerMediaTrim()
        showPlayerRecoveryVisualIfNeeded()
        if (rendererGone && playerRendererAutoRecoveryAllowed) {
            schedulePlayerRendererRehydrate(0L, "activity returned to foreground")
        }
        if (::playbackInactivity.isInitialized) playbackInactivity.onForeground()
        syncBrowseWebViewActivity()
        if (::rendererCrashGuard.isInitialized) rendererCrashGuard.markStable()
        if (::networkStateMonitor.isInitialized && startupRuntimeObserversReady) networkStateMonitor.start()
        if (::playbackRecovery.isInitialized) playbackRecovery.setActive(true)
        if (::networkRecovery.isInitialized) networkRecovery.onForeground()
        refreshShortsMemoryPressureFromSystem()
        syncShortsRuntimePolicy(forceTrim = false)
        if (::preferences.isInitialized) {
            if (!initialStartupResume) applyNativeTheme()
            if (::playbackInactivity.isInitialized) playbackInactivity.onPreferencesChanged()
            if (!initialStartupResume) updateBrowserChromeVisibility(currentPlayerUrl())
            if (::fullscreenContainer.isInitialized) {
                fullscreenContainer.gesturesEnabled = preferences.fullscreenGestures
                fullscreenContainer.sensitivity = preferences.gestureSensitivity
                fullscreenContainer.doubleTapSeekSeconds = preferences.doubleTapSeekSeconds
            }
            if (::libraryStore.isInitialized && !initialStartupResume) {
                readPlaybackLibraryState(includeResume = false)
            }
            if (!preferences.backgroundControls) playbackRuntime.stopService()
            webViewLifecycle.updateWakeLock("activity resumed")
            if (!initialStartupResume) {
                refreshFilterPolicies()
                val fingerprint = policyFingerprint()
                suppressShieldToggle = true
                filterToggle.isChecked = preferences.shieldEnabled
                suppressShieldToggle = false
                val channel = playbackRuntime.state.channel
                filterEngine.pageWhitelisted = channel.isNotBlank() && preferences.isChannelWhitelisted(channel)
                if (lastPolicyFingerprint.isNotEmpty() && fingerprint != lastPolicyFingerprint && ::webView.isInitialized) {
                    // Most preference changes are runtime DOM/player policy and do not require
                    // rebuilding two Chromium documents. Re-apply the cached policy in place;
                    // explicit shield enable/disable still reloads from its own UI handler.
                    lastPolicyFingerprint = fingerprint
                    compatibilityMonitor.reset()
                    applyPolicyAndRefresh()
                } else {
                    // Returning from another Activity used to parse/evaluate the full shield
                    // script on both WebViews even when absolutely nothing changed.
                    lastPolicyFingerprint = fingerprint
                    refreshUi()
                }
                // Settings may have changed while another Activity was on top.
                if (::playbackRuntime.isInitialized) {
                    playbackRuntime.syncControls(preferences.playbackSpeed, preferences.autoRepeat)
                    if (playerSurfaceController.visible) {
                        playbackRuntime.dispatch(PlaybackCommand.SetRepeat(preferences.autoRepeat))
                        playbackRuntime.dispatch(PlaybackCommand.SetRate(preferences.playbackSpeed))
                    }
                }
            }
            syncPlayerResourcePolicy()
            syncPlayerMediaRetention("activity resumed")
        }
    }

    private fun showPlayerRecoveryVisualIfNeeded() {
        if (!::playerRecoveryVisual.isInitialized || !::playerSurfaceController.isInitialized) return
        if (!pendingPlayerRendererRestore || !playerSurfaceController.visible) return
        if (!activityForeground && !isInPictureInPictureMode) return
        playerRecoveryVisual.show(rendererRecoveryPlayerVideoId.ifBlank { playerRoute.videoId })
    }

    /**
     * Home enrichment is useful after returning from another Activity, but it is not first-frame
     * work. Posting it one frame later avoids competing with WebView.onResume(), PiP exit and a
     * replacement playback renderer. Initial startup already owns its staged enrichment task.
     */
    private fun scheduleWarmResumeHomeRefresh(initialStartupResume: Boolean) {
        warmResumeTask?.let(window.decorView::removeCallbacks)
        warmResumeTask = null
        if (initialStartupResume && (!::startupWork.isInitialized || !startupWork.isFirstFrameReady)) return
        if (!activityForeground || browseRendererGone || rendererGone ||
            browseRoute.destination != YouTubeDestination.HOME ||
            playerSurfaceController.expanded || isInPictureInPictureMode || customView != null) return

        val task = Runnable {
            warmResumeTask = null
            if (!isDestroyed && !isFinishing && activityForeground && !browseRendererGone && !rendererGone &&
                browseRoute.destination == YouTubeDestination.HOME &&
                !playerSurfaceController.expanded && !isInPictureInPictureMode && customView == null) {
                refreshHomeRecommendations()
            }
        }
        warmResumeTask = task
        window.decorView.postOnAnimation(task)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::playbackInactivity.isInitialized) playbackInactivity.onUserInteraction()
    }

    override fun onPause() {
        activityForeground = false
        warmResumeTask?.let(window.decorView::removeCallbacks)
        warmResumeTask = null
        if (!isInPictureInPictureMode && !pipEnterRequested) {
            playerRendererRehydrateRunnable?.let(window.decorView::removeCallbacks)
            playerRendererRehydrateRunnable = null
        }
        if (::playbackInactivity.isInitialized) playbackInactivity.onBackground()
        flushPendingBrowseUrl()
        if (::stats.isInitialized) stats.flush()
        if (::playbackScreenOn.isInitialized) playbackScreenOn.update(false)
        if (::searchSuggestions.isInitialized) searchSuggestions.pause()
        window.decorView.removeCallbacks(uiRefreshFrame)
        uiRefreshPending = false
        cancelRendererImageResumes()
        setBrowseWebViewPaused(true)
        setBrowseImagesEnabled(false)
        if (::webViewLifecycle.isInitialized) webViewLifecycle.onActivityPaused()
        syncPlayerResourcePolicy()
        syncPlayerMediaRetention("activity background")
        if (::networkRecovery.isInitialized) networkRecovery.onBackground()
        if (::networkStateMonitor.isInitialized) networkStateMonitor.stop()
        if (::playbackRecovery.isInitialized) playbackRecovery.setActive(false)
        super.onPause()
    }

    private fun setBrowseWebViewPaused(paused: Boolean) {
        if (!::browseWebView.isInitialized || browseRendererGone || browseWebViewPaused == paused) return
        try {
            if (paused) browseWebView.onPause() else browseWebView.onResume()
            browseWebViewPaused = paused
        } catch (_: Exception) {}
    }

    private fun setBrowseSurfaceSuppressed(suppressed: Boolean) {
        if (!::browseWebView.isInitialized || browseSurfaceSuppressed == suppressed) return
        browseSurfaceSuppressed = suppressed
        if (suppressed) {
            // The expanded player completely covers the browse surface. Keep the SPA and
            // scroll position alive, but stop GPU compositing and release distant Shorts
            // media buffers before the dedicated player asks for its own decoder.
            if (browseRoute.destination == YouTubeDestination.HOME) {
                savedHomeScrollY = browseWebView.scrollY.coerceAtLeast(0)
            }
            if (browseRoute.destination == YouTubeDestination.SHORTS) {
                try { browseWebView.evaluateJavascript(ShortsResourceGuardScript.suspend(), null) } catch (_: Exception) {}
            } else {
                try { browseWebView.evaluateJavascript(BrowseResourceGuardScript.suspend(), null) } catch (_: Exception) {}
            }
            browseWebView.visibility = View.INVISIBLE
        } else {
            browseWebView.visibility = View.VISIBLE
            if (browseRoute.destination == YouTubeDestination.HOME && savedHomeScrollY > dp(8)) {
                // Usually Chromium preserves this already. Only repair the position when a
                // renderer/page transition reset it near the top while the player was open.
                browseWebView.post {
                    if (!isDestroyed && !isFinishing && !browseSurfaceSuppressed &&
                        browseRoute.destination == YouTubeDestination.HOME && browseWebView.scrollY <= dp(8)) {
                        browseWebView.scrollTo(browseWebView.scrollX, savedHomeScrollY)
                    }
                }
            }
            if (browseRoute.destination == YouTubeDestination.SHORTS) {
                browseWebView.post {
                    if (!isDestroyed && !isFinishing && !browseSurfaceSuppressed &&
                        browseRoute.destination == YouTubeDestination.SHORTS) {
                        try { browseWebView.evaluateJavascript(ShortsResourceGuardScript.resume(), null) } catch (_: Exception) {}
                    }
                }
            } else {
                browseWebView.post {
                    if (!isDestroyed && !isFinishing && !browseSurfaceSuppressed &&
                        browseRoute.destination != YouTubeDestination.SHORTS) {
                        try { browseWebView.evaluateJavascript(BrowseResourceGuardScript.resume(), null) } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun rendererLoadDecision(): RendererLoadDecision {
        val foreground = !::webViewLifecycle.isInitialized || webViewLifecycle.foreground
        val expanded = ::playerSurfaceController.isInitialized && playerSurfaceController.expanded
        val transitioning = ::playerSurfaceController.isInitialized && playerSurfaceController.transitioning
        val pip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode
        return RendererLoadPolicy.resolve(
            RendererLoadContext(
                foreground = foreground,
                playerExpanded = expanded,
                playerTransitioning = transitioning,
                pictureInPicture = pip,
                browseObscured = expanded || pip,
                powerConstrained = shortsPowerConstrained,
                memoryPressure = memoryPressureTier
            )
        )
    }

    private fun syncBrowseWebViewActivity() {
        if (!::browseWebView.isInitialized || !::playerSurfaceController.isInitialized) return
        val obscuredByPlayer = playerSurfaceController.expanded || isInPictureInPictureMode
        if (browseRendererGone) {
            // A browse-only renderer reclaim must not disturb an active Watch renderer. Keep the
            // dead browse surface absent while it is covered and rehydrate only when the user can
            // actually see it again.
            if (!obscuredByPlayer && activityForeground && browseRendererAutoRecoveryAllowed) {
                scheduleBrowseRendererRehydrate(delayMs = 0L, reason = "browse became visible")
            }
            syncPlayerResourcePolicy()
            return
        }
        val decision = rendererLoadDecision()
        setBrowseSurfaceSuppressed(obscuredByPlayer)
        val browseActive = decision.loadBrowseImages
        setBrowseWebViewPaused(!browseActive)
        setBrowseImagesEnabled(browseActive, decision.browseResumeDelayMs)
        syncPlayerResourcePolicy()
    }

    private fun cancelRendererImageResumes() {
        playerImageResumeTask?.let { window.decorView.removeCallbacks(it) }
        browseImageResumeTask?.let { window.decorView.removeCallbacks(it) }
        playerImageResumeTask = null
        browseImageResumeTask = null
    }

    private fun setBrowseImagesEnabled(enabled: Boolean, resumeDelayMs: Long = 0L) {
        if (!::browseWebView.isInitialized || browseRendererGone) return
        if (!enabled) {
            browseImageResumeTask?.let { window.decorView.removeCallbacks(it) }
            browseImageResumeTask = null
            if (!browseImagesEnabled) return
            browseImagesEnabled = false
            try { browseWebView.settings.loadsImagesAutomatically = false } catch (_: Exception) {}
            return
        }
        // Keep one pending resume instead of pushing it farther out on every state sync.
        if (browseImagesEnabled || browseImageResumeTask != null) return
        val resume = Runnable {
            browseImageResumeTask = null
            if (isDestroyed || isFinishing || !::browseWebView.isInitialized || browseRendererGone) return@Runnable
            val decision = rendererLoadDecision()
            if (!decision.loadBrowseImages) return@Runnable
            browseImagesEnabled = true
            try { browseWebView.settings.loadsImagesAutomatically = true } catch (_: Exception) {}
        }
        browseImageResumeTask = resume
        if (resumeDelayMs <= 0L) window.decorView.postOnAnimation(resume)
        else window.decorView.postDelayed(resume, resumeDelayMs)
    }

    /**
     * The dedicated watch WebView only needs page imagery while the full player surface is
     * visible. Disables are immediate. Re-enables are deliberately delayed until after the
     * first settled compositor frame so thumbnails/avatars do not compete with the video decoder
     * during mini/expanded/PiP transitions. Media streaming itself is never paused here.
     */
    private fun syncPlayerResourcePolicy() {
        if (!::webView.isInitialized || !playerWebViewConfigured || rendererGone || !::playerSurfaceController.isInitialized) return
        val decision = rendererLoadDecision()
        if (!decision.loadPlayerImages) {
            playerImageResumeTask?.let { window.decorView.removeCallbacks(it) }
            playerImageResumeTask = null
            if (!playerImagesEnabled) return
            playerImagesEnabled = false
            try { webView.settings.loadsImagesAutomatically = false } catch (_: Exception) {}
            return
        }
        // Keep one pending resume instead of pushing it farther out on every state sync.
        if (playerImagesEnabled || playerImageResumeTask != null) return
        val resume = Runnable {
            playerImageResumeTask = null
            if (isDestroyed || isFinishing || !::webView.isInitialized) return@Runnable
            if (!rendererLoadDecision().loadPlayerImages) return@Runnable
            playerImagesEnabled = true
            try { webView.settings.loadsImagesAutomatically = true } catch (_: Exception) {}
        }
        playerImageResumeTask = resume
        window.decorView.postDelayed(resume, decision.playerResumeDelayMs)
    }

    private fun playerMediaRetentionContext(): PlayerMediaRetentionContext {
        val session = if (::playbackRuntime.isInitialized) playbackRuntime.state else PlaybackSessionState()
        val visible = ::playerSurfaceController.isInitialized && playerSurfaceController.visible
        val minimized = ::playerSurfaceController.isInitialized && playerSurfaceController.minimized
        val pip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode
        return PlayerMediaRetentionContext(
            foreground = activityForeground,
            pictureInPicture = pip,
            surfaceVisible = visible,
            minimized = minimized,
            playing = session.playing,
            buffering = session.buffering,
            memoryPressure = memoryPressureTier
        )
    }

    private fun cancelPlayerMediaTrim() {
        playerMediaTrimTask?.let(window.decorView::removeCallbacks)
        playerMediaTrimTask = null
    }

    /**
     * Keep active playback untouched. Only paused Watch sessions can move to lean/cold retention,
     * and grace trimming is a one-shot task rather than a polling loop.
     */
    private fun syncPlayerMediaRetention(reason: String) {
        cancelPlayerMediaTrim()
        if (!::preferences.isInitialized || !preferences.memoryHardening || !::playbackRuntime.isInitialized ||
            !::playerSurfaceController.isInitialized || !playerWebViewConfigured || rendererGone) return
        val context = playerMediaRetentionContext()
        val decision = PlayerMediaRetentionPolicy.immediate(context)
        applyPlayerMediaRetention(decision, reason)
        val delay = decision.graceDelayMs ?: return
        if (delay <= 0L || decision.mode == PlayerMediaRetentionMode.ACTIVE) return
        val expectedVideoId = playbackRuntime.state.videoId
        val task = Runnable {
            playerMediaTrimTask = null
            if (isDestroyed || isFinishing || rendererGone || !playerWebViewConfigured ||
                playbackRuntime.state.videoId != expectedVideoId) return@Runnable
            val current = playerMediaRetentionContext()
            val settled = PlayerMediaRetentionPolicy.afterGrace(current)
            applyPlayerMediaRetention(settled, "$reason • grace elapsed")
        }
        playerMediaTrimTask = task
        window.decorView.postDelayed(task, delay)
    }

    private fun applyPlayerMediaRetention(decision: PlayerMediaRetentionDecision, reason: String) {
        if (!::webView.isInitialized || !playerWebViewConfigured || rendererGone) return
        val modeChanged = playerMediaRetentionMode != decision.mode
        playerMediaRetentionMode = decision.mode
        if (modeChanged || decision.compactSession) {
            try {
                webView.evaluateJavascript(
                    PlayerMediaResourceScript.apply(decision.mode, decision.compactSession),
                    null
                )
            } catch (_: Exception) {}
        }
        if (decision.pauseRenderer && ::webViewLifecycle.isInitialized) {
            webViewLifecycle.trimPausedBackgroundSession("$reason • ${decision.mode.name.lowercase()}")
        }
        if (decision.clearMemoryCache) {
            try { webView.clearCache(false) } catch (_: Exception) {}
        }
    }

    private fun wakePlayerRendererForCommand(command: PlaybackCommand) {
        if (!PlayerMediaRetentionPolicy.shouldWakeRenderer(command)) return
        cancelPlayerMediaTrim()
        if (!playerWebViewConfigured || rendererGone) return
        if (::webViewLifecycle.isInitialized) webViewLifecycle.resumeForPlaybackCommand("playback command")
        if (playerMediaRetentionMode != PlayerMediaRetentionMode.ACTIVE) {
            playerMediaRetentionMode = PlayerMediaRetentionMode.ACTIVE
            try { webView.evaluateJavascript(PlayerMediaResourceScript.apply(PlayerMediaRetentionMode.ACTIVE), null) }
            catch (_: Exception) {}
        }
    }

    private fun schedulePlayerMediaRetentionAfterCommand(command: PlaybackCommand) {
        if (!::playbackRuntime.isInitialized || !playerWebViewConfigured || rendererGone) return
        if (!PlayerMediaRetentionPolicy.shouldWakeRenderer(command)) {
            if (command is PlaybackCommand.Stop) cancelPlayerMediaTrim()
            return
        }
        // WebView command evaluation is asynchronous. Do not immediately re-park a renderer that
        // was just woken for Play/Toggle/Seek; the bridge normally cancels this one-shot as soon
        // as the resulting playback state arrives.
        cancelPlayerMediaTrim()
        val expectedVideoId = playbackRuntime.state.videoId
        val task = Runnable {
            playerMediaTrimTask = null
            if (!isDestroyed && !isFinishing && playerWebViewConfigured && !rendererGone &&
                playbackRuntime.state.videoId == expectedVideoId) {
                syncPlayerMediaRetention("playback command settled")
            }
        }
        playerMediaTrimTask = task
        window.decorView.postDelayed(task, 1_000L)
    }

    private fun applyPolicyAndRefresh() {
        refreshFilterPolicies()
        updateEarlyPlayerScripts()
        invalidatePolicyScriptCache()
        if (playerWebViewConfigured && playerSurfaceController.visible && !rendererGone) {
            webView.evaluateJavascript(playerBasePolicyScript(), null)
        }
        if (!browseRendererGone) {
            browseWebView.evaluateJavascript(browseBasePolicyScript(), null)
        }
        refreshUi()
    }

    private fun sharedEarlyScriptPolicy(): String {
        earlyScriptPolicy?.let { return it }
        return EarlyAdScript.build(preferences).also { earlyScriptPolicy = it }
    }

    private fun updateEarlyPlayerScripts() {
        if (!androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val script = EarlyAdScript.build(preferences)
        val playerReady = !playerWebViewConfigured || rendererGone || earlyPlayerScript != null
        val browseReady = browseRendererGone || earlyBrowseScript != null
        if (earlyScriptPolicy == script && playerReady && browseReady) return
        // Preference changes on Home must not wake the hidden Watch bootstrap. Cache now and
        // install into the player only after playback crosses the lazy bootstrap gate.
        if (playerWebViewConfigured && !rendererGone) installEarlyPlayerDocumentScript(script)
        if (!browseRendererGone) installEarlyBrowseDocumentScript(script + "\n" + BrowseFirstPaintScript.install())
        earlyScriptPolicy = script
    }

    /** Reinstall only the player document-start script when that WebView is rehydrated. */
    private fun installEarlyPlayerDocumentScript(script: String = sharedEarlyScriptPolicy()) {
        if (!androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val origins = setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com")
        runCatching { earlyPlayerScript?.remove() }
        earlyPlayerScript = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(webView, script, origins)
        if (earlyScriptPolicy == null) earlyScriptPolicy = script
    }

    /** Reinstall the browse document-start script, including the tiny first-paint priority layer. */
    private fun installEarlyBrowseDocumentScript(script: String = sharedEarlyScriptPolicy() + "\n" + BrowseFirstPaintScript.install()) {
        if (!androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val origins = setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com")
        runCatching { earlyBrowseScript?.remove() }
        earlyBrowseScript = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(browseWebView, script, origins)
    }

    private fun bindBrowseScrollListener() {
        if (!::browseWebView.isInitialized || browseRendererGone) return
        browseWebView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            onBrowseScrolled(scrollY, oldScrollY)
        }
    }

    private fun enterSearchMode() {
        if (urlInput.visibility == View.VISIBLE) return
        setBrowseChromeHidden(false, animated = true)
        if (::feedChromeMotionPolicy.isInitialized) feedChromeMotionPolicy.reset()
        findViewById<View>(R.id.brandContainer).visibility = View.GONE
        findViewById<View>(R.id.notificationButton).visibility = View.GONE
        findViewById<View>(R.id.backButton).visibility = View.VISIBLE
        if (::searchFilterRail.isInitialized) searchFilterRail.visibility = View.GONE
        urlInput.visibility = View.VISIBLE
        urlInput.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .showSoftInput(urlInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun exitSearchMode(clearText: Boolean = true) {
        if (::searchSuggestions.isInitialized) searchSuggestions.dismiss()
        urlInput.clearFocus()
        if (clearText) urlInput.setText("")
        urlInput.visibility = View.GONE
        findViewById<View>(R.id.backButton).visibility = View.GONE
        findViewById<View>(R.id.brandContainer).visibility = View.VISIBLE
        findViewById<View>(R.id.notificationButton).visibility = View.VISIBLE
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(urlInput.windowToken, 0)
        refreshUi()
    }

    private fun bindSearchFilterRail() {
        searchFilterAll.setOnClickListener { applySearchFilterState(currentSearchFilterState().copy(type = SearchResultType.ALL)) }
        searchFilterVideos.setOnClickListener { applySearchFilterState(currentSearchFilterState().copy(type = SearchResultType.VIDEOS)) }
        searchFilterShorts.setOnClickListener { applySearchFilterState(currentSearchFilterState().copy(type = SearchResultType.SHORTS)) }
        searchFilterChannels.setOnClickListener { applySearchFilterState(currentSearchFilterState().copy(type = SearchResultType.CHANNELS)) }
        searchFilterPlaylists.setOnClickListener { applySearchFilterState(currentSearchFilterState().copy(type = SearchResultType.PLAYLISTS)) }
        searchFilterMore.setOnClickListener { showSearchFilterSheet() }
    }

    private fun currentSearchFilterState(): SearchFilterState {
        if (browseRoute.destination != YouTubeDestination.SEARCH) return SearchFilterState()
        val token = runCatching { android.net.Uri.parse(browseRoute.url).getQueryParameter("sp") }.getOrNull()
        return SearchFilterPolicy.decode(token)
    }

    private fun applySearchFilterState(state: SearchFilterState) {
        val query = browseRoute.query.ifBlank { urlInput.text.toString().trim() }
        if (query.isBlank()) return
        renderedSearchFilterKey = ""
        showBrowseDestination(SearchFilterPolicy.buildUrl(query, state), minimizePlayer = false)
    }

    private fun showSearchFilterSheet() {
        if (browseRoute.destination != YouTubeDestination.SEARCH || browseRoute.query.isBlank()) return
        val state = currentSearchFilterState()
        val entries = mutableListOf<Pair<ActionSheet.Action, () -> Unit>>()
        entries += ActionSheet.Action(getString(R.string.search_filter_type), searchTypeLabel(state.type), R.drawable.ic_ui_filter) to
            { showSearchTypeSheet(state) }
        entries += ActionSheet.Action(getString(R.string.search_prioritize), searchPrioritizeLabel(state.prioritize), R.drawable.ic_ui_filter) to
            { showSearchPrioritizeSheet(state) }
        entries += ActionSheet.Action(getString(R.string.search_upload_date), searchUploadLabel(state.uploadDate), R.drawable.ic_ui_filter) to
            { showSearchUploadSheet(state) }
        entries += ActionSheet.Action(getString(R.string.search_duration), searchDurationLabel(state.duration), R.drawable.ic_ui_filter) to
            { showSearchDurationSheet(state) }
        if (!state.isDefault) {
            entries += ActionSheet.Action(getString(R.string.search_reset_filters), icon = R.drawable.ic_ui_close) to
                { applySearchFilterState(SearchFilterState()) }
        }
        quickActionSheet?.dismiss()
        quickActionSheet = ActionSheet.show(
            this,
            getString(R.string.search_filters),
            entries.map { it.first },
            browseRoute.query
        ) { index -> entries.getOrNull(index)?.second?.invoke() }
    }

    private fun showSearchTypeSheet(state: SearchFilterState) = showSearchChoiceSheet(
        getString(R.string.search_filter_type),
        SearchResultType.values().toList(), state.type, ::searchTypeLabel
    ) { applySearchFilterState(state.copy(type = it)) }

    private fun showSearchPrioritizeSheet(state: SearchFilterState) = showSearchChoiceSheet(
        getString(R.string.search_prioritize),
        SearchPrioritize.values().toList(), state.prioritize, ::searchPrioritizeLabel
    ) { applySearchFilterState(state.copy(prioritize = it)) }

    private fun showSearchUploadSheet(state: SearchFilterState) = showSearchChoiceSheet(
        getString(R.string.search_upload_date),
        SearchUploadDate.values().toList(), state.uploadDate, ::searchUploadLabel
    ) { applySearchFilterState(state.copy(uploadDate = it)) }

    private fun showSearchDurationSheet(state: SearchFilterState) = showSearchChoiceSheet(
        getString(R.string.search_duration),
        SearchDuration.values().toList(), state.duration, ::searchDurationLabel
    ) { applySearchFilterState(state.copy(duration = it)) }

    private fun <T> showSearchChoiceSheet(
        title: String,
        options: List<T>,
        current: T,
        label: (T) -> String,
        selected: (T) -> Unit
    ) {
        quickActionSheet?.dismiss()
        val actions = options.map { option ->
            val text = label(option)
            ActionSheet.Action(if (option == current) "✓ $text" else text)
        }
        quickActionSheet = ActionSheet.show(this, title, actions, browseRoute.query) { index ->
            options.getOrNull(index)?.let(selected)
        }
    }

    private fun searchTypeLabel(value: SearchResultType): String = when (value) {
        SearchResultType.ALL -> getString(R.string.search_filter_all)
        SearchResultType.VIDEOS -> getString(R.string.search_filter_videos)
        SearchResultType.SHORTS -> "Shorts"
        SearchResultType.CHANNELS -> getString(R.string.search_filter_channels)
        SearchResultType.PLAYLISTS -> getString(R.string.search_filter_playlists)
    }

    private fun searchPrioritizeLabel(value: SearchPrioritize): String = when (value) {
        SearchPrioritize.RELEVANCE -> getString(R.string.search_priority_relevance)
        SearchPrioritize.NEWEST -> getString(R.string.search_priority_newest)
        SearchPrioritize.POPULARITY -> getString(R.string.search_priority_popularity)
    }

    private fun searchUploadLabel(value: SearchUploadDate): String = when (value) {
        SearchUploadDate.ANY -> getString(R.string.search_upload_any)
        SearchUploadDate.TODAY -> getString(R.string.search_upload_today)
        SearchUploadDate.THIS_WEEK -> getString(R.string.search_upload_week)
        SearchUploadDate.THIS_MONTH -> getString(R.string.search_upload_month)
        SearchUploadDate.THIS_YEAR -> getString(R.string.search_upload_year)
    }

    private fun searchDurationLabel(value: SearchDuration): String = when (value) {
        SearchDuration.ANY -> getString(R.string.search_duration_any)
        SearchDuration.UNDER_3_MIN -> getString(R.string.search_duration_under_3)
        SearchDuration.THREE_TO_20_MIN -> getString(R.string.search_duration_3_20)
        SearchDuration.OVER_20_MIN -> getString(R.string.search_duration_over_20)
    }

    private fun currentPlayerUrl(): String {
        val fallback = rendererRecoveryPlayerUrl.ifBlank { playerRoute.url }
        if (rendererGone || !::webView.isInitialized || !playerWebViewConfigured) return fallback
        return runCatching { webView.url.orEmpty() }.getOrDefault("").ifBlank { fallback }
    }

    private fun navigateFromAddressBar() {
        val target = NavigationTargetResolver.resolveAddressInput(urlInput.text.toString()) ?: return
        val route = YouTubeRoute.parse(target)
        if (route.destination == YouTubeDestination.SEARCH && route.query.isNotBlank()) {
            searchSuggestions.recordSubmitted(route.query)
        }
        navigateClient(target, expandPlayback = true)
        exitSearchMode()
    }

    private fun navigateClient(rawUrl: String, expandPlayback: Boolean) {
        val normalized = YouTubeAdapter.normalizeIncomingUrl(rawUrl) ?: rawUrl
        val route = YouTubeRoute.parse(normalized)
        if (route.isNativePlayback) {
            openPlayer(normalized, expandPlayback)
        } else {
            showBrowseDestination(normalized)
        }
    }

    private fun promoteBrowsePlayback(target: String) {
        if (!YouTubeAdapter.isTrustedBridgeUrl(target)) return
        val normalized = YouTubeAdapter.normalizeIncomingUrl(target) ?: return
        val route = YouTubeRoute.parse(normalized)
        if (!route.isNativePlayback || route.videoId.isBlank()) return
        // Snapshot the visible feed position before the dedicated Watch surface takes over.
        // The browse WebView normally stays alive, but this also gives renderer recovery and
        // later SPA tab switches a bounded route-keyed restore point.
        try { browseWebView.evaluateJavascript(BrowseResourceGuardScript.rememberScroll(), null) } catch (_: Exception) {}
        // Return the browse surface to its previous destination before pausing it behind
        // the expanded player. This preserves browse history without leaving a second
        // YouTube playback document active in the background.
        if (YouTubeRoute.parse(browseWebView.url).isNativePlayback) {
            browseWebView.evaluateJavascript("document.querySelectorAll('video').forEach(v=>v.pause())", null)
            val returnUrl = preferences.lastBrowseUrl.takeUnless { YouTubeRoute.parse(it).isNativePlayback } ?: YouTubeRoute.HOME_URL
            val logicalReturn = YouTubeAdapter.normalizeIncomingUrl(returnUrl) ?: returnUrl
            if (pendingBrowseNavigationUrl != logicalReturn) {
                pendingBrowseNavigationUrl = logicalReturn
                browseWebView.loadUrl(AppLanguage.youtubeUrl(this, returnUrl))
            }
        }
        // JS bridge, shouldOverrideUrlLoading and history callbacks can all report this
        // same /watch transition. openPlayer() is intentionally idempotent for them.
        openPlayer(normalized, expand = true)
    }

    private fun logicalBrowseHistoryUrl(route: YouTubeRoute, rawUrl: String): String {
        // Keep the exact URL (including the last Short id). Shorts de-duplication happens by
        // replacing the current slot rather than by throwing away the media id.
        return YouTubeAdapter.normalizeIncomingUrl(rawUrl) ?: route.url.ifBlank { rawUrl }
    }

    private fun canNavigateBrowseBack(): Boolean {
        if (!::browseHistory.isInitialized || !::browseWebView.isInitialized) return false
        return if (browseHistory.compacted) browseHistory.backTarget() != null
        else browseWebView.canGoBack() || browseHistory.backTarget() != null
    }

    private fun navigateBrowseBack(): Boolean {
        if (!::browseHistory.isInitialized || !::browseWebView.isInitialized || browseRendererGone) return false
        if (!browseHistory.compacted && browseWebView.canGoBack()) {
            browseWebView.goBack()
            return true
        }
        val target = browseHistory.backTarget() ?: return false
        showBrowseDestination(target, minimizePlayer = true, resetHomeScroll = false)
        return true
    }

    private fun navigateBrowseForward(): Boolean {
        if (!::browseHistory.isInitialized || !::browseWebView.isInitialized || browseRendererGone) return false
        if (!browseHistory.compacted && browseWebView.canGoForward()) {
            browseWebView.goForward()
            return true
        }
        val target = browseHistory.forwardTarget() ?: return false
        showBrowseDestination(target, minimizePlayer = true, resetHomeScroll = false)
        return true
    }

    private fun maybeScheduleBrowseHistoryCompaction(
        readyUrl: String,
        pressureTier: MemoryPressureTier = memoryPressureTier
    ) {
        if (!::browseHistory.isInitialized || !::browseWebView.isInitialized || browseRendererGone) return
        val route = YouTubeRoute.parse(readyUrl)
        val historySize = runCatching { browseWebView.copyBackForwardList().size }.getOrDefault(0)
        val stable = pendingBrowseNavigationUrl.isBlank() && YouTubeAdapter.isTrustedBridgeUrl(readyUrl)
        if (!WebViewSessionCompactionPolicy.shouldCompactBrowse(
                historySize = historySize,
                alreadyCompacted = browseHistory.compacted,
                routeStable = stable,
                shorts = route.destination == YouTubeDestination.SHORTS,
                pressure = pressureTier
            )) return

        // A stronger trim signal supersedes a softer pending compaction. Do not let a normal
        // 520/900 ms route-ready delay survive after Android reports low/critical heap pressure.
        if (browseHistoryCompactionTask != null) {
            if (pressureTier.ordinal <= browseHistoryCompactionPressure.ordinal) return
            cancelBrowseHistoryCompaction()
        }

        val expected = logicalBrowseHistoryUrl(route, readyUrl)
        val task = Runnable {
            browseHistoryCompactionTask = null
            browseHistoryCompactionPressure = MemoryPressureTier.NORMAL
            if (isDestroyed || isFinishing || browseRendererGone || pendingBrowseNavigationUrl.isNotBlank()) return@Runnable
            val current = browseWebView.url.orEmpty()
            val currentRoute = YouTubeRoute.parse(current)
            if (logicalBrowseHistoryUrl(currentRoute, current) != expected ||
                currentRoute.destination == YouTubeDestination.SHORTS) return@Runnable
            try {
                browseWebView.clearHistory()
                // The bounded native ring is authoritative after Chromium's back/forward list is
                // dropped, so Back/Forward keeps the user's recent logical path naturally.
                browseHistory.markCompacted()
            } catch (_: Exception) {}
        }
        browseHistoryCompactionTask = task
        browseHistoryCompactionPressure = pressureTier
        val delayMs = WebViewSessionCompactionPolicy.compactionDelayMs(
            pressure = pressureTier,
            browseVisible = activityForeground && !browseSurfaceSuppressed,
            powerConstrained = shortsPowerConstrained
        )
        if (delayMs <= 0L) task.run() else browseWebView.postDelayed(task, delayMs)
    }

    private fun cancelBrowseHistoryCompaction() {
        browseHistoryCompactionTask?.let {
            if (::browseWebView.isInitialized) browseWebView.removeCallbacks(it)
            else window.decorView.removeCallbacks(it)
        }
        browseHistoryCompactionTask = null
        browseHistoryCompactionPressure = MemoryPressureTier.NORMAL
    }

    private fun maybeRestoreBrowseAfterRendererRecovery(readyUrl: String) {
        val targetY = pendingBrowseRecoveryScrollY
        val expectedUrl = pendingBrowseRecoveryUrl
        if (targetY <= dp(8) || expectedUrl.isBlank() || !YouTubeAdapter.isTrustedBridgeUrl(expectedUrl)) {
            pendingBrowseRecoveryScrollY = 0
            pendingBrowseRecoveryUrl = ""
            return
        }
        val readyRoute = YouTubeRoute.parse(readyUrl)
        val expectedRoute = YouTubeRoute.parse(expectedUrl)
        val sameLogicalRoute = readyRoute.destination == expectedRoute.destination &&
            (readyRoute.destination != YouTubeDestination.SEARCH || readyRoute.query == expectedRoute.query)
        if (!sameLogicalRoute) return

        val delays = longArrayOf(0L, 120L, 320L, 680L, 1_100L)
        delays.forEachIndexed { index, delay ->
            browseWebView.postDelayed({
                if (isDestroyed || isFinishing || browseRendererGone || pendingBrowseRecoveryScrollY <= 0) return@postDelayed
                val currentRoute = YouTubeRoute.parse(browseWebView.url)
                if (currentRoute.destination != expectedRoute.destination ||
                    (currentRoute.destination == YouTubeDestination.SEARCH && currentRoute.query != expectedRoute.query)) return@postDelayed
                // A non-trivial scroll means either an earlier restore succeeded or the user has
                // already interacted. In both cases stop fighting the current viewport.
                if (browseWebView.scrollY > dp(8)) {
                    pendingBrowseRecoveryScrollY = 0
                    pendingBrowseRecoveryUrl = ""
                    return@postDelayed
                }
                browseWebView.scrollTo(browseWebView.scrollX, targetY)
                if (index == delays.lastIndex || browseWebView.scrollY > dp(8)) {
                    pendingBrowseRecoveryScrollY = 0
                    pendingBrowseRecoveryUrl = ""
                }
            }, delay)
        }
    }

    private fun restoreBrowseHistorySnapshot(state: Bundle?) {
        if (state == null || !::browseHistory.isInitialized) return
        val entries = state.getStringArrayList(STATE_BROWSE_HISTORY).orEmpty()
        if (entries.isEmpty()) return
        browseHistory.restore(
            BrowseHistoryCoordinator.Snapshot(
                entries = entries,
                index = state.getInt(STATE_BROWSE_HISTORY_INDEX, entries.lastIndex),
                compacted = state.getBoolean(STATE_BROWSE_HISTORY_COMPACTED, false)
            )
        )
    }

    private fun saveBrowseHistorySnapshot(outState: Bundle) {
        if (!::browseHistory.isInitialized) return
        val snapshot = browseHistory.snapshot()
        outState.putStringArrayList(STATE_BROWSE_HISTORY, ArrayList(snapshot.entries))
        outState.putInt(STATE_BROWSE_HISTORY_INDEX, snapshot.index)
        outState.putBoolean(STATE_BROWSE_HISTORY_COMPACTED, snapshot.compacted)
    }

    private fun showBrowseDestination(url: String, minimizePlayer: Boolean = true, resetHomeScroll: Boolean = true) {
        setBrowseChromeHidden(false, animated = false)
        if (::feedChromeMotionPolicy.isInitialized) feedChromeMotionPolicy.reset()
        var surfaceChanged = false
        if (minimizePlayer && playerSurfaceController.visible && !playerSurfaceController.minimized) {
            minimizePlayerSurface()
            surfaceChanged = true
        }
        val normalized = YouTubeAdapter.normalizeIncomingUrl(url) ?: url
        // A single SPA transition may re-enter here through more than one WebView callback.
        // Suppress only the in-flight duplicate; once a URL commits the field is cleared so
        // a later deliberate tap on the same tab retains the existing behavior.
        if (pendingBrowseNavigationUrl == normalized) {
            if (surfaceChanged) refreshUi()
            return
        }
        pendingBrowseNavigationUrl = normalized

        val targetRoute = YouTubeRoute.parse(normalized)
        if (resetHomeScroll && targetRoute.destination == YouTubeDestination.HOME) savedHomeScrollY = 0
        if (targetRoute.destination != YouTubeDestination.HOME) homeRecommendations.resetPage()
        // Do not assign browseRoute before Chromium reports the committed URL. Keeping the
        // currently rendered route until commit prevents bottom-nav/chrome flashes and lets
        // onUrlChanged observe the real previous -> next transition exactly once.
        persistBrowseUrl(normalized, targetRoute, immediate = true)
        if (browseRendererGone) {
            // The user selected a browse destination while its renderer was gone. Rehydrate the
            // browse surface to that destination instead of touching the destroyed WebView.
            rendererRecoveryBrowseUrl = normalized
            rendererRecoveryBrowseScrollY = 0
            browseRendererAutoRecoveryAllowed = true
            scheduleBrowseRendererRehydrate(delayMs = 0L, reason = "user browse navigation")
            if (surfaceChanged) refreshUi()
            return
        }
        // Native bottom-nav/address-bar navigation bypasses DOM click capture, so remember the
        // current feed scroll position explicitly before Chromium changes the document/SPA route.
        try { browseWebView.evaluateJavascript(BrowseResourceGuardScript.rememberScroll(), null) } catch (_: Exception) {}
        browseWebView.loadUrl(AppLanguage.youtubeUrl(this, normalized))
        if (surfaceChanged) refreshUi()
    }

    private fun showCurrentDownload() {
        if (isDestroyed || isFinishing || rendererGone) return
        val route = YouTubeRoute.parse(currentPlayerUrl())
        val session = playbackRuntime.state
        if (route.isPlayback && route.videoId.isNotBlank()) {
            saveVideo.show("https://m.youtube.com/watch?v=${route.videoId}",
                session.title.takeIf { session.videoId == route.videoId }.orEmpty())
        }
    }

    private fun openPlayer(url: String, expand: Boolean = true) {
        val normalized = YouTubeAdapter.normalizeIncomingUrl(url) ?: url
        val route = YouTubeRoute.parse(normalized)
        if (!route.isNativePlayback) {
            showBrowseDestination(normalized)
            return
        }

        // Single normal-playback bootstrap gate. Deep links, restored sessions and user taps
        // configure the hidden Watch WebView immediately before its first navigation.
        ensurePlayerWebViewConfigured("open ${route.videoId.ifBlank { "watch" }}")

        if (rendererGone) {
            val previousTargetId = rendererRecoveryPlayerVideoId.ifBlank { playerRoute.videoId }
            playerRoute = route
            rendererRecoveryPlayerUrl = normalized
            rendererRecoveryPlayerVideoId = route.videoId
            if (route.videoId != previousTargetId) rendererRecoveryPlayerPositionMs = 0L
            rendererRecoveryPlayerWasPlaying = true
            rendererRecoveryPlayerSurface = if (expand) PlayerSurfaceState.EXPANDED.name else PlayerSurfaceState.MINI.name
            pendingPlayerRendererRestore = true
            playbackBackend.beginRecovery(
                PlaybackRecoveryHandoff(
                    playing = true,
                    positionMs = rendererRecoveryPlayerPositionMs,
                    playbackRate = playbackRuntime.state.playbackRate,
                    repeatEnabled = playbackRuntime.state.repeatEnabled
                )
            )
            playerRendererAutoRecoveryAllowed = true
            schedulePlayerRendererRehydrate(0L, "user opened playback")
            return
        }

        val current = webView.url.orEmpty()
        val currentId = YouTubeAdapter.videoIdFromUrl(current)
        val action = NavigationTransitionPolicy.playerAction(
            requestedVideoId = route.videoId,
            activeRouteVideoId = playerRoute.videoId,
            loadedVideoId = currentId,
            surfaceVisible = playerSurfaceController.visible,
            surfaceExpanded = playerSurfaceController.expanded,
            expandRequested = expand
        )
        playerRoute = route
        when (action) {
            PlayerNavigationAction.NO_OP -> return
            PlayerNavigationAction.EXPAND_EXISTING -> {
                expandPlayer(animated = true)
                return
            }
            PlayerNavigationAction.MINIMIZE_EXISTING -> {
                minimizePlayerSurface(animated = true)
                refreshUi()
                return
            }
            PlayerNavigationAction.LOAD_TARGET -> Unit
        }

        // Do not relayout an already-correct surface just because a second callback reports
        // the new video. Relayout of a playing WebView can force YouTube to rebuild viewport
        // state and contributes to visible flashes.
        if (expand) {
            if (!playerSurfaceController.expanded) {
                applyMiniSurfacePolicy(false, playbackRuntime.state.playing)
                playerSurfaceController.expand()
            }
        } else if (playerSurfaceController.visible && !playerSurfaceController.minimized) {
            minimizePlayerSurface()
        }
        syncBrowseWebViewActivity()
        if (currentId.isNullOrBlank() || currentId != route.videoId || !YouTubeAdapter.isTrustedBridgeUrl(current)) {
            webView.loadUrl(AppLanguage.youtubeUrl(this, normalized))
        }
        refreshUi()
    }

    private fun applyMiniSurfacePolicy(enabled: Boolean, resumePlaying: Boolean, force: Boolean = false) {
        if (!::webView.isInitialized || !playerWebViewConfigured || rendererGone) return
        if (!force && miniSurfaceApplied == enabled) return
        miniSurfaceApplied = enabled
        webView.evaluateJavascript(ClientSurfaceScript.mini(enabled, resumePlaying), null)
    }

    private fun expandPlayer(animated: Boolean = true) {
        if (!playerSurfaceController.visible || playerSurfaceController.expanded) return
        // Remove mini CSS before the native viewport grows. Waiting for the next renderUi frame
        // leaves one expanded frame using mini constraints and can provoke a YouTube resize pause.
        applyMiniSurfacePolicy(false, playbackRuntime.state.playing)
        playerSurfaceController.expand(animated = animated && playerSurfaceController.minimized)
        syncBrowseWebViewActivity()
        syncPlayerMediaRetention("player expanded")
        refreshUi()
    }

    private fun minimizePlayer() {
        if (!playerSurfaceController.visible || playerSurfaceController.minimized) return
        minimizePlayerSurface(animated = playerSurfaceController.expanded)
        refreshUi()
    }

    private fun minimizePlayerSurface(animated: Boolean = false) {
        if (!playerSurfaceController.visible || playerSurfaceController.minimized) return
        // Install the mini playback policy before the viewport starts shrinking so YouTube does
        // not interpret the resize as a reason to pause or rebuild the current MediaSource.
        applyMiniSurfacePolicy(true, playbackRuntime.state.playing)
        try { webView.scrollTo(0, 0) } catch (_: Exception) {}
        playerSurfaceController.minimize(animated = animated)
        syncBrowseWebViewActivity()
        syncPlayerMediaRetention("player minimized")
    }

    private fun closePlayer() {
        if (!playerSurfaceController.visible && !playbackRuntime.state.hasSession) return
        if (!rendererGone) playbackRuntime.dispatch(PlaybackCommand.Pause)
        playbackBackend.clearCommunitySegments()
        playbackRuntime.stop(clearSnapshot = true)
        playbackHealth.reset()
        cancelPlayerMediaTrim()
        playerMediaRetentionMode = PlayerMediaRetentionMode.ACTIVE
        playerRendererAutoRecoveryAllowed = false
        pendingPlayerRendererRestore = false
        playbackBackend.cancelRecovery()
        if (::playerRecoveryVisual.isInitialized) playerRecoveryVisual.hide(animated = false)
        rendererRecoveryPlayerUrl = ""
        rendererRecoveryPlayerVideoId = ""
        rendererRecoveryPlayerPositionMs = 0L
        rendererRecoveryPlayerWasPlaying = false
        rendererRecoveryPlayerSurface = ""
        playerRoute = YouTubeRoute.parse(ShieldPreferences.HOME_URL)
        playerSurfaceController.hide()
        syncBrowseWebViewActivity()
        errorOverlay.visibility = View.GONE
        if (!rendererGone) try { webView.loadUrl("about:blank") } catch (_: Exception) {}
        refreshUi()
    }

    private fun onPlaybackState(
        playing: Boolean,
        buffering: Boolean,
        title: String,
        channel: String,
        channelUrl: String,
        videoId: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val pageUrl = currentPlayerUrl()
        val recoveryExpectedId = rendererRecoveryPlayerVideoId.ifBlank { playerRoute.videoId }
        val recoveryDesiredPlaying = playbackBackend.recoveryDesiredPlaying ?: rendererRecoveryPlayerWasPlaying
        val recoveredBridge = PlayerRendererRestorePolicy.bridgeState(
            pending = pendingPlayerRendererRestore,
            desiredPlaying = recoveryDesiredPlaying,
            expectedVideoId = recoveryExpectedId,
            reportedVideoId = videoId,
            reportedPlaying = playing,
            reportedBuffering = buffering
        )
        val effectivePlaying = recoveredBridge.playing
        val effectiveBuffering = recoveredBridge.buffering
        val delta = playbackRuntime.acceptBridgeUpdate(
            playing = effectivePlaying,
            buffering = effectiveBuffering,
            title = title,
            channel = channel,
            channelUrl = channelUrl,
            videoId = videoId,
            positionMs = positionMs,
            durationMs = durationMs,
            pageUrl = pageUrl
        )
        val session = delta.current
        maybeRestorePlayerRendererSession(videoId, positionMs)
        val activelyPlaying = session.playing && !session.buffering
        if (::networkRecovery.isInitialized && networkRecovery.hasCheckpoint()) {
            networkRecovery.onBridgeState(videoId, activelyPlaying, positionMs)?.let { instruction ->
                // Let YouTube finish the bridge callback before touching the media element. The
                // coordinator emits at most one instruction per recovery stage, so this cannot
                // turn regular heartbeats into repeated seek/play commands.
                webView.postDelayed({
                    if (!isDestroyed && !isFinishing && networkOnline &&
                        playbackRuntime.state.videoId == videoId) {
                        applyNetworkRestoreInstruction(instruction)
                    }
                }, 120L)
            }
        }
        if (::playbackInactivity.isInitialized && delta.playingChanged) {
            playbackInactivity.onPlaybackContextChanged()
        }

        // Recovery still needs the sparse heartbeat, but health/wake/PiP work only depends on
        // actual playback topology changes. Avoid re-running those paths for position-only reports.
        playbackRecovery.heartbeat(activelyPlaying, positionMs)
        if (delta.playbackStateChanged || delta.videoChanged) {
            playbackHealth.heartbeat(session.playing, videoId, session.buffering)
        }
        if (activelyPlaying) recoveryDiagnostics.recordSuccessfulHeartbeat()
        if (delta.playingChanged) {
            updatePipActionsIfNeeded()
            webViewLifecycle.updateWakeLock("playback state")
            syncPlayerMediaRetention(if (session.playing) "playback active" else "playback paused")
        } else if (delta.videoChanged) {
            // A new paused Watch document must inherit the current pressure/surface policy.
            syncPlayerMediaRetention("player video changed")
        }

        if (pendingResumeRead?.matches(videoId, playerNavigationGeneration) == false) pendingResumeRead = null
        if (delta.videoChanged) {
            // The shield runtime survives YouTube SPA video changes. Release references/listeners
            // that still point at the old media/player before rebinding the current one. This is
            // event-driven and does not reload the document or discard Chromium's media buffer.
            webView.evaluateJavascript(
                "window.__videoShieldCompactSession && window.__videoShieldCompactSession(); " +
                    "window.__videoShieldSweep && window.__videoShieldSweep(false); " +
                    "window.__videoShieldScheduleCompatibility && window.__videoShieldScheduleCompatibility();",
                null
            )
            applyLongSessionMaintenance(longSessionResources.onPlayerVideoChanged())

            // The dedicated watch surface never uses WebView back/forward navigation.
            // Keep only the current watch entry so long autoplay/queue sessions cannot
            // accumulate a large Chromium navigation list in RAM.
            val currentVideoId = videoId
            webView.post {
                if (!isDestroyed && !isFinishing && playbackRuntime.state.videoId == currentVideoId) {
                    try { webView.clearHistory() } catch (_: Exception) {}
                }
            }

            currentFavorite = false
            currentQueued = false
            readPlaybackLibraryState(includeResume = true)

            if (speedAppliedVideoId != videoId) {
                speedAppliedVideoId = videoId
                webView.postDelayed({ playbackRuntime.dispatch(PlaybackCommand.SetRate(preferences.playbackSpeed)) }, 250L)
            }
            refreshCommunitySegments(videoId)

        }

        if (pendingResumeRead == null && preferences.rememberHistory && videoId.isNotBlank() && YouTubeAdapter.isTrustedBridgeUrl(pageUrl)) {
            val elapsed = android.os.SystemClock.elapsedRealtime()
            val now = System.currentTimeMillis()
            if (historyPersistVideoId != videoId) {
                val previousVideoId = historyPersistVideoId
                val carryWatchedMs = pendingWatchedMs
                if (preferences.personalizedSuggestions && previousVideoId.isNotBlank() && carryWatchedMs > 0L &&
                    now >= preferences.recommendationsSince) {
                    writeLibrary { libraryStore.recordWatched(previousVideoId, carryWatchedMs, now) }
                }
                historyPersistVideoId = videoId
                pendingWatchedMs = 0L
                lastHistoryPersistAt = 0L
            }
            val watched = if (preferences.personalizedSuggestions) {
                habitTracker.update(videoId, activelyPlaying, positionMs, elapsed, session.playbackRate.toDouble())
            } else { habitTracker.reset(); 0L }
            pendingWatchedMs = (pendingWatchedMs + watched).coerceAtMost(120_000L)
            val shouldPersist = delta.videoChanged || delta.playingChanged || !playing ||
                elapsed - lastHistoryPersistAt >= HISTORY_PROGRESS_PERSIST_INTERVAL_MS
            if (shouldPersist) {
                lastHistoryPersistAt = elapsed
                val watchedToPersist = pendingWatchedMs
                pendingWatchedMs = 0L
                val item = VideoItem(videoId, title, channel, pageUrl, now, positionMs, durationMs)
                writeLibrary {
                    if (preferences.rememberHistory && now >= preferences.historyClearedAt) {
                        libraryStore.recordHistory(item, preferences.maxHistoryItems)
                        if (preferences.personalizedSuggestions && watchedToPersist > 0L && now >= preferences.recommendationsSince)
                            libraryStore.recordWatched(videoId, watchedToPersist, now)
                    }
                }
            }
        } else {
            habitTracker.reset()
            pendingWatchedMs = 0L
        }

        if (delta.channelChanged) {
            currentSubscribed = false
            if (!delta.videoChanged) readPlaybackLibraryState(includeResume = false)
            val shouldBypass = preferences.isChannelWhitelisted(channel)
            if (filterEngine.pageWhitelisted != shouldBypass) {
                filterEngine.pageWhitelisted = shouldBypass
                webView.evaluateJavascript(
                    AdBlockScript.build(
                        preferences,
                        rulePackManager.active(),
                        shouldBypass,
                        preferredQualityOverride = effectivePreferredQuality()
                    ),
                    null
                )
            }
        }
        if (delta.presentationChanged) refreshUi() else updateMiniProgress(session)
        playbackRuntime.publish(preferences.backgroundControls)
    }

    private fun readPlaybackLibraryState(includeResume: Boolean) {
        val session = playbackRuntime.state
        val key = PlaybackReadKey(session.videoId, playerNavigationGeneration)
        val channel = session.channel
        val request = ++libraryFlagsRequest
        val readResume = includeResume && session.videoId.isNotBlank() && resumeAppliedVideoId != session.videoId
        if (readResume) pendingResumeRead = key
        val includeHistory = readResume && preferences.resumePlayback
        writeLibrary {
            val result = runCatching { libraryStore.playbackState(key.videoId, channel, includeHistory) }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                val latest = playbackRuntime.state
                if (!key.matches(latest.videoId, playerNavigationGeneration)) {
                    if (pendingResumeRead == key) pendingResumeRead = null
                    return@runOnUiThread
                }
                val state = result.getOrNull()
                if (state != null && request == libraryFlagsRequest && latest.channel == channel) {
                    currentFavorite = state.favorite; currentQueued = state.queued
                    currentSubscribed = state.subscribed; queueCountCache = state.queueCount
                }
                if (readResume && pendingResumeRead == key) {
                    resumeAppliedVideoId = key.videoId
                    val saved = state?.history
                    val target = key.resumeTarget(saved, latest.positionMs, preferences.resumePlayback, preferences.historyClearedAt)
                    if (target == null) pendingResumeRead = null else {
                        webView.postDelayed({
                            if (!isDestroyed && !isFinishing && pendingResumeRead == key) {
                                if (key.matches(playbackRuntime.state.videoId, playerNavigationGeneration) &&
                                    key.resumeTarget(saved, playbackRuntime.state.positionMs, preferences.resumePlayback, preferences.historyClearedAt) == target &&
                                    YouTubeRoute.parse(currentPlayerUrl()).videoId == key.videoId) playbackRuntime.dispatch(PlaybackCommand.SeekTo(target))
                                pendingResumeRead = null
                            }
                        }, 450L)
                        Toast.makeText(this, getString(R.string.resume_at,formatPosition(target)), Toast.LENGTH_SHORT).show()
                    }
                }
                refreshUi()
            }
        }
    }

    private fun onPlaybackEnded(videoId: String) {
        val session = playbackRuntime.state
        if (!playbackEndGuard.shouldAutoAdvance(
                reportedVideoId = videoId,
                currentVideoId = session.videoId,
                routeVideoId = playerRoute.videoId,
                navigationGeneration = playerNavigationGeneration,
                repeatEnabled = session.repeatEnabled,
                autoAdvanceEnabled = preferences.autoAdvanceQueue
            )) return
        playNextFromQueue(manual = false, completedVideoId = videoId)
    }

    private fun playNextFromQueue(manual: Boolean, completedVideoId: String = playbackRuntime.state.videoId) {
        val request = ++queueAdvanceRequest
        val generation = playerNavigationGeneration
        writeLibrary {
            val result = runCatching { libraryStore.advanceQueue(completedVideoId) }
            runOnUiThread {
                if (isDestroyed || isFinishing || request != queueAdvanceRequest) return@runOnUiThread
                if (!manual && generation != playerNavigationGeneration && playbackRuntime.state.videoId != completedVideoId) return@runOnUiThread
                val advanced = result.getOrNull()
                if (advanced == null) {
                    if (manual) Toast.makeText(this, getString(R.string.ui_could_not_load_the_library_reopen_this_tab_to_retry), Toast.LENGTH_SHORT).show()
                    refreshUi()
                    return@runOnUiThread
                }
                queueCountCache = advanced.queueCount
                currentQueued = false
                val next = advanced.next
                if (next == null) {
                    if (manual) Toast.makeText(this, getString(R.string.ui_queue_is_empty), Toast.LENGTH_SHORT).show()
                    else playSuggestedNext(completedVideoId)
                    refreshUi()
                    return@runOnUiThread
                }
                Toast.makeText(this, getString(R.string.up_next, next.title), Toast.LENGTH_SHORT).show()
                openPlayer(next.url, expand = playerSurfaceController.expanded)
                refreshUi()
            }
        }
    }

    private fun playSuggestedNext(completedId: String) {
        webView.evaluateJavascript(NextVideoScript.build(completedId)) { result ->
            if(!preferences.autoAdvanceQueue || playbackRuntime.state.repeatEnabled || playbackRuntime.state.videoId!=completedId ||
                YouTubeAdapter.videoIdFromUrl(currentPlayerUrl())!=completedId) return@evaluateJavascript
            val candidate=runCatching {
                val text=org.json.JSONTokener(result).nextValue() as? String ?: return@runCatching null
                org.json.JSONObject(text)
            }.getOrNull() ?: return@evaluateJavascript
            val url=candidate.optString("url")
            val id=YouTubeAdapter.videoIdFromUrl(url)
            if(!YouTubeAdapter.isTrustedBridgeUrl(url) || id.isNullOrBlank() || id==completedId) return@evaluateJavascript
            openPlayer(url,expand=playerSurfaceController.expanded)
        }
    }

    private fun formatPosition(positionMs: Long): String {
        val total = (positionMs / 1000L).coerceAtLeast(0L)
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val sec = total % 60L
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    private fun toggleCurrentSubscription() {
        val session = playbackRuntime.state
        if (session.channel.isBlank()) return
        val url = session.channelUrl.ifBlank {
            "https://m.youtube.com/results?search_query=" + URLEncoder.encode(session.channel, "UTF-8")
        }
        mutatePlaybackLibrary {
            val subscribed = libraryStore.toggleSubscription(session.channel, url)
            if (subscribed) getString(R.string.subscribed_local,session.channel) else getString(R.string.unsubscribed_local)
        }
    }

    private fun toggleCurrentFavorite() {
        val session = playbackRuntime.state
        if (session.videoId.isBlank()) return
        val item = VideoItem(
            session.videoId,
            session.title.ifBlank { getString(R.string.youtube_video) },
            session.channel,
            currentPlayerUrl(),
            System.currentTimeMillis(),
            session.positionMs,
            session.durationMs
        )
        mutatePlaybackLibrary {
            if (libraryStore.toggleFavorite(item)) getString(R.string.saved_favorites) else getString(R.string.removed_favorites)
        }
    }

    private fun mutatePlaybackLibrary(action: () -> String) {
        val session = playbackRuntime.state
        val key = PlaybackReadKey(session.videoId, playerNavigationGeneration)
        val channel = session.channel
        val request = ++libraryFlagsRequest
        writeLibrary {
            val result = runCatching { action() to libraryStore.playbackState(key.videoId, channel, false) }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                val latest = playbackRuntime.state
                if (request != libraryFlagsRequest || !key.matches(latest.videoId, playerNavigationGeneration) || latest.channel != channel) return@runOnUiThread
                result.onSuccess { (message, state) ->
                    currentFavorite = state.favorite; currentQueued = state.queued
                    currentSubscribed = state.subscribed; queueCountCache = state.queueCount
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, getString(R.string.ui_could_not_save_the_change_please_retry), Toast.LENGTH_SHORT).show()
                }
                refreshUi()
            }
        }
    }

    private fun toggleCurrentQueue() {
        val session = playbackRuntime.state
        if (session.videoId.isBlank()) return
        val key = PlaybackReadKey(session.videoId, playerNavigationGeneration)
        val request = ++libraryFlagsRequest
        val item = VideoItem(
            session.videoId,
            session.title.ifBlank { getString(R.string.youtube_video) },
            session.channel,
            currentPlayerUrl(),
            System.currentTimeMillis(),
            session.positionMs,
            session.durationMs
        )
        writeLibrary {
            val result = runCatching { libraryStore.toggleQueue(item) }
            runOnUiThread {
                if (isDestroyed || isFinishing || request != libraryFlagsRequest ||
                    !key.matches(playbackRuntime.state.videoId, playerNavigationGeneration)) return@runOnUiThread
                result.onSuccess { state ->
                    currentQueued = state.queued
                    queueCountCache = state.queueCount
                    Toast.makeText(
                        this,
                        getString(if (state.queued) R.string.ui_added_to_queue else R.string.ui_removed_from_queue),
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure {
                    Toast.makeText(this, getString(R.string.ui_could_not_save_the_change_please_retry), Toast.LENGTH_SHORT).show()
                }
                refreshUi()
            }
        }
    }

    private fun maybeRestorePlayerRendererSession(videoId: String, positionMs: Long) {
        if (!pendingPlayerRendererRestore || rendererGone) return
        val expectedVideoId = rendererRecoveryPlayerVideoId.ifBlank { playerRoute.videoId }
        if (!PlayerRendererRestorePolicy.canApply(expectedVideoId, videoId)) return

        // The first bridge from the expected media element is the readiness signal. Do not use a
        // fixed delay: slow devices can still be unready at 180 ms, while fast devices needlessly
        // sit on a black/buffering frame. RebindablePlaybackBackend has already coalesced any user
        // commands issued during recovery, so one deterministic activation is enough.
        val activation = playbackBackend.activateRecovery(positionMs) ?: return
        pendingPlayerRendererRestore = false
        rendererRecoveryPlayerWasPlaying = activation.desiredPlaying
        if (activation.seekApplied) playbackRuntime.overridePosition(activation.targetPositionMs)

        // The native surface can settle before the replacement document has installed its client
        // CSS. Reapply surface policy at bridge readiness so mini/PiP never depends on the timing
        // of the first renderUi frame after WebView creation.
        if (isInPictureInPictureMode) {
            miniSurfaceApplied = false
            webView.evaluateJavascript(ClientSurfaceScript.pip(true, activation.desiredPlaying), null)
        } else {
            applyMiniSurfacePolicy(playerSurfaceController.minimized, activation.desiredPlaying, force = true)
        }
        refreshCommunitySegments(videoId, force = true)
        if (::playerRecoveryVisual.isInitialized) playerRecoveryVisual.hide(animated = true)
        runtimeDiagnostics.recordRestore("player playback handoff activated on renderer bridge", true)
    }

    private fun cyclePlaybackSpeed() {
        val speeds = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val current = playbackRuntime.state.playbackRate
        val index = speeds.indices.minByOrNull { kotlin.math.abs(speeds[it] - current) } ?: 1
        val next = speeds[(index + 1) % speeds.size]
        preferences.playbackSpeed = next
        speedAppliedVideoId = playbackRuntime.state.videoId
        // __videoShieldSetRate already owns sparse retry checkpoints inside the renderer.
        // Issuing a second native command here doubled those JS timers on every speed tap.
        playbackRuntime.dispatch(PlaybackCommand.SetRate(next))
        Toast.makeText(this, getString(R.string.playback_speed,formatSpeed(next)), Toast.LENGTH_SHORT).show()
        refreshUi()
    }

    private fun formatSpeed(speed: Float): String {
        val value = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0').trimEnd('.')
        return "${value}×"
    }

    private fun showQualityDialog() {
        val values = arrayOf("adaptive", "highres", "hd2160", "hd1440", "hd1080", "hd720", "large", "medium", "small", "tiny")
        val labels = values.map { qualityLabel(it) }.toTypedArray()
        val metered = currentNetworkMetered()
        val checked = values.indexOf(effectivePreferredQuality()).coerceAtLeast(0)
        val profileName = if (metered) getString(R.string.ui_mobile_metered) else getString(R.string.ui_wi_fi_unmetered)
        // Capture in the renderer before the dialog can take window focus.
        webView.evaluateJavascript("window.__videoShieldPrepareQualityChange?.();") {
        if (isDestroyed || isFinishing) return@evaluateJavascript
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.quality_profile,profileName))
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                saveManualQuality(values[which])
                applyPolicyAndRefresh()
                dialog.dismiss()
                Toast.makeText(this, getString(R.string.quality_selected,profileName,labels[which]), Toast.LENGTH_SHORT).show()
                // A changed preference already applies once in AdBlockScript.build().
                // Only reselecting the same mode needs an explicit reset (e.g. Auto).
                if (which == checked)
                    webView.evaluateJavascript("window.__videoShieldResetQuality?.();", null)

                // Keep the playback intent captured before opening the dialog alive while
                // YouTube swaps renditions. Some player versions pause a second time after
                // setPlaybackQuality() has already returned and the first play() succeeded.
                for (delayMs in longArrayOf(80L, 350L, 900L)) {
                    webView.postDelayed({
                        if (!isDestroyed && !isFinishing) {
                            webView.evaluateJavascript("window.__videoShieldCommitQualityChange?.();", null)
                        }
                    }, delayMs)
                }
            }
            .setNegativeButton(getString(R.string.ui_cancel)) { _, _ ->
                webView.evaluateJavascript("window.__videoShieldCancelQualityChange?.();", null)
            }
            .setOnCancelListener {
                webView.evaluateJavascript("window.__videoShieldCancelQualityChange?.();", null)
            }
            .show()
        }
    }

    private fun currentNetworkMetered(): Boolean =
        if (::networkStateMonitor.isInitialized) networkStateMonitor.currentMetered() else networkMetered

    private fun effectivePreferredQuality(): String =
        preferences.preferredQualityForNetwork(currentNetworkMetered())

    private fun saveManualQuality(quality: String) {
        if(quality !in ShieldPreferences.SUPPORTED_QUALITY_VALUES) return
        preferences.preferredQuality = quality
        preferences.preferredQualityMobile = quality
        lastPolicyFingerprint = policyFingerprint()
    }

    private fun qualityLabel(value: String): String = when (value) {
        "adaptive" -> getString(R.string.quality_adaptive)
        "highres" -> getString(R.string.ui_highest)
        "hd2160" -> "2160p"
        "hd1440" -> "1440p"
        "hd1080" -> "1080p"
        "hd720" -> "720p"
        "large" -> "480p"
        "medium" -> "360p"
        "small" -> "240p"
        "tiny" -> "144p"
        else -> getString(R.string.ui_auto)
    }

    private fun refreshCommunitySegments(videoId: String, force: Boolean = false) {
        val categories = preferences.communitySegmentCategories()
        if (preferences.safeMode || !networkOnline || videoId.isBlank() || categories.isEmpty()) {
            communitySegmentRequestKey = ""
            playbackBackend.clearCommunitySegments()
            return
        }
        val key = videoId + "|" + categories.sorted().joinToString(",")
        if (!force && communitySegmentRequestKey == key) return
        communitySegmentRequestKey = key
        communitySegmentClient.load(videoId, categories) { segments ->
            runOnUiThread {
                val currentId = playbackRuntime.state.videoId
                val stillEnabled = !preferences.safeMode && preferences.communitySegmentCategories() == categories
                if (currentId == videoId && stillEnabled && networkOnline && !isFinishing) {
                    playbackBackend.setCommunitySegments(videoId, segments)
                }
            }
        }
    }

    private fun segmentCategoryLabel(category: String): String = when (category) {
        CommunitySegmentClient.CATEGORY_SPONSOR -> getString(R.string.segment_sponsor)
        CommunitySegmentClient.CATEGORY_SELF_PROMO -> getString(R.string.segment_promo)
        CommunitySegmentClient.CATEGORY_INTERACTION -> getString(R.string.segment_interaction)
        CommunitySegmentClient.CATEGORY_INTRO -> getString(R.string.segment_intro)
        CommunitySegmentClient.CATEGORY_OUTRO -> getString(R.string.segment_outro)
        CommunitySegmentClient.CATEGORY_PREVIEW -> getString(R.string.segment_preview)
        CommunitySegmentClient.CATEGORY_MUSIC_OFFTOPIC -> getString(R.string.segment_music)
        else -> getString(R.string.segment_generic)
    }

    private fun applyNativeTheme() {
        if (!::preferences.isInitialized) return
        AppTheme.applySystemBars(this)
        val shell = AppTheme.surface(this)
        val background = AppTheme.background(this)
        if (::topBar.isInitialized) topBar.setBackgroundColor(shell)
        if (::bottomBar.isInitialized) {
            // A near-opaque overlay keeps the navigation legible while letting large feed imagery
            // subtly read underneath, without the GPU cost and API variance of a live blur.
            bottomBar.setBackgroundColor(Color.argb(246, Color.red(shell), Color.green(shell), Color.blue(shell)))
        }
        // Playback stays black to avoid a light flash around video frames.
        if (::webView.isInitialized && !rendererGone) webView.setBackgroundColor(Color.BLACK)
        if (::browseWebView.isInitialized && !browseRendererGone) browseWebView.setBackgroundColor(background)
    }

    private fun updateBrowserChromeVisibility(url: String?) {
        if (!::topBar.isInitialized || !::preferences.isInitialized || !::playerSurfaceController.isInitialized) return
        val pip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode
        if (customView != null || pip) {
            topBar.visibility = View.GONE
            playbackActionStrip.visibility = View.GONE
            return
        }

        if (playerSurfaceController.expanded) setBrowseChromeHidden(false, animated = false)
        val chromeRoute = if (playerSurfaceController.expanded) playerRoute else browseRoute
        val chrome = ClientChromePolicy.forRoute(
            chromeRoute,
            fullscreen = false,
            pictureInPicture = false,
            minimalPlaybackChrome = preferences.compactYouTubeChrome && playerSurfaceController.expanded
        )
        topBar.visibility = if (chrome.showAppBar) View.VISIBLE else View.GONE
        // Runtime/compatibility status is still maintained internally, but it no longer
        // occupies permanent space under the video. The current YouTube player keeps this
        // surface focused on content and primary actions.
        playbackStatusRow.visibility = View.GONE
        // In the default compact mode the watch-page actions live inside YouTube's content
        // surface. The legacy/native rail remains available as a compatibility fallback when
        // compact chrome is explicitly disabled, without paying its layout cost by default.
        playbackActionStrip.visibility = if (
            playerSurfaceController.expanded && playbackRuntime.state.hasSession && !preferences.compactYouTubeChrome
        ) View.VISIBLE else View.GONE
        if (!urlInput.hasFocus()) {
            urlInput.hint = ClientChromePolicy.forRoute(
                browseRoute,
                fullscreen = false,
                pictureInPicture = false
            ).searchHint.let { if(it == "Search YouTube") getString(R.string.search_youtube) else it }
        }
    }

    private fun showPlaybackMoreSheet() {
        val session = playbackRuntime.state
        val pipButton = findViewById<Button>(R.id.pipButton)
        val actions = listOf(
            ActionSheet.Action(getString(R.string.ui_minimize), icon = R.drawable.ic_ui_minimize),
            ActionSheet.Action(
                if (currentSubscribed) getString(R.string.ui_subscribed) else getString(R.string.ui_subscribe),
                session.channel,
                R.drawable.ic_nav_subscriptions
            ),
            ActionSheet.Action(
                if (currentFavorite) getString(R.string.saved_favorites) else getString(R.string.ui_save),
                icon = if (currentFavorite) R.drawable.ic_ui_bookmark_filled else R.drawable.ic_ui_bookmark
            ),
            ActionSheet.Action(getString(R.string.ui_download), icon = R.drawable.ic_ui_download),
            ActionSheet.Action(getString(R.string.ui_playback_speed), speedButton.text.toString(), R.drawable.ic_ui_speed),
            ActionSheet.Action(getString(R.string.ui_video_quality), qualityButton.text.toString(), R.drawable.ic_ui_quality),
            ActionSheet.Action(autoNextButton.text.toString(), icon = R.drawable.ic_ui_queue),
            ActionSheet.Action(queueButton.text.toString(), icon = R.drawable.ic_ui_queue),
            ActionSheet.Action(repeatButton.text.toString(), icon = R.drawable.ic_ui_repeat),
            ActionSheet.Action(sleepButton.text.toString(), icon = R.drawable.ic_ui_timer),
            ActionSheet.Action(pipButton.text.toString(), icon = R.drawable.ic_ui_pip)
        )
        val handlers: List<() -> Unit> = listOf(
            { minimizePlayer() },
            { if (subscribeButton.isEnabled) toggleCurrentSubscription() },
            { if (favoriteButton.isEnabled) toggleCurrentFavorite() },
            { if (session.videoId.isNotBlank()) showCurrentDownload() },
            { if (speedButton.isEnabled) speedButton.performClick() },
            { if (qualityButton.isEnabled) qualityButton.performClick() },
            { if (autoNextButton.isEnabled) autoNextButton.performClick() },
            { if (queueButton.isEnabled) queueButton.performClick() },
            { if (repeatButton.isEnabled) repeatButton.performClick() },
            { if (sleepButton.isEnabled) sleepButton.performClick() },
            { if (pipButton.isEnabled) pipButton.performClick() }
        )
        quickActionSheet?.dismiss()
        quickActionSheet = ActionSheet.show(
            this,
            session.title.ifBlank { getString(R.string.playing_video) },
            actions
        ) { index -> handlers.getOrNull(index)?.invoke() }
    }

    private fun showSleepTimerDialog() {
        val labels = arrayOf(getString(R.string.timer_off), getString(R.string.sleep_minutes,15), getString(R.string.sleep_minutes,30), getString(R.string.sleep_minutes,60), getString(R.string.sleep_minutes,90))
        val values = intArrayOf(0, 15, 30, 60, 90)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ui_sleep_timer))
            .setItems(labels) { _, which ->
                val minutes = values[which]
                if (minutes == 0) {
                    sleepTimerController.cancel()
                    Toast.makeText(this, getString(R.string.ui_sleep_timer_off), Toast.LENGTH_SHORT).show()
                } else {
                    sleepTimerController.start(minutes)
                    Toast.makeText(this, getString(R.string.pause_after,minutes), Toast.LENGTH_SHORT).show()
                }
                refreshUi()
            }
            .setNegativeButton(getString(R.string.ui_cancel), null)
            .show()
    }

    private fun openLibrary(mode: String) {
        startActivity(Intent(this, LibraryActivity::class.java).putExtra(LibraryActivity.EXTRA_MODE, mode))
    }

    private fun toggleCurrentChannelAllowlist() {
        val channel = playbackRuntime.state.channel
        if (channel.isBlank()) return
        val whitelisted = preferences.toggleChannelWhitelist(channel)
        filterEngine.pageWhitelisted = whitelisted
        webView.evaluateJavascript(
            AdBlockScript.build(
                preferences,
                rulePackManager.active(),
                whitelisted,
                preferredQualityOverride = effectivePreferredQuality()
            ),
            null
        )
        Toast.makeText(
            this,
            if (whitelisted) getString(R.string.ads_allowed,channel) else getString(R.string.ads_protected,channel),
            Toast.LENGTH_SHORT
        ).show()
        refreshUi()
    }

    private fun enterPip() {
        if (rendererGone || Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isInPictureInPictureMode || pipEnterRequested) return
        try {
            playingBeforePip = playbackRuntime.state.playing
            pipEnterRequested = enterPictureInPictureMode(buildPipParams())
        } catch (_: Exception) {
            pipEnterRequested = false
        }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        builder.setActions(buildPipActions())
        return builder.build()
    }

    private fun buildPipActions(): List<RemoteAction> = listOf(
        pipAction(PlaybackService.CMD_SEEK_BACK, getString(R.string.back_seconds), R.drawable.ic_pip_back, 701),
        pipAction(
            if (playbackRuntime.state.playing) PlaybackService.CMD_PAUSE else PlaybackService.CMD_PLAY,
            if (playbackRuntime.state.playing) getString(R.string.ui_pause) else getString(R.string.ui_play),
            if (playbackRuntime.state.playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
            702
        ),
        pipAction(PlaybackService.CMD_SEEK_FORWARD, getString(R.string.forward_seconds), R.drawable.ic_pip_forward, 703)
    )

    private fun pipAction(command: String, title: String, iconRes: Int, requestCode: Int): RemoteAction {
        val intent = Intent(PlaybackService.ACTION_COMMAND)
            .setPackage(packageName)
            .putExtra(PlaybackService.EXTRA_COMMAND, command)
        val pending = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteAction(Icon.createWithResource(this, iconRes), title, title, pending)
    }

    private fun updatePipActionsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isInPictureInPictureMode) return
        try { setPictureInPictureParams(buildPipParams()) } catch (_: Exception) {}
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (preferences.autoPiP && playbackRuntime.state.playing && customView == null) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipEnterRequested = false
        if (isInPictureInPictureMode) {
            val resumePlaying = playingBeforePip || playbackRuntime.state.playing
            playingBeforePip = false
            pipPlaybackWanted = resumePlaying
            if (surfaceBeforePip == null) surfaceBeforePip = playerSurfaceController.state.name
            applyMiniSurfacePolicy(false, resumePlaying)
            playerSurfaceController.expand()
            if (!rendererGone) webView.evaluateJavascript(ClientSurfaceScript.pip(true, resumePlaying), null)
            topBar.visibility = View.GONE
            bottomBar.visibility = View.GONE
            updatePipActionsIfNeeded()
        } else {
            pipPlaybackWanted = false
            if (!rendererGone) webView.evaluateJavascript(ClientSurfaceScript.pip(false), null)
            surfaceBeforePip?.let { playerSurfaceController.restore(it) }
            applyMiniSurfacePolicy(playerSurfaceController.minimized, playbackRuntime.state.playing, force = true)
            surfaceBeforePip = null
            bottomBar.visibility = View.VISIBLE
            updateBrowserChromeVisibility(currentPlayerUrl())
        }
        syncBrowseWebViewActivity()
        syncPlayerMediaRetention(if (isInPictureInPictureMode) "entered PiP" else "exited PiP")
        findViewById<View>(android.R.id.content).requestApplyInsets()
    }

    private fun previewFullscreenMinimize(distancePx: Float) {
        if (customView == null || fullscreenContainer.height <= 0) return
        fullscreenContainer.animate().cancel()
        val progress = (distancePx / (fullscreenContainer.height * 0.42f).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val scale = 1f - 0.055f * progress
        fullscreenContainer.pivotX = fullscreenContainer.width * 0.5f
        fullscreenContainer.pivotY = fullscreenContainer.height * 0.5f
        fullscreenContainer.translationY = distancePx * (0.48f - 0.08f * progress)
        fullscreenContainer.scaleX = scale
        fullscreenContainer.scaleY = scale
        fullscreenContainer.alpha = 1f - 0.10f * progress
    }

    private fun cancelFullscreenMinimizePreview() {
        if (customView == null) return
        fullscreenContainer.animate().cancel()
        fullscreenContainer.animate()
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(170L)
            .setInterpolator(android.view.animation.PathInterpolator(0.20f, 0f, 0f, 1f))
            .start()
    }

    private fun animateFullscreenMinimize() {
        if (customView == null) {
            minimizePlayer()
            return
        }
        val density = resources.displayMetrics.density
        val currentY = fullscreenContainer.translationY.coerceAtLeast(0f)
        val targetY = maxOf(currentY, maxOf(72f * density, fullscreenContainer.height * 0.16f))
        fullscreenContainer.animate().cancel()
        fullscreenContainer.animate()
            .translationY(targetY)
            .scaleX(0.955f)
            .scaleY(0.955f)
            .alpha(0.72f)
            .setDuration(145L)
            .setInterpolator(android.view.animation.PathInterpolator(0.20f, 0f, 0f, 1f))
            .withEndAction {
                fullscreenContainer.translationX = 0f
                fullscreenContainer.translationY = 0f
                fullscreenContainer.scaleX = 1f
                fullscreenContainer.scaleY = 1f
                fullscreenContainer.alpha = 1f
                this@MainActivity.exitFullscreen()
                minimizePlayerSurface(animated = false)
                refreshUi()
            }
            .start()
    }

    private fun exitFullscreen() {
        val view = customView ?: return
        fullscreenContainer.removeView(view)
        fullscreenContainer.visibility = View.GONE
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        webView.visibility = View.VISIBLE
        bottomBar.visibility = View.VISIBLE
        updateBrowserChromeVisibility(currentPlayerUrl())
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun extractIncomingUrl(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_VIEW) return YouTubeAdapter.normalizeIncomingUrl(intent.dataString)
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val match = NavigationTargetResolver.extractFirstHttpUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            return YouTubeAdapter.normalizeIncomingUrl(match)
        }
        return null
    }


    private fun refreshUi() {
        if (isDestroyed || isFinishing || uiRefreshPending) return
        if (::webViewLifecycle.isInitialized && !webViewLifecycle.foreground && !isInPictureInPictureMode) return
        uiRefreshPending = true
        window.decorView.postOnAnimation(uiRefreshFrame)
    }

    private fun renderUi() {
        playbackScreenOn.update(webViewLifecycle.foreground && playerSurfaceController.visible &&
            playbackRuntime.state.playing && !isInPictureInPictureMode &&
            (!::playbackInactivity.isInitialized || !playbackInactivity.screenReleaseRequested))
        if (!isInPictureInPictureMode && miniSurfaceApplied != playerSurfaceController.minimized) {
            applyMiniSurfacePolicy(playerSurfaceController.minimized, playbackRuntime.state.playing)
        }
        val session = playbackRuntime.state
        val miniNow = playerSurfaceController.minimized
        if (::miniChromeController.isInitialized) {
            if (miniChromeActive != miniNow) {
                miniChromeActive = miniNow
                if (miniNow) miniChromeController.onMiniEntered(session.playing) else miniChromeController.onMiniExited()
            }
            miniChromeController.onPlaybackChanged(session.playing)
        }
        val network = if (networkOnline) "" else getString(R.string.ui_offline_suffix)
        val health = if (::playbackHealth.isInitialized && playbackHealth.snapshot.state !in setOf(PlaybackHealthState.IDLE, PlaybackHealthState.HEALTHY)) {
            " • ${LocalizedPresentation.health(this,playbackHealth.snapshot.state)}"
        } else ""
        val primary = when {
            session.title.isNotBlank() -> session.title
            browseRoute.destination == YouTubeDestination.SEARCH && browseRoute.query.isNotBlank() -> getString(R.string.search_query,browseRoute.query)
            browseRoute.destination == YouTubeDestination.SHORTS -> "Shorts"
            browseRoute.destination == YouTubeDestination.SUBSCRIPTIONS -> getString(R.string.ui_subscriptions)
            else -> getString(R.string.app_name)
        }
        val secondary = when {
            session.channel.isNotBlank() -> session.channel
            preferences.safeMode -> getString(R.string.safe_mode)
            else -> getString(R.string.enhanced_playback)+network+health
        }
        statusText.setTextIfChanged("$primary\n$secondary")

        if (filterToggle.isChecked != preferences.shieldEnabled) {
            suppressShieldToggle = true
            filterToggle.isChecked = preferences.shieldEnabled
            suppressShieldToggle = false
        }

        channelButton.isEnabled = session.channel.isNotBlank()
        channelButton.setTextIfChanged(if (session.channel.isNotBlank() && preferences.isChannelWhitelisted(session.channel)) getString(R.string.protect) else getString(R.string.ui_allow))
        subscribeButton.apply {
            isEnabled = session.channel.isNotBlank()
            setTextIfChanged(if (currentSubscribed) getString(R.string.ui_subscribed) else getString(R.string.ui_subscribe))
            if (renderedSubscribedStyle != currentSubscribed) {
                renderedSubscribedStyle = currentSubscribed
                setBackgroundResource(if (currentSubscribed) R.drawable.bg_action else R.drawable.bg_primary_pill)
                setTextColor(if (currentSubscribed) AppTheme.primary(this@MainActivity) else AppTheme.selectedText(this@MainActivity))
            }
        }
        favoriteButton.apply {
            isEnabled = session.videoId.isNotBlank()
            setTextIfChanged(getString(R.string.ui_save))
            setLeadingIcon(if (currentFavorite) R.drawable.ic_ui_bookmark_filled else R.drawable.ic_ui_bookmark)
        }
        queueButton.apply {
            isEnabled = session.videoId.isNotBlank()
            setTextIfChanged(if (currentQueued) getString(R.string.queued) else getString(R.string.ui_queue))
            setLeadingIcon(R.drawable.ic_ui_queue)
        }
        speedButton.setTextIfChanged(formatSpeed(session.playbackRate))
        qualityButton.setTextIfChanged(qualityLabel(effectivePreferredQuality()))
        playbackMoreButton.isEnabled = session.hasSession
        autoNextButton.apply {
            setTextIfChanged(getString(if(preferences.autoAdvanceQueue) R.string.auto_next_on else R.string.auto_next_off))
            setLeadingIcon(R.drawable.ic_ui_queue, if(preferences.autoAdvanceQueue) Color.rgb(62,166,255) else AppTheme.icon(this@MainActivity))
        }
        repeatButton.apply {
            setTextIfChanged(if (session.repeatEnabled) getString(R.string.repeat_on) else getString(R.string.ui_repeat))
            setLeadingIcon(R.drawable.ic_ui_repeat, if (session.repeatEnabled) Color.rgb(62,166,255) else AppTheme.icon(this@MainActivity))
        }
        miniPlayerTitle.setTextIfChanged(session.title.ifBlank { getString(R.string.playing_video) })
        miniPlayerChannel.setTextIfChanged(session.channel)
        miniPlayPauseButton.apply {
            setIcon(if (session.playing) R.drawable.ic_ui_pause else R.drawable.ic_ui_play)
            contentDescription = if (session.playing) getString(R.string.ui_pause) else getString(R.string.ui_play)
        }
        updateMiniProgress(session)
        updateBrowserChromeVisibility(currentPlayerUrl())
        updateBottomNavigation()
        updateSearchFilterRail()
        updateSleepTimerChrome()
    }

    private fun updateSleepTimerChrome() {
        if (!::sleepButton.isInitialized) return
        val sleepMinutes = if (::sleepTimerController.isInitialized) sleepTimerController.remainingMinutes() else 0
        sleepButton.setTextIfChanged(if (sleepMinutes > 0) getString(R.string.sleep_button, sleepMinutes) else getString(R.string.ui_sleep))
    }

    private fun configureMiniPlayerSeekBar() {
        val chrome = findViewById<SwipeDismissLayout>(R.id.miniPlayerChrome).also { it.enabledForDismiss = false }
        miniPlayerProgress.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    chrome.enabledForDismiss = false
                    miniChromeController.onInteractionStart()
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    chrome.enabledForDismiss = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    miniChromeController.onInteractionEnd()
                }
            }
            false
        }
        miniPlayerProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                miniSeekTracking = true
                chrome.enabledForDismiss = false
                miniChromeController.onInteractionStart()
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) renderedMiniProgress = progress.coerceIn(0, 1000)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                chrome.enabledForDismiss = false
                miniSeekTracking = false
                val bar = seekBar ?: return
                val duration = playbackRuntime.state.durationMs
                if (duration <= 0L) return
                val target = ((duration * bar.progress.toLong()) / 1000L).coerceIn(0L, duration)
                ++playerNavigationGeneration
                playbackRuntime.overridePosition(target)
                playbackRuntime.dispatch(PlaybackCommand.SeekTo(target))
                renderedMiniProgress = bar.progress
                miniChromeController.onInteractionEnd()
            }
        })
    }

    private fun updateMiniProgress(session: PlaybackSessionState, force: Boolean = false) {
        if (isDestroyed || isFinishing || !::miniPlayerProgress.isInitialized) return
        if (!force) {
            if (!::playerSurfaceController.isInitialized || !playerSurfaceController.minimized) return
            if (::miniChromeController.isInitialized && !miniChromeController.controlsVisible) return
        }
        val seekable = session.durationMs > 0L
        if (miniPlayerProgress.isEnabled != seekable) miniPlayerProgress.isEnabled = seekable
        if (miniSeekTracking) return
        val progress = if (seekable) {
            // Bridge reports are intentionally sparse (~18-20 s). Use the session clock to
            // extrapolate the current position when mini controls are revealed so long
            // playback sessions do not show a visibly stale progress thumb.
            val displayPosition = if (session.playing && ::playbackRuntime.isInitialized) {
                playbackRuntime.estimatedPositionMs()
            } else session.positionMs
            ((displayPosition.coerceIn(0L, session.durationMs) * 1000L) / session.durationMs).toInt()
        } else 0
        if (progress == renderedMiniProgress && !force) return
        renderedMiniProgress = progress
        miniPlayerProgress.progress = progress
    }

    private fun updateSearchFilterRail() {
        if (!::searchFilterRail.isInitialized) return
        val visible = browseRoute.destination == YouTubeDestination.SEARCH &&
            urlInput.visibility != View.VISIBLE &&
            (!playerSurfaceController.visible || playerSurfaceController.minimized) &&
            customView == null && !isInPictureInPictureMode
        val targetVisibility = if (visible) View.VISIBLE else View.GONE
        if (searchFilterRail.visibility != targetVisibility) searchFilterRail.visibility = targetVisibility
        if (!visible) {
            renderedSearchFilterKey = ""
            return
        }
        val state = currentSearchFilterState()
        val key = "$state|${AppTheme.isLight(this)}"
        if (key == renderedSearchFilterKey) return
        renderedSearchFilterKey = key
        renderSearchChip(searchFilterAll, state.type == SearchResultType.ALL)
        renderSearchChip(searchFilterVideos, state.type == SearchResultType.VIDEOS)
        renderSearchChip(searchFilterShorts, state.type == SearchResultType.SHORTS)
        renderSearchChip(searchFilterChannels, state.type == SearchResultType.CHANNELS)
        renderSearchChip(searchFilterPlaylists, state.type == SearchResultType.PLAYLISTS)
        val advanced = state.prioritize != SearchPrioritize.RELEVANCE ||
            state.uploadDate != SearchUploadDate.ANY || state.duration != SearchDuration.ANY
        renderSearchChip(searchFilterMore, advanced)
    }

    private fun renderSearchChip(button: Button, selected: Boolean) {
        if (button.isSelected == selected && button.tag == AppTheme.isLight(this)) return
        button.isSelected = selected
        button.tag = AppTheme.isLight(this)
        button.setBackgroundResource(if (selected) R.drawable.bg_search_filter_selected else R.drawable.bg_action)
        button.setTextColor(if (selected) AppTheme.selectedText(this) else AppTheme.primary(this))
        button.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(
            if (selected) AppTheme.selectedText(this) else AppTheme.icon(this)
        )
    }

    private fun updateBottomNavigation() {
        val destination = browseRoute.destination
        if (renderedNavigation == destination) return
        renderedNavigation = destination
        listOf(
            NavRender(navHomeButton, R.drawable.ic_nav_home, R.drawable.ic_nav_home_filled, YouTubeDestination.HOME, false),
            NavRender(navShortsButton, R.drawable.ic_nav_shorts, R.drawable.ic_nav_shorts_filled, YouTubeDestination.SHORTS, false),
            NavRender(navSubscriptionsButton, R.drawable.ic_nav_subscriptions, R.drawable.ic_nav_subscriptions_filled, YouTubeDestination.SUBSCRIPTIONS, false),
            NavRender(navLibraryButton, R.drawable.ic_nav_you, R.drawable.ic_nav_you_filled, YouTubeDestination.OTHER, true)
        ).forEach { item ->
            item.button.apply {
                // "You" is a native tab (LibraryActivity), so arbitrary web pages classified
                // as OTHER must not accidentally highlight it.
                val selected = !item.nativeOnly && destination == item.destination
                setCompoundDrawablesWithIntrinsicBounds(0, if (selected) item.selectedIcon else item.icon, 0, 0)
                val color = if (selected) AppTheme.primary(this@MainActivity) else AppTheme.tertiary(this@MainActivity)
                compoundDrawableTintList = android.content.res.ColorStateList.valueOf(color)
                setTextColor(color)
                isSelected = selected
            }
        }
    }

    /** Mirrors YouTube's feed chrome: scrolling down prioritizes content, scrolling up
     * immediately brings navigation back. The state machine is allocation-free because WebView
     * scroll callbacks can arrive at frame cadence. */
    private fun onBrowseScrolled(scrollY: Int, oldScrollY: Int) {
        if (scrollY != oldScrollY) {
            if (::startupWork.isInitialized) startupWork.noteInteraction()
            if (::homeRecommendations.isInitialized) homeRecommendations.noteFeedInteraction()
        }
        if (browseRoute.destination == YouTubeDestination.HOME && scrollY >= 0) savedHomeScrollY = scrollY
        if (!::bottomBar.isInitialized || !::playerSurfaceController.isInitialized ||
            !::feedChromeMotionPolicy.isInitialized) return

        val lockedVisible = playerSurfaceController.visible || customView != null ||
            isInPictureInPictureMode || browseRoute.destination == YouTubeDestination.SHORTS ||
            urlInput.visibility == View.VISIBLE

        when (feedChromeMotionPolicy.onScroll(scrollY, oldScrollY, lockedVisible)) {
            FeedChromeMotionPolicy.Action.HIDE -> setBrowseChromeHidden(true, animated = true)
            FeedChromeMotionPolicy.Action.SHOW -> setBrowseChromeHidden(false, animated = true)
            FeedChromeMotionPolicy.Action.NONE -> Unit
        }
    }

    private fun setBrowseChromeHidden(hidden: Boolean, animated: Boolean) {
        if (!::bottomBar.isInitialized) return
        // Feed scrolling must keep all five native navigation items on screen.
        // Only an actual fullscreen/PiP surface can request hidden chrome.
        val hide = hidden && (customView != null || isInPictureInPictureMode)
        if (hide && playerSurfaceController.visible) return

        val targetTranslation = if (hide) {
            (bottomBar.height.takeIf { it > 0 } ?: uiMetrics.px(R.dimen.ui_bottom_nav_height)).toFloat()
        } else 0f
        val alreadySettled = browseChromeHidden == hide &&
            bottomBar.visibility == View.VISIBLE &&
            kotlin.math.abs(bottomBar.translationY - targetTranslation) < 0.5f
        if (alreadySettled) return

        browseChromeHidden = hide
        bottomBar.animate().cancel()
        // bottomBar is an overlay in activity_main. Never set it GONE for scroll chrome: doing so
        // would resize the WebView and force Chromium to relayout/repaint the feed. PiP/fullscreen
        // can still explicitly set GONE because those are surface transitions, not feed scrolling.
        bottomBar.visibility = View.VISIBLE
        bottomBar.isClickable = !hide
        bottomBar.isFocusable = !hide
        bottomBar.importantForAccessibility = if (hide) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }

        if (!animated) {
            bottomBar.translationY = targetTranslation
            return
        }

        bottomBar.animate()
            .translationY(targetTranslation)
            .setDuration(if (hide) 150L else 170L)
            .setInterpolator(android.view.animation.PathInterpolator(0.20f, 0f, 0f, 1f))
            .start()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun onNetworkChanged(state: NetworkLinkState) {
        val previous = networkLinkState
        val onlineChanged = networkOnline != state.online
        val wasOnline = networkOnline
        val qualityProfileChanged = networkMetered != state.metered
        val activeLinkChanged = previous.online && state.online &&
            previous.networkId.isNotBlank() && state.networkId.isNotBlank() &&
            previous.networkId != state.networkId

        networkLinkState = state
        networkOnline = state.online
        networkMetered = state.metered
        val session = playbackRuntime.state
        playbackHealth.setOnline(state.online, session.hasSession)

        if (::networkRecovery.isInitialized) networkRecovery.onLinkState(state)

        if (!state.online && (onlineChanged || wasOnline)) {
            recoveryDiagnostics.recordOfflineInterruption()
            if (webViewLifecycle.foreground) {
                Toast.makeText(this, getString(R.string.ui_offline_playback_recovery_paused), Toast.LENGTH_SHORT).show()
            }
        } else if (state.online && onlineChanged) {
            if (webViewLifecycle.foreground) Toast.makeText(this, getString(R.string.ui_back_online), Toast.LENGTH_SHORT).show()
            if (session.videoId.isNotBlank()) refreshCommunitySegments(session.videoId, force = true)
        }

        // Quality profile changes are independent from playback recovery. A seamless active-link
        // handoff can keep Android "online" throughout; only apply the tiny runtime quality command
        // here and let NetworkRecoveryCoordinator verify the existing media socket separately.
        if ((qualityProfileChanged || onlineChanged || activeLinkChanged) && state.online && playerSurfaceController.visible) {
            val qualityScript = AdBlockScript.setPreferredQuality(effectivePreferredQuality())
            webView.evaluateJavascript(qualityScript, null)
            if (webViewLifecycle.foreground && (qualityProfileChanged || activeLinkChanged)) {
                val profile = if (networkMetered) getString(R.string.ui_mobile_metered) else getString(R.string.ui_wi_fi_unmetered)
                Toast.makeText(this, getString(R.string.quality_selected,profile,qualityLabel(effectivePreferredQuality())), Toast.LENGTH_SHORT).show()
            }
        }

        if (::searchSuggestions.isInitialized && (onlineChanged || activeLinkChanged)) searchSuggestions.onNetworkChanged()
        syncShortsRuntimePolicy(forceTrim = false)
        refreshUi()
    }

    private fun buildNetworkRecoveryCheckpoint(): NetworkRecoveryCheckpoint? {
        if (!::playbackRuntime.isInitialized || !::webView.isInitialized || rendererGone) return null
        val session = playbackRuntime.state
        if (!session.hasSession || session.videoId.isBlank()) return null
        val url = currentPlayerUrl()
        val route = YouTubeRoute.parse(url)
        if (!route.isNativePlayback || route.videoId != session.videoId || !YouTubeAdapter.isTrustedBridgeUrl(url)) return null
        return NetworkRecoveryCheckpoint(
            videoId = session.videoId,
            url = url,
            positionMs = playbackRuntime.estimatedPositionMs(),
            durationMs = session.durationMs,
            wasPlaying = session.playing,
            playbackRate = session.playbackRate,
            repeatEnabled = session.repeatEnabled,
            capturedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
        )
    }

    private fun applyNetworkRecoveryCheckpoint(checkpoint: NetworkRecoveryCheckpoint) {
        if (!networkOnline || !activityForeground || rendererGone || isDestroyed || isFinishing) return
        val activeRoute = YouTubeRoute.parse(currentPlayerUrl())
        if (activeRoute.videoId != checkpoint.videoId || !activeRoute.isNativePlayback) return
        val current = playbackRuntime.state
        // Stage 1 deliberately does not seek. The native session's raw bridge position may be
        // several seconds old, so seeking against it before the first post-reconnect heartbeat
        // could create a false jump. Reassert cheap controls now; onBridgeState performs a seek
        // only if the renderer later reports an actual position regression.
        playbackRuntime.dispatch(PlaybackCommand.SetRate(checkpoint.playbackRate))
        playbackRuntime.dispatch(PlaybackCommand.SetRepeat(checkpoint.repeatEnabled))
        if (checkpoint.wasPlaying && !current.playing) playbackRuntime.dispatch(PlaybackCommand.Play)
    }

    private fun applyNetworkRestoreInstruction(instruction: NetworkRestoreInstruction) {
        playbackRuntime.dispatch(PlaybackCommand.SetRate(instruction.playbackRate))
        playbackRuntime.dispatch(PlaybackCommand.SetRepeat(instruction.repeatEnabled))
        instruction.seekToMs?.let { target ->
            playbackRuntime.overridePosition(target)
            playbackRuntime.dispatch(PlaybackCommand.SeekTo(target))
        }
        if (instruction.play) playbackRuntime.dispatch(PlaybackCommand.Play)
    }

    private fun refreshShortsMemoryPressureFromSystem() {
        if (!::preferences.isInitialized || !preferences.memoryHardening) {
            shortsMemoryPressure = ShortsMemoryPressure.NORMAL
            return
        }
        shortsMemoryPressure = runCatching {
            val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            if (info.lowMemory) ShortsMemoryPressure.LOW else ShortsMemoryPressure.NORMAL
        }.getOrDefault(shortsMemoryPressure)
    }

    private fun currentShortsRuntimePolicy(): ShortsRuntimePolicy = ShortsRuntimePolicyResolver.resolve(
        online = networkOnline,
        metered = networkMetered,
        powerConstrained = shortsPowerConstrained,
        lowRamDevice = shortsLowRamDevice,
        memoryPressure = shortsMemoryPressure,
        memoryHardening = ::preferences.isInitialized && preferences.memoryHardening
    )

    private fun syncShortsRuntimePolicy(forceTrim: Boolean) {
        if (!::browseWebView.isInitialized || browseRendererGone || browseRoute.destination != YouTubeDestination.SHORTS) return
        val policy = currentShortsRuntimePolicy()
        val key = listOf(
            policy.previousPreload.webValue,
            policy.nextPreload.webValue,
            policy.hardReleaseDistant,
            policy.trimImages,
            policy.retainedVideoPressure,
            preferences.shortsAutoHideChrome
        ).joinToString("|")
        if (key != shortsRuntimePolicyKey) {
            shortsRuntimePolicyKey = key
            try {
                browseWebView.evaluateJavascript(
                    ShortsResourceGuardScript.configure(policy, autoHideChrome = preferences.shortsAutoHideChrome),
                    null
                )
            } catch (_: Exception) {}
        }
        if (forceTrim) {
            try {
                browseWebView.evaluateJavascript(
                    ShortsResourceGuardScript.trim(aggressive = true, trimImages = policy.trimImages),
                    null
                )
            } catch (_: Exception) {}
        }
    }

    private fun memoryPressureForTrimLevel(level: Int): ShortsMemoryPressure = when {
        level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> ShortsMemoryPressure.CRITICAL
        level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> ShortsMemoryPressure.LOW
        level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> ShortsMemoryPressure.MODERATE
        level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> ShortsMemoryPressure.MODERATE
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> ShortsMemoryPressure.CRITICAL
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> ShortsMemoryPressure.LOW
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> ShortsMemoryPressure.MODERATE
        else -> ShortsMemoryPressure.NORMAL
    }

    private fun renderPlaybackHealth(snapshot: PlaybackHealthSnapshot) {
        when (snapshot.state) {
            PlaybackHealthState.OFFLINE -> {
                errorTitle.text = getString(R.string.ui_you_re_offline)
                errorMessage.text = getString(R.string.offline_recovery)
                errorOverlay.visibility = View.VISIBLE
                findViewById<Button>(R.id.errorRetryButton).isEnabled = false
            }
            PlaybackHealthState.FAILED -> {
                errorTitle.text = getString(R.string.ui_playback_needs_attention)
                errorMessage.text = snapshot.reason.takeIf { it.isNotBlank() }?.let { LocalizedPresentation.diagnosticDetail(this, it) } ?: getString(R.string.ui_automatic_recovery_could_not_restore_this_page)
                errorOverlay.visibility = View.VISIBLE
                findViewById<Button>(R.id.errorRetryButton).isEnabled = networkOnline
            }
            else -> {
                errorOverlay.visibility = View.GONE
                findViewById<Button>(R.id.errorRetryButton).isEnabled = networkOnline
            }
        }
    }

    private fun playerBasePolicyScript(): String {
        val key = buildString {
            append(policyFingerprint())
            append('|').append(filterEngine.pageWhitelisted)
            append('|').append(effectivePreferredQuality())
            append('|').append(uiLanguageTag)
            append('|').append(shortsPowerConstrained)
        }
        if (key == cachedPlayerPolicyKey && cachedPlayerPolicyScript.isNotEmpty()) return cachedPlayerPolicyScript
        cachedPlayerPolicyKey = key
        cachedPlayerPolicyScript = AdBlockScript.build(
            preferences, rulePackManager.active(), filterEngine.pageWhitelisted,
            preferredQualityOverride = effectivePreferredQuality()
        ) + "\n" + AdBlockScript.setPowerConstrained(shortsPowerConstrained) + "\n" +
            sharedEarlyScriptPolicy() + "\n" +
            ClientSurfaceScript.player(
                downloadLabel = getString(R.string.ui_download),
                optionsLabel = getString(R.string.ui_more),
                lightTheme = preferences.lightTheme
            )
        return cachedPlayerPolicyScript
    }

    private fun browseBasePolicyScript(): String {
        val discoveryEnabled = preferences.personalizedSuggestions && preferences.rememberHistory
        val key = buildString {
            append(policyFingerprint())
            append('|').append(effectivePreferredQuality())
            append('|').append(discoveryEnabled)
            append('|').append(uiLanguageTag)
            append('|').append(shortsPowerConstrained)
            append('|').append(memoryPressureTier.name)
        }
        if (key == cachedBrowsePolicyKey && cachedBrowsePolicyScript.isNotEmpty()) return cachedBrowsePolicyScript
        cachedBrowsePolicyKey = key
        cachedBrowsePolicyScript = AdBlockScript.build(
            preferences, rulePackManager.active(), pageWhitelisted = false,
            preferredQualityOverride = effectivePreferredQuality()
        ) + "\n" + AdBlockScript.setPowerConstrained(shortsPowerConstrained) + "\n" +
            sharedEarlyScriptPolicy() + "\n" + DiscoveryScript.build(discoveryEnabled) +
            "\n" + ClientSurfaceScript.browse(preferences.lightTheme) + "\n" + BrowseNavigationScript.build() +
            "\n" + BrowseResourceGuardScript.install() + "\n" + ShortsResourceGuardScript.install() +
            "\n" + SearchPreviewScript.build() + "\n" + BrowseResourceGuardScript.memoryPressure(memoryPressureTier) +
            "\n" + SearchPreviewScript.memoryPressure(memoryPressureTier)
        return cachedBrowsePolicyScript
    }

    private fun syncFeedMemoryPressure(tier: MemoryPressureTier) {
        if (!::browseWebView.isInitialized || browseRendererGone) return
        val script = BrowseResourceGuardScript.memoryPressure(tier) + "\n" + SearchPreviewScript.memoryPressure(tier)
        try { browseWebView.evaluateJavascript(script, null) } catch (_: Exception) {}
    }

    /**
     * Drop reconstructible Chromium navigation state before touching the visible document/media.
     * Browse Back/Forward remains available through BrowseHistoryCoordinator's bounded native ring.
     */
    private fun compactWebViewNavigationForPressure(tier: MemoryPressureTier) {
        if (tier == MemoryPressureTier.NORMAL || !::preferences.isInitialized || !preferences.memoryHardening) return

        if (::browseWebView.isInitialized && !browseRendererGone) {
            val current = browseWebView.url.orEmpty()
            if (current.isNotBlank() && YouTubeAdapter.isTrustedBridgeUrl(current)) {
                maybeScheduleBrowseHistoryCompaction(current, tier)
            }
        }

        // The dedicated Watch surface never exposes Chromium history to app navigation. Under
        // pressure, keeping old Watch entries only retains reconstructible renderer/page state.
        if (::webView.isInitialized && playerWebViewConfigured && !rendererGone) {
            val historySize = runCatching { webView.copyBackForwardList().size }.getOrDefault(0)
            if (WebViewSessionCompactionPolicy.shouldCompactPlayerHistory(historySize, false, tier)) {
                try { webView.clearHistory() } catch (_: Exception) {}
            }
        }
    }

    private fun syncPowerConstrainedPolicy() {
        val script = AdBlockScript.setPowerConstrained(shortsPowerConstrained)
        if (::webView.isInitialized && playerWebViewConfigured && !rendererGone) {
            try { webView.evaluateJavascript(script, null) } catch (_: Exception) {}
        }
        if (::browseWebView.isInitialized && !browseRendererGone) {
            try { browseWebView.evaluateJavascript(script, null) } catch (_: Exception) {}
        }
    }

    private fun invalidatePolicyScriptCache() {
        cachedPlayerPolicyKey = ""
        cachedPlayerPolicyScript = ""
        cachedBrowsePolicyKey = ""
        cachedBrowsePolicyScript = ""
    }

    private fun applyLongSessionMaintenance(maintenance: LongSessionMaintenance) {
        if (!maintenance.trimPolicyScriptCaches || longSessionMaintenanceTask != null) return
        // Cache maintenance is deliberately off the navigation/video-change callback. Those
        // transitions already ask Chromium for compositor/media work; clearing reproducible
        // native policy strings a few hundred milliseconds later keeps that frame path lean.
        val task = Runnable {
            longSessionMaintenanceTask = null
            if (isDestroyed || isFinishing) return@Runnable
            invalidatePolicyScriptCache()
            AdBlockScript.trimCache()
        }
        longSessionMaintenanceTask = task
        window.decorView.postDelayed(task, if (shortsPowerConstrained) 700L else 320L)
    }

    private fun refreshFilterPolicies() {
        if (::filterEngine.isInitialized) filterEngine.refresh()
        if (::browseFilterEngine.isInitialized) browseFilterEngine.refresh()
    }

    private fun policyFingerprint(): String = listOf(
        preferences.shieldEnabled,
        preferences.safeMode,
        preferences.blockShorts,
        preferences.blockRecommendations,
        preferences.blockComments,
        preferences.blockEndScreen,
        preferences.blockOpenInApp,
        preferences.backgroundControls,
        preferences.screenOffPlayback,
        preferences.autoRepeat,
        preferences.playbackSpeed,
        preferences.amoledTheme,
        preferences.lightTheme,
        preferences.preferredQuality,
        preferences.preferredQualityMobile,
        preferences.communitySponsorSkip,
        preferences.skipIntrosOutros,
        preferences.personalizedSuggestions,
        preferences.rememberHistory,
        rulePackManager.active().ruleVersion,
        preferences.whitelistedChannels.sorted().joinToString(",")
    ).joinToString("|")

    private fun isTrustedBridgeOrigin(): Boolean = !rendererGone && YouTubeAdapter.isTrustedBridgeUrl(currentPlayerUrl())


    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val tier = MemoryPressurePolicy.fromTrimLevel(level)
        val escalated = tier.ordinal > memoryPressureTier.ordinal
        if (escalated) memoryPressureTier = tier
        if (::preferences.isInitialized && preferences.memoryHardening) {
            syncFeedMemoryPressure(memoryPressureTier)
            if (memoryPressureTier.atLeast(MemoryPressureTier.MODERATE)) {
                compactWebViewNavigationForPressure(memoryPressureTier)
            }
        }
        if (::playerRecoveryVisual.isInitialized) playerRecoveryVisual.trimMemory(level)
        if (escalated && tier.atLeast(MemoryPressureTier.MODERATE)) {
            // Large generated JS strings and ranking work are disposable; release them before
            // touching playback state or renderer-backed media buffers.
            invalidatePolicyScriptCache()
            AdBlockScript.trimCache()
            if (::homeRecommendations.isInitialized) homeRecommendations.trimMemory(tier)
        }
        if (::webViewLifecycle.isInitialized) webViewLifecycle.onTrimMemory(level)
        if (escalated && ::preferences.isInitialized && preferences.memoryHardening && playerWebViewConfigured && !rendererGone) {
            syncPlayerMediaRetention("trim memory ${memoryPressureTier.name.lowercase()}")
        }
        if (::preferences.isInitialized && preferences.memoryHardening) {
            val pressure = memoryPressureForTrimLevel(level)
            if (pressure.ordinal > shortsMemoryPressure.ordinal) shortsMemoryPressure = pressure
            if (browseRoute.destination == YouTubeDestination.SHORTS &&
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) {
                syncShortsRuntimePolicy(forceTrim = pressure.ordinal >= ShortsMemoryPressure.LOW.ordinal)
            } else if (::browseWebView.isInitialized && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                // Non-Shorts browse previews are disposable under real pressure. Keep the SPA
                // itself alive, but stop media and downgrade its preload without load()/src reset.
                try { browseWebView.evaluateJavascript(BrowseResourceGuardScript.suspend(), null) } catch (_: Exception) {}
            }
        }
        if (::preferences.isInitialized && preferences.memoryHardening &&
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            if (::browseWebView.isInitialized && browseSurfaceSuppressed) {
                try { browseWebView.clearCache(false) } catch (_: Exception) {}
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        memoryPressureTier = MemoryPressureTier.CRITICAL
        if (::preferences.isInitialized && preferences.memoryHardening) {
            syncFeedMemoryPressure(MemoryPressureTier.CRITICAL)
            compactWebViewNavigationForPressure(MemoryPressureTier.CRITICAL)
        }
        invalidatePolicyScriptCache()
        AdBlockScript.trimCache()
        if (::homeRecommendations.isInitialized) homeRecommendations.trimMemory(MemoryPressureTier.CRITICAL)
        if (::webViewLifecycle.isInitialized) webViewLifecycle.onLowMemory()
        if (::preferences.isInitialized && preferences.memoryHardening && playerWebViewConfigured && !rendererGone) {
            syncPlayerMediaRetention("low-memory callback")
        }
        if (::browseWebView.isInitialized && ::preferences.isInitialized && preferences.memoryHardening) {
            shortsMemoryPressure = ShortsMemoryPressure.CRITICAL
            if (browseRoute.destination == YouTubeDestination.SHORTS) {
                syncShortsRuntimePolicy(forceTrim = true)
            } else {
                try { browseWebView.evaluateJavascript(BrowseResourceGuardScript.suspend(), null) } catch (_: Exception) {}
            }
            try { browseWebView.clearCache(false) } catch (_: Exception) {}
        }
    }

    private fun scheduleDeferredStartup(playbackVisibleAtLaunch: Boolean) {
        if (!::startupWork.isInitialized) return
        val schedule = StartupSchedulingPolicy.resolve(playbackVisibleAtLaunch)

        startupWork.defer(
            key = "home-first-content-warmup",
            delayMs = schedule.localContentWarmupMs,
            yieldToInteraction = true
        ) {
            if (!playbackVisibleAtLaunch && ::homeRecommendations.isInitialized &&
                browseRoute.destination == YouTubeDestination.HOME && !browseRendererGone) {
                homeRecommendations.prepareForFirstPaint()
            }
        }

        startupWork.defer(
            key = "runtime-observers",
            delayMs = schedule.runtimeObserversMs,
            requireForeground = true
        ) {
            startupRuntimeObserversReady = true
            if (::deviceRuntimeMonitor.isInitialized) deviceRuntimeMonitor.start()
            if (::networkStateMonitor.isInitialized) networkStateMonitor.start()
        }

        startupWork.defer(
            key = "content-enrichment",
            delayMs = schedule.contentEnrichmentMs,
            requireForeground = true,
            yieldToInteraction = true
        ) {
            // Local Home ranking is already overlapped with browse loading by the first-content
            // warmup task. Keep this stage for non-critical library/player enrichment only.
            if (::libraryStore.isInitialized) readPlaybackLibraryState(includeResume = false)
            if (::playbackRuntime.isInitialized && playerSurfaceController.visible) {
                playbackRuntime.dispatch(PlaybackCommand.SetRepeat(preferences.autoRepeat))
                playbackRuntime.dispatch(PlaybackCommand.SetRate(preferences.playbackSpeed))
            }
        }

        startupWork.defer(
            key = "startup-diagnostics",
            delayMs = schedule.diagnosticsMs,
            yieldToInteraction = true
        ) {
            if (::runtimeDiagnostics.isInitialized && ::devicePolicy.isInitialized) {
                runtimeDiagnostics.recordDeviceProfile(devicePolicy.summary)
            }
        }

        startupWork.defer(
            key = "notification-permission",
            delayMs = schedule.notificationPermissionMs,
            requireForeground = true,
            yieldToInteraction = true
        ) {
            requestNotificationPermissionIfNeeded()
        }

        startupWork.defer(
            key = "local-maintenance",
            delayMs = schedule.localMaintenanceMs,
            yieldToInteraction = true
        ) {
            writeLibrary {
                libraryStore.repairQueue()
                val integrity = libraryStore.quickIntegrityCheck()
                runtimeDiagnostics.recordLibraryCheck(integrity.summary, integrity.healthy)
                OfflineCleanupService.schedule(applicationContext)
                OfflineStore(applicationContext).cleanup()
            }
        }

        startupWork.defer(
            key = "rule-update-check",
            delayMs = schedule.ruleUpdateMs,
            requireForeground = true,
            yieldToInteraction = true
        ) {
            if (networkOnline) checkRuleUpdatesIfDue()
        }

        startupWork.defer(
            key = "app-update-check",
            delayMs = schedule.appUpdateMs,
            requireForeground = true,
            yieldToInteraction = true
        ) {
            if (networkOnline) AppStartupUpdateChecker.check(this)
        }
    }

    private fun checkRuleUpdatesIfDue() {
        if (!preferences.autoRuleUpdates) return
        val url = preferences.ruleUpdateUrl.trim()
        if (url.isBlank()) return
        val now = System.currentTimeMillis()
        val intervalMs = 24L * 60L * 60L * 1000L
        if (now - preferences.lastRuleUpdateCheckMs < intervalMs) return
        preferences.lastRuleUpdateCheckMs = now

        RulePackUpdater.update(url, rulePackManager) { result ->
            if (!result.installed) return@update
            preferences.safeMode = false
            preferences.safeModeReason = ""
            compatibilityMonitor.reset()
            refreshFilterPolicies()
            lastPolicyFingerprint = policyFingerprint()
            Toast.makeText(this, getString(R.string.rules_updated,result.version), Toast.LENGTH_SHORT).show()
            if (::webView.isInitialized && playerWebViewConfigured && !rendererGone &&
                playerSurfaceController.visible) webView.reload()
            refreshUi()
        }
    }
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !preferences.notificationPermissionAsked) {
            // Record before opening the dialog, including dismissal or process death.
            // Rationale also detects a denial made before this preference existed.
            val alreadyDecided = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            preferences.notificationPermissionAsked = true
            if (alreadyDecided) return
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
    }

    private fun handlePlaybackCommand(command: PlaybackCommand) {
        if (command is PlaybackCommand.Stop || command is PlaybackCommand.SeekBack ||
            command is PlaybackCommand.SeekForward || command is PlaybackCommand.SeekTo) {
            ++playerNavigationGeneration
        }
        if (isInPictureInPictureMode) {
            when (command) {
                PlaybackCommand.Play, PlaybackCommand.QueueNext -> pipPlaybackWanted = true
                PlaybackCommand.Pause, is PlaybackCommand.Stop -> pipPlaybackWanted = false
                PlaybackCommand.Toggle -> pipPlaybackWanted = !playbackRuntime.state.playing
                else -> Unit
            }
        }
        playbackRuntime.dispatch(command)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("app_language_tag",uiLanguageTag)
        saveBrowseHistorySnapshot(outState)
        outState.putBoolean(STATE_BROWSE_RENDERER_RECOVERY, browseRendererGone)
        outState.putBoolean(STATE_PLAYER_RENDERER_RECOVERY, rendererGone)
        if (rendererGone || pendingPlayerRendererRestore) {
            outState.putString(STATE_PLAYER_RECOVERY_VIDEO_ID, rendererRecoveryPlayerVideoId.ifBlank { playbackRuntime.state.videoId })
            outState.putLong(STATE_PLAYER_RECOVERY_POSITION_MS, rendererRecoveryPlayerPositionMs.coerceAtLeast(0L))
            outState.putBoolean(STATE_PLAYER_RECOVERY_PLAYING, rendererRecoveryPlayerWasPlaying)
        }

        if (::browseWebView.isInitialized || rendererRecoveryBrowseUrl.isNotBlank()) {
            try {
                val liveUrl = if (!browseRendererGone && ::browseWebView.isInitialized) browseWebView.url.orEmpty() else ""
                val currentBrowseUrl = liveUrl.ifBlank { rendererRecoveryBrowseUrl.ifBlank { browseRoute.url } }
                val currentScrollY = if (browseRendererGone) rendererRecoveryBrowseScrollY
                    else if (::browseWebView.isInitialized) browseWebView.scrollY.coerceAtLeast(0) else 0
                outState.putString(STATE_BROWSE_URL, currentBrowseUrl)
                outState.putString(STATE_BROWSE_RECOVERY_URL, currentBrowseUrl)
                outState.putInt(STATE_BROWSE_RECOVERY_SCROLL_Y, currentScrollY)

                val route = YouTubeRoute.parse(currentBrowseUrl)
                val historySize = if (!browseRendererGone && ::browseWebView.isInitialized) {
                    runCatching { browseWebView.copyBackForwardList().size }.getOrDefault(0)
                } else 0
                if (!browseRendererGone && WebViewSessionCompactionPolicy.shouldSerializeBrowseState(
                        historySize = historySize,
                        shorts = route.destination == YouTubeDestination.SHORTS,
                        rendererRecovering = false,
                        historyCompacted = ::browseHistory.isInitialized && browseHistory.compacted,
                        pressure = memoryPressureTier
                    )) {
                    val browseState = Bundle()
                    browseWebView.saveState(browseState)
                    outState.putBundle(STATE_BROWSE_WEBVIEW, browseState)
                }
            } catch (_: Exception) {}
        }
        if ((playerWebViewConfigured && ::webView.isInitialized) || rendererRecoveryPlayerUrl.isNotBlank()) {
            try {
                val liveUrl = if (playerWebViewConfigured && !rendererGone && ::webView.isInitialized) webView.url.orEmpty() else ""
                val currentPlayerUrl = liveUrl.ifBlank { rendererRecoveryPlayerUrl.ifBlank { playerRoute.url } }
                outState.putString(STATE_PLAYER_URL, currentPlayerUrl)
                outState.putString(STATE_PLAYER_SURFACE, playerSurfaceController.state.name)

                val historySize = if (playerWebViewConfigured && !rendererGone && ::webView.isInitialized) {
                    runCatching { webView.copyBackForwardList().size }.getOrDefault(0)
                } else 0
                if (playerWebViewConfigured && !rendererGone && WebViewSessionCompactionPolicy.shouldSerializePlayerState(historySize, false, memoryPressureTier)) {
                    val playerState = Bundle()
                    webView.saveState(playerState)
                    outState.putBundle(STATE_PLAYER_WEBVIEW, playerState)
                }
            } catch (_: Exception) {}
        }
        surfaceBeforePip?.let { outState.putString(STATE_SURFACE_BEFORE_PIP, it) }
        if (isInPictureInPictureMode || surfaceBeforePip != null) {
            outState.putBoolean(STATE_PIP_PLAYBACK_WANTED, pipPlaybackWanted || playbackRuntime.state.playing)
        }
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("GestureBackNavigation") // API < 33 fallback; newer devices use OnBackInvokedCallback.
    override fun onBackPressed() {
        handleBackNavigation()
    }

    private fun handleBackNavigation() {
        if (::urlInput.isInitialized && urlInput.visibility == View.VISIBLE) {
            exitSearchMode()
            return
        }
        if (::searchSuggestions.isInitialized && searchSuggestions.dismiss()) return
        when {
            customView != null -> exitFullscreen()
            playerSurfaceController.expanded -> minimizePlayer()
            canNavigateBrowseBack() -> navigateBrowseBack()
            playbackRuntime.state.playing && preferences.backgroundControls -> moveTaskToBack(true)
            playerSurfaceController.minimized -> closePlayer()
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    finishAfterTransition()
                } else {
                    @Suppress("DEPRECATION")
                    super.onBackPressed()
                }
            }
        }
    }

    private data class RendererExitEvidence(
        val guardActive: Boolean,
        val enteringSafeMode: Boolean
    )

    private fun recordRendererExitEvidence(source: String, didCrash: Boolean): RendererExitEvidence {
        val now = android.os.SystemClock.elapsedRealtime()
        val sameRendererBurst = lastRendererExitEvidenceAt > 0L &&
            now - lastRendererExitEvidenceAt <= 250L &&
            lastRendererExitEvidenceDidCrash == didCrash

        if (!sameRendererBurst) {
            recoveryDiagnostics.recordRendererGone(didCrash, source)
            rendererCrashGuard.recordRendererExit(didCrash)
            runtimeDiagnostics.recordRestore(
                "$source renderer ${if (didCrash) "crashed" else "terminated"}",
                false
            )
            lastRendererExitEvidenceAt = now
            lastRendererExitEvidenceDidCrash = didCrash
            lastRendererExitEnabledSafeMode = false
        }

        val guardActive = rendererCrashGuard.isGuardActive()
        val enteringSafeMode = if (sameRendererBurst) {
            lastRendererExitEnabledSafeMode
        } else {
            guardActive && !preferences.safeMode
        }
        if (enteringSafeMode && !preferences.safeMode) {
            preferences.safeMode = true
            preferences.safeModeReason = "Renderer crash-loop protection enabled; automatic reload recovery is paused."
            lastRendererExitEnabledSafeMode = true
        }
        return RendererExitEvidence(guardActive, enteringSafeMode)
    }

    private fun handleRendererGone(source: String, didCrash: Boolean, affectsPlayer: Boolean, delayMs: Long) {
        runOnUiThread {
            if (activityTearingDown || isDestroyed || isFinishing) return@runOnUiThread

            val evidence = recordRendererExitEvidence(source, didCrash)
            if (!affectsPlayer) {
                val browseVisible = activityForeground &&
                    !playerSurfaceController.expanded && !isInPictureInPictureMode
                val action = BrowseRendererRehydrationPolicy.resolve(
                    BrowseRendererRecoveryContext(
                        affectsPlayer = false,
                        guardActive = evidence.guardActive,
                        enteringSafeMode = evidence.enteringSafeMode,
                        activityForeground = activityForeground,
                        browseVisible = browseVisible
                    )
                )
                browseRendererAutoRecoveryAllowed = action != BrowseRendererRecoveryAction.WAIT_FOR_MANUAL_RETRY
                when (action) {
                    BrowseRendererRecoveryAction.REHYDRATE_NOW -> {
                        val reclaimSettleMs = if (didCrash) 0L else 180L
                        scheduleBrowseRendererRehydrate(
                            delayMs = delayMs + reclaimSettleMs + if (evidence.enteringSafeMode) 220L else 0L,
                            reason = if (didCrash) "browse renderer crash" else "browse renderer reclaim"
                        )
                    }
                    BrowseRendererRecoveryAction.DEFER_UNTIL_VISIBLE -> {
                        runtimeDiagnostics.recordRestore("browse renderer recovery deferred until visible", false)
                    }
                    BrowseRendererRecoveryAction.WAIT_FOR_MANUAL_RETRY -> {
                        statusText.text = getString(R.string.ui_browse_renderer_recovery_paused)
                        Toast.makeText(this, getString(R.string.ui_browse_renderer_recovery_paused), Toast.LENGTH_LONG).show()
                    }
                    BrowseRendererRecoveryAction.RECREATE_ACTIVITY -> Unit
                }
                if (evidence.enteringSafeMode) {
                    Toast.makeText(this, getString(R.string.ui_browse_renderer_recovery_paused), Toast.LENGTH_LONG).show()
                }
                return@runOnUiThread
            }

            // Keep the Activity/browser tree alive. Only the dead playback host is replaced;
            // PlaybackSessionCoordinator, queue/history state and browse scroll/history remain intact.
            playerRendererRehydrateRunnable?.let(window.decorView::removeCallbacks)
            playerRendererRehydrateRunnable = null
            if (!rendererGone) {
                rendererGone = true
                if (playbackRuntime.state.hasSession) {
                    playbackRuntime.onNavigationStarted(rendererRecoveryPlayerVideoId)
                    playbackRuntime.publish(preferences.backgroundControls)
                }
                if (playbackRuntime.state.hasSession || playerSurfaceController.visible) {
                    playbackHealth.recoveryStarted(
                        if (didCrash) "Player renderer crashed" else "Player renderer was reclaimed",
                        1
                    )
                } else {
                    playbackHealth.reset()
                }
            }
            clearFullscreenForRendererTeardown()

            val target = rendererRecoveryPlayerUrl.takeIf {
                YouTubeAdapter.isTrustedBridgeUrl(it) && YouTubeRoute.parse(it).isNativePlayback
            }
            val action = PlayerRendererRehydrationPolicy.resolve(
                PlayerRendererRecoveryContext(
                    guardActive = evidence.guardActive,
                    enteringSafeMode = evidence.enteringSafeMode,
                    activityForeground = activityForeground,
                    pictureInPicture = isInPictureInPictureMode,
                    hasRecoverableTarget = target != null,
                    playerVisible = playerSurfaceController.visible,
                    hasPlaybackSession = playbackRuntime.state.hasSession
                )
            )
            playerRendererAutoRecoveryAllowed = action != PlayerRendererRecoveryAction.WAIT_FOR_MANUAL_RETRY
            when (action) {
                PlayerRendererRecoveryAction.REHYDRATE_NOW -> {
                    val reclaimSettleMs = if (didCrash) 0L else 220L
                    schedulePlayerRendererRehydrate(
                        delayMs = delayMs + reclaimSettleMs + if (evidence.enteringSafeMode) 220L else 0L,
                        reason = if (didCrash) "player renderer crash" else "player renderer reclaim"
                    )
                }
                PlayerRendererRecoveryAction.DEFER_UNTIL_FOREGROUND -> {
                    runtimeDiagnostics.recordRestore("player renderer recovery deferred until foreground", false)
                }
                PlayerRendererRecoveryAction.DEFER_UNTIL_NEEDED -> {
                    runtimeDiagnostics.recordRestore("player renderer recovery deferred until needed", false)
                }
                PlayerRendererRecoveryAction.WAIT_FOR_MANUAL_RETRY -> {
                    if (::playerRecoveryVisual.isInitialized) playerRecoveryVisual.hide(animated = false)
                    renderPlaybackHealth(playbackHealth.snapshot)
                    statusText.text = getString(R.string.ui_renderer_crash_loop_guard_active_tap_retry_to_restart_player)
                    Toast.makeText(this, getString(R.string.ui_renderer_crash_loop_guard_active_tap_retry_to_restart_player), Toast.LENGTH_LONG).show()
                }
            }
            if (evidence.enteringSafeMode) {
                Toast.makeText(this, getString(R.string.ui_renderer_crash_loop_guard_active_tap_retry_to_restart_player), Toast.LENGTH_LONG).show()
            }

        }
    }

    private fun schedulePlayerRendererRehydrate(delayMs: Long, reason: String) {
        if (!rendererGone || !playerRendererAutoRecoveryAllowed || activityTearingDown ||
            isDestroyed || isFinishing || (!activityForeground && !isInPictureInPictureMode)) return
        if (playerRendererRehydrateRunnable != null) return
        val target = rendererRecoveryPlayerUrl.takeIf {
            YouTubeAdapter.isTrustedBridgeUrl(it) && YouTubeRoute.parse(it).isNativePlayback
        } ?: return

        val task = Runnable {
            playerRendererRehydrateRunnable = null
            if (!rendererGone || !playerRendererAutoRecoveryAllowed ||
                (!activityForeground && !isInPictureInPictureMode) || isDestroyed || isFinishing) return@Runnable
            if (!YouTubeAdapter.isTrustedBridgeUrl(target)) return@Runnable
            rehydratePlayerWebView(reason)
        }
        playerRendererRehydrateRunnable = task
        if (delayMs <= 0L) window.decorView.postOnAnimation(task)
        else window.decorView.postDelayed(task, delayMs)
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun rehydratePlayerWebView(reason: String) {
        if (!rendererGone || isDestroyed || isFinishing) return
        val targetUrl = rendererRecoveryPlayerUrl.takeIf {
            YouTubeAdapter.isTrustedBridgeUrl(it) && YouTubeRoute.parse(it).isNativePlayback
        } ?: return
        val surface = findViewById<PlayerSurfaceLayout>(R.id.playerSurface)
        var replacement: PlaybackWebView? = null
        try {
            val newWebView = PlaybackWebView(this).apply {
                id = R.id.webView
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            replacement = newWebView
            surface.addView(newWebView, 0)
            webView = newWebView

            playerController = PlayerController(newWebView)
            // Keep commands gated until the replacement document reports the expected media
            // element. The bridge callback performs the single recovery activation.
            playbackBackend.rebind(WebViewPlaybackBackend(playerController), ready = false)
            rendererGone = false
            playerRendererAutoRecoveryAllowed = false
            playerImagesEnabled = true
            playerImageResumeTask?.let(window.decorView::removeCallbacks)
            playerImageResumeTask = null
            miniSurfaceApplied = false
            playerWebViewConfigured = false

            configureWebView()
            webViewLifecycle.rebindWebView(newWebView, "player renderer rehydrated")
            rendererRecoveryPlayerSurface.takeIf { it.isNotBlank() }?.let(playerSurfaceController::restore)
            playerRoute = YouTubeRoute.parse(targetUrl)
            newWebView.loadUrl(AppLanguage.youtubeUrl(this, targetUrl))
            runtimeDiagnostics.recordRestore("player renderer rehydrated in place: $reason", true)
            refreshUi()

            // A shared Chromium process may have taken browse down in the same burst. Recover it
            // only if it is actually visible; otherwise the existing browse policy defers it.
            if (browseRendererGone && activityForeground && !playerSurfaceController.expanded &&
                !isInPictureInPictureMode && browseRendererAutoRecoveryAllowed) {
                scheduleBrowseRendererRehydrate(0L, "shared renderer recovery")
            }
        } catch (_: Exception) {
            replacement?.let { failed -> runCatching { destroyWebView(failed, "VideoShieldBridge") } }
            rendererGone = true
            playbackBackend.detach()
            playerRendererAutoRecoveryAllowed = false
            runtimeDiagnostics.recordRestore("player renderer in-place rehydration failed", false)
            fallbackToActivityRendererRecovery()
        }
    }

    private fun fallbackToActivityRendererRecovery() {
        if (!activityRecreationGate.request() || isDestroyed || isFinishing) return
        rendererRecreateRunnable?.let(window.decorView::removeCallbacks)
        val task = Runnable {
            rendererRecreateRunnable = null
            if (!isDestroyed && !isFinishing) recreate()
        }
        rendererRecreateRunnable = task
        window.decorView.postDelayed(task, 280L)
    }

    private fun scheduleBrowseRendererRehydrate(delayMs: Long, reason: String) {
        if (!browseRendererGone || rendererGone || !browseRendererAutoRecoveryAllowed ||
            activityTearingDown || isDestroyed || isFinishing) return
        if (!activityForeground || playerSurfaceController.expanded || isInPictureInPictureMode) return
        if (browseRendererRehydrateRunnable != null) return

        val task = Runnable {
            browseRendererRehydrateRunnable = null
            if (!browseRendererGone || rendererGone || !browseRendererAutoRecoveryAllowed ||
                !activityForeground || playerSurfaceController.expanded || isInPictureInPictureMode ||
                isDestroyed || isFinishing) return@Runnable
            rehydrateBrowseWebView(reason)
        }
        browseRendererRehydrateRunnable = task
        if (delayMs <= 0L) window.decorView.postOnAnimation(task)
        else window.decorView.postDelayed(task, delayMs)
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun rehydrateBrowseWebView(reason: String) {
        if (!browseRendererGone || rendererGone || isDestroyed || isFinishing) return
        val targetUrl = pendingBrowseNavigationUrl.takeIf { YouTubeAdapter.isTrustedBridgeUrl(it) }
            ?: rendererRecoveryBrowseUrl.takeIf { YouTubeAdapter.isTrustedBridgeUrl(it) }
            ?: browseHistory.current()?.takeIf { YouTubeAdapter.isTrustedBridgeUrl(it) }
            ?: browseRoute.url.takeIf { YouTubeAdapter.isTrustedBridgeUrl(it) }
            ?: ShieldPreferences.HOME_URL
        val targetScrollY = if (pendingBrowseNavigationUrl.isNotBlank()) 0
            else rendererRecoveryBrowseScrollY.coerceAtLeast(0)

        cancelBrowseHistoryCompaction()
        browseImageResumeTask?.let(window.decorView::removeCallbacks)
        browseImageResumeTask = null
        homePullRefreshLayout.finishRefresh()

        var replacement: WebView? = null
        try {
            val newWebView = WebView(this).apply {
                id = R.id.browseWebView
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            replacement = newWebView
            // The dead WebView was already removed/destroyed by ShieldWebViewClient. Insert the
            // replacement below the pull-to-refresh indicator and rebind that layout's content.
            homePullRefreshLayout.addView(newWebView, 0)
            homePullRefreshLayout.bindContentView(newWebView)
            browseWebView = newWebView

            browseWebViewPaused = false
            browseSurfaceSuppressed = false
            browseImagesEnabled = true
            browseRendererGone = false
            browseRendererAutoRecoveryAllowed = false
            pendingBrowseRecoveryUrl = targetUrl
            pendingBrowseRecoveryScrollY = targetScrollY

            configureBrowseWebView()
            bindBrowseScrollListener()
            syncBrowseWebViewActivity()
            newWebView.loadUrl(AppLanguage.youtubeUrl(this, targetUrl))
            runtimeDiagnostics.recordRestore("browse renderer rehydrated in place: $reason", true)
            refreshUi()
        } catch (_: Exception) {
            replacement?.let { failed ->
                runCatching { destroyWebView(failed, "YouTooBeeDiscovery", "VoTuibeNavigation") }
            }
            browseRendererGone = true
            browseRendererAutoRecoveryAllowed = false
            runtimeDiagnostics.recordRestore("browse renderer in-place rehydration failed", false)
            statusText.text = getString(R.string.ui_browse_renderer_recovery_paused)
        }
    }

    /** Drop fullscreen renderer-owned views without calling back into a dead Chromium process. */
    private fun clearFullscreenForRendererTeardown() {
        val view = customView ?: return
        runCatching { fullscreenContainer.removeView(view) }
        customView = null
        customViewCallback = null
        fullscreenContainer.visibility = View.GONE
        fullscreenContainer.alpha = 1f
        fullscreenContainer.translationX = 0f
        fullscreenContainer.translationY = 0f
        fullscreenContainer.scaleX = 1f
        fullscreenContainer.scaleY = 1f
        runCatching { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        findViewById<View>(android.R.id.content).requestApplyInsets()
        if (!isInPictureInPictureMode && customView == null) {
            findViewById<PlayerSurfaceLayout>(R.id.playerSurface).post {
                if (!isDestroyed && !isFinishing && !isInPictureInPictureMode) {
                    findViewById<PlayerSurfaceLayout>(R.id.playerSurface).settleInsideHostBounds()
                }
            }
        }
        refreshUi()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==SaveVideoController.REQUEST_DESTINATION) saveVideo.destination(resultCode,data)
    }

    override fun onDestroy() {
        activityForeground = false
        activityTearingDown = true
        rendererRecreateRunnable?.let(window.decorView::removeCallbacks)
        rendererRecreateRunnable = null
        browseRendererRehydrateRunnable?.let(window.decorView::removeCallbacks)
        browseRendererRehydrateRunnable = null
        playerRendererRehydrateRunnable?.let(window.decorView::removeCallbacks)
        playerRendererRehydrateRunnable = null
        warmResumeTask?.let(window.decorView::removeCallbacks)
        warmResumeTask = null
        cancelPlayerMediaTrim()
        activityRecreationGate.cancel()
        clearFullscreenForRendererTeardown()
        if (::startupWork.isInitialized) startupWork.close()
        if (::browseSession.isInitialized) browseSession.close()
        if (::stats.isInitialized) stats.flush()
        if (::playbackScreenOn.isInitialized) playbackScreenOn.update(false)
        quickActionSheet?.dismiss()
        EqDialog.dismiss(this)
        if (::saveVideo.isInitialized) saveVideo.close()
        if (::searchSuggestions.isInitialized) searchSuggestions.close()
        if (::miniChromeController.isInitialized) miniChromeController.close()
        if (::playerRecoveryVisual.isInitialized) playerRecoveryVisual.close()
        window.decorView.removeCallbacks(uiRefreshFrame)
        uiRefreshPending = false
        cancelRendererImageResumes()
        longSessionMaintenanceTask?.let(window.decorView::removeCallbacks)
        longSessionMaintenanceTask = null
        cancelBrowseHistoryCompaction()
        if (::homeRecommendations.isInitialized) homeRecommendations.close()
        browseNavigationBridge?.close()
        browseNavigationBridge = null
        earlyPlayerScript?.remove(); earlyPlayerScript = null
        earlyBrowseScript?.remove(); earlyBrowseScript = null
        earlyScriptPolicy = null
        if (::playerSurfaceController.isInitialized) playerSurfaceController.close()
        libraryTasks.shutdownAfter { if (::libraryStore.isInitialized) libraryStore.close() }
        if (::playerController.isInitialized) playerController.release()
        if (::videoShieldBridge.isInitialized) videoShieldBridge.close()
        if (isFinishing && ::playbackRuntime.isInitialized) playbackRuntime.stopService()
        if (::sleepTimerController.isInitialized) sleepTimerController.dispose()
        if (::playbackInactivity.isInitialized) playbackInactivity.dispose()
        if (::playbackRecovery.isInitialized) playbackRecovery.dispose()
        if (::networkRecovery.isInitialized) networkRecovery.dispose()
        if (::networkStateMonitor.isInitialized) networkStateMonitor.stop()
        if (::deviceRuntimeMonitor.isInitialized) deviceRuntimeMonitor.stop()
        if (::webViewLifecycle.isInitialized) webViewLifecycle.release("activity destroyed")
        if (::commandRouter.isInitialized) commandRouter.stop()
        if (::communitySegmentClient.isInitialized) communitySegmentClient.close()
        if (::webView.isInitialized) {
            destroyWebView(webView, "VideoShieldBridge")
        }
        if (::browseWebView.isInitialized) {
            destroyWebView(browseWebView, "YouTooBeeDiscovery", "VoTuibeNavigation")
        }
        super.onDestroy()
    }

    /** Break Java/Kotlin callback chains before asking Chromium to destroy the renderer view. */
    private fun destroyWebView(target: WebView, vararg bridgeNames: String) {
        try {
            target.setOnScrollChangeListener(null)
            bridgeNames.forEach { name -> runCatching { target.removeJavascriptInterface(name) } }
            // Clients commonly close over Activity state. Replace them before loading blank so
            // renderer callbacks emitted during teardown cannot retain or re-enter this Activity.
            target.webViewClient = android.webkit.WebViewClient()
            target.webChromeClient = WebChromeClient()
            runCatching { target.onPause() }
            (target.parent as? ViewGroup)?.removeView(target)
            target.stopLoading()
            runCatching { target.settings.javaScriptEnabled = false }
            target.loadUrl("about:blank")
            target.clearHistory()
            target.removeAllViews()
            target.destroy()
        } catch (_: Exception) {}
    }
    private data class NavRender(
        val button: Button,
        val icon: Int,
        val selectedIcon: Int,
        val destination: YouTubeDestination,
        val nativeOnly: Boolean
    )

    companion object {
        private const val HISTORY_PROGRESS_PERSIST_INTERVAL_MS = 20_000L
        private const val RESTORE_SNAPSHOT_MAX_AGE_MS = 12L * 60L * 60L * 1000L
        private const val STATE_BROWSE_WEBVIEW = "browse_webview_state"
        private const val STATE_BROWSE_URL = "browse_url"
        private const val STATE_PLAYER_WEBVIEW = "player_webview_state"
        private const val STATE_PLAYER_URL = "player_url"
        private const val STATE_PLAYER_SURFACE = "player_surface_state"
        private const val STATE_SURFACE_BEFORE_PIP = "surface_before_pip"
        private const val STATE_PIP_PLAYBACK_WANTED = "pip_playback_wanted"
        private const val STATE_BROWSE_HISTORY = "browse_history"
        private const val STATE_BROWSE_HISTORY_INDEX = "browse_history_index"
        private const val STATE_BROWSE_HISTORY_COMPACTED = "browse_history_compacted"
        private const val STATE_BROWSE_RECOVERY_URL = "browse_recovery_url"
        private const val STATE_BROWSE_RECOVERY_SCROLL_Y = "browse_recovery_scroll_y"
        private const val STATE_BROWSE_RENDERER_RECOVERY = "browse_renderer_recovery"
        private const val STATE_PLAYER_RENDERER_RECOVERY = "player_renderer_recovery"
        private const val STATE_PLAYER_RECOVERY_VIDEO_ID = "player_renderer_recovery_video_id"
        private const val STATE_PLAYER_RECOVERY_POSITION_MS = "player_renderer_recovery_position_ms"
        private const val STATE_PLAYER_RECOVERY_PLAYING = "player_renderer_recovery_playing"
    }

}
