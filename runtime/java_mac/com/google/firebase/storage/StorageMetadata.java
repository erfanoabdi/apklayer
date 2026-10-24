package com.google.firebase.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// com.google.firebase.storage.StorageMetadata -- metadata about a Storage
// object (content type, cache control, custom key/values, etc.). On Mac.apk
// this is a real, fully-functional value object: apps build it via Builder to
// attach to an upload, and read it back from a (failed) getMetadata Task. The
// server-populated fields (size, generation, timestamps) read empty/zero
// because there is no backend that ever populated them.
//
// Signatures mirror the published Firebase Storage Android reference.
public class StorageMetadata {

    private final StorageReference reference;
    private final String contentType;
    private final String cacheControl;
    private final String contentDisposition;
    private final String contentEncoding;
    private final String contentLanguage;
    private final Map<String, String> customMetadata;

    StorageMetadata() {
        this(null, null, null, null, null, null, new HashMap<String, String>());
    }

    StorageMetadata(StorageReference reference, String contentType, String cacheControl,
                    String contentDisposition, String contentEncoding, String contentLanguage,
                    Map<String, String> customMetadata) {
        this.reference = reference;
        this.contentType = contentType;
        this.cacheControl = cacheControl;
        this.contentDisposition = contentDisposition;
        this.contentEncoding = contentEncoding;
        this.contentLanguage = contentLanguage;
        this.customMetadata = customMetadata == null
                ? new HashMap<String, String>() : new HashMap<String, String>(customMetadata);
    }

    public StorageReference getReference() { return reference; }

    public String getBucket() {
        return reference == null ? null : reference.getBucket();
    }

    public String getName() {
        return reference == null ? null : reference.getName();
    }

    public String getPath() {
        return reference == null ? null : reference.getPath();
    }

    // Server-assigned fields: never populated without a backend.
    public String getGeneration() { return null; }
    public String getMetadataGeneration() { return null; }
    public long getCreationTimeMillis() { return 0L; }
    public long getUpdatedTimeMillis() { return 0L; }
    public long getSizeBytes() { return 0L; }
    public String getMd5Hash() { return null; }

    public String getContentType() { return contentType; }
    public String getCacheControl() { return cacheControl; }
    public String getContentDisposition() { return contentDisposition; }
    public String getContentEncoding() { return contentEncoding; }
    public String getContentLanguage() { return contentLanguage; }

    public String getCustomMetadata(String key) {
        return customMetadata.get(key);
    }

    public Set<String> getCustomMetadataKeys() {
        return Collections.unmodifiableSet(customMetadata.keySet());
    }

    public static class Builder {

        private StorageReference reference;
        private String contentType;
        private String cacheControl;
        private String contentDisposition;
        private String contentEncoding;
        private String contentLanguage;
        private final Map<String, String> customMetadata = new HashMap<String, String>();

        public Builder() {}

        public Builder(StorageMetadata source) {
            if (source != null) {
                this.reference = source.reference;
                this.contentType = source.contentType;
                this.cacheControl = source.cacheControl;
                this.contentDisposition = source.contentDisposition;
                this.contentEncoding = source.contentEncoding;
                this.contentLanguage = source.contentLanguage;
                this.customMetadata.putAll(source.customMetadata);
            }
        }

        public Builder setContentType(String contentType) {
            this.contentType = contentType; return this;
        }
        public Builder setCacheControl(String cacheControl) {
            this.cacheControl = cacheControl; return this;
        }
        public Builder setContentDisposition(String contentDisposition) {
            this.contentDisposition = contentDisposition; return this;
        }
        public Builder setContentEncoding(String contentEncoding) {
            this.contentEncoding = contentEncoding; return this;
        }
        public Builder setContentLanguage(String contentLanguage) {
            this.contentLanguage = contentLanguage; return this;
        }
        public Builder setCustomMetadata(String key, String value) {
            if (value == null) customMetadata.remove(key);
            else customMetadata.put(key, value);
            return this;
        }

        public String getContentType() { return contentType; }
        public String getCacheControl() { return cacheControl; }
        public String getContentDisposition() { return contentDisposition; }
        public String getContentEncoding() { return contentEncoding; }
        public String getContentLanguage() { return contentLanguage; }

        public StorageMetadata build() {
            return new StorageMetadata(reference, contentType, cacheControl,
                    contentDisposition, contentEncoding, contentLanguage, customMetadata);
        }
    }
}
