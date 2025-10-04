package com.jbros.tagkosha.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jbros.tagkosha.adapter.TagAdapter
import com.jbros.tagkosha.databinding.BottomSheetTagExplorerBinding
import com.jbros.tagkosha.viewmodel.TagsViewModel
import timber.log.Timber

class TagExplorerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTagExplorerBinding? = null
    private val binding get() = _binding!!

    private lateinit var tagAdapter: TagAdapter
    private var tagSelectedListener: OnTagSelectedListener? = null

    // Get a reference to the Activity's ViewModel
    private val tagsViewModel: TagsViewModel by activityViewModels()
    private var allTags = listOf<String>() // Holds the current full list of tags

    interface OnTagSelectedListener {
        fun onTagSelected(tag: String)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tagSelectedListener = parentFragment as? OnTagSelectedListener ?: context as? OnTagSelectedListener
        if (tagSelectedListener == null) {
            throw ClassCastException("Calling fragment or activity must implement OnTagSelectedListener")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetTagExplorerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearchView()
        observeTags() // Start observing for live updates
    }

    private fun setupRecyclerView() {
        tagAdapter = TagAdapter(emptyList()) { selectedTag ->
            Timber.d("Tag clicked: %s", selectedTag)
            tagSelectedListener?.onTagSelected(selectedTag)
            dismiss()
        }
        binding.recyclerViewTags.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = tagAdapter
        }
    }
    
    private fun observeTags() {
        tagsViewModel.tags.observe(viewLifecycleOwner, Observer { tags ->
            Timber.d("Live update received in BottomSheet. Tag count: %d", tags.size)
            allTags = tags // Update our local copy of the full list
            // Re-apply the current search filter to the new list, or show the full list
            filterTags(binding.searchViewTags.query.toString())
        })
    }

    private fun setupSearchView() {
        binding.searchViewTags.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterTags(newText)
                return true
            }
        })
    }

    private fun filterTags(query: String?) {
        val filteredList = if (query.isNullOrBlank()) {
            allTags
        } else {
            val lowerCaseQuery = query.lowercase()
            allTags.filter { it.lowercase().contains(lowerCaseQuery) }
        }
        tagAdapter.updateList(filteredList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TagExplorerBottomSheet"
        // The newInstance factory is no longer needed to pass data
    }
}
