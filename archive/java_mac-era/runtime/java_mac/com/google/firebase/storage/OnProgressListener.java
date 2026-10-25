package com.google.firebase.storage;

// com.google.firebase.storage.OnProgressListener<T> -- fired as bytes transfer.
// On Mac.apk (no Storage backend) a transfer never makes progress, so this is
// registered but never invoked.
public interface OnProgressListener<T> {
    void onProgress(T snapshot);
}
