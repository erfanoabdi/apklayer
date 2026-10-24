// android.window.OnBackInvokedCallback -- callback invoked when a back
// gesture/button-press is completed and committed. AOSP frameworks/base
// core/java/android/window/OnBackInvokedCallback.java (API 33+): a plain
// interface, registered via
// OnBackInvokedDispatcher#registerOnBackInvokedCallback(int, OnBackInvokedCallback).
//
// Was a "public class OnBackInvokedCallback { public OnBackInvokedCallback()
// {} }" auto-generated stub (v1.0.92) -- WRONG_KIND. AOSP declares this as an
// interface, and app code commonly assigns it a lambda:
//   dispatcher.registerOnBackInvokedCallback(priority, () -> { ... });
// A lambda target must be a functional interface; against a class the call
// site cannot even be produced by javac, and any app class compiled against
// the real android.jar as `class X implements OnBackInvokedCallback` throws
// IncompatibleClassChangeError the moment X is loaded/linked (the JVM checks
// that every type named in an `implements` clause is actually an interface),
// independent of whether OnBackInvokedDispatcher is ever reached. Fixed here
// to the exact AOSP shape.
package android.window;

public interface OnBackInvokedCallback {
    /** Called when a back gesture has been completed and committed, or a
     *  back button press has been released and committed. */
    void onBackInvoked();
}
