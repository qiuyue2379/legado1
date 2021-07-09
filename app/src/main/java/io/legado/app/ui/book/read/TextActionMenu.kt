package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.LayoutInflater
import android.view.Menu
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.RequiresApi
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import io.legado.app.R
import io.legado.app.databinding.ItemTextBinding
import io.legado.app.databinding.PopupActionMenuBinding
import io.legado.app.service.BaseReadAloudService
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import splitties.views.onClick
import java.util.*

@SuppressLint("RestrictedApi")
class TextActionMenu(private val context: Context, private val callBack: CallBack) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    TextToSpeech.OnInitListener {

    private val binding = PopupActionMenuBinding.inflate(LayoutInflater.from(context))
    private val menu = MenuBuilder(context)
    private val moreMenu = MenuBuilder(context)
    private val ttsListener by lazy {
        TTSUtteranceListener()
    }

    init {
        @SuppressLint("InflateParams")
        contentView = binding.root

        isTouchable = true
        isOutsideTouchable = false
        isFocusable = false

        SupportMenuInflater(context).inflate(R.menu.content_select_action, menu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            onInitializeMenu(moreMenu)
        }
        val menuItems = menu.visibleItems + moreMenu.visibleItems
        menuItems.forEach {
            val textView = ItemTextBinding.inflate(LayoutInflater.from(context)).root.apply {
                tag = it
                text = it.title
                onClick {
                    if (!callBack.onMenuItemSelected(it.itemId)) {
                        onMenuItemSelected(it)
                    }
                    callBack.onMenuActionFinally()
                }
            }
            binding.root.addView(textView)
        }
    }

    private fun onMenuItemSelected(item: MenuItemImpl) {
        when (item.itemId) {
            R.id.menu_copy -> context.sendToClip(callBack.selectedText)
            R.id.menu_share_str -> context.share(callBack.selectedText)
            R.id.menu_aloud -> {
                if (BaseReadAloudService.isRun) {
                    context.toastOnUi(R.string.alouding_disable)
                    return
                }
                readAloud(callBack.selectedText)
            }
            R.id.menu_browser -> {
                kotlin.runCatching {
                    val intent = if (callBack.selectedText.isAbsUrl()) {
                        Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(callBack.selectedText)
                        }
                    } else {
                        Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, callBack.selectedText)
                        }
                    }
                    context.startActivity(intent)
                }.onFailure {
                    it.printStackTrace()
                    context.toastOnUi(it.localizedMessage ?: "ERROR")
                }
            }
            else -> item.intent?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    it.putExtra(Intent.EXTRA_PROCESS_TEXT, callBack.selectedText)
                    context.startActivity(it)
                }
            }
        }
    }

    private var textToSpeech: TextToSpeech? = null
    private var ttsInitFinish = false
    private var lastText: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    private fun readAloud(text: String) {
        lastText = text
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(context, this).apply {
                setOnUtteranceProgressListener(ttsListener)
            }
            return
        }
        if (!ttsInitFinish) return
        if (text == "") return
        if (textToSpeech?.isSpeaking == true)
            textToSpeech?.stop()
        textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, "select_text")
        lastText = ""
    }

    @Synchronized
    override fun onInit(status: Int) {
        textToSpeech?.language = Locale.CHINA
        ttsInitFinish = true
        readAloud(lastText)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntent(): Intent {
        return Intent()
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSupportedActivities(): List<ResolveInfo> {
        return context.packageManager
            .queryIntentActivities(createProcessTextIntent(), 0)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntentForResolveInfo(info: ResolveInfo): Intent {
        return createProcessTextIntent()
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            .setClassName(info.activityInfo.packageName, info.activityInfo.name)
    }

    /**
     * Start with a menu Item order value that is high enough
     * so that your "PROCESS_TEXT" menu items appear after the
     * standard selection menu items like Cut, Copy, Paste.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun onInitializeMenu(menu: Menu) {
        kotlin.runCatching {
            var menuItemOrder = 100
            for (resolveInfo in getSupportedActivities()) {
                menu.add(
                    Menu.NONE, Menu.NONE,
                    menuItemOrder++, resolveInfo.loadLabel(context.packageManager)
                ).intent = createProcessTextIntentForResolveInfo(resolveInfo)
            }
        }.onFailure {
            context.toastOnUi("获取文字操作菜单出错:${it.localizedMessage}")
        }
    }

    private inner class TTSUtteranceListener : UtteranceProgressListener() {

        override fun onStart(utteranceId: String?) {

        }

        override fun onDone(utteranceId: String?) {
            textToSpeech?.shutdown()
            textToSpeech = null
        }

        override fun onError(utteranceId: String?) {

        }
    }

    interface CallBack {
        val selectedText: String

        fun onMenuItemSelected(itemId: Int): Boolean

        fun onMenuActionFinally()
    }
}