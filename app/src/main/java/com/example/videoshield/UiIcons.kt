package com.example.videoshield

import android.widget.Button
import android.widget.TextView

fun TextView.setTextIfChanged(value: CharSequence) {
    if (!android.text.TextUtils.equals(text, value)) text = value
}

/** Sized inline vector for a labeled action, with the same baseline in every screen. */
fun Button.setLeadingIcon(resource: Int) = setLeadingIcon(resource, AppTheme.icon(context))

fun Button.setLeadingIcon(resource: Int, tint: Int) {
    val key = "$resource:$tint"
    if (tag == key) return
    tag = key
    val size = (20 * resources.displayMetrics.density).toInt()
    val drawable = context.getDrawable(resource)?.mutate() ?: return
    drawable.setTint(tint)
    drawable.setBounds(0, 0, size, size)
    setCompoundDrawablesRelative(drawable, null, null, null)
    compoundDrawablePadding = (6 * resources.displayMetrics.density).toInt()
}
