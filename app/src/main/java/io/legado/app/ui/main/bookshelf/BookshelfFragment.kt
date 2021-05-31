package io.legado.app.ui.main.bookshelf

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppConst
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.DialogBookshelfConfigBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.FragmentBookshelfBinding
import io.legado.app.help.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.ATH
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.book.arrange.ArrangeBookActivity
import io.legado.app.ui.book.cache.CacheActivity
import io.legado.app.ui.book.group.GroupManageDialog
import io.legado.app.ui.book.local.ImportBookActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.document.FilePicker
import io.legado.app.ui.document.FilePickerParam
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.main.bookshelf.books.BooksFragment
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 书架界面
 */
class BookshelfFragment : VMBaseFragment<BookshelfViewModel>(R.layout.fragment_bookshelf),
    TabLayout.OnTabSelectedListener,
    SearchView.OnQueryTextListener {

    private val binding by viewBinding(FragmentBookshelfBinding::bind)
    override val viewModel: BookshelfViewModel by viewModels()
    private val activityViewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: FragmentStateAdapter
    private lateinit var tabLayout: TabLayout
    private var bookGroupLiveData: LiveData<List<BookGroup>>? = null
    private val bookGroups = mutableListOf<BookGroup>()
    private val fragmentMap = hashMapOf<Long, BooksFragment>()
    private val importBookshelf = registerForActivityResult(FilePicker()) {
        it?.readText(requireContext())?.let { text ->
            viewModel.importBookshelf(text, selectedGroup.groupId)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        tabLayout = binding.titleBar.findViewById(R.id.tab_layout)
        setSupportToolbar(binding.titleBar.toolbar)
        initView()
        initBookGroupData()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_bookshelf, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_search -> startActivity<SearchActivity>()
            R.id.menu_update_toc -> {
                val fragment = fragmentMap[selectedGroup.groupId]
                fragment?.getBooks()?.let {
                    activityViewModel.upToc(it)
                }
            }
            R.id.menu_bookshelf_layout -> configBookshelf()
            R.id.menu_group_manage -> GroupManageDialog()
                .show(childFragmentManager, "groupManageDialog")
            R.id.menu_add_local -> startActivity<ImportBookActivity>()
            R.id.menu_add_url -> addBookByUrl()
            R.id.menu_arrange_bookshelf -> startActivity<ArrangeBookActivity> {
                putExtra("groupId", selectedGroup.groupId)
            }
            R.id.menu_download -> startActivity<CacheActivity> {
                putExtra("groupId", selectedGroup.groupId)
            }
            R.id.menu_export_bookshelf -> {
                val fragment = fragmentMap[selectedGroup.groupId]
                viewModel.exportBookshelf(fragment?.getBooks()) {
                    activity?.share(it)
                }
            }
            R.id.menu_import_bookshelf -> importBookshelfAlert()
        }
    }

    private val selectedGroup: BookGroup
        get() = bookGroups[tabLayout.selectedTabPosition]

    private fun initView() {
        ATH.applyEdgeEffectColor(binding.viewPagerBookshelf)
        tabLayout.isTabIndicatorFullWidth = false
        tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
        tabLayout.setSelectedTabIndicatorColor(requireContext().accentColor)
        binding.viewPagerBookshelf.offscreenPageLimit = 1
        adapter = TabFragmentPageAdapter()
        binding.viewPagerBookshelf.adapter = adapter
        TabLayoutMediator(tabLayout, binding.viewPagerBookshelf) { tab, i ->
            tab.text = bookGroups[i].groupName
        }.attach()
    }

    private fun initBookGroupData() {
        bookGroupLiveData?.removeObservers(viewLifecycleOwner)
        bookGroupLiveData = appDb.bookGroupDao.liveDataShow().apply {
            observe(viewLifecycleOwner) {
                viewModel.checkGroup(it)
                upGroup(it)
            }
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        startActivity<SearchActivity> {
            putExtra("key", query)
        }
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return false
    }

    @Synchronized
    private fun upGroup(data: List<BookGroup>) {
        if (data.isEmpty()) {
            appDb.bookGroupDao.enableGroup(AppConst.bookGroupAllId)
        } else {
            if (data != bookGroups) {
                bookGroups.clear()
                bookGroups.addAll(data)
                adapter.notifyDataSetChanged()
                selectLastTab()
            }
        }
    }

    @Synchronized
    private fun selectLastTab() {
        tabLayout.removeOnTabSelectedListener(this)
        tabLayout.getTabAt(getPrefInt(PreferKey.saveTabPosition, 0))?.select()
        tabLayout.addOnTabSelectedListener(this)
    }

    @SuppressLint("InflateParams")
    private fun configBookshelf() {
        alert(titleResource = R.string.bookshelf_layout) {
            val bookshelfLayout = getPrefInt(PreferKey.bookshelfLayout)
            val bookshelfSort = getPrefInt(PreferKey.bookshelfSort)
            val alertBinding =
                DialogBookshelfConfigBinding.inflate(layoutInflater)
                    .apply {
                        rgLayout.checkByIndex(bookshelfLayout)
                        rgSort.checkByIndex(bookshelfSort)
                        swShowUnread.isChecked = AppConfig.showUnread
                    }
            customView { alertBinding.root }
            okButton {
                alertBinding.apply {
                    var changed = false
                    if (bookshelfLayout != rgLayout.getCheckedIndex()) {
                        putPrefInt(PreferKey.bookshelfLayout, rgLayout.getCheckedIndex())
                        changed = true
                    }
                    if (bookshelfSort != rgSort.getCheckedIndex()) {
                        putPrefInt(PreferKey.bookshelfSort, rgSort.getCheckedIndex())
                        changed = true
                    }
                    if (AppConfig.showUnread != swShowUnread.isChecked) {
                        AppConfig.showUnread = swShowUnread.isChecked
                        changed = true
                    }
                    if (changed) {
                        activity?.recreate()
                    }
                }
            }
            noButton()
        }.show()
    }

    @SuppressLint("InflateParams")
    private fun addBookByUrl() {
        alert(titleResource = R.string.add_book_url) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater)
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    viewModel.addBookByUrl(it)
                }
            }
            noButton()
        }.show()
    }

    override fun onTabReselected(tab: TabLayout.Tab) {
        fragmentMap[selectedGroup.groupId]?.let {
            toastOnUi("${selectedGroup.groupName}(${it.getBooksCount()})")
        }
    }

    override fun onTabUnselected(tab: TabLayout.Tab) = Unit

    override fun onTabSelected(tab: TabLayout.Tab) {
        putPrefInt(PreferKey.saveTabPosition, tab.position)
    }

    fun gotoTop() {
        fragmentMap[selectedGroup.groupId]?.gotoTop()
    }

    private fun importBookshelfAlert() {
        alert(titleResource = R.string.import_bookshelf) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url/json"
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    viewModel.importBookshelf(it, selectedGroup.groupId)
                }
            }
            noButton()
            neutralButton(R.string.select_file) {
                importBookshelf.launch(
                    FilePickerParam(
                        mode = FilePicker.FILE,
                        allowExtensions = arrayOf("txt", "json")
                    )
                )
            }
        }.show()
    }

    private inner class TabFragmentPageAdapter :
        FragmentStateAdapter(this) {

        override fun getItemId(position: Int): Long {
            val group = bookGroups[position]
            return group.groupId
        }

        override fun containsItem(itemId: Long): Boolean {
            return fragmentMap.containsKey(itemId)
        }

        override fun getItemCount(): Int {
            return bookGroups.size
        }

        override fun createFragment(position: Int): Fragment {
            val group = bookGroups[position]
            val fragment = BooksFragment.newInstance(position, group.groupId)
            fragmentMap[group.groupId] = fragment
            return fragment
        }

    }
}