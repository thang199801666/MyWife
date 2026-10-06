package com.example.videoshield

import android.os.Build
import android.view.View
import android.view.WindowInsets

fun View.offlineSystemInsets() {
    setOnApplyWindowInsetsListener { view, insets ->
        if(Build.VERSION.SDK_INT>=30) {
            val bars=insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
        } else {
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
        }
        insets
    }
    requestApplyInsets()
}
