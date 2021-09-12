package io.qiuyue.app.ui.widget.text

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import io.qiuyue.app.lib.theme.ATH
import io.qiuyue.app.lib.theme.ThemeStore

/**
 * @author Aidan Follestad (afollestad)
 */
class EditText(context: Context, attrs: AttributeSet) : AppCompatEditText(context, attrs) {

    init {
        if (!isInEditMode) {
            ATH.setTint(this, ThemeStore.accentColor(context))
        }
    }
}
