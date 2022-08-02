package io.legado.app.model.localBook

import android.graphics.Bitmap
import android.text.TextUtils
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.BookHelp
import io.legado.app.utils.*
import me.ag2s.epublib.domain.EpubBook
import me.ag2s.epublib.domain.Resource
import me.ag2s.epublib.domain.TOCReference
import me.ag2s.epublib.epub.EpubReader
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset

class EpubFile(var book: Book) {

    companion object : BaseLocalBookParse {
        private var eFile: EpubFile? = null

        @Synchronized
        private fun getEFile(book: Book): EpubFile {
            if (eFile == null || eFile?.book?.bookUrl != book.bookUrl) {
                eFile = EpubFile(book)
                //对于Epub文件默认不启用替换
                //io.legado.app.data.entities.Book getUseReplaceRule
                return eFile!!
            }
            eFile?.book = book
            return eFile!!
        }

        @Synchronized
        override fun getChapterList(book: Book): ArrayList<BookChapter> {
            return getEFile(book).getChapterList()
        }

        @Synchronized
        override fun getContent(book: Book, chapter: BookChapter): String? {
            return getEFile(book).getContent(chapter)
        }

        @Synchronized
        override fun getImage(
            book: Book,
            href: String
        ): InputStream? {
            return getEFile(book).getImage(href)
        }

        @Synchronized
        override fun upBookInfo(book: Book) {
            return getEFile(book).upBookInfo()
        }
    }

    private var mCharset: Charset = Charset.defaultCharset()
    private var epubBook: EpubBook? = null
        get() {
            if (field != null) {
                return field
            }
            field = readEpub()
            return field
        }

    init {
        try {
            epubBook?.let {
                if (book.coverUrl.isNullOrEmpty()) {
                    book.coverUrl = FileUtils.getPath(
                        appCtx.externalFiles,
                        "covers",
                        "${MD5Utils.md5Encode16(book.bookUrl)}.jpg"
                    )
                }
                if (!File(book.coverUrl!!).exists()) {
                    /*部分书籍DRM处理后，封面获取异常，待优化*/
                    it.coverImage?.inputStream?.use { input ->
                        BitmapUtils.decodeBitmap(input)?.let { cover ->
                            val out =
                                FileOutputStream(FileUtils.createFileIfNotExist(book.coverUrl!!))
                            cover.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            out.flush()
                            out.close()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printOnDebug()
        }
    }

    /**
     * 重写epub文件解析代码，直接读出压缩包文件生成Resources给epublib，这样的好处是可以逐一修改某些文件的格式错误
     */
    private fun readEpub(): EpubBook? {
        return kotlin.runCatching {
            //ContentScheme拷贝到私有文件夹采用懒加载防止OOM
            val zipFile = BookHelp.getEpubFile(book)
            EpubReader().readEpubLazy(zipFile, "utf-8")
        }.onFailure {
            it.printOnDebug()
        }.getOrNull()
    }

    private fun getContent(chapter: BookChapter): String? {
        /**
         * <image width="1038" height="670" xlink:href="..."/>
         * ...titlepage.xhtml
         */
        if (chapter.url.contains("titlepage.xhtml")) {
            return "<img src=\"cover.jpeg\" />"
        }
        /*获取当前章节文本*/
        epubBook?.let { epubBook ->
            val nextUrl = chapter.getVariable("nextUrl")
            val startFragmentId = chapter.startFragmentId
            val endFragmentId = chapter.endFragmentId
            val elements = Elements()
            var isChapter = false
            /*一些书籍依靠href索引的resource会包含多个章节，需要依靠fragmentId来截取到当前章节的内容*/
            /*注:这里较大增加了内容加载的时间，所以首次获取内容后可存储到本地cache，减少重复加载*/
            for (res in epubBook.contents) {
                if (chapter.url.substringBeforeLast("#") == res.href) {
                    elements.add(getBody(res, startFragmentId, endFragmentId))
                    isChapter = true
                    /**
                     * fix https://github.com/gedoor/legado/issues/1927 加载全部内容的bug
                     * content src text/000001.html（当前章节）
                    -                   * content src text/000001.html#toc_id_x (下一章节）
                     */
                    if (res.href == nextUrl?.substringBeforeLast("#")) break
                } else if (isChapter) {
                    // fix 最后一章存在多个html时 内容缺失
                    if (res.href == nextUrl?.substringBeforeLast("#")) {
                        break
                    }
                    elements.add(getBody(res, startFragmentId, endFragmentId))
                }
            }
            //title标签中的内容不需要显示在正文中，去除
            elements.select("title").remove()
            var html = elements.outerHtml()
            val tag = Book.rubyTag
            if (book.getDelTag(tag)) {
                html = html.replace("<ruby>\\s?([\\u4e00-\\u9fa5])\\s?.*?</ruby>".toRegex(), "$1")
            }
            return HtmlFormatter.formatKeepImg(html)
        }
        return null
    }

    private fun getBody(res: Resource, startFragmentId: String?, endFragmentId: String?): Element {
        val body = Jsoup.parse(String(res.data, mCharset)).body()
        if (!startFragmentId.isNullOrBlank()) {
            body.getElementById(startFragmentId)?.previousElementSiblings()?.remove()
        }
        if (!endFragmentId.isNullOrBlank() && endFragmentId != startFragmentId) {
            body.getElementById(endFragmentId)?.run {
                nextElementSiblings().remove()
                remove()
            }
        }
        /*选择去除正文中的H标签，部分书籍标题与阅读标题重复待优化*/
        val tag = Book.hTag
        if (book.getDelTag(tag)) {
            body.getElementsByTag("h1").remove()
            body.getElementsByTag("h2").remove()
            body.getElementsByTag("h3").remove()
            body.getElementsByTag("h4").remove()
            body.getElementsByTag("h5").remove()
            body.getElementsByTag("h6").remove()
            //body.getElementsMatchingOwnText(chapter.title)?.remove()
        }

        val children = body.children()
        children.select("script").remove()
        children.select("style").remove()
        return body
    }

    private fun getImage(href: String): InputStream? {
        if (href == "cover.jpeg") return epubBook?.coverImage?.inputStream
        val abHref = href.replace("../", "")
        return epubBook?.resources?.getByHref(abHref)?.inputStream
    }

    private fun upBookInfo() {
        if (epubBook == null) {
            eFile = null
            book.intro = "书籍导入异常"
        } else {
            val metadata = epubBook!!.metadata
            book.name = metadata.firstTitle
            if (book.name.isEmpty()) {
                book.name = book.originName.replace(".epub", "")
            }

            if (metadata.authors.size > 0) {
                val author =
                    metadata.authors[0].toString().replace("^, |, $".toRegex(), "")
                book.author = author
            }
            if (metadata.descriptions.size > 0) {
                book.intro = Jsoup.parse(metadata.descriptions[0]).text()
            }
        }
    }

    private fun getChapterList(): ArrayList<BookChapter> {
        val chapterList = ArrayList<BookChapter>()
        epubBook?.let { eBook ->
            val refs = eBook.tableOfContents.tocReferences
            if (refs == null || refs.isEmpty()) {
                val spineReferences = eBook.spine.spineReferences
                var i = 0
                val size = spineReferences.size
                while (i < size) {
                    val resource = spineReferences[i].resource
                    var title = resource.title
                    if (TextUtils.isEmpty(title)) {
                        try {
                            val doc =
                                Jsoup.parse(String(resource.data, mCharset))
                            val elements = doc.getElementsByTag("title")
                            if (elements.size > 0) {
                                title = elements[0].text()
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                    val chapter = BookChapter()
                    chapter.index = i
                    chapter.bookUrl = book.bookUrl
                    chapter.url = resource.href
                    if (i == 0 && title.isEmpty()) {
                        chapter.title = "封面"
                    } else {
                        chapter.title = title
                    }
                    chapterList.add(chapter)
                    i++
                }
            } else {
                parseFirstPage(chapterList, refs)
                parseMenu(chapterList, refs, 0)
                for (i in chapterList.indices) {
                    chapterList[i].index = i
                }
            }
        }
        book.latestChapterTitle = chapterList.lastOrNull()?.title
        book.totalChapterNum = chapterList.size
        return chapterList
    }

    /*获取书籍起始页内容。部分书籍第一章之前存在封面，引言，扉页等内容*/
    /*tile获取不同书籍风格杂乱，格式化处理待优化*/
    private var durIndex = 0
    private fun parseFirstPage(
        chapterList: ArrayList<BookChapter>,
        refs: List<TOCReference>?
    ) {
        val contents = epubBook?.contents
        if (epubBook == null || contents == null || refs == null) return
        var i = 0
        durIndex = 0
        while (i < contents.size) {
            val content = contents[i]
            if (!content.mediaType.toString().contains("htm")) continue
            /**
             * 检索到第一章href停止
             * completeHref可能有fragment(#id) 必须去除
             * fix https://github.com/gedoor/legado/issues/1932
             */
            if (refs[0].completeHref.substringBeforeLast("#") == content.href) break
            val chapter = BookChapter()
            var title = content.title
            if (TextUtils.isEmpty(title)) {
                val elements = Jsoup.parse(
                    String(epubBook!!.resources.getByHref(content.href).data, mCharset)
                ).getElementsByTag("title")
                title =
                    if (elements.size > 0 && elements[0].text().isNotBlank())
                        elements[0].text()
                    else
                        "--卷首--"
            }
            chapter.bookUrl = book.bookUrl
            chapter.title = title
            chapter.url = content.href
            chapter.startFragmentId =
                if (content.href.substringAfter("#") == content.href) null
                else content.href.substringAfter("#")

            chapterList.lastOrNull()?.endFragmentId = chapter.startFragmentId
            chapterList.lastOrNull()?.putVariable("nextUrl", chapter.url)
            chapterList.add(chapter)
            durIndex++
            i++
        }
    }

    private fun parseMenu(
        chapterList: ArrayList<BookChapter>,
        refs: List<TOCReference>?,
        level: Int
    ) {
        refs?.forEach { ref ->
            if (ref.resource != null) {
                val chapter = BookChapter()
                chapter.bookUrl = book.bookUrl
                chapter.title = ref.title
                chapter.url = ref.completeHref
                chapter.startFragmentId = ref.fragmentId
                chapterList.lastOrNull()?.endFragmentId = chapter.startFragmentId
                chapterList.lastOrNull()?.putVariable("nextUrl", chapter.url)
                chapterList.add(chapter)
                durIndex++
            }
            if (ref.children != null && ref.children.isNotEmpty()) {
                chapterList.lastOrNull()?.isVolume = true
                parseMenu(chapterList, ref.children, level + 1)
            }
        }
    }

}