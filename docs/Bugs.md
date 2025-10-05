1. When we add a new note , depending upon the filters , the new note may be 'invisble'
2. Add a #untagged to every note without a tag
3. paginations and 500 records issue. refer tag-popularity.md . my doubts in proposal are whether it wil be only for the main query or for otehrs aslo. anyways better design is pagination. even there will it work from offline or online... or it does not matter ?
4.Active filter list - #work , #work/kitchen ... ignore #work... 
5. Code refactoring must : deleteNote is duplicate in both NoteEditorActivity , MainActivity
6. Improve edit note - suggest tags




============ PENDING COST OPTIMIZATIONS================

search for lastUpdatedByDeviceId in tag-popularity.md . right now we are going ahead with count() everytime the tag is clicked but eventually we should optimize it by device id. Ais concerns of drifiting seem very corener scenario and could be mitigated by a repair from another device or  may be by then we will enable cloud functions and this will go away ... 
