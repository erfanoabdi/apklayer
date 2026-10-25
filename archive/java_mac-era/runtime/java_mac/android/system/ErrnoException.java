// android.system.ErrnoException — POSIX errno-bearing exception. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:libcore/luni/src/main/java/android/system/ErrnoException.java
// Carries the function name + errno integer so the caller can decide
// between ENOENT (file missing, expected for cache traversal),
// EACCES (permission denied), etc.
package android.system;

public class ErrnoException extends Exception {
    public final int errno;
    public final String functionName;

    public ErrnoException(String functionName, int errno) {
        super(functionName + " failed: " + errnoMessage(errno));
        this.functionName = functionName;
        this.errno = errno;
    }
    public ErrnoException(String functionName, int errno, Throwable cause) {
        super(functionName + " failed: " + errnoMessage(errno), cause);
        this.functionName = functionName;
        this.errno = errno;
    }

    public int getErrno() { return errno; }

    /** Try to translate errno to its POSIX symbolic name; fall back to
     *  "errno=N" if unknown. */
    private static String errnoMessage(int errno) {
        switch (errno) {
            case OsConstants.ENOENT: return "No such file or directory";
            case OsConstants.EACCES: return "Permission denied";
            case OsConstants.EEXIST: return "File exists";
            case OsConstants.EISDIR: return "Is a directory";
            case OsConstants.ENOTDIR: return "Not a directory";
            case OsConstants.EIO:    return "I/O error";
            case OsConstants.ENOSPC: return "No space left on device";
            default: return "errno=" + errno;
        }
    }
}
