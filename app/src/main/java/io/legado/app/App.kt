package io.legado.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import com.jeremyliao.liveeventbus.LiveEventBus
import io.legado.app.constant.AppConst.channelIdDownload
import io.legado.app.constant.AppConst.channelIdReadAloud
import io.legado.app.constant.AppConst.channelIdWeb
import io.legado.app.data.AppDatabase
import io.legado.app.help.ActivityHelp
import io.legado.app.help.AppConfig
import io.legado.app.help.CrashHandler
import io.legado.app.help.ReadBookConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getPrefInt

@Suppress("DEPRECATION")
class App : Application() {

    companion object {
        @JvmStatic
        lateinit var INSTANCE: App
            private set

        @JvmStatic
        lateinit var db: AppDatabase
            private set
    }

    var versionCode = 0
    var versionName = ""

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        CrashHandler().init(this)
        db = AppDatabase.createDatabase(INSTANCE)
        packageManager.getPackageInfo(packageName, 0)?.let {
            versionCode = it.versionCode
            versionName = it.versionName
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createChannelId()
        applyDayNight()
        LiveEventBus
            .config()
            .supportBroadcast(this)
            .lifecycleObserverAlwaysActive(true)
            .autoClear(false)

        registerActivityLifecycleCallbacks(ActivityHelp)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_NO -> applyDayNight()
        }
    }

    /**
     * 更新主题
     */
    fun applyTheme() {
        if (AppConfig.isNightTheme) {
            ThemeStore.editTheme(this)
                .primaryColor(
                    getPrefInt("colorPrimaryNight", getCompatColor(R.color.md_blue_grey_600))
                ).accentColor(
                    getPrefInt("colorAccentNight", getCompatColor(R.color.md_brown_800))
                ).backgroundColor(
                    getPrefInt("colorBackgroundNight", getCompatColor(R.color.shine_color))
                ).bottomBackground(
                    getPrefInt("colorBottomBackgroundNight", getCompatColor(R.color.md_grey_850))
                ).apply()
        } else {
            ThemeStore.editTheme(this)
                .primaryColor(
                    getPrefInt("colorPrimary", getCompatColor(R.color.md_indigo_800))
                ).accentColor(
                    getPrefInt("colorAccent", getCompatColor(R.color.md_red_600))
                ).backgroundColor(
                    getPrefInt("colorBackground", getCompatColor(R.color.md_grey_100))
                ).bottomBackground(
                    getPrefInt("colorBottomBackground", getCompatColor(R.color.md_grey_200))
                ).apply()
        }
    }

    fun applyDayNight() {
        ReadBookConfig.upBg()
        applyTheme()
        initNightMode()
    }

    private fun initNightMode() {
        val targetMode =
            if (AppConfig.isNightTheme) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        AppCompatDelegate.setDefaultNightMode(targetMode)
    }

    /**
     * 创建通知ID
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannelId() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.let {
            //用唯一的ID创建渠道对象
            val downloadChannel = NotificationChannel(
                channelIdDownload,
                getString(R.string.download_offline),
                NotificationManager.IMPORTANCE_LOW
            )
            //初始化channel
            downloadChannel.enableLights(false)
            downloadChannel.enableVibration(false)
            downloadChannel.setSound(null, null)

            //用唯一的ID创建渠道对象
            val readAloudChannel = NotificationChannel(
                channelIdReadAloud,
                getString(R.string.read_aloud),
                NotificationManager.IMPORTANCE_LOW
            )
            //初始化channel
            readAloudChannel.enableLights(false)
            readAloudChannel.enableVibration(false)
            readAloudChannel.setSound(null, null)

            //用唯一的ID创建渠道对象
            val webChannel = NotificationChannel(
                channelIdWeb,
                getString(R.string.web_service),
                NotificationManager.IMPORTANCE_LOW
            )
            //初始化channel
            webChannel.enableLights(false)
            webChannel.enableVibration(false)
            webChannel.setSound(null, null)

            //向notification manager 提交channel
            it.createNotificationChannels(listOf(downloadChannel, readAloudChannel, webChannel))
        }
    }

}
