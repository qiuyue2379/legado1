package io.qiuyue.app.utils

import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import io.qiuyue.app.lib.theme.ATH

fun AlertDialog.applyTint(): AlertDialog {
    return ATH.setAlertDialogTint(this)
}

fun AlertDialog.requestInputMethod() {
    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
}
