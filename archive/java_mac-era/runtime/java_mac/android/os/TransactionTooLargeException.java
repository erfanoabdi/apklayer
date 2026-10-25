// android.os.TransactionTooLargeException -- thrown when a binder transaction
// buffer overflows (too much data in one transact, e.g. a giant Bundle).
// Apps catch this around large saved-state writes. extends RemoteException.
//
// AOSP ref: frameworks/base/core/java/android/os/TransactionTooLargeException.java
package android.os;

public class TransactionTooLargeException extends RemoteException {
    public TransactionTooLargeException() { super(); }
    public TransactionTooLargeException(String msg) { super(msg); }
}
