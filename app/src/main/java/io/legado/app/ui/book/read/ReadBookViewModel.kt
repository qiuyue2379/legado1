package io.legado.app.ui.book.read

import android.app.Application
import android.content.Intent
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.help.AppConfig
import io.legado.app.help.BookHelp
import io.legado.app.help.ContentProcessor
import io.legado.app.help.storage.BookWebDav
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.PreciseSearch
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.help.ReadAloud
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.utils.msg
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.withContext

class ReadBookViewModel(application: Application) : BaseViewModel(application) {
    var isInitFinish = false
    var searchContentQuery = ""

    fun initData(intent: Intent) {
        execute {
            ReadBook.inBookshelf = intent.getBooleanExtra("inBookshelf", true)
            val bookUrl = intent.getStringExtra("bookUrl")
            val book = when {
                bookUrl.isNullOrEmpty() -> appDb.bookDao.lastReadBook
                else -> appDb.bookDao.getBook(bookUrl)
            } ?: ReadBook.book
            when {
                book != null -> initBook(book)
                else -> ReadBook.upMsg(context.getString(R.string.no_book))
            }
        }.onFinally {
            ReadBook.saveRead()
        }
    }

    private fun initBook(book: Book) {
        if (ReadBook.book?.bookUrl != book.bookUrl) {
            ReadBook.resetData(book)
            isInitFinish = true
            if (!book.isLocalBook() && ReadBook.webBook == null) {
                autoChangeSource(book.name, book.author)
                return
            }
            ReadBook.chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
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
            syncBookProgress(book)
        } else {
            ReadBook.book = book
            if (ReadBook.durChapterIndex != book.durChapterIndex) {
                ReadBook.durChapterIndex = book.durChapterIndex
                ReadBook.durChapterPos = book.durChapterPos
                ReadBook.clearTextChapter()
            }
            ReadBook.titleDate.postValue(book.name)
            ReadBook.upWebBook(book)
            isInitFinish = true
            if (!book.isLocalBook() && ReadBook.webBook == null) {
                autoChangeSource(book.name, book.author)
                return
            }
            ReadBook.chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
            if (ReadBook.chapterSize == 0) {
                if (book.tocUrl.isEmpty()) {
                    loadBookInfo(book)
                } else {
                    loadChapterList(book)
                }
            } else {
                if (ReadBook.curTextChapter != null) {
                    ReadBook.callBack?.upContent(resetPageOffset = false)
                } else {
                    ReadBook.loadContent(resetPageOffset = true)
                }
            }
            if (!BaseReadAloudService.isRun) {
                syncBookProgress(book)
            }
        }
    }

    private fun loadBookInfo(
        book: Book,
        changeDruChapterIndex: ((chapters: List<BookChapter>) -> Unit)? = null,
    ) {
        if (book.isLocalBook()) {
            loadChapterList(book, changeDruChapterIndex)
        } else {
            ReadBook.webBook?.getBookInfo(viewModelScope, book, canReName = false)
                ?.onSuccess {
                    loadChapterList(book, changeDruChapterIndex)
                }
        }
    }

    fun loadChapterList(
        book: Book,
        changeDruChapterIndex: ((chapters: List<BookChapter>) -> Unit)? = null,
    ) {
        if (book.isLocalBook()) {
            execute {
                LocalBook.getChapterList(book).let {
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*it.toTypedArray())
                    appDb.bookDao.update(book)
                    ReadBook.chapterSize = it.size
                    if (it.isEmpty()) {
                        ReadBook.upMsg(context.getString(R.string.error_load_toc))
                    } else {
                        ReadBook.upMsg(null)
                        ReadBook.loadContent(resetPageOffset = true)
                    }
                }
            }.onError {
                ReadBook.upMsg("LoadTocError:${it.localizedMessage}")
            }
        } else {
            ReadBook.webBook?.getChapterList(viewModelScope, book)
                ?.onSuccess(IO) { cList ->
                    if (cList.isNotEmpty()) {
                        if (changeDruChapterIndex == null) {
                            appDb.bookChapterDao.insert(*cList.toTypedArray())
                            appDb.bookDao.update(book)
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
    }

    fun syncBookProgress(
        book: Book,
        syncBookProgress: Boolean = AppConfig.syncBookProgress,
        alertSync: ((progress: BookProgress) -> Unit)? = null
    ) {
        if (syncBookProgress)
            execute {
                BookWebDav.getBookProgress(book)
            }.onSuccess {
                it?.let { progress ->
                    if (progress.durChapterIndex < book.durChapterIndex ||
                        (progress.durChapterIndex == book.durChapterIndex && progress.durChapterPos < book.durChapterPos)
                    ) {
                        alertSync?.invoke(progress)
                    } else {
                        ReadBook.setProgress(progress)
                    }
                }
            }
    }

    fun changeTo(newBook: Book) {
        execute {
            var oldTocSize: Int = newBook.totalChapterNum
            ReadBook.upMsg(null)
            ReadBook.book?.let {
                oldTocSize = it.totalChapterNum
                it.changeTo(newBook)
            }
            ReadBook.book = newBook
            appDb.bookSourceDao.getBookSource(newBook.origin)?.let {
                ReadBook.webBook = WebBook(it)
            }
            ReadBook.prevTextChapter = null
            ReadBook.curTextChapter = null
            ReadBook.nextTextChapter = null
            withContext(Main) {
                ReadBook.callBack?.upContent()
            }
            if (newBook.tocUrl.isEmpty()) {
                loadBookInfo(newBook) {
                    upChangeDurChapterIndex(newBook, oldTocSize, it)
                }
            } else {
                loadChapterList(newBook) {
                    upChangeDurChapterIndex(newBook, oldTocSize, it)
                }
            }
        }.onFinally {
            postEvent(EventBus.SOURCE_CHANGED, newBook.bookUrl)
        }
    }

    private fun autoChangeSource(name: String, author: String) {
        if (!AppConfig.autoChangeSource) return
        execute {
            val sources = appDb.bookSourceDao.allTextEnabled
            val book = PreciseSearch.searchFirstBook(this, sources, name, author)
            if (book != null) {
                book.upInfoFromOld(ReadBook.book)
                changeTo(book)
            } else {
                throw Exception("自动换源失败")
            }
        }.onStart {
            ReadBook.upMsg(context.getString(R.string.source_auto_changing))
        }.onError {
            context.toastOnUi(it.msg)
        }.onFinally {
            ReadBook.upMsg(null)
        }
    }

    private fun upChangeDurChapterIndex(book: Book, oldTocSize: Int, chapters: List<BookChapter>) {
        execute {
            ReadBook.durChapterIndex = BookHelp.getDurChapter(
                book.durChapterIndex,
                oldTocSize,
                book.durChapterTitle,
                chapters
            )
            book.durChapterIndex = ReadBook.durChapterIndex
            book.durChapterTitle = chapters[ReadBook.durChapterIndex].title
            appDb.bookDao.update(book)
            appDb.bookChapterDao.insert(*chapters.toTypedArray())
            ReadBook.chapterSize = chapters.size
            ReadBook.upMsg(null)
            ReadBook.loadContent(resetPageOffset = true)
        }
    }

    fun openChapter(index: Int, durChapterPos: Int = 0, success: (() -> Unit)? = null) {
        ReadBook.clearTextChapter()
        ReadBook.callBack?.upContent()
        if (index != ReadBook.durChapterIndex) {
            ReadBook.durChapterIndex = index
            ReadBook.durChapterPos = durChapterPos
        }
        ReadBook.saveRead()
        ReadBook.loadContent(resetPageOffset = true) {
            success?.invoke()
        }
    }

    fun removeFromBookshelf(success: (() -> Unit)?) {
        execute {
            Book.delete(ReadBook.book)
        }.onSuccess {
            success?.invoke()
        }
    }

    fun upBookSource(success: (() -> Unit)?) {
        execute {
            ReadBook.book?.let { book ->
                appDb.bookSourceDao.getBookSource(book.origin)?.let {
                    ReadBook.webBook = WebBook(it)
                }
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun refreshContent(book: Book) {
        execute {
            appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?.let { chapter ->
                    BookHelp.delContent(book, chapter)
                    ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
                }
        }
    }

    fun reverseContent(book: Book) {
        execute {
            appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?.let { chapter ->
                    BookHelp.reverseContent(book, chapter)
                    ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
                }
        }
    }

    /**
     * 内容搜索跳转
     */
    fun searchResultPositions(
        pages: List<TextPage>,
        indexWithinChapter: Int
    ): Array<Int> {
        // calculate search result's pageIndex
        var content = ""
        pages.map {
            content += it.text
        }
        var count = 1
        var index = content.indexOf(searchContentQuery)
        while (count != indexWithinChapter) {
            index = content.indexOf(searchContentQuery, index + 1)
            count += 1
        }
        val contentPosition = index
        var pageIndex = 0
        var length = pages[pageIndex].text.length
        while (length < contentPosition) {
            pageIndex += 1
            if (pageIndex > pages.size) {
                pageIndex = pages.size
                break
            }
            length += pages[pageIndex].text.length
        }

        // calculate search result's lineIndex
        val currentPage = pages[pageIndex]
        var lineIndex = 0
        length = length - currentPage.text.length + currentPage.textLines[lineIndex].text.length
        while (length < contentPosition) {
            lineIndex += 1
            if (lineIndex > currentPage.textLines.size) {
                lineIndex = currentPage.textLines.size
                break
            }
            length += currentPage.textLines[lineIndex].text.length
        }

        // charIndex
        val currentLine = currentPage.textLines[lineIndex]
        length -= currentLine.text.length
        val charIndex = contentPosition - length
        var addLine = 0
        var charIndex2 = 0
        // change line
        if ((charIndex + searchContentQuery.length) > currentLine.text.length) {
            addLine = 1
            charIndex2 = charIndex + searchContentQuery.length - currentLine.text.length - 1
        }
        // changePage
        if ((lineIndex + addLine + 1) > currentPage.textLines.size) {
            addLine = -1
            charIndex2 = charIndex + searchContentQuery.length - currentLine.text.length - 1
        }
        return arrayOf(pageIndex, lineIndex, charIndex, addLine, charIndex2)
    }

    /**
     * 替换规则变化
     */
    fun replaceRuleChanged() {
        execute {
            ReadBook.book?.let {
                ContentProcessor.get(it.name, it.origin).upReplaceRules()
                ReadBook.loadContent(resetPageOffset = false)
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