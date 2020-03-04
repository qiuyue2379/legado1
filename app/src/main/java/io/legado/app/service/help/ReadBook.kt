package io.legado.app.service.help

import androidx.lifecycle.MutableLiveData
import io.legado.app.App
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.BookHelp
import io.legado.app.help.IntentDataHelp
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.WebBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.page.ChapterProvider
import io.legado.app.ui.book.read.page.entities.TextChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.anko.toast


object ReadBook {

    var titleDate = MutableLiveData<String>()
    var book: Book? = null
    var inBookshelf = false
    var chapterSize = 0
    var durChapterIndex = 0
    var durPageIndex = 0
    var isLocalBook = true
    var callBack: CallBack? = null
    var prevTextChapter: TextChapter? = null
    var curTextChapter: TextChapter? = null
    var nextTextChapter: TextChapter? = null
    var webBook: WebBook? = null
    private val loadingChapters = arrayListOf<Int>()

    fun resetData(book: Book) {
        this.book = book
        titleDate.postValue(book.name)
        durChapterIndex = book.durChapterIndex
        durPageIndex = book.durChapterPos
        isLocalBook = book.origin == BookType.local
        chapterSize = 0
        prevTextChapter = null
        curTextChapter = null
        nextTextChapter = null
        upWebBook(book.origin)
    }

    fun upWebBook(origin: String) {
        val bookSource = App.db.bookSourceDao().getBookSource(origin)
        webBook = if (bookSource != null) WebBook(bookSource) else null
    }

    fun moveToNextPage() {
        durPageIndex++
        callBack?.upContent()
        saveRead()
    }

    fun moveToNextChapter(upContent: Boolean): Boolean {
        if (durChapterIndex < chapterSize - 1) {
            durPageIndex = 0
            durChapterIndex++
            prevTextChapter = curTextChapter
            curTextChapter = nextTextChapter
            nextTextChapter = null
            book?.let {
                if (curTextChapter == null) {
                    loadContent(durChapterIndex, upContent)
                } else if (upContent) {
                    callBack?.upContent()
                }
                loadContent(durChapterIndex.plus(1), upContent)
                GlobalScope.launch(Dispatchers.IO) {
                    for (i in 2..10) {
                        delay(100)
                        download(durChapterIndex + i)
                    }
                }
            }
            saveRead()
            callBack?.upView()
            curPageChanged()
            return true
        } else {
            return false
        }
    }

    fun moveToPrevChapter(upContent: Boolean, toLast: Boolean = true): Boolean {
        if (durChapterIndex > 0) {
            durPageIndex = if (toLast) prevTextChapter?.lastIndex() ?: 0 else 0
            durChapterIndex--
            nextTextChapter = curTextChapter
            curTextChapter = prevTextChapter
            prevTextChapter = null
            book?.let {
                if (curTextChapter == null) {
                    loadContent(durChapterIndex, upContent)
                } else if (upContent) {
                    callBack?.upContent()
                }
                loadContent(durChapterIndex.minus(1), upContent)
                GlobalScope.launch(Dispatchers.IO) {
                    for (i in -5..-2) {
                        delay(100)
                        download(durChapterIndex + i)
                    }
                }
            }
            saveRead()
            callBack?.upView()
            curPageChanged()
            return true
        } else {
            return false
        }
    }

    fun skipToPage(page: Int) {
        durPageIndex = page
        callBack?.upContent()
        curPageChanged()
        saveRead()
    }

    fun setPageIndex(pageIndex: Int) {
        durPageIndex = pageIndex
        saveRead()
        curPageChanged()
    }

    private fun curPageChanged() {
        callBack?.upPageProgress()
        if (BaseReadAloudService.isRun) {
            readAloud(!BaseReadAloudService.pause)
        }
    }

    /**
     * 朗读
     */
    fun readAloud(play: Boolean = true) {
        val book = book
        val textChapter = curTextChapter
        if (book != null && textChapter != null) {
            val key = IntentDataHelp.putData(textChapter)
            ReadAloud.play(
                App.INSTANCE,
                book.name,
                textChapter.title,
                durPageIndex,
                key,
                play
            )
        }
    }

    fun durChapterPos(): Int {
        curTextChapter?.let {
            if (durPageIndex < it.pageSize()) {
                return durPageIndex
            }
            return it.pageSize() - 1
        }
        return durPageIndex
    }

    /**
     * chapterOnDur: 0为当前页,1为下一页,-1为上一页
     */
    fun textChapter(chapterOnDur: Int = 0): TextChapter? {
        return when (chapterOnDur) {
            0 -> curTextChapter
            1 -> nextTextChapter
            -1 -> prevTextChapter
            else -> null
        }
    }

    /**
     * 加载章节内容
     */
    fun loadContent() {
        loadContent(durChapterIndex)
        loadContent(durChapterIndex + 1)
        loadContent(durChapterIndex - 1)
    }

    fun loadContent(index: Int, upContent: Boolean = true) {
        book?.let { book ->
            if (addLoading(index)) {
                Coroutine.async {
                    App.db.bookChapterDao().getChapter(book.bookUrl, index)?.let { chapter ->
                        BookHelp.getContent(book, chapter)?.let {
                            contentLoadFinish(chapter, it, upContent)
                            removeLoading(chapter.index)
                        } ?: download(chapter)
                    } ?: removeLoading(index)
                }.onError {
                    removeLoading(index)
                }
            }
        }
    }

    private fun download(index: Int) {
        book?.let { book ->
            if (book.isLocalBook()) return
            if (addLoading(index)) {
                Coroutine.async {
                    App.db.bookChapterDao().getChapter(book.bookUrl, index)?.let { chapter ->
                        if (BookHelp.hasContent(book, chapter)) {
                            removeLoading(chapter.index)
                        } else {
                            download(chapter)
                        }
                    } ?: removeLoading(index)
                }.onError {
                    removeLoading(index)
                }
            }
        }
    }

    private fun download(chapter: BookChapter) {
        book?.let { book ->
            webBook?.getContent(book, chapter)
                ?.onSuccess(Dispatchers.IO) { content ->
                    if (content.isNullOrEmpty()) {
                        contentLoadFinish(chapter, App.INSTANCE.getString(R.string.content_empty))
                        removeLoading(chapter.index)
                    } else {
                        BookHelp.saveContent(book, chapter, content)
                        contentLoadFinish(chapter, content)
                        removeLoading(chapter.index)
                    }
                }?.onError {
                    contentLoadFinish(chapter, it.localizedMessage ?: "未知错误")
                    removeLoading(chapter.index)
                }
        }
    }

    private fun addLoading(index: Int): Boolean {
        synchronized(this) {
            if (loadingChapters.contains(index)) return false
            loadingChapters.add(index)
            return true
        }
    }

    private fun removeLoading(index: Int) {
        synchronized(this) {
            loadingChapters.remove(index)
        }
    }

    /**
     * 内容加载完成
     */
    private fun contentLoadFinish(
        chapter: BookChapter,
        content: String,
        upContent: Boolean = true
    ) {
        Coroutine.async {
            if (chapter.index in durChapterIndex - 1..durChapterIndex + 1) {
                val c = BookHelp.disposeContent(
                    chapter.title,
                    book!!.name,
                    webBook?.bookSource?.bookSourceUrl,
                    content,
                    book!!.useReplaceRule
                )
                when (chapter.index) {
                    durChapterIndex -> {
                        curTextChapter = ChapterProvider.getTextChapter(chapter, c, chapterSize)
                        if (upContent) callBack?.upContent()
                        callBack?.upView()
                        curPageChanged()
                        callBack?.contentLoadFinish()
                    }
                    durChapterIndex - 1 -> {
                        prevTextChapter = ChapterProvider.getTextChapter(chapter, c, chapterSize)
                        if (upContent) callBack?.upContent(-1)
                    }
                    durChapterIndex + 1 -> {
                        nextTextChapter = ChapterProvider.getTextChapter(chapter, c, chapterSize)
                        if (upContent) callBack?.upContent(1)
                    }
                }
            }
        }.onError {
            it.printStackTrace()
            App.INSTANCE.toast(it.localizedMessage ?: "ChapterProvider ERROR")
        }
    }

    fun saveRead() {
        Coroutine.async {
            book?.let { book ->
                book.lastCheckCount = 0
                book.durChapterTime = System.currentTimeMillis()
                book.durChapterIndex = durChapterIndex
                book.durChapterPos = durPageIndex
                curTextChapter?.let {
                    book.durChapterTitle = it.title
                }
                App.db.bookDao().update(book)
            }
        }
    }

    interface CallBack {
        fun upContent(relativePosition: Int = 0)
        fun upView()
        fun upPageProgress()
        fun contentLoadFinish()
    }
}