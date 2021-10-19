package io.legado.app.ui.association

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.model.NoStackTraceException
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.isJson

import io.legado.app.utils.readText
import timber.log.Timber
import java.io.File

class FileAssociationViewModel(application: Application) : BaseViewModel(application) {
    val onLineImportLive = MutableLiveData<Uri>()
    val importBookSourceLive = MutableLiveData<String>()
    val importRssSourceLive = MutableLiveData<String>()
    val importReplaceRuleLive = MutableLiveData<String>()
    val openBookLiveData = MutableLiveData<String>()
    val errorLiveData = MutableLiveData<String>()

    fun dispatchIndent(uri: Uri) {
        execute {
            //如果是普通的url，需要根据返回的内容判断是什么
            if (uri.scheme == "file" || uri.scheme == "content") {
                val content = if (uri.scheme == "file") {
                    File(uri.path.toString()).readText()
                } else {
                    DocumentFile.fromSingleUri(context, uri)?.readText(context)
                }
                content?.let {
                    if (it.isJson()) {
                        //暂时根据文件内容判断属于什么
                        when {
                            content.contains("bookSourceUrl") -> {
                                importBookSourceLive.postValue(it)
                                return@execute
                            }
                            content.contains("sourceUrl") -> {
                                importRssSourceLive.postValue(it)
                                return@execute
                            }
                            content.contains("pattern") -> {
                                importReplaceRuleLive.postValue(it)
                                return@execute
                            }
                        }
                    }
                    val book = LocalBook.importFile(uri)
                    openBookLiveData.postValue(book.bookUrl)
                } ?: throw NoStackTraceException("文件不存在")
            } else {
                onLineImportLive.postValue(uri)
            }
        }.onError {
            Timber.e(it)
            errorLiveData.postValue(it.localizedMessage)
        }
    }
}