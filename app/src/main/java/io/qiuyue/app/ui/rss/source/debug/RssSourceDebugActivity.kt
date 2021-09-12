package io.qiuyue.app.ui.rss.source.debug

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.SearchView
import androidx.activity.viewModels
import io.qiuyue.app.R
import io.qiuyue.app.base.VMBaseActivity
import io.qiuyue.app.databinding.ActivitySourceDebugBinding
import io.qiuyue.app.lib.theme.ATH
import io.qiuyue.app.lib.theme.accentColor
import io.qiuyue.app.ui.widget.dialog.TextDialog
import io.qiuyue.app.utils.gone
import io.qiuyue.app.utils.toastOnUi
import io.qiuyue.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch


class RssSourceDebugActivity : VMBaseActivity<ActivitySourceDebugBinding, RssSourceDebugModel>() {

    override val binding by viewBinding(ActivitySourceDebugBinding::inflate)
    override val viewModel by viewModels<RssSourceDebugModel>()

    private val adapter by lazy { RssSourceDebugAdapter(this) }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        initSearchView()
        viewModel.observe { state, msg ->
            launch {
                adapter.addItem(msg)
                if (state == -1 || state == 1000) {
                    binding.rotateLoading.hide()
                }
            }
        }
        viewModel.initData(intent.getStringExtra("key")) {
            startSearch()
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.rss_source_debug, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_list_src ->
                TextDialog.show(supportFragmentManager, viewModel.listSrc)
            R.id.menu_content_src ->
                TextDialog.show(supportFragmentManager, viewModel.contentSrc)
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initRecyclerView() {
        ATH.applyEdgeEffectColor(binding.recyclerView)
        binding.recyclerView.adapter = adapter
        binding.rotateLoading.loadingColor = accentColor
    }

    private fun initSearchView() {
        binding.titleBar.findViewById<SearchView>(R.id.search_view).gone()
    }

    private fun startSearch() {
        adapter.clearItems()
        viewModel.startDebug({
            binding.rotateLoading.show()
        }, {
            toastOnUi("未获取到源")
        })
    }
}