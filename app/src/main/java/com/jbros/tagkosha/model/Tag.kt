package com.jbros.tagkosha.model

import com.google.firebase.firestore.PropertyName // <-- ADD THIS IMPORT

// A simple data class to hold the full tag information from Firestore
data class Tag(
    // --- THIS ANNOTATION IS THE FIX ---
    // It tells Firestore: "The field 'tagName' in the database document
    // should be mapped to this 'name' property in our Kotlin class."
    @get:PropertyName("tagName") @set:PropertyName("tagName")
    var name: String = "",

    var count: Long = 0, // Use Long as Firestore's number type is 64-bit

    @get:PropertyName("userId") @set:PropertyName("userId")
    var userId: String = ""
) {
    // Adding a no-argument constructor is good practice for Firebase's toObject() mapping
    constructor() : this("", 0, "")
}