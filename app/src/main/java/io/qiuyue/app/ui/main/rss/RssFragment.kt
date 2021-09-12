package io.qiuyue.app.ui.main.rss

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.viewModels
import io.qiuyue.app.R
import io.qiuyue.app.base.VMBaseFragment
import io.qiuyue.app.constant.AppPattern
import io.qiuyue.app.data.appDb
import io.qiuyue.app.data.entities.RssSource
import io.qiuyue.app.databinding.FragmentRssBinding
import io.qiuyue.app.databinding.ItemRssBinding
import io.qiuyue.app.lib.theme.ATH
import io.qiuyue.app.lib.theme.primaryTextColor
import io.qiuyue.app.ui.rss.article.RssSortActivity
import io.qiuyue.app.ui.rss.favorites.RssFavoritesActivity
import io.qiuyue.app.ui.rss.read.ReadRssActivity
import io.qiuyue.app.ui.rss.source.edit.RssSourceEditActivity
import io.qiuyue.app.ui.rss.source.manage.RssSourceActivity
import io.qiuyue.app.ui.rss.source.manage.RssSourceViewModel
import io.qiuyue.app.ui.rss.subscription.RuleSubActivity
import io.qiuyue.app.utils.cnCompare
import io.qiuyue.app.utils.openUrl
import io.qiuyue.app.utils.splitNotBlank
import io.qiuyue.app.utils.startActivity
import io.qiuyue.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


/**
 * 订阅界面
 */
class RssFragment : VMBaseFragment<RssSourceViewModel>(R.layout.fragment_rss),
    RssAdapter.CallBack {
    private val binding by viewBinding(FragmentRssBinding::bind)
    override val viewModel by viewModels<RssSourceViewModel>()
    private val adapter by lazy { RssAdapter(requireContext(), this) }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var rssFlowJob: Job? = null
    private val groups = linkedSetOf<String>()
    private var groupsMenu: SubMenu? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        initSearchView()
        initRecyclerView()
        initGroupData()
        upRssFlowJob()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_rss, menu)
        groupsMenu = menu.findItem(R.id.menu_group)?.subMenu
        upGroupsMenu()
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_rss_config -> startActivity<RssSourceActivity>()
            R.id.menu_rss_star -> startActivity<RssFavoritesActivity>()
            else -> if (item.groupId == R.id.menu_group_text) {
                searchView.setQuery(item.title, true)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        searchView.clearFocus()
    }

    private fun upGroupsMenu() = groupsMenu?.let { subMenu ->
        subMenu.removeGroup(R.id.menu_group_text)
        groups.sortedWith { o1, o2 ->
            o1.cnCompare(o2)
        }.forEach {
            subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, it)
        }
    }

    private fun initSearchView() {
        ATH.setTint(searchView, primaryTextColor)
        searchView.onActionViewExpanded()
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.rss)
        searchView.clearFocus()
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                upRssFlowJob(newText)
                return false
            }
        })
    }

    private fun initRecyclerView() {
        ATH.applyEdgeEffectColor(binding.recyclerView)
        binding.recyclerView.adapter = adapter
        adapter.addHeaderView {
            ItemRssBinding.inflate(layoutInflater, it, false).apply {
                tvName.setText(R.string.rule_subscription)
                ivIcon.setImageResource(R.drawable.image_legado)
                root.setOnClickListener {
                    startActivity<RuleSubActivity>()
                }
            }
        }
    }

    private fun initGroupData() {
        launch {
            appDb.rssSourceDao.flowGroup().collect {
                groups.clear()
                it.map { group ->
                    groups.addAll(group.splitNotBlank(AppPattern.splitGroupRegex))
                }
                upGroupsMenu()
            }
        }
    }

    private fun upRssFlowJob(searchKey: String? = null) {
        rssFlowJob?.cancel()
        rssFlowJob = launch {
            when {
                searchKey.isNullOrEmpty() -> appDb.rssSourceDao.flowEnabled()
                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    appDb.rssSourceDao.flowEnabledByGroup("%$key%")
                }
                else -> appDb.rssSourceDao.flowEnabled("%$searchKey%")
            }.collect {
                adapter.setItems(it)
            }
        }
    }

    override fun openRss(rssSource: RssSource) {
        if (rssSource.singleUrl) {
            if (rssSource.sourceUrl.startsWith("http", true)) {
                startActivity<ReadRssActivity> {
                    putExtra("title", rssSource.sourceName)
                    putExtra("origin", rssSource.sourceUrl)
                }
            } else {
                context?.openUrl(rssSource.sourceUrl)
            }
        } else {
            startActivity<RssSortActivity> {
                putExtra("url", rssSource.sourceUrl)
            }
        }
    }

    override fun toTop(rssSource: RssSource) {
        viewModel.topSource(rssSource)
    }

    override fun edit(rssSource: RssSource) {
        startActivity<RssSourceEditActivity> {
            putExtra("data", rssSource.sourceUrl)
        }
    }

    override fun del(rssSource: RssSource) {
        viewModel.del(rssSource)
    }
}