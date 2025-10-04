package com.jbros.tagkosha.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jbros.tagkosha.databinding.ItemTagBinding

class TagAdapter(
    private var tags: List<String>,
    private val onTagClicked: (String) -> Unit
) : RecyclerView.Adapter<TagAdapter.TagViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val binding = ItemTagBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TagViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.bind(tags[position])
    }

    override fun getItemCount(): Int = tags.size

    // New method to update the list with filtered results
    fun updateList(newList: List<String>) {
        tags = newList
        notifyDataSetChanged()
    }

    inner class TagViewHolder(private val binding: ItemTagBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tag: String) {
            binding.tvTagName.text = tag
            itemView.setOnClickListener {
                onTagClicked(tag)
            }
        }
    }
}
