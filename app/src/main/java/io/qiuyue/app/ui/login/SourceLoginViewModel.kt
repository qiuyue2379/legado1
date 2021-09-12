package io.qiuyue.app.ui.login

import android.app.Application
import io.qiuyue.app.base.BaseViewModel
import io.qiuyue.app.data.appDb
import io.qiuyue.app.data.entities.BaseSource
import io.qiuyue.app.utils.toastOnUi

class SourceLoginViewModel(application: Application) : BaseViewModel(application) {

    var source: BaseSource? = null

    fun initData(sourceUrl: String, success: (bookSource: BaseSource) -> Unit) {
        execute {
            source = appDb.bookSourceDao.getBookSource(sourceUrl)
            if (source == null) {
                source = appDb.rssSourceDao.getByKey(sourceUrl)
            }
            source
        }.onSuccess {
            if (it != null) {
                success.invoke(it)
            } else {
                context.toastOnUi("未找到书源")
            }
        }
    }

}