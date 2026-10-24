package android.content;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Who is responsible for a data access: the uid/package, an optional
 * attribution tag, and a chain of upstream callers for delegated access.
 *
 * On a device this is what AppOps records against. Mac.apk runs one app in one
 * process with no permission broker, so the source is always the app itself and
 * the attribution tag is null -- which is exactly what AOSP reports for a
 * Context that was never created with createAttributionContext().
 *
 * Source: frameworks/base/core/java/android/content/AttributionSource.java
 */
public final class AttributionSource implements Parcelable {

    private final int mUid;
    private final int mPid;
    private final int mDeviceId;
    private final String mPackageName;
    private final String mAttributionTag;
    private final AttributionSource mNext;

    /** AOSP Context.DEVICE_ID_DEFAULT -- the physical device, as opposed to a
     *  virtual (streamed) one. Mac.apk has no VirtualDeviceManager, so every
     *  source is on the default device. */
    private static final int DEVICE_ID_DEFAULT = 0;
    /** AOSP Process.INVALID_PID. */
    private static final int INVALID_PID = -1;

    public AttributionSource(int uid, String packageName, String attributionTag) {
        this(uid, packageName, attributionTag, null);
    }

    public AttributionSource(int uid, String packageName, String attributionTag,
                             AttributionSource next) {
        this(uid, INVALID_PID, DEVICE_ID_DEFAULT, packageName, attributionTag, next);
    }

    private AttributionSource(int uid, int pid, int deviceId, String packageName,
                              String attributionTag, AttributionSource next) {
        mUid = uid;
        mPid = pid;
        mDeviceId = deviceId;
        mPackageName = packageName;
        mAttributionTag = attributionTag;
        mNext = next;
    }

    public int getUid() { return mUid; }

    /** AOSP :530 -- the pid claimed for the accessing app. AOSP notes it "may
     *  be unavailable, for example if we're in a oneway Binder call", in which
     *  case it is Process.INVALID_PID -- which is what a source built through
     *  the three/four-arg constructors carries here. */
    public int getPid() { return mPid; }

    /** AOSP :556 -- which (physical or virtual) device the access happens on.
     *  Always the default device here; there is no VirtualDeviceManager. */
    public int getDeviceId() { return mDeviceId; }

    /** AOSP :356 -- "if the attribution source cannot be trusted to be from
     *  the caller": true when the calling uid is root, the system, or the uid
     *  this source claims. Ported verbatim. In this runtime every caller is
     *  the app itself, so the check passes for the app's own source and
     *  correctly FAILS for a source claiming a different uid -- it is a real
     *  comparison, not a constant true. */
    public boolean checkCallingUid() {
        final int callingUid = android.os.Binder.getCallingUid();
        if (callingUid != android.os.Process.ROOT_UID
                && android.os.UserHandle.getAppId(callingUid) != android.os.Process.SYSTEM_UID
                && callingUid != mUid) {
            return false;
        }
        return true;
    }

    /** AOSP :340 -- the throwing form, message verbatim. */
    public void enforceCallingUid() {
        if (!checkCallingUid()) {
            throw new SecurityException("Calling uid: " + android.os.Binder.getCallingUid()
                    + " doesn't match source uid: " + mUid);
        }
    }

    /** AOSP :494 --
     *      return mAttributionSourceState.token != null
     *          && context.getSystemService(PermissionManager.class)
     *                    .isRegisteredAttributionSource(this);
     *  i.e. "has the system's permission broker REGISTERED this source". The
     *  token is minted by that broker. Mac.apk has no permission broker and
     *  mints no tokens, so no source is registered and the honest answer is
     *  false -- the same answer a device gives for a source an app fabricated
     *  itself rather than obtaining from the system. Answering true would
     *  assert a provenance check we never ran, which is precisely the check
     *  this method exists to perform. */
    public boolean isTrusted(Context context) { return false; }

    /** AOSP :274 -- the source for the CURRENT app: its own uid, pid, default
     *  device and package name. AOSP consults ActivityThread's cached global
     *  source first and otherwise builds one exactly as below. @NonNull;
     *  AOSP's fallback is `throw new IllegalStateException("Failed to resolve
     *  AttributionSource")`, which we cannot reach because the package name
     *  comes from our own Application rather than a PackageManager query that
     *  could fail. */
    public static AttributionSource myAttributionSource() {
        int uid = android.os.Process.myUid();
        if (uid == android.os.Process.ROOT_UID) {
            uid = android.os.Process.SYSTEM_UID;
        }
        String pkg = null;
        android.app.Application app = android.app.Application.macapk_get();
        if (app != null) pkg = app.getPackageName();
        return new AttributionSource(uid, android.os.Process.myPid(), DEVICE_ID_DEFAULT,
                                     pkg, null, null);
    }

    public String getPackageName() { return mPackageName; }

    public String getAttributionTag() { return mAttributionTag; }

    public AttributionSource getNext() { return mNext; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttributionSource)) return false;
        AttributionSource a = (AttributionSource) o;
        return mUid == a.mUid
                && java.util.Objects.equals(mPackageName, a.mPackageName)
                && java.util.Objects.equals(mAttributionTag, a.mAttributionTag);
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(mUid, mPackageName, mAttributionTag);
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mUid);
        dest.writeInt(mPid);
        dest.writeInt(mDeviceId);
        dest.writeString(mPackageName);
        dest.writeString(mAttributionTag);
        // The delegation CHAIN is the point of this class -- an app builds
        // A -> B to say "B is accessing on A's behalf". Dropping mNext (which
        // the previous writeToParcel did) flattened every chain to its head on
        // any round trip, so getNext() came back null. AOSP carries the next
        // link too (as a one-element AttributionSourceState[]).
        dest.writeTypedObject(mNext, flags);
    }

    public static final Creator<AttributionSource> CREATOR = new Creator<AttributionSource>() {
        @Override public AttributionSource createFromParcel(Parcel in) {
            int uid = in.readInt();
            int pid = in.readInt();
            int deviceId = in.readInt();
            String pkg = in.readString();
            String tag = in.readString();
            AttributionSource next = in.readTypedObject(AttributionSource.CREATOR);
            return new AttributionSource(uid, pid, deviceId, pkg, tag, next);
        }
        @Override public AttributionSource[] newArray(int size) {
            return new AttributionSource[size];
        }
    };

    public static final class Builder {
        private final int mUid;
        private int mPid = INVALID_PID;
        private int mDeviceId = DEVICE_ID_DEFAULT;
        private String mPackageName;
        private String mAttributionTag;
        private AttributionSource mNext;

        public Builder(int uid) { mUid = uid; }

        /** AOSP's copy constructor: seed every field from an existing source. */
        public Builder(AttributionSource current) {
            if (current == null) {
                throw new IllegalArgumentException("current AttributionSource can not be null");
            }
            mUid = current.mUid;
            mPid = current.mPid;
            mDeviceId = current.mDeviceId;
            mPackageName = current.mPackageName;
            mAttributionTag = current.mAttributionTag;
            mNext = current.mNext;
        }

        public Builder setPackageName(String v) { mPackageName = v; return this; }
        public Builder setAttributionTag(String v) { mAttributionTag = v; return this; }
        public Builder setNext(AttributionSource v) { mNext = v; return this; }
        public Builder setPid(int v) { mPid = v; return this; }
        public Builder setDeviceId(int v) { mDeviceId = v; return this; }

        /** AOSP :771 -- the @NonNull variant of setNext(), which rejects null
         *  instead of clearing the chain. The exception message is AOSP's. */
        public Builder setNextAttributionSource(AttributionSource value) {
            if (value == null) {
                throw new IllegalArgumentException("Null AttributionSource not permitted.");
            }
            mNext = value;
            return this;
        }

        public AttributionSource build() {
            return new AttributionSource(mUid, mPid, mDeviceId, mPackageName, mAttributionTag,
                                         mNext);
        }
    }
}
