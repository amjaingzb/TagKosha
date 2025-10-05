package com.jbros.tagkosha

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
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
    // --- NEW: State variable to track if the reserved tag is in the input field ---
    private var isReservedTagPresent = false

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
        setupTagInputListener()


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

            // Filter out the #untagged tag before displaying. The user should never see it.
            val tagsToDisplay = note.tags.filter { it != "#untagged" }
            binding.etNoteTags.setText(tagsToDisplay.joinToString(" "))
        }
    }

    /**
     * Adds a TextWatcher to the tag input field to provide immediate visual
     * feedback if the user types the reserved "#untagged" tag.
     */
    private fun setupTagInputListener() {
        // --- FIX #1: Save the EditText's ACTUAL current color, not a theme color ---
        val defaultTextColor = binding.etNoteTags.currentTextColor

        binding.etNoteTags.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.toString().contains("#untagged")) {
                    binding.etNoteTags.setTextColor(Color.RED)
                    // Set the state variable to true
                    isReservedTagPresent = true
                } else {
                    binding.etNoteTags.setTextColor(defaultTextColor)
                    // Set the state variable to false
                    isReservedTagPresent = false
                }
            }
        })
    }
    private fun saveNote() {
        if (isReservedTagPresent) {
            Toast.makeText(this, "Cannot save. Please remove the reserved '#untagged' tag.", Toast.LENGTH_LONG).show()
            return // Stop the function here
        }

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

        // --- Step 1: Parse all tags the user typed ---
        val tagRegex = Regex("#[\\w-/]+")
        val userTypedTags = tagRegex.findAll(tagsInput).map { it.value }.toSet()

        // --- Step 2: Apply the #untagged logic ---
        // First, explicitly remove the #untagged tag if the user typed it. The system will manage it.
        val cleanUserTags = userTypedTags - "#untagged"

        // Now, determine the final set of tags for the note.
        val finalTags = if (cleanUserTags.isEmpty()) {
            // If there are no other tags, the note must be marked as #untagged.
            setOf("#untagged")
        } else {
            // Otherwise, use the user's tags.
            cleanUserTags
        }

        // --- Step 3: Calculate the difference for the transaction ---
        val originalTags = existingNote?.tags?.toSet() ?: emptySet()
        val tagsToAdd = finalTags - originalTags
        val tagsToRemove = originalTags - finalTags
        Timber.d("Final tags: %s, Tags to add: %s, Tags to remove: %s", finalTags, tagsToAdd, tagsToRemove)

        // --- All database operations now happen inside a transaction ---
        firestore.runTransaction { transaction ->
            // --- PHASE 1: ALL READS MUST HAPPEN FIRST ---
            // For every tag we intend to add, we first READ its document to see if it exists.
            // We store the results of these reads in a map.
            val tagsToAddSnapshots = tagsToAdd.associateWith { tagName ->
                val tagRef = firestore.collection("tags").document(getTagDocId(userId, tagName))
                transaction.get(tagRef) // Execute the read
            }

            // (Note: We don't need to read for tagsToRemove, as we can safely assume they exist
            // and just decrement them. A write on a non-existent doc would fail later if that assumption is wrong).


            // --- PHASE 2: ALL WRITES HAPPEN AFTER ALL READS ---
            val noteRef = if (existingNote == null) {
                firestore.collection("notes").document()
            } else {
                firestore.collection("notes").document(existingNote!!.id!!)
            }

            // 1. Handle Tag Increments (using the data we read in Phase 1)
            tagsToAdd.forEach { tagName ->
                val tagRef = firestore.collection("tags").document(getTagDocId(userId, tagName))
                val tagDoc = tagsToAddSnapshots[tagName]!! // Get the pre-fetched snapshot
                if (tagDoc.exists()) {
                    transaction.update(tagRef, "count", FieldValue.increment(1))
                } else {
                    val newTagData = mapOf("userId" to userId, "tagName" to tagName, "count" to 1)
                    transaction.set(tagRef, newTagData)
                }
            }

            // 2. Handle Tag Decrements
            tagsToRemove.forEach { tagName ->
                val tagRef = firestore.collection("tags").document(getTagDocId(userId, tagName))
                transaction.update(tagRef, "count", FieldValue.increment(-1))
            }

            // 3. Save the Note Itself
            if (existingNote == null) {
                val newNote = Note(
                    id = noteRef.id, userId = userId, title = title, content = content,
                    tags = finalTags.toList(), createdAt = Date(), updatedAt = Date()
                )
                transaction.set(noteRef, newNote)
            } else {
                val updatedData = mapOf(
                    "title" to title, "content" to content,
                    "tags" to finalTags.toList(), "updatedAt" to Date()
                )
                transaction.update(noteRef, updatedData)
            }
            null // Return null from the transaction block
        }.addOnSuccessListener {
            Toast.makeText(this, "Note saved successfully", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener { e ->
            Timber.e(e, "Transaction failed: Error saving note")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }

    }


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

}
