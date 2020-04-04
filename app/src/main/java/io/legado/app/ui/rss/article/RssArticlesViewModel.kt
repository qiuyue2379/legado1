package io.legado.app.ui.rss.article

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import io.legado.app.App
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.model.Rss
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RssArticlesViewModel(application: Application) : BaseViewModel(application) {
    val loadFinally = MutableLiveData<Boolean>()
    var isLoading = true
    var order = System.currentTimeMillis()
    private var nextPageUrl: String? = null
    private val articles = arrayListOf<RssArticle>()
    var sortName: String = ""
    var sortUrl: String = ""

    fun init(bundle: Bundle?) {
        bundle?.let {
            sortName = it.getString("sortName") ?: ""
            sortUrl = it.getString("sortUrl") ?: ""
        }
    }


    fun loadContent(rssSource: RssSource) {
        isLoading = true
        Rss.getArticles(sortName, sortUrl, rssSource, null)
            .onSuccess(Dispatchers.IO) {
                nextPageUrl = it.nextPageUrl
                it.articles.let { list ->
                    list.forEach { rssArticle ->
                        rssArticle.order = order--
                    }
                    App.db.rssArticleDao().insert(*list.toTypedArray())
                    if (!rssSource.ruleNextPage.isNullOrEmpty()) {
                        App.db.rssArticleDao().clearOld(rssSource.sourceUrl, sortName, order)
                        loadFinally.postValue(true)
                    } else {
                        withContext(Dispatchers.Main) {
                            loadFinally.postValue(false)
                        }
                    }
                    isLoading = false
                }
            }.onError {
                toast(it.localizedMessage)
            }
    }

    fun loadMore(rssSource: RssSource) {
        isLoading = true
        val pageUrl = nextPageUrl
        if (!pageUrl.isNullOrEmpty()) {
            Rss.getArticles(sortName, pageUrl, rssSource, pageUrl)
                .onSuccess(Dispatchers.IO) {
                    nextPageUrl = it.nextPageUrl
                    it.articles.let { list ->
                        if (list.isEmpty()) {
                            loadFinally.postValue(true)
                            return@let
                        }
                        if (articles.contains(list.first())) {
                            loadFinally.postValue(false)
                        } else {
                            list.forEach { rssArticle ->
                                rssArticle.order = order--
                            }
                            App.db.rssArticleDao().insert(*list.toTypedArray())
                        }
                    }
                    isLoading = false
                }
        } else {
            loadFinally.postValue(false)
        }
    }


}