rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {

    // --- USERS COLLECTION ---
    // Users can read any user's public profile (if you want this).
    // They can only create and update their OWN user document.
    match /users/{userId} {
      allow read: if request.auth != null; // Allows users to see others' names, etc.
      
      // A user can create their own profile document.
      // The document ID must match their authentication UID.
      allow create: if request.auth != null && request.auth.uid == userId;

      // A user can only update their own profile document.
      allow update: if request.auth != null && request.auth.uid == userId;

      // Generally, users should not be able to delete their own profile document.
      allow delete: if false; 
    }


    // --- NOTES COLLECTION ---
    // Users have full control (create, read, update, delete) over their own notes only.
    match /notes/{noteId} {

      // A user can READ, UPDATE, or DELETE a note only if they are the owner.
      // We check the 'userId' field inside the document that already exists.
      allow read, update, delete: if request.auth != null 
                                  && resource.data.userId == request.auth.uid;

      // A user can CREATE a note only if the new note they are submitting
      // has a 'userId' field that matches their own ID. This prevents spoofing.
      allow create: if request.auth != null 
                    && request.resource.data.userId == request.auth.uid;
    }


    // --- TAGS COLLECTION ---
    // Users can create, read, and delete their own unique tags.
    // We assume tags are not updatable (e.g., you can't rename a tag).
    match /tags/{tagId} {

      // A user can READ or DELETE a tag only if they are the owner.
      allow read, delete: if request.auth != null 
                         && resource.data.userId == request.auth.uid;

      // A user can CREATE a tag only if the new tag they are submitting
      // has a 'userId' field that matches their own ID.
      allow create: if request.auth != null 
                    && request.resource.data.userId == request.auth.uid;
      
      // It's good practice to explicitly deny actions you don't want.
      // Here, we prevent anyone from ever updating a tag document.
      allow update: if false;
    }
  }
}
