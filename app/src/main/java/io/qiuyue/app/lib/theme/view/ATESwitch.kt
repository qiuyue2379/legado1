package io.qiuyue.app.lib.theme.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.SwitchCompat
import io.qiuyue.app.lib.theme.ATH
import io.qiuyue.app.lib.theme.accentColor

/**
 * @author Aidan Follestad (afollestad)
 */
class ATESwitch(context: Context, attrs: AttributeSet) : SwitchCompat(context, attrs) {

    init {
        if (!isInEditMode) {
            ATH.setTint(this, context.accentColor)
        }

    }

}
