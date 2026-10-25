// android.os.strictmode.Violation -- base type for a StrictMode policy
// violation, handed to an OnThreadViolationListener / OnVmViolationListener.
//
// AOSP ref: frameworks/base/core/java/android/os/strictmode/Violation.java
// (android16-release). Ported in full, including the cached hashCode: AOSP
// dedupes violations by hash before reporting them, so a listener that keeps a
// Set<Violation> depends on message + stack trace + cause hashing together and
// on the cache being invalidated by initCause/setStackTrace/fillInStackTrace.
//
// Mac.apk enforces no StrictMode policy, so nothing here constructs one -- but
// an app may subclass, catch, or hash these itself, and the constructors are
// public in AOSP, so the shapes have to be right.
package android.os.strictmode;

public abstract class Violation extends Throwable {
    private static final long serialVersionUID = 0L;

    private int mHashCode;
    private boolean mHashCodeValid;

    Violation(String message) {
        super(message);
    }

    @Override
    public int hashCode() {
        synchronized (this) {
            if (mHashCodeValid) {
                return mHashCode;
            }
            final String message = getMessage();
            final Throwable cause = getCause();
            int hashCode = message != null ? message.hashCode() : getClass().hashCode();
            hashCode = hashCode * 37 + calcStackTraceHashCode(getStackTrace());
            hashCode = hashCode * 37 + (cause != null ? cause.toString().hashCode() : 0);
            mHashCodeValid = true;
            return mHashCode = hashCode;
        }
    }

    @Override
    public synchronized Throwable initCause(Throwable cause) {
        mHashCodeValid = false;
        return super.initCause(cause);
    }

    @Override
    public void setStackTrace(StackTraceElement[] stackTrace) {
        super.setStackTrace(stackTrace);
        synchronized (this) {
            mHashCodeValid = false;
        }
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        mHashCodeValid = false;
        return super.fillInStackTrace();
    }

    private static int calcStackTraceHashCode(final StackTraceElement[] stackTrace) {
        int hashCode = 17;
        if (stackTrace != null) {
            for (int i = 0; i < stackTrace.length; i++) {
                if (stackTrace[i] != null) {
                    hashCode = hashCode * 37 + stackTrace[i].hashCode();
                }
            }
        }
        return hashCode;
    }
}
