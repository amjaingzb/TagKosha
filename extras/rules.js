rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {

    // --- USERS COLLECTION ---
    // Rule Change: Combined 'create' and 'update' for simplicity.
    // The previous 'create' rule was too strict and caused the sign-up error.
    match /users/{userId} {
      // Allow a user to create their own document, and then read/update it later.
      // This is secure because the document ID must match their auth UID.
      allow create, update, read: if request.auth != null && request.auth.uid == userId;

      // It's still good practice to prevent users from deleting their own account record.
      allow delete: if false;
    }


    // --- NOTES COLLECTION ---
    // Your rules here were already perfect and highly secure. No changes needed.
    match /notes/{noteId} {
      // Allow read, update, delete only if the user is the owner.
      allow read, update, delete: if request.auth != null
                                  && resource.data.userId == request.auth.uid;

      // Allow create only if the new note has a userId matching the user's ID.
      allow create: if request.auth != null
                    && request.resource.data.userId == request.auth.uid;
    }


    // --- TAGS COLLECTION ---
    // Rule Change: Added 'update' permission.
    // Our app uses '.set()' which acts as an "upsert" (create or update).
    // This rule allows re-saving an existing tag, which happens when editing a note.
    match /tags/{tagId} {
      // Allow read or delete only if the user is the owner.
      allow read, delete: if request.auth != null
                         && resource.data.userId == request.auth.uid;

      // Allow create OR update if the submitted tag data has a userId matching the user's ID.
      // This covers both new tags and existing tags.
      allow create, update: if request.auth != null
                           && request.resource.data.userId == request.auth.uid;
    }
  }
}
