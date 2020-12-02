package io.legado.app.ui.book.toc

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.LiveData
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.App
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.FragmentBookmarkBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.ATH
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.getViewModelOfActivity
import io.legado.app.utils.requestInputMethod
import io.legado.app.utils.viewbindingdelegate.viewBinding


class BookmarkFragment : VMBaseFragment<ChapterListViewModel>(R.layout.fragment_bookmark),
    BookmarkAdapter.Callback,
    ChapterListViewModel.BookmarkCallBack {
    override val viewModel: ChapterListViewModel
        get() = getViewModelOfActivity(ChapterListViewModel::class.java)
    private val binding by viewBinding(FragmentBookmarkBinding::bind)
    private lateinit var adapter: BookmarkAdapter
    private var bookmarkLiveData: LiveData<PagedList<Bookmark>>? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.bookMarkCallBack = this
        initRecyclerView()
        initData()
    }

    private fun initRecyclerView() {
        ATH.applyEdgeEffectColor(binding.recyclerView)
        adapter = BookmarkAdapter(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
    }

    private fun initData() {
        viewModel.book?.let { book ->
            bookmarkLiveData?.removeObservers(viewLifecycleOwner)
            bookmarkLiveData =
                LivePagedListBuilder(
                    App.db.bookmarkDao().observeByBook(book.bookUrl, book.name, book.author), 20
                ).build()
            bookmarkLiveData?.observe(viewLifecycleOwner, { adapter.submitList(it) })
        }
    }

    override fun startBookmarkSearch(newText: String?) {
        if (newText.isNullOrBlank()) {
            initData()
        } else {
            bookmarkLiveData?.removeObservers(viewLifecycleOwner)
            bookmarkLiveData = LivePagedListBuilder(
                App.db.bookmarkDao().liveDataSearch(
                    viewModel.bookUrl,
                    newText
                ), 20
            ).build()
            bookmarkLiveData?.observe(viewLifecycleOwner, { adapter.submitList(it) })
        }
    }


    override fun onClick(bookmark: Bookmark) {
        val bookmarkData = Intent()
        bookmarkData.putExtra("index", bookmark.chapterIndex)
        bookmarkData.putExtra("pageIndex", bookmark.pageIndex)
        activity?.setResult(Activity.RESULT_OK, bookmarkData)
        activity?.finish()
    }

    @SuppressLint("InflateParams")
    override fun onLongClick(bookmark: Bookmark) {
        viewModel.book?.let { book ->
            requireContext().alert(R.string.bookmark) {
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater)
                message = book.name + " • " + bookmark.chapterName
                customView {
                    alertBinding.apply {
                        editView.setHint(R.string.note_content)
                        editView.setText(bookmark.content)
                    }.root
                }
                yesButton {
                    alertBinding.editView.text?.toString()?.let { editContent ->
                        bookmark.content = editContent
                        App.db.bookmarkDao().update(bookmark)
                    }
                }
                noButton()
                neutralButton(R.string.delete) {
                    App.db.bookmarkDao().delete(bookmark)
                }
            }.show().requestInputMethod()
        }
    }
}