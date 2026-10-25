package com.google.firebase.storage;

// com.google.firebase.storage.FileDownloadTask -- returned by
// StorageReference.getFile(...). Real Storage streams the object into the target
// File. On Mac.apk there is no backend, so the download fails cleanly with
// StorageException(ERROR_UNKNOWN) and nothing is written to the target file.
//
// Signatures mirror the published Firebase Storage Android reference.
public class FileDownloadTask extends StorageTask<FileDownloadTask.TaskSnapshot> {

    private final StorageReference reference;

    FileDownloadTask(StorageReference reference) {
        this.reference = reference;
        fail(StorageException.fromException(null));
    }

    public StorageReference getStorage() { return reference; }

    public class TaskSnapshot {

        TaskSnapshot() {}

        public long getBytesTransferred() { return 0L; }

        public long getTotalByteCount() { return 0L; }

        public StorageReference getStorage() { return reference; }

        public FileDownloadTask getTask() { return FileDownloadTask.this; }
    }
}
