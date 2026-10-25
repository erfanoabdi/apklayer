// android.os.RemoteException -- parent exception for all Binder
// remote-invocation errors. Mirrors AOSP's
// frameworks/base/core/java/android/os/RemoteException.java.
//
// Superclass fix: this used to extend java.lang.Exception directly. AOSP
// extends android.util.AndroidException. That matters for two independent
// reasons: (1) `catch (AndroidException e)` in app/framework code (a common
// catch-all for platform checked exceptions) must catch a RemoteException too,
// and won't if the superclass is wrong; (2) DeadObjectException and every other
// RemoteException subclass inherits whatever we get wrong here. Confirmed via
// debug/framework_lint.py's superclass-diff finding.
package android.os;

import android.util.AndroidException;

public class RemoteException extends AndroidException {
    public RemoteException() { super(); }
    public RemoteException(String message) { super(message); }

    // AOSP @hide (compiled public on the real platform; see the matching ctor
    // added to AndroidException).
    public RemoteException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    // AOSP @hide.
    public RemoteException(Throwable cause) {
        this(cause.getMessage(), cause, true, false);
    }

    /**
     * Rethrow this as an unchecked runtime exception. AOSP:
     * "Apps making calls into other processes may end up persisting internal
     * state or making security decisions based on the perceived success or
     * failure of a call ... we want to strongly throw when there was trouble
     * with the transaction."
     */
    public RuntimeException rethrowAsRuntimeException() {
        throw new RuntimeException(this);
    }

    /**
     * Rethrow this exception when it came from the system server, collapsing
     * it into a clean DeadSystemRuntimeException rather than propagating a
     * misleading stack trace. Mirrors AOSP exactly, including that a
     * DeadObjectException's original detail is DISCARDED in favor of a fresh
     * DeadSystemException with AOSP's fixed message.
     */
    public RuntimeException rethrowFromSystemServer() {
        if (this instanceof DeadObjectException) {
            throw new DeadSystemRuntimeException();
        } else {
            throw new RuntimeException(this);
        }
    }
}
