package io.qiuyue.app.ui.dict

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import io.qiuyue.app.R
import io.qiuyue.app.base.BaseDialogFragment
import io.qiuyue.app.databinding.DialogDictBinding
import io.qiuyue.app.utils.invisible
import io.qiuyue.app.utils.toastOnUi
import io.qiuyue.app.utils.viewbindingdelegate.viewBinding

/**
 * 词典
 */
class DictDialog : BaseDialogFragment() {

    companion object {

        fun dict(manager: FragmentManager, word: String) {
            DictDialog().apply {
                val bundle = Bundle()
                bundle.putString("word", word)
                arguments = bundle
            }.show(manager, word)
        }

    }

    private val viewModel by viewModels<DictViewModel>()
    private val binding by viewBinding(DialogDictBinding::bind)

    override fun onStart() {
        super.onStart()
        dialog?.window
            ?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.dialog_dict, container)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvDict.movementMethod = LinkMovementMethod()
        val word = arguments?.getString("word")
        if (word.isNullOrEmpty()) {
            toastOnUi(R.string.cannot_empty)
            dismiss()
            return
        }
        viewModel.dictHtmlData.observe(viewLifecycleOwner) {
            binding.rotateLoading.invisible()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                binding.tvDict.text = Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                binding.tvDict.text = Html.fromHtml(it)
            }
        }
        viewModel.dict(word)

    }


}