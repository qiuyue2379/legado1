package io.legado.app.ui.book.changesource

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.SimpleRecyclerAdapter
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ItemChangeSourceBinding
import io.legado.app.utils.invisible
import io.legado.app.utils.visible
import org.jetbrains.anko.sdk27.listeners.onClick
import org.jetbrains.anko.sdk27.listeners.onLongClick


class ChangeSourceAdapter(context: Context, val callBack: CallBack) :
    SimpleRecyclerAdapter<SearchBook, ItemChangeSourceBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemChangeSourceBinding {
        return ItemChangeSourceBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemChangeSourceBinding,
        item: SearchBook,
        payloads: MutableList<Any>
    ) {
        val bundle = payloads.getOrNull(0) as? Bundle
        binding.apply {
            if (bundle == null) {
                tvOrigin.text = item.originName
                tvLast.text = item.getDisplayLastChapterTitle()
                if (callBack.bookUrl == item.bookUrl) {
                    ivChecked.visible()
                } else {
                    ivChecked.invisible()
                }
            } else {
                bundle.keySet().map {
                    when (it) {
                        "name" -> tvOrigin.text = item.originName
                        "latest" -> tvLast.text = item.getDisplayLastChapterTitle()
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemChangeSourceBinding) {
        holder.itemView.onClick {
            getItem(holder.layoutPosition)?.let {
                callBack.changeTo(it)
            }
        }
        holder.itemView.onLongClick {
            showMenu(holder.itemView, getItem(holder.layoutPosition))
            true
        }
    }

    private fun showMenu(view: View, searchBook: SearchBook?) {
        searchBook ?: return
        val popupMenu = PopupMenu(context, view)
        popupMenu.inflate(R.menu.change_source_item)
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_disable_book_source -> {
                    callBack.disableSource(searchBook)
                }
            }
            true
        }
        popupMenu.show()
    }

    interface CallBack {
        val bookUrl: String?
        fun changeTo(searchBook: SearchBook)
        fun disableSource(searchBook: SearchBook)
    }
}