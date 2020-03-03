package io.legado.app.service

import android.content.Intent
import androidx.core.app.NotificationCompat
import io.legado.app.App
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.IntentAction
import io.legado.app.help.AppConfig
import io.legado.app.help.IntentHelp
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.WebBook
import io.legado.app.ui.book.source.manage.BookSourceActivity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.asCoroutineDispatcher
import org.jetbrains.anko.toast
import java.util.concurrent.Executors

class CheckSourceService : BaseService() {
    private val threadCount = AppConfig.threadCount
    private var searchPool = Executors.newFixedThreadPool(threadCount).asCoroutineDispatcher()
    private var task: Coroutine<*>? = null
    private val allIds = ArrayList<String>()
    private val checkedIds = ArrayList<String>()
    private var processIndex = 0

    override fun onCreate() {
        super.onCreate()
        updateNotification(0, getString(R.string.start))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentAction.start -> intent.getStringArrayListExtra("selectIds")?.let {
                check(it)
            }
            else -> stopSelf()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        task?.cancel()
        searchPool.close()
    }

    private fun check(ids: List<String>) {
        task?.cancel()
        allIds.clear()
        checkedIds.clear()
        allIds.addAll(ids)
        processIndex = 0
        updateNotification(0, getString(R.string.progress_show, 0, allIds.size))
        task = execute {
            for (i in 0 until threadCount) {
                check()
            }
        }.onError {
            toast("校验书源出错:${it.localizedMessage}")
        }
    }


    private fun check() {
        synchronized(this) {
            processIndex++
        }
        if (processIndex < allIds.size) {
            val sourceUrl = allIds[processIndex]
            App.db.bookSourceDao().getBookSource(sourceUrl)?.let { source ->
                val webBook = WebBook(source)
                webBook.searchBook("我的", scope = this, context = searchPool)
                    .onError(IO) {
                        source.addGroup("失效")
                        App.db.bookSourceDao().update(source)
                    }.onFinally(IO) {
                        check()
                        checkedIds.add(sourceUrl)
                        updateNotification(
                            checkedIds.size,
                            getString(R.string.progress_show, checkedIds.size, allIds.size)
                        )
                        synchronized(this) {
                            if (processIndex >= allIds.size + threadCount - 1) {
                                stopSelf()
                            }
                        }
                    }
            }
        }
    }

    /**
     * 更新通知
     */
    private fun updateNotification(state: Int, msg: String) {
        val builder = NotificationCompat.Builder(this, AppConst.channelIdReadAloud)
            .setSmallIcon(R.drawable.ic_network_check)
            .setOngoing(true)
            .setContentTitle(getString(R.string.check_book_source))
            .setContentText(msg)
            .setContentIntent(
                IntentHelp.activityPendingIntent<BookSourceActivity>(this, "activity")
            )
            .addAction(
                R.drawable.ic_stop_black_24dp,
                getString(R.string.cancel),
                IntentHelp.servicePendingIntent<CheckSourceService>(this, IntentAction.stop)
            )
        builder.setProgress(allIds.size, state, false)
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        val notification = builder.build()
        startForeground(112202, notification)
    }

}