package com.jbros.tagkosha

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
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
        tagsViewModel // Initialize
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
        // The 'tags' variable is correctly inferred as List<Tag> from the ViewModel
        tagsViewModel.tags.observe(this, Observer { tags ->
            Timber.d("Tags updated from ViewModel. Object count: %d", tags.size)
            allUserTags.clear()

            // THE FIX: Use .map to extract the 'name' string from each 'Tag' object
            val tagNames = tags.map { it.name }
            allUserTags.addAll(tagNames)

            // The rest of the logic can now proceed as it expects a list of strings
            if (activeFilters.isNotEmpty()) {
                performNoteQuery()
            }
        })
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
            },
            // --- NEW CALLBACK IMPLEMENTATION ---
            onTagChipClicked = { tag ->
                // Simply reuse the existing onTagSelected logic
                onTagSelected(tag)
            }
        )
        binding.recyclerViewNotes.adapter = noteAdapter
        binding.recyclerViewNotes.layoutManager = LinearLayoutManager(this)
    }

    private fun setupFilterRecyclerView() {
        activeFilterAdapter = ActiveFilterAdapter(activeFilters.toList()) { removedFilter ->
            onTagRemoved(removedFilter)
        }
        binding.recyclerViewActiveFilters.adapter = activeFilterAdapter
    }

    private fun performNoteQuery() {
        val userId = firebaseAuth.currentUser?.uid ?: return
        var query: Query = firestore.collection("notes").whereEqualTo("userId", userId)

        // First, get the complete list of tags to search for, including children.
        val expandedTags = getExpandedTags()
        
        // Only apply the 'whereArrayContainsAny' filter if we have tags to search for.
        if (expandedTags.isNotEmpty()) {
            // Handle Firestore's limitation: the 'in' or 'array-contains-any' operator
            // can only handle a list of up to 10 items.
            if (expandedTags.size > 10) {
                Toast.makeText(this, "Filter is too broad...", Toast.LENGTH_LONG).show()
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
                    // We no longer trust the listener's result set to be perfectly pre-filtered during live updates.
                    val filteredNotes = if (activeFilters.isNotEmpty()) {
                        newNotes.filter { note ->
                            // The 'all' check handles both single and multiple ("AND") filters correctly.
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
            // --- NEW: Trigger the self-healing mechanism ---
            verifyAndRepairTagCount(tag)
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
        binding.recyclerViewActiveFilters.visibility = if (activeFilters.isEmpty()) View.GONE else View.VISIBLE
        activeFilterAdapter = ActiveFilterAdapter(activeFilters.toList()) { removedFilter ->
            onTagRemoved(removedFilter)
        }
        binding.recyclerViewActiveFilters.adapter = activeFilterAdapter
    }

    private fun verifyAndRepairTagCount(tagName: String) {
        val currentUser = firebaseAuth.currentUser ?: return
        Timber.d("Verifying count for tag: %s", tagName)

        // 1. Get the locally cached count from the ViewModel's data
        val cachedTag = tagsViewModel.tags.value?.find { it.name == tagName }
        val cachedCount = cachedTag?.count ?: -1 // Use -1 to signify "not found" or "unknown"

        // 2. Run a cheap, server-side count() query for accuracy
        firestore.collection("notes")
            .whereEqualTo("userId", currentUser.uid)
            .whereArrayContains("tags", tagName)
            .count()
            .get(AggregateSource.SERVER)
            .addOnSuccessListener { snapshot ->
                val serverCount = snapshot.count
                Timber.d("Server count for '%s' is %d. Cached count is %d.", tagName, serverCount, cachedCount)

                // 3. If they don't match, repair the data in Firestore
                if (serverCount != cachedCount) {
                    val tagDocId = getTagDocId(currentUser.uid, tagName)
                    firestore.collection("tags").document(tagDocId)
                        .update("count", serverCount)
                        .addOnSuccessListener { Timber.i("Successfully repaired count for tag '%s' to %d", tagName, serverCount) }
                        .addOnFailureListener { e -> Timber.e(e, "Failed to repair count for tag: %s", tagName) }
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Failed to get server count for tag: %s", tagName)
            }
    }

    private fun showDeleteConfirmationDialog(note: Note) {
        AlertDialog.Builder(this)
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to permanently delete this note?")
            .setPositiveButton("Delete") { _, _ -> deleteNote(note) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteNote(note: Note) {
        val noteId = note.id ?: return
        val currentUser = firebaseAuth.currentUser ?: return
        Timber.d("Deleting note %s and decrementing %d tags.", noteId, note.tags.size)

        // Use a WriteBatch to delete the note and decrement all tags atomically
        val batch = firestore.batch()

        // 1. Schedule the note deletion
        val noteRef = firestore.collection("notes").document(noteId)
        batch.delete(noteRef)

        // 2. Schedule the counter decrements for all hierarchical tags
        val tagsToDecrement = getHierarchicalTags(note.tags.toSet())
        tagsToDecrement.forEach { tagName ->
            val tagDocId = getTagDocId(currentUser.uid, tagName)
            val tagRef = firestore.collection("tags").document(tagDocId)
            batch.update(tagRef, "count", FieldValue.increment(-1))
        }

        // 3. Commit the atomic operation
        batch.commit()
            .addOnSuccessListener { Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { e -> Timber.e(e, "Error deleting note") }
    }

    // --- NEW HELPER FUNCTIONS (needed for deleteNote and repair) ---
    private fun getHierarchicalTags(tags: Set<String>): Set<String> {
        val allHierarchicalTags = mutableSetOf<String>()
        for (tag in tags) {
            val parts = tag.removePrefix("#").split('/')
            for (i in 1..parts.size) {
                allHierarchicalTags.add("#" + parts.take(i).joinToString("/"))
            }
        }
        return allHierarchicalTags
    }

    private fun getTagDocId(userId: String, tagName: String): String {
        val safeTagName = tagName.substring(1).replace('/', '.')
        return "${userId}_$safeTagName"
    }
}
