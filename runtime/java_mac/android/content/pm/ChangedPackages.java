package android.content.pm;

/** android.content.pm.ChangedPackages (API 26) -- the result of
 *  PackageManager.getChangedPackages(int): a sequence number plus the packages
 *  that changed since the caller's previous sequence number.
 *
 *  Mirrors frameworks/base/core/java/android/content/pm/ChangedPackages.java
 *  (android16-release). */
public final class ChangedPackages implements android.os.Parcelable {
    private final int mSequenceNumber;
    private final java.util.List<String> mPackageNames;

    public ChangedPackages(int sequenceNumber, java.util.List<String> packageNames) {
        mSequenceNumber = sequenceNumber;
        mPackageNames = packageNames;
    }

    /** AOSP: "the last known sequence number of changes". */
    public int getSequenceNumber() { return mSequenceNumber; }

    /** AOSP: "the names of the packages that have changed". */
    public java.util.List<String> getPackageNames() { return mPackageNames; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mSequenceNumber);
        dest.writeStringList(mPackageNames);
    }

    public static final android.os.Parcelable.Creator<ChangedPackages> CREATOR =
            new android.os.Parcelable.Creator<ChangedPackages>() {
        @Override public ChangedPackages createFromParcel(android.os.Parcel in) {
            int seq = in.readInt();
            java.util.List<String> names = new java.util.ArrayList<>();
            in.readStringList(names);
            return new ChangedPackages(seq, names);
        }
        @Override public ChangedPackages[] newArray(int size) {
            return new ChangedPackages[size];
        }
    };
}
