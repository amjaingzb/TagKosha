package com.jbros.tagkosha

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jbros.tagkosha.adapter.NoteAdapter
import com.jbros.tagkosha.auth.LoginActivity
import com.jbros.tagkosha.databinding.ActivityMainBinding
import com.jbros.tagkosha.model.Note
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var noteAdapter: NoteAdapter
    private val notesList = mutableListOf<Note>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        checkUser()
        setupRecyclerView()
        loadNotes()

        binding.fabAddNote.setOnClickListener {
            startActivity(Intent(this, NoteEditorActivity::class.java))
        }

        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_sign_out -> {
                    firebaseAuth.signOut()
                    Toast.makeText(this, "Signed Out", Toast.LENGTH_SHORT).show()
                    checkUser()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        noteAdapter = NoteAdapter(notesList) { note ->
            // Handle note click: open editor with existing note data
            val intent = Intent(this, NoteEditorActivity::class.java)
            intent.putExtra("EXISTING_NOTE", note)
            startActivity(intent)
        }
        binding.recyclerViewNotes.adapter = noteAdapter
        binding.recyclerViewNotes.layoutManager = LinearLayoutManager(this)
    }

    private fun loadNotes() {
        val userId = firebaseAuth.currentUser?.uid ?: return

        firestore.collection("notes")
            .whereEqualTo("userId", userId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Timber.e(error, "Error loading notes") // This will print the full error to Logcat.
                    Toast.makeText(this, "Error loading notes. Check Logcat.", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    notesList.clear()
                    for (document in snapshots.documents) {
                        val note = document.toObject(Note::class.java)
                        if (note != null) {
                            note.id = document.id // Important: Store the document ID
                            notesList.add(note)
                        }
                    }
                    noteAdapter.updateNotes(notesList)
                }
            }
    }

    private fun checkUser() {
        if (firebaseAuth.currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            // Clear the back stack
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
