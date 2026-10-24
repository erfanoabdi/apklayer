package com.google.firebase.storage;

import java.io.InputStream;

// com.google.firebase.storage.StreamDownloadTask -- returned by
// StorageReference.getStream(...). Real Storage opens an InputStream over the
// downloading object. On Mac.apk there is no backend, so the task fails cleanly
// with StorageException(ERROR_UNKNOWN); the snapshot's stream is null.
//
// Signatures mirror the published Firebase Storage Android reference.
public class StreamDownloadTask extends StorageTask<StreamDownloadTask.TaskSnapshot> {

    private final StorageReference reference;

    StreamDownloadTask(StorageReference reference) {
        this.reference = reference;
        fail(StorageException.fromException(null));
    }

    public StorageReference getStorage() { return reference; }

    // The published API lets callers supply a processor that consumes the stream
    // as it arrives. With no backend there is no stream to process, but the
    // setter is kept so app code compiles.
    public interface StreamProcessor {
        void doInBackground(TaskSnapshot state, InputStream stream) throws java.io.IOException;
    }

    public class TaskSnapshot {

        TaskSnapshot() {}

        public long getBytesTransferred() { return 0L; }

        public long getTotalByteCount() { return 0L; }

        public InputStream getStream() { return null; }

        public StorageReference getStorage() { return reference; }

        public StreamDownloadTask getTask() { return StreamDownloadTask.this; }
    }
}
