package android.media;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;

import java.util.Objects;

/**
 * Identifies a MediaSession2 (API 29). MediaSession2 itself was never completed
 * in the platform -- AOSP deprecated the surrounding MediaSessionManager APIs one
 * release later and pointed apps at androidx.media2/media3, which an app ships
 * inside its own APK -- so nothing in this tree ever creates a session of this
 * kind. The token type still exists and is still constructible by an app,
 * because the public constructor builds a TYPE_SESSION_SERVICE token out of a
 * ComponentName the app names itself; that path works here exactly as on
 * Android. MediaSessionManager.getSession2Tokens() correspondingly reports none.
 *
 * Source: frameworks/base media/java/android/media/Session2Token.java, and the
 * published API-36 reference for the two constant values (TYPE_SESSION = 0,
 * TYPE_SESSION_SERVICE = 1).
 */
public final class Session2Token implements Parcelable {

    /** Type for MediaSession2. */
    public static final int TYPE_SESSION = 0;
    /** Type for MediaSession2Service. */
    public static final int TYPE_SESSION_SERVICE = 1;

    private final int mUid;
    private final int mType;
    private final String mPackageName;
    private final String mServiceName;
    private final Bundle mExtras;

    /** AOSP throws NullPointerException on a null context or component, and
     *  IllegalArgumentException when the component does not resolve to a
     *  service. Resolution needs a PackageManager query that would fail here for
     *  a reason unrelated to the app's correctness, so only the null checks are
     *  enforced -- accepting a component AOSP might have rejected, never
     *  rejecting one it would have accepted. */
    public Session2Token(Context context, ComponentName serviceComponent) {
        Objects.requireNonNull(context, "context shouldn't be null");
        Objects.requireNonNull(serviceComponent, "serviceComponent shouldn't be null");
        mType = TYPE_SESSION_SERVICE;
        mPackageName = serviceComponent.getPackageName();
        mServiceName = serviceComponent.getClassName();
        mUid = Process.myUid();
        mExtras = null;
    }

    private Session2Token(int uid, int type, String packageName, String serviceName,
            Bundle extras) {
        mUid = uid;
        mType = type;
        mPackageName = packageName;
        mServiceName = serviceName;
        mExtras = extras;
    }

    public int getUid() { return mUid; }
    public int getType() { return mType; }
    public String getPackageName() { return mPackageName; }
    /** Null for a TYPE_SESSION token, which has no service behind it. */
    public String getServiceName() { return mServiceName; }
    public Bundle getExtras() { return mExtras; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mUid);
        dest.writeInt(mType);
        dest.writeString(mPackageName);
        dest.writeString(mServiceName);
        dest.writeBundle(mExtras);
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Session2Token)) return false;
        Session2Token o = (Session2Token) obj;
        return mUid == o.mUid && mType == o.mType
                && Objects.equals(mPackageName, o.mPackageName)
                && Objects.equals(mServiceName, o.mServiceName);
    }

    @Override public int hashCode() {
        return Objects.hash(mUid, mType, mPackageName, mServiceName);
    }

    @Override public String toString() {
        return "Session2Token{pkg=" + mPackageName + " type=" + mType
                + " service=" + mServiceName + " uid=" + mUid + "}";
    }

    public static final Parcelable.Creator<Session2Token> CREATOR =
            new Parcelable.Creator<Session2Token>() {
        @Override public Session2Token createFromParcel(Parcel in) {
            int uid = in.readInt();
            int type = in.readInt();
            String pkg = in.readString();
            String service = in.readString();
            return new Session2Token(uid, type, pkg, service, in.readBundle());
        }
        @Override public Session2Token[] newArray(int size) { return new Session2Token[size]; }
    };
}
