// android.database.CursorWindowAllocationException -- thrown by AOSP
// when a CursorWindow cannot allocate its shared-memory block. Our
// CursorWindow is a plain in-process buffer that never fails to
// allocate, so this is never thrown here, but the type must resolve
// for any app code that references or catches it.
// Source: frameworks/base/core/java/android/database/CursorWindowAllocationException.java
package android.database;

public class CursorWindowAllocationException extends RuntimeException {
    public CursorWindowAllocationException(String description) {
        super(description);
    }
}
