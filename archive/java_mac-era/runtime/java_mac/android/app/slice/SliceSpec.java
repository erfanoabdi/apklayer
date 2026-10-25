package android.app.slice;
/** The versioned format of a Slice.
 *
 *  AOSP ref: frameworks/base/core/java/android/app/slice/SliceSpec.java
 *  (android16-release). The whole android.app.slice package is @Deprecated
 *  there (slices were withdrawn from the platform surface), which is mirrored
 *  on every member below -- an app compiling against it should get the same
 *  warning it gets on a device. */
@Deprecated
public final class SliceSpec implements android.os.Parcelable {
    private final String mType; private final int mRevision;
    public SliceSpec(String type, int revision) { mType = type; mRevision = revision; }
    private SliceSpec(android.os.Parcel in) { mType = in.readString(); mRevision = in.readInt(); }
    public String getType() { return mType; }
    public int getRevision() { return mRevision; }
    public boolean canRender(SliceSpec candidate) {
        return candidate != null && mType != null && mType.equals(candidate.mType);
    }

    /** AOSP SliceSpec is Parcelable: a spec crosses the Binder from the app to
     *  the slice host in bindSlice/pinSlice. Type + revision is the whole
     *  value, so the round trip is exact. */
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mType);
        dest.writeInt(mRevision);
    }
    @Override public boolean equals(Object o) {
        if (!(o instanceof SliceSpec)) return false;
        SliceSpec other = (SliceSpec) o;
        return mRevision == other.mRevision && java.util.Objects.equals(mType, other.mType);
    }
    @Override public int hashCode() { return java.util.Objects.hash(mType, mRevision); }
    @Override public String toString() { return "SliceSpec{" + mType + "," + mRevision + "}"; }

    public static final android.os.Parcelable.Creator<SliceSpec> CREATOR =
            new android.os.Parcelable.Creator<SliceSpec>() {
        @Override public SliceSpec createFromParcel(android.os.Parcel in) {
            return new SliceSpec(in);
        }
        @Override public SliceSpec[] newArray(int size) { return new SliceSpec[size]; }
    };
}
