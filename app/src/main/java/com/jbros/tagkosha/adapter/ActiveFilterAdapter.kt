package com.jbros.tagkosha.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jbros.tagkosha.databinding.ItemFilterChipBinding

class ActiveFilterAdapter(
    private var activeFilters: List<String>,
    private val onFilterRemoved: (String) -> Unit
) : RecyclerView.Adapter<ActiveFilterAdapter.FilterViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val binding = ItemFilterChipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FilterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        holder.bind(activeFilters[position])
    }

    override fun getItemCount(): Int = activeFilters.size

    inner class FilterViewHolder(private val binding: ItemFilterChipBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(filterTag: String) {
            binding.chipFilter.text = filterTag
            binding.chipFilter.setOnCloseIconClickListener {
                onFilterRemoved(filterTag)
            }
        }
    }
}
