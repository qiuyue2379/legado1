package io.legado.app.ui.book.search

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.help.config.AppConfig
import io.legado.app.model.webBook.SearchModel
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(application: Application) : BaseViewModel(application) {
    val bookshelf = hashSetOf<String>()
    val upAdapterLiveData = MutableLiveData<String>()
    val searchScope: SearchScope = SearchScope(AppConfig.searchScope)
    private val searchModel = SearchModel(viewModelScope, object : SearchModel.CallBack {

        override fun getSearchScope(): SearchScope {
            return searchScope
        }

        override fun onSearchStart() {
            isSearchLiveData.postValue(true)
        }

        override fun onSearchSuccess(searchBooks: ArrayList<SearchBook>) {
            searchFlowCallBack?.invoke(searchBooks)
        }

        override fun onSearchFinish(isEmpty: Boolean) {
            isSearchLiveData.postValue(false)
            searchFinishCallback?.invoke(isEmpty)
        }

        override fun onSearchCancel() {
            isSearchLiveData.postValue(false)
        }
    })
    var searchFinishCallback: ((isEmpty: Boolean) -> Unit)? = null
    var isSearchLiveData = MutableLiveData<Boolean>()
    var searchKey: String = ""
    private var searchID = 0L
    private var searchFlowCallBack: ((searchBooks: ArrayList<SearchBook>) -> Unit)? = null
    val searchDataFlow = callbackFlow {

        searchFlowCallBack = {
            trySend(ArrayList(it))
        }

        awaitClose {
            searchFlowCallBack = null
        }
    }.flowOn(IO)


    init {
        execute {
            appDb.bookDao.flowAll().mapLatest { books ->
                books.map { "${it.name}-${it.author}" }
            }.collect {
                bookshelf.clear()
                bookshelf.addAll(it)
                upAdapterLiveData.postValue("isInBookshelf")
            }
        }.onError {
            AppLog.put("加载书架数据失败", it)
        }
    }

    /**
     * 开始搜索
     */
    fun search(key: String) {
        if ((searchKey == key) || key.isNotEmpty()) {
            searchModel.cancelSearch()
            searchID = System.currentTimeMillis()
            searchKey = key
        }
        if (searchKey.isEmpty()) {
            return
        }
        searchModel.search(searchID, searchKey)
    }

    /**
     * 停止搜索
     */
    fun stop() {
        searchModel.cancelSearch()
    }

    /**
     * 保存搜索关键字
     */
    fun saveSearchKey(key: String) {
        execute {
            appDb.searchKeywordDao.get(key)?.let {
                it.usage = it.usage + 1
                it.lastUseTime = System.currentTimeMillis()
                appDb.searchKeywordDao.update(it)
            } ?: appDb.searchKeywordDao.insert(SearchKeyword(key, 1))
        }
    }

    /**
     * 清楚搜索关键字
     */
    fun clearHistory() {
        execute {
            appDb.searchKeywordDao.deleteAll()
        }
    }

    fun deleteHistory(searchKeyword: SearchKeyword) {
        appDb.searchKeywordDao.delete(searchKeyword)
    }

    override fun onCleared() {
        super.onCleared()
        searchModel.close()
    }

}
