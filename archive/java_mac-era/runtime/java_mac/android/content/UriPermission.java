// android.content.UriPermission -- describes a single Uri permission
// grant, created via Intent FLAG_GRANT_READ/WRITE_URI_PERMISSION or
// explicitly through Context.grantUriPermission(). Returned by
// ContentResolver.getPersistedUriPermissions() and
// getOutgoingPersistedUriPermissions(). Mirrors
// frameworks/base/core/java/android/content/UriPermission.java (fields,
// accessors, Parcelable shape, and INVALID_TIME all verified against
// AOSP source).
//
// Mac.apk note: since v1.0.1654 (MACAPK_PERSISTED_URI_GRANTS)
// ContentResolver.getPersistedUriPermissions() returns real instances, built
// by MacApkUriGrants.persisted() exactly as UriGrantsManagerService's
// UriPermission.buildPersistedPublicApiObject does:
// new UriPermission(uri, persistedModeFlags, persistedCreateTime). Context's
// grantUriPermission family are still no-ops, and
// getOutgoingPersistedUriPermissions() is always empty (one app, so no
// other app can hold a grant from this one).
package android.content;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

public final class UriPermission implements Parcelable {
    private final Uri mUri;
    private final int mModeFlags;
    private final long mPersistedTime;

    /** Value returned by getPersistedTime() when a permission has not been persisted. */
    public static final long INVALID_TIME = Long.MIN_VALUE;

    // Intent.FLAG_GRANT_READ_URI_PERMISSION / FLAG_GRANT_WRITE_URI_PERMISSION,
    // inlined locally because this tree's Intent.java does not declare them
    // as named constants yet (see the parseUri() comment in Intent.java,
    // which notes the same gap). Values confirmed against
    // frameworks/base/core/java/android/content/Intent.java: 0x00000001 /
    // 0x00000002, stable since API 1.
    private static final int FLAG_GRANT_READ_URI_PERMISSION = 0x1;
    private static final int FLAG_GRANT_WRITE_URI_PERMISSION = 0x2;

    /** {@hide} AOSP ctor used by whoever constructs a grant record. */
    public UriPermission(Uri uri, int modeFlags, long persistedTime) {
        mUri = uri;
        mModeFlags = modeFlags;
        mPersistedTime = persistedTime;
    }

    /** {@hide} AOSP ctor used by CREATOR. */
    public UriPermission(Parcel in) {
        mUri = in.readParcelable(null, Uri.class);
        mModeFlags = in.readInt();
        mPersistedTime = in.readLong();
    }

    /** Return the Uri this permission pertains to. */
    public Uri getUri() {
        return mUri;
    }

    /** Returns if this permission offers read access. */
    public boolean isReadPermission() {
        return (mModeFlags & FLAG_GRANT_READ_URI_PERMISSION) != 0;
    }

    /** Returns if this permission offers write access. */
    public boolean isWritePermission() {
        return (mModeFlags & FLAG_GRANT_WRITE_URI_PERMISSION) != 0;
    }

    /**
     * Return the time when this permission was first persisted, in
     * milliseconds since January 1, 1970 00:00:00.0 UTC. Returns
     * INVALID_TIME if not persisted.
     */
    public long getPersistedTime() {
        return mPersistedTime;
    }

    @Override
    public String toString() {
        return "UriPermission {uri=" + mUri + ", modeFlags=" + mModeFlags + ", persistedTime="
                + mPersistedTime + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mUri, flags);
        dest.writeInt(mModeFlags);
        dest.writeLong(mPersistedTime);
    }

    public static final Creator<UriPermission> CREATOR = new Creator<UriPermission>() {
        @Override
        public UriPermission createFromParcel(Parcel source) {
            return new UriPermission(source);
        }

        @Override
        public UriPermission[] newArray(int size) {
            return new UriPermission[size];
        }
    };
}
