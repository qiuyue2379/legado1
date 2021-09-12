package io.qiuyue.app.model

import android.content.Context
import io.qiuyue.app.constant.IntentAction
import io.qiuyue.app.service.DownloadService
import io.qiuyue.app.utils.startService

object Download {

    fun start(context: Context, downloadId: Long, fileName: String) {
        context.startService<DownloadService> {
            action = IntentAction.start
            putExtra("downloadId", downloadId)
            putExtra("fileName", fileName)
        }
    }

    fun stop(context: Context) {
        context.startService<DownloadService> {
            action = IntentAction.stop
        }
    }

}