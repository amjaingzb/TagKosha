package com.jbros.tagkosha.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jbros.tagkosha.databinding.ItemTagTreeBinding
import com.jbros.tagkosha.model.TagNode

class TagTreeAdapter(
    private val onTagClicked: (TagNode) -> Unit, // Callback for when a tag text is clicked
    private val onExpandClicked: (TagNode) -> Unit // Callback for when an expand arrow is clicked
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

            // Calculate indentation based on level
            val indentation = node.level * 50 // 50 pixels per level
            binding.root.setPadding(indentation + 48, binding.root.paddingTop, binding.root.paddingRight, binding.root.paddingBottom)

            // Handle visibility and rotation of the expand arrow
            if (isSearchMode || node.children.isEmpty()) {
                binding.ivExpandArrow.visibility = View.INVISIBLE
            } else {
                binding.ivExpandArrow.visibility = View.VISIBLE
                binding.ivExpandArrow.rotation = if (node.isExpanded) 0f else -90f
            }

            // Set click listeners
            binding.tvTagName.setOnClickListener {
                onTagClicked(node)
            }
            binding.ivExpandArrow.setOnClickListener {
                onExpandClicked(node)
            }
        }
    }
}
