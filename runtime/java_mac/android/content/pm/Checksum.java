package android.content.pm;

/** android.content.pm.Checksum (API 31) -- one typed digest of an APK, as
 *  produced by PackageManager.requestChecksums and PackageInstaller.Session
 *  .setChecksums.
 *
 *  Mirrors frameworks/base/core/java/android/content/pm/Checksum.java
 *  (android16-release). The TYPE_* values are a bitmask an app ORs together to
 *  say which digests it wants, so the VALUES are the contract. */
public final class Checksum implements android.os.Parcelable {
    /** Merkle root of the whole file, SHA-256 over 4K blocks (fs-verity). */
    public static final int TYPE_WHOLE_MERKLE_ROOT_4K_SHA256 = 0x00000001;
    public static final int TYPE_WHOLE_MD5    = 0x00000002;
    public static final int TYPE_WHOLE_SHA1   = 0x00000004;
    public static final int TYPE_WHOLE_SHA256 = 0x00000008;
    public static final int TYPE_WHOLE_SHA512 = 0x00000010;
    /** Merkle root over the APK Signing Block's 1M chunks. */
    public static final int TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256 = 0x00000020;
    public static final int TYPE_PARTIAL_MERKLE_ROOT_1M_SHA512 = 0x00000040;

    private final int mType;
    private final byte[] mValue;

    public Checksum(int type, byte[] value) {
        mType = type;
        mValue = value;
    }

    /** AOSP: "the type of the checksum." */
    public int getType() { return mType; }

    /** AOSP: "the raw bytes of the checksum." */
    public byte[] getValue() { return mValue; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeByteArray(mValue);
    }

    public static final android.os.Parcelable.Creator<Checksum> CREATOR =
            new android.os.Parcelable.Creator<Checksum>() {
        @Override public Checksum createFromParcel(android.os.Parcel in) {
            return new Checksum(in.readInt(), in.createByteArray());
        }
        @Override public Checksum[] newArray(int size) { return new Checksum[size]; }
    };
}
