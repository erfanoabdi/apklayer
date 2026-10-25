package com.google.firebase.database;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.tasks.GmsTaskExecutors;

import java.util.Map;

// com.google.firebase.database.OnDisconnect -- registers writes to be performed
// by the server when this client disconnects. On our offline shim there is no
// server connection to disconnect from, so every registration resolves
// SUCCESSFULLY (the request is "accepted") but is never executed -- the honest
// no-backend behavior. This lets presence-system apps install onDisconnect
// handlers at startup without exceptions.
//
// Signatures mirror the published Firebase RTDB Android reference.
public class OnDisconnect {

    OnDisconnect() {}

    public Task<Void> setValue(Object value) { return Tasks.forResult(null); }
    public Task<Void> setValue(Object value, Object priority) { return Tasks.forResult(null); }
    public Task<Void> setValue(Object value, String priority) { return Tasks.forResult(null); }
    public Task<Void> setValue(Object value, double priority) { return Tasks.forResult(null); }

    public void setValue(Object value, DatabaseReference.CompletionListener listener) {
        complete(listener);
    }
    public void setValue(Object value, Object priority,
                         DatabaseReference.CompletionListener listener) {
        complete(listener);
    }

    public Task<Void> updateChildren(Map<String, Object> update) { return Tasks.forResult(null); }
    public void updateChildren(Map<String, Object> update,
                               DatabaseReference.CompletionListener listener) {
        complete(listener);
    }

    public Task<Void> removeValue() { return Tasks.forResult(null); }
    public void removeValue(DatabaseReference.CompletionListener listener) {
        complete(listener);
    }

    public Task<Void> cancel() { return Tasks.forResult(null); }
    public void cancel(DatabaseReference.CompletionListener listener) {
        complete(listener);
    }

    private void complete(final DatabaseReference.CompletionListener listener) {
        if (listener == null) return;
        GmsTaskExecutors.defaultExecutor().execute(new Runnable() {
            @Override public void run() {
                try { listener.onComplete(null, null); } catch (Throwable ignored) {}
            }
        });
    }
}
