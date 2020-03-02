package io.legado.app.ui.update

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.ui.update.getVersion.getLocalVersionName
import com.maning.updatelibrary.InstallUtils
import com.maning.updatelibrary.InstallUtils.*
import okhttp3.*

import io.legado.app.R
import kotlinx.android.synthetic.main.activity_down.*

import org.jetbrains.anko.toast
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {
    private var downloadCallBack: DownloadCallBack? = null
    private var apkDownloadPath: String? = null
    lateinit var upload_fath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_down)
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

    private fun initCallBack() {
        downloadCallBack = object : DownloadCallBack {
            override fun onStart() {
                Log.i("TAG", "InstallUtils---onStart")
                tv_progress.setText("0%")
                tv_info.setText("正在下载...")
                btnDownload.setClickable(false)
                btnDownload.setBackgroundResource(R.color.colorAccent)
            }

            override fun onComplete(path: String) {
                Log.i("TAG", "InstallUtils---onComplete:$path")
                apkDownloadPath = path
                tv_progress.setText("100%")
                tv_info.setText("下载成功")
                btnDownload.setClickable(true)
                btnDownload.setBackgroundResource(R.color.colorPrimary)
                //先判断有没有安装权限
                checkInstallPermission(this@MainActivity, object : InstallPermissionCallBack {
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
                                        this@MainActivity,
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

            @RequiresApi(Build.VERSION_CODES.N)
            override fun onLoading(
                total: Long,
                current: Long
            ) { //内部做了处理，onLoading 进度转回progress必须是+1，防止频率过快
                Log.i(
                    "TAG", "InstallUtils----onLoading:-----total:$total,current:$current"
                )
                val progress = (current * 100 / total).toInt()

                onProgressUpdateBar.setVisibility(View.VISIBLE)
                tv_progress.setVisibility(View.VISIBLE)
                tv_info.setVisibility(View.VISIBLE)


                tv_progress.setText("$progress%")

                onProgressUpdateBar.setProgress(progress, true)


            }

            override fun onFail(e: Exception) {
                Log.i("TAG", "InstallUtils---onFail:" + e.message)
                tv_info.setText("下载失败:$e")
                btnDownload.setClickable(true)
                btnDownload.setBackgroundResource(R.color.colorPrimary)
            }

            override fun cancle() {
                Log.i("TAG", "InstallUtils---cancle")
                tv_info.setText("下载取消")
                btnDownload.setClickable(true)
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

            override fun onFail(e: Exception) {
                tv_info.setText("安装失败:$e")
            }
        })
    }


    private fun initView() {

        object : Thread() {
            override fun run() {
                val okHttpClient = OkHttpClient()

                val request = Request.Builder()
                    .url("http://qiuyue.vicp.net:85/apk/release/js.json")//请求的url
                    .get()
                    .build()

                //创建/Call
                val call = okHttpClient.newCall(request)

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                    }

                    @Throws(IOException::class)
                    override fun onResponse(call: Call, response: Response) {
                        val string = response.body?.string()
                        print(string)
                        if (string != null) {
                            try {
                                val res = JSONObject(string)
                                val status = res.getBoolean("status")
                                Looper.prepare()
                                //   toast(res.getString("msg"))
                                if (status) {
                                    val jsonArray = res.getJSONArray("apkData")
                                    for (i in 0 until jsonArray.length()) {
                                        val jsonObject: JSONObject = jsonArray.getJSONObject(i)
                                        upload_fath = jsonObject.getString("upload_fath")
                                        val version = jsonObject.getString("version")
                                        // toast(upload_fath)
                                        //   val a = "love23next234csdn3423javaeye"
                                        val regEx = "[^0-9]"
                                        val p: Pattern = Pattern.compile(regEx)
                                        val remoteVersion: Matcher = p.matcher(version)
                                        val localVersion: Matcher =
                                            p.matcher(getLocalVersionName(this@MainActivity))
                                        print(remoteVersion.toString())
                                        toast(remoteVersion.replaceAll("").trim())

                                        if (remoteVersion.replaceAll("").trim().toInt() > localVersion.replaceAll(
                                                ""
                                            ).trim().toInt()
                                        ) {
                                            //    toast("hahaahah")
                                            dialog()
                                        }
                                    }
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    private fun dialog() {
        val normalDialog = AlertDialog.Builder(this)
        normalDialog.setIcon(R.mipmap.ic_launcher)
        normalDialog.setTitle("下载")
        normalDialog.setMessage("更新说明")
        normalDialog.setPositiveButton(
            "确定"
        ) { dialog, which ->

            InstallUtils.with(this@MainActivity)
                //必须-下载地址
                .setApkUrl("http://qiuyue.vicp.net:85/apk/release/" + "${upload_fath}")
                //非必须-下载保存的文件的完整路径+name.apk
                //  .setApkPath(Constants.APK_SAVE_PATH)
                // 非必须-下载回调
                .setCallBack(downloadCallBack) //开始下载
                .startDownload()
        }
        normalDialog.setNegativeButton("取消") { dialog, which ->
        }
        // 显示
        normalDialog.show()
    }

}