package com.google.firebase.database;

import java.util.Collections;

// com.google.firebase.database.MutableData -- the mutable view of a location's
// data handed to a Transaction.Handler. On our offline shim the starting data
// is always empty; setValue mutates the in-memory view (so the handler can read
// back what it just wrote within the same transaction) but is never persisted.
//
// Signatures mirror the published Firebase RTDB Android reference.
public class MutableData {

    private Object value;
    private Object priority;

    MutableData() {}

    public boolean hasChildren() { return false; }

    public boolean hasChild(String path) { return false; }

    public long getChildrenCount() { return 0L; }

    public Iterable<MutableData> getChildren() {
        return Collections.<MutableData>emptyList();
    }

    public String getKey() { return null; }

    public Object getValue() { return value; }

    public <T> T getValue(Class<T> valueType) { return null; }

    public <T> T getValue(GenericTypeIndicator<T> t) { return null; }

    public MutableData child(String path) { return new MutableData(); }

    public void setValue(Object value) { this.value = value; }

    public Object getPriority() { return priority; }

    public void setPriority(Object priority) { this.priority = priority; }

    @Override public String toString() {
        return "MutableData { value = " + value + " }";
    }
}
