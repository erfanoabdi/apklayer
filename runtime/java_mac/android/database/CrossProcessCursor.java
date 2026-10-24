// android.database.CrossProcessCursor -- the AOSP marker interface for
// cursors whose row data can be marshalled into a CursorWindow for a
// remote (cross-process) reader. In Mac.apk everything is in-process,
// so the window path is a local convenience rather than a Binder
// transport, but the type must resolve: AbstractWindowedCursor-derived
// cursors are CrossProcessCursors in AOSP and app/loader code branches
// on `instanceof CrossProcessCursor` and calls fillWindow on it.
// Source: frameworks/base/core/java/android/database/CrossProcessCursor.java
package android.database;

public interface CrossProcessCursor extends Cursor {
    /** Returns the CursorWindow currently backing this cursor, or null
     *  if the cursor does not have a window. */
    CursorWindow getWindow();

    /** Copies rows of cell data into the window starting at the given
     *  position. AOSP clears the window, sets the start position, and
     *  pulls each row's cells in. */
    void fillWindow(int position, CursorWindow window);

    /** Called when the cursor position changes (mirrors
     *  AbstractCursor.onMove); a window cursor refills the window when
     *  the requested position falls outside it. */
    boolean onMove(int oldPosition, int newPosition);
}
