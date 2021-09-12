package io.qiuyue.app.model

import android.content.Context
import io.qiuyue.app.R
import io.qiuyue.app.constant.IntentAction
import io.qiuyue.app.data.entities.BookSource
import io.qiuyue.app.service.CheckSourceService
import io.qiuyue.app.utils.startService
import io.qiuyue.app.utils.toastOnUi

object CheckSource {
    var keyword = "我的"

    fun start(context: Context, sources: List<BookSource>) {
        if (sources.isEmpty()) {
            context.toastOnUi(R.string.non_select)
            return
        }
        val selectedIds: ArrayList<String> = arrayListOf()
        sources.map {
            selectedIds.add(it.bookSourceUrl)
        }
        context.startService<CheckSourceService> {
            action = IntentAction.start
            putExtra("selectIds", selectedIds)
        }
    }

    fun stop(context: Context) {
        context.startService<CheckSourceService> {
            action = IntentAction.stop
        }
    }
}