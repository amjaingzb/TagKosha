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

    inner class TagViewHolder(private val binding: ItemTagTreeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(node: TagNode) {
            binding.tvTagName.text = node.displayName

            val indentation = node.level * 50
            binding.root.setPadding(indentation + 16, binding.root.paddingTop, binding.root.paddingRight, binding.root.paddingBottom)

            // --- REVISED VISIBILITY AND ROTATION LOGIC ---
            if (isSearchMode || node.children.isEmpty()) {
                // In search mode or if it's a leaf node, hide the arrow but keep the space
                binding.ivExpandArrow.visibility = View.INVISIBLE
                binding.ivExpandArrow.setOnClickListener(null) // Remove click listener
            } else {
                // It's a parent node in browse mode, show the arrow
                binding.ivExpandArrow.visibility = View.VISIBLE
                // Animate the rotation
                binding.ivExpandArrow.animate().rotation(if (node.isExpanded) 90f else 0f).setDuration(200).start()
                binding.ivExpandArrow.setOnClickListener {
                    onExpandClicked(node)
                }
            }

            // The whole item is always clickable to select the tag
            itemView.setOnClickListener {
                onTagClicked(node)
            }
        }
    }
}
