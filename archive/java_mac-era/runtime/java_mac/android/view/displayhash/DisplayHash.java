// android.view.displayhash.DisplayHash -- an opaque, system-signed hash of a
// region of the screen, produced by View.generateDisplayHash. Mac.apk has no
// system service that can attest a frame, so no DisplayHash is ever produced
// (generateDisplayHash reports DISPLAY_HASH_ERROR_UNKNOWN, exactly as a device
// whose window manager declines the request does) -- but the type must exist
// because the callback signature names it.
// Source: frameworks/base core/java/android/view/displayhash/DisplayHash.java
package android.view.displayhash;

public final class DisplayHash implements android.os.Parcelable {
    private final long mTimeMillis;
    private final android.graphics.Rect mBoundsInWindow;
    private final String mHashAlgorithm;
    private final byte[] mImageHash;
    private final byte[] mHmac;

    /** @hide -- AOSP's constructor is @hide too; only the system server builds one. */
    DisplayHash(long timeMillis, android.graphics.Rect boundsInWindow, String hashAlgorithm,
            byte[] imageHash, byte[] hmac) {
        mTimeMillis = timeMillis;
        mBoundsInWindow = boundsInWindow;
        mHashAlgorithm = hashAlgorithm;
        mImageHash = imageHash;
        mHmac = hmac;
    }

    long macapkTimeMillis() { return mTimeMillis; }
    android.graphics.Rect macapkBoundsInWindow() { return mBoundsInWindow; }
    String macapkHashAlgorithm() { return mHashAlgorithm; }
    byte[] macapkImageHash() { return mImageHash; }
    byte[] macapkHmac() { return mHmac; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeLong(mTimeMillis);
        dest.writeParcelable(mBoundsInWindow, flags);
        dest.writeString(mHashAlgorithm);
        dest.writeByteArray(mImageHash);
        dest.writeByteArray(mHmac);
    }

    public static final android.os.Parcelable.Creator<DisplayHash> CREATOR =
            new android.os.Parcelable.Creator<DisplayHash>() {
        @Override public DisplayHash createFromParcel(android.os.Parcel source) {
            long t = source.readLong();
            android.graphics.Rect r = source.readParcelable(
                    android.graphics.Rect.class.getClassLoader());
            String alg = source.readString();
            byte[] hash = source.createByteArray();
            byte[] hmac = source.createByteArray();
            return new DisplayHash(t, r, alg, hash, hmac);
        }
        @Override public DisplayHash[] newArray(int size) { return new DisplayHash[size]; }
    };
}
