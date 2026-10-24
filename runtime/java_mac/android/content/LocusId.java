package android.content;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * An opaque identifier that ties a shortcut, notification, or content-capture
 * event back to the same logical "place" in the app.
 *
 * AOSP treats the id as an opaque string it never interprets; the only
 * behavior that matters to an app is that the value round-trips and that
 * equals/hashCode compare by it (apps key maps on LocusId).
 *
 * Source: frameworks/base/core/java/android/content/LocusId.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/content/LocusId.java
 */
public final class LocusId implements Parcelable {

    private final String mId;

    public LocusId(String id) {
        if (id == null) {
            // AOSP: Preconditions.checkStringNotEmpty(id, "id cannot be empty")
            throw new IllegalArgumentException("id cannot be empty");
        }
        mId = id;
    }

    public String getId() { return mId; }

    @Override public int hashCode() {
        return 31 + (mId == null ? 0 : mId.hashCode());
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LocusId)) return false;
        LocusId other = (LocusId) obj;
        return mId == null ? other.mId == null : mId.equals(other.mId);
    }

    /** AOSP sanitizes the id in toString so it cannot leak PII into logs. */
    @Override public String toString() {
        return "LocusId[" + (mId == null ? "" : mId.length() + "_chars") + "]";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mId);
    }

    public static final Creator<LocusId> CREATOR = new Creator<LocusId>() {
        @Override public LocusId createFromParcel(Parcel parcel) {
            return new LocusId(parcel.readString());
        }
        @Override public LocusId[] newArray(int size) {
            return new LocusId[size];
        }
    };
}
