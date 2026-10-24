// android.database.CursorJoiner -- performs a sorted merge-join over
// two cursors on a shared set of (already-sorted) join columns,
// yielding a Result per step (LEFT = a row present only in the left
// cursor, RIGHT = present only in the right, BOTH = a matching pair).
// Sync adapters use it to diff a local cursor against a server cursor.
// Both cursors MUST be sorted ascending on their join columns and have
// the same number of join columns.
// Source: frameworks/base/core/java/android/database/CursorJoiner.java
package android.database;

import java.util.Iterator;

public final class CursorJoiner implements Iterator<CursorJoiner.Result>,
        Iterable<CursorJoiner.Result> {
    private final Cursor mCursorLeft;
    private final Cursor mCursorRight;
    private boolean mCompareResultIsValid;
    private Result mCompareResult;
    private final int[] mColumnsLeft;
    private final int[] mColumnsRight;
    private final String[] mValues;

    public enum Result {
        /** The row in the right cursor is unique (no match on the left). */
        RIGHT,
        /** The row in the left cursor is unique (no match on the right). */
        LEFT,
        /** The rows in both cursors match. */
        BOTH
    }

    public CursorJoiner(Cursor cursorLeft, String[] columnNamesLeft,
                        Cursor cursorRight, String[] columnNamesRight) {
        if (columnNamesLeft.length != columnNamesRight.length) {
            throw new IllegalArgumentException(
                "you must have the same number of columns on the left and right, "
                + columnNamesLeft.length + " != " + columnNamesRight.length);
        }
        mCursorLeft = cursorLeft;
        mCursorRight = cursorRight;
        mCursorLeft.moveToFirst();
        mCursorRight.moveToFirst();
        mCompareResultIsValid = false;
        mColumnsLeft = buildColumnIndices(mCursorLeft, columnNamesLeft);
        mColumnsRight = buildColumnIndices(mCursorRight, columnNamesRight);
        // Scratch buffer: left columns followed by right columns.
        mValues = new String[mColumnsLeft.length + mColumnsRight.length];
    }

    @Override
    public Iterator<Result> iterator() {
        return this;
    }

    private static int[] buildColumnIndices(Cursor cursor, String[] columnNames) {
        int[] columns = new int[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            columns[i] = cursor.getColumnIndexOrThrow(columnNames[i]);
        }
        return columns;
    }

    /** Reads the join-column values of the given cursor into the values
     *  array at the given offset. */
    private void populateValues(String[] values, Cursor cursor, int[] columnIndices, int offset) {
        for (int i = 0; i < columnIndices.length; i++) {
            values[offset + i] = cursor.getString(columnIndices[i]);
        }
    }

    private void incrementCursors() {
        if (mCompareResult == Result.LEFT) {
            mCursorLeft.moveToNext();
        } else if (mCompareResult == Result.RIGHT) {
            mCursorRight.moveToNext();
        } else { // BOTH
            mCursorLeft.moveToNext();
            mCursorRight.moveToNext();
        }
    }

    @Override
    public boolean hasNext() {
        if (mCompareResultIsValid) {
            switch (mCompareResult) {
                case BOTH:  return !mCursorLeft.isLast() || !mCursorRight.isLast();
                case LEFT:  return !mCursorLeft.isLast() || !mCursorRight.isAfterLast();
                case RIGHT: return !mCursorLeft.isAfterLast() || !mCursorRight.isLast();
                default:    break;
            }
        }
        return !mCursorLeft.isAfterLast() || !mCursorRight.isAfterLast();
    }

    @Override
    public Result next() {
        if (!hasNext()) {
            throw new IllegalStateException("you must only call next() when hasNext() is true");
        }
        incrementIfRequired();

        boolean hasLeft = !mCursorLeft.isAfterLast();
        boolean hasRight = !mCursorRight.isAfterLast();

        if (hasLeft && hasRight) {
            populateValues(mValues, mCursorLeft, mColumnsLeft, 0 /* offset */);
            populateValues(mValues, mCursorRight, mColumnsRight, mColumnsLeft.length);
            switch (compareStrings(mValues)) {
                case -1:
                    mCompareResult = Result.LEFT;
                    break;
                case 0:
                    mCompareResult = Result.BOTH;
                    break;
                case 1:
                    mCompareResult = Result.RIGHT;
                    break;
                default:
                    throw new IllegalStateException("bad value from compareStrings");
            }
        } else if (hasLeft) {
            mCompareResult = Result.LEFT;
        } else {
            mCompareResult = Result.RIGHT;
        }
        mCompareResultIsValid = true;
        return mCompareResult;
    }

    private void incrementIfRequired() {
        if (mCompareResultIsValid) {
            incrementCursors();
            mCompareResultIsValid = false;
        }
    }

    /** Compares the left half of values against the right half pairwise,
     *  returning -1 / 0 / 1 the way String.compareTo would for the
     *  first differing pair. */
    private int compareStrings(String[] values) {
        int n = mColumnsLeft.length;
        for (int i = 0; i < n; i++) {
            String left = values[i];
            String right = values[n + i];
            int result = compareNullable(left, right);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static int compareNullable(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        int c = a.compareTo(b);
        return (c < 0) ? -1 : (c > 0) ? 1 : 0;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("not implemented");
    }
}
