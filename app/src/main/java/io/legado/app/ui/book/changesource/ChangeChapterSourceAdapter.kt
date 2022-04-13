package io.legado.app.ui.book.changesource

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import io.legado.app.R
import io.legado.app.base.adapter.DiffRecyclerAdapter
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ItemChangeSourceBinding
import io.legado.app.utils.invisible
import io.legado.app.utils.visible
import splitties.views.onLongClick


class ChangeChapterSourceAdapter(
    context: Context,
    val viewModel: ChangeChapterSourceViewModel,
    val callBack: CallBack
) : DiffRecyclerAdapter<SearchBook, ItemChangeSourceBinding>(context) {

    override val diffItemCallback = object : DiffUtil.ItemCallback<SearchBook>() {
        override fun areItemsTheSame(oldItem: SearchBook, newItem: SearchBook): Boolean {
            return oldItem.bookUrl == newItem.bookUrl
        }

        override fun areContentsTheSame(oldItem: SearchBook, newItem: SearchBook): Boolean {
            return oldItem.originName == newItem.originName
                    && oldItem.getDisplayLastChapterTitle() == newItem.getDisplayLastChapterTitle()
        }

    }

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
                tvAuthor.text = item.author
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
                        "upCurSource" -> if (callBack.bookUrl == item.bookUrl) {
                            ivChecked.visible()
                        } else {
                            ivChecked.invisible()
                        }
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemChangeSourceBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.openToc(it)
            }
        }
        holder.itemView.onLongClick {
            showMenu(holder.itemView, getItem(holder.layoutPosition))
        }
    }

    private fun showMenu(view: View, searchBook: SearchBook?) {
        searchBook ?: return
        val popupMenu = PopupMenu(context, view)
        popupMenu.inflate(R.menu.change_source_item)
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_top_source -> {
                    callBack.topSource(searchBook)
                }
                R.id.menu_bottom_source -> {
                    callBack.bottomSource(searchBook)
                }
                R.id.menu_edit_source -> {
                    callBack.editSource(searchBook)
                }
                R.id.menu_disable_source -> {
                    callBack.disableSource(searchBook)
                }
                R.id.menu_delete_source -> {
                    callBack.deleteSource(searchBook)
                    updateItems(0, itemCount, listOf<Int>())
                }
            }
            true
        }
        popupMenu.show()
    }

    interface CallBack {
        val bookUrl: String?
        fun openToc(searchBook: SearchBook)
        fun topSource(searchBook: SearchBook)
        fun bottomSource(searchBook: SearchBook)
        fun editSource(searchBook: SearchBook)
        fun disableSource(searchBook: SearchBook)
        fun deleteSource(searchBook: SearchBook)
    }
}