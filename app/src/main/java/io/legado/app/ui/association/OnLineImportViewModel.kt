package io.legado.app.ui.association

import android.app.Application
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import okhttp3.MediaType.Companion.toMediaType

class OnLineImportViewModel(app: Application) : BaseAssociationViewModel(app) {

    fun getText(url: String, success: (text: String) -> Unit) {
        execute {
            okHttpClient.newCallResponseBody {
                url(url)
            }.text("utf-8")
        }.onSuccess {
            success.invoke(it)
        }.onError {
            errorLive.postValue(
                it.localizedMessage ?: context.getString(R.string.unknown_error)
            )
        }
    }

    fun getBytes(url: String, success: (bytes: ByteArray) -> Unit) {
        execute {
            @Suppress("BlockingMethodInNonBlockingContext")
            okHttpClient.newCallResponseBody {
                url(url)
            }.bytes()
        }.onSuccess {
            success.invoke(it)
        }.onError {
            errorLive.postValue(
                it.localizedMessage ?: context.getString(R.string.unknown_error)
            )
        }
    }

    fun importReadConfig(bytes: ByteArray, finally: (title: String, msg: String) -> Unit) {
        execute {
            val config = ReadBookConfig.import(bytes).getOrThrow()
            ReadBookConfig.configList.forEachIndexed { index, c ->
                if (c.name == config.name) {
                    ReadBookConfig.configList[index] = config
                    return@execute config.name
                }
                ReadBookConfig.configList.add(config)
                return@execute config.name
            }
        }.onSuccess {
            finally.invoke(context.getString(R.string.success), "导入排版成功")
        }.onError {
            finally.invoke(
                context.getString(R.string.error),
                it.localizedMessage ?: context.getString(R.string.unknown_error)
            )
        }
    }

    fun determineType(url: String, finally: (title: String, msg: String) -> Unit) {
        execute {
            val rs = okHttpClient.newCallResponseBody {
                url(url)
            }
            when (rs.contentType()) {
                "application/zip".toMediaType(),
                "application/octet-stream".toMediaType() -> {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    importReadConfig(rs.bytes(), finally)
                }
                else -> {
                    val json = rs.text("utf-8")
                    importJson(json)
                }
            }
        }
    }

}