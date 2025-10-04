package com.jbros.tagkosha.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jbros.tagkosha.adapter.TagAdapter
import com.jbros.tagkosha.databinding.BottomSheetTagExplorerBinding
import timber.log.Timber

class TagExplorerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTagExplorerBinding? = null
    private val binding get() = _binding!!

    private lateinit var tagAdapter: TagAdapter
    private var tagSelectedListener: OnTagSelectedListener? = null

    // Interface to communicate back to the MainActivity
    interface OnTagSelectedListener {
        fun onTagSelected(tag: String)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Attach the listener from the hosting activity (MainActivity)
        tagSelectedListener = context as? OnTagSelectedListener
        if (tagSelectedListener == null) {
            throw ClassCastException("$context must implement OnTagSelectedListener")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetTagExplorerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        // --- DUMMY DATA FOR NOW ---
        val dummyTags = listOf("#work", "#personal", "#shopping", "#ideas", "#project-alpha", "#work/project-beta")

        tagAdapter = TagAdapter(dummyTags) { selectedTag ->
            Timber.d("Tag clicked: %s", selectedTag)
            tagSelectedListener?.onTagSelected(selectedTag)
            dismiss() // Close the bottom sheet after selection
        }
        binding.recyclerViewTags.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = tagAdapter
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
