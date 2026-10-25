package android.telephony;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.telecom.PhoneAccountHandle;

/**
 * Base class for a carrier's visual-voicemail client service.
 *
 * On Android the platform binds to this service and drives the four callbacks
 * as the SIM/cell state changes. Mac.apk has no telephony stack, so nothing
 * ever binds and no callback fires -- the same as a device where the carrier's
 * VVM service was never selected. The class must still exist so an app that
 * declares one compiles and its onBind resolves.
 *
 * Source: frameworks/base/telephony/java/android/telephony/VisualVoicemailService.java
 */
public abstract class VisualVoicemailService extends Service {

    public static final String SERVICE_INTERFACE = "android.telephony.VisualVoicemailService";

    /**
     * A handle the subclass finishes when it is done with a callback. Mac.apk
     * never hands one out, but the type is public API and finish() is a real
     * (idempotent) operation on it.
     */
    public static class VisualVoicemailTask {

        private final Runnable mOnFinished;
        private boolean mFinished;

        /** @hide */
        public VisualVoicemailTask() { mOnFinished = null; }

        /** @hide */
        public VisualVoicemailTask(Runnable onFinished) { mOnFinished = onFinished; }

        public final void finish() {
            if (mFinished) return;
            mFinished = true;
            if (mOnFinished != null) mOnFinished.run();
        }
    }

    /**
     * AOSP returns the framework's own binder here. Mac.apk has no
     * VisualVoicemailService transport for the platform side, so there is no
     * peer to bind and null -- Service's documented "binding not supported" --
     * is returned rather than a binder nothing will ever talk to.
     */
    @Override public IBinder onBind(Intent intent) { return null; }

    public abstract void onCellServiceConnected(VisualVoicemailTask task,
            PhoneAccountHandle phoneAccountHandle);

    public abstract void onSmsReceived(VisualVoicemailTask task, VisualVoicemailSms sms);

    public abstract void onSimRemoved(VisualVoicemailTask task,
            PhoneAccountHandle phoneAccountHandle);

    public abstract void onStopped(VisualVoicemailTask task);
}
