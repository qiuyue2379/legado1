package io.qiuyue.app.lib.theme.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ProgressBar
import io.qiuyue.app.lib.theme.ATH
import io.qiuyue.app.lib.theme.ThemeStore

/**
 * @author Aidan Follestad (afollestad)
 */
class ATEProgressBar(context: Context, attrs: AttributeSet) : ProgressBar(context, attrs) {

    init {
        if (!isInEditMode) {
            ATH.setTint(this, ThemeStore.accentColor(context))
        }
    }
}