package com.jbros.tagkosha

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jbros.tagkosha.databinding.ActivityNoteEditorBinding
import com.jbros.tagkosha.model.Note
import timber.log.Timber
import java.util.Date

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditorBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var firebaseAuth: FirebaseAuth
    private var existingNote: Note? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        firebaseAuth = FirebaseAuth.getInstance()

        existingNote = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("EXISTING_NOTE", Note::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("EXISTING_NOTE") as? Note
        }

        setupToolbar()
        populateUI()

        binding.btnSaveNote.setOnClickListener {
            saveNote()
        }
    }

    private fun setupToolbar() {
        binding.topAppBarEditor.setNavigationOnClickListener {
            finish()
        }
        binding.topAppBarEditor.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_delete_note -> {
                    showDeleteConfirmationDialog()
                    true
                }
                R.id.menu_clone_note, R.id.menu_share_note -> {
                    Toast.makeText(this, "Feature not implemented yet", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        // Hide action icons if it's a new note (user must save first)
        val isExistingNote = existingNote != null
        binding.topAppBarEditor.menu.findItem(R.id.menu_delete_note).isVisible = isExistingNote
        binding.topAppBarEditor.menu.findItem(R.id.menu_clone_note).isVisible = isExistingNote
        binding.topAppBarEditor.menu.findItem(R.id.menu_share_note).isVisible = isExistingNote
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to permanently delete this note?")
            .setPositiveButton("Delete") { _, _ ->
                deleteNote()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteNote() {
        val noteId = existingNote?.id
        if (noteId == null) {
            Toast.makeText(this, "Error: Note ID not found.", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("notes").document(noteId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Error deleting note")
                Toast.makeText(this, "Error deleting note.", Toast.LENGTH_SHORT).show()
            }
    }

    // ... (rest of the file is unchanged) ...
    private fun populateUI() {
        existingNote?.let { note ->
            binding.etNoteTitle.setText(note.title)
            binding.etNoteContent.setText(note.content)
            binding.etNoteTags.setText(note.tags.joinToString(" "))
        }
    }

    private fun saveNote() {
        val title = binding.etNoteTitle.text.toString().trim()
        val content = binding.etNoteContent.text.toString().trim()
        val tagsInput = binding.etNoteTags.text.toString()

        if (title.isEmpty()) {
            Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        Timber.d("--- Tag Parsing ---")
        Timber.d("Raw Input String: '%s'", tagsInput)

        // --- The Official TagKosha Regex ---
        // A tag starts with '#' and can contain letters, numbers, underscore, hyphen, and forward slash.
        // This correctly implements our "Smart Structure" grammar.
        val tagRegex = Regex("#[\\w-/]+")
        val tags = tagRegex.findAll(tagsInput)
            .map { it.value } // Get the matched string value
            .toSet()          // Remove duplicates
            .toList()         // Convert back to a list
        // --- End Regex Logic ---

        Timber.d("Parsed Output List: %s", tags.toString())
        Timber.d("---------------------")

        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            Timber.e("Attempted to save note with null userId")
            return
        }

        saveTagsForUser(userId, tags)

        if (existingNote == null) {
            // Create new note
            val newNote = Note(
                userId = userId,
                title = title,
                content = content,
                tags = tags,
                createdAt = Date(),
                updatedAt = Date()
            )
            firestore.collection("notes").add(newNote)
                .addOnSuccessListener {
                    Timber.d("New note saved successfully.")
                    Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "Error saving new note")
                    Toast.makeText(this, "Error saving note", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Update existing note
            val noteId = existingNote!!.id ?: return
            val updatedData = mapOf(
                "title" to title,
                "content" to content,
                "tags" to tags,
                "updatedAt" to Date()
            )
            firestore.collection("notes").document(noteId).update(updatedData)
                .addOnSuccessListener {
                    Timber.d("Note with ID %s updated successfully.", noteId)
                    Toast.makeText(this, "Note updated", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "Error updating note with ID: %s", noteId)
                    Toast.makeText(this, "Error updating note", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveTagsForUser(userId: String, tags: List<String>) {
        val tagsCollection = firestore.collection("tags")
        for (tagName in tags) {
            // --- THE FIX IS HERE, USING YOUR SUGGESTION ---
            // Create a "safe" version of the tag name for the document ID by replacing '/' with '.'
            val safeTagName = tagName.substring(1).replace('/', '.')
            val docId = "${userId}_$safeTagName"
            val tagData = hashMapOf(
                "userId" to userId,
                "tagName" to tagName // Still save the ORIGINAL tag name with the '/' in the data
            )
            tagsCollection.document(docId).set(tagData)
                .addOnFailureListener { e ->
                    Timber.e(e, "Failed to save tag: %s", tagName)
                }
        }
    }
}
