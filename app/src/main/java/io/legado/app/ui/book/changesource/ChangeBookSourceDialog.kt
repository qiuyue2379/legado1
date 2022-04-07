package io.legado.app.ui.book.changesource

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppPattern
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.DialogBookChangeSourceBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch


class ChangeBookSourceDialog() : BaseDialogFragment(R.layout.dialog_book_change_source),
    Toolbar.OnMenuItemClickListener,
    ChangeBookSourceAdapter.CallBack {

    constructor(name: String, author: String) : this() {
        arguments = Bundle().apply {
            putString("name", name)
            putString("author", author)
        }
    }

    private val binding by viewBinding(DialogBookChangeSourceBinding::bind)
    private val groups = linkedSetOf<String>()
    private val callBack: CallBack? get() = activity as? CallBack
    private val viewModel: ChangeBookSourceViewModel by viewModels()
    private val waitDialog by lazy { WaitDialog(requireContext()) }
    private val adapter by lazy { ChangeBookSourceAdapter(requireContext(), viewModel, this) }
    private val editSourceResult =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            viewModel.startSearch()
        }
    private val searchFinishCallback: (isEmpty: Boolean) -> Unit = {
        if (it) {
            val searchGroup = AppConfig.searchGroup
            if (searchGroup.isNotEmpty()) {
                launch {
                    alert("搜索结果为空") {
                        setMessage("${searchGroup}分组搜索结果为空,是否切换到全部分组")
                        cancelButton()
                        okButton {
                            AppConfig.searchGroup = ""
                            viewModel.startSearch()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        viewModel.initData(arguments)
        showTitle()
        initMenu()
        initRecyclerView()
        initSearchView()
        initBottomBar()
        initLiveData()
        viewModel.searchFinishCallback = searchFinishCallback
    }

    private fun showTitle() {
        binding.toolBar.title = viewModel.name
        binding.toolBar.subtitle = viewModel.author
    }

    private fun initMenu() {
        binding.toolBar.inflateMenu(R.menu.change_source)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.menu.findItem(R.id.menu_check_author)
            ?.isChecked = AppConfig.changeSourceCheckAuthor
        binding.toolBar.menu.findItem(R.id.menu_load_info)
            ?.isChecked = AppConfig.changeSourceLoadInfo
        binding.toolBar.menu.findItem(R.id.menu_load_toc)
            ?.isChecked = AppConfig.changeSourceLoadToc
    }

    private fun initRecyclerView() {
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == 0) {
                    binding.recyclerView.scrollToPosition(0)
                }
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                if (toPosition == 0) {
                    binding.recyclerView.scrollToPosition(0)
                }
            }
        })
    }

    private fun initSearchView() {
        val searchView = binding.toolBar.menu.findItem(R.id.menu_screen).actionView as SearchView
        searchView.setOnCloseListener {
            showTitle()
            false
        }
        searchView.setOnSearchClickListener {
            binding.toolBar.title = ""
            binding.toolBar.subtitle = ""
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.screen(newText)
                return false
            }

        })
    }

    private fun initBottomBar() {
        binding.tvDur.text = callBack?.oldBook?.originName
        binding.tvDur.setOnClickListener {
            scrollToDurSource()
        }
        binding.ivTop.setOnClickListener {
            binding.recyclerView.scrollToPosition(0)
        }
        binding.ivBottom.setOnClickListener {
            binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun initLiveData() {
        viewModel.searchStateData.observe(viewLifecycleOwner) {
            binding.refreshProgressBar.isAutoLoading = it
            if (it) {
                startStopMenuItem?.let { item ->
                    item.setIcon(R.drawable.ic_stop_black_24dp)
                    item.setTitle(R.string.stop)
                }
            } else {
                startStopMenuItem?.let { item ->
                    item.setIcon(R.drawable.ic_refresh_black_24dp)
                    item.setTitle(R.string.refresh)
                }
            }
            binding.toolBar.menu.applyTint(requireContext())
        }
        lifecycleScope.launchWhenStarted {
            viewModel.searchDataFlow.conflate().collect {
                adapter.setItems(it)
                delay(1000)
            }
        }
        launch {
            appDb.bookSourceDao.flowGroupEnabled().conflate().collect {
                groups.clear()
                it.map { group ->
                    groups.addAll(group.splitNotBlank(AppPattern.splitGroupRegex))
                }
                upGroupMenu()
            }
        }
    }

    private val startStopMenuItem: MenuItem?
        get() = binding.toolBar.menu.findItem(R.id.menu_start_stop)

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_check_author -> {
                AppConfig.changeSourceCheckAuthor = !item.isChecked
                item.isChecked = !item.isChecked
                viewModel.refresh()
            }
            R.id.menu_load_info -> {
                AppConfig.changeSourceLoadInfo = !item.isChecked
                item.isChecked = !item.isChecked
            }
            R.id.menu_load_toc -> {
                AppConfig.changeSourceLoadToc = !item.isChecked
                item.isChecked = !item.isChecked
            }
            R.id.menu_start_stop -> viewModel.startOrStopSearch()
            R.id.menu_source_manage -> startActivity<BookSourceActivity>()
            else -> if (item?.groupId == R.id.source_group) {
                if (!item.isChecked) {
                    item.isChecked = true
                    if (item.title.toString() == getString(R.string.all_source)) {
                        AppConfig.searchGroup = ""
                    } else {
                        AppConfig.searchGroup = item.title.toString()
                    }
                    viewModel.startOrStopSearch()
                    viewModel.refresh()
                }
            }
        }
        return false
    }

    private fun scrollToDurSource() {
        adapter.getItems().forEachIndexed { index, searchBook ->
            if (searchBook.bookUrl == bookUrl) {
                (binding.recyclerView.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(index, 60.dpToPx())
                return
            }
        }
    }

    override fun changeTo(searchBook: SearchBook) {
        if (searchBook.type == callBack?.oldBook?.type) {
            changeSource(searchBook) {
                dismissAllowingStateLoss()
            }
        } else {
            alert(
                titleResource = R.string.book_type_different,
                messageResource = R.string.soure_change_source
            ) {
                okButton {
                    changeSource(searchBook) {
                        dismissAllowingStateLoss()
                    }
                }
                cancelButton()
            }
        }
    }

    override val bookUrl: String?
        get() = callBack?.oldBook?.bookUrl

    override fun topSource(searchBook: SearchBook) {
        viewModel.topSource(searchBook)
    }

    override fun bottomSource(searchBook: SearchBook) {
        viewModel.bottomSource(searchBook)
    }

    override fun editSource(searchBook: SearchBook) {
        editSourceResult.launch {
            putExtra("sourceUrl", searchBook.origin)
        }
    }

    override fun disableSource(searchBook: SearchBook) {
        viewModel.disableSource(searchBook)
    }

    override fun deleteSource(searchBook: SearchBook) {
        viewModel.del(searchBook)
        if (bookUrl == searchBook.bookUrl) {
            viewModel.autoChangeSource(callBack?.oldBook?.type) { book, toc, source ->
                callBack?.changeTo(source, book, toc)
            }
        }
    }

    private fun changeSource(searchBook: SearchBook, onSuccess: (() -> Unit)? = null) {
        waitDialog.setText(R.string.load_toc)
        waitDialog.show()
        val book = searchBook.toBook()
        viewModel.getToc(book, {
            waitDialog.dismiss()
            toastOnUi(it)
        }) { toc, source ->
            waitDialog.dismiss()
            callBack?.changeTo(source, book, toc)
            onSuccess?.invoke()
        }
    }

    /**
     * 更新分组菜单
     */
    private fun upGroupMenu() {
        val menu: Menu = binding.toolBar.menu
        val selectedGroup = AppConfig.searchGroup
        menu.removeGroup(R.id.source_group)
        val allItem = menu.add(R.id.source_group, Menu.NONE, Menu.NONE, R.string.all_source)
        var hasSelectedGroup = false
        groups.sortedWith { o1, o2 ->
            o1.cnCompare(o2)
        }.forEach { group ->
            menu.add(R.id.source_group, Menu.NONE, Menu.NONE, group)?.let {
                if (group == selectedGroup) {
                    it.isChecked = true
                    hasSelectedGroup = true
                }
            }
        }
        menu.setGroupCheckable(R.id.source_group, true, true)
        if (!hasSelectedGroup) {
            allItem.isChecked = true
        }
    }

    override fun observeLiveBus() {
        observeEvent<String>(EventBus.SOURCE_CHANGED) {
            adapter.notifyItemRangeChanged(
                0,
                adapter.itemCount,
                bundleOf(Pair("upCurSource", bookUrl))
            )
        }
    }

    interface CallBack {
        val oldBook: Book?
        fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>)
    }

}