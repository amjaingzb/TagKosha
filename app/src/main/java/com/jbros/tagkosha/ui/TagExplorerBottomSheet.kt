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
import com.jbros.tagkosha.adapter.TagTreeAdapter
import com.jbros.tagkosha.databinding.BottomSheetTagExplorerBinding
import com.jbros.tagkosha.model.TagNode
import com.jbros.tagkosha.viewmodel.TagsViewModel
import timber.log.Timber

class TagExplorerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTagExplorerBinding? = null
    private val binding get() = _binding!!

    private lateinit var tagTreeAdapter: TagTreeAdapter
    private var tagSelectedListener: OnTagSelectedListener? = null

    private val tagsViewModel: TagsViewModel by activityViewModels()
    private var rootNodes = mutableListOf<TagNode>()
    private var flatTagList = mutableListOf<TagNode>() // For fast searching

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
        observeTags()
    }

    private fun setupRecyclerView() {
        tagTreeAdapter = TagTreeAdapter(
            onTagClicked = { node ->
                tagSelectedListener?.onTagSelected(node.fullName)
                dismiss()
            },
            onExpandClicked = { node ->
                node.isExpanded = !node.isExpanded
                updateDisplayListFromTree()
            }
        )
        binding.recyclerViewTags.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = tagTreeAdapter
        }
    }

    private fun observeTags() {
        tagsViewModel.tags.observe(viewLifecycleOwner, Observer { tags ->
            Timber.d("Reparsing tag tree. Tag count: %d", tags.size)
            rootNodes = parseFlatListToTree(tags)
            flatTagList = createFlatListFromTree(rootNodes)
            // Show the initial tree view
            updateDisplayListFromTree()
        })
    }
    
    // Generates the visible list from the tree structure
    private fun updateDisplayListFromTree() {
        val displayList = mutableListOf<TagNode>()
        fun addNodesToList(nodes: List<TagNode>) {
            for (node in nodes) {
                displayList.add(node)
                if (node.isExpanded) {
                    addNodesToList(node.children)
                }
            }
        }
        addNodesToList(rootNodes)
        tagTreeAdapter.submitList(displayList, isSearch = false)
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

    // --- REVISED AND CORRECTED SEARCH LOGIC ---
    private fun filterTags(query: String?) {
        if (query.isNullOrBlank()) {
            // If search is cleared, show the tree view again
            updateDisplayListFromTree()
        } else {
            // Otherwise, show a flat list of search results
            val lowerCaseQuery = query.lowercase()
            val searchResults = flatTagList.filter { 
                it.fullName.lowercase().contains(lowerCaseQuery) 
            }
            tagTreeAdapter.submitList(searchResults, isSearch = true)
        }
    }
    
    // --- PARSER LOGIC (UNCHANGED, BUT RENAMED FOR CLARITY) ---
    private fun parseFlatListToTree(tags: List<String>): MutableList<TagNode> {
        val nodeMap = mutableMapOf<String, TagNode>()
        val roots = mutableListOf<TagNode>()

        for (tag in tags.sorted()) {
            val parts = tag.removePrefix("#").split('/')
            val node = TagNode(
                fullName = tag,
                displayName = parts.last(),
                level = parts.size - 1
            )
            nodeMap[tag] = node
            if (node.level == 0) {
                roots.add(node)
            } else {
                val parentFullName = "#" + parts.dropLast(1).joinToString("/")
                nodeMap[parentFullName]?.children?.add(node)
            }
        }
        return roots
    }
    
    // New helper to create a flat list for searching
    private fun createFlatListFromTree(nodes: List<TagNode>): MutableList<TagNode> {
        val flatList = mutableListOf<TagNode>()
        fun addToList(nodesToAdd: List<TagNode>) {
            for (node in nodesToAdd) {
                flatList.add(node)
                addToList(node.children)
            }
        }
        addToList(nodes)
        return flatList
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TagExplorerBottomSheet"
    }
}
