package io.legado.app.ui.book.local

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.base.BaseViewModel
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.DocItem
import io.legado.app.utils.DocumentUtils
import java.io.File
import java.util.*


class ImportBookViewModel(application: Application) : BaseViewModel(application) {

    fun addToBookshelf(uriList: HashSet<String>, finally: () -> Unit) {
        execute {
            uriList.forEach {
                LocalBook.importFile(it)
            }
        }.onFinally {
            finally.invoke()
        }
    }

    fun deleteDoc(uriList: HashSet<String>, finally: () -> Unit) {
        execute {
            uriList.forEach {
                DocumentFile.fromSingleUri(context, Uri.parse(it))?.delete()
            }
        }.onFinally {
            finally.invoke()
        }
    }

    fun scanDoc(documentFile: DocumentFile, find: (docItem: DocItem) -> Unit) {
        execute {
            val docList = DocumentUtils.listFiles(context, documentFile.uri)
            docList.forEach { docItem ->
                if (docItem.isDir) {
                    DocumentFile.fromSingleUri(context, docItem.uri)?.let {
                        scanDoc(it, find)
                    }
                } else if (docItem.name.endsWith(".txt", true)
                    || docItem.name.endsWith(".epub", true)
                ) {
                    find(docItem)
                }
            }
        }
    }

    fun scanFile(file: File, find: (docItem: DocItem) -> Unit) {
        execute {
            file.listFiles()?.forEach {
                if (it.isDirectory) {
                    scanFile(it, find)
                } else if (it.name.endsWith(".txt", true)
                    || it.name.endsWith(".epub", true)
                ) {
                    find(
                        DocItem(
                            it.name,
                            it.extension,
                            it.length(),
                            Date(it.lastModified()),
                            Uri.parse(it.absolutePath)
                        )
                    )
                }
            }
        }
    }

}