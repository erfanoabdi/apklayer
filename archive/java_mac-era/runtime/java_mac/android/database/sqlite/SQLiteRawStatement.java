// android.database.sqlite.SQLiteRawStatement -- a cursor-less handle on a
// prepared sqlite3_stmt, added in API 35 and gated upstream by
// @FlaggedApi("android.database.sqlite.sqlite_apis_35"). AOSP:
// frameworks/base/core/java/android/database/sqlite/SQLiteRawStatement.java
//
// This is a REAL implementation on the same libsqlite3 that backs the rest of
// android.database.sqlite (com.macapk.loader.NativeSqlite ->
// runtime/src/native_sqlite.cpp). It is not a stub: bind, step, reset, the
// column accessors and close all drive the actual engine, because a database
// API that silently no-ops corrupts data rather than failing.
//
// THREE PLACES WHERE WE DERIVE WHAT AOSP CALLS DIRECTLY, and why. Our JNI
// surface does not export sqlite3_column_bytes, sqlite3_bind_parameter_count,
// sqlite3_bind_parameter_index or sqlite3_bind_parameter_name. Each is
// answered below in a way that is still SQLITE's answer, not a guess:
//
//   1. getColumnLength / readColumnBlob -- computed from the column value
//      itself. sqlite3_column_bytes is defined as "the size in bytes of the
//      value AFTER conversion to BLOB/TEXT", which is exactly the length of
//      what sqlite3_column_blob / sqlite3_column_text hand back. Same number,
//      one extra copy.
//   2. getParameterCount -- PROBED FROM SQLITE. sqlite3_bind_* returns
//      SQLITE_RANGE for an index outside 1..count, so binding NULL at
//      ascending indices until the engine refuses gives the engine's own
//      count. Done once in the constructor, before the caller can bind
//      anything, and followed by sqlite3_clear_bindings, so there is no
//      observable side effect. A statement's parameter count is immutable, so
//      caching it is exact.
//   3. getParameterName / getParameterIndex -- the ONLY place we mirror
//      SQLite's lexer instead of calling it, because a name cannot be probed.
//      The scanner below implements SQLite's documented parameter syntax
//      (?, ?NNN, :AAA, @AAA, $AAA) while skipping string literals, quoted
//      identifiers, blobs and both comment forms. It is then CROSS-CHECKED
//      against the probed count from (2): if the scan disagrees with what the
//      engine says, the parse is not trusted and these two methods answer
//      "unknown" (0 / null) rather than a possibly-wrong index. Returning a
//      wrong index would bind the wrong parameter, which is the one failure
//      mode here that could corrupt data; "unknown" is a documented answer
//      (the API declares getParameterName @Nullable, and AOSP returns null
//      for an anonymous '?').
package android.database.sqlite;

import com.macapk.loader.NativeSqlite;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SQLiteRawStatement implements Closeable {

    /** sqlite3_column_type() == SQLITE_INTEGER. */
    public static final int SQLITE_DATA_TYPE_INTEGER = 1;
    /** sqlite3_column_type() == SQLITE_FLOAT. */
    public static final int SQLITE_DATA_TYPE_FLOAT = 2;
    /** sqlite3_column_type() == SQLITE_TEXT. */
    public static final int SQLITE_DATA_TYPE_TEXT = 3;
    /** sqlite3_column_type() == SQLITE_BLOB. */
    public static final int SQLITE_DATA_TYPE_BLOB = 4;
    /** sqlite3_column_type() == SQLITE_NULL. */
    public static final int SQLITE_DATA_TYPE_NULL = 5;

    /** sqlite3.h: the 1-based bind index was out of range. */
    private static final int SQLITE_RANGE = 25;
    /** sqlite3.h SQLITE_BUSY / SQLITE_LOCKED, which step() maps to exceptions. */
    private static final int SQLITE_BUSY = 5;
    private static final int SQLITE_LOCKED = 6;

    private final SQLiteDatabase mDatabase;
    private final String mSql;
    private final long mStatement;
    private final int mParameterCount;
    /** Parameter names by 1-based index, or null when the SQL scan was not
     *  corroborated by the engine's own parameter count. */
    private final String[] mParameterNames;

    /**
     * The thread that created this object, exactly as AOSP: the statement is
     * bound to the connection its transaction holds, so touching it from
     * another thread is a bug. Set to null by close(), which is how a
     * use-after-close is told apart from a foreign-thread call.
     */
    private Thread mThread;

    SQLiteRawStatement(SQLiteDatabase db, String sql) {
        mThread = Thread.currentThread();
        mDatabase = db;
        mSql = sql;
        // AOSP: mSession.throwIfNoTransaction(). The object's lifetime is
        // bounded by the enclosing transaction, so there must be one.
        if (!db.inTransaction()) {
            throw new IllegalStateException("no transaction is active");
        }
        final long dbHandle = db.macapkDbHandle();
        if (dbHandle == 0) {
            throw new IllegalStateException("attempt to re-open an already-closed object: " + db);
        }
        final long stmt = NativeSqlite.nativePrepare(dbHandle, sql);
        if (stmt == 0) {
            throw new SQLiteException("error preparing statement: "
                    + NativeSqlite.nativeLastExecError() + " [" + sql + "]");
        }
        mStatement = stmt;
        mParameterCount = probeParameterCount(stmt);
        mParameterNames = scanParameterNames(sql, mParameterCount);
    }

    /**
     * Ask SQLite how many bind parameters the statement has, by binding NULL
     * at ascending indices until it answers SQLITE_RANGE. See the class header
     * for why this is the engine's answer rather than an estimate.
     *
     * The upper bound mirrors SQLITE_MAX_VARIABLE_NUMBER's default ceiling so
     * a pathological statement cannot spin; a real statement never approaches
     * it.
     */
    private static int probeParameterCount(long stmt) {
        int n = 0;
        while (n < 32766) {
            if (NativeSqlite.nativeBindNull(stmt, n + 1) == SQLITE_RANGE) {
                break;
            }
            n++;
        }
        NativeSqlite.nativeClearBindings(stmt);
        return n;
    }

    /**
     * SQLite's parameter forms, per its own syntax documentation:
     *   ?          anonymous, takes the next free index
     *   ?NNN       explicit index NNN
     *   :AAAA      named
     *   @AAAA      named
     *   $AAAA      named (Tcl syntax; the name may contain '::' and a
     *              parenthesised suffix, which we consume as part of the name)
     * A repeated name reuses the index of its first occurrence.
     *
     * Returns a 1-based-indexed array (slot 0 unused), or null when the scan
     * does not agree with `expectedCount` -- see the class header.
     */
    private static String[] scanParameterNames(String sql, int expectedCount) {
        if (expectedCount <= 0) {
            return null;
        }
        final List<String> byIndex = new ArrayList<>();   // 0-based; index i -> parameter i+1
        int next = 0;                                     // highest index assigned so far
        final int n = sql.length();
        int i = 0;
        try {
            while (i < n) {
                final char c = sql.charAt(i);
                // ---- skip the things a parameter sigil can hide inside ----
                if (c == '\'' || c == '"' || c == '`') {
                    // String literal or quoted identifier; '' / "" / `` escapes.
                    final char q = c;
                    i++;
                    while (i < n) {
                        if (sql.charAt(i) == q) {
                            if (i + 1 < n && sql.charAt(i + 1) == q) { i += 2; continue; }
                            i++;
                            break;
                        }
                        i++;
                    }
                    continue;
                }
                if (c == '[') {                       // MS-style quoted identifier
                    while (i < n && sql.charAt(i) != ']') i++;
                    i++;
                    continue;
                }
                if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                    while (i < n && sql.charAt(i) != '\n') i++;
                    continue;
                }
                if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < n && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) i++;
                    i += 2;
                    continue;
                }
                if ((c == 'x' || c == 'X') && i + 1 < n && sql.charAt(i + 1) == '\'') {
                    i++;                              // blob literal: fall through to the quote
                    continue;
                }
                // ---- the parameter sigils ----
                if (c == '?') {
                    i++;
                    int start = i;
                    while (i < n && Character.isDigit(sql.charAt(i))) i++;
                    if (i > start) {
                        int explicit = Integer.parseInt(sql.substring(start, i));
                        while (byIndex.size() < explicit) byIndex.add(null);
                        if (explicit > next) next = explicit;
                    } else {
                        next++;
                        while (byIndex.size() < next) byIndex.add(null);
                    }
                    continue;
                }
                if (c == ':' || c == '@' || c == '$') {
                    int start = i;
                    i++;
                    while (i < n && isParamNameChar(sql.charAt(i))) i++;
                    if (i == start + 1) {
                        // A bare sigil is not a parameter (e.g. the '::' cast
                        // form, or '@' inside something else).
                        continue;
                    }
                    final String name = sql.substring(start, i);
                    int existing = byIndex.indexOf(name);
                    if (existing >= 0) {
                        continue;                     // repeated name reuses its index
                    }
                    next++;
                    while (byIndex.size() < next - 1) byIndex.add(null);
                    byIndex.add(name);
                    continue;
                }
                i++;
            }
        } catch (RuntimeException e) {
            return null;                              // unparseable -> "unknown"
        }
        if (byIndex.size() != expectedCount) {
            // SQLite disagrees with the scan: do not trust it.
            return null;
        }
        final String[] out = new String[expectedCount + 1];
        for (int k = 0; k < expectedCount; k++) {
            out[k + 1] = byIndex.get(k);
        }
        return out;
    }

    private static boolean isParamNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '$';
    }

    // ---------------------------------------------------------------- state

    private void throwIfInvalid() {
        if (mThread != Thread.currentThread()) {
            if (mThread == null) {
                throw new IllegalStateException("method called on a closed statement");
            }
            throw new IllegalStateException("method called on a foreign thread: " + mThread);
        }
    }

    private static void throwIfInvalidBounds(int arrayLength, int offset, int length) {
        if (arrayLength < 0) {
            throw new IllegalArgumentException("invalid array length " + arrayLength);
        }
        if (offset < 0 || offset >= arrayLength) {
            throw new IllegalArgumentException("invalid offset " + offset
                    + " for array length " + arrayLength);
        }
        if (length <= 0 || ((arrayLength - offset) < length)) {
            throw new IllegalArgumentException("invalid offset " + offset
                    + " and length " + length
                    + " for array length " + arrayLength);
        }
    }

    public boolean isOpen() {
        return mThread != null;
    }

    @Override
    public void close() {
        if (mThread != null) {
            throwIfInvalid();
            NativeSqlite.nativeFinalize(mStatement);
            mThread = null;
        }
    }

    @Override
    public String toString() {
        if (isOpen()) {
            return "SQLiteRawStatement: " + mSql;
        }
        return "SQLiteRawStatement: (closed) " + mSql;
    }

    // ---------------------------------------------------------------- stepping

    public boolean step() {
        throwIfInvalid();
        final int err = NativeSqlite.nativeStep(mStatement);
        switch (err) {
            case NativeSqlite.SQLITE_ROW:
                return true;
            case NativeSqlite.SQLITE_DONE:
                return false;
            case SQLITE_BUSY:
                throw new SQLiteDatabaseLockedException("database " + mDatabase + " busy");
            case SQLITE_LOCKED:
                throw new SQLiteDatabaseLockedException("database " + mDatabase + " locked");
            default:
                throw new SQLiteException("error " + err + ": "
                        + NativeSqlite.nativeErrmsg(mDatabase.macapkDbHandle())
                        + " [" + mSql + "]");
        }
    }

    public void reset() {
        throwIfInvalid();
        NativeSqlite.nativeReset(mStatement);
    }

    public void clearBindings() {
        throwIfInvalid();
        NativeSqlite.nativeClearBindings(mStatement);
    }

    // ---------------------------------------------------------------- parameters

    public int getParameterCount() {
        throwIfInvalid();
        return mParameterCount;
    }

    /**
     * The 1-based index of the named parameter, or 0 if there is no such
     * parameter. See the class header for why this can also answer 0 when the
     * SQL scan could not be corroborated.
     */
    public int getParameterIndex(String name) {
        Objects.requireNonNull(name);
        throwIfInvalid();
        if (mParameterNames == null) {
            return 0;
        }
        for (int i = 1; i < mParameterNames.length; i++) {
            if (name.equals(mParameterNames[i])) {
                return i;
            }
        }
        return 0;
    }

    /**
     * The name of the parameter at the given 1-based index, or null if the
     * parameter is anonymous ('?') -- which is also what AOSP returns, since
     * sqlite3_bind_parameter_name yields NULL for those.
     */
    public String getParameterName(int parameterIndex) {
        throwIfInvalid();
        if (mParameterNames == null
                || parameterIndex < 1 || parameterIndex >= mParameterNames.length) {
            return null;
        }
        return mParameterNames[parameterIndex];
    }

    public void bindNull(int parameterIndex) {
        throwIfInvalid();
        checkBind(NativeSqlite.nativeBindNull(mStatement, parameterIndex), parameterIndex);
    }

    public void bindInt(int parameterIndex, int value) {
        // SQLite has one integer type; AOSP's bindInt is bindLong with a
        // narrower Java parameter, not a different storage class.
        bindLong(parameterIndex, value);
    }

    public void bindLong(int parameterIndex, long value) {
        throwIfInvalid();
        checkBind(NativeSqlite.nativeBindLong(mStatement, parameterIndex, value), parameterIndex);
    }

    public void bindDouble(int parameterIndex, double value) {
        throwIfInvalid();
        checkBind(NativeSqlite.nativeBindDouble(mStatement, parameterIndex, value), parameterIndex);
    }

    public void bindText(int parameterIndex, String value) {
        Objects.requireNonNull(value);
        throwIfInvalid();
        checkBind(NativeSqlite.nativeBindString(mStatement, parameterIndex, value), parameterIndex);
    }

    public void bindBlob(int parameterIndex, byte[] value) {
        Objects.requireNonNull(value);
        throwIfInvalid();
        checkBind(NativeSqlite.nativeBindBlob(mStatement, parameterIndex, value), parameterIndex);
    }

    public void bindBlob(int parameterIndex, byte[] value, int offset, int length) {
        Objects.requireNonNull(value);
        throwIfInvalid();
        throwIfInvalidBounds(value.length, offset, length);
        final byte[] slice = new byte[length];
        System.arraycopy(value, offset, slice, 0, length);
        checkBind(NativeSqlite.nativeBindBlob(mStatement, parameterIndex, slice), parameterIndex);
    }

    private void checkBind(int rc, int parameterIndex) {
        if (rc == NativeSqlite.SQLITE_OK) {
            return;
        }
        if (rc == SQLITE_RANGE) {
            throw new IllegalArgumentException("invalid parameter index " + parameterIndex
                    + " (statement has " + mParameterCount + ")");
        }
        throw new SQLiteException("error " + rc + " binding parameter " + parameterIndex
                + ": " + NativeSqlite.nativeErrmsg(mDatabase.macapkDbHandle()));
    }

    // ---------------------------------------------------------------- results

    public int getResultColumnCount() {
        throwIfInvalid();
        return NativeSqlite.nativeColumnCount(mStatement);
    }

    public int getColumnType(int columnIndex) {
        throwIfInvalid();
        return NativeSqlite.nativeColumnType(mStatement, columnIndex);
    }

    public String getColumnName(int columnIndex) {
        throwIfInvalid();
        return NativeSqlite.nativeColumnName(mStatement, columnIndex);
    }

    /**
     * sqlite3_column_bytes: the size in bytes of the column value AFTER
     * conversion to BLOB or UTF-8 TEXT. NULL is 0. See the class header for
     * why this is derived from the value rather than read directly.
     */
    public int getColumnLength(int columnIndex) {
        throwIfInvalid();
        final int type = NativeSqlite.nativeColumnType(mStatement, columnIndex);
        if (type == NativeSqlite.SQLITE_NULL) {
            return 0;
        }
        if (type == NativeSqlite.SQLITE_BLOB) {
            final byte[] b = NativeSqlite.nativeColumnBlob(mStatement, columnIndex);
            return b == null ? 0 : b.length;
        }
        // INTEGER / FLOAT / TEXT all convert to UTF-8 text, and it is the
        // BYTE length that sqlite3_column_bytes reports, not the char count.
        final String s = NativeSqlite.nativeColumnString(mStatement, columnIndex);
        return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }

    public long getColumnLong(int columnIndex) {
        throwIfInvalid();
        return NativeSqlite.nativeColumnLong(mStatement, columnIndex);
    }

    public int getColumnInt(int columnIndex) {
        throwIfInvalid();
        return (int) NativeSqlite.nativeColumnLong(mStatement, columnIndex);
    }

    public double getColumnDouble(int columnIndex) {
        throwIfInvalid();
        return NativeSqlite.nativeColumnDouble(mStatement, columnIndex);
    }

    public String getColumnText(int columnIndex) {
        throwIfInvalid();
        final String s = NativeSqlite.nativeColumnString(mStatement, columnIndex);
        // AOSP declares this @NonNull; sqlite3_column_text on a NULL column
        // yields an empty string through the same path.
        return s == null ? "" : s;
    }

    public byte[] getColumnBlob(int columnIndex) {
        throwIfInvalid();
        return NativeSqlite.nativeColumnBlob(mStatement, columnIndex);
    }

    /**
     * Copy up to {@code length} bytes of the column's blob value into
     * {@code buffer} at {@code offset}, starting {@code srcOffset} bytes into
     * the value. Returns the number of bytes copied, which may be zero.
     *
     * AOSP's parameter order is (columnIndex, buffer, offset, length,
     * srcOffset) -- note that srcOffset comes LAST, which is easy to get
     * backwards; it matches core/api/current.txt's
     * "readColumnBlob(int, byte[], int, int, int)".
     */
    public int readColumnBlob(int columnIndex, byte[] buffer, int offset, int length,
            int srcOffset) {
        Objects.requireNonNull(buffer);
        throwIfInvalid();
        throwIfInvalidBounds(buffer.length, offset, length);
        final byte[] value = NativeSqlite.nativeColumnBlob(mStatement, columnIndex);
        if (value == null || srcOffset < 0 || srcOffset >= value.length) {
            return 0;
        }
        final int n = Math.min(length, value.length - srcOffset);
        System.arraycopy(value, srcOffset, buffer, offset, n);
        return n;
    }
}
