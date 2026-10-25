package com.google.android.gms.cast.framework;

import android.content.Context;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

// com.google.android.gms.cast.framework.CastContext -- the singleton entry point
// for the Cast framework. getSharedInstance returns a context whose
// SessionManager never discovers a receiver (no Cast backend on a de-Googled
// device), so the app's Cast button stays "no devices" and sessions never start.
// NEVER fabricates a device. Mirrors the published static accessors.
// Ref: developers.google.com/android/reference/.../framework/CastContext
public class CastContext {

    private static CastContext sInstance;
    private final SessionManager sessionManager = new SessionManager();

    private CastContext() {}

    public static synchronized CastContext getSharedInstance(Context context) {
        if (sInstance == null) sInstance = new CastContext();
        return sInstance;
    }

    // The Task-returning overload added in newer play-services-cast.
    public static synchronized Task<CastContext> getSharedInstance(
            Context context, java.util.concurrent.Executor executor) {
        return Tasks.forResult(getSharedInstance(context));
    }

    public static synchronized CastContext getSharedInstance() {
        return sInstance;
    }

    public SessionManager getSessionManager() { return sessionManager; }

    public int getCastState() { return 1; /* NO_DEVICES_AVAILABLE */ }

    public void addCastStateListener(Object listener) {}
    public void removeCastStateListener(Object listener) {}
}
