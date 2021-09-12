package io.qiuyue.app.ui.association

import android.os.Bundle
import androidx.activity.viewModels
import io.qiuyue.app.base.VMBaseActivity
import io.qiuyue.app.databinding.ActivityTranslucenceBinding
import io.qiuyue.app.ui.book.read.ReadBookActivity
import io.qiuyue.app.utils.startActivity
import io.qiuyue.app.utils.toastOnUi
import io.qiuyue.app.utils.viewbindingdelegate.viewBinding

class FileAssociationActivity :
    VMBaseActivity<ActivityTranslucenceBinding, FileAssociationViewModel>() {

    override val binding by viewBinding(ActivityTranslucenceBinding::inflate)

    override val viewModel by viewModels<FileAssociationViewModel>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.rotateLoading.show()
        viewModel.onLineImportLive.observe(this) {
            startActivity<OnLineImportActivity> {
                data = it
            }
            finish()
        }
        viewModel.importBookSourceLive.observe(this) {
            binding.rotateLoading.hide()
            ImportBookSourceDialog.start(supportFragmentManager, it, true)
        }
        viewModel.importRssSourceLive.observe(this) {
            binding.rotateLoading.hide()
            ImportRssSourceDialog.start(supportFragmentManager, it, true)
        }
        viewModel.importReplaceRuleLive.observe(this) {
            binding.rotateLoading.hide()
            ImportReplaceRuleDialog.start(supportFragmentManager, it, true)
        }
        viewModel.errorLiveData.observe(this, {
            binding.rotateLoading.hide()
            toastOnUi(it)
            finish()
        })
        viewModel.openBookLiveData.observe(this, {
            binding.rotateLoading.hide()
            startActivity<ReadBookActivity> {
                putExtra("bookUrl", it)
            }
            finish()
        })
        intent.data?.let { data ->
            viewModel.dispatchIndent(data)
        }
    }

}
