@file:Suppress("DEPRECATION")

package io.qiuyue.app.ui.book.toc

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import com.google.android.material.tabs.TabLayout
import io.qiuyue.app.R
import io.qiuyue.app.base.VMBaseActivity
import io.qiuyue.app.databinding.ActivityChapterListBinding
import io.qiuyue.app.lib.theme.ATH
import io.qiuyue.app.lib.theme.accentColor
import io.qiuyue.app.lib.theme.primaryTextColor
import io.qiuyue.app.utils.gone
import io.qiuyue.app.utils.viewbindingdelegate.viewBinding
import io.qiuyue.app.utils.visible


class TocActivity : VMBaseActivity<ActivityChapterListBinding, TocViewModel>() {

    override val binding by viewBinding(ActivityChapterListBinding::inflate)
    override val viewModel by viewModels<TocViewModel>()

    private lateinit var tabLayout: TabLayout
    private var searchView: SearchView? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        tabLayout = binding.titleBar.findViewById(R.id.tab_layout)
        tabLayout.isTabIndicatorFullWidth = false
        tabLayout.setSelectedTabIndicatorColor(accentColor)
        binding.viewPager.adapter = TabFragmentPageAdapter()
        tabLayout.setupWithViewPager(binding.viewPager)
        intent.getStringExtra("bookUrl")?.let {
            viewModel.initBook(it)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_toc, menu)
        val search = menu.findItem(R.id.menu_search)
        searchView = search.actionView as SearchView
        ATH.setTint(searchView!!, primaryTextColor)
        searchView?.maxWidth = resources.displayMetrics.widthPixels
        searchView?.onActionViewCollapsed()
        searchView?.setOnCloseListener {
            tabLayout.visible()
            false
        }
        searchView?.setOnSearchClickListener { tabLayout.gone() }
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                if (tabLayout.selectedTabPosition == 1) {
                    viewModel.startBookmarkSearch(newText)
                } else {
                    viewModel.startChapterListSearch(newText)
                }
                return false
            }
        })
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_reverse_toc -> viewModel.reverseToc {
                setResult(RESULT_OK, Intent().apply {
                    putExtra("index", it.durChapterIndex)
                    putExtra("chapterPos", it.durChapterPos)
                })
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (tabLayout.isGone) {
            searchView?.onActionViewCollapsed()
            tabLayout.visible()
        } else {
            super.onBackPressed()
        }
    }

    @Suppress("DEPRECATION")
    private inner class TabFragmentPageAdapter :
        FragmentPagerAdapter(supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getItem(position: Int): Fragment {
            return when (position) {
                1 -> BookmarkFragment()
                else -> ChapterListFragment()
            }
        }

        override fun getCount(): Int {
            return 2
        }

        override fun getPageTitle(position: Int): CharSequence {
            return when (position) {
                1 -> getString(R.string.bookmark)
                else -> getString(R.string.chapter_list)
            }
        }

    }

}