package io.legado.app.service

import android.content.Intent
import androidx.core.app.NotificationCompat
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.help.AppConfig
import io.legado.app.model.CacheBook
import io.legado.app.ui.book.cache.CacheActivity
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.postEvent
import io.legado.app.utils.servicePendingIntent
import kotlinx.coroutines.*
import splitties.init.appCtx
import java.util.concurrent.Executors
import kotlin.math.min

class CacheBookService : BaseService() {
    private val threadCount = AppConfig.threadCount
    private var cachePool =
        Executors.newFixedThreadPool(min(threadCount, AppConst.MAX_THREAD)).asCoroutineDispatcher()
    private var downloadJob: Job? = null

    private var notificationContent = appCtx.getString(R.string.starting_download)

    private val notificationBuilder by lazy {
        val builder = NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setContentTitle(getString(R.string.offline_cache))
            .setContentIntent(activityPendingIntent<CacheActivity>("cacheActivity"))
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.cancel),
            servicePendingIntent<CacheBookService>(IntentAction.stop)
        )
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    override fun onCreate() {
        super.onCreate()
        upNotification()
        launch {
            while (isActive) {
                delay(1000)
                upNotificationContent()
                postEvent(EventBus.UP_DOWNLOAD, "")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                IntentAction.start -> addDownloadData(
                    intent.getStringExtra("bookUrl"),
                    intent.getIntExtra("start", 0),
                    intent.getIntExtra("end", 0)
                )
                IntentAction.remove -> removeDownload(intent.getStringExtra("bookUrl"))
                IntentAction.stop -> stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        cachePool.close()
        CacheBook.cacheBookMap.clear()
        super.onDestroy()
        postEvent(EventBus.UP_DOWNLOAD, "")
    }

    private fun addDownloadData(bookUrl: String?, start: Int, end: Int) {
        bookUrl ?: return
        execute {
            val cacheBook = CacheBook.getOrCreate(bookUrl) ?: return@execute
            cacheBook.addDownload(start, end)
            if (downloadJob == null) {
                download()
            }
        }
    }

    private fun removeDownload(bookUrl: String?) {
        CacheBook.cacheBookMap.remove(bookUrl)
        if (CacheBook.cacheBookMap.isEmpty()) {
            stopSelf()
        }
    }

    private fun download() {
        downloadJob = launch(cachePool) {
            while (isActive) {
                if (CacheBook.cacheBookMap.isEmpty()) {
                    CacheBook.stop(this@CacheBookService)
                    return@launch
                }
                CacheBook.cacheBookMap.forEach {
                    while (CacheBook.onDownloadCount > threadCount) {
                        delay(100)
                    }
                    it.value.download(this, cachePool)
                }
            }
        }
    }

    private fun upNotificationContent() {
        notificationContent =
            "正在下载:${CacheBook.onDownloadCount}|等待中:${CacheBook.waitDownloadCount}|失败:${CacheBook.errorCount}|成功:${CacheBook.successDownloadCount}"
        upNotification()
    }

    /**
     * 更新通知
     */
    private fun upNotification() {
        notificationBuilder.setContentText(notificationContent)
        val notification = notificationBuilder.build()
        startForeground(AppConst.notificationIdDownload, notification)
    }

}