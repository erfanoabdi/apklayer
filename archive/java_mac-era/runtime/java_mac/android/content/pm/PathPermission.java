// android.content.pm.PathPermission -- "Description of permissions needed to
// access a particular path in a ProviderInfo" (AOSP's own javadoc).
//
// AOSP ref: frameworks/base/core/java/android/content/pm/PathPermission.java
// (aosp-mirror/platform_frameworks_base, android14-release), mirrored field for
// field: it extends android.os.PatternMatcher and adds exactly two immutable
// Strings plus their getters.
//
// Populated from a `<path-permission>` child of a `<provider>` in the manifest
// (see Manifest.java's provider walk, which mirrors PackageParser.parseProvider
// lines 5292-5360). A provider that declares no <path-permission> gets a NULL
// array here, which is AOSP's default -- ProviderInfo.pathPermissions is
// declared `= null` and is only filled in when the element exists.
package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.PatternMatcher;

public class PathPermission extends PatternMatcher {
    private final String mReadPermission;
    private final String mWritePermission;

    public PathPermission(String pattern, int type, String readPermission,
            String writePermission) {
        super(pattern, type);
        mReadPermission = readPermission;
        mWritePermission = writePermission;
    }

    public String getReadPermission() { return mReadPermission; }

    public String getWritePermission() { return mWritePermission; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mReadPermission);
        dest.writeString(mWritePermission);
    }

    public PathPermission(Parcel src) {
        super(src);
        mReadPermission = src.readString();
        mWritePermission = src.readString();
    }

    public static final Parcelable.Creator<PathPermission> CREATOR =
        new Parcelable.Creator<PathPermission>() {
            @Override public PathPermission createFromParcel(Parcel source) {
                return new PathPermission(source);
            }
            @Override public PathPermission[] newArray(int size) {
                return new PathPermission[size];
            }
        };
}
