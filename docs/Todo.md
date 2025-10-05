Bullet points 
1. onSnapShot concept so that I am updating things in real time whenerevr there is any change in db from other device .. 
2. Build content search (or may be not) . refer point 3 or securityConcern
3. securityConcern : encode contents in app . only keep tags decoded outside the app so firebase or algoia has reduced risk of my data 
5. desktop app 
6. free tier users - 1 device - sqlite - personal space to backup db .. or maybe tagKoshaLite.. or license based
7. implement storage limits - display free %age remaining
8. Grammar display as a tool tip and some detailed help also ... also whatever is error highlight that like a red line in spell checks 
9. (cost in terms of firebase . I am assuming if the data is fully cached then this search will be fully local ).. onSnapShot listener should help to maintain data updates..
10. Whatever we are doing for tags will we not have to do that same for notes also ? (I mean why should we fetch the notes without checking in local cache) Also should we start listener for the currently active note and stop that when a new note is active. that will allow note to be synced as well ..
12. Borrow release version from workkonds, about screen from rkm attendance app , also borrow the concept of rendering test distributions of app useless by controlling the base version in meta data of firebase db (we had this concept in wordkons)
12.5) Pagination: We need to replace our temporary limit(50) fix with proper "infinite scrolling" for the main note list. This is the crucial next step to ensure the app performs well for users with hundreds or thousands of notes.
13. Full-Text Search: After pagination is solid, the next major feature should be implementing a search bar that allows users to find notes by searching for text within their titles and content. This will complement our tag filtering and make the app a complete note-finding tool.

14. Strict Offline Restrictions on Edits/Deletes: We explicitly decided that users cannot edit or delete existing notes while offline. They can only create new ones. This was the most critical simplification, as it completely avoids the complex and error-prone logic of trying to merge counter decrements and updates when the device comes back online.
15 . Handling of Zero-Count Tags: We have not yet implemented a mechanism to clean up and delete a tag document from Firestore when its count falls to zero. Currently, these tags will remain in the database with a count of 0, which is harmless but not ideal for long-term data hygiene.
16. cloud function to deal with counter
17. majorly enhance a new note or edit note to support html or rich edits , pasting of url , pasting of clipboard image data , clipboard url data ... firestore 5gb space 


=========================DONE===================================
4. Show available tags like ankit was showing with theor count in bracket
11.. in tag broswer - sorted listing , count of notes as well as alphabet as well as tree 
2. Add a #untagged to every note without a tag
