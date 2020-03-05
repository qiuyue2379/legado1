package io.legado.app.ui.widget.text

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import io.legado.app.R
import io.legado.app.lib.theme.ATH
import io.legado.app.utils.gone
import io.legado.app.utils.visible
import kotlinx.android.synthetic.main.item_1line_text_and_del.view.*
import org.jetbrains.anko.sdk27.listeners.onClick


class AutoCompleteTextView : AppCompatAutoCompleteTextView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var delCallBack: ((value: String) -> Unit)? = null

    init {
        ATH.applyAccentTint(this)
    }

    override fun enoughToFilter(): Boolean {
        return true
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            showDropDown()
        }
        return super.onTouchEvent(event)
    }

    fun setFilterValues(values: List<String>?) {
        values?.let {
            setAdapter(MyAdapter(context, values))
        }
    }

    fun setFilterValues(vararg value: String) {
        setAdapter(MyAdapter(context, value.toMutableList()))
    }

    inner class MyAdapter(context: Context, values: List<String>) :
        ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, values) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_1line_text_and_del, parent, false)
            view.text_view.text = getItem(position)
            if (delCallBack != null) view.iv_delete.visible() else view.iv_delete.gone()
            view.iv_delete.onClick {
                getItem(position)?.let {
                    remove(it)
                    delCallBack?.invoke(it)
                    showDropDown()
                }
            }
            return view
        }
    }

}
