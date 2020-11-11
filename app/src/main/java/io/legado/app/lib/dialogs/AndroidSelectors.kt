@file:Suppress("NOTHING_TO_INLINE", "unused")

package io.legado.app.lib.dialogs

import android.content.Context
import android.content.DialogInterface
import androidx.fragment.app.Fragment

inline fun Fragment.selector(
    title: CharSequence? = null,
    items: List<CharSequence>,
    noinline onClick: (DialogInterface, Int) -> Unit
) = activity?.selector(title, items, onClick)

fun Context.selector(
    title: CharSequence? = null,
    items: List<CharSequence>,
    onClick: (DialogInterface, Int) -> Unit
) {
    with(AndroidAlertBuilder(this)) {
        if (title != null) {
            this.title = title
        }
        items(items, onClick)
        show()
    }
}

fun Context.selector(
    titleSource: Int? = null,
    items: List<CharSequence>,
    onClick: (DialogInterface, Int) -> Unit
) {
    with(AndroidAlertBuilder(this)) {
        if (titleSource != null) {
            this.title = getString(titleSource)
        }
        items(items, onClick)
        show()
    }
}
