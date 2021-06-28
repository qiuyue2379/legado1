package io.legado.app.ui.book.cache

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.AppConfig
import io.legado.app.help.BookHelp
import io.legado.app.help.ContentProcessor
import io.legado.app.help.storage.BookWebDav
import io.legado.app.utils.*
import me.ag2s.epublib.domain.*
import me.ag2s.epublib.epub.EpubWriter
import me.ag2s.epublib.util.ResourceUtil
import splitties.init.appCtx
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import javax.script.SimpleBindings


class CacheViewModel(application: Application) : BaseViewModel(application) {

    fun getExportFileName(book: Book): String {
        val jsStr = AppConfig.bookExportFileName
        if (jsStr.isNullOrBlank()) {
            return "${book.name} 作者：${book.getRealAuthor()}"
        }
        val bindings = SimpleBindings()
        bindings["name"] = book.name
        bindings["author"] = book.getRealAuthor()
        return AppConst.SCRIPT_ENGINE.eval(jsStr, bindings).toString()
    }

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
        val filename = "${getExportFileName(book)}.txt"
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
        val filename = "${getExportFileName(book)}.txt"
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

    private fun getAllContents(book: Book, append: (text: String) -> Unit) {
        val useReplace = AppConfig.exportUseReplace
        val contentProcessor = ContentProcessor(book.name, book.origin)
        append(
            "${book.name}\n${
                context.getString(
                    R.string.author_show,
                    book.getRealAuthor()
                )
            }\n${
                context.getString(
                    R.string.intro_show,
                    "\n" + HtmlFormatter.format(book.getDisplayIntro())
                )
            }"
        )
        appDb.bookChapterDao.getChapterList(book.bookUrl).forEach { chapter ->
            BookHelp.getContent(book, chapter).let { content ->
                val content1 = contentProcessor
                    .getContent(
                        book,
                        chapter.title.replace("\\r?\\n".toRegex(), " "),
                        content ?: "null",
                        false,
                        useReplace
                    )
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
                    while (matcher.find()) {
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
    //////////////////Start EPUB
    /**
     * 导出Epub
     */
    fun exportEPUB(path: String, book: Book, finally: (msg: String) -> Unit) {
        execute {
            if (path.isContentScheme()) {
                val uri = Uri.parse(path)
                DocumentFile.fromTreeUri(context, uri)?.let {
                    exportEpub(it, book)
                }
            } else {
                exportEpub(FileUtils.createFolderIfNotExist(path), book)
            }
        }.onError {
            finally(it.localizedMessage ?: "ERROR")
        }.onSuccess {
            finally(context.getString(R.string.success))
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun exportEpub(doc: DocumentFile, book: Book) {
        val filename = "${getExportFileName(book)}.epub"
        DocumentUtils.delete(doc, filename)
        val epubBook = EpubBook()
        epubBook.version = "2.0"
        //set metadata
        setEpubMetadata(book, epubBook)
        //set cover
        setCover(book, epubBook)
        //set css
        val contentModel = setAssets(doc, book, epubBook)

        //设置正文
        setEpubContent(contentModel, book, epubBook)
        DocumentUtils.createFileIfNotExist(doc, filename)?.let { bookDoc ->
            context.contentResolver.openOutputStream(bookDoc.uri, "wa")?.use { bookOs ->
                EpubWriter().write(epubBook, bookOs)
            }

        }
    }


    private fun exportEpub(file: File, book: Book) {
        val filename = "${getExportFileName(book)}.epub"
        val epubBook = EpubBook()
        epubBook.version = "2.0"
        //set metadata
        setEpubMetadata(book, epubBook)
        //set cover
        setCover(book, epubBook)
        //set css
        val contentModel = setAssets(book, epubBook)

        val bookPath = FileUtils.getPath(file, filename)
        val bookFile = FileUtils.createFileWithReplace(bookPath)
        //设置正文
        setEpubContent(contentModel, book, epubBook)
        EpubWriter().write(epubBook, FileOutputStream(bookFile))
    }

    private fun setAssets(doc: DocumentFile, book: Book, epubBook: EpubBook): String {
        if(!(AppConfig.isGooglePlay|| appCtx.packageName.contains("debug",true))) return setAssets(book, epubBook)

        var contentModel = ""
        DocumentUtils.getDirDocument(doc, "Asset").let { customPath ->
            if (customPath == null) {//使用内置模板
                contentModel = setAssets(book, epubBook)
            } else {//外部模板
                customPath.listFiles().forEach { folder ->
                    if (folder.isDirectory && folder.name == "Text") {
                        folder.listFiles().sortedWith { o1, o2 ->
                            val name1 = o1.name ?: ""
                            val name2 = o2.name ?: ""
                            name1.cnCompare(name2)
                        }.forEach { file ->
                            if (file.isFile) {
                                when {
                                    //正文模板
                                    file.name.equals(
                                        "chapter.html",
                                        true
                                    ) || file.name.equals("chapter.xhtml", true) -> {
                                        contentModel = file.readText(context) ?: ""
                                    }
                                    //封面等其他模板
                                    true == file.name?.endsWith("html", true) -> {
                                        epubBook.addSection(
                                            FileUtils.getNameExcludeExtension(
                                                file.name ?: "Cover.html"
                                            ),
                                            ResourceUtil.createPublicResource(
                                                book.name,
                                                book.getRealAuthor(),
                                                book.getDisplayIntro(),
                                                book.kind,
                                                book.wordCount,
                                                file.readText(context) ?: "",
                                                "${folder.name}/${file.name}"
                                            )
                                        )
                                    }
                                    else -> {
                                        //其他格式文件当做资源文件
                                        folder.listFiles().forEach {
                                            if (it.isFile)
                                                epubBook.resources.add(
                                                    Resource(
                                                        it.readBytes(context),
                                                        "${folder.name}/${it.name}"
                                                    )
                                                )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (folder.isDirectory) {
                        //资源文件
                        folder.listFiles().forEach {
                            if (it.isFile)
                                epubBook.resources.add(
                                    Resource(
                                        it.readBytes(context),
                                        "${folder.name}/${it.name}"
                                    )
                                )
                        }
                    } else {//Asset下面的资源文件
                        epubBook.resources.add(
                            Resource(
                                folder.readBytes(context),
                                "${folder.name}"
                            )
                        )
                    }
                }
            }
        }

        return contentModel
    }

    private fun setAssets(book: Book, epubBook: EpubBook): String {
        epubBook.resources.add(
            Resource(
                appCtx.assets.open("epub/fonts.css").readBytes(),
                "Styles/fonts.css"
            )
        )
        epubBook.resources.add(
            Resource(
                appCtx.assets.open("epub/main.css").readBytes(),
                "Styles/main.css"
            )
        )
        epubBook.resources.add(
            Resource(
                appCtx.assets.open("epub/logo.png").readBytes(),
                "Images/logo.png"
            )
        )
        epubBook.addSection(
            context.getString(R.string.img_cover),
            ResourceUtil.createPublicResource(
                book.name,
                book.getRealAuthor(),
                book.getDisplayIntro(),
                book.kind,
                book.wordCount,
                String(appCtx.assets.open("epub/cover.html").readBytes()),
                "Text/cover.html"
            )
        )
        epubBook.addSection(
            context.getString(R.string.book_intro),
            ResourceUtil.createPublicResource(
                book.name,
                book.getRealAuthor(),
                book.getDisplayIntro(),
                book.kind,
                book.wordCount,
                String(appCtx.assets.open("epub/intro.html").readBytes()),
                "Text/intro.html"
            )
        )
        return String(appCtx.assets.open("epub/chapter.html").readBytes())
    }

    private fun setCover(book: Book, epubBook: EpubBook) {
        Glide.with(context)
            .asBitmap()
            .load(book.getDisplayCover())
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val stream = ByteArrayOutputStream()
                    resource.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    val byteArray: ByteArray = stream.toByteArray()
                    resource.recycle()
                    stream.close()
                    epubBook.coverImage = Resource(byteArray, "Images/cover.jpg")
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                }
            })
    }

    private fun setEpubContent(contentModel: String, book: Book, epubBook: EpubBook) {
        //正文
        val useReplace = AppConfig.exportUseReplace
        val contentProcessor = ContentProcessor(book.name, book.origin)
        appDb.bookChapterDao.getChapterList(book.bookUrl).forEachIndexed { index, chapter ->
            BookHelp.getContent(book, chapter).let { content ->
                var content1 = fixPic(epubBook, book, content ?: "null", chapter)
                content1 = contentProcessor
                    .getContent(book, "", content1, false, useReplace)
                    .joinToString("\n")
                epubBook.addSection(
                    chapter.title,
                    ResourceUtil.createChapterResource(
                        chapter.title.replace("\uD83D\uDD12", ""),
                        content1,
                        contentModel,
                        "Text/chapter_${index}.html"
                    )
                )
            }
        }
    }

    private fun fixPic(
        epubBook: EpubBook,
        book: Book,
        content: String,
        chapter: BookChapter
    ): String {
        val data = StringBuilder("")
        content.split("\n").forEach { text ->
            var text1 = text
            val matcher = AppPattern.imgPattern.matcher(text)
            while (matcher.find()) {
                matcher.group(1)?.let {
                    val src = NetworkUtils.getAbsoluteURL(chapter.url, it)
                    val originalHref = "${MD5Utils.md5Encode16(src)}${BookHelp.getImageSuffix(src)}"
                    val href = "Images/${MD5Utils.md5Encode16(src)}.${BookHelp.getImageSuffix(src)}"
                    val vFile = BookHelp.getImage(book, src)
                    val fp = FileResourceProvider(vFile.parent)
                    if (vFile.exists()) {
                        val img = LazyResource(fp, href, originalHref)
                        epubBook.resources.add(img)
                    }
                    text1 = text1.replace(src, "../${href}")
                }
            }
            data.append(text1).append("\n")
        }
        return data.toString()
    }

    private fun setEpubMetadata(book: Book, epubBook: EpubBook) {
        val metadata = Metadata()
        metadata.titles.add(book.name)//书籍的名称
        metadata.authors.add(Author(book.getRealAuthor()))//书籍的作者
        metadata.language = "zh"//数据的语言
        metadata.dates.add(Date())//数据的创建日期
        metadata.publishers.add("Legado")//数据的创建者
        metadata.descriptions.add(book.getDisplayIntro())//书籍的简介
        //metadata.subjects.add("")//书籍的主题，在静读天下里面有使用这个分类书籍
        epubBook.metadata = metadata
    }

    //////end of EPUB
}