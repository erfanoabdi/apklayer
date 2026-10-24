// android.content.ReceiverCallNotAllowedException -- thrown when a
// BroadcastReceiver's onReceive() tries to registerReceiver() or bindService(),
// which the receiver Context forbids.
//
// AOSP ref:
// frameworks/base/core/java/android/content/ReceiverCallNotAllowedException.java
package android.content;

public class ReceiverCallNotAllowedException extends android.util.AndroidRuntimeException {
    private static final long serialVersionUID = 0L;

    public ReceiverCallNotAllowedException(String msg) {
        super(msg);
    }
}
