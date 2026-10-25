// android.content.pm.PermissionGroupInfo -- metadata for a permission
// group (<permission-group> in the manifest). Apps and the system query
// it to present grouped permission UI.
//
// AOSP ref:
//   frameworks/base/core/java/android/content/pm/PermissionGroupInfo.java
// AOSP: PermissionGroupInfo extends PackageItemInfo (inheriting name /
// metaData / loadLabel). Previously a bare stub.
package android.content.pm;

public class PermissionGroupInfo extends PackageItemInfo implements android.os.Parcelable {
    public int descriptionRes;
    public CharSequence nonLocalizedDescription;
    public int flags;
    public int priority;
    public int requestRes;
    public int requestDetailResourceId;
    public int backgroundRequestResourceId;
    public int backgroundRequestDetailResourceId;

    /** Set in flags when the permission group is personal-and-sensitive. */
    public static final int FLAG_PERSONAL_INFO = 1 << 0;

    public PermissionGroupInfo() {}

    public PermissionGroupInfo(PermissionGroupInfo orig) {
        name = orig.name;
        packageName = orig.packageName;
        labelRes = orig.labelRes;
        nonLocalizedLabel = orig.nonLocalizedLabel;
        icon = orig.icon;
        descriptionRes = orig.descriptionRes;
        nonLocalizedDescription = orig.nonLocalizedDescription;
        flags = orig.flags;
        priority = orig.priority;
    }

    public CharSequence loadDescription(PackageManager pm) {
        return nonLocalizedDescription;
    }

    @Override public String toString() {
        return "PermissionGroupInfo{" + name + "}";
    }

    // ---- Parcelable --------------------------------------------------------
    // AOSP: `PermissionGroupInfo extends PackageItemInfo implements Parcelable`
    // (android11-release PermissionGroupInfo.java:32). Order is AOSP
    // PermissionGroupInfo.java:192-202; this tree declares every field it
    // writes. Note nonLocalizedDescription sits BETWEEN the resource ids and
    // flags/priority, not at the end.
    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(android.os.Parcel dest, int parcelableFlags) {
        super.writeToParcel(dest, parcelableFlags);
        dest.writeInt(descriptionRes);
        dest.writeInt(requestRes);
        dest.writeInt(requestDetailResourceId);
        dest.writeInt(backgroundRequestResourceId);
        dest.writeInt(backgroundRequestDetailResourceId);
        android.text.TextUtils.writeToParcel(nonLocalizedDescription, dest, parcelableFlags);
        dest.writeInt(flags);
        dest.writeInt(priority);
    }

    /** AOSP PermissionGroupInfo.java:214-224. */
    private PermissionGroupInfo(android.os.Parcel source) {
        super(source);
        descriptionRes = source.readInt();
        requestRes = source.readInt();
        requestDetailResourceId = source.readInt();
        backgroundRequestResourceId = source.readInt();
        backgroundRequestDetailResourceId = source.readInt();
        nonLocalizedDescription =
            android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
        flags = source.readInt();
        priority = source.readInt();
    }

    public static final android.os.Parcelable.Creator<PermissionGroupInfo> CREATOR =
        new android.os.Parcelable.Creator<PermissionGroupInfo>() {
            @Override public PermissionGroupInfo createFromParcel(android.os.Parcel source) {
                return new PermissionGroupInfo(source);
            }
            @Override public PermissionGroupInfo[] newArray(int size) {
                return new PermissionGroupInfo[size];
            }
        };
}
