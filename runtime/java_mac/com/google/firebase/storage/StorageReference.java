package com.google.firebase.storage;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

// com.google.firebase.storage.StorageReference -- a pointer to an object (or
// path prefix) in Cloud Storage. Path NAVIGATION (child / getParent / getRoot /
// getName / getPath / getBucket) is real and fully functional: it is pure string
// manipulation that needs no backend, so apps can build and compare references
// exactly as on a real device. Every DATA operation (upload / download /
// metadata / delete / list) fails cleanly because there is no Storage backend on
// a de-Googled device -- we never fabricate stored bytes, URLs, or metadata.
//
// Signatures mirror the published Firebase Storage Android reference.
public class StorageReference implements Comparable<StorageReference> {

    private final FirebaseStorage storage;
    private final String path; // normalized, no leading/trailing slash

    StorageReference(FirebaseStorage storage, String path) {
        this.storage = storage;
        this.path = normalize(path);
    }

    private static String normalize(String p) {
        if (p == null) return "";
        // Collapse repeated slashes and strip leading/trailing ones so name/path
        // derivation matches the published reference's behavior.
        StringBuilder sb = new StringBuilder();
        boolean prevSlash = false;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '/') {
                if (!prevSlash && sb.length() > 0) sb.append('/');
                prevSlash = true;
            } else {
                sb.append(c);
                prevSlash = false;
            }
        }
        int len = sb.length();
        while (len > 0 && sb.charAt(len - 1) == '/') sb.deleteCharAt(--len);
        return sb.toString();
    }

    // ---- navigation (real, local) ----------------------------------------

    public StorageReference child(String pathString) {
        if (pathString == null) {
            throw new NullPointerException("childName cannot be null");
        }
        String childPath = path.isEmpty() ? pathString : path + "/" + pathString;
        return new StorageReference(storage, childPath);
    }

    public StorageReference getParent() {
        if (path.isEmpty()) return null; // root has no parent
        int idx = path.lastIndexOf('/');
        String parentPath = idx < 0 ? "" : path.substring(0, idx);
        return new StorageReference(storage, parentPath);
    }

    public StorageReference getRoot() {
        return new StorageReference(storage, "");
    }

    public String getName() {
        if (path.isEmpty()) return "";
        int idx = path.lastIndexOf('/');
        return idx < 0 ? path : path.substring(idx + 1);
    }

    public String getPath() {
        return "/" + path;
    }

    public String getBucket() {
        return storage.getBucketName();
    }

    public FirebaseStorage getStorage() {
        return storage;
    }

    public java.net.URI getActiveUploadTasks() {
        // Real API returns the in-flight uploads; none exist here.
        return null;
    }

    // ---- uploads (return an UploadTask that fails cleanly) ---------------

    public UploadTask putBytes(byte[] bytes) {
        long n = bytes == null ? 0L : bytes.length;
        return new UploadTask(this, n);
    }

    public UploadTask putBytes(byte[] bytes, StorageMetadata metadata) {
        long n = bytes == null ? 0L : bytes.length;
        return new UploadTask(this, n);
    }

    public UploadTask putStream(InputStream stream) {
        return new UploadTask(this, -1L);
    }

    public UploadTask putStream(InputStream stream, StorageMetadata metadata) {
        return new UploadTask(this, -1L);
    }

    public UploadTask putFile(android.net.Uri uri) {
        return new UploadTask(this, -1L);
    }

    public UploadTask putFile(android.net.Uri uri, StorageMetadata metadata) {
        return new UploadTask(this, -1L);
    }

    public UploadTask putFile(android.net.Uri uri, StorageMetadata metadata,
                              android.net.Uri existingUploadUri) {
        return new UploadTask(this, -1L);
    }

    // ---- downloads --------------------------------------------------------

    public Task<byte[]> getBytes(long maxDownloadSizeBytes) {
        return failedTask();
    }

    public StreamDownloadTask getStream() {
        return new StreamDownloadTask(this);
    }

    public StreamDownloadTask getStream(StreamDownloadTask.StreamProcessor processor) {
        return new StreamDownloadTask(this);
    }

    public FileDownloadTask getFile(File destinationFile) {
        return new FileDownloadTask(this);
    }

    public FileDownloadTask getFile(android.net.Uri destinationUri) {
        return new FileDownloadTask(this);
    }

    public Task<android.net.Uri> getDownloadUrl() {
        return failedTask();
    }

    // ---- metadata ---------------------------------------------------------

    public Task<StorageMetadata> getMetadata() {
        return failedTask();
    }

    public Task<StorageMetadata> updateMetadata(StorageMetadata metadata) {
        return failedTask();
    }

    public Task<Void> delete() {
        return failedTask();
    }

    // ---- listing (resolves to an EMPTY result, not a failure) ------------

    public Task<ListResult> list(int maxResults) {
        return emptyListTask();
    }

    public Task<ListResult> list(int maxResults, String pageToken) {
        return emptyListTask();
    }

    public Task<ListResult> listAll() {
        return emptyListTask();
    }

    public List<UploadTask> getActiveUploadTasksUnderRoot() {
        return new ArrayList<UploadTask>();
    }

    public List<FileDownloadTask> getActiveDownloadTasks() {
        return new ArrayList<FileDownloadTask>();
    }

    // ---- helpers ----------------------------------------------------------

    // A gms Task already in the failed state carrying a StorageException. We
    // build it via TaskCompletionSource (NOT TaskImpl internals) per the bucket
    // bridging rule.
    private <T> Task<T> failedTask() {
        TaskCompletionSource<T> src = new TaskCompletionSource<T>();
        src.setException(StorageException.fromException(null));
        return src.getTask();
    }

    private Task<ListResult> emptyListTask() {
        TaskCompletionSource<ListResult> src = new TaskCompletionSource<ListResult>();
        src.setResult(new ListResult());
        return src.getTask();
    }

    @Override public int compareTo(StorageReference o) {
        return path.compareTo(o.path);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StorageReference)) return false;
        StorageReference r = (StorageReference) o;
        return path.equals(r.path) && storage.equals(r.storage);
    }

    @Override public int hashCode() {
        return 31 * storage.hashCode() + path.hashCode();
    }

    @Override public String toString() {
        return "gs://" + getBucket() + "/" + path;
    }
}
