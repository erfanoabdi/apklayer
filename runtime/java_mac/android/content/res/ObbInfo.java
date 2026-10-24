// android.content.res.ObbInfo -- the metadata in an OBB (Opaque Binary Blob)
// expansion file's footer: which package it belongs to, its version, and its
// flags.
//
// AOSP ref: frameworks/base/core/java/android/content/res/ObbInfo.java
// (android16-release). Ported verbatim, including the @hide `salt` field
// (public in bytecode) and the parcel field order.
package android.content.res;

import android.os.Parcel;
import android.os.Parcelable;

public class ObbInfo implements Parcelable {
    /** The OBB is an overlay patch on top of a base OBB. */
    public static final int OBB_OVERLAY = 1 << 0;

    /** The canonical path of the file this info was read from. */
    public String filename;

    /** The package the OBB belongs to. */
    public String packageName;

    /** The version of the OBB, as declared in its footer. */
    public int version;

    /** OBB_* flag bits. */
    public int flags;

    /** @hide upstream, public in bytecode -- the 8-byte encryption salt. */
    public byte[] salt;

    ObbInfo() {
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ObbInfo{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        sb.append(" packageName=");
        sb.append(packageName);
        sb.append(",version=");
        sb.append(version);
        sb.append(",flags=");
        sb.append(flags);
        sb.append('}');
        return sb.toString();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int parcelableFlags) {
        dest.writeString(filename);
        dest.writeString(packageName);
        dest.writeInt(version);
        dest.writeInt(flags);
        dest.writeByteArray(salt);
    }

    public static final Parcelable.Creator<ObbInfo> CREATOR = new Parcelable.Creator<ObbInfo>() {
        public ObbInfo createFromParcel(Parcel source) { return new ObbInfo(source); }
        public ObbInfo[] newArray(int size) { return new ObbInfo[size]; }
    };

    private ObbInfo(Parcel source) {
        filename = source.readString();
        packageName = source.readString();
        version = source.readInt();
        flags = source.readInt();
        salt = source.createByteArray();
    }
}
