package io.legado.app.model.localBook

import android.net.Uri
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.script.SimpleBindings
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.exception.NoStackTraceException
import io.legado.app.exception.TocEmptyException
import io.legado.app.help.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.*
import splitties.init.appCtx
import java.io.*
import java.util.regex.Pattern

/**
 * 书籍文件导入 目录正文解析
 * 支持在线文件(txt epub umd 压缩文件需要用户解压) 本地文件
 */
object LocalBook {

    private val nameAuthorPatterns = arrayOf(
        Pattern.compile("(.*?)《([^《》]+)》.*?作者：(.*)"),
        Pattern.compile("(.*?)《([^《》]+)》(.*)"),
        Pattern.compile("(^)(.+) 作者：(.+)$"),
        Pattern.compile("(^)(.+) by (.+)$")
    )

    @Throws(FileNotFoundException::class, SecurityException::class)
    fun getBookInputStream(book: Book): InputStream {
        val uri = Uri.parse(book.bookUrl)
        if (uri.isContentScheme()) {
            return appCtx.contentResolver.openInputStream(uri)!!
        }
        val file = File(uri.path!!)
        if (file.exists()) {
            return FileInputStream(File(uri.path!!))
        }
        throw FileNotFoundException("${uri.path} 文件不存在")
    }

    fun getLastModified(book: Book): Result<Long> {
        return kotlin.runCatching {
            val uri = Uri.parse(book.bookUrl)
            if (uri.isContentScheme()) {
                return@runCatching DocumentFile.fromSingleUri(appCtx, uri)!!.lastModified()
            }
            val file = File(uri.path!!)
            if (file.exists()) {
                return@runCatching File(uri.path!!).lastModified()
            }
            throw FileNotFoundException("${uri.path} 文件不存在")
        }
    }

    @Throws(Exception::class)
    fun getChapterList(book: Book): ArrayList<BookChapter> {
        val chapters = when {
            book.isEpub() -> {
                EpubFile.getChapterList(book)
            }
            book.isUmd() -> {
                UmdFile.getChapterList(book)
            }
            else -> {
                TextFile.getChapterList(book)
            }
        }
        if (chapters.isEmpty()) {
            throw TocEmptyException(appCtx.getString(R.string.chapter_list_empty))
        }
        return chapters
    }

    fun getContent(book: Book, chapter: BookChapter): String? {
        return try {
            when {
                book.isEpub() -> {
                    EpubFile.getContent(book, chapter)
                }
                book.isUmd() -> {
                    UmdFile.getContent(book, chapter)
                }
                else -> {
                    TextFile.getContent(book, chapter)
                }
            }
        } catch (e: Exception) {
            e.printOnDebug()
            e.localizedMessage
        }
    }

    /**
     * 下载在线的文件并自动导入到阅读（txt umd epub)
     * 压缩文件请先提示用户解压
     */
    fun importFileOnLine(
        str: String,
        fileName: String,
        source: BaseSource? = null,
    ): Book {
        return saveBookFile(str, fileName, source).let {
            importFile(it)
        }
    }

    /**
     * 导入本地文件
     */
    fun importFile(uri: Uri): Book {
        val bookUrl: String
        val updateTime: Long
        //这个变量不要修改,否则会导致读取不到缓存
        val fileName = (if (uri.isContentScheme()) {
            bookUrl = uri.toString()
            val doc = DocumentFile.fromSingleUri(appCtx, uri)!!
            updateTime = doc.lastModified()
            doc.name!!
        } else {
            bookUrl = uri.path!!
            val file = File(bookUrl)
            updateTime = file.lastModified()
            file.name
        })
        var book = appDb.bookDao.getBook(bookUrl)
        if (book == null) {
            val nameAuthor = analyzeNameAuthor(fileName)
            book = Book(
                bookUrl = bookUrl,
                name = nameAuthor.first,
                author = nameAuthor.second,
                originName = fileName,
                coverUrl = FileUtils.getPath(
                    appCtx.externalFiles,
                    "covers",
                    "${MD5Utils.md5Encode16(bookUrl)}.jpg"
                ),
                latestChapterTime = updateTime
            )
            if (book.isEpub()) EpubFile.upBookInfo(book)
            if (book.isUmd()) UmdFile.upBookInfo(book)
            appDb.bookDao.insert(book)
        } else {
            //已有书籍说明是更新,删除原有目录
            appDb.bookChapterDao.delByBook(bookUrl)
        }
        return book
    }

    /**
     * 从文件分析书籍必要信息（书名 作者等）
     */
    private fun analyzeNameAuthor(fileName: String): Pair<String, String> {
        val tempFileName = fileName.substringBeforeLast(".")
        var name: String
        var author: String
        for (pattern in nameAuthorPatterns) {
            pattern.matcher(tempFileName).takeIf { it.find() }?.run {
                name = group(2)!!
                val group1 = group(1) ?: ""
                val group3 = group(3) ?: ""
                author = BookHelp.formatBookAuthor(group1 + group3)
                return Pair(name, author)
            }
        }
        if (!AppConfig.bookImportFileName.isNullOrBlank()) {
            try {
                //在脚本中定义如何分解文件名成书名、作者名
                val jsonStr = AppConst.SCRIPT_ENGINE.eval(
                    //在用户脚本后添加捕获author、name的代码，只要脚本中author、name有值就会被捕获
                    AppConfig.bookImportFileName + "\nJSON.stringify({author:author,name:name})",
                    //将文件名注入到脚步的src变量中
                    SimpleBindings().also { it["src"] = tempFileName }
                ).toString()
                val bookMess = GSON.fromJsonObject<HashMap<String, String>>(jsonStr)
                    .getOrThrow() ?: HashMap()
                name = bookMess["name"] ?: tempFileName
                author = bookMess["author"]?.takeIf { it.length != tempFileName.length } ?: ""
            } catch (e: Exception) {
                name = BookHelp.formatBookName(tempFileName)
                author = BookHelp.formatBookAuthor(tempFileName.replace(name, ""))
                    .takeIf { it.length != tempFileName.length } ?: ""
            }
        } else {
            name = BookHelp.formatBookName(tempFileName)
            author = BookHelp.formatBookAuthor(tempFileName.replace(name, ""))
                .takeIf { it.length != tempFileName.length } ?: ""
        }
        return Pair(name, author)
    }

    fun deleteBook(book: Book, deleteOriginal: Boolean) {
        kotlin.runCatching {
            BookHelp.clearCache(book)
            if (deleteOriginal) {
                if (book.bookUrl.isContentScheme()) {
                    val uri = Uri.parse(book.bookUrl)
                    DocumentFile.fromSingleUri(appCtx, uri)?.delete()
                } else {
                    FileUtils.delete(book.bookUrl)
                }
            }
        }
    }

    /**
     * 下载在线的文件
     */
    fun saveBookFile(
        str: String,
        fileName: String,
        source: BaseSource? = null,
    ): Uri {
        val bytes = when {
            str.isAbsUrl() -> AnalyzeUrl(str, source = source).getByteArray()
            str.isDataUrl() -> Base64.decode(str.substringAfter("base64,"), Base64.DEFAULT)
            else -> throw NoStackTraceException("在线导入书籍支持http/https/DataURL")
        }
        return saveBookFile(bytes, fileName)
    }

    /**
     * 分析下载文件类书源的下载链接的文件后缀
     * https://www.example.com/download/{fileName}.{type} 含有文件名和后缀
     * https://www.example.com/download/?fileid=1234, {type: "txt"} 规则设置
     */
    fun parseFileSuffix(url: String): String {
        val analyzeUrl = AnalyzeUrl(url)
        val urlNoOption = analyzeUrl.url
        val lastPath = urlNoOption.substringAfterLast("/")
        val fileType = lastPath.substringAfterLast(".")
        val type = analyzeUrl.type
        return type ?: fileType
    }

    private fun saveBookFile(
        bytes: ByteArray,
        fileName: String
    ): Uri {
        val defaultBookTreeUri = AppConfig.defaultBookTreeUri
        if (defaultBookTreeUri.isNullOrBlank()) throw NoStackTraceException("没有设置书籍保存位置!")
        val treeUri = Uri.parse(defaultBookTreeUri)
        return if (treeUri.isContentScheme()) {
            val treeDoc = DocumentFile.fromTreeUri(appCtx, treeUri)
            var doc = treeDoc!!.findFile(fileName)
            if (doc == null) {
                doc = treeDoc.createFile(FileUtils.getMimeType(fileName), fileName)
                    ?: throw SecurityException("Permission Denial")
            }
            appCtx.contentResolver.openOutputStream(doc.uri)!!.use { oStream ->
                oStream.write(bytes)
            }
            doc.uri
        } else {
            val treeFile = File(treeUri.path!!)
            val file = treeFile.getFile(fileName)
            FileOutputStream(file).use { oStream ->
                oStream.write(bytes)
            }
            Uri.fromFile(file)
        }
    }

    //文件类书源 合并在线书籍信息 在线 > 本地
    fun mergeBook(localBook: Book, onLineBook: Book?): Book {
        onLineBook ?: return localBook
        localBook.name = onLineBook.name.ifBlank { localBook.name }
        localBook.author = onLineBook.author.ifBlank { localBook.author }
        localBook.coverUrl = onLineBook.coverUrl
        localBook.intro =
            if (onLineBook.intro.isNullOrBlank()) localBook.intro else onLineBook.intro
        localBook.save()
        return localBook
    }

}
