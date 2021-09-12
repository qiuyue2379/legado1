package io.qiuyue.app.ui.about

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import io.qiuyue.app.R
import io.qiuyue.app.base.BaseDialogFragment
import io.qiuyue.app.base.adapter.ItemViewHolder
import io.qiuyue.app.base.adapter.RecyclerAdapter
import io.qiuyue.app.constant.AppLog
import io.qiuyue.app.databinding.DialogRecyclerViewBinding
import io.qiuyue.app.databinding.ItemAppLogBinding
import io.qiuyue.app.lib.theme.primaryColor
import io.qiuyue.app.ui.widget.dialog.TextDialog
import io.qiuyue.app.utils.LogUtils
import io.qiuyue.app.utils.viewbindingdelegate.viewBinding
import io.qiuyue.app.utils.windowSize
import splitties.views.onClick
import java.util.*

class AppLogDialog : BaseDialogFragment() {

    companion object {
        fun show(fragmentManager: FragmentManager) {
            AppLogDialog().show(fragmentManager, "appLogDialog")
        }
    }

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy {
        LogAdapter(requireContext())
    }

    override fun onStart() {
        super.onStart()
        val dm = requireActivity().windowSize
        dialog?.window?.setLayout(
            (dm.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_recycler_view, container)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.run {
            toolBar.setBackgroundColor(primaryColor)
            toolBar.setTitle(R.string.log)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
        }
        adapter.setItems(AppLog.logs)
    }

    inner class LogAdapter(context: Context) :
        RecyclerAdapter<Triple<Long, String, Throwable?>, ItemAppLogBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemAppLogBinding {
            return ItemAppLogBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAppLogBinding,
            item: Triple<Long, String, Throwable?>,
            payloads: MutableList<Any>
        ) {
            binding.textTime.text = LogUtils.logTimeFormat.format(Date(item.first))
            binding.textMessage.text = item.second
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemAppLogBinding) {
            binding.root.onClick {
                getItem(holder.layoutPosition)?.let { item ->
                    item.third?.let {
                        TextDialog.show(childFragmentManager, it.stackTraceToString())
                    }
                }
            }
        }

    }

}