// android.os.strictmode.SqliteObjectLeakedViolation -- AOSP ref:
// frameworks/base/core/java/android/os/strictmode/
// SqliteObjectLeakedViolation.java (android16-release).
package android.os.strictmode;

public final class SqliteObjectLeakedViolation extends Violation {
    public SqliteObjectLeakedViolation(String message, Throwable originStack) {
        super(message);
        initCause(originStack);
    }
}
