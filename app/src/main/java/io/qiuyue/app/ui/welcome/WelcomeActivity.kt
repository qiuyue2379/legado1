package io.qiuyue.app.ui.welcome

import android.content.Intent
import android.os.Bundle
import com.github.liuyueyi.quick.transfer.ChineseUtils
import io.qiuyue.app.base.BaseActivity
import io.qiuyue.app.constant.PreferKey
import io.qiuyue.app.data.appDb
import io.qiuyue.app.databinding.ActivityWelcomeBinding
import io.qiuyue.app.help.AppConfig
import io.qiuyue.app.help.coroutine.Coroutine
import io.qiuyue.app.help.storage.AppWebDav
import io.qiuyue.app.lib.theme.accentColor
import io.qiuyue.app.ui.book.read.ReadBookActivity
import io.qiuyue.app.ui.main.MainActivity
import io.qiuyue.app.utils.getPrefBoolean
import io.qiuyue.app.utils.startActivity
import io.qiuyue.app.utils.viewbindingdelegate.viewBinding
import java.util.concurrent.TimeUnit

open class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>() {

    override val binding by viewBinding(ActivityWelcomeBinding::inflate)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.ivBook.setColorFilter(accentColor)
        binding.vwTitleLine.setBackgroundColor(accentColor)
        // 避免从桌面启动程序后，会重新实例化入口类的activity
        if (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0) {
            finish()
        } else {
            init()
        }
    }

    private fun init() {
        Coroutine.async {
            val books = appDb.bookDao.all
            books.forEach { book ->
                AppWebDav.getBookProgress(book)?.let { bookProgress ->
                    if (bookProgress.durChapterIndex > book.durChapterIndex ||
                        (bookProgress.durChapterIndex == book.durChapterIndex &&
                                bookProgress.durChapterPos > book.durChapterPos)
                    ) {
                        book.durChapterIndex = bookProgress.durChapterIndex
                        book.durChapterPos = bookProgress.durChapterPos
                        book.durChapterTitle = bookProgress.durChapterTitle
                        book.durChapterTime = bookProgress.durChapterTime
                        appDb.bookDao.update(book)
                    }
                }
            }
        }
        Coroutine.async {
            appDb.cacheDao.clearDeadline(System.currentTimeMillis())
            //清除过期数据
            if (getPrefBoolean(PreferKey.autoClearExpired, true)) {
                appDb.searchBookDao
                    .clearExpired(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
            }
            //初始化简繁转换引擎
            when (AppConfig.chineseConverterType) {
                1 -> ChineseUtils.t2s("初始化")
                2 -> ChineseUtils.s2t("初始化")
                else -> null
            }
        }
        binding.root.postDelayed({ startMainActivity() }, 500)
    }

    private fun startMainActivity() {
        startActivity<MainActivity>()
        if (getPrefBoolean(PreferKey.defaultToRead)) {
            startActivity<ReadBookActivity>()
        }
        finish()
    }

}

class Launcher1 : WelcomeActivity()
class Launcher2 : WelcomeActivity()
class Launcher3 : WelcomeActivity()
class Launcher4 : WelcomeActivity()
class Launcher5 : WelcomeActivity()
class Launcher6 : WelcomeActivity()