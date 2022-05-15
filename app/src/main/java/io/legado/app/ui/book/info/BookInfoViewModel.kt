package io.legado.app.ui.book.info

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.BookHelp
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.BookCover
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO

class BookInfoViewModel(application: Application) : BaseViewModel(application) {
    val bookData = MutableLiveData<Book>()
    val chapterListData = MutableLiveData<List<BookChapter>>()
    var inBookshelf = false
    var bookSource: BookSource? = null
    private var changeSourceCoroutine: Coroutine<*>? = null
    var isImportBookOnLine = false

    fun initData(intent: Intent) {
        execute {
            val name = intent.getStringExtra("name") ?: ""
            val author = intent.getStringExtra("author") ?: ""
            val bookUrl = intent.getStringExtra("bookUrl") ?: ""
            appDb.bookDao.getBook(name, author)?.let {
                inBookshelf = true
                upBook(it)
                return@execute
            }
            if (bookUrl.isNotBlank()) {
                appDb.searchBookDao.getSearchBook(bookUrl)?.toBook()?.let {
                    upBook(it)
                    return@execute
                }
            }
            appDb.searchBookDao.getFirstByNameAuthor(name, author)?.toBook()?.let {
                upBook(it)
                return@execute
            }
            throw NoStackTraceException("未找到书籍")
        }.onError {
            context.toastOnUi(it.localizedMessage)
        }
    }

    fun refreshData(intent: Intent) {
        execute {
            val name = intent.getStringExtra("name") ?: ""
            val author = intent.getStringExtra("author") ?: ""
            appDb.bookDao.getBook(name, author)?.let { book ->
                upBook(book)
            }
        }
    }

    private fun upBook(book: Book) {
        execute {
            bookData.postValue(book)
            upCoverByRule(book)
            bookSource = if (book.isLocalBook()) null else
                appDb.bookSourceDao.getBookSource(book.origin)
            isImportBookOnLine = (bookSource?.bookSourceType ?: BookType.local) == BookType.file
            if (book.tocUrl.isEmpty()) {
                loadBookInfo(book)
            } else if (isImportBookOnLine) {
                chapterListData.postValue(emptyList())
            } else {
                val chapterList = appDb.bookChapterDao.getChapterList(book.bookUrl)
                if (chapterList.isNotEmpty()) {
                    chapterListData.postValue(chapterList)
                } else {
                    loadChapter(book)
                }
            }
        }
    }

    private fun upCoverByRule(book: Book) {
        execute {
            if (book.customCoverUrl.isNullOrBlank()) {
                BookCover.searchCover(book)?.let { coverUrl ->
                    book.customCoverUrl = coverUrl
                    bookData.postValue(book)
                    if (inBookshelf) {
                        saveBook(book)
                    }
                }
            }
        }
    }

    fun loadBookInfo(
        book: Book,
        canReName: Boolean = true,
        scope: CoroutineScope = viewModelScope
    ) {
        execute(scope) {
            if (book.isLocalBook()) {
                loadChapter(book, scope)
            } else {
                bookSource?.let { bookSource ->
                    WebBook.getBookInfo(this, bookSource, book, canReName = canReName)
                        .onSuccess(IO) {
                            bookData.postValue(book)
                            if (isImportBookOnLine) {
                                appDb.searchBookDao.update(book.toSearchBook())
                            }
                            if (inBookshelf) {
                                appDb.bookDao.update(book)
                            }
                            loadChapter(it, scope)
                        }.onError {
                            AppLog.put("获取数据信息失败\n${it.localizedMessage}", it)
                            context.toastOnUi(R.string.error_get_book_info)
                        }
                } ?: let {
                    chapterListData.postValue(emptyList())
                    context.toastOnUi(R.string.error_no_source)
                }
            }
        }
    }

    private fun loadChapter(
        book: Book,
        scope: CoroutineScope = viewModelScope
    ) {
        execute(scope) {
            if (book.isLocalBook()) {
                LocalBook.getChapterList(book).let {
                    appDb.bookDao.update(book)
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*it.toTypedArray())
                    chapterListData.postValue(it)
                }
            } else if (isImportBookOnLine) {
                chapterListData.postValue(emptyList())
            } else {
                bookSource?.let { bookSource ->
                    WebBook.getChapterList(this, bookSource, book)
                        .onSuccess(IO) {
                            if (inBookshelf) {
                                appDb.bookDao.update(book)
                                appDb.bookChapterDao.delByBook(book.bookUrl)
                                appDb.bookChapterDao.insert(*it.toTypedArray())
                            }
                            chapterListData.postValue(it)
                        }.onError {
                            chapterListData.postValue(emptyList())
                            AppLog.put("获取目录失败\n${it.localizedMessage}", it)
                            context.toastOnUi(R.string.error_get_chapter_list)
                        }
                } ?: let {
                    chapterListData.postValue(emptyList())
                    context.toastOnUi(R.string.error_no_source)
                }
            }
        }.onError {
            context.toastOnUi("LoadTocError:${it.localizedMessage}")
        }
    }

    fun loadGroup(groupId: Long, success: ((groupNames: String?) -> Unit)) {
        execute {
            appDb.bookGroupDao.getGroupNames(groupId).joinToString(",")
        }.onSuccess {
            success.invoke(it)
        }
    }

    fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        changeSourceCoroutine?.cancel()
        changeSourceCoroutine = execute {
            bookSource = source
            bookData.value?.changeTo(book, toc)
            if (inBookshelf) {
                appDb.bookDao.insert(book)
                appDb.bookChapterDao.insert(*toc.toTypedArray())
            }
            bookData.postValue(book)
            chapterListData.postValue(toc)
        }.onFinally {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }
    }

    fun topBook() {
        execute {
            bookData.value?.let { book ->
                val minOrder = appDb.bookDao.minOrder
                book.order = minOrder - 1
                book.durChapterTime = System.currentTimeMillis()
                appDb.bookDao.update(book)
            }
        }
    }

    fun saveBook(book: Book?, success: (() -> Unit)? = null) {
        book ?: return
        execute {
            if (book.order == 0) {
                book.order = appDb.bookDao.minOrder - 1
            }
            appDb.bookDao.getBook(book.name, book.author)?.let {
                book.durChapterPos = it.durChapterPos
                book.durChapterTitle = it.durChapterTitle
            }
            book.save()
            if (ReadBook.book?.name == book.name && ReadBook.book?.author == book.author) {
                ReadBook.book = book
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun saveChapterList(success: (() -> Unit)?) {
        execute {
            chapterListData.value?.let {
                appDb.bookChapterDao.insert(*it.toTypedArray())
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun addToBookshelf(success: (() -> Unit)?) {
        execute {
            bookData.value?.let { book ->
                if (book.order == 0) {
                    book.order = appDb.bookDao.minOrder - 1
                }
                appDb.bookDao.getBook(book.name, book.author)?.let {
                    book.durChapterPos = it.durChapterPos
                    book.durChapterTitle = it.durChapterTitle
                }
                book.save()
            }
            chapterListData.value?.let {
                appDb.bookChapterDao.insert(*it.toTypedArray())
            }
            inBookshelf = true
        }.onSuccess {
            success?.invoke()
        }
    }

    fun delBook(deleteOriginal: Boolean = false, success: (() -> Unit)? = null) {
        execute {
            bookData.value?.let {
                it.delete()
                inBookshelf = false
                if (it.isLocalBook()) {
                    LocalBook.deleteBook(it, deleteOriginal)
                }
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun clearCache() {
        execute {
            BookHelp.clearCache(bookData.value!!)
        }.onSuccess {
            context.toastOnUi(R.string.clear_cache_success)
        }.onError {
            context.toastOnUi("清理缓存出错\n${it.localizedMessage}")
        }
    }

    fun upEditBook() {
        bookData.value?.let {
            appDb.bookDao.getBook(it.bookUrl)?.let { book ->
                bookData.postValue(book)
            }
        }
    }

    fun changeToLocalBook(bookUrl: String) {
        appDb.bookDao.getBook(bookUrl)?.let { localBook ->
            isImportBookOnLine = false
            inBookshelf = true
            LocalBook.mergeBook(localBook, bookData.value).let {
                bookData.postValue(it)
                loadChapter(it)
            }
        }
    }

}