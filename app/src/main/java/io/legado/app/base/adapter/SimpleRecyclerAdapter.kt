package io.legado.app.base.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import java.util.*
import kotlin.collections.ArrayList

/**
 * Created by Invincible on 2017/12/15.
 */
@Suppress("unused")
abstract class SimpleRecyclerAdapter<ITEM, VB : ViewBinding>(protected val context: Context) :
    RecyclerView.Adapter<ItemViewHolder>() {

    val inflater: LayoutInflater = LayoutInflater.from(context)

    private val asyncListDiffer: AsyncListDiffer<ITEM> by lazy {
        AsyncListDiffer(this, diffItemCallback)
    }

    private val lock = Object()

    private var itemClickListener: ((holder: ItemViewHolder, item: ITEM) -> Unit)? = null
    private var itemLongClickListener: ((holder: ItemViewHolder, item: ITEM) -> Boolean)? = null

    var itemAnimation: ItemAnimation? = null

    open val diffItemCallback: DiffUtil.ItemCallback<ITEM> =
        object : DiffUtil.ItemCallback<ITEM>() {

            override fun areItemsTheSame(oldItem: ITEM, newItem: ITEM): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: ITEM, newItem: ITEM): Boolean {
                return true
            }

        }

    fun setOnItemClickListener(listener: (holder: ItemViewHolder, item: ITEM) -> Unit) {
        itemClickListener = listener
    }

    fun setOnItemLongClickListener(listener: (holder: ItemViewHolder, item: ITEM) -> Boolean) {
        itemLongClickListener = listener
    }

    fun bindToRecyclerView(recyclerView: RecyclerView) {
        recyclerView.adapter = this
    }

    fun setItems(items: List<ITEM>?) {
        synchronized(lock) {
            asyncListDiffer.submitList(items)
        }
    }

    fun setItem(position: Int, item: ITEM) {
        synchronized(lock) {
            val list = ArrayList(asyncListDiffer.currentList)
            list[position] = item
            asyncListDiffer.submitList(list)
        }
    }

    fun addItem(item: ITEM) {
        synchronized(lock) {
            val list = ArrayList(asyncListDiffer.currentList)
            list.add(item)
            asyncListDiffer.submitList(list)
        }
    }

    fun addItems(position: Int, newItems: List<ITEM>) {
        synchronized(lock) {
            val list = ArrayList(asyncListDiffer.currentList)
            list.addAll(position, newItems)
            asyncListDiffer.submitList(list)
        }
    }

    fun addItems(newItems: List<ITEM>) {
        synchronized(lock) {
            val list = ArrayList(asyncListDiffer.currentList)
            list.addAll(newItems)
            asyncListDiffer.submitList(list)
        }
    }

    fun removeItem(position: Int) {
        synchronized(lock) {
            val list = ArrayList(asyncListDiffer.currentList)
            if (list.removeAt(position) != null) {
                asyncListDiffer.submitList(list)
            }
        }
    }

    fun removeItem(item: ITEM) {
        synchronized(lock) {
            val list = ArrayList(asyncListDiffer.currentList)
            if (list.remove(item)) {
                asyncListDiffer.submitList(list)
            }
        }
    }

    fun removeItems(items: List<ITEM>) {
        synchronized(lock) {
            val list = ArrayList(asyncListDiffer.currentList)
            if (list.removeAll(items)) {
                asyncListDiffer.submitList(list)
            }
        }
    }

    fun swapItem(oldPosition: Int, newPosition: Int) {
        synchronized(lock) {
            val size = itemCount
            if (oldPosition in 0 until size && newPosition in 0 until size) {
                Collections.swap(asyncListDiffer.currentList, oldPosition, newPosition)
                notifyItemChanged(oldPosition)
                notifyItemChanged(newPosition)
            }
        }
    }

    fun updateItem(item: ITEM) =
        synchronized(lock) {
            val index = asyncListDiffer.currentList.indexOf(item)
            if (index >= 0) {
                asyncListDiffer.currentList[index] = item
                notifyItemChanged(index)
            }
        }

    fun updateItem(position: Int, payload: Any) =
        synchronized(lock) {
            val size = itemCount
            if (position in 0 until size) {
                notifyItemChanged(position, payload)
            }
        }

    fun updateItems(fromPosition: Int, toPosition: Int, payloads: Any) =
        synchronized(lock) {
            val size = itemCount
            if (fromPosition in 0 until size && toPosition in 0 until size) {
                notifyItemRangeChanged(
                    fromPosition,
                    toPosition - fromPosition + 1,
                    payloads
                )
            }
        }

    fun clearItems() =
        synchronized(lock) {
            asyncListDiffer.submitList(arrayListOf())
        }

    fun isEmpty() = asyncListDiffer.currentList.isEmpty()

    fun isNotEmpty() = asyncListDiffer.currentList.isNotEmpty()

    fun getItem(position: Int): ITEM? = asyncListDiffer.currentList.getOrNull(position)

    fun getItems(): List<ITEM> = asyncListDiffer.currentList

    /**
     * grid 模式下使用
     */
    protected open fun getSpanSize(viewType: Int, position: Int) = 1

    final override fun getItemCount() = getItems().size

    final override fun getItemViewType(position: Int): Int {
        return 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val holder = ItemViewHolder(getViewBinding(parent))

        @Suppress("UNCHECKED_CAST")
        registerListener(holder, (holder.binding as VB))

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

        return holder
    }

    protected abstract fun getViewBinding(parent: ViewGroup): VB

    final override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {}

    @Suppress("UNCHECKED_CAST")
    final override fun onBindViewHolder(
        holder: ItemViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        getItem(holder.layoutPosition)?.let {
            convert(holder, (holder.binding as VB), it, payloads)
        }
    }

    override fun onViewAttachedToWindow(holder: ItemViewHolder) {
        super.onViewAttachedToWindow(holder)
        addAnimation(holder)
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

    /**
     * 如果使用了事件回调,回调里不要直接使用item,会出现不更新的问题,
     * 使用getItem(holder.layoutPosition)来获取item
     */
    abstract fun convert(
        holder: ItemViewHolder,
        binding: VB,
        item: ITEM,
        payloads: MutableList<Any>
    )

    /**
     * 注册事件
     */
    abstract fun registerListener(holder: ItemViewHolder, binding: VB)
}