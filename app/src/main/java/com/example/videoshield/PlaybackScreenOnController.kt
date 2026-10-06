package com.example.videoshield

import android.view.Window
import android.view.WindowManager

/** Keeps only the visible video activity awake; never changes system timeout. */
class PlaybackScreenOnController(private val window: Window) {
    private var enabled=false
    fun update(watching: Boolean) {
        if(enabled==watching) return
        enabled=watching
        if(watching) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
