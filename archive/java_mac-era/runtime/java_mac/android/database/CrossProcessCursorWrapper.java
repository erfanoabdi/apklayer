// android.database.CrossProcessCursorWrapper -- a CursorWrapper that
// also satisfies the CrossProcessCursor contract. When the wrapped
// cursor is itself a CrossProcessCursor, fillWindow/getWindow/onMove
// delegate to it; otherwise the wrapper synthesizes the window from the
// plain Cursor via DatabaseUtils.cursorFillWindow. Lets a non-windowed
// cursor be presented through the windowed (cross-process) API.
// Source: frameworks/base/core/java/android/database/CrossProcessCursorWrapper.java
package android.database;

public class CrossProcessCursorWrapper extends CursorWrapper implements CrossProcessCursor {

    public CrossProcessCursorWrapper(Cursor cursor) {
        super(cursor);
    }

    @Override
    public void fillWindow(int position, CursorWindow window) {
        final Cursor cursor = getWrappedCursor();
        if (cursor instanceof CrossProcessCursor) {
            ((CrossProcessCursor) cursor).fillWindow(position, window);
        } else {
            DatabaseUtils.cursorFillWindow(cursor, position, window);
        }
    }

    @Override
    public CursorWindow getWindow() {
        final Cursor cursor = getWrappedCursor();
        if (cursor instanceof CrossProcessCursor) {
            return ((CrossProcessCursor) cursor).getWindow();
        }
        return null;
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        final Cursor cursor = getWrappedCursor();
        if (cursor instanceof CrossProcessCursor) {
            return ((CrossProcessCursor) cursor).onMove(oldPosition, newPosition);
        }
        return true;
    }
}
