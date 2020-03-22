package io.legado.app.ui.book.arrange

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.SimpleRecyclerAdapter
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.ItemTouchCallback
import io.legado.app.lib.theme.backgroundColor
import kotlinx.android.synthetic.main.item_arrange_book.view.*
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.sdk27.listeners.onClick
import java.util.*

class ArrangeBookAdapter(context: Context, val callBack: CallBack) :
    SimpleRecyclerAdapter<Book>(context, R.layout.item_arrange_book),
    ItemTouchCallback.OnItemTouchCallbackListener {
    val groupRequestCode = 12
    private val selectedBooks: HashSet<Book> = hashSetOf()
    var actionItem: Book? = null

    fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            getItems().forEach {
                selectedBooks.add(it)
            }
        } else {
            selectedBooks.clear()
        }
        notifyDataSetChanged()
        callBack.upSelectCount()
    }

    fun revertSelection() {
        getItems().forEach {
            if (selectedBooks.contains(it)) {
                selectedBooks.remove(it)
            } else {
                selectedBooks.add(it)
            }
        }
        notifyDataSetChanged()
        callBack.upSelectCount()
    }

    fun selectedBooks(): Array<Book> {
        val books = arrayListOf<Book>()
        selectedBooks.forEach {
            if (getItems().contains(it)) {
                books.add(it)
            }
        }
        return books.toTypedArray()
    }

    override fun convert(holder: ItemViewHolder, item: Book, payloads: MutableList<Any>) {
        with(holder.itemView) {
            backgroundColor = context.backgroundColor
            tv_name.text = item.name
            tv_author.text = item.author
            tv_author.visibility = if (item.author.isEmpty()) View.GONE else View.VISIBLE
            tv_group_s.text = getGroupName(item.group)
            checkbox.isChecked = selectedBooks.contains(item)
        }
    }

    override fun registerListener(holder: ItemViewHolder) {
        holder.itemView.apply {
            checkbox.setOnCheckedChangeListener { buttonView, isChecked ->
                getItem(holder.layoutPosition)?.let {
                    if (buttonView.isPressed) {
                        if (isChecked) {
                            selectedBooks.add(it)
                        } else {
                            selectedBooks.remove(it)
                        }
                        callBack.upSelectCount()
                    }

                }
            }
            onClick {
                getItem(holder.layoutPosition)?.let {
                    checkbox.isChecked = !checkbox.isChecked
                    if (checkbox.isChecked) {
                        selectedBooks.add(it)
                    } else {
                        selectedBooks.remove(it)
                    }
                    callBack.upSelectCount()
                }
            }
            tv_delete.onClick {
                getItem(holder.layoutPosition)?.let {
                    callBack.deleteBook(it)
                }
            }
            tv_group.onClick {
                getItem(holder.layoutPosition)?.let {
                    actionItem = it
                    callBack.selectGroup(it.group, groupRequestCode)
                }
            }
        }
    }

    private fun getGroupList(groupId: Int): List<String> {
        val groupNames = arrayListOf<String>()
        callBack.groupList.forEach {
            if (it.groupId and groupId > 0) {
                groupNames.add(it.groupName)
            }
        }
        return groupNames
    }

    private fun getGroupName(groupId: Int): String {
        val groupNames = getGroupList(groupId)
        if (groupNames.isEmpty()) {
            return ""
        }
        return groupNames.joinToString(",")
    }

    private var isMoved = false

    override fun onMove(srcPosition: Int, targetPosition: Int): Boolean {
        val srcItem = getItem(srcPosition)
        val targetItem = getItem(targetPosition)
        Collections.swap(getItems(), srcPosition, targetPosition)
        notifyItemMoved(srcPosition, targetPosition)
        if (srcItem != null && targetItem != null) {
            if (srcItem.order == targetItem.order) {
                for ((index, item) in getItems().withIndex()) {
                    item.order = index + 1
                }
            } else {
                val pos = srcItem.order
                srcItem.order = targetItem.order
                targetItem.order = pos
            }
        }
        isMoved = true
        return true
    }

    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (isMoved) {
            callBack.updateBook(*getItems().toTypedArray())
        }
        isMoved = false
    }

    interface CallBack {
        val groupList: List<BookGroup>
        fun upSelectCount()
        fun updateBook(vararg book: Book)
        fun deleteBook(book: Book)
        fun selectGroup(groupId: Int, requestCode: Int)
    }
}