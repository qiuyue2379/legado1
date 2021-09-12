package io.qiuyue.app.ui.login

import android.os.Bundle
import androidx.activity.viewModels
import io.qiuyue.app.R
import io.qiuyue.app.base.VMBaseActivity
import io.qiuyue.app.data.entities.BaseSource
import io.qiuyue.app.databinding.ActivitySourceLoginBinding
import io.qiuyue.app.utils.viewbindingdelegate.viewBinding


class SourceLoginActivity : VMBaseActivity<ActivitySourceLoginBinding, SourceLoginViewModel>() {

    override val binding by viewBinding(ActivitySourceLoginBinding::inflate)
    override val viewModel by viewModels<SourceLoginViewModel>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        intent.getStringExtra("sourceUrl")?.let {
            viewModel.initData(it) { source ->
                initView(source)
            }
        }
    }

    private fun initView(source: BaseSource) {
        if (source.loginUi.isNullOrEmpty()) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fl_fragment, WebViewLoginFragment())
                .commit()
        } else {
            RuleUiLoginDialog().show(supportFragmentManager, "ruleUiLogin")
        }
    }

}