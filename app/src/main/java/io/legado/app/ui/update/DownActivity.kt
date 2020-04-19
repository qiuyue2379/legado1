package io.legado.app.ui.update

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.daimajia.numberprogressbar.NumberProgressBar
import com.maning.updatelibrary.InstallUtils.*
import com.zenglb.downloadinstaller.DownloadInstaller
import com.zenglb.downloadinstaller.DownloadProgressCallBack
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.ui.update.getVersion.getLocalVersionName
import kotlinx.android.synthetic.main.activity_down.*
import okhttp3.*
import org.jetbrains.anko.sdk27.listeners.onClick
import org.jetbrains.anko.toast
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern

class DownActivity : BaseActivity(R.layout.activity_down) {

    private var downloadCallBack: DownloadCallBack? = null
    private var apkDownloadPath: String? = null
    lateinit var uploadfath: String

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        initCallBack()
    }

    override fun onResume() {
        super.onResume()
        //设置监听,防止其他页面设置回调后当前页面回调失效
        if (isDownloading()) {
            setDownloadCallBack(downloadCallBack)
        }
    }

    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    private fun initCallBack() {
        downloadCallBack = object : DownloadCallBack {
            override fun onStart() {
                tv_progress.text = "0%"
                tv_info.text = "正在下载..."
                btnDownload.isClickable = false
                btnDownload.setBackgroundResource(R.color.colorAccent)
            }

            @SuppressLint("SetTextI18n")
            override fun onComplete(path: String) {
                apkDownloadPath = path
                tv_progress.text = "100%"
                tv_info.text = "下载成功"
                btnDownload.isClickable = true
                btnDownload.setBackgroundResource(R.color.colorPrimary)
                //先判断有没有安装权限
                checkInstallPermission(this@DownActivity, object : InstallPermissionCallBack {
                    override fun onGranted() { //去安装APK
                        installApk(apkDownloadPath.toString())
                    }

                    override fun onDenied() { //弹出弹框提醒用户
                        val alertDialog =
                            AlertDialog.Builder(applicationContext)
                                .setTitle("温馨提示")
                                .setMessage("必须授权才能安装APK，请设置允许安装")
                                .setNegativeButton("取消", null)
                                .setPositiveButton(
                                    "设置"
                                ) { dialog, which ->
                                    //打开设置页面
                                    openInstallPermissionSetting(
                                        this@DownActivity,
                                        object : InstallPermissionCallBack {
                                            override fun onGranted() { //去安装APK
                                                installApk(apkDownloadPath.toString())
                                            }

                                            override fun onDenied() { //还是不允许咋搞？
                                                Toast.makeText(
                                                    applicationContext,
                                                    "不允许安装咋搞？强制更新就退出应用程序吧！",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        })
                                }
                                .create()
                        alertDialog.show()
                    }
                })
            }

            @SuppressLint("SetTextI18n")
            @RequiresApi(Build.VERSION_CODES.N)
            override fun onLoading(
                total: Long,
                current: Long
            ) { //内部做了处理，onLoading 进度转回progress必须是+1，防止频率过快
                val progress = (current * 100 / total).toInt()
                onProgressUpdateBar.visibility = View.VISIBLE
                tv_progress.visibility = View.VISIBLE
                tv_info.visibility = View.VISIBLE
                tv_progress.text = "$progress%"
                onProgressUpdateBar.setProgress(progress, true)
            }

            @SuppressLint("SetTextI18n")
            override fun onFail(e: Exception) {
                tv_info.text = "下载失败:$e"
                btnDownload.isClickable = true
                btnDownload.setBackgroundResource(R.color.colorPrimary)
            }

            override fun cancle() {
                tv_info.text = "下载取消"
                btnDownload.isClickable = true
                btnDownload.setBackgroundResource(R.color.colorPrimary)
            }
        }
    }

    private fun installApk(path: String) {
        installAPK(this, path, object : InstallCallBack {
            override fun onSuccess() { //onSuccess：表示系统的安装界面被打开
                //防止用户取消安装，在这里可以关闭当前应用，以免出现安装被取消
                toast("正在安装程序")
            }

            @SuppressLint("SetTextI18n")
            override fun onFail(e: Exception) {
                tv_info.text = "安装失败:$e"
            }
        })
    }

    private fun dance() {
        //取消下载
        cancleDownload()
        btnDownload.isClickable = true
        btnDownload.setBackgroundResource(R.color.colorPrimary)
        toast("已取消下载")
    }

    private fun initView() {
        btnDownload.onClick {
            dialog()
        }
        btnCancle.onClick {
            dance()
        }
        btnDown.onClick {
            showUpdateDialog("有版本更新,请下载!", false, "http://qiuyue.vicp.net:86/apk/app/release/$uploadfath")
        }

        object : Thread() {
            override fun run() {
                val okHttpClient = OkHttpClient()
                val request = Request.Builder()
                    .url("http://qiuyue.vicp.net:86/apk/app/release/output.json")//请求的url
                    .get()
                    .build()

                //创建/Call
                val call = okHttpClient.newCall(request)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {

                    }

                    @Throws(IOException::class)
                    override fun onResponse(call: Call, response: Response) {
                        val string = response.body()?.string()
                        print(string)
                        if (string != null) {
                            try {
                                val getJsonArray = JSONArray(string)
                                val jsonObject: JSONObject = getJsonArray.getJSONObject(0)
                                val obj = jsonObject.getJSONObject("apkData")
                                Looper.prepare()
                                uploadfath = obj.getString("outputFile")
                                val version = obj.getString("versionName")

                                val regEx = "[^0-9]"
                                val p: Pattern = Pattern.compile(regEx)
                                val remoteVersion: Matcher = p.matcher(version)
                                val localVersion: Matcher =
                                    p.matcher(getLocalVersionName(this@DownActivity))
                                if (remoteVersion.replaceAll("").trim()
                                        .toInt() > localVersion.replaceAll("").trim().toInt()
                                ) {
                                    //dialog()
                                    showUpdateDialog("有版本更新,请下载!", true, "http://qiuyue.vicp.net:86/apk/app/release/$uploadfath")
                                    tv_markdown.text = "有版本更新，请下载!"
                                } else {
                                    toast("已是最新版本")
                                    tv_markdown.text = "已是最新版本!"
                                }
                                Looper.loop()
                            } catch (e: JSONException) {
                                e.printStackTrace()
                            }
                        }
                    }
                })

            }
        }.start()
    }

    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    private fun dialog() {
        val normalDialog = AlertDialog.Builder(this)
        normalDialog.setIcon(R.mipmap.ic_launcher)
        normalDialog.setTitle("下载")
        normalDialog.setMessage("有版本更新，请下载!")
        normalDialog.setPositiveButton(
            "确定"
        ) { dialog, which ->
            with(this@DownActivity)
                .setApkUrl("http://qiuyue.vicp.net:86/apk/app/release/$uploadfath")
                .setCallBack(downloadCallBack) //开始下载
                .startDownload()
        }
        normalDialog.setNegativeButton("取消") { dialog, which ->
        }
        // 显示
        normalDialog.show()
    }

    @SuppressLint("InflateParams")
    private fun showUpdateDialog(
        UpdateMsg: String,
        isForceUpdate: Boolean,
        downloadUrl: String
    ) {
        val builder = AlertDialog.Builder(this)
        val inflater = LayoutInflater.from(this)
        val updateView: View = inflater.inflate(R.layout.update_layout, null)
        val progressBar: NumberProgressBar = updateView.findViewById(R.id.tips_progress)
        val updateMsg = updateView.findViewById<TextView>(R.id.update_mess_txt)
        updateMsg.text = UpdateMsg
        builder.setTitle("发现新版本")
        var negativeBtnStr = "以后再说"
        if (isForceUpdate) {
            builder.setTitle("版本升级")
            negativeBtnStr = "退出应用"
        }
        builder.setView(updateView)
        builder.setNegativeButton(negativeBtnStr, null)
        builder.setPositiveButton(R.string.apk_update_yes, null)
        val downloadDialog = builder.create()
        downloadDialog.setCanceledOnTouchOutside(false)
        downloadDialog.setCancelable(false)
        downloadDialog.show()
        downloadDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener {
                if (isForceUpdate) {
                    progressBar.visibility = View.VISIBLE

                    DownloadInstaller(this, downloadUrl, true, object : DownloadProgressCallBack {
                        override fun downloadProgress(progress: Int) {
                            runOnUiThread { progressBar.progress = progress }
                            if (progress == 100) {
                                downloadDialog.dismiss()
                            }
                        }

                        override fun downloadException(e: java.lang.Exception?) {}
                        override fun onInstallStart() {
                            downloadDialog.dismiss()
                        }
                    }).start()

                    //升级按钮变灰色
                    downloadDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(Color.GRAY)
                } else {
                    DownloadInstaller(this, downloadUrl).start()
                    downloadDialog.dismiss()
                }
            }
        downloadDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            .setOnClickListener {
                if (isForceUpdate) {
                    this@DownActivity.finish()
                } else {
                    downloadDialog.dismiss()
                }
            }
    }

}