package com.google.firebase.firestore;

// com.google.firebase.firestore.DocumentChange -- describes a single delta
// inside a QuerySnapshot's getDocumentChanges(). On our offline-only shim
// snapshots are always empty so the change list is always empty too, but apps
// reference the type and the Type enum, so ship the full surface.
public class DocumentChange {

    // NO_INDEX is the published sentinel for a removed doc's new index (and an
    // added doc's old index).
    public static final int NO_INDEX = -1;

    public enum Type {
        ADDED,
        MODIFIED,
        REMOVED
    }

    private final QueryDocumentSnapshot document;
    private final Type type;
    private final int oldIndex;
    private final int newIndex;

    DocumentChange(QueryDocumentSnapshot document, Type type, int oldIndex, int newIndex) {
        this.document = document;
        this.type = type;
        this.oldIndex = oldIndex;
        this.newIndex = newIndex;
    }

    public Type getType() { return type; }

    public QueryDocumentSnapshot getDocument() { return document; }

    public int getOldIndex() { return oldIndex; }

    public int getNewIndex() { return newIndex; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentChange)) return false;
        DocumentChange c = (DocumentChange) o;
        return oldIndex == c.oldIndex && newIndex == c.newIndex
            && type == c.type
            && (document == null ? c.document == null : document.equals(c.document));
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (document != null ? document.hashCode() : 0);
        result = 31 * result + oldIndex;
        result = 31 * result + newIndex;
        return result;
    }
}
