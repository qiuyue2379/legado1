package io.legado.app.ui.book.changesource

import android.app.Application
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppPattern
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.CompositeCoroutine
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import splitties.init.appCtx
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.min

@Suppress("MemberVisibilityCanBePrivate")
open class ChangeBookSourceViewModel(application: Application) : BaseViewModel(application) {
    private val threadCount = AppConfig.threadCount
    private var searchPool: ExecutorCoroutineDispatcher? = null
    val searchStateData = MutableLiveData<Boolean>()
    var searchFinishCallback: ((isEmpty: Boolean) -> Unit)? = null
    var name: String = ""
    var author: String = ""
    private var tasks = CompositeCoroutine()
    private var screenKey: String = ""
    private var bookSourceList = arrayListOf<BookSource>()
    private val searchBooks = Collections.synchronizedList(arrayListOf<SearchBook>())
    private val searchGroup get() = appCtx.getPrefString("searchGroup") ?: ""
    private var searchCallback: SourceCallback? = null
    val searchDataFlow = callbackFlow {

        searchCallback = object : SourceCallback {

            override fun searchSuccess(searchBook: SearchBook) {
                appDb.searchBookDao.insert(searchBook)
                when {
                    screenKey.isEmpty() -> searchBooks.add(searchBook)
                    searchBook.name.contains(screenKey) -> searchBooks.add(searchBook)
                    else -> return
                }
                trySend(arrayOf(searchBooks))
            }

            override fun upAdapter() {
                trySend(arrayOf(searchBooks))
            }

        }

        getDbSearchBooks().let {
            searchBooks.clear()
            searchBooks.addAll(it)
            trySend(arrayOf(searchBooks))
        }

        if (searchBooks.isEmpty()) {
            startSearch()
        }

        awaitClose {
            searchCallback = null
        }
    }.map {
        searchBooks.sortedBy { it.originOrder }
    }.flowOn(IO)

    @Volatile
    private var searchIndex = -1

    @CallSuper
    open fun initData(arguments: Bundle?) {
        arguments?.let { bundle ->
            bundle.getString("name")?.let {
                name = it
            }
            bundle.getString("author")?.let {
                author = it.replace(AppPattern.authorRegex, "")
            }
        }
    }

    private fun initSearchPool() {
        searchPool = Executors
            .newFixedThreadPool(min(threadCount, AppConst.MAX_THREAD)).asCoroutineDispatcher()
        searchIndex = -1
    }

    fun refresh() {
        getDbSearchBooks().let {
            searchBooks.clear()
            searchBooks.addAll(it)
            searchCallback?.upAdapter()
        }
    }

    fun startSearch() {
        execute {
            stopSearch()
            appDb.searchBookDao.clear(name, author)
            searchBooks.clear()
            bookSourceList.clear()
            if (searchGroup.isBlank()) {
                bookSourceList.addAll(appDb.bookSourceDao.allEnabled)
            } else {
                val sources = appDb.bookSourceDao.getEnabledByGroup(searchGroup)
                if (sources.isEmpty()) {
                    bookSourceList.addAll(appDb.bookSourceDao.allEnabled)
                } else {
                    bookSourceList.addAll(sources)
                }
            }
            searchStateData.postValue(true)
            initSearchPool()
            for (i in 0 until threadCount) {
                search()
            }
        }
    }

    private fun search() {
        synchronized(this) {
            if (searchIndex >= bookSourceList.lastIndex) {
                return
            }
            searchIndex++
        }
        val source = bookSourceList[searchIndex]
        val task = Coroutine.async(scope = viewModelScope, context = searchPool!!) {
            val resultBooks = WebBook.searchBookAwait(this, source, name)
            resultBooks.forEach { searchBook ->
                if (searchBook.name == name) {
                    if ((AppConfig.changeSourceCheckAuthor && searchBook.author.contains(author))
                        || !AppConfig.changeSourceCheckAuthor
                    ) {
                        if (searchBook.latestChapterTitle.isNullOrEmpty()) {
                            if (AppConfig.changeSourceLoadInfo || AppConfig.changeSourceLoadToc) {
                                loadBookInfo(this, source, searchBook.toBook())
                            } else {
                                searchCallback?.searchSuccess(searchBook)
                            }
                        } else {
                            searchCallback?.searchSuccess(searchBook)
                        }
                    }
                }
            }
        }.timeout(60000L)
            .onError {
                nextSearch()
            }
            .onSuccess {
                nextSearch()
            }
        tasks.add(task)
    }

    private suspend fun loadBookInfo(scope: CoroutineScope, source: BookSource, book: Book) {
        val mBook = WebBook.getBookInfoAwait(scope, source, book)
        if (context.getPrefBoolean(PreferKey.changeSourceLoadToc)) {
            loadBookToc(scope, source, mBook)
        } else {
            //从详情页里获取最新章节
            book.latestChapterTitle = mBook.latestChapterTitle
            val searchBook = book.toSearchBook()
            searchCallback?.searchSuccess(searchBook)
        }
    }

    private suspend fun loadBookToc(scope: CoroutineScope, source: BookSource, book: Book) {
        val chapters = WebBook.getChapterListAwait(scope, source, book)
        book.latestChapterTitle = chapters.last().title
        val searchBook: SearchBook = book.toSearchBook()
        searchCallback?.searchSuccess(searchBook)
    }

    private fun nextSearch() {
        synchronized(this) {
            if (searchIndex < bookSourceList.lastIndex) {
                search()
            } else {
                searchIndex++
            }
            if (searchIndex >= bookSourceList.lastIndex + bookSourceList.size
                || searchIndex >= bookSourceList.lastIndex + threadCount
            ) {
                searchStateData.postValue(false)
                tasks.clear()
                searchFinishCallback?.invoke(searchBooks.isEmpty())
            }
        }
    }

    private fun getDbSearchBooks(): List<SearchBook> {
        return if (screenKey.isEmpty()) {
            if (AppConfig.changeSourceCheckAuthor) {
                appDb.searchBookDao.getChangeSourceSearch(name, author, searchGroup)
            } else {
                appDb.searchBookDao.getChangeSourceSearch(name, "", searchGroup)
            }
        } else {
            if (AppConfig.changeSourceCheckAuthor) {
                appDb.searchBookDao.getChangeSourceSearch(name, author, screenKey, searchGroup)
            } else {
                appDb.searchBookDao.getChangeSourceSearch(name, "", screenKey, searchGroup)
            }
        }
    }

    /**
     * 筛选
     */
    fun screen(key: String?) {
        screenKey = key?.trim() ?: ""
        execute {
            getDbSearchBooks().let {
                searchBooks.clear()
                searchBooks.addAll(it)
                searchCallback?.upAdapter()
            }
        }
    }

    fun startOrStopSearch() {
        if (tasks.isEmpty) {
            startSearch()
        } else {
            stopSearch()
        }
    }

    fun stopSearch() {
        tasks.clear()
        searchPool?.close()
        searchStateData.postValue(false)
    }

    override fun onCleared() {
        super.onCleared()
        searchPool?.close()
    }

    fun disableSource(searchBook: SearchBook) {
        execute {
            appDb.bookSourceDao.getBookSource(searchBook.origin)?.let { source ->
                source.enabled = false
                appDb.bookSourceDao.update(source)
            }
            searchBooks.remove(searchBook)
            searchCallback?.upAdapter()
        }
    }

    fun topSource(searchBook: SearchBook) {
        execute {
            appDb.bookSourceDao.getBookSource(searchBook.origin)?.let { source ->
                val minOrder = appDb.bookSourceDao.minOrder - 1
                source.customOrder = minOrder
                searchBook.originOrder = source.customOrder
                appDb.bookSourceDao.update(source)
                updateSource(searchBook)
            }
            searchCallback?.upAdapter()
        }
    }

    fun bottomSource(searchBook: SearchBook) {
        execute {
            appDb.bookSourceDao.getBookSource(searchBook.origin)?.let { source ->
                val maxOrder = appDb.bookSourceDao.maxOrder + 1
                source.customOrder = maxOrder
                searchBook.originOrder = source.customOrder
                appDb.bookSourceDao.update(source)
                updateSource(searchBook)
            }
            searchCallback?.upAdapter()
        }
    }

    fun updateSource(searchBook: SearchBook) {
        appDb.searchBookDao.update(searchBook)
    }

    fun del(searchBook: SearchBook) {
        execute {
            appDb.bookSourceDao.getBookSource(searchBook.origin)?.let { source ->
                appDb.bookSourceDao.delete(source)
                appDb.searchBookDao.delete(searchBook)
            }
        }
        searchBooks.remove(searchBook)
        searchCallback?.upAdapter()
    }

    fun firstSourceOrNull(searchBook: SearchBook): SearchBook? {
        return searchBooks.firstOrNull { it.bookUrl != searchBook.bookUrl }
    }

    interface SourceCallback {

        fun searchSuccess(searchBook: SearchBook)

        fun upAdapter()

    }

}