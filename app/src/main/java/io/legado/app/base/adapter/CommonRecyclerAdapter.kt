package io.legado.app.base.adapter

import android.content.Context
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import java.util.*

/**
 * Created by Invincible on 2017/11/24.
 *
 * 通用的adapter 可添加header，footer，以及不同类型item
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class CommonRecyclerAdapter<ITEM, VB : ViewBinding>(protected val context: Context) :
    RecyclerView.Adapter<ItemViewHolder>() {

    constructor(context: Context, vararg delegates: ItemViewDelegate<ITEM, VB>) : this(context) {
        addItemViewDelegates(*delegates)
    }

    constructor(
        context: Context,
        vararg delegates: Pair<Int, ItemViewDelegate<ITEM, VB>>
    ) : this(context) {
        addItemViewDelegates(*delegates)
    }

    val inflater: LayoutInflater = LayoutInflater.from(context)

    private val headerItems: SparseArray<(parent: ViewGroup) -> ViewBinding> by lazy { SparseArray() }
    private val footerItems: SparseArray<(parent: ViewGroup) -> ViewBinding> by lazy { SparseArray() }

    private val itemDelegates: HashMap<Int, ItemViewDelegate<ITEM, VB>> = hashMapOf()
    private val items: MutableList<ITEM> = mutableListOf()

    private val lock = Object()

    private var itemClickListener: ((holder: ItemViewHolder, item: ITEM) -> Unit)? = null
    private var itemLongClickListener: ((holder: ItemViewHolder, item: ITEM) -> Boolean)? = null

    // 这个用Kotlin的setter就行了, 不需要手动开一个函数进行设置
    var itemAnimation: ItemAnimation? = null

    fun setOnItemClickListener(listener: (holder: ItemViewHolder, item: ITEM) -> Unit) {
        itemClickListener = listener
    }

    fun setOnItemLongClickListener(listener: (holder: ItemViewHolder, item: ITEM) -> Boolean) {
        itemLongClickListener = listener
    }

    fun bindToRecyclerView(recyclerView: RecyclerView) {
        recyclerView.adapter = this
    }

    fun <DELEGATE : ItemViewDelegate<ITEM, VB>> addItemViewDelegate(
        viewType: Int,
        delegate: DELEGATE
    ) {
        itemDelegates[viewType] = delegate
    }

    fun addItemViewDelegate(delegate: ItemViewDelegate<ITEM, VB>) {
        itemDelegates[itemDelegates.size] = delegate
    }

    fun addItemViewDelegates(vararg delegates: ItemViewDelegate<ITEM, VB>) {
        delegates.forEach {
            addItemViewDelegate(it)
        }
    }

    fun addItemViewDelegates(vararg delegates: Pair<Int, ItemViewDelegate<ITEM, VB>>) =
        delegates.forEach {
            addItemViewDelegate(it.first, it.second)
        }

    fun addHeaderView(header: ((parent: ViewGroup) -> ViewBinding)) {
        synchronized(lock) {
            val index = headerItems.size()
            headerItems.put(TYPE_HEADER_VIEW + headerItems.size(), header)
            notifyItemInserted(index)
        }
    }

    fun addFooterView(footer: ((parent: ViewGroup) -> ViewBinding)) =
        synchronized(lock) {
            val index = getActualItemCount() + footerItems.size()
            footerItems.put(TYPE_FOOTER_VIEW + footerItems.size(), footer)
            notifyItemInserted(index)
        }


    fun removeHeaderView(header: ((parent: ViewGroup) -> ViewBinding)) =
        synchronized(lock) {
            val index = headerItems.indexOfValue(header)
            if (index >= 0) {
                headerItems.remove(index)
                notifyItemRemoved(index)
            }
        }

    fun removeFooterView(footer: ((parent: ViewGroup) -> ViewBinding)) =
        synchronized(lock) {
            val index = footerItems.indexOfValue(footer)
            if (index >= 0) {
                footerItems.remove(index)
                notifyItemRemoved(getActualItemCount() + index - 2)
            }
        }

    fun setItems(items: List<ITEM>?) {
        synchronized(lock) {
            if (this.items.isNotEmpty()) {
                this.items.clear()
            }
            if (items != null) {
                this.items.addAll(items)
            }
            notifyDataSetChanged()
        }
    }

    fun setItems(items: List<ITEM>?, diffResult: DiffUtil.DiffResult) {
        synchronized(lock) {
            if (this.items.isNotEmpty()) {
                this.items.clear()
            }
            if (items != null) {
                this.items.addAll(items)
            }
            diffResult.dispatchUpdatesTo(this)
        }
    }

    fun setItem(position: Int, item: ITEM) {
        synchronized(lock) {
            val oldSize = getActualItemCount()
            if (position in 0 until oldSize) {
                this.items[position] = item
                notifyItemChanged(position + getHeaderCount())
            }
        }
    }

    fun addItem(item: ITEM) {
        synchronized(lock) {
            val oldSize = getActualItemCount()
            if (this.items.add(item)) {
                notifyItemInserted(oldSize + getHeaderCount())
            }
        }
    }

    fun addItems(position: Int, newItems: List<ITEM>) {
        synchronized(lock) {
            if (this.items.addAll(position, newItems)) {
                notifyItemRangeInserted(position + getHeaderCount(), newItems.size)
            }
        }
    }

    fun addItems(newItems: List<ITEM>) {
        synchronized(lock) {
            val oldSize = getActualItemCount()
            if (this.items.addAll(newItems)) {
                if (oldSize == 0 && getHeaderCount() == 0) {
                    notifyDataSetChanged()
                } else {
                    notifyItemRangeInserted(oldSize + getHeaderCount(), newItems.size)
                }
            }
        }
    }

    fun removeItem(position: Int) {
        synchronized(lock) {
            if (this.items.removeAt(position) != null) {
                notifyItemRemoved(position + getHeaderCount())
            }
        }
    }

    fun removeItem(item: ITEM) {
        synchronized(lock) {
            if (this.items.remove(item)) {
                notifyItemRemoved(this.items.indexOf(item) + getHeaderCount())
            }
        }
    }

    fun removeItems(items: List<ITEM>) {
        synchronized(lock) {
            if (this.items.removeAll(items)) {
                notifyDataSetChanged()
            }
        }
    }

    fun swapItem(oldPosition: Int, newPosition: Int) {
        synchronized(lock) {
            val size = getActualItemCount()
            if (oldPosition in 0 until size && newPosition in 0 until size) {
                val srcPosition = oldPosition + getHeaderCount()
                val targetPosition = newPosition + getHeaderCount()
                Collections.swap(this.items, srcPosition, targetPosition)
                notifyItemChanged(srcPosition)
                notifyItemChanged(targetPosition)
            }
        }
    }

    fun updateItem(item: ITEM) =
        synchronized(lock) {
            val index = this.items.indexOf(item)
            if (index >= 0) {
                this.items[index] = item
                notifyItemChanged(index)
            }
        }

    fun updateItem(position: Int, payload: Any) =
        synchronized(lock) {
            val size = getActualItemCount()
            if (position in 0 until size) {
                notifyItemChanged(position + getHeaderCount(), payload)
            }
        }

    fun updateItems(fromPosition: Int, toPosition: Int, payloads: Any) =
        synchronized(lock) {
            val size = getActualItemCount()
            if (fromPosition in 0 until size && toPosition in 0 until size) {
                notifyItemRangeChanged(
                    fromPosition + getHeaderCount(),
                    toPosition - fromPosition + 1,
                    payloads
                )
            }
        }

    fun clearItems() =
        synchronized(lock) {
            this.items.clear()
            notifyDataSetChanged()
        }

    fun isEmpty() = items.isEmpty()

    fun isNotEmpty() = items.isNotEmpty()

    /**
     * 除去header和footer
     */
    fun getActualItemCount() = items.size


    fun getHeaderCount() = headerItems.size()


    fun getFooterCount() = footerItems.size()

    fun getItem(position: Int): ITEM? = items.getOrNull(position)

    fun getItemByLayoutPosition(position: Int) = items.getOrNull(position - getHeaderCount())

    fun getItems(): List<ITEM> = items

    protected open fun getItemViewType(item: ITEM, position: Int) = 0

    /**
     * grid 模式下使用
     */
    protected open fun getSpanSize(viewType: Int, position: Int) = 1

    final override fun getItemCount() = getActualItemCount() + getHeaderCount() + getFooterCount()

    final override fun getItemViewType(position: Int) = when {
        isHeader(position) -> TYPE_HEADER_VIEW + position
        isFooter(position) -> TYPE_FOOTER_VIEW + position - getActualItemCount() - getHeaderCount()
        else -> getItem(getActualPosition(position))?.let {
            getItemViewType(it, getActualPosition(position))
        } ?: 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when {
        viewType < TYPE_HEADER_VIEW + getHeaderCount() -> {
            ItemViewHolder(headerItems.get(viewType).invoke(parent))
        }

        viewType >= TYPE_FOOTER_VIEW -> {
            ItemViewHolder(footerItems.get(viewType).invoke(parent))
        }

        else -> {
            val holder = ItemViewHolder(getViewBinding(parent))

            @Suppress("UNCHECKED_CAST")
            itemDelegates.getValue(viewType)
                .registerListener(holder, (holder.binding as VB))

            if (itemClickListener != null) {
                holder.itemView.setOnClickListener {
                    getItem(holder.layoutPosition)?.let {
                        itemClickListener?.invoke(holder, it)
                    }
                }
            }

            if (itemLongClickListener != null) {
                holder.itemView.setOnLongClickListener {
                    getItem(holder.layoutPosition)?.let {
                        itemLongClickListener?.invoke(holder, it) ?: true
                    } ?: true
                }
            }

            holder
        }
    }

    protected abstract fun getViewBinding(parent: ViewGroup): VB

    final override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {}

    @Suppress("UNCHECKED_CAST")
    final override fun onBindViewHolder(
        holder: ItemViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (!isHeader(holder.layoutPosition) && !isFooter(holder.layoutPosition)) {
            getItem(holder.layoutPosition - getHeaderCount())?.let {
                itemDelegates.getValue(getItemViewType(holder.layoutPosition))
                    .convert(holder, (holder.binding as VB), it, payloads)
            }
        }
    }

    override fun onViewAttachedToWindow(holder: ItemViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (!isHeader(holder.layoutPosition) && !isFooter(holder.layoutPosition)) {
            addAnimation(holder)
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val manager = recyclerView.layoutManager
        if (manager is GridLayoutManager) {
            manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return getSpanSize(getItemViewType(position), position)
                }
            }
        }
    }

    private fun isHeader(position: Int) = position < getHeaderCount()

    private fun isFooter(position: Int) = position >= getActualItemCount() + getHeaderCount()

    private fun getActualPosition(position: Int) = position - getHeaderCount()

    private fun addAnimation(holder: ItemViewHolder) {
        itemAnimation?.let {
            if (it.itemAnimEnabled) {
                if (!it.itemAnimFirstOnly || holder.layoutPosition > it.itemAnimStartPosition) {
                    startAnimation(holder, it)
                    it.itemAnimStartPosition = holder.layoutPosition
                }
            }
        }
    }

    protected open fun startAnimation(holder: ItemViewHolder, item: ItemAnimation) {
        item.itemAnimation?.let {
            for (anim in it.getAnimators(holder.itemView)) {
                anim.setDuration(item.itemAnimDuration).start()
                anim.interpolator = item.itemAnimInterpolator
            }
        }
    }

    companion object {
        private const val TYPE_HEADER_VIEW = Int.MIN_VALUE
        private const val TYPE_FOOTER_VIEW = Int.MAX_VALUE - 999
    }

}




