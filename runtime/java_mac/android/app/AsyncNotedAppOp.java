// android.app.AsyncNotedAppOp -- "When an app-op is noted asynchronously (i.e.
// the app is not currently in a call that would note the op) this class
// describes the noted op." Delivered to
// AppOpsManager.OnOpNotedCallback.onAsyncNoted. API 30.
//
// AOSP ref: frameworks/base/core/java/android/app/AsyncNotedAppOp.java
// (android16-release). It is a pure value carrier: op name, noting uid,
// attribution tag, developer message and the time the note happened. AOSP has no
// public constructor -- an instance only ever arrives from the system's app-ops
// service -- so the only public surface is the accessors, the Parcelable pair,
// and the CREATOR, all of which are reproduced here.
//
// Mac.apk runs no app-ops service, so nothing in this process ever produces one;
// the type exists because AppOpsManager.OnOpNotedCallback.onAsyncNoted names it
// in its signature, and without the type that callback cannot be declared at all.
package android.app;

import android.os.Parcel;
import android.os.Parcelable;

public final class AsyncNotedAppOp implements Parcelable {

    private final int mOpCode;
    private final int mNotingUid;
    private final String mAttributionTag;
    private final String mMessage;
    private final long mTime;

    /** @hide -- AOSP's constructor is @hide too; the system service is the only
     *  legitimate producer. */
    AsyncNotedAppOp(int op, int notingUid, String attributionTag, String message, long time) {
        mOpCode = op;
        mNotingUid = notingUid;
        mAttributionTag = attributionTag;
        mMessage = message;
        mTime = time;
    }

    private AsyncNotedAppOp(Parcel in) {
        mOpCode = in.readInt();
        mNotingUid = in.readInt();
        mAttributionTag = in.readString();
        mMessage = in.readString();
        mTime = in.readLong();
    }

    /** AOSP getOp(): "the op that was noted", as its public name. Resolved
     *  through the same op-code table SyncNotedAppOp uses (AOSP resolves both
     *  through AppOpsManager.opToPublicName). */
    public String getOp() { return new SyncNotedAppOp(mOpCode, mAttributionTag).getOp(); }

    /** AOSP getAttributionTag(): the attribution tag, or null for the default. */
    public String getAttributionTag() { return mAttributionTag; }

    /** AOSP getMessage(): "the message attached to the noteOp", never null. */
    public String getMessage() { return mMessage != null ? mMessage : ""; }

    /** AOSP getNotingUid(): "the uid that noted the op", non-negative. */
    public int getNotingUid() { return mNotingUid; }

    /** AOSP getTime(): "the time the op was noted", as
     *  System.currentTimeMillis(). */
    public long getTime() { return mTime; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mOpCode);
        dest.writeInt(mNotingUid);
        dest.writeString(mAttributionTag);
        dest.writeString(mMessage);
        dest.writeLong(mTime);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof AsyncNotedAppOp)) return false;
        AsyncNotedAppOp other = (AsyncNotedAppOp) o;
        return mOpCode == other.mOpCode && mNotingUid == other.mNotingUid
                && mTime == other.mTime
                && java.util.Objects.equals(mAttributionTag, other.mAttributionTag)
                && java.util.Objects.equals(mMessage, other.mMessage);
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(mOpCode, mNotingUid, mAttributionTag, mMessage, mTime);
    }

    public static final Parcelable.Creator<AsyncNotedAppOp> CREATOR =
            new Parcelable.Creator<AsyncNotedAppOp>() {
        @Override public AsyncNotedAppOp createFromParcel(Parcel in) {
            return new AsyncNotedAppOp(in);
        }
        @Override public AsyncNotedAppOp[] newArray(int size) { return new AsyncNotedAppOp[size]; }
    };
}
