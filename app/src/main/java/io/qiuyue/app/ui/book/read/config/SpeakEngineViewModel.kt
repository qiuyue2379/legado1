package io.qiuyue.app.ui.book.read.config

import android.app.Application
import android.net.Uri
import io.qiuyue.app.base.BaseViewModel
import io.qiuyue.app.data.appDb
import io.qiuyue.app.data.entities.HttpTTS
import io.qiuyue.app.help.DefaultData
import io.qiuyue.app.help.http.newCall
import io.qiuyue.app.help.http.okHttpClient
import io.qiuyue.app.help.http.text
import io.qiuyue.app.utils.*

class SpeakEngineViewModel(application: Application) : BaseViewModel(application) {

    fun importDefault() {
        execute {
            DefaultData.importDefaultHttpTTS()
        }
    }

    fun importOnLine(url: String) {
        execute {
            okHttpClient.newCall {
                url(url)
            }.text("utf-8").let { json ->
                import(json)
            }
        }.onSuccess {
            context.toastOnUi("导入成功")
        }.onError {
            context.toastOnUi("导入失败")
        }
    }

    fun importLocal(uri: Uri) {
        execute {
            uri.readText(context)?.let {
                import(it)
            }
        }.onSuccess {
            context.toastOnUi("导入成功")
        }.onError {
            context.toastOnUi("导入失败")
        }
    }

    fun import(text: String) {
        when {
            text.isJsonArray() -> {
                GSON.fromJsonArray<HttpTTS>(text)?.let {
                    appDb.httpTTSDao.insert(*it.toTypedArray())
                }
            }
            text.isJsonObject() -> {
                GSON.fromJsonObject<HttpTTS>(text)?.let {
                    appDb.httpTTSDao.insert(it)
                }
            }
            else -> {
                throw Exception("格式不对")
            }
        }
    }

}