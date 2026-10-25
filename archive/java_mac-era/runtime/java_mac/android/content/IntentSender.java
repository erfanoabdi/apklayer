// android.content.IntentSender -- handle to a remote Intent. Mac.apk
// has no IPC; the type exists so apps that declare `throws
// SendIntentException` (e.g. for Play Games sign-in flows) compile
// and resolve at runtime.
package android.content;

import android.os.Parcel;
import android.os.Parcelable;

public class IntentSender implements Parcelable {
    // When created from a PendingIntent (PendingIntent.getIntentSender),
    // sendIntent() re-dispatches through that token so an IntentSender
    // obtained from a PendingIntent actually fires the wrapped operation.
    private android.app.PendingIntent target;

    public IntentSender() {}
    public IntentSender(android.app.PendingIntent target) { this.target = target; }

    public void sendIntent(Context context, int code, Intent intent,
                           OnFinished onFinished, android.os.Handler handler)
        throws SendIntentException {
        if (target == null) return;
        try {
            target.send(context, code, intent);
        } catch (android.app.PendingIntent.CanceledException e) {
            throw new SendIntentException(e);
        }
        if (onFinished != null) {
            Runnable r = () -> {
                try { onFinished.onSendFinished(this, intent, code, null, null); }
                catch (Throwable ignored) {}
            };
            if (handler != null) handler.post(r); else r.run();
        }
    }

    /** AOSP core/java/android/content/IntentSender.java:206 -- the
     *  requiredPermission overload. On a device the permission is enforced by
     *  the ActivityManager against the RECEIVER of the broadcast; Mac.apk
     *  dispatches in-process to the app's own receivers, which by definition
     *  hold every permission the app declares, so no receiver can be filtered
     *  out by it. Delegating is therefore the same delivery set, not a
     *  weakened check. */
    public void sendIntent(Context context, int code, Intent intent,
                           OnFinished onFinished, android.os.Handler handler,
                           String requiredPermission)
            throws SendIntentException {
        sendIntent(context, code, intent, onFinished, handler);
    }

    /** AOSP :286 -- the API-36 form: an Executor instead of a Handler, plus a
     *  BroadcastOptions bundle. `options` carries temp-allowlist and
     *  background-activity-start knobs that do not exist in a single-process
     *  host with no background-execution limits, so it does not change
     *  delivery here (the same reasoning Context.sendBroadcast(Intent,String,
     *  Bundle) already documents). */
    public void sendIntent(Context context, int code, Intent intent,
                           String requiredPermission, android.os.Bundle options,
                           java.util.concurrent.Executor executor, OnFinished onFinished)
            throws SendIntentException {
        if (target == null) return;
        try {
            target.send(context, code, intent);
        } catch (android.app.PendingIntent.CanceledException e) {
            throw new SendIntentException(e);
        }
        if (onFinished != null) {
            final IntentSender self = this;
            final Intent delivered = intent;
            Runnable r = new Runnable() {
                @Override public void run() {
                    try { onFinished.onSendFinished(self, delivered, code, null, null); }
                    catch (Throwable ignored) {}
                }
            };
            if (executor != null) executor.execute(r); else r.run();
        }
    }

    /** Mac.apk: the PendingIntent behind this sender, or null for a target-less
     *  one. AOSP's counterpart is the @hide getTarget() (the IIntentSender that
     *  Activity.startIntentSenderForResultInner hands to
     *  startActivityIntentSender). */
    public android.app.PendingIntent macapkTarget() { return target; }

    /** AOSP :317 -- `return getCreatorPackage();` verbatim. */
    public String getTargetPackage() { return getCreatorPackage(); }

    /** AOSP :330 -- the package that created the underlying PendingIntent,
     *  "or null if there is none associated with it" -- which is exactly the
     *  target-less case reachable through our public no-arg constructor. */
    public String getCreatorPackage() {
        return (target != null) ? target.getCreatorPackage() : null;
    }

    /** AOSP :343 -- "-1 if there is none associated with it". */
    public int getCreatorUid() {
        return (target != null) ? target.getCreatorUid() : -1;
    }

    /** AOSP :358, verbatim:
     *      int uid = getCachedInfo().getCreatorUid();
     *      return uid > 0 ? new UserHandle(UserHandle.getUserId(uid)) : null;
     *  Derived from getCreatorUid() above rather than answered independently,
     *  so the two can never disagree -- note that means a PendingIntent whose
     *  creator uid is unknown (0 or -1) yields null here, which is AOSP's own
     *  rule and not a Mac.apk shortcut. */
    public android.os.UserHandle getCreatorUserHandle() {
        int uid = getCreatorUid();
        return uid > 0 ? new android.os.UserHandle(android.os.UserHandle.getUserId(uid)) : null;
    }

    /** AOSP :421 / :437 -- the null-tolerant static pair. AOSP writes the
     *  target's binder (or null); the analogue here is the PendingIntent this
     *  sender wraps, written with the same typed encoding writeToParcel below
     *  uses, so the two stay in step. */
    public static void writeIntentSenderOrNullToParcel(IntentSender sender, Parcel out) {
        out.writeTypedObject(sender != null ? sender.target : null, 0);
    }

    public static IntentSender readIntentSenderOrNullFromParcel(Parcel in) {
        android.app.PendingIntent pi = in.readTypedObject(android.app.PendingIntent.CREATOR);
        return (pi != null) ? new IntentSender(pi) : null;
    }

    /** AOSP IntentSender.equals/hashCode (IntentSender.java:352-365) compare
     *  the underlying IIntentSender binder, so two handles to the SAME pending
     *  operation are equal. Inheriting Object identity instead breaks the
     *  standard "have I already registered this sender?" check and makes an
     *  IntentSender useless as a map key. Our analogue is the wrapped
     *  PendingIntent, which already defines AOSP-shaped value equality
     *  (type + requestCode + the Intent's component/action); Objects.equals
     *  keeps the null-target case (the public no-arg constructor) from
     *  throwing, which AOSP does not have to handle because its mTarget is
     *  never null. */
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof IntentSender)) return false;
        return java.util.Objects.equals(target, ((IntentSender) other).target);
    }
    @Override public int hashCode() {
        return target != null ? target.hashCode() : 0;
    }

    // ---- Parcelable ----
    //
    // ⚠ Until v1.0.940 this was an empty body plus a CREATOR that answered a
    // target-less IntentSender. That was invisible while android.os.Parcel
    // faked marshalling with a side list of live objects; now that it marshals
    // for real, an IntentSender handed to an app through a Bundle (the standard
    // "call me back when you are done" handle -- Play sign-in, package
    // installer sessions) would come back inert and sendIntent() would silently
    // return without firing anything.
    //
    // AOSP writes `out.writeStrongBinder(mTarget.asBinder())`
    // (frameworks/base/core/java/android/content/IntentSender.java:315-317),
    // i.e. a handle to the remote IIntentSender. There is no binder transport
    // here and the target is a live in-process PendingIntent, so we flatten the
    // PendingIntent itself. That is the same substitution the rest of this tree
    // makes for binder-carried handles, and it preserves the observable
    // contract: the reconstructed IntentSender re-dispatches the same operation
    // with the same request code.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        // Typed, not writeParcelable: the CREATOR is named on both sides, so no
        // class name goes on the wire and the read side needs no ClassLoader.
        dest.writeTypedObject(target, flags);
    }

    public static final Creator<IntentSender> CREATOR = new Creator<IntentSender>() {
        @Override public IntentSender createFromParcel(Parcel s) {
            // AOSP answers null when the binder was null. The analogue here is a
            // null target, which is what writeTypedObject encodes as 0 -- but we
            // still return the OBJECT rather than null, because unlike AOSP a
            // target-less IntentSender is reachable through the public no-arg
            // constructor and sendIntent() already treats it as a no-op.
            return new IntentSender(s.readTypedObject(android.app.PendingIntent.CREATOR));
        }
        @Override public IntentSender[] newArray(int size) { return new IntentSender[size]; }
    };

    public interface OnFinished {
        void onSendFinished(IntentSender sender, Intent intent, int resultCode,
                            String resultData, android.os.Bundle resultExtras);
    }

    // AOSP: `extends android.util.AndroidException`, so `catch (AndroidException)`
    // catches it. frameworks/base/core/java/android/content/IntentSender.java
    public static class SendIntentException extends android.util.AndroidException {
        public SendIntentException() { super(); }
        public SendIntentException(String msg) { super(msg); }
        public SendIntentException(Throwable cause) { super(cause); }
    }
}
