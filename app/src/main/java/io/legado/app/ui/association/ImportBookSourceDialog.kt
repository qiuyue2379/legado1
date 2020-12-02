package io.legado.app.ui.association

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.SimpleRecyclerAdapter
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.BookSource
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemSourceImportBinding
import io.legado.app.help.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.text.AutoCompleteTextView
import io.legado.app.utils.getViewModelOfActivity
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import org.jetbrains.anko.sdk27.listeners.onClick

/**
 * 导入书源弹出窗口
 */
class ImportBookSourceDialog : BaseDialogFragment(), Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)

    val viewModel: ImportBookSourceViewModel
        get() = getViewModelOfActivity(ImportBookSourceViewModel::class.java)
    lateinit var adapter: SourcesAdapter

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
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
        binding.toolBar.setTitle(R.string.import_book_source)
        initMenu()
        adapter = SourcesAdapter(requireContext())
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        adapter.setItems(viewModel.allSources)
        binding.tvCancel.visible()
        binding.tvCancel.onClick {
            dismiss()
        }
        binding.tvOk.visible()
        binding.tvOk.onClick {
            viewModel.importSelect {
                dismiss()
            }
        }
    }

    private fun initMenu() {
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.inflateMenu(R.menu.import_source)
        binding.toolBar.menu.findItem(R.id.menu_Keep_original_name)
            ?.isChecked = AppConfig.importKeepName
    }

    @SuppressLint("InflateParams")
    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_new_group -> {
                alert(R.string.diy_edit_source_group) {
                    var editText: AutoCompleteTextView? = null
                    customView {
                        layoutInflater.inflate(R.layout.dialog_edit_text, null).apply {
                            editText = findViewById(R.id.edit_view)
                        }
                    }
                    okButton {
                        editText?.text?.toString()?.let { group ->
                            viewModel.groupName = group
                            item.title = getString(R.string.diy_edit_source_group_title, group)
                        }
                    }
                    noButton()
                }.show()
            }
            R.id.menu_select_all -> {
                viewModel.selectStatus.forEachIndexed { index, b ->
                    if (!b) {
                        viewModel.selectStatus[index] = true
                    }
                }
                adapter.notifyDataSetChanged()
            }
            R.id.menu_un_select_all -> {
                viewModel.selectStatus.forEachIndexed { index, b ->
                    if (b) {
                        viewModel.selectStatus[index] = false
                    }
                }
                adapter.notifyDataSetChanged()
            }
            R.id.menu_Keep_original_name -> {
                item.isChecked = !item.isChecked
                putPrefBoolean(PreferKey.importKeepName, item.isChecked)
            }
        }
        return false
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        activity?.finish()
    }

    inner class SourcesAdapter(context: Context) :
        SimpleRecyclerAdapter<BookSource, ItemSourceImportBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemSourceImportBinding {
            return ItemSourceImportBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemSourceImportBinding,
            item: BookSource,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                cbSourceName.isChecked = viewModel.selectStatus[holder.layoutPosition]
                cbSourceName.text = item.bookSourceName
                tvSourceState.text =
                    if (viewModel.checkSources[holder.layoutPosition] != null) {
                        "已存在"
                    } else {
                        "新书源"
                    }
            }

        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemSourceImportBinding) {
            binding.apply {
                cbSourceName.setOnCheckedChangeListener { buttonView, isChecked ->
                    if (buttonView.isPressed) {
                        viewModel.selectStatus[holder.layoutPosition] = isChecked
                    }
                }
            }
        }

    }

}