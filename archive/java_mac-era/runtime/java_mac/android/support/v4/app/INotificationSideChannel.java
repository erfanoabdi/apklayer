// Mac.apk -- stub for a support-library AIDL interface.
//
// MUST BE AN INTERFACE. This was auto-generated as a `class` at v1.0.92, which
// is wrong in a way no converter gate could see: the app's own dex declares
// INotificationSideChannel$Stub / $Stub$Proxy / $Default as IMPLEMENTING it, and
// AppClassLoader is parent-first for android.support.*, so our shadow won and
// every implementer failed to link with
//
//   IncompatibleClassChangeError: class ...INotificationSideChannel$Stub can not
//   implement ...INotificationSideChannel, because it is not an interface
//
// at RUN TIME, on any app using androidx.core notifications (measured on F-Droid
// and GD Lite). Found via the OTHER bucket of debug/dexconv_verify/VerifyCheck.java,
// which counts link failures that are neither a VerifyError nor a missing
// dependency -- a bucket the G8 fleet number does not include.
//
// Shape mirrors the real android.support.v4.app.INotificationSideChannel: an
// AIDL-generated interface extending android.os.IInterface. Methods are declared
// so an implementer's overrides link.
package android.support.v4.app;

import android.os.IInterface;

public interface INotificationSideChannel extends IInterface {
    void notify(String packageName, int id, String tag, android.app.Notification notification);

    void cancel(String packageName, int id, String tag);

    void cancelAll(String packageName);
}
