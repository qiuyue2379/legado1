package io.legado.app.ui.update

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object getVersion {

    /**
     * 获取本地软件版本号名称
     */
    fun getLocalVersionName(ctx: Context): String {
        var localVersion = ""
        try {
            val packageInfo = ctx.applicationContext
                .packageManager
                .getPackageInfo(ctx.packageName, 0)
            localVersion = packageInfo.versionName
            Log.d("TAG", "当前版本名称：$localVersion")
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return localVersion
    }

}