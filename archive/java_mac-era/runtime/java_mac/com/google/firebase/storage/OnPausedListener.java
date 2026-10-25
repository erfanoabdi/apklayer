package com.google.firebase.storage;

// com.google.firebase.storage.OnPausedListener<T> -- fired when a transfer is
// paused. Our transfers fail immediately rather than entering a paused state,
// so this is registered but never invoked.
public interface OnPausedListener<T> {
    void onPaused(T snapshot);
}
