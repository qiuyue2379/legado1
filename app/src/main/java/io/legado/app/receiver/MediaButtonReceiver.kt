package io.legado.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import io.legado.app.App
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.Book
import io.legado.app.help.ActivityHelp
import io.legado.app.service.AudioPlayService
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.help.AudioPlay
import io.legado.app.service.help.ReadAloud
import io.legado.app.ui.audio.AudioPlayActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.postEvent
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Created by GKF on 2018/1/6.
 * 监听耳机键
 */

class MediaButtonReceiver : BroadcastReceiver() {

    companion object {

        fun handleIntent(context: Context, intent: Intent): Boolean {
            val intentAction = intent.action
            if (Intent.ACTION_MEDIA_BUTTON == intentAction) {
                val keyEvent =
                    intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                val keycode: Int = keyEvent.keyCode
                val action: Int = keyEvent.action
                if (action == KeyEvent.ACTION_DOWN) {
                    when (keycode) {
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            ReadAloud.prevParagraph(context)
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            ReadAloud.nextParagraph(context)
                        }
                        else -> readAloud(context)
                    }
                }
            }
            return true
        }

        private fun readAloud(context: Context) {
            when {
                BaseReadAloudService.isRun -> if (BaseReadAloudService.isPlay()) {
                    ReadAloud.pause(context)
                    AudioPlay.pause(context)
                } else {
                    ReadAloud.resume(context)
                    AudioPlay.resume(context)
                }
                AudioPlayService.isRun -> if (AudioPlayService.pause) {
                    AudioPlay.resume(context)
                } else {
                    AudioPlay.pause(context)
                }
                ActivityHelp.isExist(ReadBookActivity::class.java) ->
                    postEvent(EventBus.MEDIA_BUTTON, true)
                ActivityHelp.isExist(AudioPlayActivity::class.java) ->
                    postEvent(EventBus.MEDIA_BUTTON, true)
                else -> if (context.getPrefBoolean("mediaButtonOnExit", true)) {
                    GlobalScope.launch(Main) {
                        val lastBook: Book? = withContext(IO) {
                            App.db.bookDao.lastReadBook
                        }
                        lastBook?.let {
                            if (!ActivityHelp.isExist(MainActivity::class.java)) {
                                Intent(context, MainActivity::class.java).let {
                                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(it)
                                }
                            }
                            Intent(context, ReadBookActivity::class.java).let {
                                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                it.putExtra("readAloud", true)
                                context.startActivity(it)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (handleIntent(context, intent) && isOrderedBroadcast) {
            abortBroadcast()
        }
    }

}
