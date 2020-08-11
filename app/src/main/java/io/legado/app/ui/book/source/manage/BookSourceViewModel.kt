package io.legado.app.ui.book.source.manage

import android.app.Application
import android.text.TextUtils
import androidx.documentfile.provider.DocumentFile
import io.legado.app.App
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.writeText
import org.jetbrains.anko.toast
import java.io.File

class BookSourceViewModel(application: Application) : BaseViewModel(application) {

    fun topSource(vararg sources: BookSource) {
        execute {
            val minOrder = App.db.bookSourceDao().minOrder - 1
            sources.forEachIndexed { index, bookSource ->
                bookSource.customOrder = minOrder - index
            }
            App.db.bookSourceDao().update(*sources)
        }
    }

    fun bottomSource(vararg sources: BookSource) {
        execute {
            val maxOrder = App.db.bookSourceDao().maxOrder + 1
            sources.forEachIndexed { index, bookSource ->
                bookSource.customOrder = maxOrder + index
            }
            App.db.bookSourceDao().update(*sources)
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

    fun enableSelection(sources: List<BookSource>) {
        execute {
            val list = arrayListOf<BookSource>()
            sources.forEach {
                list.add(it.copy(enabled = true))
            }
            App.db.bookSourceDao().update(*list.toTypedArray())
        }
    }

    fun disableSelection(sources: List<BookSource>) {
        execute {
            val list = arrayListOf<BookSource>()
            sources.forEach {
                list.add(it.copy(enabled = false))
            }
            App.db.bookSourceDao().update(*list.toTypedArray())
        }
    }

    fun enableSelectExplore(sources: List<BookSource>) {
        execute {
            val list = arrayListOf<BookSource>()
            sources.forEach {
                list.add(it.copy(enabledExplore = true))
            }
            App.db.bookSourceDao().update(*list.toTypedArray())
        }
    }

    fun disableSelectExplore(sources: List<BookSource>) {
        execute {
            val list = arrayListOf<BookSource>()
            sources.forEach {
                list.add(it.copy(enabledExplore = false))
            }
            App.db.bookSourceDao().update(*list.toTypedArray())
        }
    }

    fun delSelection(sources: List<BookSource>) {
        execute {
            App.db.bookSourceDao().delete(*sources.toTypedArray())
        }
    }

    fun exportSelection(sources: List<BookSource>, file: File) {
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

    fun exportSelection(sources: List<BookSource>, doc: DocumentFile) {
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
                    source.removeGroup(group)
                }
                App.db.bookSourceDao().update(*sources.toTypedArray())
            }
        }
    }

}