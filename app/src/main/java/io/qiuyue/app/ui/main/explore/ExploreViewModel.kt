package io.qiuyue.app.ui.main.explore

import android.app.Application
import io.qiuyue.app.base.BaseViewModel
import io.qiuyue.app.data.appDb
import io.qiuyue.app.data.entities.BookSource

class ExploreViewModel(application: Application) : BaseViewModel(application) {

    fun topSource(bookSource: BookSource) {
        execute {
            val minXh = appDb.bookSourceDao.minOrder
            bookSource.customOrder = minXh - 1
            appDb.bookSourceDao.insert(bookSource)
        }
    }

}