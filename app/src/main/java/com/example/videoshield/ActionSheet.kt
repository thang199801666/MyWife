package com.example.videoshield

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Small native sheet: vector icons, accessible rows and bounded scrolling, without a UI framework. */
object ActionSheet {
    data class Action(val label: String, val description: String = "", val icon: Int = 0, val danger: Boolean = false)
    fun show(activity: Activity, title: String, actions: List<Action>, subtitle: String = "", selected: (Int) -> Unit): Dialog? {
        if (activity.isFinishing || activity.isDestroyed) return null
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
        val dialog = Dialog(activity)
        val surface = Color.rgb(33, 33, 33)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(12))
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadii = floatArrayOf(dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f)
            }
        }
        root.addView(View(activity).apply {
            background = GradientDrawable().apply { setColor(Color.rgb(110,110,110)); cornerRadius = dp(2).toFloat() }
        }, LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(12) })
        val scroll = object : ScrollView(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val cap = (resources.displayMetrics.heightPixels * .78f).toInt()
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST))
            }
        }.apply { isFillViewport = false }
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), 0, dp(8), dp(8)) }
        header.addView(TextView(activity).apply {
            text = title; textSize = 20f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(IconButton(activity, null, android.R.attr.borderlessButtonStyle).apply {
            setIcon(R.drawable.ic_ui_close); contentDescription = activity.getString(R.string.ui_close_menu)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        content.addView(header)
        if (subtitle.isNotBlank()) content.addView(TextView(activity).apply {
            text = subtitle; textSize = 13f; setTextColor(Color.LTGRAY); setPadding(dp(20), 0, dp(20), dp(16))
        })
        actions.forEachIndexed { index, action ->
            val color = if (action.danger) Color.rgb(255, 125, 142) else Color.WHITE
            val row = LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(64)
                setPadding(dp(20), dp(12), dp(20), dp(12))
                background = RippleDrawable(ColorStateList.valueOf(0x22ffffff), ColorDrawable(surface), null)
                isFocusable = true; isClickable = true
                contentDescription = listOf(action.label, action.description).filter(String::isNotBlank).joinToString(". ")
                setOnClickListener { dialog.dismiss(); if (!activity.isDestroyed && !activity.isFinishing) selected(index) }
            }
            if (action.icon != 0) row.addView(ImageView(activity).apply {
                setImageResource(action.icon); imageTintList = ColorStateList.valueOf(color)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(20) })
            val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
            labels.addView(TextView(activity).apply { text = action.label; textSize = 16f; setTextColor(color) })
            if (action.description.isNotBlank()) labels.addView(TextView(activity).apply {
                text = action.description; textSize = 12f; setTextColor(Color.LTGRAY); setPadding(0, dp(4), 0, 0)
            })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            content.addView(row)
        }
        scroll.addView(content); root.addView(scroll)
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.55f); setGravity(Gravity.BOTTOM)
            setWindowAnimations(if (android.animation.ValueAnimator.areAnimatorsEnabled()) R.style.ActionSheetAnimation else 0)
        }
        dialog.show()
        dialog.window?.setLayout(-1, -2)
        return dialog
    }
}
