package io.qiuyue.app.ui.book.read.config

import android.app.Dialog
import android.content.Context
import android.view.KeyEvent
import io.qiuyue.app.R
import io.qiuyue.app.constant.PreferKey
import io.qiuyue.app.databinding.DialogPageKeyBinding
import io.qiuyue.app.lib.theme.backgroundColor
import io.qiuyue.app.utils.getPrefString
import io.qiuyue.app.utils.hideSoftInput
import io.qiuyue.app.utils.putPrefString
import splitties.views.onClick


class PageKeyDialog(context: Context) : Dialog(context, R.style.AppTheme_AlertDialog) {

    private val binding = DialogPageKeyBinding.inflate(layoutInflater)

    init {
        setContentView(binding.root)
        binding.run {
            contentView.setBackgroundColor(context.backgroundColor)
            etPrev.setText(context.getPrefString(PreferKey.prevKeys))
            etNext.setText(context.getPrefString(PreferKey.nextKeys))
            tvReset.onClick {
                etPrev.setText("")
                etNext.setText("")
            }
            tvOk.setOnClickListener {
                context.putPrefString(PreferKey.prevKeys, etPrev.text?.toString())
                context.putPrefString(PreferKey.nextKeys, etNext.text?.toString())
                dismiss()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_DEL) {
            if (binding.etPrev.hasFocus()) {
                val editableText = binding.etPrev.editableText
                if (editableText.isEmpty() or editableText.endsWith(",")) {
                    editableText.append(keyCode.toString())
                } else {
                    editableText.append(",").append(keyCode.toString())
                }
                return true
            } else if (binding.etNext.hasFocus()) {
                val editableText = binding.etNext.editableText
                if (editableText.isEmpty() or editableText.endsWith(",")) {
                    editableText.append(keyCode.toString())
                } else {
                    editableText.append(",").append(keyCode.toString())
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dismiss() {
        super.dismiss()
        currentFocus?.hideSoftInput()
    }

}