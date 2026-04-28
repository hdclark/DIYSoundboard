package com.hdclark.diysoundboard

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * A FrameLayout that measures itself as a square (width == height) so that
 * soundboard buttons are always square regardless of screen width.
 */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Force height == width so the cell is always square.
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
