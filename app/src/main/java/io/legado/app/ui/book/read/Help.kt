package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.AsyncTask
import android.os.Build
import android.view.*
import android.view.View.NO_ID
import android.widget.EditText
import io.legado.app.App
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.AppConfig
import io.legado.app.help.ReadBookConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.customView
import io.legado.app.lib.dialogs.noButton
import io.legado.app.lib.dialogs.yesButton
import io.legado.app.lib.theme.ATH
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.service.help.Download
import io.legado.app.service.help.ReadBook
import io.legado.app.utils.applyTint
import io.legado.app.utils.requestInputMethod
import kotlinx.android.synthetic.main.dialog_download_choice.view.*
import kotlinx.android.synthetic.main.dialog_edit_text.view.*
import org.jetbrains.anko.layoutInflater


object Help {

    private const val NAVIGATION = "navigationBarBackground"

    /**
     * 更新状态栏,导航栏
     */
    fun upSystemUiVisibility(activity: Activity, toolBarHide: Boolean = true) {
        var flag = (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        if (ReadBookConfig.hideNavigationBar) {
            flag = flag or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (toolBarHide) {
            if (ReadBookConfig.hideStatusBar) {
                flag = flag or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
            if (ReadBookConfig.hideNavigationBar) {
                flag = flag or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            }
        }
        activity.window.decorView.systemUiVisibility = flag
        if (toolBarHide) {
            ATH.setLightStatusBar(activity, ReadBookConfig.durConfig.statusIconDark())
        } else {
            ATH.setLightStatusBarAuto(
                activity,
                ThemeStore.statusBarColor(activity, AppConfig.isTransparentStatusBar)
            )
        }
    }


    @SuppressLint("SourceLockedOrientationActivity")
    fun setOrientation(activity: Activity) = activity.apply {
        when (AppConfig.requestedDirection) {
            "0" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            "1" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "2" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "3" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }


    /**
     * 返回NavigationBar是否存在
     * 该方法需要在View完全被绘制出来之后调用，否则判断不了
     * 在比如 onWindowFocusChanged（）方法中可以得到正确的结果
     */
    fun isNavigationBarExist(activity: Activity?): Boolean {
        activity?.let {
            val vp = it.window.decorView as? ViewGroup
            if (vp != null) {
                for (i in 0 until vp.childCount) {
                    vp.getChildAt(i).context.packageName
                    if (vp.getChildAt(i).id != NO_ID
                        && NAVIGATION == activity.resources.getResourceEntryName(vp.getChildAt(i).id)
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * 保持亮屏
     */
    fun keepScreenOn(window: Window, on: Boolean) {
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * 适配刘海
     */
    fun upLayoutInDisplayCutoutMode(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && AppConfig.readBodyToLh) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    @SuppressLint("InflateParams")
    fun showDownloadDialog(context: Context) {
        ReadBook.book?.let { book ->
            context.alert(titleResource = R.string.download_offline) {
                var view: View? = null
                customView {
                    LayoutInflater.from(context).inflate(R.layout.dialog_download_choice, null)
                        .apply {
                            view = this
                            edit_start.setText(book.durChapterIndex.toString())
                            edit_end.setText(book.totalChapterNum.toString())
                        }
                }
                yesButton {
                    view?.apply {
                        val start = edit_start?.text?.toString()?.toInt() ?: 0
                        val end = edit_end?.text?.toString()?.toInt() ?: book.totalChapterNum
                        Download.start(context, book.bookUrl, start, end)
                    }
                }
                noButton()
            }.show().applyTint()
        }
    }

    @SuppressLint("InflateParams")
    fun showBookMark(context: Context) = with(context) {
        val book = ReadBook.book ?: return
        val textChapter = ReadBook.curTextChapter ?: return
        context.alert(title = getString(R.string.bookmark_add)) {
            var editText: EditText? = null
            message = book.name + " • " + textChapter.title
            customView {
                layoutInflater.inflate(R.layout.dialog_edit_text, null).apply {
                    editText = edit_view.apply {
                        hint = "备注内容"
                    }
                }
            }
            yesButton {
                editText?.text?.toString()?.let { editContent ->
                    AsyncTask.execute {
                        val bookmark = Bookmark(
                            book.durChapterTime,
                            book.bookUrl,
                            book.name,
                            ReadBook.durChapterIndex,
                            ReadBook.durPageIndex,
                            textChapter.title,
                            editContent
                        )
                        App.db.bookmarkDao().insert(bookmark)
                    }
                }
            }
            noButton()
        }.show().applyTint().requestInputMethod()
    }
}