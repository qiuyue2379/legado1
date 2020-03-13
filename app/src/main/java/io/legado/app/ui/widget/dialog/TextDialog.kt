package io.legado.app.ui.widget.dialog

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import kotlinx.android.synthetic.main.dialog_text_view.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.noties.markwon.Markwon


class TextDialog : BaseDialogFragment() {

    companion object {
        const val MD = 1

        fun show(
            fragmentManager: FragmentManager,
            content: String?,
            mode: Int = 0,
            time: Long = 0
        ) {
            TextDialog().apply {
                val bundle = Bundle()
                bundle.putString("content", content)
                bundle.putInt("mode", mode)
                bundle.putLong("time", time)
                arguments = bundle
                isCancelable = false
            }.show(fragmentManager, "textDialog")
        }

    }

    private var time = 0L

    override fun onStart() {
        super.onStart()
        val dm = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(dm)
        dialog?.window?.setLayout((dm.widthPixels * 0.9).toInt(), (dm.heightPixels * 0.9).toInt())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_text_view, container)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.let {
            val content = it.getString("content") ?: ""
            when (it.getInt("mode")) {
                MD -> text_view.post {
                    Markwon.create(requireContext())
                        .setMarkdown(
                            text_view,
                            content
                        )
                }
                else -> text_view.text = content
            }
            time = it.getLong("time", 0L)
        }
        if (time > 0) {
            badge_view.setBadgeCount((time / 1000).toInt())
            launch {
                while (time > 0) {
                    delay(1000)
                    time -= 1000
                    badge_view.setBadgeCount((time / 1000).toInt())
                    if (time <= 0) {
                        dialog?.setCancelable(true)
                    }
                }
            }
        }
    }

}
