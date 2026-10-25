// android.window.CompatOnBackInvokedCallback -- @hide marker interface, AOSP
// frameworks/base core/java/android/window/CompatOnBackInvokedCallback.java
// (android14-release, whole file):
//
//   "Marker interface for OnBackInvokedCallback used for backward compatibility
//    between the new system back and the old back event dispatching. Callbacks
//    implementing this interface are allowed to be registered even if
//    enableOnbackInvoked is set to false in the application manifest."
//
// WindowOnBackInvokedDispatcher.Checker is the only reader: a registration that
// is not enabled for the app is DROPPED unless its callback is one of these.
// AOSP's one implementor is ViewRootImpl's compat callback, which turns a system
// back GESTURE into a KEYCODE_BACK pair for a legacy app; Mac.apk has no system
// gesture (every BACK already arrives as a key), so nothing here implements it
// yet. The type exists so the Checker is the verbatim AOSP rule.
package android.window;

/** @hide */
public interface CompatOnBackInvokedCallback extends OnBackInvokedCallback {
    @Override
    void onBackInvoked();
}
