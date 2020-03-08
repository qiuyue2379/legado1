package io.legado.app.web.controller

import io.legado.app.App
import io.legado.app.data.entities.Book
import io.legado.app.help.BookHelp
import io.legado.app.model.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.web.utils.ReturnData
import kotlinx.coroutines.runBlocking

class BookshelfController {

    val bookshelf: ReturnData
        get() {
            val books = App.db.bookDao().all
            val returnData = ReturnData()
            return if (books.isEmpty()) {
                returnData.setErrorMsg("还没有添加小说")
            } else returnData.setData(books)
        }

    fun getChapterList(parameters: Map<String, List<String>>): ReturnData {
        val bookUrl = parameters["url"]?.getOrNull(0)
        val returnData = ReturnData()
        if (bookUrl.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数url不能为空，请指定书籍地址")
        }
        val chapterList = App.db.bookChapterDao().getChapterList(bookUrl)
        return returnData.setData(chapterList)
    }

    fun getBookContent(parameters: Map<String, List<String>>): ReturnData {
        val bookUrl = parameters["url"]?.getOrNull(0)
        val index = parameters["index"]?.getOrNull(0)?.toInt()
        val returnData = ReturnData()
        if (bookUrl.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数url不能为空，请指定书籍地址")
        }
        if (index == null) {
            return returnData.setErrorMsg("参数index不能为空, 请指定目录序号")
        }
        val book = App.db.bookDao().getBook(bookUrl)
        val chapter = App.db.bookChapterDao().getChapter(bookUrl, index)
        if (book == null || chapter == null) {
            returnData.setErrorMsg("未找到")
        } else {
            val content: String? = BookHelp.getContent(book, chapter)
            if (content != null) {
                returnData.setData(content)
            } else {
                App.db.bookSourceDao().getBookSource(book.origin)?.let { source ->
                    runBlocking {
                        WebBook(source).getContentSuspend(book, chapter)
                    }.let {
                        returnData.setData(it)
                    }
                } ?: returnData.setErrorMsg("未找到书源")
            }
        }
        return returnData
    }

    fun saveBook(postData: String?): ReturnData {
        val book = GSON.fromJsonObject<Book>(postData)
        val returnData = ReturnData()
        if (book != null) {
            App.db.bookDao().insert(book)
            return returnData.setData("")
        }
        return returnData.setErrorMsg("格式不对")
    }

}
