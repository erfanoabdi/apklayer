package com.google.firebase.firestore;

// com.google.firebase.firestore.SnapshotMetadata -- per-snapshot flags. On the
// offline-only shim, reads resolve from the local cache and there are never
// pending writes attached to a read result, so a read snapshot reports
// hasPendingWrites=false, isFromCache=true (it came from the local layer, not
// the server). Faithful client-side value type.
public class SnapshotMetadata {
    private final boolean hasPendingWrites;
    private final boolean isFromCache;

    SnapshotMetadata(boolean hasPendingWrites, boolean isFromCache) {
        this.hasPendingWrites = hasPendingWrites;
        this.isFromCache = isFromCache;
    }

    public boolean hasPendingWrites() { return hasPendingWrites; }

    public boolean isFromCache() { return isFromCache; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SnapshotMetadata)) return false;
        SnapshotMetadata m = (SnapshotMetadata) o;
        return hasPendingWrites == m.hasPendingWrites && isFromCache == m.isFromCache;
    }

    @Override
    public int hashCode() {
        return 31 * (hasPendingWrites ? 1 : 0) + (isFromCache ? 1 : 0);
    }

    @Override
    public String toString() {
        return "SnapshotMetadata{hasPendingWrites=" + hasPendingWrites
            + ", isFromCache=" + isFromCache + "}";
    }
}
