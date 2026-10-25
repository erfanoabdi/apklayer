package android.content.pm;

/**
 * A checksum of an installed APK's split, from PackageManager.requestChecksums.
 * Mac.apk can compute the real digest of the split file on disk, so getType /
 * getValue return actual data rather than a stub.
 *
 * Source: frameworks/base/core/java/android/content/pm/ApkChecksum.java
 *
 * 2026-08-23 conformance sweep: AOSP wraps type+value in a nested Checksum
 * Parcelable and writes a presence-bitmask before the nullable fields
 * (ApkChecksum.java L195-207); we do not carry that nested class, so this
 * writes our own flat (splitName, type, value, installerPackageName) fields
 * in a self-consistent order instead of AOSP's exact wire shape. Parcel's
 * writeString/writeByteArray already encode null cleanly, so no bitmask is
 * needed. getInstallerCertificate() is always null because the field was
 * never stored by the constructor (see below) -- nothing to parcel there.
 */
public final class ApkChecksum implements android.os.Parcelable {

    private final String mSplitName;
    private final int mType;
    private final byte[] mValue;
    private final String mInstallerPackageName;

    public ApkChecksum(String splitName, int type, byte[] value) {
        this(splitName, type, value, null, null);
    }
    public ApkChecksum(String splitName, int type, byte[] value,
            String installerPackageName, java.security.cert.Certificate installerCertificate) {
        mSplitName = splitName;
        mType = type;
        mValue = value;
        mInstallerPackageName = installerPackageName;
    }

    public String getSplitName() { return mSplitName; }
    public int getType() { return mType; }
    public byte[] getValue() { return mValue; }
    public String getInstallerPackageName() { return mInstallerPackageName; }
    public java.security.cert.Certificate getInstallerCertificate() { return null; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mSplitName);
        dest.writeInt(mType);
        dest.writeByteArray(mValue);
        dest.writeString(mInstallerPackageName);
    }

    public static final android.os.Parcelable.Creator<ApkChecksum> CREATOR =
            new android.os.Parcelable.Creator<ApkChecksum>() {
        @Override public ApkChecksum createFromParcel(android.os.Parcel in) {
            String splitName = in.readString();
            int type = in.readInt();
            byte[] value = in.createByteArray();
            String installerPackageName = in.readString();
            return new ApkChecksum(splitName, type, value, installerPackageName, null);
        }
        @Override public ApkChecksum[] newArray(int size) { return new ApkChecksum[size]; }
    };
}
