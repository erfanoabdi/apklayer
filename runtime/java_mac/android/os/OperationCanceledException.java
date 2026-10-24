// android.os.OperationCanceledException -- thrown when an operation is
// canceled via a CancellationSignal. CursorLoader.loadInBackground
// throws this when the load was canceled before the query ran; AOSP's
// SQLiteCursor / ContentResolver query paths throw it too. RuntimeException
// subclass so callers don't have to declare it.
//
// AOSP ref:
//   frameworks/base/core/java/android/os/OperationCanceledException.java
package android.os;

public class OperationCanceledException extends RuntimeException {
    public OperationCanceledException() {
        this(null);
    }

    public OperationCanceledException(String message) {
        super(message != null ? message : "The operation has been canceled.");
    }
}
