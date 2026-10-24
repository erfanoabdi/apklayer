// android.database.StaleDataException -- AOSP runtime exception thrown
// when a windowed cursor is accessed after its CursorWindow was
// released. AbstractWindowedCursor.checkPosition raises it.
// AOSP: frameworks/base/core/java/android/database/StaleDataException.java
package android.database;

public class StaleDataException extends RuntimeException {
    public StaleDataException() { super(); }
    public StaleDataException(String description) { super(description); }
}
