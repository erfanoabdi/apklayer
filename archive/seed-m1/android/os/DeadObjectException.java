package android.os;

// apklayer patch: upstream class is generated from DeadObjectException.aidl; minimal copy.
public class DeadObjectException extends RemoteException {
    public DeadObjectException() { super(); }
    public DeadObjectException(Throwable cause) { super(cause); }
    public DeadObjectException(String message) { super(message); }
    public DeadObjectException(String message, Throwable cause) { super(message, cause, true, true); }
}
