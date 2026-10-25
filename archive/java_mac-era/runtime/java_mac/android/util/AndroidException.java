// android.util.AndroidException -- the base checked exception for the
// Android platform. CameraAccessException, Settings.SettingNotFoundException,
// android.os.RemoteException, and several others extend it in AOSP. Mirrors
// AOSP's frameworks/base/core/java/android/util/AndroidException.java.
package android.util;

public class AndroidException extends Exception {
    public AndroidException() { super(); }
    public AndroidException(String name) { super(name); }
    public AndroidException(String name, Throwable cause) { super(name, cause); }
    public AndroidException(Exception cause) { super(cause); }
    public AndroidException(Throwable cause) { super(cause); }

    // AOSP's @hide 4-arg ctor (Suppression/writableStackTrace pass-through). Not
    // part of the public SDK stub jar (hence "@hide"), but it IS compiled public
    // on the real platform, and android.os.RemoteException's own @hide 4-arg
    // ctor calls super(message, cause, enableSuppression, writableStackTrace) --
    // without this overload here, RemoteException fails to compile/link against
    // AndroidException at all. protected in AOSP; matched here.
    protected AndroidException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
