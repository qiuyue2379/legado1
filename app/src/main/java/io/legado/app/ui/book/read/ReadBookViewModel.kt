package io.legado.app.ui.book.read

import android.app.Application
import android.content.Intent
import io.legado.app.App
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.AppConfig
import io.legado.app.help.BookHelp
import io.legado.app.help.IntentDataHelp
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.help.ReadAloud
import io.legado.app.service.help.ReadBook
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.withContext

class ReadBookViewModel(application: Application) : BaseViewModel(application) {

    var isInitFinish = false

    fun initData(intent: Intent) {
        execute {
            ReadBook.inBookshelf = intent.getBooleanExtra("inBookshelf", true)
            IntentDataHelp.getData<Book>(intent.getStringExtra("key"))?.let {
                initBook(it)
            } ?: intent.getStringExtra("bookUrl")?.let {
                App.db.bookDao().getBook(it)?.let { book ->
                    initBook(book)
                }
            } ?: App.db.bookDao().lastReadBook?.let {
                initBook(it)
            }
        }.onFinally {
            if (ReadBook.inBookshelf) {
                ReadBook.saveRead()
            }
        }
    }

    private fun initBook(book: Book) {
        if (ReadBook.book?.bookUrl != book.bookUrl) {
            ReadBook.resetData(book)
            if (!book.isLocalBook() && ReadBook.webBook == null) {
                autoChangeSource(book.name, book.author)
                return
            }
            isInitFinish = true
            ReadBook.chapterSize = App.db.bookChapterDao().getChapterCount(book.bookUrl)
            if (ReadBook.chapterSize == 0) {
                if (book.tocUrl.isEmpty()) {
                    loadBookInfo(book)
                } else {
                    loadChapterList(book)
                }
            } else {
                if (ReadBook.durChapterIndex > ReadBook.chapterSize - 1) {
                    ReadBook.durChapterIndex = ReadBook.chapterSize - 1
                }
                ReadBook.loadContent(resetPageOffset = true)
            }
        } else {
            isInitFinish = true
            ReadBook.book!!.group = book.group
            ReadBook.titleDate.postValue(book.name)
            ReadBook.upWebBook(book)
            if (!book.isLocalBook() && ReadBook.webBook == null) {
                autoChangeSource(book.name, book.author)
                return
            }
            ReadBook.chapterSize = App.db.bookChapterDao().getChapterCount(book.bookUrl)
            if (ReadBook.chapterSize == 0) {
                if (book.tocUrl.isEmpty()) {
                    loadBookInfo(book)
                } else {
                    loadChapterList(book)
                }
            } else {
                if (ReadBook.curTextChapter != null) {
                    ReadBook.callBack?.upContent(resetPageOffset = false)
                }
            }
        }
    }

    private fun loadBookInfo(
        book: Book,
        changeDruChapterIndex: ((chapters: List<BookChapter>) -> Unit)? = null
    ) {
        execute {
            if (book.isLocalBook()) {
                loadChapterList(book, changeDruChapterIndex)
            } else {
                ReadBook.webBook?.getBookInfo(book, this, canReName = false)
                    ?.onSuccess {
                        loadChapterList(book, changeDruChapterIndex)
                    }
            }
        }
    }

    fun loadChapterList(
        book: Book,
        changeDruChapterIndex: ((chapters: List<BookChapter>) -> Unit)? = null
    ) {
        execute {
            if (book.isLocalBook()) {
                LocalBook.getChapterList(book).let {
                    App.db.bookChapterDao().delByBook(book.bookUrl)
                    App.db.bookChapterDao().insert(*it.toTypedArray())
                    App.db.bookDao().update(book)
                    ReadBook.chapterSize = it.size
                    if (it.isEmpty()) {
                        ReadBook.upMsg(context.getString(R.string.error_load_toc))
                    } else {
                        ReadBook.upMsg(null)
                        ReadBook.loadContent(resetPageOffset = true)
                    }
                }
            } else {
                ReadBook.webBook?.getChapterList(book, this)
                    ?.onSuccess(IO) { cList ->
                        if (cList.isNotEmpty()) {
                            if (changeDruChapterIndex == null) {
                                App.db.bookChapterDao().insert(*cList.toTypedArray())
                                App.db.bookDao().update(book)
                                ReadBook.chapterSize = cList.size
                                ReadBook.upMsg(null)
                                ReadBook.loadContent(resetPageOffset = true)
                            } else {
                                changeDruChapterIndex(cList)
                            }
                        } else {
                            ReadBook.upMsg(context.getString(R.string.error_load_toc))
                        }
                    }?.onError {
                        ReadBook.upMsg(context.getString(R.string.error_load_toc))
                    }
            }
        }.onError {
            ReadBook.upMsg("LoadTocError:${it.localizedMessage}")
        }
    }

    fun changeTo(newBook: Book) {
        execute {
            ReadBook.upMsg(null)
            ReadBook.book?.changeTo(newBook)
            ReadBook.prevTextChapter = null
            ReadBook.curTextChapter = null
            ReadBook.nextTextChapter = null
            withContext(Main) {
                ReadBook.callBack?.upContent()
            }
            ReadBook.book = newBook
            App.db.bookSourceDao().getBookSource(newBook.origin)?.let {
                ReadBook.webBook = WebBook(it)
            }
            if (newBook.tocUrl.isEmpty()) {
                loadBookInfo(newBook) { upChangeDurChapterIndex(newBook, it) }
            } else {
                loadChapterList(newBook) { upChangeDurChapterIndex(newBook, it) }
            }
        }
    }

    private fun autoChangeSource(name: String, author: String) {
        if (!AppConfig.autoChangeSource) return
        execute {
            App.db.bookSourceDao().allTextEnabled.forEach { source ->
                try {
                    val searchBooks = WebBook(source).searchBookSuspend(this, name)
                    searchBooks.getOrNull(0)?.let {
                        if (it.name == name && (it.author == author || author == "")) {
                            changeTo(it.toBook())
                            return@forEach
                        }
                    }
                } catch (e: Exception) {
                    //nothing
                }
            }
        }.onStart {
            ReadBook.upMsg(context.getString(R.string.source_auto_changing))
        }.onFinally {
            ReadBook.upMsg(null)
        }
    }

    private fun upChangeDurChapterIndex(book: Book, chapters: List<BookChapter>) {
        execute {
            ReadBook.durChapterIndex = BookHelp.getDurChapterIndexByChapterTitle(
                book.durChapterTitle,
                book.durChapterIndex,
                chapters
            )
            book.durChapterIndex = ReadBook.durChapterIndex
            book.durChapterTitle = chapters[ReadBook.durChapterIndex].title
            App.db.bookDao().update(book)
            App.db.bookChapterDao().insert(*chapters.toTypedArray())
            ReadBook.chapterSize = chapters.size
            ReadBook.loadContent(resetPageOffset = true)
        }
    }

    fun openChapter(index: Int, pageIndex: Int = 0) {
        ReadBook.prevTextChapter = null
        ReadBook.curTextChapter = null
        ReadBook.nextTextChapter = null
        ReadBook.callBack?.upContent()
        if (index != ReadBook.durChapterIndex) {
            ReadBook.durChapterIndex = index
            ReadBook.durPageIndex = pageIndex
        }
        ReadBook.saveRead()
        ReadBook.loadContent(resetPageOffset = true)
    }

    fun removeFromBookshelf(success: (() -> Unit)?) {
        execute {
            ReadBook.book?.delete()
        }.onSuccess {
            success?.invoke()
        }
    }

    fun upBookSource() {
        execute {
            ReadBook.book?.let { book ->
                App.db.bookSourceDao().getBookSource(book.origin)?.let {
                    ReadBook.webBook = WebBook(it)
                }
            }
        }
    }

    fun refreshContent(book: Book) {
        execute {
            App.db.bookChapterDao().getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?.let { chapter ->
                    BookHelp.delContent(book, chapter)
                    ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (BaseReadAloudService.pause) {
            ReadAloud.stop(context)
        }
    }

}