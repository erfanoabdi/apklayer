package android.app;

/** The handle to an ongoing voice interaction. Mac.apk has no voice-interaction
 *  service, so an Activity is never launched via one (getVoiceInteractor→null);
 *  the type resolves. Source: frameworks/base/core/java/android/app/VoiceInteractor.java
 *  (android16-release).
 *
 *  Every method below keys off isDestroyed(), which is constant true here
 *  because no interaction session can ever have been established -- and AOSP's
 *  own bodies all begin with {@code if (isDestroyed()) { Log.w(...); return
 *  <empty>; }}. So these are AOSP's destroyed-interactor answers, not invented
 *  ones. */
public final class VoiceInteractor {
    VoiceInteractor() {}

    /** AOSP submitRequest(Request): {@code return submitRequest(request, null);}.
     *
     *  ⚠ This used to be declared {@code submitRequest(Object)}. AOSP's
     *  parameter type is Request, and the JVM resolves a call by exact
     *  descriptor -- so an app compiled against the SDK emitted
     *  {@code (Landroid/app/VoiceInteractor$Request;)Z} and took a
     *  NoSuchMethodError against the Object-typed one. Fixed to AOSP's type. */
    public boolean submitRequest(Request request) { return submitRequest(request, null); }

    /** AOSP submitRequest(Request, String name): "Submit a new Request to the
     *  currently active user interaction. @return Returns true of the request
     *  was successfully submitted, else false." A destroyed interactor cannot
     *  accept one, which is AOSP's false. */
    public boolean submitRequest(Request request, String name) { return false; }

    /** AOSP isDestroyed(): true once the interaction session is gone. No
     *  session is ever created here. */
    public boolean isDestroyed() { return true; }

    // AOSP returns Request[], not String[].
    // frameworks/base/core/java/android/app/VoiceInteractor.java
    public Request[] getActiveRequests() { return new Request[0]; }

    /** AOSP getActiveRequest(String name): "Returns the active request with that
     *  name, or null if there was none." Nothing was ever submitted. */
    public Request getActiveRequest(String name) { return null; }

    /** AOSP supportsCommands(String[]): "@return Array of booleans indicating
     *  whether each command is supported or not." AOSP's destroyed-interactor
     *  branch is literally {@code return new boolean[commands.length];} -- an
     *  all-false array of the SAME length, which callers index by position, so
     *  the length must match or they read out of bounds. */
    public boolean[] supportsCommands(String[] commands) {
        return new boolean[commands != null ? commands.length : 0];
    }

    /** AOSP getPackageName(): "the package name of the service providing the
     *  VoiceInteractionService." No such service is installed; AOSP's own body
     *  leaves the local null when there is no interactor to ask. */
    public String getPackageName() { return null; }

    public void notifyDirectActionsChanged() { }

    /** AOSP registerOnDestroyedCallback(Executor, Runnable): "Registers a
     *  callback to be called when the VoiceInteractor is destroyed. @return true
     *  if the callback was registered." AOSP validates both arguments, then
     *  refuses (false) if the interactor is ALREADY destroyed -- which it always
     *  is here -- because such a callback could never fire. Returning false is
     *  what tells the caller to run its teardown now instead of waiting. */
    public boolean registerOnDestroyedCallback(java.util.concurrent.Executor executor,
            Runnable callback) {
        if (executor == null) throw new NullPointerException("executor cannot be null");
        if (callback == null) throw new NullPointerException("callback cannot be null");
        return false;
    }

    /** AOSP unregisterOnDestroyedCallback(Runnable): "@return true if the
     *  callback was unregistered." Nothing was registered. */
    public boolean unregisterOnDestroyedCallback(Runnable callback) {
        if (callback == null) throw new NullPointerException("callback cannot be null");
        return false;
    }

    /** AOSP's `public static abstract class VoiceInteractor.Request`. Needed as a
     *  real type so getActiveRequests()'s descriptor matches the SDK's; no voice
     *  interaction ever starts here, so no instance is ever produced. */
    public static abstract class Request {
        Request() {}
        public String getName() { return null; }
        public void cancel() {}
        public android.content.Context getContext() { return null; }
        public Activity getActivity() { return null; }
        public void onCancel() {}
        public void onAttached(Activity activity) {}
        public void onDetached() {}
    }
}
