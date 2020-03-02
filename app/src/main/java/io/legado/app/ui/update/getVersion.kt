package io.legado.app.ui.update

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * @author Li Xuyang
 * @date : 2019/9/24 21:06
 */
object getVersion {


    /* 获取本地软件版本号​
 */
    fun getLocalVersion(ctx: Context): Int {
        var localVersion = 0
        try {
            val packageInfo = ctx.applicationContext
                .packageManager
                .getPackageInfo(ctx.packageName, 0)
            localVersion = packageInfo.versionCode
            Log.d("TAG", "当前版本号：$localVersion")
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        return localVersion
    }


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