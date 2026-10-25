package com.google.firebase.database;

// com.google.firebase.database.ChildEventListener -- callback for child-level
// change events. Signatures mirror the published Firebase RTDB Android
// reference (the previousChildName is nullable, given for added/changed/moved).
public interface ChildEventListener {
    void onChildAdded(DataSnapshot snapshot, String previousChildName);
    void onChildChanged(DataSnapshot snapshot, String previousChildName);
    void onChildRemoved(DataSnapshot snapshot);
    void onChildMoved(DataSnapshot snapshot, String previousChildName);
    void onCancelled(DatabaseError error);
}
