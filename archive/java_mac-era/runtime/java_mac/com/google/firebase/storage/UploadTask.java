package com.google.firebase.storage;

// com.google.firebase.storage.UploadTask -- returned by StorageReference.putBytes
// / putStream / putFile. Real Storage streams the payload to the backend and
// completes with a TaskSnapshot describing the uploaded object. On Mac.apk there
// is no backend, so the upload fails cleanly with StorageException(ERROR_UNKNOWN)
// -- we never report bytes transferred or fabricate stored metadata.
//
// Signatures mirror the published Firebase Storage Android reference.
public class UploadTask extends StorageTask<UploadTask.TaskSnapshot> {

    private final StorageReference reference;
    private final long totalByteCount;

    UploadTask(StorageReference reference, long totalByteCount) {
        this.reference = reference;
        this.totalByteCount = totalByteCount;
        // No backend: the transfer cannot complete. Fail terminally so listeners
        // fire onFailure / onComplete instead of hanging forever.
        fail(StorageException.fromException(null));
    }

    public StorageReference getStorage() { return reference; }

    // com.google.firebase.storage.UploadTask.TaskSnapshot -- the progress/result
    // object. Since the upload never succeeds, getMetadata / getDownloadUrl read
    // null and the transferred count stays 0.
    public class TaskSnapshot {

        TaskSnapshot() {}

        public long getBytesTransferred() { return 0L; }

        public long getTotalByteCount() { return totalByteCount; }

        public StorageMetadata getMetadata() { return null; }

        public android.net.Uri getUploadSessionUri() { return null; }

        public StorageReference getStorage() { return reference; }

        public UploadTask getTask() { return UploadTask.this; }
    }
}
