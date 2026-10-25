// android.database.CursorIndexOutOfBoundsException -- thrown by
// AbstractCursor.checkPosition (and value getters reached at an
// invalid row) when a cursor is read before moveToFirst / after the
// last row. Apps catch this exact type to distinguish "no rows" from
// other failures, so it must extend IndexOutOfBoundsException (not a
// generic RuntimeException) and carry both ctor shapes.
// Source: frameworks/base/core/java/android/database/CursorIndexOutOfBoundsException.java
package android.database;

public class CursorIndexOutOfBoundsException extends IndexOutOfBoundsException {
    public CursorIndexOutOfBoundsException(int index, int size) {
        super("Index " + index + " requested, with a size of " + size);
    }

    public CursorIndexOutOfBoundsException(String message) {
        super(message);
    }
}
