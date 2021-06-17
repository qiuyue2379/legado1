package io.legado.app.ui.rss.read

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.tencent.smtt.sdk.WebView

class VisibleWebView(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(View.VISIBLE)
    }

}