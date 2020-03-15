package io.legado.app.ui.main.bookshelf.books

import android.content.Context
import android.os.Bundle
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.lib.theme.ATH
import io.legado.app.utils.invisible
import kotlinx.android.synthetic.main.item_bookshelf_grid.view.*
import org.jetbrains.anko.sdk27.listeners.onClick
import org.jetbrains.anko.sdk27.listeners.onLongClick

class BooksAdapterGrid(context: Context, private val callBack: CallBack) :
    BaseBooksAdapter(context, R.layout.item_bookshelf_grid) {

    override fun convert(holder: ItemViewHolder, item: Book, payloads: MutableList<Any>) {
        val bundle = payloads.getOrNull(0) as? Bundle
        with(holder.itemView) {
            if (bundle == null) {
                ATH.applyBackgroundTint(this)
                tv_name.text = item.name
                iv_cover.load(item.getDisplayCover(), item.name, item.author)
                if (item.origin != BookType.local && callBack.isUpdate(item.bookUrl)) {
                    bv_unread.invisible()
                    rl_loading.show()
                } else {
                    rl_loading.hide()
                    bv_unread.setBadgeCount(item.getUnreadChapterNum())
                    bv_unread.setHighlight(item.lastCheckCount > 0)
                }
            } else {
                bundle.keySet().map {
                    when (it) {
                        "name" -> tv_name.text = item.name
                        "cover" -> iv_cover.load(item.getDisplayCover(), item.name, item.author)
                        "refresh" -> if (item.origin != BookType.local && callBack.isUpdate(item.bookUrl)) {
                            bv_unread.invisible()
                            rl_loading.show()
                        } else {
                            rl_loading.hide()
                            bv_unread.setBadgeCount(item.getUnreadChapterNum())
                            bv_unread.setHighlight(item.lastCheckCount > 0)
                        }
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder) {
        holder.itemView.apply {
            onClick {
                getItem(holder.layoutPosition)?.let {
                    callBack.open(it)
                }
            }

            onLongClick {
                getItem(holder.layoutPosition)?.let {
                    callBack.openBookInfo(it)
                }
                true
            }
        }
    }
}