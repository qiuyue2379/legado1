package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.help.ReadTipConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.book.read.Help
import io.legado.app.utils.postEvent
import kotlinx.android.synthetic.main.dialog_tip_config.*
import org.jetbrains.anko.sdk27.listeners.onCheckedChange
import org.jetbrains.anko.sdk27.listeners.onClick

class TipConfigDialog : BaseDialogFragment() {

    override fun onStart() {
        super.onStart()
        val dm = DisplayMetrics()
        activity?.let {
            Help.upSystemUiVisibility(it)
            it.windowManager?.defaultDisplay?.getMetrics(dm)
        }
        dialog?.window?.let {
            val attr = it.attributes
            attr.dimAmount = 0.0f
            it.attributes = attr
            it.setLayout((dm.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_tip_config, container)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initEvent()
    }

    private fun initView() {
        tv_header_left.text = ReadTipConfig.tipHeaderLeftStr
        tv_header_middle.text = ReadTipConfig.tipHeaderMiddleStr
        tv_header_right.text = ReadTipConfig.tipHeaderRightStr
        tv_footer_left.text = ReadTipConfig.tipFooterLeftStr
        tv_footer_middle.text = ReadTipConfig.tipFooterMiddleStr
        tv_footer_right.text = ReadTipConfig.tipFooterRightStr
        sw_hide_header.isChecked = ReadTipConfig.hideHeader
        sw_hide_footer.isChecked = ReadTipConfig.hideFooter
    }

    private fun initEvent() {
        tv_header_left.onClick {
            selector(items = ReadTipConfig.tipArray.toList()) { _, i ->
                ReadTipConfig.tipHeaderLeft = i
                tv_header_left.text = ReadTipConfig.tipArray[i]
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        tv_header_middle.onClick {
            selector(items = ReadTipConfig.tipArray.toList()) { _, i ->
                ReadTipConfig.tipHeaderMiddle = i
                tv_header_middle.text = ReadTipConfig.tipArray[i]
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        tv_header_right.onClick {
            selector(items = ReadTipConfig.tipArray.toList()) { _, i ->
                ReadTipConfig.tipHeaderRight = i
                tv_header_right.text = ReadTipConfig.tipArray[i]
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        tv_footer_left.onClick {
            selector(items = ReadTipConfig.tipArray.toList()) { _, i ->
                ReadTipConfig.tipFooterLeft = i
                tv_footer_left.text = ReadTipConfig.tipArray[i]
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        tv_footer_middle.onClick {
            selector(items = ReadTipConfig.tipArray.toList()) { _, i ->
                ReadTipConfig.tipFooterMiddle = i
                tv_footer_middle.text = ReadTipConfig.tipArray[i]
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        tv_footer_right.onClick {
            selector(items = ReadTipConfig.tipArray.toList()) { _, i ->
                ReadTipConfig.tipFooterRight = i
                tv_footer_right.text = ReadTipConfig.tipArray[i]
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        sw_hide_header.onCheckedChange { buttonView, isChecked ->
            if (buttonView?.isPressed == true) {
                ReadTipConfig.hideHeader = isChecked
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        sw_hide_footer.onCheckedChange { buttonView, isChecked ->
            if (buttonView?.isPressed == true) {
                ReadTipConfig.hideFooter = isChecked
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
    }

}