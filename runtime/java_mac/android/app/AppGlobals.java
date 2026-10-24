package android.app;

/** AOSP android.app.AppGlobals (@hide, but public in the platform's bytecode
 *  and reached through reflection and JNI). Missing here, a guest native
 *  library that looked it up got a null class, then a null method id, and the
 *  JNI bridge aborted the process on the call (WhatsApp, during start-up).
 *  Same answers ActivityThread gives. */
public class AppGlobals {
    private AppGlobals() {}

    /** The first Application the process created. */
    public static Application getInitialApplication() {
        return ActivityThread.currentApplication();
    }

    /** The package name of that Application. */
    public static String getInitialPackage() {
        Application app = getInitialApplication();
        return app != null ? app.getPackageName() : null;
    }

    /** Settings.Global core settings; none are mirrored, so the default stands. */
    public static int getIntCoreSetting(String key, int defaultValue) {
        return defaultValue;
    }

    public static float getFloatCoreSetting(String key, float defaultValue) {
        return defaultValue;
    }
}
