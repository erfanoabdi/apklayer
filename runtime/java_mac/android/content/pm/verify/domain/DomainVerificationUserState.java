package android.content.pm.verify.domain;

/** android.content.pm.verify.domain.DomainVerificationUserState (API 31) --
 *  per-user state of an app's APP LINKS: for each web host the app declares in
 *  an `<intent-filter android:autoVerify="true">`, whether the platform
 *  VERIFIED it (via the /.well-known/assetlinks.json check), whether the USER
 *  SELECTED it manually, or neither; plus whether the app is allowed to handle
 *  its links at all.
 *
 *  Mirrors frameworks/base/core/java/android/content/pm/verify/domain/
 *  DomainVerificationUserState.java (android16-release).
 *
 *  The constructor is @hide upstream (only the system builds one); it is public
 *  here because nothing else in this tree could. */
public final class DomainVerificationUserState implements android.os.Parcelable {
    /** AOSP: "The domain is unverified and unselected." */
    public static final int DOMAIN_STATE_NONE = 0;
    /** AOSP: "The user has explicitly chosen to allow this app to open this
     *  domain." */
    public static final int DOMAIN_STATE_SELECTED = 1;
    /** AOSP: "The domain has been verified by the system." */
    public static final int DOMAIN_STATE_VERIFIED = 2;

    private final String mPackageName;
    private final android.os.UserHandle mUser;
    private final boolean mLinkHandlingAllowed;
    private final java.util.Map<String, Integer> mHostToStateMap;

    public DomainVerificationUserState(String packageName, android.os.UserHandle user,
            java.util.Map<String, Integer> hostToStateMap, boolean linkHandlingAllowed) {
        mPackageName = packageName;
        mUser = user;
        mLinkHandlingAllowed = linkHandlingAllowed;
        mHostToStateMap = (hostToStateMap != null)
                ? hostToStateMap : java.util.Collections.<String, Integer>emptyMap();
    }

    public String getPackageName() { return mPackageName; }

    public android.os.UserHandle getUser() { return mUser; }

    /** AOSP: "whether the package is allowed to open its declared domains."
     *  Note upstream annotates this @NonNull on a primitive, which is a
     *  harmless upstream quirk; the value is a plain boolean. */
    public boolean isLinkHandlingAllowed() { return mLinkHandlingAllowed; }

    /** AOSP: "a map of hosts to their DOMAIN_STATE_*." @NonNull. */
    public java.util.Map<String, Integer> getHostToStateMap() { return mHostToStateMap; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeTypedObject(mUser, flags);
        dest.writeBoolean(mLinkHandlingAllowed);
        dest.writeInt(mHostToStateMap.size());
        for (java.util.Map.Entry<String, Integer> e : mHostToStateMap.entrySet()) {
            dest.writeString(e.getKey());
            dest.writeInt(e.getValue() != null ? e.getValue() : DOMAIN_STATE_NONE);
        }
    }

    public static final android.os.Parcelable.Creator<DomainVerificationUserState> CREATOR =
            new android.os.Parcelable.Creator<DomainVerificationUserState>() {
        @Override public DomainVerificationUserState createFromParcel(android.os.Parcel in) {
            String pkg = in.readString();
            android.os.UserHandle user = in.readTypedObject(android.os.UserHandle.CREATOR);
            boolean allowed = in.readBoolean();
            int n = in.readInt();
            java.util.LinkedHashMap<String, Integer> map = new java.util.LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                String host = in.readString();
                map.put(host, in.readInt());
            }
            return new DomainVerificationUserState(pkg, user, map, allowed);
        }
        @Override public DomainVerificationUserState[] newArray(int size) {
            return new DomainVerificationUserState[size];
        }
    };
}
