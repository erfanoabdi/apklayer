package com.google.firebase.database;

// com.google.firebase.database.ValueEventListener -- callback for full-value
// reads. Signatures mirror the published Firebase RTDB Android reference.
public interface ValueEventListener {
    void onDataChange(DataSnapshot snapshot);
    void onCancelled(DatabaseError error);
}
