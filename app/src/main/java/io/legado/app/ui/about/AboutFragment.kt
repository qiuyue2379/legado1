package io.legado.app.ui.about

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import constant.UiType
import io.legado.app.R
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.help.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.*
import listener.OnInitUiListener
import model.UiConfig
import model.UpdateConfig
import okhttp3.*
import update.UpdateAppUtils
import java.io.IOException


class AboutFragment : PreferenceFragmentCompat() {
    private val licenseUrl = "https://github.com/gedoor/legado/blob/master/LICENSE"
    private val disclaimerUrl = "https://gedoor.github.io/MyBookshelf/disclaimer.html"
    private val qqGroups = linkedMapOf(
        Pair("(QQ群VIP1)701903217", "-iolizL4cbJSutKRpeImHlXlpLDZnzeF"),
        Pair("(QQ群VIP2)263949160", "xwfh7_csb2Gf3Aw2qexEcEtviLfLfd4L"),
        Pair("(QQ群1)805192012", "6GlFKjLeIk5RhQnR3PNVDaKB6j10royo"),
        Pair("(QQ群2)773736122", "5Bm5w6OgLupXnICbYvbgzpPUgf0UlsJF"),
        Pair("(QQ群3)981838750", "g_Sgmp2nQPKqcZQ5qPcKLHziwX_mpps9"),
        Pair("(QQ群4)256929088", "czEJPLDnT4Pd9SKQ6RoRVzKhDxLchZrO"),
        Pair("(QQ群5)811843556", "zKZ2UYGZ7o5CzcA6ylxzlqi21si_iqaX")
    )

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.about)
        findPreference<Preference>("update_log")?.summary =
                "${getString(R.string.version)} ${appInfo.versionName}"
        if (AppConfig.isGooglePlay) {
            preferenceScreen.removePreferenceRecursively("check_update")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.overScrollMode = View.OVER_SCROLL_NEVER
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        when (preference?.key) {
            "contributors" -> openUrl(R.string.contributors_url)
            "update_log" -> showUpdateLog()
            "check_update" -> shopUpdate()
            "mail" -> requireContext().sendMail("kunfei.ge@gmail.com")
            "sourceRuleSummary" -> openUrl(R.string.source_rule_url)
            "git" -> openUrl(R.string.this_github_url)
            "home_page" -> openUrl(R.string.home_page_url)
            "license" -> requireContext().openUrl(licenseUrl)
            "disclaimer" -> requireContext().openUrl(disclaimerUrl)
            "qq" -> showQqGroups()
            "gzGzh" -> requireContext().sendToClip(getString(R.string.legado_gzh))
            "crashLog" -> showCrashLogs()
            "tg" -> openUrl(R.string.tg_url)
            "discord" -> openUrl(R.string.discord_url)
        }
        return super.onPreferenceTreeClick(preference)
    }

    @Suppress("SameParameterValue")
    private fun openUrl(@StringRes addressID: Int) {
        requireContext().openUrl(getString(addressID))
    }

    private fun showUpdateLog() {
        val log = String(requireContext().assets.open("updateLog.md").readBytes())
        TextDialog.show(childFragmentManager, log, TextDialog.MD)
    }

    private fun showQqGroups() {
        alert(titleResource = R.string.join_qq_group) {
            val names = arrayListOf<String>()
            qqGroups.forEach {
                names.add(it.key)
            }
            items(names) { _, index ->
                qqGroups[names[index]]?.let {
                    if (!joinQQGroup(it)) {
                        requireContext().sendToClip(it)
                    }
                }
            }
        }.show()
    }

    private fun joinQQGroup(key: String): Boolean {
        val intent = Intent()
        intent.data =
            Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D$key")
        // 此Flag可根据具体产品需要自定义，如设置，则在加群界面按返回，返回手Q主界面，不设置，按返回会返回到呼起产品界面
        // intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        kotlin.runCatching {
            startActivity(intent)
            return true
        }.onFailure {
            toastOnUi("添加失败,请手动添加")
        }
        return false
    }

    private fun showCrashLogs() {
        context?.externalCacheDir?.let { exCacheDir ->
            val crashDir = FileUtils.getFile(exCacheDir, "crash")
            val crashLogs = crashDir.listFiles()
            val crashLogNames = arrayListOf<String>()
            crashLogs?.forEach {
                crashLogNames.add(it.name)
            }
            context?.selector(R.string.crash_log, crashLogNames) { _, select ->
                crashLogs?.getOrNull(select)?.let { logFile ->
                    TextDialog.show(childFragmentManager, logFile.readText())
                }
            }
        }
    }

    private fun shopUpdate() {
        object : Thread() {
            override fun run() {
                val okHttpClient = OkHttpClient()
                val request = Request.Builder()
                    .url("http://qiu-yue.top:86/apk/app/release/output-metadata.json")//请求的url
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
                            kotlin.runCatching {
                                val beat: JsonObject = JsonParser.parseString(string).asJsonObject
                                val assets: JsonArray = beat.get("elements").asJsonArray
                                Looper.prepare()
                                val version = assets[0].asJsonObject["versionName"].asString
                                val uploadfath = assets[0].asJsonObject["outputFile"].asString
                                val dirName = "有版本更新，请下载!"
                                UpdateAppUtils
                                    .getInstance()
                                    .apkUrl("http://qiu-yue.top:86/apk/app/release/$uploadfath")
                                    .updateTitle("发现新版本")
                                    .updateContent(dirName)
                                    .updateConfig(UpdateConfig(alwaysShowDownLoadDialog = true))
                                    .uiConfig(
                                        UiConfig(
                                            uiType = UiType.CUSTOM,
                                            customLayoutId = R.layout.view_update_dialog_custom
                                        )
                                    )
                                    .setOnInitUiListener(object : OnInitUiListener {
                                        @SuppressLint("SetTextI18n")
                                        override fun onInitUpdateUi(
                                            view: View?,
                                            updateConfig: UpdateConfig,
                                            uiConfig: UiConfig
                                        ) {
                                            view?.findViewById<TextView>(R.id.tv_update_title)?.text =
                                                "版本更新啦"
                                            view?.findViewById<TextView>(R.id.tv_version_name)?.text =
                                                version
                                        }
                                    })
                                    .update()

                                Looper.loop()
                            }.onFailure {
                                it.printStackTrace()
                            }
                        }
                    }
                })

            }
        }.start()
    }

}