package com.google.firebase.firestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// com.google.firebase.firestore.FieldPath -- a dotted path into a document's
// fields. Pure client-side value type (faithful). We keep the segments so
// equality/ordering behave; nothing here reaches a backend.
public final class FieldPath {
    private final List<String> segments;

    private FieldPath(List<String> segments) {
        this.segments = segments;
    }

    // Builds a path from individual, un-escaped segments. Real Firestore
    // validates non-empty; we mirror that.
    public static FieldPath of(String... fieldNames) {
        if (fieldNames == null || fieldNames.length == 0) {
            throw new IllegalArgumentException(
                "Invalid field path. Provided path must not be empty.");
        }
        for (int i = 0; i < fieldNames.length; i++) {
            if (fieldNames[i] == null || fieldNames[i].isEmpty()) {
                throw new IllegalArgumentException(
                    "Invalid field name at argument " + (i + 1)
                    + ". Field names must not be null or empty.");
            }
        }
        return new FieldPath(new ArrayList<>(Arrays.asList(fieldNames)));
    }

    // The sentinel path that refers to a document's ID in queries / ordering.
    public static FieldPath documentId() {
        return new FieldPath(Arrays.asList("__name__"));
    }

    List<String> getSegments() { return segments; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldPath)) return false;
        return segments.equals(((FieldPath) o).segments);
    }

    @Override
    public int hashCode() { return segments.hashCode(); }

    @Override
    public String toString() { return String.join(".", segments); }
}
