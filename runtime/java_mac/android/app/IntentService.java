// android.app.IntentService -- a Service that handles each start Intent
// sequentially on a single background worker thread, calling
// onHandleIntent for each, then stops itself when the queue drains.
// AOSP API-3+ (deprecated in API-30 in favor of WorkManager/JobIntent-
// Service, but still used widely).
//
// AOSP ref: frameworks/base/core/java/android/app/IntentService.java.
// We mirror its real behavior: onCreate spins up a HandlerThread, each
// onStartCommand enqueues the Intent to that thread's Handler, the
// handler calls onHandleIntent then stopSelf(startId). Previously this
// class had no worker thread and onHandleIntent never ran.
package android.app;

import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

// AOSP fidelity: the deprecation usage below MIRRORS AOSP, which deprecates its
// own APIs while continuing to implement them. Removing it would diverge
// from the surface we exist to reproduce, so it is suppressed here rather
// than globally -- every other lint category stays live for this file.
@SuppressWarnings("deprecation")
public abstract class IntentService extends Service {
    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;
    private final String mName;
    private boolean mRedelivery;

    /** Handler that runs onHandleIntent on the worker thread, then stops
     *  the service when this work item (msg.arg1 == startId) is done.
     *  AOSP IntentService.ServiceHandler. */
    private final class ServiceHandler extends Handler {
        ServiceHandler(Looper looper) { super(looper); }
        @Override public void handleMessage(Message msg) {
            try { onHandleIntent((Intent) msg.obj); }
            catch (Throwable ignored) {}
            stopSelf(msg.arg1);
        }
    }

    public IntentService(String name) { mName = name; }

    /** AOSP: controls onStartCommand's return value -- START_REDELIVER_INTENT
     *  when true so an interrupted handle is redelivered, else
     *  START_NOT_STICKY. */
    public void setIntentRedelivery(boolean enabled) { mRedelivery = enabled; }

    @Override public void onCreate() {
        super.onCreate();
        HandlerThread thread = new HandlerThread("IntentService[" + mName + "]");
        thread.start();
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override public void onStart(Intent intent, int startId) {
        if (mServiceHandler == null) {
            // Defensive: a start arriving before onCreate ran. Drive
            // onCreate so the worker exists, matching AOSP's guarantee
            // that onCreate precedes any onStart.
            onCreate();
        }
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        onStart(intent, startId);
        return mRedelivery ? START_REDELIVER_INTENT : START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        if (mServiceLooper != null) mServiceLooper.quit();
    }

    /** IntentService is not bindable by default. */
    @Override public IBinder onBind(Intent intent) { return null; }

    /** Invoked on the worker thread for each start Intent. */
    protected abstract void onHandleIntent(Intent intent);
}
