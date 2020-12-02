package io.legado.app.ui.replace

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.SimpleRecyclerAdapter
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.databinding.ItemReplaceRuleBinding
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import org.jetbrains.anko.sdk27.listeners.onClick
import java.util.*


class ReplaceRuleAdapter(context: Context, var callBack: CallBack) :
    SimpleRecyclerAdapter<ReplaceRule, ItemReplaceRuleBinding>(context),
    ItemTouchCallback.Callback {

    private val selected = linkedSetOf<ReplaceRule>()

    fun selectAll() {
        getItems().forEach {
            selected.add(it)
        }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    fun revertSelection() {
        getItems().forEach {
            if (selected.contains(it)) {
                selected.remove(it)
            } else {
                selected.add(it)
            }
        }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    fun getSelection(): LinkedHashSet<ReplaceRule> {
        val selection = linkedSetOf<ReplaceRule>()
        getItems().map {
            if (selected.contains(it)) {
                selection.add(it)
            }
        }
        return selection
    }

    override fun getViewBinding(parent: ViewGroup): ItemReplaceRuleBinding {
        return ItemReplaceRuleBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemReplaceRuleBinding,
        item: ReplaceRule,
        payloads: MutableList<Any>
    ) {
        with(binding) {
            val bundle = payloads.getOrNull(0) as? Bundle
            if (bundle == null) {
                root.setBackgroundColor(context.backgroundColor)
                if (item.group.isNullOrEmpty()) {
                    cbName.text = item.name
                } else {
                    cbName.text =
                        String.format("%s (%s)", item.name, item.group)
                }
                swtEnabled.isChecked = item.isEnabled
                cbName.isChecked = selected.contains(item)
            } else {
                bundle.keySet().map {
                    when (it) {
                        "selected" -> cbName.isChecked = selected.contains(item)
                        "name", "group" ->
                            if (item.group.isNullOrEmpty()) {
                                cbName.text = item.name
                            } else {
                                cbName.text =
                                    String.format("%s (%s)", item.name, item.group)
                            }
                        "enabled" -> swtEnabled.isChecked = item.isEnabled
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemReplaceRuleBinding) {
        binding.apply {
            swtEnabled.setOnCheckedChangeListener { _, isChecked ->
                getItem(holder.layoutPosition)?.let {
                    it.isEnabled = isChecked
                    callBack.update(it)
                }
            }
            ivEdit.onClick {
                getItem(holder.layoutPosition)?.let {
                    callBack.edit(it)
                }
            }
            cbName.onClick {
                getItem(holder.layoutPosition)?.let {
                    if (cbName.isChecked) {
                        selected.add(it)
                    } else {
                        selected.remove(it)
                    }
                }
                callBack.upCountView()
            }
            ivMenuMore.onClick {
                showMenu(ivMenuMore, holder.layoutPosition)
            }
        }
    }

    private fun showMenu(view: View, position: Int) {
        val item = getItem(position) ?: return
        val popupMenu = PopupMenu(context, view)
        popupMenu.inflate(R.menu.replace_rule_item)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_top -> callBack.toTop(item)
                R.id.menu_bottom -> callBack.toBottom(item)
                R.id.menu_del -> callBack.delete(item)
            }
            true
        }
        popupMenu.show()
    }

    override fun onMove(srcPosition: Int, targetPosition: Int): Boolean {
        val srcItem = getItem(srcPosition)
        val targetItem = getItem(targetPosition)
        if (srcItem != null && targetItem != null) {
            if (srcItem.order == targetItem.order) {
                callBack.upOrder()
            } else {
                val srcOrder = srcItem.order
                srcItem.order = targetItem.order
                targetItem.order = srcOrder
                movedItems.add(srcItem)
                movedItems.add(targetItem)
            }
        }
        Collections.swap(getItems(), srcPosition, targetPosition)
        notifyItemMoved(srcPosition, targetPosition)
        return true
    }

    private val movedItems = linkedSetOf<ReplaceRule>()

    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (movedItems.isNotEmpty()) {
            callBack.update(*movedItems.toTypedArray())
            movedItems.clear()
        }
    }

    fun initDragSelectTouchHelperCallback(): DragSelectTouchHelper.Callback {
        return object : DragSelectTouchHelper.AdvanceCallback<ReplaceRule>(Mode.ToggleAndReverse) {
            override fun currentSelectedId(): MutableSet<ReplaceRule> {
                return selected
            }

            override fun getItemId(position: Int): ReplaceRule {
                return getItem(position)!!
            }

            override fun updateSelectState(position: Int, isSelected: Boolean): Boolean {
                getItem(position)?.let {
                    if (isSelected) {
                        selected.add(it)
                    } else {
                        selected.remove(it)
                    }
                    notifyItemChanged(position, bundleOf(Pair("selected", null)))
                    callBack.upCountView()
                    return true
                }
                return false
            }
        }
    }

    interface CallBack {
        fun update(vararg rule: ReplaceRule)
        fun delete(rule: ReplaceRule)
        fun edit(rule: ReplaceRule)
        fun toTop(rule: ReplaceRule)
        fun toBottom(rule: ReplaceRule)
        fun upOrder()
        fun upCountView()
    }
}
