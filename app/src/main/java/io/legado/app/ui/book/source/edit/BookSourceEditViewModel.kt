package io.legado.app.ui.book.source.edit

import android.app.Application
import android.content.Intent
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.NoStackTraceException
import io.legado.app.utils.*
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

class BookSourceEditViewModel(application: Application) : BaseViewModel(application) {

    var bookSource: BookSource? = null
    private var oldSourceUrl: String? = null

    fun initData(intent: Intent, onFinally: () -> Unit) {
        execute {
            val sourceUrl = intent.getStringExtra("sourceUrl")
            var source: BookSource? = null
            if (sourceUrl != null) {
                source = appDb.bookSourceDao.getBookSource(sourceUrl)
            }
            source?.let {
                oldSourceUrl = it.bookSourceUrl
                bookSource = it
            }
        }.onFinally {
            onFinally()
        }
    }

    fun save(source: BookSource, success: (() -> Unit)? = null) {
        execute {
            oldSourceUrl?.let {
                if (oldSourceUrl != source.bookSourceUrl) {
                    appDb.bookSourceDao.delete(it)
                }
            }
            oldSourceUrl = source.bookSourceUrl
            source.lastUpdateTime = System.currentTimeMillis()
            appDb.bookSourceDao.insert(source)
            bookSource = source
        }.onSuccess {
            success?.invoke()
        }.onError {
            context.toastOnUi(it.localizedMessage)
            Timber.e(it)
        }
    }

    fun pasteSource(onSuccess: (source: BookSource) -> Unit) {
        execute(context = Dispatchers.Main) {
            val text = context.getClipText()
            if (text.isNullOrBlank()) {
                throw NoStackTraceException("剪贴板为空")
            } else {
                importSource(text, onSuccess)
            }
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "Error")
            Timber.e(it)
        }
    }

    fun importSource(text: String, finally: (source: BookSource) -> Unit) {
        execute {
            importSource(text)
        }.onSuccess {
            it?.let(finally) ?: context.toastOnUi("格式不对")
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "Error")
        }
    }

    suspend fun importSource(text: String): BookSource? {
        return when {
            text.isAbsUrl() -> {
                val text1 = okHttpClient.newCallStrResponse { url(text) }.body
                text1?.let { importSource(text1) }
            }
            text.isJsonArray() -> {
                val items: List<Map<String, Any>> = jsonPath.parse(text).read("$")
                val jsonItem = jsonPath.parse(items[0])
                BookSource.fromJson(jsonItem.jsonString())
            }
            text.isJsonObject() -> {
                BookSource.fromJson(text)
            }
            else -> {
                null
            }
        }
    }
}