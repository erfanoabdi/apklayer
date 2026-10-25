// android.view.InflateException -- thrown by LayoutInflater and
// PreferenceInflater when XML inflation fails. Apps catch the type
// explicitly; without it, NoClassDefFoundError fires inside a
// synchronized block in PreferenceInflater.inflate and the
// subsequent monitorexit during unwind can manifest as
// IllegalMonitorStateException, masking the real cause. Mirrors
// AOSP frameworks/base/core/java/android/view/InflateException.java.
package android.view;

public class InflateException extends RuntimeException {
    public InflateException() { super(); }
    public InflateException(String detailMessage) { super(detailMessage); }
    public InflateException(String detailMessage, Throwable cause) {
        super(detailMessage, cause);
    }
    public InflateException(Throwable cause) { super(cause); }
}
