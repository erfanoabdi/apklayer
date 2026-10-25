// android.database.MatrixCursor -- AOSP in-memory cursor. Was a no-op shell
// (addRow did nothing, getCount=0) so every in-process path that builds an
// in-memory cursor (ContentProvider.query results, SearchView/search-suggestion
// adapters, androidx CursorAdapter/SimpleCursorAdapter, androidx.paging
// fallbacks, MergeCursor-wrapped cursors) silently yielded ZERO rows -- the
// Bundle-Parcelable silent-blank class. Now stores rows + reads them with type
// coercion + a real getType(). v1.0.423.
//
// v1.0.923: rebased onto AbstractCursor, which is what AOSP does
// (frameworks/base/core/java/android/database/MatrixCursor.java:29,
// "public class MatrixCursor extends AbstractCursor"). The standalone
// implementation had drifted from AOSP on four separately-observable points,
// each of which an app can see:
//   - getColumnIndex was an EXACT match. AbstractCursor's is equalsIgnoreCase
//     and strips everything up to the last '.' (AbstractCursor.java:316-336),
//     so "BB", "bB" and "t.bb" all resolve on a real MatrixCursor. Note this
//     is the OPPOSITE of SQLiteCursor, which overrides getColumnIndex with an
//     exact HashMap lookup (SQLiteCursor.java:174-200): the same question has
//     two different correct answers depending on the cursor type, and one
//     shared implementation cannot satisfy both.
//   - addRow silently truncated or padded a wrong-length row. AOSP throws
//     IllegalArgumentException (:104-118, :128-171), which is how a provider
//     finds out its projection and its row builder disagree.
//   - getColumnName(oob) returned null instead of indexing the array and
//     letting ArrayIndexOutOfBoundsException out.
//   - the value getters coerced leniently. AOSP PARSES a non-Number through
//     Integer/Long/Double.parseXxx and lets NumberFormatException out
//     (:263-305), and getBlob is a bare `(byte[]) value` cast, i.e. a
//     ClassCastException on anything else. A SQLite cursor answers the same
//     questions with 0 / a SQLiteException instead -- the divergence between
//     the two cursor types is real and apps are written against it.
package android.database;

import java.util.ArrayList;

public class MatrixCursor extends AbstractCursor {
    private final String[] columnNames;
    private Object[] data;
    private int rowCount = 0;
    private final int columnCount;

    /** Not AOSP: a zero-column cursor, used inside this tree wherever a
     *  provider has to answer "no such collection" with a shaped-but-empty
     *  result. Harmless extra ctor; AOSP callers always name their columns. */
    public MatrixCursor() { this(new String[0]); }

    public MatrixCursor(String[] columnNames) { this(columnNames, 16); }

    public MatrixCursor(String[] columnNames, int initialCapacity) {
        this.columnNames = columnNames == null ? new String[0] : columnNames;
        this.columnCount = this.columnNames.length;
        if (initialCapacity < 1) initialCapacity = 1;
        this.data = new Object[columnCount * initialCapacity];
    }

    /** AOSP MatrixCursor.get (:66-81). It checks the COLUMN bounds and the
     *  ROW position and nothing else -- in particular it never consults
     *  mClosed, which is why a closed MatrixCursor keeps handing out data
     *  while a closed SQLiteCursor throws StaleDataException. */
    private Object get(int column) {
        if (column < 0 || column >= columnCount) {
            throw new CursorIndexOutOfBoundsException("Requested column: "
                    + column + ", # of columns: " + columnCount);
        }
        if (mPos < 0) {
            throw new CursorIndexOutOfBoundsException("Before first row.");
        }
        if (mPos >= rowCount) {
            throw new CursorIndexOutOfBoundsException("After last row.");
        }
        return data[mPos * columnCount + column];
    }

    public RowBuilder newRow() {
        final int row = rowCount++;
        final int endIndex = rowCount * columnCount;
        ensureCapacity(endIndex);
        return new RowBuilder(row);
    }

    public void addRow(Object[] columnValues) {
        if (columnValues.length != columnCount) {
            throw new IllegalArgumentException("columnNames.length = "
                    + columnCount + ", columnValues.length = " + columnValues.length);
        }
        int start = rowCount++ * columnCount;
        ensureCapacity(start + columnCount);
        System.arraycopy(columnValues, 0, data, start, columnCount);
    }

    public void addRow(Iterable<?> columnValues) {
        int start = rowCount * columnCount;
        int end = start + columnCount;
        ensureCapacity(end);

        if (columnValues instanceof ArrayList<?>) {
            addRow((ArrayList<?>) columnValues, start);
            return;
        }

        int current = start;
        Object[] localData = data;
        for (Object columnValue : columnValues) {
            if (current == end) {
                throw new IllegalArgumentException("columnValues.size() > columnNames.length");
            }
            localData[current++] = columnValue;
        }
        if (current != end) {
            throw new IllegalArgumentException("columnValues.size() < columnNames.length");
        }
        // AOSP bumps the count only here, so a row that threw above is not
        // counted (its cells are already written, but unreachable).
        rowCount++;
    }

    /** AOSP's ArrayList fast path (:159-171). It reports the mismatch as
     *  "columnValues.size()" rather than the >/< pair above; kept because the
     *  message is the only thing distinguishing the two paths. */
    private void addRow(ArrayList<?> columnValues, int start) {
        int size = columnValues.size();
        if (size != columnCount) {
            throw new IllegalArgumentException("columnNames.length = "
                    + columnCount + ", columnValues.size() = " + size);
        }
        rowCount++;
        Object[] localData = data;
        for (int i = 0; i < size; i++) {
            localData[start + i] = columnValues.get(i);
        }
    }

    private void ensureCapacity(int size) {
        if (size > data.length) {
            Object[] oldData = this.data;
            int newSize = data.length * 2;
            if (newSize < size) newSize = size;
            this.data = new Object[newSize];
            System.arraycopy(oldData, 0, this.data, 0, oldData.length);
        }
    }

    /** AOSP's RowBuilder is an INNER class holding only the row index, so the
     *  named-add overload can scan the enclosing cursor's column names. */
    public class RowBuilder {
        private final int row;
        private final int endIndex;
        private int index;

        RowBuilder(int row) {
            this.row = row;
            this.index = row * columnCount;
            this.endIndex = index + columnCount;
        }

        /** Positional add. Overflowing the row is a
         *  CursorIndexOutOfBoundsException, not a silent drop. */
        public RowBuilder add(Object columnValue) {
            if (index == endIndex) {
                throw new CursorIndexOutOfBoundsException("No more columns left.");
            }
            data[index++] = columnValue;
            return this;
        }

        /** Named add. A column this cursor does not define is SILENTLY
         *  IGNORED (AOSP :232-240) -- the overload exists so a provider can
         *  offer every column it knows and let the caller's projection decide
         *  which ones land, so throwing here would break that idiom. */
        public RowBuilder add(String columnName, Object value) {
            for (int i = 0; i < columnNames.length; i++) {
                if (columnName.equals(columnNames[i])) {
                    data[(row * columnCount) + i] = value;
                }
            }
            return this;
        }

        public final RowBuilder add(int columnIndex, Object value) {
            data[(row * columnCount) + columnIndex] = value;
            return this;
        }
    }

    // ---- AbstractCursor implementation ----------------------------------

    @Override public int getCount() { return rowCount; }
    @Override public String[] getColumnNames() { return columnNames; }

    @Override public String getString(int column) {
        Object value = get(column);
        if (value == null) return null;
        return value.toString();
    }
    @Override public short getShort(int column) {
        Object value = get(column);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).shortValue();
        return Short.parseShort(value.toString());
    }
    @Override public int getInt(int column) {
        Object value = get(column);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }
    @Override public long getLong(int column) {
        Object value = get(column);
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }
    @Override public float getFloat(int column) {
        Object value = get(column);
        if (value == null) return 0.0f;
        if (value instanceof Number) return ((Number) value).floatValue();
        return Float.parseFloat(value.toString());
    }
    @Override public double getDouble(int column) {
        Object value = get(column);
        if (value == null) return 0.0d;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }
    /** A bare cast, exactly as AOSP writes it (:301-305): reading a non-blob
     *  column as a blob is a ClassCastException, not null. */
    @Override public byte[] getBlob(int column) {
        Object value = get(column);
        return (byte[]) value;
    }
    @Override public int getType(int column) {
        return DatabaseUtils.getTypeOfObject(get(column));
    }
    @Override public boolean isNull(int column) { return get(column) == null; }
}
