package com.example.videoshield

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.Button

/** Vector-only button with a centered icon; independent of font glyph metrics. */
class IconButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.buttonStyle) : Button(context, attrs, defStyleAttr) {
    private var icon: Drawable? = compoundDrawables[1]?.mutate()
    private var iconResource = 0
    init { text = ""; setCompoundDrawables(null, null, null, null) }
    fun setIcon(resource: Int) {
        if (resource == iconResource) return
        iconResource = resource
        icon = context.getDrawable(resource)?.mutate()
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        icon?.let {
            val size = minOf(it.intrinsicWidth.coerceAtLeast(1), width, height)
            it.setBounds((width-size)/2, (height-size)/2, (width+size)/2, (height+size)/2)
            it.alpha = if (isEnabled) 255 else 90
            it.draw(canvas)
        }
    }
}
