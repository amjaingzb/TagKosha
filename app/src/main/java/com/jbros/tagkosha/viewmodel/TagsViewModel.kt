package com.jbros.tagkosha.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import timber.log.Timber
const val TAG_SANITY_LIMIT = 5000 // Define a constant for our limit

class TagsViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()
    private var tagsListener: ListenerRegistration? = null

    // Private MutableLiveData that is only exposed as a non-mutable LiveData
    private val _tags = MutableLiveData<List<String>>()
    val tags: LiveData<List<String>> = _tags

    init {
        startTagsListener()
    }

    private fun startTagsListener() {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            Timber.w("User is not logged in, cannot fetch tags.")
            _tags.postValue(emptyList())
            return
        }

        val query = firestore.collection("tags").whereEqualTo("userId", userId)

        // The listener will keep the data fresh in real-time
        tagsListener = query.addSnapshotListener { snapshots, error ->
            if (error != null) {
                Timber.e(error, "Error listening for tag updates")
                _tags.postValue(emptyList())
                return@addSnapshotListener
            }

            if (snapshots != null) {
                val tagNames = snapshots.documents.mapNotNull { it.getString("tagName") }.sorted()
                // --- THE SAFEGUARD CHECK ---
                if (tagNames.size > TAG_SANITY_LIMIT) {
                    // Log a severe warning. This is our "canary in the coal mine."
                    Timber.e("CRITICAL: User has %d tags, exceeding the sanity limit of %d. Client-side performance may be affected.", tagNames.size, TAG_SANITY_LIMIT)
                    // In a future, more advanced app, we could even send a non-fatal
                    // crash report to Crashlytics here to alert us (the developers).
                }
                // --- END SAFEGUARD ---
                _tags.postValue(tagNames) // Post the new list to observers
                Timber.d("Tag list updated. Found %d tags.", tagNames.size)
            }
        }
    }

    // This is called automatically when the ViewModel is no longer used, preventing memory leaks
    override fun onCleared() {
        super.onCleared()
        Timber.d("TagsViewModel cleared. Removing Firestore listener.")
        tagsListener?.remove()
    }
}
