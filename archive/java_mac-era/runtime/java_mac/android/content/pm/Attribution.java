package android.content.pm;

/** android.content.pm.Attribution (API 31) -- one `<attribution>` element of a
 *  manifest: a tag naming a distinct part of the app plus the string resource
 *  that labels it for the user. The privacy/AppOps plumbing charges an
 *  operation to an attribution so a permission-usage screen can say WHICH part
 *  of the app used the microphone rather than just naming the app.
 *
 *  Mirrors frameworks/base/core/java/android/content/pm/Attribution.java
 *  (android16-release) exactly: two final fields, two getters, Parcelable.
 *
 *  Mac.apk's manifest parser does not read `<attribution>` yet, so
 *  PackageInfo.attributions stays null -- the same value AOSP reports for a
 *  package that declares none, which is nearly all of them. The CLASS has to
 *  exist regardless: we present API 36, so a checkcast or an array store of
 *  this type must resolve, and a missing class is a NoClassDefFoundError rather
 *  than the null a caller is prepared for. */
public final class Attribution implements android.os.Parcelable {
    private final String mTag;
    private final int mLabel;

    /** AOSP's constructor is @hide (the platform parser is the only producer);
     *  public here for the same reason our other *Info constructors are -- the
     *  loader lane has to be able to build one once the parser learns the
     *  element, and nothing in this tree can construct it otherwise. */
    public Attribution(String tag, int label) {
        mTag = tag;
        mLabel = label;
    }

    /** AOSP Attribution.getTag(): "the tag of this attribution." */
    public String getTag() { return mTag; }

    /** AOSP Attribution.getLabel(): the string RESOURCE ID of the user-visible
     *  label, not the text. */
    public int getLabel() { return mLabel; }

    @Override public int describeContents() { return 0; }

    /** AOSP field order: tag then label. */
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mTag);
        dest.writeInt(mLabel);
    }

    public static final android.os.Parcelable.Creator<Attribution> CREATOR =
            new android.os.Parcelable.Creator<Attribution>() {
        @Override public Attribution createFromParcel(android.os.Parcel in) {
            return new Attribution(in.readString(), in.readInt());
        }
        @Override public Attribution[] newArray(int size) {
            return new Attribution[size];
        }
    };
}
