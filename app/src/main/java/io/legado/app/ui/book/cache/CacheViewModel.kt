package io.legado.app.ui.book.cache

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.AppConfig
import io.legado.app.help.BookHelp
import io.legado.app.help.ContentProcessor
import io.legado.app.help.storage.BookWebDav
import io.legado.app.utils.*
import java.io.File
import java.nio.charset.Charset


class CacheViewModel(application: Application) : BaseViewModel(application) {


    fun export(path: String, book: Book, finally: (msg: String) -> Unit) {
        execute {
            if (path.isContentScheme()) {
                val uri = Uri.parse(path)
                DocumentFile.fromTreeUri(context, uri)?.let {
                    export(it, book)
                }
            } else {
                export(FileUtils.createFolderIfNotExist(path), book)
            }
        }.onError {
            finally(it.localizedMessage ?: "ERROR")
        }.onSuccess {
            finally(context.getString(R.string.success))
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun export(doc: DocumentFile, book: Book) {
        val filename = "${book.name} by ${book.author}.txt"
        DocumentUtils.delete(doc, filename)
        DocumentUtils.createFileIfNotExist(doc, filename)?.let { bookDoc ->
            val stringBuilder = StringBuilder()
            context.contentResolver.openOutputStream(bookDoc.uri, "wa")?.use { bookOs ->
                getAllContents(book) {
                    bookOs.write(it.toByteArray(Charset.forName(AppConfig.exportCharset)))
                    stringBuilder.append(it)
                }
            }
            if (AppConfig.exportToWebDav) {
                // 导出到webdav
                val byteArray =
                    stringBuilder.toString().toByteArray(Charset.forName(AppConfig.exportCharset))
                BookWebDav.exportWebDav(byteArray, filename)
            }
        }
        getSrcList(book).forEach {
            val vFile = BookHelp.getImage(book, it.third)
            if (vFile.exists()) {
                DocumentUtils.createFileIfNotExist(
                    doc,
                    "${it.second}-${MD5Utils.md5Encode16(it.third)}.jpg",
                    subDirs = arrayOf("${book.name}_${book.author}", "images", it.first)
                )?.writeBytes(context, vFile.readBytes())
            }
        }
    }

    private suspend fun export(file: File, book: Book) {
        val filename = "${book.name} by ${book.author}.txt"
        val bookPath = FileUtils.getPath(file, filename)
        val bookFile = FileUtils.createFileWithReplace(bookPath)
        val stringBuilder = StringBuilder()
        getAllContents(book) {
            bookFile.appendText(it, Charset.forName(AppConfig.exportCharset))
            stringBuilder.append(it)
        }
        if (AppConfig.exportToWebDav) {
            val byteArray =
                stringBuilder.toString().toByteArray(Charset.forName(AppConfig.exportCharset))
            BookWebDav.exportWebDav(byteArray, filename) // 导出到webdav
        }
        getSrcList(book).forEach {
            val vFile = BookHelp.getImage(book, it.third)
            if (vFile.exists()) {
                FileUtils.createFileIfNotExist(
                    file,
                    "${book.name}_${book.author}",
                    "images",
                    it.first,
                    "${it.second}-${MD5Utils.md5Encode16(it.third)}.jpg"
                ).writeBytes(vFile.readBytes())
            }
        }
    }

    private suspend fun getAllContents(book: Book, append: (text: String) -> Unit) {
        val useReplace = AppConfig.exportUseReplace
        val contentProcessor = ContentProcessor(book.name, book.origin)
        append("${book.name}\n${context.getString(R.string.author_show, book.author)}")
        appDb.bookChapterDao.getChapterList(book.bookUrl).forEach { chapter ->
            BookHelp.getContent(book, chapter).let { content ->
                val content1 = contentProcessor
                    .getContent(book, chapter.title, content ?: "null", false, useReplace)
                    .joinToString("\n")
                append.invoke("\n\n$content1")
            }
        }
    }

    private fun getSrcList(book: Book): ArrayList<Triple<String, Int, String>> {
        val srcList = arrayListOf<Triple<String, Int, String>>()
        appDb.bookChapterDao.getChapterList(book.bookUrl).forEach { chapter ->
            BookHelp.getContent(book, chapter)?.let { content ->
                content.split("\n").forEachIndexed { index, text ->
                    val matcher = AppPattern.imgPattern.matcher(text)
                    if (matcher.find()) {
                        matcher.group(1)?.let {
                            val src = NetworkUtils.getAbsoluteURL(chapter.url, it)
                            srcList.add(Triple(chapter.title, index, src))
                        }
                    }
                }
            }
        }
        return srcList
    }
}