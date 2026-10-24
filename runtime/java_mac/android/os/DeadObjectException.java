// android.os.DeadObjectException -- thrown by transact() when the remote process
// hosting the IBinder has died. Service-binding code catches this by type to
// trigger a rebind. extends RemoteException, matching AOSP.
//
// AOSP ref: frameworks/base/core/java/android/os/DeadObjectException.java
package android.os;

public class DeadObjectException extends RemoteException {
    public DeadObjectException() { super(); }
    public DeadObjectException(String message) { super(message); }
}
