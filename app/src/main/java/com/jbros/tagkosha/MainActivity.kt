package com.jbros.tagkosha

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jbros.tagkosha.adapter.ActiveFilterAdapter
import com.jbros.tagkosha.adapter.NoteAdapter
import com.jbros.tagkosha.auth.LoginActivity
import com.jbros.tagkosha.databinding.ActivityMainBinding
import com.jbros.tagkosha.model.Note
import com.jbros.tagkosha.ui.TagExplorerBottomSheet
import com.jbros.tagkosha.viewmodel.TagsViewModel
import timber.log.Timber

class MainActivity : AppCompatActivity(), TagExplorerBottomSheet.OnTagSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var noteAdapter: NoteAdapter
    private val notesList = mutableListOf<Note>()

    private lateinit var activeFilterAdapter: ActiveFilterAdapter
    private val activeFilters = mutableSetOf<String>()
    private val allUserTags = mutableListOf<String>()


    // Getting the ViewModel is all we need to do. It will start its work automatically.
    // It will be created, start its listener, and survive configuration changes.
    private val tagsViewModel: TagsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // The ViewModel will be created here and start listening.
        // We don't need to explicitly observe the tags here unless MainActivity
        // needs the list for another purpose, which it currently does not.
        tagsViewModel

        checkUser()
        setupNoteRecyclerView()
        setupFilterRecyclerView()
        observeTags()
        performNoteQuery()

        binding.fabAddNote.setOnClickListener {
            startActivity(Intent(this, NoteEditorActivity::class.java))
        }

        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_filter -> {
                    // No need to pass data anymore. The sheet will get it from the ViewModel.
                    TagExplorerBottomSheet().show(supportFragmentManager, TagExplorerBottomSheet.TAG)
                    true
                }
                R.id.menu_sign_out -> {
                    firebaseAuth.signOut()
                    checkUser()
                    true
                }
                else -> false
            }
        }
    }

    private fun observeTags() {
        tagsViewModel.tags.observe(this) { tags ->
            Timber.d("Tags updated from ViewModel. Count: %d", tags.size)
            allUserTags.clear()
            allUserTags.addAll(tags)
            // If filters are active, re-run the query because the available tags might have changed
            if (activeFilters.isNotEmpty()) {
                performNoteQuery()
            }
        }
    }

    private fun setupNoteRecyclerView() {
        noteAdapter = NoteAdapter(
            notes = notesList,
            onNoteClicked = { note ->
                // Single-tap action
            val intent = Intent(this, NoteEditorActivity::class.java)
            intent.putExtra("EXISTING_NOTE", note)
            startActivity(intent)
            },
            onActionClicked = { note, action ->
                // Long-press context menu actions
                when (action) {
                    NoteAdapter.Action.DELETE -> showDeleteConfirmationDialog(note)
                    NoteAdapter.Action.CLONE, NoteAdapter.Action.SHARE -> {
                        Toast.makeText(this, "Feature not implemented yet", Toast.LENGTH_SHORT).show()
        }
                }
            }
        )
        binding.recyclerViewNotes.adapter = noteAdapter
        binding.recyclerViewNotes.layoutManager = LinearLayoutManager(this)
    }

    // --- Delete Logic (now in MainActivity) ---
    private fun showDeleteConfirmationDialog(note: Note) {
        AlertDialog.Builder(this)
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to permanently delete this note?")
            .setPositiveButton("Delete") { _, _ ->
                deleteNote(note)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteNote(note: Note) {
        val noteId = note.id
        if (noteId == null) {
            Toast.makeText(this, "Error: Note ID not found.", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("notes").document(noteId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show()
                // The listener will automatically update the UI
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Error deleting note")
                Toast.makeText(this, "Error deleting note.", Toast.LENGTH_SHORT).show()
            }
    }


    private fun setupFilterRecyclerView() {
        activeFilterAdapter = ActiveFilterAdapter(activeFilters.toList()) { removedFilter ->
            onTagRemoved(removedFilter)
        }
        binding.recyclerViewActiveFilters.adapter = activeFilterAdapter
    }

    // Replace the existing performNoteQuery function with this one
    private fun performNoteQuery() {
        val userId = firebaseAuth.currentUser?.uid ?: return
        var query: Query = firestore.collection("notes").whereEqualTo("userId", userId)

        // First, get the complete list of tags to search for, including children.
        val expandedTags = getExpandedTags()
        Timber.d("Active filters (user selection): %s", activeFilters)
        Timber.d("Expanded filters (for query): %s", expandedTags)

        // Only apply the 'whereArrayContainsAny' filter if we have tags to search for.
        if (expandedTags.isNotEmpty()) {
            // Handle Firestore's limitation: the 'in' or 'array-contains-any' operator
            // can only handle a list of up to 10 items.
            if (expandedTags.size > 10) {
                Toast.makeText(this, "Filter is too broad, results may be incomplete. Please be more specific.", Toast.LENGTH_LONG).show()
                // We'll proceed with a truncated list to avoid a crash.
                query = query.whereArrayContainsAny("tags", expandedTags.take(10))
            } else {
                query = query.whereArrayContainsAny("tags", expandedTags)
            }
        }

        query.orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Timber.e(error, "Error loading notes. Message: %s", error.message)
                    Toast.makeText(this, "Error loading notes. Check Logcat.", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    notesList.clear()
                    val newNotes = snapshots.documents.mapNotNull { doc ->
                        val note = doc.toObject(Note::class.java)
                        note?.apply { id = doc.id }
                    }

                    // Client-side filtering to enforce "AND" logic for multiple active filters.
                    // This is now more powerful to handle hierarchies correctly.
                    val filteredNotes = if (activeFilters.size > 1) {
                        newNotes.filter { note ->
                            // A note must satisfy the hierarchy of EACH active filter.
                            activeFilters.all { filter ->
                                note.tags.any { it.startsWith(filter) }
                            }
                        }
                    } else {
                        newNotes
                    }

                    notesList.addAll(filteredNotes)
                    noteAdapter.updateNotes(notesList)
                }
            }
    }

    // Add this entire new function
    private fun getExpandedTags(): List<String> {
        if (activeFilters.isEmpty()) {
            return emptyList()
        }
        // Use a Set to automatically handle duplicates if a parent and child are both selected
        val expanded = mutableSetOf<String>()
        activeFilters.forEach { filterTag ->
            val matchingTags = allUserTags.filter { tagInList ->
                // A tag matches if it's the exact filter OR if it's a child (starts with filter + "/")
                tagInList == filterTag || tagInList.startsWith("$filterTag/")
            }
            expanded.addAll(matchingTags)
        }
        return expanded.toList()
    }

    private fun checkUser() {
        if (firebaseAuth.currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // --- Filter Management Logic ---

    // This is called from the TagExplorerBottomSheet when a tag is selected
    override fun onTagSelected(tag: String) {
        if (activeFilters.add(tag)) { // .add() returns true if the set was changed
            updateFilterUI()
            performNoteQuery()
        }
    }

    // This is called from the ActiveFilterAdapter when a chip's close icon is clicked
    private fun onTagRemoved(tag: String) {
        if (activeFilters.remove(tag)) {
            updateFilterUI()
            performNoteQuery()
        }
    }

    // This function updates the UI for the active filter chips
    private fun updateFilterUI() {
        if (activeFilters.isEmpty()) {
            binding.recyclerViewActiveFilters.visibility = View.GONE
        } else {
            binding.recyclerViewActiveFilters.visibility = View.VISIBLE
        }
        // Re-create the adapter with the updated list to refresh the chips
        activeFilterAdapter = ActiveFilterAdapter(activeFilters.toList()) { removedFilter ->
            onTagRemoved(removedFilter)
        }
        binding.recyclerViewActiveFilters.adapter = activeFilterAdapter
    }
}
