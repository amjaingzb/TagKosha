package com.jbros.tagkosha

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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
        val note = existingNote
        val noteId = note?.id
        val currentUser = firebaseAuth.currentUser
        if (noteId == null || note == null || currentUser == null) {
            Toast.makeText(this, "Error: Note data not found.", Toast.LENGTH_SHORT).show()
            return
        }

        // Use a WriteBatch to perform the delete and decrements atomically
        val batch = firestore.batch()

        // 1. Schedule the note deletion
        val noteRef = firestore.collection("notes").document(noteId)
        batch.delete(noteRef)

        // 2. Schedule the counter decrements
        // --- SIMPLIFIED: Decrement only the note's exact tags ---
        note.tags.forEach { tagName ->
            val tagDocId = getTagDocId(firebaseAuth.currentUser!!.uid, tagName)
            val tagRef = firestore.collection("tags").document(tagDocId)
            batch.update(tagRef, "count", FieldValue.increment(-1))
        }

        // 3. Commit the atomic batch
        batch.commit()
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

        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            Timber.e("Attempted to save note with null userId")
            return
        }

        // --- Tag Parsing Logic (Unchanged) ---
        val tagRegex = Regex("#[\\w-/]+")
        val newTags = tagRegex.findAll(tagsInput).map { it.value }.toSet()
        Timber.d("Parsed new tags: %s", newTags)

        // --- Calculate Tag Differences ---
        val originalTags = existingNote?.tags?.toSet() ?: emptySet()
        val tagsToAdd = newTags - originalTags
        val tagsToRemove = originalTags - newTags
        Timber.d("Tags to add: %s", tagsToAdd)
        Timber.d("Tags to remove: %s", tagsToRemove)

        // --- All database operations now happen inside a transaction ---
        firestore.runTransaction { transaction ->
            // Determine if this is a new note or an update
            val noteRef = if (existingNote == null) {
                firestore.collection("notes").document() // Create a reference for a new note
            } else {
                firestore.collection("notes").document(existingNote!!.id!!) // Get ref for existing
            }

            // --- 1. SIMPLIFIED: Handle Tag Increments ---
            // We no longer call getHierarchicalTags. We only increment the actual tags added.
            tagsToAdd.forEach { tagName ->
                val tagRef = firestore.collection("tags").document(getTagDocId(userId, tagName))
                val tagDoc = transaction.get(tagRef)
                if (tagDoc.exists()) {
                    transaction.update(tagRef, "count", FieldValue.increment(1))
                } else {
                    val newTagData = mapOf("userId" to userId, "tagName" to tagName, "count" to 1)
                    transaction.set(tagRef, newTagData)
                }
            }

            // --- 2. SIMPLIFIED: Handle Tag Decrements ---
            // We no longer call getHierarchicalTags. We only decrement the actual tags removed.
            tagsToRemove.forEach { tagName ->
                val tagRef = firestore.collection("tags").document(getTagDocId(userId, tagName))
                transaction.update(tagRef, "count", FieldValue.increment(-1))
            }

            // --- 3. Save the Note Itself ---
            if (existingNote == null) {
                val newNote = Note(
                    id = noteRef.id,
                    userId = userId,
                    title = title,
                    content = content,
                    tags = newTags.toList(),
                    createdAt = Date(),
                    updatedAt = Date()
                )
                transaction.set(noteRef, newNote)
            } else {
                val updatedData = mapOf(
                    "title" to title,
                    "content" to content,
                    "tags" to newTags.toList(),
                    "updatedAt" to Date()
                )
                transaction.update(noteRef, updatedData)
            }

            // Transaction will be committed automatically here if successful
            null // Return null to satisfy the transaction block
        }.addOnSuccessListener {
            Toast.makeText(this, "Note saved successfully", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener { e ->
            Timber.e(e, "Transaction failed: Error saving note")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Takes a set of tags (e.g., {"#work/projA", "#home"}) and returns a set
     * containing all tags and their parents (e.g., {"#work", "#work/projA", "#home"}).
     */
//    private fun getHierarchicalTags(tags: Set<String>): Set<String> {
//        val allHierarchicalTags = mutableSetOf<String>()
//        for (tag in tags) {
//            val parts = tag.removePrefix("#").split('/')
//            for (i in 1..parts.size) {
//                allHierarchicalTags.add("#" + parts.take(i).joinToString("/"))
//            }
//        }
//        return allHierarchicalTags
//    }

    /**
     * Creates a Firestore-safe document ID for a given tag.
     */
    private fun getTagDocId(userId: String, tagName: String): String {
        val safeTagName = tagName.substring(1).replace('/', '.')
        return "${userId}_$safeTagName"
    }

//    private fun saveTagsForUser(userId: String, tags: List<String>) {
//        val tagsCollection = firestore.collection("tags")
//        for (tagName in tags) {
//            // --- THE FIX IS HERE, USING YOUR SUGGESTION ---
//            // Create a "safe" version of the tag name for the document ID by replacing '/' with '.'
//            val safeTagName = tagName.substring(1).replace('/', '.')
//            val docId = "${userId}_$safeTagName"
//            val tagData = hashMapOf(
//                "userId" to userId,
//                "tagName" to tagName // Still save the ORIGINAL tag name with the '/' in the data
//            )
//            tagsCollection.document(docId).set(tagData)
//                .addOnFailureListener { e ->
//                    Timber.e(e, "Failed to save tag: %s", tagName)
//                }
//        }
//    }
}
