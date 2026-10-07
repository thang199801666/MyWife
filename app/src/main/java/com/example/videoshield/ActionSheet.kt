package com.example.videoshield

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
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
        val ui = UiMetrics(activity)
        val dialog = Dialog(activity)
        val surface = AppTheme.elevated(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.px(R.dimen.ui_chip_horizontal_padding), 0, ui.spaceLg)
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadii = floatArrayOf(ui.sheetCornerRadius.toFloat(), ui.sheetCornerRadius.toFloat(), ui.sheetCornerRadius.toFloat(), ui.sheetCornerRadius.toFloat(), 0f, 0f, 0f, 0f)
            }
        }
        root.addView(View(activity).apply {
            background = GradientDrawable().apply { setColor(AppTheme.tertiary(activity)); cornerRadius = ui.spaceXxs.toFloat() }
        }, LinearLayout.LayoutParams(ui.sheetHandleWidth, ui.sheetHandleHeight).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = ui.spaceLg })
        val scroll = object : ScrollView(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val cap = (resources.displayMetrics.heightPixels * .78f).toInt()
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST))
            }
        }.apply { isFillViewport = false }
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(ui.sheetHorizontalPadding, 0, ui.spaceMd, ui.spaceMd) }
        header.addView(TextView(activity).apply {
            text = title; setTextAppearance(R.style.YouTextSectionTitle)
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(IconButton(activity, null, android.R.attr.borderlessButtonStyle).apply {
            setIcon(R.drawable.ic_ui_close); contentDescription = activity.getString(R.string.ui_close_menu)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(ui.sheetCloseSize, ui.sheetCloseSize))
        content.addView(header)
        if (subtitle.isNotBlank()) content.addView(TextView(activity).apply {
            text = subtitle; setTextAppearance(R.style.YouTextSupporting); setPadding(ui.sheetHorizontalPadding, 0, ui.sheetHorizontalPadding, ui.spaceXl)
        })
        actions.forEachIndexed { index, action ->
            val color = if (action.danger) Color.rgb(220, 55, 75) else AppTheme.primary(activity)
            val row = LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL; minimumHeight = ui.sheetRowMinHeight
                setPadding(ui.sheetHorizontalPadding, ui.spaceMd, ui.sheetHorizontalPadding, ui.spaceMd)
                background = RippleDrawable(ColorStateList.valueOf(AppTheme.color(activity, R.attr.appRipple)), ColorDrawable(surface), null)
                isFocusable = true; isClickable = true
                contentDescription = listOf(action.label, action.description).filter(String::isNotBlank).joinToString(". ")
                setOnClickListener { dialog.dismiss(); if (!activity.isDestroyed && !activity.isFinishing) selected(index) }
            }
            if (action.icon != 0) row.addView(ImageView(activity).apply {
                setImageResource(action.icon); imageTintList = ColorStateList.valueOf(color)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(ui.sheetIconSize, ui.sheetIconSize).apply { marginEnd = ui.sheetHorizontalPadding })
            val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
            labels.addView(TextView(activity).apply { text = action.label; setTextAppearance(R.style.YouTextActionTitle); setTextColor(color) })
            if (action.description.isNotBlank()) labels.addView(TextView(activity).apply {
                text = action.description; setTextAppearance(R.style.YouTextMetadata); setPadding(0, ui.spaceXs, 0, 0)
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
