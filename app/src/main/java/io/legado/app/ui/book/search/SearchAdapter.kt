package io.legado.app.ui.book.search

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.SimpleRecyclerAdapter
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ItemSearchBinding
import io.legado.app.utils.gone
import io.legado.app.utils.visible
import org.jetbrains.anko.sdk27.listeners.onClick

class SearchAdapter(context: Context, val callBack: CallBack) :
    SimpleRecyclerAdapter<SearchBook, ItemSearchBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemSearchBinding {
        return ItemSearchBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemSearchBinding,
        item: SearchBook,
        payloads: MutableList<Any>
    ) {
        val bundle = payloads.getOrNull(0) as? Bundle
        if (bundle == null) {
            bind(binding, item)
        } else {
            bindChange(binding, item, bundle)
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemSearchBinding) {
        holder.itemView.apply {
            onClick {
                getItem(holder.layoutPosition)?.let {
                    callBack.showBookInfo(it.name, it.author)
                }
            }
        }
    }

    private fun bind(binding: ItemSearchBinding, searchBook: SearchBook) {
        with(binding) {
            tvName.text = searchBook.name
            tvAuthor.text = context.getString(R.string.author_show, searchBook.author)
            bvOriginCount.setBadgeCount(searchBook.origins.size)
            upLasted(binding, searchBook.latestChapterTitle)
            if (searchBook.intro.isNullOrEmpty()) {
                tvIntroduce.text =
                    context.getString(R.string.intro_show_null)
            } else {
                tvIntroduce.text =
                    context.getString(R.string.intro_show, searchBook.intro)
            }
            upKind(binding, searchBook.getKindList())
            ivCover.load(searchBook.coverUrl, searchBook.name, searchBook.author)

        }
    }

    private fun bindChange(binding: ItemSearchBinding, searchBook: SearchBook, bundle: Bundle) {
        with(binding) {
            bundle.keySet().map {
                when (it) {
                    "name" -> tvName.text = searchBook.name
                    "author" -> tvAuthor.text =
                        context.getString(R.string.author_show, searchBook.author)
                    "origins" -> bvOriginCount.setBadgeCount(searchBook.origins.size)
                    "last" -> upLasted(binding, searchBook.latestChapterTitle)
                    "intro" -> {
                        if (searchBook.intro.isNullOrEmpty()) {
                            tvIntroduce.text =
                                context.getString(R.string.intro_show_null)
                        } else {
                            tvIntroduce.text =
                                context.getString(R.string.intro_show, searchBook.intro)
                        }
                    }
                    "kind" -> upKind(binding, searchBook.getKindList())
                    "cover" -> ivCover.load(
                        searchBook.coverUrl,
                        searchBook.name,
                        searchBook.author
                    )
                }
            }
        }
    }

    private fun upLasted(binding: ItemSearchBinding, latestChapterTitle: String?) {
        with(binding) {
            if (latestChapterTitle.isNullOrEmpty()) {
                tvLasted.gone()
            } else {
                tvLasted.text =
                    context.getString(R.string.lasted_show, latestChapterTitle)
                tvLasted.visible()
            }
        }
    }

    private fun upKind(binding: ItemSearchBinding, kinds: List<String>) = with(binding) {
        if (kinds.isEmpty()) {
            llKind.gone()
        } else {
            llKind.visible()
            llKind.setLabels(kinds)
        }
    }

    interface CallBack {
        fun showBookInfo(name: String, author: String)
    }
}