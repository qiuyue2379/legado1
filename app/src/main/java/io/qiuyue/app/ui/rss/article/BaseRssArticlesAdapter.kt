package io.qiuyue.app.ui.rss.article

import android.content.Context
import androidx.viewbinding.ViewBinding
import io.qiuyue.app.base.adapter.RecyclerAdapter
import io.qiuyue.app.data.entities.RssArticle


abstract class BaseRssArticlesAdapter<VB : ViewBinding>(context: Context, val callBack: CallBack) :
    RecyclerAdapter<RssArticle, VB>(context) {

    interface CallBack {
        val isGridLayout: Boolean
        fun readRss(rssArticle: RssArticle)
    }
}