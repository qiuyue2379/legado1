@file:Suppress("DEPRECATION")

package io.qiuyue.app.ui.main.bookshelf.style1

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.google.android.material.tabs.TabLayout
import io.qiuyue.app.R
import io.qiuyue.app.constant.AppConst
import io.qiuyue.app.constant.PreferKey
import io.qiuyue.app.data.appDb
import io.qiuyue.app.data.entities.Book
import io.qiuyue.app.data.entities.BookGroup
import io.qiuyue.app.databinding.FragmentBookshelfBinding
import io.qiuyue.app.lib.theme.ATH
import io.qiuyue.app.lib.theme.accentColor
import io.qiuyue.app.ui.book.search.SearchActivity
import io.qiuyue.app.ui.main.bookshelf.BaseBookshelfFragment
import io.qiuyue.app.ui.main.bookshelf.style1.books.BooksFragment
import io.qiuyue.app.utils.getPrefInt
import io.qiuyue.app.utils.putPrefInt
import io.qiuyue.app.utils.toastOnUi
import io.qiuyue.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 书架界面
 */
class BookshelfFragment1 : BaseBookshelfFragment(R.layout.fragment_bookshelf),
    TabLayout.OnTabSelectedListener,
    SearchView.OnQueryTextListener {

    private val binding by viewBinding(FragmentBookshelfBinding::bind)
    private val adapter by lazy { TabFragmentPageAdapter(childFragmentManager) }
    private val tabLayout: TabLayout by lazy {
        binding.titleBar.findViewById(R.id.tab_layout)
    }
    private val bookGroups = mutableListOf<BookGroup>()
    private val fragmentMap = hashMapOf<Long, BooksFragment>()

    override val groupId: Long get() = selectedGroup.groupId

    override val books: List<Book>
        get() {
            val fragment = fragmentMap[selectedGroup.groupId]
            return fragment?.getBooks() ?: emptyList()
        }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        initView()
        initBookGroupData()
    }

    private val selectedGroup: BookGroup
        get() = bookGroups[tabLayout.selectedTabPosition]

    private fun initView() {
        ATH.applyEdgeEffectColor(binding.viewPagerBookshelf)
        tabLayout.isTabIndicatorFullWidth = false
        tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
        tabLayout.setSelectedTabIndicatorColor(requireContext().accentColor)
        tabLayout.setupWithViewPager(binding.viewPagerBookshelf)
        binding.viewPagerBookshelf.offscreenPageLimit = 1
        binding.viewPagerBookshelf.adapter = adapter
    }

    private fun initBookGroupData() {
        launch {
            appDb.bookGroupDao.flowShow().collect {
                upGroup(it)
            }
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.start(requireContext(), query)
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

    override fun onTabReselected(tab: TabLayout.Tab) {
        fragmentMap[selectedGroup.groupId]?.let {
            toastOnUi("${selectedGroup.groupName}(${it.getBooksCount()})")
        }
    }

    override fun onTabUnselected(tab: TabLayout.Tab) = Unit

    override fun onTabSelected(tab: TabLayout.Tab) {
        putPrefInt(PreferKey.saveTabPosition, tab.position)
    }

    override fun gotoTop() {
        fragmentMap[selectedGroup.groupId]?.gotoTop()
    }

    private inner class TabFragmentPageAdapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getPageTitle(position: Int): CharSequence {
            return bookGroups[position].groupName
        }

        override fun getItemPosition(`object`: Any): Int {
            return POSITION_NONE
        }

        override fun getItem(position: Int): Fragment {
            val group = bookGroups[position]
            return BooksFragment.newInstance(position, group.groupId)
        }

        override fun getCount(): Int {
            return bookGroups.size
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val fragment = super.instantiateItem(container, position) as BooksFragment
            val group = bookGroups[position]
            fragmentMap[group.groupId] = fragment
            return fragment
        }

    }
}