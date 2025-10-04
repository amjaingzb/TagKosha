package com.jbros.tagkosha

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jbros.tagkosha.databinding.ActivityNoteEditorBinding
import com.jbros.tagkosha.model.Note
import java.util.Date
import timber.log.Timber // Add this import


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

        // Check if we are editing an existing note
        existingNote = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("EXISTING_NOTE", Note::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("EXISTING_NOTE") as? Note
        }

        populateUI()

        binding.btnSaveNote.setOnClickListener {
            saveNote()
        }
    }

    private fun populateUI() {
        existingNote?.let { note ->
            binding.etNoteTitle.setText(note.title)
            binding.etNoteContent.setText(note.content)
            binding.etNoteTags.setText(note.tags.joinToString(" "))
        }
    }

    // Replace the ENTIRE saveNote function with this new one.

    // Replace the entire saveNote function with this one.
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

    // --- ADD THIS NEW HELPER FUNCTION TO THE SAME FILE ---
// Replace only this function.
    private fun saveTagsForUser(userId: String, tags: List<String>) {
        val tagsCollection = firestore.collection("tags")
        for (tagName in tags) {
            // --- THE FIX IS HERE, USING YOUR SUGGESTION ---
            // Create a "safe" version of the tag name for the document ID by replacing '/' with '.'
            val safeTagName = tagName.substring(1).replace('/', '.')
            val docId = "${userId}_$safeTagName"
            // --- END FIX ---

            val tagData = hashMapOf(
                "userId" to userId,
                "tagName" to tagName // Still save the ORIGINAL tag name with the '/' in the data
            )

            // Use .set() with the safe docId to either create or overwrite the tag.
            tagsCollection.document(docId).set(tagData)
                .addOnFailureListener { e ->
                    Timber.e(e, "Failed to save tag: %s", tagName)
                }
        }
    }}
