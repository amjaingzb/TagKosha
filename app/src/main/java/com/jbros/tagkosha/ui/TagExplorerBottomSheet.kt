package com.jbros.tagkosha.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jbros.tagkosha.adapter.TagAdapter
import com.jbros.tagkosha.databinding.BottomSheetTagExplorerBinding
import timber.log.Timber
import java.io.Serializable

class TagExplorerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTagExplorerBinding? = null
    private val binding get() = _binding!!

    private lateinit var tagAdapter: TagAdapter
    private var tagSelectedListener: OnTagSelectedListener? = null
    private var allTags: List<String> = emptyList()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            allTags = it.getStringArrayList(ARG_TAGS) ?: emptyList()
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
    }

    private fun setupRecyclerView() {
        tagAdapter = TagAdapter(allTags) { selectedTag ->
            Timber.d("Tag clicked: %s", selectedTag)
            tagSelectedListener?.onTagSelected(selectedTag)
            dismiss()
        }
        binding.recyclerViewTags.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = tagAdapter
        }
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
        private const val ARG_TAGS = "tags_list"

        // Use this factory method to create a new instance and pass data
        fun newInstance(tags: List<String>): TagExplorerBottomSheet {
            val fragment = TagExplorerBottomSheet()
            val args = Bundle()
            args.putStringArrayList(ARG_TAGS, ArrayList(tags))
            fragment.arguments = args
            return fragment
        }
    }
}
