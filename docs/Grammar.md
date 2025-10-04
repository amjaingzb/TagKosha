Of course. Here is a detailed summary of all the design decisions and conclusions from our discussion. This document will serve as the official specification for the TagKosha tagging system.

---

### **TagKosha: Tagging System Design Specification**

#### **1. Core Philosophy**

The TagKosha tagging system is designed to be a **"Smart Structure"** system. Its primary goal is not just to search for text, but to understand the hierarchical relationships between tags. This is achieved by enforcing a clear and consistent set of rules (a "grammar") that enables powerful features for organization, browsing, and discovery, similar to the label system in Gmail.

---

#### **2. The Official Tag Grammar**

The following rules define a valid tag and how user input is parsed.

**2.1. Character Categories**

| Category | Character(s) | Role & Behavior |
| :--- | :--- | :--- |
| **Tag Identifier** | `#` | The only character that signifies the beginning of a new tag. |
| **Hierarchy Separator**| `/` | The only character used to create parent-child relationships within a tag. |
| **Allowed Characters**| `a-z`, `A-Z`, `0-9`, `_`, `-` | The set of characters allowed within a tag name segment. |
| **Whitespace**| Space, Tab, Newline | Used to separate distinct, independent tags from each other. |
| **Forbidden**| All other characters | These are not allowed within a tag name and act as separators, ending a tag. |

**2.2. Tag Structure Examples**

*   **Simple Tag:** `#shopping`
*   **Multi-Word Tag:** `#to-do-list` or `#project_alpha`
*   **Hierarchical Tag:** `#work/projectA/tasks`

---

#### **3. System Behavior and Data Storage**

**3.1. Multi-Tagging (Breadth)**
A single note can have multiple independent tags. These are stored in an array within the note's document in Firestore.
*   **User Input Example:** A note about a dark brown winter coat.
*   **Tags Applied:** `#winter, #clothing/outerwear/coat, #color/dark/brown`
*   **Firestore Storage:** `tags: ["#winter", "#clothing/outerwear/coat", "#color/dark/brown"]`

**3.2. Hierarchical Tagging (Depth)**
The hierarchy is not stored as separate tags. It is stored as a single string containing the `/` separator. The hierarchical relationship is an emergent property created by our search and query logic.
*   **Tag:** `#work/projectA/tasks`
*   **System Understanding:** "tasks" is a child of "projectA", which is a child of "work".
*   **Intermediate Tags:** The system does **not** automatically create separate tags for `#work` or `#work/projectA`.

---

#### **4. Search Functionality: A Phased Approach**

To provide a modern "search-as-you-type" experience, we will implement the functionality in two phases.

**Phase 1: Firestore-Native Prefix Search**
*   **Technology:** Cloud Firestore native queries.
*   **Mechanism:** As the user types, the app will query for all tags that **start with** the typed text.
*   **User Experience:**
    *   Typing `#w` shows `[#work, #winter]`.
    *   Typing `#work/p` shows `[#work/projectA, #work/projectB]`.
    *   This provides instant feedback and feels responsive.
*   **Limitation:** This method cannot find substrings. Typing `projectA` will **not** find the tag `#work/projectA`.

**Later Phase: Advanced Full-Text Search**
*   **Technology:** Integration with a specialized third-party search service like **Algolia**.
*   **Architecture:** A Cloud Function will be set up to automatically sync the `tags` collection from Firestore to an Algolia search index. The app will query Algolia directly for search, not Firestore.
*   **User Experience:**
    *   Provides a true "contains" search. Typing `projectA` will instantly find `#work/projectA`.
    *   Offers advanced features like typo tolerance and relevance ranking.
    *   This will fulfill the user expectation of a powerful, modern search that "just works."

---

#### **5. User Interface (UI) and User Experience (UX) Plan**

The "Smart Structure" system enables a superior UI.

**5.1. Tag Input Field**
*   **Real-Time Validation (Future Goal):** To provide a better experience than silently ignoring characters, the UI should give instant feedback. As a user types a "Forbidden" character (e.g., `!`, `.`, `@`), it will be visually flagged within the `EditText`, for example, with a red underline, indicating it is not a valid part of a tag name.

**5.2. Tag Display**
*   On a note, tags will be displayed as a simple, clean list of "pills" or "chips," each showing a full tag string (e.g., `[#winter] [#work/projectA]`).

**5.3. Tag Browsing (Key Future Feature)**
*   Because the system understands the `/` hierarchy, we can build a dedicated "Tags" screen that displays all user tags in a collapsible tree structure. This allows for discovery and organization without relying on search.
*   **Example UI Visualization:**
    ```
    [+] work
        [+] projectA
            - tasks
            - design
        - projectB
    [+] personal
        - finances
    - shopping
    ```
