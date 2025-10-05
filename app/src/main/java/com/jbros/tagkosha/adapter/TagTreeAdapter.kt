package com.jbros.tagkosha.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jbros.tagkosha.databinding.ItemTagTreeBinding
import com.jbros.tagkosha.model.TagNode

class TagTreeAdapter(
    private val onTagClicked: (TagNode) -> Unit,
    private val onExpandClicked: (TagNode) -> Unit
) : RecyclerView.Adapter<TagTreeAdapter.TagViewHolder>() {

    private var displayList: List<TagNode> = emptyList()
    private var isSearchMode: Boolean = false

    fun submitList(list: List<TagNode>, isSearch: Boolean) {
        displayList = list
        isSearchMode = isSearch
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val binding = ItemTagTreeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TagViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.bind(displayList[position])
    }

    override fun getItemCount(): Int = displayList.size

    // ... inside the TagTreeAdapter class ...

    inner class TagViewHolder(private val binding: ItemTagTreeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(node: TagNode) {
            // --- EDITED LOGIC TO INCLUDE COUNT ---
            // 1. Determine the base name to display (full path for search, short name for tree)
            val nameToShow = if (isSearchMode) node.fullName else node.displayName

            // 2. Append the count if it's greater than 0, otherwise just show the name.
            binding.tvTagName.text = if (node.count > 0) {
                "$nameToShow (${node.count})"
            } else {
                nameToShow
            }
            // --- END EDITED LOGIC ---

            // Only apply indentation in browse (tree) mode.
            val indentation = if (isSearchMode) 0 else node.level * 50
            binding.root.setPadding(indentation + 48, binding.root.paddingTop, binding.root.paddingRight, binding.root.paddingBottom)

            if (isSearchMode || node.children.isEmpty()) {
                binding.ivExpandArrow.visibility = View.INVISIBLE
                binding.ivExpandArrow.setOnClickListener(null)
            } else {
                binding.ivExpandArrow.visibility = View.VISIBLE
                binding.ivExpandArrow.animate().rotation(if (node.isExpanded) 90f else 0f).setDuration(200).start()
                binding.ivExpandArrow.setOnClickListener {
                    onExpandClicked(node)
                }
            }

            itemView.setOnClickListener {
                onTagClicked(node)
            }
        }
    }
}
