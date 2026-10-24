// android.app.Service -- the framework component for long-running work
// without a UI. AOSP API-1+. Mac.apk is single-process, so a Service is
// instantiated + driven in-process by android.app.ServiceRegistry
// (started via Context.startService / PendingIntent.getService) rather
// than by an ActivityManagerService across a binder.
//
// AOSP ref: frameworks/base/core/java/android/app/Service.java.
// We mirror the public API surface (start-result constants, foreground
// notification contract, stopSelf family) so foreground-service apps
// (music players, downloaders, location trackers) resolve every method
// they call. We do not display Android notifications, so startForeground
// records the foreground state + id without rendering chrome.
package android.app;

import android.content.ComponentName;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.IBinder;

// AOSP: `public abstract class Service extends ContextWrapper implements
// ComponentCallbacks2, ContentCaptureManager.ContentCaptureClient`
// (frameworks/base/core/java/android/app/Service.java:310). We declare only the
// first; ContentCaptureClient is a hidden interface no app can name.
//
// TYPE IDENTITY, and it costs nothing at runtime: all three ComponentCallbacks2
// methods are already implemented below with exactly these signatures. The class
// simply never ADVERTISED the type, so every `instanceof ComponentCallbacks`
// answered false and every cast threw. That is the defect that stopped
// Thunderbird booting via android.app.Application
// (docs/application-attachbasecontext-never-ran_2026-08-12.md); this is the same
// bug on the other big component base class. Because assignability is inherited,
// this one line also makes IntentService, JobService, VpnService, DreamService,
// MediaBrowserService, TileService, WallpaperService, ConnectionService,
// ChooserTargetService, RemoteViewsService, AbstractInputMethodService,
// InputMethodService and AccessibilityService assignable to it.
public abstract class Service extends ContextWrapper
        implements android.content.ComponentCallbacks2 {
    /** onStartCommand return values. AOSP API-5+. Apps return these to
     *  tell the system how to behave if the service is killed; on
     *  Mac.apk the service is never killed, so the value is bookkeeping
     *  only, but it must be the right constant. */
    public static final int START_CONTINUATION_MASK = 0x0000000f;
    public static final int START_STICKY_COMPATIBILITY = 0;
    public static final int START_STICKY = 1;
    public static final int START_NOT_STICKY = 2;
    public static final int START_REDELIVER_INTENT = 3;
    public static final int START_TASK_REMOVED_COMPLETE = 1000;

    public static final int START_FLAG_REDELIVERY = 0x0001;
    public static final int START_FLAG_RETRY      = 0x0002;

    /** stopForeground flags. AOSP API-24+. */
    public static final int STOP_FOREGROUND_REMOVE = 0x00000001;
    public static final int STOP_FOREGROUND_DETACH = 0x00000002;

    // Foreground-service-type flags. AOSP API-29+ (subset; the common
    // ones apps OR together for setForegroundServiceType / the
    // startForeground(id, n, type) overload).
    public static final int FOREGROUND_SERVICE_TYPE_MANIFEST          = -1;
    public static final int FOREGROUND_SERVICE_TYPE_NONE             = 0;
    public static final int FOREGROUND_SERVICE_TYPE_DATA_SYNC        = 0x00000001;
    public static final int FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK   = 0x00000002;
    public static final int FOREGROUND_SERVICE_TYPE_PHONE_CALL       = 0x00000004;
    public static final int FOREGROUND_SERVICE_TYPE_LOCATION         = 0x00000008;
    public static final int FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE = 0x00000010;
    public static final int FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION = 0x00000020;
    public static final int FOREGROUND_SERVICE_TYPE_CAMERA           = 0x00000040;
    public static final int FOREGROUND_SERVICE_TYPE_MICROPHONE       = 0x00000080;
    public static final int FOREGROUND_SERVICE_TYPE_HEALTH           = 0x00000100;
    public static final int FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING = 0x00000200;
    public static final int FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED  = 0x00000400;
    public static final int FOREGROUND_SERVICE_TYPE_SPECIAL_USE      = 0x40000000;
    public static final int FOREGROUND_SERVICE_TYPE_SHORT_SERVICE    = 0x00000800;

    private int mForegroundId;
    private Notification mForegroundNotification;
    private int mForegroundServiceType = FOREGROUND_SERVICE_TYPE_NONE;
    private boolean mIsForeground;

    public Service() { super(null); }

    /** Package-private hook so ServiceRegistry (same package) can set the
     *  base context: ContextWrapper.attachBaseContext is protected and a
     *  protected member is not reachable on an arbitrary reference from
     *  another class, so we expose this thin forwarder for the registry. */
    final void macapkAttach(android.content.Context base) {
        attachBaseContext(base);
    }

    public void onCreate() {}
    public void onDestroy() {}

    /** AOSP Service.dump(FileDescriptor, PrintWriter, String[]): the hook
     *  `adb shell dumpsys activity service <name>` calls. AOSP's base
     *  implementation writes NOTHING -- it exists purely so a service can
     *  override it -- so an empty body is the faithful answer, not a stub.
     *
     *  It was absent, and that matters because the standard override shape
     *  begins with `super.dump(fd, writer, args)`: two corpus apps reference it
     *  (debug/preflight.py --all, v1.0.940) and would have taken a
     *  NoSuchMethodError inside their own dump. */
    protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
    }

    /** AOSP: default onStartCommand routes pre-API-5 apps through
     *  onStart and returns START_STICKY-compatibility. Modern apps
     *  override this; the default value matches AOSP. */
    public int onStartCommand(Intent intent, int flags, int startId) {
        onStart(intent, startId);
        return mStartCompatibility ? START_STICKY_COMPATIBILITY : START_STICKY;
    }

    /** Set true for apps targeting API < 5 (they expect onStart, not
     *  onStartCommand). AOSP toggles this from the target SDK; Mac.apk
     *  leaves it false (modern default). */
    private boolean mStartCompatibility = false;

    /** Deprecated pre-API-5 entry point. AOSP keeps it for old apps. */
    @Deprecated public void onStart(Intent intent, int startId) {}

    public IBinder onBind(Intent intent) { return null; }
    public boolean onUnbind(Intent intent) { return false; }
    public void onRebind(Intent intent) {}
    public void onTaskRemoved(Intent rootIntent) {}

    public void onConfigurationChanged(android.content.res.Configuration newConfig) {}
    public void onLowMemory() {}
    public void onTrimMemory(int level) {}

    /** AOSP API-34: called shortly before a time-limited (short) service
     *  is force-stopped. We never time-limit, so it is never invoked,
     *  but the override point must exist. */
    public void onTimeout(int startId) {}
    public void onTimeout(int startId, int fgsType) {}

    /** AOSP: returns the Application that owns this Service. */
    public final Application getApplication() {
        android.content.Context base = getApplicationContext();
        if (base instanceof Application) return (Application) base;
        return Application.getInstance();
    }

    public final void stopSelf() { ServiceRegistry.remove(this); }
    public final void stopSelf(int startId) { ServiceRegistry.remove(this); }
    /** AOSP: stop only if startId is the most recent start. We always
     *  stop and report success -- single-process, no concurrent starts
     *  to reconcile. */
    public final boolean stopSelfResult(int startId) {
        ServiceRegistry.remove(this);
        return true;
    }

    /** Promote the service to the foreground with an ongoing
     *  notification. AOSP renders the notification in the status bar;
     *  Mac.apk records the state (we don't display Android
     *  notifications). The notification id + object are kept so a later
     *  stopForeground(STOP_FOREGROUND_DETACH) can leave it posted. */
    public final void startForeground(int id, Notification notification) {
        mForegroundId = id;
        mForegroundNotification = notification;
        mIsForeground = true;
        if (notification != null) notification.flags |= Notification.FLAG_FOREGROUND_SERVICE;
    }
    /** AOSP API-29 overload taking a foregroundServiceType bitmask. */
    public final void startForeground(int id, Notification notification, int foregroundServiceType) {
        mForegroundServiceType = foregroundServiceType;
        startForeground(id, notification);
    }

    /** AOSP API-24+ flag-based stopForeground. STOP_FOREGROUND_REMOVE
     *  cancels the notification; STOP_FOREGROUND_DETACH leaves it
     *  posted as a standalone notification. */
    public final void stopForeground(int flags) {
        mIsForeground = false;
        if ((flags & STOP_FOREGROUND_REMOVE) != 0) {
            mForegroundNotification = null;
            mForegroundId = 0;
        }
    }
    /** Deprecated pre-API-24 boolean overload (removeNotification). */
    @Deprecated public final void stopForeground(boolean removeNotification) {
        stopForeground(removeNotification ? STOP_FOREGROUND_REMOVE : 0);
    }

    public final int getForegroundServiceType() { return mForegroundServiceType; }

    // ---- AOSP API-36 constant completion (android.app.Service) ----
    // Values extracted verbatim from the AOSP source for this class
    // (android16-release). javac INLINES a compile-time constant into the app at
    // ITS build time, so a shipped APK cannot observe a missing one at runtime --
    // these are here for our own framework code, for anything compiled against
    // THIS jar, and so the surface is complete rather than nearly complete.
    public static final int STOP_FOREGROUND_LEGACY = 0;
}
