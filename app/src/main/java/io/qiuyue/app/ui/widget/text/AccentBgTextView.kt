package io.qiuyue.app.ui.widget.text

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import io.qiuyue.app.R
import io.qiuyue.app.lib.theme.Selector
import io.qiuyue.app.lib.theme.ThemeStore
import io.qiuyue.app.utils.ColorUtils
import io.qiuyue.app.utils.dp
import io.qiuyue.app.utils.getCompatColor

class AccentBgTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    private var radius = 0

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AccentBgTextView)
        radius = typedArray.getDimensionPixelOffset(R.styleable.AccentBgTextView_radius, radius)
        typedArray.recycle()
        upBackground()
        setTextColor(Color.WHITE)
    }

    fun setRadius(radius: Int) {
        this.radius = radius.dp
        upBackground()
    }

    private fun upBackground() {
        background = if (isInEditMode) {
            Selector.shapeBuild()
                .setCornerRadius(radius)
                .setDefaultBgColor(context.getCompatColor(R.color.accent))
                .setPressedBgColor(ColorUtils.darkenColor(context.getCompatColor(R.color.accent)))
                .create()
        } else {
            Selector.shapeBuild()
                .setCornerRadius(radius)
                .setDefaultBgColor(ThemeStore.accentColor(context))
                .setPressedBgColor(ColorUtils.darkenColor(ThemeStore.accentColor(context)))
                .create()
        }
    }
}
