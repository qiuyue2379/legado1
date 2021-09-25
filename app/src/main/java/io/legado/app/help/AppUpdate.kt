package io.legado.app.help

import io.legado.app.constant.AppConst
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.NoStackTraceException
import io.legado.app.utils.jsonPath
import io.legado.app.utils.readString
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import splitties.init.appCtx

object AppUpdate {

    fun checkFromGitHub(
        scope: CoroutineScope,
        showErrorMsg: Boolean = true,
        callback: (newVersion: String, updateBody: String, url: String, fileName: String) -> Unit
    ) {
        Coroutine.async(scope) {
            val lastReleaseUrl = "http://qiu-yue.top:86/apk/app/release/output-metadata.json"
            val body = okHttpClient.newCallStrResponse {
                url(lastReleaseUrl)
            }.body

            if (body.isNullOrBlank()) {
                throw NoStackTraceException("获取新版本出错")
            }
            val rootDoc = jsonPath.parse(body)
            val tagName = rootDoc.readString("$.elements[0].versionName")
                ?: throw NoStackTraceException("获取新版本出错")
            if (tagName > AppConst.appInfo.versionName) {
                val updateBody = "有版本更新，请下载!"
                val downloadUrl = rootDoc.readString("$.elements[0].outputFile")
                    ?: throw NoStackTraceException("获取新版本出错")
                val fileName = rootDoc.readString("$.elements[0].outputFile")
                    ?: throw NoStackTraceException("获取新版本出错")
                return@async arrayOf(tagName, updateBody, "http://qiu-yue.top:86/apk/app/release/$downloadUrl", fileName)
            } else {
                throw NoStackTraceException("已是最新版本")
            }
        }.onSuccess {
            callback.invoke(it[0], it[1], it[2], it[3])
        }.onError {
            if (showErrorMsg) {
                appCtx.toastOnUi("检测更新\n${it.localizedMessage}")
            }
        }
    }

}