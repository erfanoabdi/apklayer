// android.os.DeadSystemRuntimeException -- thrown by
// RemoteException.rethrowFromSystemServer() when the failing call was a
// DeadObjectException, meaning the system_server died. There is nothing an
// app can do about it (the system restarts on its own), so it is an
// unchecked RuntimeException rather than a checked one.
//
// AOSP ref: frameworks/base/core/java/android/os/DeadSystemRuntimeException.java
// (@hide, but compiled public -- reachable via the public rethrowFromSystemServer()).
// Previously missing entirely, which made RemoteException.rethrowFromSystemServer()
// impossible to implement to spec.
package android.os;

public class DeadSystemRuntimeException extends RuntimeException {
    public DeadSystemRuntimeException() {
        super(new DeadSystemException());
    }
}
