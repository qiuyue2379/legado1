package io.legado.app.ui.book.source.manage

import android.app.Application
import android.net.Uri
import android.text.TextUtils
import androidx.documentfile.provider.DocumentFile
import com.jayway.jsonpath.JsonPath
import io.legado.app.App
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.BookSource
import io.legado.app.help.http.HttpHelper
import io.legado.app.help.storage.OldRule
import io.legado.app.help.storage.Restore.jsonPath
import io.legado.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.anko.toast
import java.io.File
import java.net.URLEncoder

class BookSourceViewModel(application: Application) : BaseViewModel(application) {

    fun topSource(bookSource: BookSource) {
        execute {
            bookSource.customOrder = App.db.bookSourceDao().minOrder - 1
            App.db.bookSourceDao().insert(bookSource)
        }
    }

    fun del(bookSource: BookSource) {
        execute { App.db.bookSourceDao().delete(bookSource) }
    }

    fun update(vararg bookSource: BookSource) {
        execute { App.db.bookSourceDao().update(*bookSource) }
    }

    fun upOrder() {
        execute {
            val sources = App.db.bookSourceDao().all
            for ((index: Int, source: BookSource) in sources.withIndex()) {
                source.customOrder = index + 1
            }
            App.db.bookSourceDao().update(*sources.toTypedArray())
        }
    }

    fun enableSelection(sources: LinkedHashSet<BookSource>) {
        execute {
            val list = arrayListOf<BookSource>()
            sources.forEach {
                list.add(it.copy(enabled = true))
            }
            App.db.bookSourceDao().update(*list.toTypedArray())
        }
    }

    fun disableSelection(sources: LinkedHashSet<BookSource>) {
        execute {
            val list = arrayListOf<BookSource>()
            sources.forEach {
                list.add(it.copy(enabled = false))
            }
            App.db.bookSourceDao().update(*list.toTypedArray())
        }
    }

    fun enableSelectExplore(sources: LinkedHashSet<BookSource>) {
        execute {
            val list = arrayListOf<BookSource>()
            sources.forEach {
                list.add(it.copy(enabledExplore = true))
            }
            App.db.bookSourceDao().update(*list.toTypedArray())
        }
    }

    fun disableSelectExplore(sources: LinkedHashSet<BookSource>) {
        execute {
            val list = arrayListOf<BookSource>()
            sources.forEach {
                list.add(it.copy(enabledExplore = false))
            }
            App.db.bookSourceDao().update(*list.toTypedArray())
        }
    }

    fun delSelection(sources: LinkedHashSet<BookSource>) {
        execute {
            App.db.bookSourceDao().delete(*sources.toTypedArray())
        }
    }

    fun exportSelection(sources: LinkedHashSet<BookSource>, file: File) {
        execute {
            val json = GSON.toJson(sources)
            FileUtils.createFileIfNotExist(file, "exportBookSource.json")
                .writeText(json)
        }.onSuccess {
            context.toast("成功导出至\n${file.absolutePath}")
        }.onError {
            context.toast("导出失败\n${it.localizedMessage}")
        }
    }

    fun exportSelection(sources: LinkedHashSet<BookSource>, doc: DocumentFile) {
        execute {
            val json = GSON.toJson(sources)
            doc.findFile("exportBookSource.json")?.delete()
            doc.createFile("", "exportBookSource.json")
                ?.writeText(context, json)
        }.onSuccess {
            context.toast("成功导出至\n${doc.uri.path}")
        }.onError {
            context.toast("导出失败\n${it.localizedMessage}")
        }
    }

    fun addGroup(group: String) {
        execute {
            val sources = App.db.bookSourceDao().noGroup
            sources.map { source ->
                source.bookSourceGroup = group
            }
            App.db.bookSourceDao().update(*sources.toTypedArray())
        }
    }

    fun upGroup(oldGroup: String, newGroup: String?) {
        execute {
            val sources = App.db.bookSourceDao().getByGroup(oldGroup)
            sources.map { source ->
                source.bookSourceGroup?.splitNotBlank(",")?.toHashSet()?.let {
                    it.remove(oldGroup)
                    if (!newGroup.isNullOrEmpty())
                        it.add(newGroup)
                    source.bookSourceGroup = TextUtils.join(",", it)
                }
            }
            App.db.bookSourceDao().update(*sources.toTypedArray())
        }
    }

    fun delGroup(group: String) {
        execute {
            execute {
                val sources = App.db.bookSourceDao().getByGroup(group)
                sources.map { source ->
                    source.bookSourceGroup?.splitNotBlank(",")?.toHashSet()?.let {
                        it.remove(group)
                        source.bookSourceGroup = TextUtils.join(",", it)
                    }
                }
                App.db.bookSourceDao().update(*sources.toTypedArray())
            }
        }
    }

    fun importSourceFromFilePath(path: String, finally: (msg: String) -> Unit) {
        execute {
            val content = if (path.isContentPath()) {
                //在前面被解码了，如果不进行编码，中文会无法识别
                val newPath = Uri.encode(path, ":/.")
                DocumentFile.fromSingleUri(context, Uri.parse(newPath))?.readText(context)
            } else {
                val file = File(path)
                if (file.exists()) {
                    file.readText()
                } else {
                    null
                }
            }
            if (content != null) {
                importSource(content, finally)
            } else {
                withContext(Dispatchers.Main) {
                    finally("打开文件出错")
                }
            }
        }.onError {
            finally(it.localizedMessage ?: "打开文件出错")
        }
    }

    fun importSource(text: String, finally: (msg: String) -> Unit) {
        execute {
            val text1 = text.trim()
            when {
                text1.isJsonObject() -> {
                    val json = JsonPath.parse(text1)
                    val urls = json.read<List<String>>("$.sourceUrls")
                    var count = 0
                    if (!urls.isNullOrEmpty()) {
                        urls.forEach {
                            count += importSourceUrl(it)
                        }
                    } else {
                        OldRule.jsonToBookSource(text1)?.let {
                            App.db.bookSourceDao().insert(it)
                            count = 1
                        }
                    }
                    "导入${count}条"
                }
                text1.isJsonArray() -> {
                    val bookSources = mutableListOf<BookSource>()
                    val items: List<Map<String, Any>> = jsonPath.parse(text1).read("$")
                    for (item in items) {
                        val jsonItem = jsonPath.parse(item)
                        OldRule.jsonToBookSource(jsonItem.jsonString())?.let {
                            bookSources.add(it)
                        }
                    }
                    App.db.bookSourceDao().insert(*bookSources.toTypedArray())
                    "导入${bookSources.size}条"
                }
                text1.isAbsUrl() -> {
                    val count = importSourceUrl(text1)
                    "导入${count}条"
                }
                else -> "格式不对"
            }
        }.onError {
            finally(it.localizedMessage ?: "")
        }.onSuccess {
            finally(it)
        }
    }

    private fun importSourceUrl(url: String): Int {
        HttpHelper.simpleGet(url)?.let { body ->
            val bookSources = mutableListOf<BookSource>()
            val items: List<Map<String, Any>> = jsonPath.parse(body).read("$")
            for (item in items) {
                val jsonItem = jsonPath.parse(item)
                OldRule.jsonToBookSource(jsonItem.jsonString())?.let { source ->
                    bookSources.add(source)
                }
            }
            App.db.bookSourceDao().insert(*bookSources.toTypedArray())
            return bookSources.size
        }
        return 0
    }
}