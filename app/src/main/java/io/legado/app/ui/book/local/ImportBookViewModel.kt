package io.legado.app.ui.book.local

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppPattern.bookFileRegex
import io.legado.app.constant.PreferKey
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class ImportBookViewModel(application: Application) : BaseViewModel(application) {
    var sort = context.getPrefInt(PreferKey.localBookImportSort)
    var dataCallback: DataCallback? = null
    var dataFlowStart: (() -> Unit)? = null
    val dataFlow = callbackFlow<List<FileDoc>> {

        val list = Collections.synchronizedList(ArrayList<FileDoc>())

        dataCallback = object : DataCallback {

            override fun setItems(fileDocs: List<FileDoc>) {
                list.clear()
                list.addAll(fileDocs)
                trySend(list)
            }

            override fun addItems(fileDocs: List<FileDoc>) {
                list.addAll(fileDocs)
                trySend(list)
            }

            override fun clear() {
                list.clear()
                trySend(emptyList())
            }
        }

        withContext(Main) {
            dataFlowStart?.invoke()
        }

        awaitClose {
            dataCallback = null
        }

    }.map { docList ->
        when (sort) {
            2 -> docList.sortedWith(
                compareBy({ !it.isDir }, { -it.lastModified }, { it.name })
            )
            1 -> docList.sortedWith(
                compareBy({ !it.isDir }, { -it.size }, { it.name })
            )
            else -> docList.sortedWith(
                compareBy({ !it.isDir }, { it.name })
            )
        }
    }.flowOn(IO)

    fun addToBookshelf(uriList: HashSet<String>, finally: () -> Unit) {
        execute {
            uriList.forEach {
                LocalBook.importFile(Uri.parse(it))
            }
        }.onFinally {
            finally.invoke()
        }
    }

    fun deleteDoc(uriList: HashSet<String>, finally: () -> Unit) {
        execute {
            uriList.forEach {
                val uri = Uri.parse(it)
                if (uri.isContentScheme()) {
                    DocumentFile.fromSingleUri(context, uri)?.delete()
                } else {
                    uri.path?.let { path ->
                        File(path).delete()
                    }
                }
            }
        }.onFinally {
            finally.invoke()
        }
    }

    fun loadDoc(uri: Uri) {
        execute {
            val docList = DocumentUtils.listFiles(uri) { item ->
                when {
                    item.name.startsWith(".") -> false
                    item.isDir -> true
                    else -> item.name.matches(bookFileRegex)
                }
            }
            dataCallback?.setItems(docList)
        }.onError {
            context.toastOnUi("获取文件列表出错\n${it.localizedMessage}")
        }
    }

    fun scanDoc(
        fileDoc: FileDoc,
        isRoot: Boolean,
        scope: CoroutineScope,
        finally: (() -> Unit)? = null
    ) {
        if (isRoot) {
            dataCallback?.clear()
        }
        if (!scope.isActive) {
            finally?.invoke()
            return
        }
        kotlin.runCatching {
            val list = ArrayList<FileDoc>()
            DocumentUtils.listFiles(fileDoc.uri).forEach { docItem ->
                if (!scope.isActive) {
                    finally?.invoke()
                    return
                }
                if (docItem.isDir) {
                    scanDoc(docItem, false, scope)
                } else if (docItem.name.endsWith(".txt", true)
                    || docItem.name.endsWith(".epub", true)
                ) {
                    list.add(docItem)
                }
            }
            if (!scope.isActive) {
                finally?.invoke()
                return
            }
            if (list.isNotEmpty()) {
                dataCallback?.addItems(list)
            }
        }.onFailure {
            context.toastOnUi("扫描文件夹出错\n${it.localizedMessage}")
        }
        if (isRoot) {
            finally?.invoke()
        }
    }

    interface DataCallback {

        fun setItems(fileDocs: List<FileDoc>)

        fun addItems(fileDocs: List<FileDoc>)

        fun clear()

    }

}