package android.telecom;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;

/**
 * Identifies a calling account (a SIM, or a VoIP app's account).
 *
 * A REAL value object: it is an identifier triple with no device dependency,
 * and an app builds one before asking whether the device supports Telecom at
 * all. Nothing about holding a handle implies an account was registered --
 * TelecomManager.registerPhoneAccount is where that is refused, loudly.
 *
 * Source: frameworks/base/telecomm/java/android/telecom/PhoneAccountHandle.java
 */
public final class PhoneAccountHandle implements Parcelable {

    private final ComponentName mComponentName;
    private final String mId;
    private final UserHandle mUserHandle;

    public PhoneAccountHandle(ComponentName componentName, String id) {
        this(componentName, id, android.os.Process.myUserHandle());
    }

    /**
     * AOSP's 3-arg form binds the account to a specific user. Mac.apk is
     * single-user, so callers pass (and read back) the current user; the field
     * is stored rather than dropped because equals()/hashCode() include it and
     * an app that round-trips a handle must get an equal object back.
     */
    public PhoneAccountHandle(ComponentName componentName, String id, UserHandle userHandle) {
        mComponentName = componentName;
        mId = id;
        mUserHandle = userHandle;
    }

    public ComponentName getComponentName() { return mComponentName; }

    public String getId() { return mId; }

    public UserHandle getUserHandle() { return mUserHandle; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PhoneAccountHandle)) return false;
        PhoneAccountHandle o = (PhoneAccountHandle) other;
        return java.util.Objects.equals(o.mComponentName, mComponentName)
                && java.util.Objects.equals(o.mId, mId)
                && java.util.Objects.equals(o.mUserHandle, mUserHandle);
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(mComponentName, mId, mUserHandle);
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(mComponentName != null ? mComponentName.flattenToString() : null);
        out.writeString(mId);
    }

    public static final Creator<PhoneAccountHandle> CREATOR = new Creator<PhoneAccountHandle>() {
        @Override public PhoneAccountHandle createFromParcel(Parcel in) {
            String cn = in.readString();
            return new PhoneAccountHandle(
                    cn != null ? ComponentName.unflattenFromString(cn) : null, in.readString());
        }
        @Override public PhoneAccountHandle[] newArray(int size) {
            return new PhoneAccountHandle[size];
        }
    };
}
