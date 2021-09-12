package io.qiuyue.app.ui.book.searchContent

import android.content.Context
import android.view.ViewGroup
import io.qiuyue.app.R
import io.qiuyue.app.base.adapter.ItemViewHolder
import io.qiuyue.app.base.adapter.RecyclerAdapter
import io.qiuyue.app.databinding.ItemSearchListBinding
import io.qiuyue.app.lib.theme.accentColor
import io.qiuyue.app.utils.getCompatColor
import io.qiuyue.app.utils.hexString


class SearchContentAdapter(context: Context, val callback: Callback) :
    RecyclerAdapter<SearchResult, ItemSearchListBinding>(context) {

    val cacheFileNames = hashSetOf<String>()
    val textColor = context.getCompatColor(R.color.primaryText).hexString.substring(2)
    val accentColor = context.accentColor.hexString.substring(2)

    override fun getViewBinding(parent: ViewGroup): ItemSearchListBinding {
        return ItemSearchListBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemSearchListBinding,
        item: SearchResult,
        payloads: MutableList<Any>
    ) {
        binding.run {
            val isDur = callback.durChapterIndex() == item.chapterIndex
            if (payloads.isEmpty()) {
                tvSearchResult.text = item.getHtmlCompat(textColor, accentColor)
                tvSearchResult.paint.isFakeBoldText = isDur
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemSearchListBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                callback.openSearchResult(it)
            }
        }
    }

    interface Callback {
        fun openSearchResult(searchResult: SearchResult)
        fun durChapterIndex(): Int
    }
}