package com.jbros.tagkosha.model

data class TagNode(
    val fullName: String, // e.g., "#work/projectA"
    val displayName: String, // e.g., "projectA"
    val level: Int, // Indentation level (0 for root, 1 for child, etc.)
    val children: MutableList<TagNode> = mutableListOf(),
    var isExpanded: Boolean = false
)
