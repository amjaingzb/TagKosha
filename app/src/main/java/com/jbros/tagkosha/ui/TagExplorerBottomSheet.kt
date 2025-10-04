package com.jbros.tagkosha.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jbros.tagkosha.adapter.TagAdapter
import com.jbros.tagkosha.databinding.BottomSheetTagExplorerBinding
import timber.log.Timber

class TagExplorerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTagExplorerBinding? = null
    private val binding get() = _binding!!

    private lateinit var tagAdapter: TagAdapter
    private var tagSelectedListener: OnTagSelectedListener? = null

    private lateinit var firestore: FirebaseFirestore
    private lateinit var firebaseAuth: FirebaseAuth
    private val allTags = mutableListOf<String>()

    interface OnTagSelectedListener {
        fun onTagSelected(tag: String)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tagSelectedListener = context as? OnTagSelectedListener
        if (tagSelectedListener == null) {
            throw ClassCastException("$context must implement OnTagSelectedListener")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetTagExplorerBinding.inflate(inflater, container, false)
        firestore = FirebaseFirestore.getInstance()
        firebaseAuth = FirebaseAuth.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearchView() // New setup method
        fetchTags()
    }

    private fun setupRecyclerView() {
        // Initialize with an empty list. The list will be populated by fetchTags().
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

    private fun setupSearchView() {
        binding.searchViewTags.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // We perform filtering as the user types, so we don't need to do anything on submit.
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterTags(newText)
                return true
            }
        })
    }

    private fun filterTags(query: String?) {
        val filteredList = if (query.isNullOrBlank()) {
            allTags // If search is empty, show all tags
        } else {
            // Perform client-side filtering (case-insensitive)
            val lowerCaseQuery = query.lowercase()
            allTags.filter { it.lowercase().contains(lowerCaseQuery) }
        }
        tagAdapter.updateList(filteredList)
    }

    private fun fetchTags() {
        val userId = firebaseAuth.currentUser?.uid ?: return

        firestore.collection("tags")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                allTags.clear()
                val tagNames = documents.mapNotNull { it.getString("tagName") }.sorted()
                allTags.addAll(tagNames)
                // Initially, display the full list
                tagAdapter.updateList(allTags)
                Timber.d("Successfully fetched %d tags", allTags.size)
            }
            .addOnFailureListener { exception ->
                Timber.e(exception, "Error getting tags")
                Toast.makeText(context, "Failed to load tags.", Toast.LENGTH_SHORT).show()
            }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TagExplorerBottomSheet"
    }
}
