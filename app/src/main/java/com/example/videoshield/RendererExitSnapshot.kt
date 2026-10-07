package com.example.videoshield

/** Small browser-process-independent snapshot captured before a dead WebView is destroyed. */
data class RendererExitSnapshot(
    val didCrash: Boolean,
    val url: String,
    val scrollY: Int
)
