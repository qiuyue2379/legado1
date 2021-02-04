package io.legado.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import androidx.multidex.MultiDexApplication
import com.jeremyliao.liveeventbus.LiveEventBus
import com.tencent.smtt.sdk.QbSdk
import com.tencent.smtt.sdk.QbSdk.PreInitCallback
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppConst.channelIdDownload
import io.legado.app.constant.AppConst.channelIdReadAloud
import io.legado.app.constant.AppConst.channelIdWeb
import io.legado.app.data.AppDatabase
import io.legado.app.help.ActivityHelp
import io.legado.app.help.AppConfig
import io.legado.app.help.CrashHandler
import io.legado.app.help.ThemeConfig.applyDayNight
import io.legado.app.help.http.HttpHelper
import io.legado.app.utils.LanguageUtils
import io.legado.app.utils.defaultSharedPreferences
import rxhttp.wrapper.param.RxHttp


@Suppress("DEPRECATION")
class App : MultiDexApplication() {

    companion object {

        @JvmStatic
        lateinit var db: AppDatabase
            private set

        lateinit var androidId: String
        var versionCode = 0
        var versionName = ""
        var navigationBarHeight = 0
    }

    override fun onCreate() {
        super.onCreate()
        androidId = Settings.System.getString(contentResolver, Settings.Secure.ANDROID_ID)
        CrashHandler(this)
        LanguageUtils.setConfiguration(this)
        db = AppDatabase.createDatabase(this)
        RxHttp.init(HttpHelper.client, BuildConfig.DEBUG)
        RxHttp.setOnParamAssembly {
            it.addHeader(AppConst.UA_NAME, AppConfig.userAgent)
        }
        packageManager.getPackageInfo(packageName, 0)?.let {
            versionCode = it.versionCode
            versionName = it.versionName
        }
        createNotificationChannels()
        applyDayNight(this)
        LiveEventBus.config()
            .supportBroadcast(this)
            .lifecycleObserverAlwaysActive(true)
            .autoClear(false)
        registerActivityLifecycleCallbacks(ActivityHelp)
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(AppConfig)
        initX5()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES,
            Configuration.UI_MODE_NIGHT_NO -> applyDayNight(this)
        }
    }

    private fun initX5() {
        //搜集本地tbs内核信息并上报服务器，服务器返回结果决定使用哪个内核。
        val cb: PreInitCallback = object : PreInitCallback {
            override fun onViewInitFinished(arg0: Boolean) {
                //x5內核初始化完成的回调，为true表示x5内核加载成功，否则表示x5内核加载失败，会自动切换到系统内核。
            }
            override fun onCoreInitFinished() {}
        }
        //x5内核初始化接口
        QbSdk.initX5Environment(applicationContext, cb)
    }

    /**
     * 创建通知ID
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.let {
            val downloadChannel = NotificationChannel(
                channelIdDownload,
                getString(R.string.action_download),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }

            val readAloudChannel = NotificationChannel(
                channelIdReadAloud,
                getString(R.string.read_aloud),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }

            val webChannel = NotificationChannel(
                channelIdWeb,
                getString(R.string.web_service),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }

            //向notification manager 提交channel
            it.createNotificationChannels(listOf(downloadChannel, readAloudChannel, webChannel))
        }
    }

}