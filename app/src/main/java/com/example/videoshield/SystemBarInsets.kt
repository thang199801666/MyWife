package com.example.videoshield

import android.app.Activity
import android.graphics.Insets
import android.os.Build
import android.view.View
import android.view.WindowInsets

/** Applies the safe drawing area when Android enforces edge-to-edge for our target SDK. */
object SystemBarInsets {
    fun install(activity: Activity, immersive: () -> Boolean = { false }) {
        if (Build.VERSION.SDK_INT < 35) return // Older decor already fits this app's non-edge-to-edge windows.
        val content = activity.findViewById<View>(android.R.id.content)
        val left = content.paddingLeft
        val top = content.paddingTop
        val right = content.paddingRight
        val bottom = content.paddingBottom
        val handledTypes = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        content.setOnApplyWindowInsetsListener { view, insets ->
            val safe = if (immersive()) Insets.NONE else insets.getInsets(handledTypes)
            view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom)
            // Do not give WebView a second copy of the bars already handled by its parent.
            // IME insets remain available to scrolling text fields and the web content.
            WindowInsets.Builder(insets).setInsets(handledTypes, Insets.NONE).build()
        }
        content.requestApplyInsets()
    }
}
