// android.content.BroadcastReceiver -- the app-side handler for a broadcast.
//
// AOSP ref: frameworks/base/core/java/android/content/BroadcastReceiver.java
// (android16-release). The result state (code / data / extras / abort flag) is
// REAL here, held in a PendingResult exactly as upstream does, so an ordered
// broadcast's receiver chain can pass a result along and the final receiver
// can read it back.
//
// ⚠ That is a behaviour change, not just new methods: every one of these used
// to be a no-op stub returning 0/null/false, which meant setResult() wrote
// nowhere and getResult*() could only ever report the default. Context's
// 7-arg sendOrderedBroadcast could not seed initialCode/initialData/
// initialExtras for that reason; it now installs a PendingResult carrying
// them on the final receiver.
//
// ⚠ STATED PARTIAL, matching the note at that call site: delivery itself is
// unchanged. Context.sendBroadcast fans out in-process to every matching
// receiver WITHOUT installing a shared PendingResult on each in turn, so an
// intermediate receiver's setResult() is not passed down the chain and its
// abortBroadcast() does not stop later receivers. Only the final receiver
// participates in the result protocol today.
package android.content;

import android.os.Bundle;
import android.os.IBinder;

public abstract class BroadcastReceiver {
    private PendingResult mPendingResult;
    private boolean mDebugUnregister;

    public BroadcastReceiver() {
    }

    public abstract void onReceive(Context context, Intent intent);

    /**
     * AOSP's carrier for one in-flight broadcast's result state. It is the
     * SAME object the receiver mutates through the BroadcastReceiver methods
     * below, which is why goAsync() can hand it out and keep working after
     * onReceive() returns.
     */
    public static class PendingResult {
        /** @hide upstream. */
        public static final int TYPE_COMPONENT = 0;
        public static final int TYPE_REGISTERED = 1;
        public static final int TYPE_UNREGISTERED = 2;

        final int mType;
        final boolean mOrderedHint;
        final boolean mInitialStickyHint;
        final int mSendingUser;
        final int mSentFromUid;
        final String mSentFromPackage;

        int mResultCode;
        String mResultData;
        Bundle mResultExtras;
        boolean mAbortBroadcast;
        boolean mFinished;

        /** Mac.apk entry point (AOSP's constructor takes the binder token and
         *  flags a single-process runtime has nothing to put in). */
        public PendingResult(int resultCode, String resultData, Bundle resultExtras,
                             int type, boolean ordered, boolean sticky,
                             int sendingUser, int sentFromUid, String sentFromPackage) {
            mResultCode = resultCode;
            mResultData = resultData;
            mResultExtras = resultExtras;
            mType = type;
            mOrderedHint = ordered;
            mInitialStickyHint = sticky;
            mSendingUser = sendingUser;
            mSentFromUid = sentFromUid;
            mSentFromPackage = sentFromPackage;
        }

        public final void setResultCode(int code) {
            checkSynchronousHint();
            mResultCode = code;
        }

        public final int getResultCode() { return mResultCode; }

        public final void setResultData(String data) {
            checkSynchronousHint();
            mResultData = data;
        }

        public final String getResultData() { return mResultData; }

        public final void setResultExtras(Bundle extras) {
            checkSynchronousHint();
            mResultExtras = extras;
        }

        /** AOSP :208 -- `makeMap` means "create the Bundle if absent and
         *  remember it", so a caller can accumulate into it. */
        public final Bundle getResultExtras(boolean makeMap) {
            Bundle e = mResultExtras;
            if (!makeMap) return e;
            if (e == null) mResultExtras = e = new Bundle();
            return e;
        }

        public final void setResult(int code, String data, Bundle extras) {
            checkSynchronousHint();
            mResultCode = code;
            mResultData = data;
            mResultExtras = extras;
        }

        public final boolean getAbortBroadcast() { return mAbortBroadcast; }

        public final void abortBroadcast() {
            checkSynchronousHint();
            mAbortBroadcast = true;
        }

        public final void clearAbortBroadcast() { mAbortBroadcast = false; }

        /** AOSP hands the result back to the ActivityManager so the next
         *  receiver in the ordered chain can run. There is no such dispatcher
         *  here -- Context.sendOrderedBroadcast fans out to every matching
         *  receiver and then posts the final receiver -- so finish() marks the
         *  result complete and nothing further is scheduled. */
        public final void finish() { mFinished = true; }

        /** @hide upstream. */
        public int getSendingUserId() { return mSendingUser; }
        public int getSentFromUid() { return mSentFromUid; }
        public String getSentFromPackage() { return mSentFromPackage; }

        /** AOSP :352 -- note it LOGS rather than throws: "we don't assert when
         *  receiving the initial sticky value, since that may have come from
         *  an ordered broadcast". Ported with the same non-fatal shape, since
         *  an app that sets a result on a non-ordered broadcast must not be
         *  killed for it. */
        void checkSynchronousHint() {
            if (mOrderedHint || mInitialStickyHint) {
                return;
            }
            com.macapk.loader.NativeLog.w("BroadcastReceiver",
                    "trying to return result during a non-ordered broadcast");
        }
    }

    // ---- The receiver-side facade over the current PendingResult. Every one
    // is null-tolerant exactly as AOSP is: a receiver invoked outside a
    // dispatch (or after goAsync()) has no pending result, and AOSP answers
    // the neutral value rather than NPEing.

    public final void setResult(int code, String data, Bundle extras) {
        checkSynchronousHint();
        if (mPendingResult != null) {
            mPendingResult.mResultCode = code;
            mPendingResult.mResultData = data;
            mPendingResult.mResultExtras = extras;
        }
    }

    public final void setResultCode(int code) {
        checkSynchronousHint();
        if (mPendingResult != null) mPendingResult.mResultCode = code;
    }

    public final int getResultCode() {
        return mPendingResult != null ? mPendingResult.mResultCode : 0;
    }

    public final void setResultData(String data) {
        checkSynchronousHint();
        if (mPendingResult != null) mPendingResult.mResultData = data;
    }

    public final String getResultData() {
        return mPendingResult != null ? mPendingResult.mResultData : null;
    }

    public final void setResultExtras(Bundle extras) {
        checkSynchronousHint();
        if (mPendingResult != null) mPendingResult.mResultExtras = extras;
    }

    public final Bundle getResultExtras(boolean makeMap) {
        if (mPendingResult == null) {
            return null;
        }
        return mPendingResult.getResultExtras(makeMap);
    }

    public final void abortBroadcast() {
        checkSynchronousHint();
        if (mPendingResult != null) mPendingResult.mAbortBroadcast = true;
    }

    public final boolean getAbortBroadcast() {
        return mPendingResult != null && mPendingResult.mAbortBroadcast;
    }

    public final void clearAbortBroadcast() {
        if (mPendingResult != null) mPendingResult.mAbortBroadcast = false;
    }

    public final boolean isOrderedBroadcast() {
        return mPendingResult != null && mPendingResult.mOrderedHint;
    }

    public final boolean isInitialStickyBroadcast() {
        return mPendingResult != null && mPendingResult.mInitialStickyHint;
    }

    /** AOSP :443 -- detach the pending result and hand it to the caller, which
     *  keeps the broadcast alive past onReceive() until the caller finishes
     *  it. Note it NULLS mPendingResult, so every method above then reports
     *  the neutral value -- that is the contract, not a bug. */
    public final PendingResult goAsync() {
        PendingResult res = mPendingResult;
        mPendingResult = null;
        return res;
    }

    /** @hide upstream, public in bytecode -- the dispatcher installs the
     *  result before invoking onReceive(). Context's broadcast fan-out uses
     *  this. */
    public final void setPendingResult(PendingResult result) { mPendingResult = result; }
    public final PendingResult getPendingResult() { return mPendingResult; }

    /** AOSP :679 -- "Accidentally left in the SDK." An empty body upstream,
     *  verbatim here; the ordered hint comes from the PendingResult. */
    public final void setOrderedHint(boolean isOrdered) {
    }

    public final void setDebugUnregister(boolean debug) { mDebugUnregister = debug; }
    public final boolean getDebugUnregister() { return mDebugUnregister; }

    public int getSentFromUid() {
        return mPendingResult != null ? mPendingResult.mSentFromUid
                                      : android.os.Process.INVALID_UID;
    }

    public String getSentFromPackage() {
        return mPendingResult != null ? mPendingResult.mSentFromPackage : null;
    }

    /** @SystemApi upstream. Single user here. */
    public final android.os.UserHandle getSendingUser() {
        return android.os.UserHandle.of(getSendingUserId());
    }

    /** @hide upstream; AOSP NPEs when there is no pending result, so the null
     *  guard here is strictly more forgiving and cannot break a caller. */
    public int getSendingUserId() {
        return mPendingResult != null ? mPendingResult.mSendingUser
                                      : android.os.UserHandle.USER_SYSTEM;
    }

    /** AOSP :473 -- "provide a binder to an already-bound service", used by a
     *  receiver that wants to talk to a service ONLY if it is already running.
     *  AOSP asks the ActivityManager, which returns null when the service is
     *  not currently bound. Mac.apk's ServiceRegistry has no
     *  "already-bound-only" query, and BINDING here would be the wrong answer:
     *  peekService must not start or bind anything, and a caller uses null to
     *  mean "not running, skip". Null is therefore the correct result, and it
     *  is the same one a device gives whenever the service is not up. */
    public IBinder peekService(Context myContext, Intent service) {
        return null;
    }

    private void checkSynchronousHint() {
        if (mPendingResult != null) {
            mPendingResult.checkSynchronousHint();
        }
    }
}
