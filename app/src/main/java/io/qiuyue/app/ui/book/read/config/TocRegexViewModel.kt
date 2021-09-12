package io.qiuyue.app.ui.book.read.config

import android.app.Application
import io.qiuyue.app.base.BaseViewModel
import io.qiuyue.app.data.appDb
import io.qiuyue.app.data.entities.TxtTocRule
import io.qiuyue.app.help.DefaultData
import io.qiuyue.app.help.http.newCall
import io.qiuyue.app.help.http.okHttpClient
import io.qiuyue.app.help.http.text
import io.qiuyue.app.utils.GSON
import io.qiuyue.app.utils.fromJsonArray

class TocRegexViewModel(application: Application) : BaseViewModel(application) {

    fun saveRule(rule: TxtTocRule) {
        execute {
            if (rule.serialNumber < 0) {
                rule.serialNumber = appDb.txtTocRuleDao.lastOrderNum + 1
            }
            appDb.txtTocRuleDao.insert(rule)
        }
    }

    fun importDefault() {
        execute {
            DefaultData.importDefaultTocRules()
        }
    }

    fun importOnLine(url: String, finally: (msg: String) -> Unit) {
        execute {
            okHttpClient.newCall {
                url(url)
            }.text("utf-8").let { json ->
                GSON.fromJsonArray<TxtTocRule>(json)?.let {
                    appDb.txtTocRuleDao.insert(*it.toTypedArray())
                }
            }
        }.onSuccess {
            finally("导入成功")
        }.onError {
            finally("导入失败")
        }
    }

}