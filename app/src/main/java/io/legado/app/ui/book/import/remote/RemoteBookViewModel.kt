package io.legado.app.ui.book.import.remote

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.lib.webdav.Authorization
import io.legado.app.model.analyzeRule.CustomUrl
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.remote.RemoteBook
import io.legado.app.model.remote.RemoteBookWebDav
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.*

class RemoteBookViewModel(application: Application) : BaseViewModel(application) {
    var sortKey = RemoteBookSort.Default
    var sortAscending = false
    val dirList = arrayListOf<RemoteBook>()

    var dataCallback: DataCallback? = null

    val dataFlow = callbackFlow<List<RemoteBook>> {

        val list = Collections.synchronizedList(ArrayList<RemoteBook>())

        dataCallback = object : DataCallback {

            override fun setItems(remoteFiles: List<RemoteBook>) {
                list.clear()
                list.addAll(remoteFiles)
                trySend(list)
            }

            override fun addItems(remoteFiles: List<RemoteBook>) {
                list.addAll(remoteFiles)
                trySend(list)
            }

            override fun clear() {
                list.clear()
                trySend(emptyList())
            }
        }

        awaitClose {
            dataCallback = null
        }
    }.map { list ->
        if (sortAscending) when (sortKey) {
            RemoteBookSort.Name -> list.sortedWith(compareBy({ !it.isDir }, { it.filename }))
            else -> list.sortedWith(compareBy({ !it.isDir }, { it.lastModify }))
        } else when (sortKey) {
            RemoteBookSort.Name -> list.sortedWith { o1, o2 ->
                val compare = -compareValues(o1.isDir, o2.isDir)
                if (compare == 0) {
                    return@sortedWith -compareValues(o1.filename, o2.filename)
                }
                return@sortedWith compare
            }
            else -> list.sortedWith { o1, o2 ->
                val compare = -compareValues(o1.isDir, o2.isDir)
                if (compare == 0) {
                    return@sortedWith -compareValues(o1.lastModify, o2.lastModify)
                }
                return@sortedWith compare
            }
        }
    }.flowOn(Dispatchers.IO)

    private var remoteBookWebDav: RemoteBookWebDav? = null

    fun initData(onSuccess: () -> Unit) {
        execute {
            val config = appDb.serverDao.get(10001)?.getConfigJsonObject()
            if (config != null && config.has("url")) {
                val url = config.getString("url")
                if (url.isNotBlank()) {
                    val user = config.getString("user")
                    val password = config.getString("password")
                    val authorization = Authorization(user, password)
                    remoteBookWebDav = RemoteBookWebDav(url, authorization, 10001)
                    return@execute
                }
            }
            remoteBookWebDav = AppWebDav.defaultBookWebDav
                ?: throw NoStackTraceException("webDav没有配置")
        }.onError {
            context.toastOnUi("初始化webDav出错:${it.localizedMessage}")
        }.onSuccess {
            onSuccess.invoke()
        }
    }

    fun loadRemoteBookList(path: String?, loadCallback: (loading: Boolean) -> Unit) {
        execute {
            val bookWebDav = remoteBookWebDav
                ?: throw NoStackTraceException("没有配置webDav")
            dataCallback?.clear()
            val url = path ?: bookWebDav.rootBookUrl
            val bookList = bookWebDav.getRemoteBookList(url)
            dataCallback?.setItems(bookList)
        }.onError {
            AppLog.put("获取webDav书籍出错\n${it.localizedMessage}", it)
            context.toastOnUi("获取webDav书籍出错\n${it.localizedMessage}")
        }.onStart {
            loadCallback.invoke(true)
        }.onFinally {
            loadCallback.invoke(false)
        }
    }

    fun addToBookshelf(remoteBooks: HashSet<RemoteBook>, finally: () -> Unit) {
        execute {
            remoteBooks.forEach { remoteBook ->
                val bookWebDav = remoteBookWebDav
                    ?: throw NoStackTraceException("没有配置webDav")
                val downloadBookPath = bookWebDav.downloadRemoteBook(remoteBook)
                downloadBookPath.let {
                    val localBook = LocalBook.importFile(it)
                    localBook.origin = BookType.webDavTag + CustomUrl(remoteBook.path)
                        .putAttribute(
                            "serverID",
                            bookWebDav.serverID
                        ).toString()
                    localBook.save()
                    remoteBook.isOnBookShelf = true
                }
            }
        }.onError {
            AppLog.put("导入出错\n${it.localizedMessage}", it)
            context.toastOnUi("导入出错\n${it.localizedMessage}")
        }.onFinally {
            finally.invoke()
        }
    }

    interface DataCallback {

        fun setItems(remoteFiles: List<RemoteBook>)

        fun addItems(remoteFiles: List<RemoteBook>)

        fun clear()

    }
}