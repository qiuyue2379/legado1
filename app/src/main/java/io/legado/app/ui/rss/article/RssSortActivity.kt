@file:Suppress("DEPRECATION")

package io.legado.app.ui.rss.article

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityRssArtivlesBinding
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.utils.gone
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible

class RssSortActivity : VMBaseActivity<ActivityRssArtivlesBinding, RssSortViewModel>() {

    override val binding by viewBinding(ActivityRssArtivlesBinding::inflate)
    override val viewModel by viewModels<RssSortViewModel>()
    private lateinit var adapter: TabFragmentPageAdapter
    private val sortList = mutableListOf<Pair<String, String>>()
    private val fragmentMap = hashMapOf<String, Fragment>()
    private val upSourceResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.initData(intent) {
                upFragments()
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        adapter = TabFragmentPageAdapter()
        binding.viewPager.adapter = adapter
        binding.tabLayout.setupWithViewPager(binding.viewPager)
        viewModel.titleLiveData.observe(this, {
            binding.titleBar.title = it
        })
        viewModel.initData(intent) {
            upFragments()
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.rss_articles, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_edit_source -> viewModel.rssSource?.sourceUrl?.let {
                upSourceResult.launch(
                    Intent(this, RssSourceEditActivity::class.java)
                        .putExtra("data", it)
                )
            }
            R.id.menu_clear -> {
                viewModel.url?.let {
                    viewModel.clearArticles()
                }
            }
            R.id.menu_switch_layout -> {
                viewModel.switchLayout()
                upFragments()
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun upFragments() {
        viewModel.rssSource?.sortUrls()?.let {
            sortList.clear()
            sortList.addAll(it)
        }
        if (sortList.size == 1) {
            binding.tabLayout.gone()
        } else {
            binding.tabLayout.visible()
        }
        adapter.notifyDataSetChanged()
    }

    private inner class TabFragmentPageAdapter :
        FragmentStatePagerAdapter(supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getItemPosition(`object`: Any): Int {
            return POSITION_NONE
        }

        override fun getPageTitle(position: Int): CharSequence {
            return sortList[position].first
        }

        override fun getItem(position: Int): Fragment {
            val sort = sortList[position]
            return RssArticlesFragment.create(sort.first, sort.second)
        }

        override fun getCount(): Int {
            return sortList.size
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val fragment = super.instantiateItem(container, position) as Fragment
            fragmentMap[sortList[position].first] = fragment
            return fragment
        }
    }

}