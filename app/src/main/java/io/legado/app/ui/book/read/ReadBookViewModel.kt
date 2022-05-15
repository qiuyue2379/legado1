package io.legado.app.ui.book.read

import android.app.Application
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.BookHelp
import io.legado.app.help.ContentProcessor
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.AppWebDav
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.provider.ImageProvider
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.utils.msg
import io.legado.app.utils.postEvent
import io.legado.app.utils.toStringArray
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO

class ReadBookViewModel(application: Application) : BaseViewModel(application) {
    val permissionDenialLiveData = MutableLiveData<Int>()
    var isInitFinish = false
    var searchContentQuery = ""
    private var changeSourceCoroutine: Coroutine<*>? = null

    /**
     * 初始化
     */
    fun initData(intent: Intent) {
        execute {
            ReadBook.inBookshelf = intent.getBooleanExtra("inBookshelf", true)
            ReadBook.tocChanged = intent.getBooleanExtra("tocChanged", false)
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
        val isSameBook = ReadBook.book?.bookUrl == book.bookUrl
        if (isSameBook) ReadBook.upData(book) else ReadBook.resetData(book)
        isInitFinish = true
        if (ReadBook.chapterSize == 0) {
            if (book.tocUrl.isEmpty()) {
                loadBookInfo(book)
            } else {
                loadChapterList(book)
            }
        } else if (book.isLocalBook()
            && LocalBook.getLastModified(book).getOrDefault(0L) > book.latestChapterTime
        ) {
            loadChapterList(book)
        } else if (isSameBook) {
            if (ReadBook.curTextChapter != null) {
                ReadBook.callBack?.upContent(resetPageOffset = false)
            } else {
                ReadBook.loadContent(resetPageOffset = true)
            }
        } else {
            if (ReadBook.durChapterIndex > ReadBook.chapterSize - 1) {
                ReadBook.durChapterIndex = ReadBook.chapterSize - 1
            }
            ReadBook.loadContent(resetPageOffset = isSameBook)
        }
        if (!isSameBook || !BaseReadAloudService.isRun) {
            syncBookProgress(book)
        }
        if (!book.isLocalBook() && ReadBook.bookSource == null) {
            autoChangeSource(book.name, book.author)
            return
        }
    }

    /**
     * 加载详情页
     */
    private fun loadBookInfo(book: Book) {
        if (book.isLocalBook()) {
            loadChapterList(book)
        } else {
            ReadBook.bookSource?.let { source ->
                WebBook.getBookInfo(viewModelScope, source, book, canReName = false)
                    .onSuccess {
                        loadChapterList(book)
                    }.onError {
                        ReadBook.upMsg("详情页出错: ${it.localizedMessage}")
                    }
            }
        }
    }

    /**
     * 加载目录
     */
    fun loadChapterList(book: Book) {
        if (book.isLocalBook()) {
            execute {
                LocalBook.getChapterList(book).let {
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*it.toTypedArray())
                    appDb.bookDao.update(book)
                    ReadBook.chapterSize = it.size
                    ReadBook.upMsg(null)
                    ReadBook.loadContent(resetPageOffset = true)
                }
            }.onError {
                when (it) {
                    is SecurityException -> {
                        permissionDenialLiveData.postValue(1)
                    }
                    else -> {
                        AppLog.put("LoadTocError:${it.localizedMessage}", it)
                        ReadBook.upMsg("LoadTocError:${it.localizedMessage}")
                    }
                }
            }
        } else {
            ReadBook.bookSource?.let {
                WebBook.getChapterList(viewModelScope, it, book)
                    .onSuccess(IO) { cList ->
                        appDb.bookChapterDao.insert(*cList.toTypedArray())
                        appDb.bookDao.update(book)
                        ReadBook.chapterSize = cList.size
                        ReadBook.upMsg(null)
                        ReadBook.loadContent(resetPageOffset = true)
                    }.onError {
                        ReadBook.upMsg(context.getString(R.string.error_load_toc))
                    }
            }
        }
    }

    /**
     * 同步进度
     */
    fun syncBookProgress(
        book: Book,
        alertSync: ((progress: BookProgress) -> Unit)? = null
    ) {
        execute {
            if (AppWebDav.syncBookProgress) {
                AppWebDav.getBookProgress(book)
                    ?: throw NoStackTraceException("没有进度")
            } else {
                throw NoStackTraceException("进度同步未启用")
            }
        }.onSuccess { progress ->
            if (progress.durChapterIndex < book.durChapterIndex ||
                (progress.durChapterIndex == book.durChapterIndex
                        && progress.durChapterPos < book.durChapterPos)
            ) {
                alertSync?.invoke(progress)
            } else {
                ReadBook.setProgress(progress)
            }
        }

    }

    /**
     * 换源
     */
    fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        changeSourceCoroutine?.cancel()
        changeSourceCoroutine = execute {
            ReadBook.upMsg(context.getString(R.string.loading))
            ReadBook.book?.changeTo(book, toc)
            val nextChapter = toc.getOrElse(book.durChapterIndex) {
                toc.first()
            }
            WebBook.getContentAwait(
                this,
                bookSource = source,
                book = book,
                bookChapter = toc[book.durChapterIndex],
                nextChapterUrl = nextChapter.url
            )
            appDb.bookDao.insert(book)
            appDb.bookChapterDao.insert(*toc.toTypedArray())
            ReadBook.resetData(book)
            ReadBook.upMsg(null)
            ReadBook.loadContent(resetPageOffset = true)
        }.onError {
            context.toastOnUi("换源失败\n${it.localizedMessage}")
            ReadBook.upMsg(null)
        }.onFinally {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }
    }

    /**
     * 自动换源
     */
    private fun autoChangeSource(name: String, author: String) {
        if (!AppConfig.autoChangeSource) return
        execute {
            val sources = appDb.bookSourceDao.allTextEnabled
            sources.forEach { source ->
                WebBook.preciseSearchAwait(this, source, name, author).getOrNull()?.let { book ->
                    if (book.tocUrl.isEmpty()) {
                        WebBook.getBookInfoAwait(this, source, book)
                    }
                    val toc = WebBook.getChapterListAwait(this, source, book).getOrThrow()
                    changeTo(source, book, toc)
                    return@execute
                }
            }
            throw NoStackTraceException("自动换源失败")
        }.onStart {
            ReadBook.upMsg(context.getString(R.string.source_auto_changing))
        }.onError {
            context.toastOnUi(it.msg)
        }.onFinally {
            ReadBook.upMsg(null)
        }
    }

    fun openChapter(index: Int, durChapterPos: Int = 0, success: (() -> Unit)? = null) {
        if (index < ReadBook.chapterSize) {
            ReadBook.clearTextChapter()
            ReadBook.callBack?.upContent()
            ReadBook.durChapterIndex = index
            ReadBook.durChapterPos = durChapterPos
            ReadBook.saveRead()
            ReadBook.loadContent(resetPageOffset = true) {
                success?.invoke()
            }
        }
    }

    fun removeFromBookshelf(success: (() -> Unit)?) {
        execute {
            ReadBook.book?.delete()
        }.onSuccess {
            success?.invoke()
        }
    }

    fun upBookSource(success: (() -> Unit)?) {
        execute {
            ReadBook.book?.let { book ->
                ReadBook.bookSource = appDb.bookSourceDao.getBookSource(book.origin)
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun refreshContentDur(book: Book) {
        execute {
            appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?.let { chapter ->
                    BookHelp.delContent(book, chapter)
                    ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
                }
        }
    }

    fun refreshContentAfter(book: Book) {
        execute {
            appDb.bookChapterDao.getChapterList(
                book.bookUrl,
                ReadBook.durChapterIndex,
                book.totalChapterNum
            ).forEach { chapter ->
                BookHelp.delContent(book, chapter)
            }
            ReadBook.loadContent(false)
        }
    }

    fun refreshContentAll(book: Book) {
        execute {
            BookHelp.clearCache(book)
            ReadBook.loadContent(false)
        }
    }

    /**
     * 保存内容
     */
    fun saveContent(book: Book, content: String) {
        execute {
            appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?.let { chapter ->
                    BookHelp.saveText(book, chapter, content)
                    ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
                }
        }
    }

    /**
     * 反转内容
     */
    fun reverseContent(book: Book) {
        execute {
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@execute
            val content = BookHelp.getContent(book, chapter) ?: return@execute
            val stringBuilder = StringBuilder()
            content.toStringArray().forEach {
                stringBuilder.insert(0, it)
            }
            BookHelp.saveText(book, chapter, stringBuilder.toString())
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
        }
    }

    /**
     * 内容搜索跳转
     */
    fun searchResultPositions(
        textChapter: TextChapter,
        searchResult: SearchResult
    ): Array<Int> {
        // calculate search result's pageIndex
        val pages = textChapter.pages
        val content = textChapter.getContent()

        var count = 0
        var index = content.indexOf(searchContentQuery)
        while (count != searchResult.resultCountWithinChapter) {
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

    fun refreshImage(src: String) {
        execute {
            ImageProvider.bitmapLruCache.remove(src)
            ReadBook.book?.let { book ->
                BookHelp.getImage(book, src).delete()
            }
        }.onFinally {
            ReadBook.loadContent(false)
        }
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

    fun disableSource() {
        execute {
            ReadBook.bookSource?.let {
                it.enabled = false
                appDb.bookSourceDao.update(it)
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