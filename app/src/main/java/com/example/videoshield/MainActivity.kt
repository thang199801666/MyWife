package com.example.videoshield

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Intent
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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import java.net.URLEncoder

class MainActivity : LocalizedActivity() {
    private var quickActionSheet: android.app.Dialog? = null
    private val libraryWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val habitTracker = ViewingHabitTracker()
    private var homeRecommendationRequest = 0L

    private fun refreshHomeRecommendations() {
        if (!::browseWebView.isInitialized || isDestroyed || isFinishing || libraryWorker.isShutdown) return
        val request = ++homeRecommendationRequest
        if (YouTubeRoute.parse(browseWebView.url).destination != YouTubeDestination.HOME) return
        val enabled = preferences.personalizedSuggestions && preferences.rememberHistory
        val since = preferences.recommendationsSince
        libraryWorker.execute {
            val rows = if (enabled) runCatching { libraryStore.recommendations(since).take(12) }.getOrDefault(emptyList()) else emptyList()
            val script = HomeRecommendationsScript.build(enabled, rows.map { it.copy(reason=LocalizedPresentation.recommendation(this,it.reason)) },getString(R.string.ui_for_you),getString(R.string.home_hint))
            runOnUiThread {
                if (!isDestroyed && !isFinishing && request == homeRecommendationRequest &&
                    enabled == (preferences.personalizedSuggestions && preferences.rememberHistory) &&
                    since == preferences.recommendationsSince &&
                    YouTubeAdapter.isTrustedBridgeUrl(browseWebView.url) &&
                    YouTubeRoute.parse(browseWebView.url).destination == YouTubeDestination.HOME) {
                    browseWebView.evaluateJavascript(script, null)
                }
            }
        }
    }

    private fun writeLibrary(action: () -> Unit) {
        if (isDestroyed || libraryWorker.isShutdown) return
        libraryWorker.execute { try { action() } catch (e: Exception) { android.util.Log.w("YouTooBee", "Local library write failed", e) } }
    }

    private fun installDiscovery(view: WebView) {
        view.addJavascriptInterface(DiscoveryBridge(
            allowed = { !isDestroyed && !isFinishing && preferences.personalizedSuggestions && preferences.rememberHistory && YouTubeAdapter.isTrustedBridgeUrl(view.url) },
            accept = { payload ->
                val capturedAt = System.currentTimeMillis()
                writeLibrary {
                    if (preferences.personalizedSuggestions && preferences.rememberHistory && capturedAt >= preferences.recommendationsSince)
                        libraryStore.saveCandidates(DiscoveryBridge.parse(payload, capturedAt))
                    runOnUiThread { refreshHomeRecommendations() }
                }
            }
        ), "YouTooBeeDiscovery")
    }
    private lateinit var webView: WebView
    private lateinit var browseWebView: WebView
    private lateinit var playerSurfaceController: PlayerSurfaceController
    private var miniSurfaceApplied = false
    private var earlyPlayerScript: androidx.webkit.ScriptHandler? = null
    private var earlyBrowseScript: androidx.webkit.ScriptHandler? = null
    private var earlyScriptPolicy: String? = null
    private lateinit var playbackScreenOn: PlaybackScreenOnController
    private var uiRefreshPending = false
    private var renderedNavigation: YouTubeDestination? = null
    private val uiRefreshFrame = Runnable {
        uiRefreshPending = false
        if (!isDestroyed && !isFinishing) renderUi()
    }
    private lateinit var urlInput: EditText
    private lateinit var searchSuggestions: SearchSuggestionsController
    private lateinit var saveVideo: SaveVideoController
    private lateinit var filterToggle: ToggleButton
    private lateinit var statusText: TextView
    private lateinit var channelButton: Button
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var fullscreenContainer: FullscreenGestureLayout

    private lateinit var preferences: ShieldPreferences
    private lateinit var rulePackManager: RulePackManager
    private lateinit var filterEngine: FilterEngine
    private lateinit var browseFilterEngine: FilterEngine
    private lateinit var compatibilityMonitor: CompatibilityMonitor
    private lateinit var stats: ShieldStats
    private lateinit var libraryStore: LibraryStore
    private lateinit var playerController: PlayerController
    private lateinit var playbackBackend: PlaybackBackend
    private lateinit var sleepTimerController: SleepTimerController
    private lateinit var playbackRecovery: PlaybackRecoveryController
    private lateinit var recoveryDiagnostics: RecoveryDiagnosticsStore
    private lateinit var compatibilityDiagnostics: CompatibilityDiagnosticsStore
    private lateinit var networkStateMonitor: NetworkStateMonitor
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

    private lateinit var errorOverlay: View
    private lateinit var errorTitle: TextView
    private lateinit var errorMessage: TextView

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var suppressShieldToggle = false
    private var lastPolicyFingerprint = ""
    private var currentSubscribed = false
    private var currentFavorite = false
    private var currentQueued = false
    private var queueCountCache = 0
    private var resumeAppliedVideoId = ""
    private var pendingResumeRead: PlaybackReadKey? = null
    private var libraryFlagsRequest = 0L
    private var playerNavigationGeneration = 0L
    private var speedAppliedVideoId = ""
    private var communitySegmentRequestKey = ""
    private var networkOnline = true
    private var networkMetered = false
    private var rendererGone = false
    private var browseRoute = YouTubeRoute.parse(ShieldPreferences.HOME_URL)
    private var playerRoute = YouTubeRoute.parse(ShieldPreferences.HOME_URL)
    private var surfaceBeforePip: String? = null
    private var playingBeforePip = false
    private var pipPlaybackWanted = false


    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackScreenOn = PlaybackScreenOnController(window)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        setContentView(R.layout.activity_main)
        SystemBarInsets.install(this) { isInPictureInPictureMode }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                OnBackInvokedCallback { handleBackNavigation() }
            )
        }

        preferences = ShieldPreferences(this)
        rulePackManager = RulePackManager(this)
        filterEngine = FilterEngine(preferences, rulePackManager)
        browseFilterEngine = FilterEngine(preferences, rulePackManager)
        stats = ShieldStats(this)
        libraryStore = LibraryStore(this)
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
        runtimeDiagnostics.recordDeviceProfile(devicePolicy.summary)
        writeLibrary {
            libraryStore.repairQueue()
            val integrity = libraryStore.quickIntegrityCheck()
            runtimeDiagnostics.recordLibraryCheck(integrity.summary, integrity.healthy)
        }
        compatibilityMonitor = CompatibilityMonitor(preferences, rulePackManager) { reason, rolledBack ->
            runOnUiThread {
                val suffix = if (rolledBack) " Previous rules restored." else ""
                Toast.makeText(this, "$reason.$suffix", Toast.LENGTH_LONG).show()
                val channel = playbackSession.state.channel
                filterEngine.pageWhitelisted = channel.isNotBlank() && preferences.isChannelWhitelisted(channel)
                lastPolicyFingerprint = policyFingerprint()
                webView.reload()
                refreshUi()
            }
        }
        lastPolicyFingerprint = policyFingerprint()

        webView = findViewById(R.id.webView)
        browseWebView = findViewById(R.id.browseWebView)
        playerSurfaceController = PlayerSurfaceController(
            this,
            findViewById(R.id.playerSurface),
            findViewById(R.id.miniPlayerChrome)
        )
        findViewById<PlayerSurfaceLayout>(R.id.playerSurface).apply {
            isMini = { playerSurfaceController.minimized && !isInPictureInPictureMode }
            onVideoTap = { expandPlayer() }
        }
        errorOverlay = findViewById(R.id.errorOverlay)
        errorTitle = findViewById(R.id.errorTitle)
        errorMessage = findViewById(R.id.errorMessage)
        playerController = PlayerController(webView)
        playbackBackend = WebViewPlaybackBackend(playerController)
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
                if (!isFinishing && networkOnline && YouTubeAdapter.isTrustedBridgeUrl(webView.url)) {
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
        networkStateMonitor = NetworkStateMonitor(this) { online ->
            runOnUiThread { onNetworkChanged(online) }
        }
        networkOnline = networkStateMonitor.currentOnline()
        networkMetered = networkStateMonitor.currentMetered()
        playbackWakeLock = PlaybackWakeLockController(this) { held, reason ->
            runtimeDiagnostics.recordWakeLock(held, reason)
        }
        webViewLifecycle = WebViewLifecycleCoordinator(
            webView = webView,
            preferences = preferences,
            runtimeDiagnostics = runtimeDiagnostics,
            wakeLock = playbackWakeLock,
            devicePolicy = devicePolicy,
            sessionState = { playbackSession.state },
            isInPictureInPicture = { isInPictureInPictureMode },
            isRendererGone = { rendererGone }
        )
        deviceRuntimeMonitor = DeviceRuntimeMonitor(this) { state, reason ->
            runOnUiThread { webViewLifecycle.onDeviceRuntimeChanged(state, reason) }
        }
        webViewLifecycle.onDeviceRuntimeChanged(deviceRuntimeMonitor.currentState(), "initial-state")
        sleepTimerController = SleepTimerController(
            preferences = preferences,
            onExpired = {
                playbackBackend.pause()
                Toast.makeText(this, getString(R.string.ui_sleep_timer_finished), Toast.LENGTH_SHORT).show()
            },
            onTick = { refreshUi() }
        )
        urlInput = findViewById(R.id.urlInput)
        saveVideo = SaveVideoController(this)
        OfflineCleanupService.schedule(this)
        writeLibrary { OfflineStore(this).cleanup() }
        searchSuggestions = SearchSuggestionsController(urlInput, findViewById(R.id.browserContainer)) { query ->
            urlInput.setText(query)
            navigateFromAddressBar()
        }
        filterToggle = findViewById(R.id.filterToggle)
        statusText = findViewById(R.id.statusText)
        channelButton = findViewById(R.id.channelButton)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        applyNativeTheme()
        playbackHealth.setOnline(networkOnline, false)
        fullscreenContainer.gesturesEnabled = preferences.fullscreenGestures
        fullscreenContainer.sensitivity = preferences.gestureSensitivity
        fullscreenContainer.doubleTapSeekSeconds = preferences.doubleTapSeekSeconds
        fullscreenContainer.bindFeedback(findViewById(R.id.fullscreenGestureFeedback))
        fullscreenContainer.callback = object : FullscreenGestureLayout.Callback {
            override fun exitFullscreen() {
                this@MainActivity.exitFullscreen()
                minimizePlayer()
            }
            override fun currentPositionMs(): Long = playbackSession.estimatedPositionMs(preferences.playbackSpeed)
            override fun currentDurationMs(): Long = playbackSession.state.durationMs
            override fun seekToMs(positionMs: Long) {
                ++playerNavigationGeneration
                playbackSession.overridePosition(positionMs)
                playbackBackend.seekToMs(positionMs)
            }
        }

        configureWebView()
        configureBrowseWebView()
        bindUi()
        commandRouter = PlayerCommandRouter(this) { command, position -> handlePlaybackCommand(command, position) }
        commandRouter.start()
        deviceRuntimeMonitor.start()
        sleepTimerController.restore()
        requestNotificationPermissionIfNeeded()

        val incoming = extractIncomingUrl(intent)
        val sameLanguage = savedInstanceState?.getString("app_language_tag") == AppLanguage.tag(this)
        val restoredBrowse = if (savedInstanceState != null && sameLanguage) {
            try {
                savedInstanceState.getBundle(STATE_BROWSE_WEBVIEW)?.let { browseWebView.restoreState(it) != null } == true
            } catch (_: Exception) { false }
        } else false
        val restoredPlayer = if (savedInstanceState != null && sameLanguage && !rendererGone) {
            try {
                savedInstanceState.getBundle(STATE_PLAYER_WEBVIEW)?.let { webView.restoreState(it) != null } == true
            } catch (_: Exception) { false }
        } else false
        if (savedInstanceState != null) {
            runtimeDiagnostics.recordRestore("browse WebView instance state", restoredBrowse)
            runtimeDiagnostics.recordRestore("player WebView instance state", restoredPlayer)
        }
        if (!restoredBrowse) browseWebView.loadUrl(AppLanguage.youtubeUrl(this,preferences.lastBrowseUrl))

        if (restoredPlayer) {
            playerSurfaceController.restore(savedInstanceState?.getString(STATE_PLAYER_SURFACE))
            playerRoute = YouTubeRoute.parse(webView.url)
        } else {
            val playbackRestore = if (incoming == null) playbackSession.recoverableUrl(preferences.resumePlayback, RESTORE_SNAPSHOT_MAX_AGE_MS) else null
            if (playbackRestore != null) runtimeDiagnostics.recordRestore("playback snapshot", true)
            val initialTarget = incoming ?: playbackRestore
            if (initialTarget != null) {
                navigateClient(initialTarget, expandPlayback = true)
            } else {
                playerSurfaceController.hide()
            }
        }
        if (incoming != null && restoredPlayer) navigateClient(incoming, expandPlayback = true)
        refreshUi()
        checkRuleUpdatesIfDue()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIncomingUrl(intent)?.let { navigateClient(it, expandPlayback = true) }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun configureWebView() {
        updateEarlyPlayerScripts()
        installDiscovery(webView)
        (webView as PlaybackWebView).apply {
            canSwipeMinimize = {
                playerSurfaceController.expanded &&
                    customView == null && !isInPictureInPictureMode && playerRoute.destination == YouTubeDestination.WATCH
            }
            onSwipeMinimize = { minimizePlayer() }
            onMinimizeDrag = { playerSurfaceController.previewMinimize(it) }
        }
        (webView as PlaybackWebView).keepActiveWhenHidden = {
            preferences.backgroundControls && preferences.screenOffPlayback &&
                playbackSession.state.hasSession && !isFinishing && !rendererGone
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

        webView.addJavascriptInterface(
            VideoShieldBridge(
                originAllowed = { isTrustedBridgeOrigin() },
                onPageAdsHidden = { count ->
                    stats.pageAdsHidden(count)
                    runOnUiThread { refreshUi() }
                },
                onAdSkipped = {
                    stats.adSkipped()
                    runOnUiThread { refreshUi() }
                },
                onSegmentSkipped = { category, durationMs ->
                    stats.segmentSkipped()
                    runOnUiThread {
                        if (::webViewLifecycle.isInitialized && webViewLifecycle.foreground) {
                            val seconds = (durationMs / 1000L).coerceAtLeast(1L)
                            Toast.makeText(this, getString(R.string.segment_skipped,segmentCategoryLabel(category),seconds), Toast.LENGTH_SHORT).show()
                        }
                        refreshUi()
                    }
                },
                onPlaybackState = { playing, title, channel, channelUrl, videoId, positionMs, durationMs ->
                    runOnUiThread { onPlaybackState(playing, title, channel, channelUrl, videoId, positionMs, durationMs) }
                },
                onPlaybackEnded = { videoId ->
                    runOnUiThread { onPlaybackEnded(videoId) }
                },
                onDownloadRequested = { showCurrentDownload() },
                onQualitySelected = { quality -> saveManualQuality(quality); refreshUi() },
                onCompatibilityReport = { playerFound, videoFound, scriptErrors, ruleVersion ->
                    runOnUiThread {
                        val activeRuleVersion = rulePackManager.active().ruleVersion
                        compatibilityDiagnostics.recordReport(
                            webView.url,
                            playerFound,
                            videoFound,
                            scriptErrors,
                            ruleVersion,
                            activeRuleVersion
                        )
                        compatibilityMonitor.onReport(webView.url, playerFound, videoFound, scriptErrors, ruleVersion)
                    }
                }
            ),
            "VideoShieldBridge"
        )

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
                AdBlockScript.build(
                    preferences,
                    rulePackManager.active(),
                    filterEngine.pageWhitelisted,
                    preferredQualityOverride = effectivePreferredQuality()
                ) + "\n" + EarlyAdScript.build(preferences) + "\n" + DiscoveryScript.build(preferences.personalizedSuggestions && preferences.rememberHistory) + "\n" + ClientSurfaceScript.player(getString(R.string.ui_download)) +
                    (if (isInPictureInPictureMode) "\n" + ClientSurfaceScript.pip(true, pipPlaybackWanted) else "") +
                    (if (playerSurfaceController.minimized) "\n" + ClientSurfaceScript.mini(true, playbackSession.state.playing) else "")
            },
            onBlocked = {
                stats.networkBlocked()
                runOnUiThread { refreshUi() }
            },
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
                playerRoute = YouTubeRoute.parse(url)
                playbackSession.onNavigationStarted()
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
                runOnUiThread { refreshUi() }
            },
            onPageReady = { url ->
                runOnUiThread { playbackHealth.pageReady(YouTubeRoute.parse(url).isPlayback) }
            },
            onMainFrameError = { message ->
                runOnUiThread {
                    val recoverable = networkOnline && preferences.playbackRecovery &&
                        playbackSession.state.hasSession && YouTubeAdapter.isTrustedBridgeUrl(webView.url)
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
            onRendererGone = { didCrash ->
                runOnUiThread {
                    if (rendererGone) return@runOnUiThread
                    rendererGone = true
                    if (::webViewLifecycle.isInitialized) webViewLifecycle.release("renderer gone")
                    recoveryDiagnostics.recordRendererGone(didCrash)
                    rendererCrashGuard.recordRendererExit(didCrash)
                    playbackHealth.recoveryExhausted(
                        if (didCrash) "WebView renderer crashed" else "WebView renderer was terminated"
                    )
                    if (rendererCrashGuard.isGuardActive()) {
                        preferences.safeMode = true
                        preferences.safeModeReason = "Renderer crash-loop protection enabled; automatic reload recovery is paused."
                        Toast.makeText(this, getString(R.string.ui_renderer_crash_loop_guard_active_tap_retry_to_restart_player), Toast.LENGTH_LONG).show()
                        renderPlaybackHealth(playbackHealth.snapshot)
                    } else {
                        Toast.makeText(this, getString(R.string.ui_player_restarted_safely), Toast.LENGTH_SHORT).show()
                        window.decorView.postDelayed({ if (!isFinishing) recreate() }, 250L)
                    }
                }
            },
            navigationInterceptor = { target ->
                val normalized = YouTubeAdapter.normalizeIncomingUrl(target) ?: target
                val route = YouTubeRoute.parse(normalized)
                if (YouTubeAdapter.isTrustedBridgeUrl(normalized) && !route.isPlayback) {
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
        browseWebView.setBackgroundColor(Color.BLACK)
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
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(browseWebView, true)
        }
        installDiscovery(browseWebView)
        browseWebView.addJavascriptInterface(BrowseNavigationBridge { target ->
            runOnUiThread {
                if (!isDestroyed && !isFinishing && YouTubeAdapter.isTrustedBridgeUrl(browseWebView.url)) {
                    promoteBrowsePlayback(target)
                }
            }
        }, "VoTuibeNavigation")
        browseWebView.webChromeClient = WebChromeClient()
        browseWebView.webViewClient = ShieldWebViewClient(
            filterEngine = browseFilterEngine,
            scriptProvider = {
                AdBlockScript.build(
                    preferences,
                    rulePackManager.active(),
                    pageWhitelisted = false,
                    preferredQualityOverride = effectivePreferredQuality()
                ) + "\n" + EarlyAdScript.build(preferences) + "\n" + DiscoveryScript.build(preferences.personalizedSuggestions && preferences.rememberHistory) + "\n" + ClientSurfaceScript.browse() + "\n" + BrowseNavigationScript.build() + "\n" + SearchPreviewScript.build()
            },
            onBlocked = {
                stats.networkBlocked()
                runOnUiThread { refreshUi() }
            },
            onUrlChanged = { url ->
                runOnUiThread {
                    val route = YouTubeRoute.parse(url)
                    if (route.isPlayback) {
                        promoteBrowsePlayback(url)
                        return@runOnUiThread
                    }
                    browseRoute = route
                    preferences.lastBrowseUrl = url
                    refreshHomeRecommendations()
                    if (!urlInput.hasFocus()) {
                        urlInput.setText("")
                        urlInput.hint = ClientChromePolicy.forRoute(
                            browseRoute,
                            fullscreen = false,
                            pictureInPicture = false
                        ).searchHint.let { if(it == "Search YouTube") getString(R.string.search_youtube) else it }
                    }
                    refreshUi()
                }
            },
            onNavigationStarted = { url ->
                val route = YouTubeRoute.parse(url)
                if (!route.isPlayback) browseRoute = route
                runOnUiThread { refreshUi() }
            },
            onPageReady = { runOnUiThread { refreshHomeRecommendations() } },
            onMainFrameError = { message ->
                runOnUiThread {
                    if (message.isNotBlank() && !playerSurfaceController.expanded) {
                        statusText.text = getString(R.string.browse_error,message)
                    }
                }
            },
            onNavigationBlocked = { target, reason ->
                runtimeDiagnostics.recordBlockedNavigation(target, "browse: $reason")
            },
            onRendererGone = { didCrash ->
                runOnUiThread {
                    runtimeDiagnostics.recordRestore(
                        if (didCrash) "browse renderer crashed" else "browse renderer terminated",
                        false
                    )
                    window.decorView.postDelayed({ if (!isFinishing) recreate() }, 180L)
                }
            },
            navigationInterceptor = { target ->
                val normalized = YouTubeAdapter.normalizeIncomingUrl(target) ?: target
                val route = YouTubeRoute.parse(normalized)
                if (route.isPlayback) {
                    runOnUiThread { openPlayer(normalized, expand = true) }
                    true
                } else {
                    false
                }
            }
        )
    }

    private fun bindUi() {
        findViewById<Button>(R.id.minimizePlayerButton).setLeadingIcon(R.drawable.ic_ui_minimize)
        findViewById<Button>(R.id.sleepButton).setLeadingIcon(R.drawable.ic_ui_timer)
        findViewById<Button>(R.id.pipButton).setLeadingIcon(R.drawable.ic_ui_pip)
        findViewById<TextView>(R.id.appBrand).setOnClickListener { showBrowseDestination(YouTubeRoute.HOME_URL) }
        findViewById<Button>(R.id.backButton).setOnClickListener { if (browseWebView.canGoBack()) browseWebView.goBack() }
        findViewById<Button>(R.id.forwardButton).setOnClickListener { if (browseWebView.canGoForward()) browseWebView.goForward() }
        findViewById<Button>(R.id.reloadButton).setOnClickListener { browseWebView.reload() }
        findViewById<Button>(R.id.goButton).setOnClickListener {
            if (urlInput.visibility != View.VISIBLE) {
                findViewById<View>(R.id.appBrand).visibility = View.GONE
                urlInput.visibility = View.VISIBLE
                urlInput.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(urlInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            } else navigateFromAddressBar()
        }
        findViewById<Button>(R.id.pipButton).setOnClickListener { enterPip() }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.subscribeButton).setOnClickListener { toggleCurrentSubscription() }
        findViewById<Button>(R.id.favoriteButton).setOnClickListener {
            showCurrentDownload()
        }
        findViewById<Button>(R.id.favoriteButton).setOnLongClickListener { toggleCurrentFavorite(); true }
        findViewById<Button>(R.id.queueButton).setOnClickListener { toggleCurrentQueue() }
        findViewById<Button>(R.id.speedButton).setOnClickListener { cyclePlaybackSpeed() }
        findViewById<Button>(R.id.qualityButton).setOnClickListener { showQualityDialog() }
        findViewById<Button>(R.id.repeatButton).setOnClickListener {
            preferences.autoRepeat = !preferences.autoRepeat
            applyPolicyAndRefresh()
            Toast.makeText(this, if (preferences.autoRepeat) getString(R.string.ui_repeat_enabled) else getString(R.string.ui_repeat_disabled), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.sleepButton).setOnClickListener { showSleepTimerDialog() }
        findViewById<Button>(R.id.minimizePlayerButton).setOnClickListener { minimizePlayer() }
        findViewById<Button>(R.id.autoNextButton).setOnClickListener {
            preferences.autoAdvanceQueue = !preferences.autoAdvanceQueue
            applyPolicyAndRefresh()
        }
        findViewById<Button>(R.id.miniExpandButton).setOnClickListener { expandPlayer() }
        findViewById<Button>(R.id.miniPlayPauseButton).setOnClickListener { playbackBackend.toggle() }
        findViewById<Button>(R.id.miniCloseButton).setOnClickListener { closePlayer() }
        findViewById<SwipeDismissLayout>(R.id.miniPlayerChrome).onDismiss = { closePlayer() }
        findViewById<TextView>(R.id.miniPlayerTitle).setOnClickListener { expandPlayer() }
        findViewById<TextView>(R.id.miniPlayerChannel).setOnClickListener { expandPlayer() }
        findViewById<Button>(R.id.navHomeButton).setOnClickListener { showBrowseDestination(YouTubeRoute.HOME_URL) }
        findViewById<Button>(R.id.navShieldButton).setOnClickListener { showBrowseDestination(YouTubeRoute.SHORTS_URL) }
        findViewById<Button>(R.id.navSubscriptionsButton).setOnClickListener { showBrowseDestination(YouTubeRoute.SUBSCRIPTIONS_URL) }
        findViewById<Button>(R.id.navLibraryButton).setOnClickListener { openLibrary(LibraryActivity.MODE_HISTORY) }
        findViewById<Button>(R.id.navCreateButton).setOnClickListener {
            quickActionSheet?.dismiss()
            quickActionSheet=ActionSheet.show(this,getString(R.string.app_name),listOf(
                ActionSheet.Action(getString(R.string.ui_for_you),getString(R.string.history_suggestions),R.drawable.ic_nav_home),
                ActionSheet.Action(getString(R.string.ui_queue),getString(R.string.next_videos),R.drawable.ic_ui_queue),
                ActionSheet.Action(getString(R.string.ui_favorites),getString(R.string.favorite_videos),R.drawable.ic_ui_bookmark),
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
                rendererGone = false
                recreate()
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
                preferences.lastUrl = ShieldPreferences.HOME_URL
                rendererGone = false
                recreate()
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
            lastPolicyFingerprint = policyFingerprint()
            updateEarlyPlayerScripts()
            if (playerSurfaceController.visible) webView.reload()
            browseWebView.reload()
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
        if (::searchSuggestions.isInitialized) searchSuggestions.resume()
        refreshHomeRecommendations()
        if (::browseWebView.isInitialized) try { browseWebView.onResume() } catch (_: Exception) {}
        if (::webViewLifecycle.isInitialized) webViewLifecycle.onActivityResumed()
        if (::rendererCrashGuard.isInitialized) rendererCrashGuard.markStable()
        if (::networkStateMonitor.isInitialized) networkStateMonitor.start()
        if (::playbackRecovery.isInitialized) playbackRecovery.setActive(true)
        if (::preferences.isInitialized) {
            applyNativeTheme()
            updateBrowserChromeVisibility(if (::webView.isInitialized) webView.url else null)
            if (::fullscreenContainer.isInitialized) {
                fullscreenContainer.gesturesEnabled = preferences.fullscreenGestures
                fullscreenContainer.sensitivity = preferences.gestureSensitivity
                fullscreenContainer.doubleTapSeekSeconds = preferences.doubleTapSeekSeconds
            }
            if (::libraryStore.isInitialized) {
                readPlaybackLibraryState(includeResume = false)
            }
            if (!preferences.backgroundControls) playbackSession.stopService()
            webViewLifecycle.updateWakeLock("activity resumed")
            val fingerprint = policyFingerprint()
            suppressShieldToggle = true
            filterToggle.isChecked = preferences.shieldEnabled
            suppressShieldToggle = false
            val channel = playbackSession.state.channel
            filterEngine.pageWhitelisted = channel.isNotBlank() && preferences.isChannelWhitelisted(channel)
            if (lastPolicyFingerprint.isNotEmpty() && fingerprint != lastPolicyFingerprint && ::webView.isInitialized) {
                lastPolicyFingerprint = fingerprint
                compatibilityMonitor.reset()
                updateEarlyPlayerScripts()
                if (playerSurfaceController.visible) webView.reload()
                browseWebView.reload()
            } else {
                lastPolicyFingerprint = fingerprint
                applyPolicyAndRefresh()
            }
        }
    }

    override fun onPause() {
        if (::playbackScreenOn.isInitialized) playbackScreenOn.update(false)
        if (::searchSuggestions.isInitialized) searchSuggestions.pause()
        window.decorView.removeCallbacks(uiRefreshFrame)
        uiRefreshPending = false
        if (::browseWebView.isInitialized) try { browseWebView.onPause() } catch (_: Exception) {}
        if (::webViewLifecycle.isInitialized) webViewLifecycle.onActivityPaused()
        if (::networkStateMonitor.isInitialized) networkStateMonitor.stop()
        if (::playbackRecovery.isInitialized) playbackRecovery.setActive(false)
        super.onPause()
    }

    private fun applyPolicyAndRefresh() {
        updateEarlyPlayerScripts()
        if (playerSurfaceController.visible) {
            webView.evaluateJavascript(
                AdBlockScript.build(
                    preferences,
                    rulePackManager.active(),
                    filterEngine.pageWhitelisted,
                    preferredQualityOverride = effectivePreferredQuality()
                ) + "\n" + EarlyAdScript.build(preferences) + "\n" + DiscoveryScript.build(preferences.personalizedSuggestions && preferences.rememberHistory) + "\n" + ClientSurfaceScript.player(getString(R.string.ui_download)),
                null
            )
        }
        browseWebView.evaluateJavascript(
            AdBlockScript.build(
                preferences,
                rulePackManager.active(),
                pageWhitelisted = false,
                preferredQualityOverride = effectivePreferredQuality()
            ) + "\n" + EarlyAdScript.build(preferences) + "\n" + DiscoveryScript.build(preferences.personalizedSuggestions && preferences.rememberHistory) + "\n" + ClientSurfaceScript.browse() + "\n" + BrowseNavigationScript.build(),
            null
        )
        refreshUi()
    }

    private fun updateEarlyPlayerScripts() {
        if (!androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val script = EarlyAdScript.build(preferences)
        if (earlyScriptPolicy == script && earlyPlayerScript != null && earlyBrowseScript != null) return
        val origins = setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com")
        earlyPlayerScript?.remove(); earlyBrowseScript?.remove()
        earlyPlayerScript = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(webView, script, origins)
        earlyBrowseScript = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(browseWebView, script, origins)
        earlyScriptPolicy = script
    }

    private fun navigateFromAddressBar() {
        searchSuggestions.dismiss()
        val target = NavigationTargetResolver.resolveAddressInput(urlInput.text.toString()) ?: return
        navigateClient(target, expandPlayback = true)
        urlInput.clearFocus()
        urlInput.setText("")
        urlInput.visibility = View.GONE
        findViewById<View>(R.id.appBrand).visibility = View.VISIBLE
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(urlInput.windowToken, 0)
    }

    private fun navigateClient(rawUrl: String, expandPlayback: Boolean) {
        val normalized = YouTubeAdapter.normalizeIncomingUrl(rawUrl) ?: rawUrl
        val route = YouTubeRoute.parse(normalized)
        if (route.isPlayback) {
            openPlayer(normalized, expandPlayback)
        } else {
            showBrowseDestination(normalized)
        }
    }

    private fun promoteBrowsePlayback(target: String) {
        if (!YouTubeAdapter.isTrustedBridgeUrl(target)) return
        val normalized = YouTubeAdapter.normalizeIncomingUrl(target) ?: return
        val route = YouTubeRoute.parse(normalized)
        if (!route.isPlayback || route.videoId.isBlank()) return
        // Multiple WebView/history callbacks can describe the same transition.
        if (!playerSurfaceController.expanded || playerRoute.videoId != route.videoId) openPlayer(normalized, expand = true)
        if (YouTubeRoute.parse(browseWebView.url).isPlayback) {
            browseWebView.evaluateJavascript("document.querySelectorAll('video').forEach(v=>v.pause())", null)
            val returnUrl = preferences.lastBrowseUrl.takeUnless { YouTubeRoute.parse(it).isPlayback } ?: YouTubeRoute.HOME_URL
            browseWebView.loadUrl(AppLanguage.youtubeUrl(this,returnUrl))
        }
    }

    private fun showBrowseDestination(url: String, minimizePlayer: Boolean = true) {
        if (minimizePlayer && playerSurfaceController.visible) {
            minimizePlayerSurface()
        }
        val normalized = YouTubeAdapter.normalizeIncomingUrl(url) ?: url
        browseRoute = YouTubeRoute.parse(normalized)
        preferences.lastBrowseUrl = normalized
        browseWebView.loadUrl(AppLanguage.youtubeUrl(this,normalized))
        refreshUi()
    }

    private fun showCurrentDownload() {
        if (isDestroyed || isFinishing) return
        val route = YouTubeRoute.parse(webView.url)
        val session = playbackSession.state
        if (route.isPlayback && route.videoId.isNotBlank()) {
            saveVideo.show("https://m.youtube.com/watch?v=${route.videoId}",
                session.title.takeIf { session.videoId == route.videoId }.orEmpty())
        }
    }

    private fun openPlayer(url: String, expand: Boolean = true) {
        val normalized = YouTubeAdapter.normalizeIncomingUrl(url) ?: url
        val route = YouTubeRoute.parse(normalized)
        if (!route.isPlayback) {
            showBrowseDestination(normalized)
            return
        }
        playerRoute = route
        if (expand) playerSurfaceController.expand() else minimizePlayerSurface()
        val current = webView.url.orEmpty()
        val currentId = YouTubeAdapter.videoIdFromUrl(current)
        if (currentId.isNullOrBlank() || currentId != route.videoId || !YouTubeAdapter.isTrustedBridgeUrl(current)) {
            webView.loadUrl(AppLanguage.youtubeUrl(this,normalized))
        }
        refreshUi()
    }

    private fun expandPlayer() {
        if (!playerSurfaceController.visible) return
        playerSurfaceController.expand()
        refreshUi()
    }

    private fun minimizePlayer() {
        if (!playerSurfaceController.visible) return
        minimizePlayerSurface()
        refreshUi()
    }

    private fun minimizePlayerSurface() {
        miniSurfaceApplied = true
        webView.evaluateJavascript(ClientSurfaceScript.mini(true, playbackSession.state.playing), null)
        try { webView.scrollTo(0, 0) } catch (_: Exception) {}
        playerSurfaceController.minimize()
    }

    private fun closePlayer() {
        if (!playerSurfaceController.visible && !playbackSession.state.hasSession) return
        playbackBackend.pause()
        playbackBackend.clearCommunitySegments()
        playbackSession.stop(clearSnapshot = true)
        playbackHealth.reset()
        playerRoute = YouTubeRoute.parse(ShieldPreferences.HOME_URL)
        playerSurfaceController.hide()
        errorOverlay.visibility = View.GONE
        try { webView.loadUrl("about:blank") } catch (_: Exception) {}
        refreshUi()
    }

    private fun onPlaybackState(
        playing: Boolean,
        title: String,
        channel: String,
        channelUrl: String,
        videoId: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val pageUrl = webView.url.orEmpty()
        val delta = playbackSession.acceptBridgeUpdate(
            playing = playing,
            title = title,
            channel = channel,
            channelUrl = channelUrl,
            videoId = videoId,
            positionMs = positionMs,
            durationMs = durationMs,
            pageUrl = pageUrl
        )
        val session = delta.current

        playbackRecovery.heartbeat(playing, positionMs)
        playbackHealth.heartbeat(playing, videoId)
        if (playing) recoveryDiagnostics.recordSuccessfulHeartbeat()
        updatePipActionsIfNeeded()
        webViewLifecycle.updateWakeLock("playback state")

        if (pendingResumeRead?.matches(videoId, playerNavigationGeneration) == false) pendingResumeRead = null
        if (delta.videoChanged) {
            webView.evaluateJavascript(AdBlockScript.build(preferences, rulePackManager.active(),
                filterEngine.pageWhitelisted, preferredQualityOverride = effectivePreferredQuality()), null)
            currentFavorite = false
            currentQueued = false
            readPlaybackLibraryState(includeResume = true)

            if (speedAppliedVideoId != videoId) {
                speedAppliedVideoId = videoId
                webView.postDelayed({ playbackBackend.setPlaybackRate(preferences.playbackSpeed) }, 250L)
            }
            refreshCommunitySegments(videoId)

        }

        if (pendingResumeRead == null && preferences.rememberHistory && videoId.isNotBlank() && YouTubeAdapter.isTrustedBridgeUrl(pageUrl)) {
            val now = System.currentTimeMillis()
            val watched = if (preferences.personalizedSuggestions) habitTracker.update(videoId, playing, positionMs, android.os.SystemClock.elapsedRealtime(), preferences.playbackSpeed.toDouble()) else { habitTracker.reset(); 0L }
            val item = VideoItem(videoId, title, channel, pageUrl, now, positionMs, durationMs)
            writeLibrary {
                if (preferences.rememberHistory && now >= preferences.historyClearedAt) {
                    libraryStore.recordHistory(item, preferences.maxHistoryItems)
                    if (preferences.personalizedSuggestions && now >= preferences.recommendationsSince) libraryStore.recordWatched(videoId, watched, now)
                }
            }
        } else {
            habitTracker.reset()
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
        refreshUi()
        playbackSession.publish(preferences.backgroundControls, preferences.playbackSpeed)
    }

    private fun readPlaybackLibraryState(includeResume: Boolean) {
        val session = playbackSession.state
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
                val latest = playbackSession.state
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
                                if (key.matches(playbackSession.state.videoId, playerNavigationGeneration) &&
                                    key.resumeTarget(saved, playbackSession.state.positionMs, preferences.resumePlayback, preferences.historyClearedAt) == target &&
                                    YouTubeRoute.parse(webView.url).videoId == key.videoId) playbackBackend.seekToMs(target)
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
        if (preferences.autoRepeat || !preferences.autoAdvanceQueue || videoId.isBlank()) return
        playNextFromQueue(manual = false, completedVideoId = videoId)
    }

    private fun playNextFromQueue(manual: Boolean, completedVideoId: String = playbackSession.state.videoId) {
        ++libraryFlagsRequest
        if (completedVideoId.isNotBlank() && libraryStore.removeFromQueue(completedVideoId)) {
            queueCountCache = (queueCountCache - 1).coerceAtLeast(0)
        }
        val next = libraryStore.popNext() ?: run {
            currentQueued = false
            if (manual) Toast.makeText(this, getString(R.string.ui_queue_is_empty), Toast.LENGTH_SHORT).show()
            else playSuggestedNext(completedVideoId)
            refreshUi()
            return
        }
        queueCountCache = (queueCountCache - 1).coerceAtLeast(0)
        currentQueued = false
        Toast.makeText(this, getString(R.string.up_next,next.title), Toast.LENGTH_SHORT).show()
        openPlayer(next.url, expand = playerSurfaceController.expanded)
        refreshUi()
    }

    private fun playSuggestedNext(completedId: String) {
        webView.evaluateJavascript(NextVideoScript.build(completedId)) { result ->
            if(!preferences.autoAdvanceQueue || preferences.autoRepeat || playbackSession.state.videoId!=completedId ||
                YouTubeAdapter.videoIdFromUrl(webView.url)!=completedId) return@evaluateJavascript
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
        val session = playbackSession.state
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
        val session = playbackSession.state
        if (session.videoId.isBlank()) return
        val item = VideoItem(
            session.videoId,
            session.title.ifBlank { getString(R.string.youtube_video) },
            session.channel,
            webView.url.orEmpty(),
            System.currentTimeMillis(),
            session.positionMs,
            session.durationMs
        )
        mutatePlaybackLibrary {
            if (libraryStore.toggleFavorite(item)) getString(R.string.saved_favorites) else getString(R.string.removed_favorites)
        }
    }

    private fun mutatePlaybackLibrary(action: () -> String) {
        val session = playbackSession.state
        val key = PlaybackReadKey(session.videoId, playerNavigationGeneration)
        val channel = session.channel
        val request = ++libraryFlagsRequest
        writeLibrary {
            val result = runCatching { action() to libraryStore.playbackState(key.videoId, channel, false) }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                val latest = playbackSession.state
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
        ++libraryFlagsRequest
        val session = playbackSession.state
        if (session.videoId.isBlank()) return
        if (currentQueued) {
            if (libraryStore.removeFromQueue(session.videoId)) queueCountCache = (queueCountCache - 1).coerceAtLeast(0)
            currentQueued = false
            Toast.makeText(this, getString(R.string.ui_removed_from_queue), Toast.LENGTH_SHORT).show()
        } else {
            val item = VideoItem(
                session.videoId,
                session.title.ifBlank { getString(R.string.youtube_video) },
                session.channel,
                webView.url.orEmpty(),
                System.currentTimeMillis(),
                session.positionMs,
                session.durationMs
            )
            if (libraryStore.enqueue(item)) {
                queueCountCache += 1
                currentQueued = true
                Toast.makeText(this, getString(R.string.ui_added_to_queue), Toast.LENGTH_SHORT).show()
            }
        }
        readPlaybackLibraryState(includeResume = false)
        refreshUi()
    }

    private fun cyclePlaybackSpeed() {
        val speeds = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val current = preferences.playbackSpeed
        val index = speeds.indices.minByOrNull { kotlin.math.abs(speeds[it] - current) } ?: 1
        val next = speeds[(index + 1) % speeds.size]
        preferences.playbackSpeed = next
        playbackBackend.setPlaybackRate(next)
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
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.quality_profile,profileName))
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                saveManualQuality(values[which])
                applyPolicyAndRefresh()
                dialog.dismiss()
                Toast.makeText(this, getString(R.string.quality_selected,profileName,labels[which]), Toast.LENGTH_SHORT).show()
                webView.evaluateJavascript("if(window.__videoShieldResetQuality) window.__videoShieldResetQuality();",null)
            }
            .setNegativeButton(getString(R.string.ui_cancel), null)
            .show()
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
                val currentId = playbackSession.state.videoId
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
        val shell = if (preferences.amoledTheme) Color.BLACK else Color.rgb(21, 23, 28)
        val system = if (preferences.amoledTheme) Color.BLACK else Color.rgb(11, 12, 15)
        window.statusBarColor = system
        window.navigationBarColor = system
        if (::topBar.isInitialized) topBar.setBackgroundColor(shell)
        if (::bottomBar.isInitialized) bottomBar.setBackgroundColor(shell)
        if (::webView.isInitialized) webView.setBackgroundColor(system)
        if (::browseWebView.isInitialized) browseWebView.setBackgroundColor(system)
    }

    private fun updateBrowserChromeVisibility(url: String?) {
        if (!::topBar.isInitialized || !::preferences.isInitialized || !::playerSurfaceController.isInitialized) return
        val pip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode
        if (customView != null || pip) {
            topBar.visibility = View.GONE
            findViewById<View>(R.id.playbackActionStrip).visibility = View.GONE
            return
        }

        val chromeRoute = if (playerSurfaceController.expanded) playerRoute else browseRoute
        val chrome = ClientChromePolicy.forRoute(
            chromeRoute,
            fullscreen = false,
            pictureInPicture = false,
            minimalPlaybackChrome = preferences.compactYouTubeChrome && playerSurfaceController.expanded
        )
        topBar.visibility = if (chrome.showAppBar) View.VISIBLE else View.GONE
        findViewById<View>(R.id.playbackStatusRow).visibility = if (playerSurfaceController.expanded) View.VISIBLE else View.GONE
        findViewById<View>(R.id.playbackActionStrip).visibility =
            if (playerSurfaceController.expanded && playbackSession.state.hasSession) View.VISIBLE else View.GONE
        if (!urlInput.hasFocus()) {
            urlInput.hint = ClientChromePolicy.forRoute(
                browseRoute,
                fullscreen = false,
                pictureInPicture = false
            ).searchHint.let { if(it == "Search YouTube") getString(R.string.search_youtube) else it }
        }
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
        val channel = playbackSession.state.channel
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            playingBeforePip = playbackSession.state.playing
            enterPictureInPictureMode(buildPipParams())
        } catch (_: Exception) {}
    }

    private fun buildPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        builder.setActions(buildPipActions())
        return builder.build()
    }

    private fun buildPipActions(): List<RemoteAction> = listOf(
        pipAction(PlaybackService.CMD_SEEK_BACK, getString(R.string.back_seconds), R.drawable.ic_pip_back, 701),
        pipAction(
            if (playbackSession.state.playing) PlaybackService.CMD_PAUSE else PlaybackService.CMD_PLAY,
            if (playbackSession.state.playing) getString(R.string.ui_pause) else getString(R.string.ui_play),
            if (playbackSession.state.playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
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
        if (preferences.autoPiP && playbackSession.state.playing && customView == null) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            val resumePlaying = playingBeforePip || playbackSession.state.playing
            playingBeforePip = false
            pipPlaybackWanted = resumePlaying
            surfaceBeforePip = playerSurfaceController.state.name
            miniSurfaceApplied = false
            webView.evaluateJavascript(ClientSurfaceScript.mini(false), null)
            playerSurfaceController.expand()
            browseWebView.visibility = View.GONE
            webView.evaluateJavascript(ClientSurfaceScript.pip(true, resumePlaying), null)
            topBar.visibility = View.GONE
            bottomBar.visibility = View.GONE
            updatePipActionsIfNeeded()
        } else {
            pipPlaybackWanted = false
            browseWebView.visibility = View.VISIBLE
            webView.evaluateJavascript(ClientSurfaceScript.pip(false), null)
            surfaceBeforePip?.let { playerSurfaceController.restore(it) }
            miniSurfaceApplied = playerSurfaceController.minimized
            webView.evaluateJavascript(ClientSurfaceScript.mini(miniSurfaceApplied, playbackSession.state.playing), null)
            surfaceBeforePip = null
            bottomBar.visibility = View.VISIBLE
            updateBrowserChromeVisibility(webView.url)
        }
        findViewById<View>(android.R.id.content).requestApplyInsets()
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
        updateBrowserChromeVisibility(webView.url)
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
            playbackSession.state.playing && !isInPictureInPictureMode)
        if (miniSurfaceApplied != playerSurfaceController.minimized) {
            miniSurfaceApplied = playerSurfaceController.minimized
            webView.evaluateJavascript(ClientSurfaceScript.mini(miniSurfaceApplied, playbackSession.state.playing), null)
        }
        val session = playbackSession.state
        val network = if (networkOnline) "" else " • Offline"
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
        findViewById<Button>(R.id.subscribeButton).apply {
            isEnabled = session.channel.isNotBlank()
            setTextIfChanged(if (currentSubscribed) getString(R.string.ui_subscribed) else getString(R.string.ui_subscribe))
        }
        findViewById<Button>(R.id.favoriteButton).apply {
            isEnabled = session.videoId.isNotBlank()
            setTextIfChanged(getString(R.string.ui_save))
            setLeadingIcon(if (currentFavorite) R.drawable.ic_ui_bookmark_filled else R.drawable.ic_ui_bookmark)
        }
        findViewById<Button>(R.id.queueButton).apply {
            isEnabled = session.videoId.isNotBlank()
            setTextIfChanged(if (currentQueued) getString(R.string.queued) else getString(R.string.ui_queue))
            setLeadingIcon(R.drawable.ic_ui_queue)
        }
        findViewById<Button>(R.id.speedButton).setTextIfChanged(formatSpeed(preferences.playbackSpeed))
        findViewById<Button>(R.id.qualityButton).setTextIfChanged(qualityLabel(effectivePreferredQuality()))
        findViewById<Button>(R.id.autoNextButton).apply {
            setTextIfChanged(getString(if(preferences.autoAdvanceQueue) R.string.auto_next_on else R.string.auto_next_off))
            setLeadingIcon(R.drawable.ic_ui_queue, if(preferences.autoAdvanceQueue) Color.rgb(62,166,255) else Color.WHITE)
        }
        findViewById<Button>(R.id.repeatButton).apply {
            setTextIfChanged(if (preferences.autoRepeat) getString(R.string.repeat_on) else getString(R.string.ui_repeat))
            setLeadingIcon(R.drawable.ic_ui_repeat, if (preferences.autoRepeat) Color.rgb(62,166,255) else Color.WHITE)
        }
        findViewById<TextView>(R.id.miniPlayerTitle).setTextIfChanged(session.title.ifBlank { getString(R.string.playing_video) })
        findViewById<TextView>(R.id.miniPlayerChannel).setTextIfChanged(session.channel)
        findViewById<IconButton>(R.id.miniPlayPauseButton).apply {
            setIcon(if (session.playing) R.drawable.ic_ui_pause else R.drawable.ic_ui_play)
            contentDescription = if (session.playing) getString(R.string.ui_pause) else getString(R.string.ui_play)
        }
        findViewById<ProgressBar>(R.id.miniPlayerProgress).progress = if (session.durationMs > 0L) {
            ((session.positionMs.coerceIn(0L, session.durationMs) * 1000L) / session.durationMs).toInt()
        } else 0
        updateBrowserChromeVisibility(if (::webView.isInitialized) webView.url else null)
        updateBottomNavigation()
        val sleepMinutes = if (::sleepTimerController.isInitialized) sleepTimerController.remainingMinutes() else 0
        findViewById<Button>(R.id.sleepButton).setTextIfChanged(if (sleepMinutes > 0) getString(R.string.sleep_button,sleepMinutes) else getString(R.string.ui_sleep))
    }

    private fun updateBottomNavigation() {
        val destination = browseRoute.destination
        if (renderedNavigation == destination) return
        renderedNavigation = destination
        listOf(Triple(R.id.navHomeButton, R.drawable.ic_nav_home, YouTubeDestination.HOME),
            Triple(R.id.navShieldButton, R.drawable.ic_nav_shorts, YouTubeDestination.SHORTS),
            Triple(R.id.navSubscriptionsButton, R.drawable.ic_nav_subscriptions, YouTubeDestination.SUBSCRIPTIONS),
            Triple(R.id.navLibraryButton, R.drawable.ic_nav_you, YouTubeDestination.OTHER)).forEach { (id, icon, route) ->
            findViewById<Button>(id).apply {
                setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0)
                compoundDrawableTintList = android.content.res.ColorStateList.valueOf(if (destination == route) Color.WHITE else Color.rgb(170,170,170))
                setTextColor(if (destination == route) Color.WHITE else Color.rgb(170,170,170))
                isSelected = destination == route
            }
        }
    }

    private fun onNetworkChanged(online: Boolean) {
        val changed = networkOnline != online
        val wasOnline = networkOnline
        val meteredNow = currentNetworkMetered()
        val qualityProfileChanged = networkMetered != meteredNow
        networkOnline = online
        networkMetered = meteredNow
        val session = playbackSession.state
        playbackHealth.setOnline(online, session.hasSession)
        if (!online && (changed || wasOnline)) {
            recoveryDiagnostics.recordOfflineInterruption()
            if (webViewLifecycle.foreground) Toast.makeText(this, getString(R.string.ui_offline_playback_recovery_paused), Toast.LENGTH_SHORT).show()
        } else if (online && changed) {
            if (webViewLifecycle.foreground) Toast.makeText(this, getString(R.string.ui_back_online), Toast.LENGTH_SHORT).show()
            if (session.videoId.isNotBlank()) refreshCommunitySegments(session.videoId, force = true)
            if (session.playing) {
                playbackRecovery.heartbeat(true, session.positionMs)
                playbackHealth.heartbeat(true, session.videoId)
            } else if (webViewLifecycle.foreground && session.hasSession && session.videoId.isNotBlank() && YouTubeAdapter.isTrustedBridgeUrl(webView.url)) {
                webView.postDelayed({
                    val latest = playbackSession.state
                    if (networkOnline && webViewLifecycle.foreground && !latest.playing) webView.reload()
                }, 650L)
            }
        }
        // A profile change may be reported while offline. Reapply on reconnection as well,
        // because networkMetered already contains the new value by that point.
        if ((qualityProfileChanged || changed) && online && playerSurfaceController.visible) {
            webView.evaluateJavascript(
                AdBlockScript.build(
                    preferences,
                    rulePackManager.active(),
                    filterEngine.pageWhitelisted,
                    preferredQualityOverride = effectivePreferredQuality()
                ),
                null
            )
            browseWebView.evaluateJavascript(
                AdBlockScript.build(
                    preferences,
                    rulePackManager.active(),
                    pageWhitelisted = false,
                    preferredQualityOverride = effectivePreferredQuality()
                ),
                null
            )
            if (webViewLifecycle.foreground) {
                val profile = if (networkMetered) getString(R.string.ui_mobile_metered) else getString(R.string.ui_wi_fi_unmetered)
                Toast.makeText(this, getString(R.string.quality_selected,profile,qualityLabel(effectivePreferredQuality())), Toast.LENGTH_SHORT).show()
            }
        }
        refreshUi()
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
                errorMessage.text = snapshot.reason.ifBlank { getString(R.string.ui_automatic_recovery_could_not_restore_this_page) }
                errorOverlay.visibility = View.VISIBLE
                findViewById<Button>(R.id.errorRetryButton).isEnabled = networkOnline
            }
            else -> {
                errorOverlay.visibility = View.GONE
                findViewById<Button>(R.id.errorRetryButton).isEnabled = networkOnline
            }
        }
    }

    private fun policyFingerprint(): String = listOf(
        preferences.shieldEnabled,
        preferences.safeMode,
        preferences.blockTrackers,
        preferences.blockShorts,
        preferences.blockRecommendations,
        preferences.blockComments,
        preferences.blockEndScreen,
        preferences.blockOpenInApp,
        preferences.autoPiP,
        preferences.backgroundControls,
        preferences.amoledTheme,
        preferences.autoRepeat,
        preferences.preferredQuality,
        preferences.preferredQualityMobile,
        preferences.communitySponsorSkip,
        preferences.skipIntrosOutros,
        rulePackManager.active().ruleVersion,
        preferences.whitelistedChannels.sorted().joinToString(",")
    ).joinToString("|")

    private fun isTrustedBridgeOrigin(): Boolean = YouTubeAdapter.isTrustedBridgeUrl(webView.url)


    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::webViewLifecycle.isInitialized) webViewLifecycle.onTrimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::webViewLifecycle.isInitialized) webViewLifecycle.onLowMemory()
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
            lastPolicyFingerprint = policyFingerprint()
            Toast.makeText(this, getString(R.string.rules_updated,result.version), Toast.LENGTH_SHORT).show()
            if (::webView.isInitialized) webView.reload()
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

    private fun handlePlaybackCommand(command: String, positionMs: Long?) {
        if (command == PlaybackService.CMD_STOP || command == PlaybackService.CMD_SEEK_BACK ||
            command == PlaybackService.CMD_SEEK_FORWARD || command == PlaybackService.CMD_SEEK_TO) {
            ++playerNavigationGeneration
        }
        if (isInPictureInPictureMode) {
            when (command) {
                PlaybackService.CMD_PLAY, PlaybackService.CMD_QUEUE_NEXT -> pipPlaybackWanted = true
                PlaybackService.CMD_PAUSE, PlaybackService.CMD_STOP -> pipPlaybackWanted = false
                PlaybackService.CMD_PLAY_PAUSE -> pipPlaybackWanted = !playbackSession.state.playing
            }
        }
        when (command) {
            PlaybackService.CMD_STOP -> {
                playbackBackend.pause()
                playbackSession.stop(clearSnapshot = true)
                if (::webViewLifecycle.isInitialized) webViewLifecycle.release("stop command")
            }
            PlaybackService.CMD_PLAY -> playbackBackend.play()
            PlaybackService.CMD_PAUSE -> playbackBackend.pause()
            PlaybackService.CMD_PLAY_PAUSE -> playbackBackend.toggle()
            PlaybackService.CMD_SEEK_BACK -> playbackBackend.seekBack()
            PlaybackService.CMD_SEEK_FORWARD -> playbackBackend.seekForward()
            PlaybackService.CMD_SEEK_TO -> positionMs?.let { playbackBackend.seekToMs(it) }
            PlaybackService.CMD_QUEUE_NEXT -> playNextFromQueue(manual = true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("app_language_tag",uiLanguageTag)
        if (::browseWebView.isInitialized) {
            try {
                val browseState = Bundle()
                browseWebView.saveState(browseState)
                outState.putBundle(STATE_BROWSE_WEBVIEW, browseState)
            } catch (_: Exception) {}
        }
        if (::webView.isInitialized && !rendererGone) {
            try {
                val playerState = Bundle()
                webView.saveState(playerState)
                outState.putBundle(STATE_PLAYER_WEBVIEW, playerState)
                outState.putString(STATE_PLAYER_SURFACE, playerSurfaceController.state.name)
            } catch (_: Exception) {}
        }
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("GestureBackNavigation") // API < 33 fallback; newer devices use OnBackInvokedCallback.
    override fun onBackPressed() {
        handleBackNavigation()
    }

    private fun handleBackNavigation() {
        if (::searchSuggestions.isInitialized && searchSuggestions.dismiss()) {
            urlInput.clearFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(urlInput.windowToken, 0)
            return
        }
        when {
            customView != null -> exitFullscreen()
            playerSurfaceController.expanded -> minimizePlayer()
            browseWebView.canGoBack() -> browseWebView.goBack()
            playbackSession.state.playing && preferences.backgroundControls -> moveTaskToBack(true)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==SaveVideoController.REQUEST_DESTINATION) saveVideo.destination(resultCode,data)
    }

    override fun onDestroy() {
        if (::playbackScreenOn.isInitialized) playbackScreenOn.update(false)
        quickActionSheet?.dismiss()
        EqDialog.dismiss(this)
        if (::saveVideo.isInitialized) saveVideo.close()
        if (::searchSuggestions.isInitialized) searchSuggestions.close()
        window.decorView.removeCallbacks(uiRefreshFrame)
        uiRefreshPending = false
        libraryWorker.execute { if (::libraryStore.isInitialized) libraryStore.close() }
        libraryWorker.shutdown()
        if (::playerController.isInitialized) playerController.release()
        if (isFinishing) {
            if (::webViewLifecycle.isInitialized) webViewLifecycle.release("activity finishing")
            if (::playbackSession.isInitialized) playbackSession.stopService()
        }
        if (::sleepTimerController.isInitialized) sleepTimerController.dispose()
        if (::playbackRecovery.isInitialized) playbackRecovery.dispose()
        if (::networkStateMonitor.isInitialized) networkStateMonitor.stop()
        if (::deviceRuntimeMonitor.isInitialized) deviceRuntimeMonitor.stop()
        if (::webViewLifecycle.isInitialized) webViewLifecycle.release("activity destroyed")
        if (::commandRouter.isInitialized) commandRouter.stop()
        if (::communitySegmentClient.isInitialized) communitySegmentClient.close()
        if (::webView.isInitialized && !rendererGone) {
            try {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeJavascriptInterface("VideoShieldBridge")
                webView.removeAllViews()
                webView.destroy()
            } catch (_: Exception) {}
        }
        if (::browseWebView.isInitialized) {
            try {
                (browseWebView.parent as? ViewGroup)?.removeView(browseWebView)
                browseWebView.stopLoading()
                browseWebView.loadUrl("about:blank")
                browseWebView.clearHistory()
                browseWebView.removeAllViews()
                browseWebView.destroy()
            } catch (_: Exception) {}
        }
        super.onDestroy()
    }
    companion object {
        private const val RESTORE_SNAPSHOT_MAX_AGE_MS = 12L * 60L * 60L * 1000L
        private const val STATE_BROWSE_WEBVIEW = "browse_webview_state"
        private const val STATE_PLAYER_WEBVIEW = "player_webview_state"
        private const val STATE_PLAYER_SURFACE = "player_surface_state"
    }

}

