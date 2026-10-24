package com.google.firebase.database;

import java.util.Collections;

// com.google.firebase.database.DataSnapshot -- an immutable snapshot of the
// data at a database location. On our offline-only (de-Googled) shim we never
// fabricate remote data, so every snapshot delivered by the shim is EMPTY:
// exists()=false, getValue()=null, getChildren() empty, getChildrenCount()=0.
// This mirrors the published Firebase RTDB Android reference's public surface
// so listeners receive a real (empty) snapshot rather than crashing on a
// missing type.
public class DataSnapshot {

    private final DatabaseReference ref;

    DataSnapshot(DatabaseReference ref) {
        this.ref = ref;
    }

    // Factory for an empty snapshot rooted at a reference.
    static DataSnapshot empty(DatabaseReference ref) {
        return new DataSnapshot(ref);
    }

    public boolean exists() { return false; }

    public Object getValue() { return null; }

    public Object getValue(boolean useExportFormat) { return null; }

    public <T> T getValue(Class<T> valueType) { return null; }

    public <T> T getValue(GenericTypeIndicator<T> t) { return null; }

    // A child snapshot at the given relative path -- also empty.
    public DataSnapshot child(String path) {
        return new DataSnapshot(ref == null ? null : ref.child(path));
    }

    public boolean hasChild(String path) { return false; }

    public boolean hasChildren() { return false; }

    public long getChildrenCount() { return 0L; }

    public Iterable<DataSnapshot> getChildren() {
        return Collections.<DataSnapshot>emptyList();
    }

    public String getKey() {
        return ref == null ? null : ref.getKey();
    }

    public DatabaseReference getRef() { return ref; }

    public Object getPriority() { return null; }

    @Override public String toString() {
        return "DataSnapshot { key = " + getKey() + ", value = null }";
    }
}
