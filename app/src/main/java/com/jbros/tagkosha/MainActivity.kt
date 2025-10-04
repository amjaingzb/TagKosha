package com.jbros.tagkosha

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
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
import timber.log.Timber

class MainActivity : AppCompatActivity(), TagExplorerBottomSheet.OnTagSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var noteAdapter: NoteAdapter
    private val notesList = mutableListOf<Note>()

    private lateinit var activeFilterAdapter: ActiveFilterAdapter
    private val activeFilters = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        checkUser()
        setupNoteRecyclerView()
        setupFilterRecyclerView()
        performNoteQuery()

        binding.fabAddNote.setOnClickListener {
            startActivity(Intent(this, NoteEditorActivity::class.java))
        }

        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_filter -> {
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

    private fun setupNoteRecyclerView() {
        noteAdapter = NoteAdapter(notesList) { note ->
            val intent = Intent(this, NoteEditorActivity::class.java)
            intent.putExtra("EXISTING_NOTE", note)
            startActivity(intent)
        }
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

        if (activeFilters.isNotEmpty()) {
            query = query.whereArrayContainsAny("tags", activeFilters.toList())
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

                    // --- THE FIX IS HERE ---
                    // We must manually map the documents to our Note objects and
                    // crucially, assign the document ID to our object's 'id' field.
                    val newNotes = snapshots.documents.mapNotNull { doc ->
                        val note = doc.toObject(Note::class.java)
                        note?.apply {
                            id = doc.id // This was the missing line!
                        }
                    }
                    // --- END FIX ---

                    // Client-side filtering for "AND" logic
                    val filteredNotes = if (activeFilters.size > 1) {
                        newNotes.filter { it.tags.containsAll(activeFilters) }
                    } else {
                        newNotes
                    }

                    notesList.addAll(filteredNotes)
                    noteAdapter.updateNotes(notesList)
                }
            }
    }

    private fun checkUser() {
        if (firebaseAuth.currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // --- Filter Management ---

    override fun onTagSelected(tag: String) {
        if (activeFilters.add(tag)) { // 'add' returns true if the tag was not already present
            updateFilterUI()
            performNoteQuery()
        }
    }

    private fun onTagRemoved(tag: String) {
        if (activeFilters.remove(tag)) {
            updateFilterUI()
            performNoteQuery()
        }
    }

    private fun updateFilterUI() {
        if (activeFilters.isEmpty()) {
            binding.recyclerViewActiveFilters.visibility = View.GONE
        } else {
            binding.recyclerViewActiveFilters.visibility = View.VISIBLE
        }
        // Update the adapter with the new list
        activeFilterAdapter = ActiveFilterAdapter(activeFilters.toList()) { removedFilter ->
            onTagRemoved(removedFilter)
        }
        binding.recyclerViewActiveFilters.adapter = activeFilterAdapter
    }
}
