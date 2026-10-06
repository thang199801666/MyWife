package com.example.videoshield

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.View

/** User-selected application chrome theme. Video surfaces remain black intentionally. */
object AppTheme {
    enum class Mode { BLACK, LIGHT }

    fun mode(context: Context): Mode = if (ShieldPreferences(context).lightTheme) Mode.LIGHT else Mode.BLACK
    fun isLight(context: Context): Boolean = mode(context) == Mode.LIGHT

    fun apply(activity: Activity) {
        activity.setTheme(if (isLight(activity)) R.style.AppThemeLight else R.style.AppThemeBlack)
    }

    fun applySystemBars(activity: Activity) {
        val light = isLight(activity)
        val background = color(activity, R.attr.appBackground)
        activity.window.statusBarColor = background
        activity.window.navigationBarColor = background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            // onCreate can run before setContentView. Accessing decorView first
            // creates the decor instead of asking PhoneWindow for an absent one.
            activity.window.decorView.windowInsetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            var flags = activity.window.decorView.systemUiVisibility
            @Suppress("DEPRECATION")
            val status = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            @Suppress("DEPRECATION")
            val nav = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
            flags = if (light) flags or status or nav else flags and status.inv() and nav.inv()
            @Suppress("DEPRECATION")
            run { activity.window.decorView.systemUiVisibility = flags }
        }
    }

    fun color(context: Context, attr: Int): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attr, value, true)) {
            when {
                value.resourceId != 0 -> context.getColor(value.resourceId)
                else -> value.data
            }
        } else Color.TRANSPARENT
    }

    fun colors(context: Context, attr: Int): ColorStateList = ColorStateList.valueOf(color(context, attr))
    fun background(context: Context) = color(context, R.attr.appBackground)
    fun surface(context: Context) = color(context, R.attr.appSurface)
    fun elevated(context: Context) = color(context, R.attr.appSurfaceElevated)
    fun control(context: Context) = color(context, R.attr.appControlSurface)
    fun primary(context: Context) = color(context, R.attr.appTextPrimary)
    fun secondary(context: Context) = color(context, R.attr.appTextSecondary)
    fun tertiary(context: Context) = color(context, R.attr.appTextTertiary)
    fun icon(context: Context) = color(context, R.attr.appIconColor)
    fun divider(context: Context) = color(context, R.attr.appDivider)
    fun selectedSurface(context: Context) = color(context, R.attr.appSelectedSurface)
    fun selectedText(context: Context) = color(context, R.attr.appSelectedText)
}
