package io.legado.app.ui.book.info

import android.app.Application
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import io.legado.app.App
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.BookHelp
import io.legado.app.model.WebBook
import io.legado.app.model.localBook.AnalyzeTxtFile
import io.legado.app.model.localBook.LocalBook
import kotlinx.coroutines.Dispatchers.IO

class BookInfoViewModel(application: Application) : BaseViewModel(application) {
    val bookData = MutableLiveData<Book>()
    val chapterListData = MutableLiveData<List<BookChapter>>()
    var durChapterIndex = 0
    var inBookshelf = false

    fun initData(intent: Intent) {
        execute {
            intent.getStringExtra("bookUrl")?.let {
                App.db.bookDao().getBook(it)?.let { book ->
                    inBookshelf = true
                    setBook(book)
                } ?: App.db.searchBookDao().getSearchBook(it)?.toBook()?.let { book ->
                    setBook(book)
                }
            }
        }
    }

    private fun setBook(book: Book) {
        durChapterIndex = book.durChapterIndex
        bookData.postValue(book)
        if (book.tocUrl.isEmpty()) {
            loadBookInfo(book)
        } else {
            val chapterList = App.db.bookChapterDao().getChapterList(book.bookUrl)
            if (chapterList.isNotEmpty()) {
                chapterListData.postValue(chapterList)
            } else {
                loadChapter(book)
            }
        }
    }

    fun loadBookInfo(
        book: Book,
        changeDruChapterIndex: ((chapters: List<BookChapter>) -> Unit)? = null
    ) {
        execute {
            if (book.isLocalBook()) {
                loadChapter(book, changeDruChapterIndex)
            } else {
                App.db.bookSourceDao().getBookSource(book.origin)?.let { bookSource ->
                    WebBook(bookSource).getBookInfo(book, this)
                        .onSuccess(IO) {
                            it?.let {
                                bookData.postValue(book)
                                if (inBookshelf) {
                                    App.db.bookDao().update(book)
                                }
                                loadChapter(it, changeDruChapterIndex)
                            }
                        }.onError {
                            toast(R.string.error_get_book_info)
                        }
                } ?: let {
                    chapterListData.postValue(null)
                    toast(R.string.error_no_source)
                }
            }
        }
    }

    private fun loadChapter(
        book: Book,
        changeDruChapterIndex: ((chapters: List<BookChapter>) -> Unit)? = null
    ) {
        execute {
            if (book.isLocalBook()) {
                AnalyzeTxtFile.analyze(context, book).let {
                    App.db.bookDao().update(book)
                    App.db.bookChapterDao().insert(*it.toTypedArray())
                    chapterListData.postValue(it)
                }
            } else {
                App.db.bookSourceDao().getBookSource(book.origin)?.let { bookSource ->
                    WebBook(bookSource).getChapterList(book, this)
                        .onSuccess(IO) {
                            it?.let {
                                if (it.isNotEmpty()) {
                                    if (inBookshelf) {
                                        App.db.bookDao().update(book)
                                        App.db.bookChapterDao().insert(*it.toTypedArray())
                                    }
                                    if (changeDruChapterIndex == null) {
                                        chapterListData.postValue(it)
                                    } else {
                                        changeDruChapterIndex(it)
                                    }
                                } else {
                                    toast(R.string.chapter_list_empty)
                                }
                            }
                        }.onError {
                            chapterListData.postValue(null)
                            toast(R.string.error_get_chapter_list)
                        }
                } ?: let {
                    chapterListData.postValue(null)
                    toast(R.string.error_no_source)
                }
            }
        }
    }

    fun loadGroup(groupId: Int, success: ((groupNames: String?) -> Unit)) {
        execute {
            val groupNames = arrayListOf<String>()
            App.db.bookGroupDao().all.forEach {
                if (groupId and it.groupId > 0) {
                    groupNames.add(it.groupName)
                }
            }
            groupNames.joinToString(",")
        }.onSuccess {
            success.invoke(it)
        }
    }

    fun changeTo(book: Book) {
        execute {
            if (inBookshelf) {
                bookData.value?.let {
                    App.db.bookDao().delete(it)
                }
                App.db.bookDao().insert(book)
            }
            bookData.postValue(book)
            if (book.tocUrl.isEmpty()) {
                loadBookInfo(book) { upChangeDurChapterIndex(book, it) }
            } else {
                loadChapter(book) { upChangeDurChapterIndex(book, it) }
            }
        }
    }

    private fun upChangeDurChapterIndex(book: Book, chapters: List<BookChapter>) {
        execute {
            book.durChapterIndex = BookHelp.getDurChapterIndexByChapterTitle(
                book.durChapterTitle,
                book.durChapterIndex,
                chapters
            )
            book.durChapterTitle = chapters[book.durChapterIndex].title
            App.db.bookDao().insert(book)
            App.db.bookChapterDao().insert(*chapters.toTypedArray())
            bookData.postValue(book)
            chapterListData.postValue(chapters)
        }
    }

    fun saveBook(success: (() -> Unit)? = null) {
        execute {
            bookData.value?.let { book ->
                App.db.bookDao().insert(book)
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun saveChapterList(success: (() -> Unit)?) {
        execute {
            chapterListData.value?.let {
                App.db.bookChapterDao().insert(*it.toTypedArray())
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    fun addToBookshelf(success: (() -> Unit)?) {
        execute {
            bookData.value?.let { book ->
                App.db.bookDao().insert(book)
            }
            chapterListData.value?.let {
                App.db.bookChapterDao().insert(*it.toTypedArray())
            }
            inBookshelf = true
        }.onSuccess {
            success?.invoke()
        }
    }

    fun delBook(deleteOriginal: Boolean = false, success: (() -> Unit)? = null) {
        execute {
            bookData.value?.let {
                App.db.bookDao().delete(it)
                inBookshelf = false
                if (it.isLocalBook()) {
                    LocalBook.deleteBook(it, deleteOriginal)
                }
            }
        }.onSuccess {
            success?.invoke()
        }
    }
}