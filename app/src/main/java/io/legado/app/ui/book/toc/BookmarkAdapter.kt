package io.legado.app.ui.book.toc

import android.content.Context
import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.ItemBookmarkBinding
import splitties.views.onLongClick

class BookmarkAdapter(context: Context, val callback: Callback) :
    RecyclerAdapter<Bookmark, ItemBookmarkBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemBookmarkBinding {
        return ItemBookmarkBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookmarkBinding,
        item: Bookmark,
        payloads: MutableList<Any>
    ) {
        binding.tvChapterName.text = item.chapterName
        binding.tvBookText.text = item.bookText
        binding.tvContent.text = item.content
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookmarkBinding) {
        getItem(holder.layoutPosition)?.let { bookmark ->
            binding.root.setOnClickListener {
                callback.onClick(bookmark)
            }
            binding.root.onLongClick {
                callback.onLongClick(bookmark)
            }
        }
    }

    interface Callback {
        fun onClick(bookmark: Bookmark)
        fun onLongClick(bookmark: Bookmark)
    }

}