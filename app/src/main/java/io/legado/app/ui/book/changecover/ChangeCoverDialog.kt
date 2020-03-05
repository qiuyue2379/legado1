package io.legado.app.ui.book.changecover

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import io.legado.app.R
import io.legado.app.utils.getViewModel
import io.legado.app.base.BaseDialogFragment
import kotlinx.android.synthetic.main.dialog_change_source.*


class ChangeCoverDialog : BaseDialogFragment(),
    CoverAdapter.CallBack {

    companion object {
        const val tag = "changeCoverDialog"

        fun show(manager: FragmentManager, name: String, author: String) {
            val fragment = (manager.findFragmentByTag(tag) as? ChangeCoverDialog)
                ?: ChangeCoverDialog().apply {
                    val bundle = Bundle()
                    bundle.putString("name", name)
                    bundle.putString("author", author)
                    arguments = bundle
                }
            fragment.show(manager, tag)
        }
    }

    private var callBack: CallBack? = null
    private lateinit var viewModel: ChangeCoverViewModel
    lateinit var adapter: CoverAdapter

    override fun onStart() {
        super.onStart()
        val dm = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(dm)
        dialog?.window?.setLayout((dm.widthPixels * 0.9).toInt(), (dm.heightPixels * 0.9).toInt())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        callBack = activity as? CallBack
        viewModel = getViewModel(ChangeCoverViewModel::class.java)
        return inflater.inflate(R.layout.dialog_change_cover, container)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.searchStateData.observe(viewLifecycleOwner, Observer {
            refresh_progress_bar.isAutoLoading = it
        })
        tool_bar.setTitle(R.string.change_cover_source)
        arguments?.let { bundle ->
            viewModel.initData(bundle)
        }
        recycler_view.layoutManager = GridLayoutManager(requireContext(), 3)
        adapter = CoverAdapter(requireContext(), this)
        recycler_view.adapter = adapter
        viewModel.loadDbSearchBook()
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        viewModel.searchBooksLiveData.observe(viewLifecycleOwner, Observer {
            val diffResult = DiffUtil.calculateDiff(DiffCallBack(adapter.getItems(), it))
            adapter.setItems(it)
            diffResult.dispatchUpdatesTo(adapter)
        })
    }

    override fun changeTo(coverUrl: String) {
        callBack?.coverChangeTo(coverUrl)
        dismiss()
    }

    interface CallBack {
        fun coverChangeTo(coverUrl: String)
    }
}