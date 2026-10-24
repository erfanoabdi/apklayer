package com.google.firebase.storage;

import com.google.firebase.FirebaseApp;

import java.util.HashMap;
import java.util.Map;

// com.google.firebase.storage.FirebaseStorage -- entry point to the Cloud
// Storage SDK. On Mac.apk it is a real, fully-constructable client whose
// references navigate locally and whose data operations fail cleanly (no
// backend, no fabricated data). getInstance is cached per app+bucket so
// identity-comparing apps see a stable instance, mirroring the published SDK.
//
// Signatures mirror the published Firebase Storage Android reference
// (developer.android.com/reference/com/google/firebase/storage/FirebaseStorage).
public class FirebaseStorage {

    private static final Map<String, FirebaseStorage> INSTANCES =
            new HashMap<String, FirebaseStorage>();

    private final FirebaseApp app;
    private final String bucketUrl; // gs:// url or null for the default bucket
    private long maxUploadRetryTimeMillis = 600_000L;       // 10 min, SDK default
    private long maxDownloadRetryTimeMillis = 600_000L;
    private long maxOperationRetryTimeMillis = 120_000L;    // 2 min, SDK default

    private FirebaseStorage(FirebaseApp app, String bucketUrl) {
        this.app = app;
        this.bucketUrl = bucketUrl;
    }

    public static synchronized FirebaseStorage getInstance() {
        return getInstance(FirebaseApp.getInstance(), null);
    }

    public static synchronized FirebaseStorage getInstance(String url) {
        return getInstance(FirebaseApp.getInstance(), url);
    }

    public static synchronized FirebaseStorage getInstance(FirebaseApp app) {
        return getInstance(app, null);
    }

    public static synchronized FirebaseStorage getInstance(FirebaseApp app, String url) {
        String key = (app == null ? "[null]" : app.getName()) + "|" + (url == null ? "" : url);
        FirebaseStorage s = INSTANCES.get(key);
        if (s == null) {
            s = new FirebaseStorage(app, url);
            INSTANCES.put(key, s);
        }
        return s;
    }

    public FirebaseApp getApp() { return app; }

    // ---- references -------------------------------------------------------

    public StorageReference getReference() {
        return new StorageReference(this, "");
    }

    public StorageReference getReference(String location) {
        if (location == null) {
            throw new NullPointerException("location cannot be null");
        }
        // A gs:// or https:// argument is a full URL, not a relative path.
        if (location.startsWith("gs://") || location.startsWith("http://")
                || location.startsWith("https://")) {
            return getReferenceFromUrl(location);
        }
        return new StorageReference(this, location);
    }

    public StorageReference getReferenceFromUrl(String fullUrl) {
        if (fullUrl == null) {
            throw new NullPointerException("location cannot be null");
        }
        // Strip scheme + bucket host so the object path roots the reference.
        // The bucket itself is irrelevant offline (no connection); the path is
        // what matters for child navigation and name derivation.
        String path = fullUrl;
        int schemeIdx = path.indexOf("://");
        if (schemeIdx >= 0) {
            path = path.substring(schemeIdx + 3);
            int slash = path.indexOf('/');
            path = slash < 0 ? "" : path.substring(slash + 1);
        }
        // https form may carry the object after "/o/"; decode that segment.
        int oIdx = path.indexOf("/o/");
        if (oIdx >= 0) {
            path = path.substring(oIdx + 3);
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
        }
        return new StorageReference(this, path);
    }

    // The bucket name used when stringifying references. Derived from the
    // configured url or the FirebaseOptions storage bucket, falling back to the
    // application id; never a network lookup.
    String getBucketName() {
        if (bucketUrl != null && bucketUrl.startsWith("gs://")) {
            String rest = bucketUrl.substring(5);
            int slash = rest.indexOf('/');
            return slash < 0 ? rest : rest.substring(0, slash);
        }
        if (app != null && app.getOptions() != null) {
            String bucket = app.getOptions().getStorageBucket();
            if (bucket != null && !bucket.isEmpty()) return bucket;
        }
        return "";
    }

    // ---- retry / timeout settings (recorded, no backend to apply them to) -

    public long getMaxUploadRetryTimeMillis() { return maxUploadRetryTimeMillis; }
    public void setMaxUploadRetryTimeMillis(long maxTransferRetryMillis) {
        this.maxUploadRetryTimeMillis = maxTransferRetryMillis;
    }

    public long getMaxDownloadRetryTimeMillis() { return maxDownloadRetryTimeMillis; }
    public void setMaxDownloadRetryTimeMillis(long maxTransferRetryMillis) {
        this.maxDownloadRetryTimeMillis = maxTransferRetryMillis;
    }

    public long getMaxOperationRetryTimeMillis() { return maxOperationRetryTimeMillis; }
    public void setMaxOperationRetryTimeMillis(long maxOperationRetryMillis) {
        this.maxOperationRetryTimeMillis = maxOperationRetryMillis;
    }

    public void useEmulator(String host, int port) {
        // No backend, no emulator to point at; recorded as a no-op.
    }
}
