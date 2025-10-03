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

    private fun saveNote() {
        val title = binding.etNoteTitle.text.toString().trim()
        val content = binding.etNoteContent.text.toString().trim()
        val tagsInput = binding.etNoteTags.text.toString().trim()

        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Title and content cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Parse the tags from the input string
        val tags = tagsInput.split(" ").filter { it.startsWith("#") && it.length > 1 }.toSet().toList()

        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // --- NEW LOGIC TO SAVE TAGS TO THE 'tags' COLLECTION ---
        saveTagsForUser(userId, tags)
        // --- END NEW LOGIC ---

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
                    Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error saving note: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Note updated", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error updating note: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // --- ADD THIS NEW HELPER FUNCTION TO THE SAME FILE ---
    private fun saveTagsForUser(userId: String, tags: List<String>) {
        val tagsCollection = firestore.collection("tags")
        for (tagName in tags) {
            // Create a unique ID for each tag document to prevent duplicates
            // For example, by combining userId and the tag name itself.
            val docId = "${userId}_${tagName.substring(1)}" // e.g., "user123_idea"

            val tagData = hashMapOf(
                "userId" to userId,
                "tagName" to tagName
            )

            // Use .set() with a specific docId to either create or overwrite the tag.
            // This ensures each user has only one document per tag name.
            tagsCollection.document(docId).set(tagData)
        }
    }
}
