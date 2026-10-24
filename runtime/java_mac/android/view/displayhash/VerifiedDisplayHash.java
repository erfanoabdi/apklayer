// android.view.displayhash.VerifiedDisplayHash -- the result of
// DisplayHashManager.verifyDisplayHash, i.e. a DisplayHash whose HMAC the
// system has checked. Source: frameworks/base
// core/java/android/view/displayhash/VerifiedDisplayHash.java
package android.view.displayhash;

public final class VerifiedDisplayHash implements android.os.Parcelable {
    private final long mTimeMillis;
    private final android.graphics.Rect mBoundsInWindow;
    private final String mHashAlgorithm;
    private final byte[] mImageHash;

    public VerifiedDisplayHash(long timeMillis, android.graphics.Rect boundsInWindow,
            String hashAlgorithm, byte[] imageHash) {
        mTimeMillis = timeMillis;
        mBoundsInWindow = boundsInWindow;
        mHashAlgorithm = hashAlgorithm;
        mImageHash = imageHash;
    }

    public long getTimeMillis() { return mTimeMillis; }
    public android.graphics.Rect getBoundsInWindow() { return mBoundsInWindow; }
    public String getHashAlgorithm() { return mHashAlgorithm; }
    public byte[] getImageHash() { return mImageHash; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeLong(mTimeMillis);
        dest.writeParcelable(mBoundsInWindow, flags);
        dest.writeString(mHashAlgorithm);
        dest.writeByteArray(mImageHash);
    }

    public static final android.os.Parcelable.Creator<VerifiedDisplayHash> CREATOR =
            new android.os.Parcelable.Creator<VerifiedDisplayHash>() {
        @Override public VerifiedDisplayHash createFromParcel(android.os.Parcel source) {
            return new VerifiedDisplayHash(source.readLong(),
                    source.readParcelable(android.graphics.Rect.class.getClassLoader()),
                    source.readString(), source.createByteArray());
        }
        @Override public VerifiedDisplayHash[] newArray(int size) {
            return new VerifiedDisplayHash[size];
        }
    };
}
