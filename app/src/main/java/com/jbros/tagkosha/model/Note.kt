package com.jbros.tagkosha.model

import com.google.firebase.firestore.ServerTimestamp
import java.io.Serializable
import java.util.Date

data class Note(
    var id: String? = null,
    val userId: String? = null,
    var title: String = "",
    var content: String = "",
    var tags: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp var updatedAt: Date? = null
) : Serializable // Implement Serializable to pass this object between activities
