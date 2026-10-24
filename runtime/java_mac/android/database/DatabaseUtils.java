// android.database.DatabaseUtils -- static SQL/query helpers. Mirrors AOSP
// frameworks/base/core/java/android/database/DatabaseUtils.java for the
// commonly-used subset. ExoPlayer's SimpleCache index (and many AndroidX/
// Room helpers) call DatabaseUtils.queryNumEntries / longForQuery /
// sqlEscapeString; the class was missing entirely, NoClassDefFoundError'ing
// those code paths. Backed by Mac.apk's real libsqlite3-based
// android.database.sqlite.* implementation.
package android.database;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class DatabaseUtils {
    private static final String TAG = "DatabaseUtils";

    /** AOSP's DatabaseUtils has the IMPLICIT public default constructor --
     *  core/api/current.txt carries "ctor public DatabaseUtils();". A private
     *  one here makes `new DatabaseUtils()` fail where a device succeeds. */
    public DatabaseUtils() {}

    /** Wrap a string in single quotes, doubling embedded quotes (SQL escape). */
    public static String sqlEscapeString(String value) {
        StringBuilder escaper = new StringBuilder();
        appendEscapedSQLString(escaper, value);
        return escaper.toString();
    }

    /** AOSP appendValueToSql (DatabaseUtils.java:542-556): render a Java value
     *  as an SQL literal. Booleans become the bare integers 1/0 (SQLite has no
     *  boolean type); everything else that is not null goes through
     *  appendEscapedSQLString on its toString(), which means a byte[] is
     *  rendered as its identity toString ("'[B@1f2e3d'") rather than as a hex
     *  blob literal. That is a genuine AOSP wart and it is MIRRORED, not
     *  corrected: it is only reachable from ContentProvider code that builds
     *  SQL by hand, and any caller that "works" today does so because of this
     *  exact rendering. */
    public static final void appendValueToSql(StringBuilder sql, Object value) {
        if (value == null) {
            sql.append("NULL");
        } else if (value instanceof Boolean) {
            sql.append(((Boolean) value).booleanValue() ? '1' : '0');
        } else {
            appendEscapedSQLString(sql, value.toString());
        }
    }

    public static void appendEscapedSQLString(StringBuilder sb, String sqlString) {
        sb.append('\'');
        if (sqlString.indexOf('\'') != -1) {
            int length = sqlString.length();
            for (int i = 0; i < length; i++) {
                char c = sqlString.charAt(i);
                if (c == '\'') sb.append('\'');
                sb.append(c);
            }
        } else {
            sb.append(sqlString);
        }
        sb.append('\'');
    }

    public static long longForQuery(SQLiteDatabase db, String query, String[] selectionArgs) {
        SQLiteStatement prog = db.compileStatement(query);
        try {
            return longForQuery(prog, selectionArgs);
        } finally {
            prog.close();
        }
    }

    public static long longForQuery(SQLiteStatement prog, String[] selectionArgs) {
        if (selectionArgs != null) {
            prog.bindAllArgsAsStrings(selectionArgs);
        }
        return prog.simpleQueryForLong();
    }

    public static long queryNumEntries(SQLiteDatabase db, String table) {
        return queryNumEntries(db, table, null, null);
    }

    public static long queryNumEntries(SQLiteDatabase db, String table, String selection) {
        return queryNumEntries(db, table, selection, null);
    }

    public static long queryNumEntries(SQLiteDatabase db, String table, String selection,
                                       String[] selectionArgs) {
        String s = (selection != null) ? " where " + selection : "";
        return longForQuery(db, "select count(*) from " + table + s, selectionArgs);
    }

    // v1.0.426 Tier-2: the string-query + ContentValues-copy helpers AndroidX /
    // ContentProvider code reaches for.
    public static String stringForQuery(SQLiteDatabase db, String query, String[] selectionArgs) {
        SQLiteStatement prog = db.compileStatement(query);
        try { return stringForQuery(prog, selectionArgs); }
        finally { prog.close(); }
    }
    public static String stringForQuery(SQLiteStatement prog, String[] selectionArgs) {
        if (selectionArgs != null) prog.bindAllArgsAsStrings(selectionArgs);
        return prog.simpleQueryForString();
    }

    /** AOSP cursorRowToContentValues: copy the cursor's current row into a
     *  ContentValues keyed by column name (all values stored as String, matching
     *  the default AOSP path when the column type isn't a BLOB). */
    public static void cursorRowToContentValues(android.database.Cursor cursor,
                                                android.content.ContentValues values) {
        if (cursor == null || values == null) return;
        String[] columns = cursor.getColumnNames();
        for (int i = 0; i < columns.length; i++) {
            if (cursor.getType(i) == android.database.Cursor.FIELD_TYPE_BLOB) {
                values.put(columns[i], cursor.getBlob(i));
            } else {
                values.put(columns[i], cursor.getString(i));
            }
        }
    }
    public static void cursorStringToContentValues(android.database.Cursor cursor, String field,
                                                   android.content.ContentValues values) {
        cursorStringToContentValues(cursor, field, values, field);
    }
    public static void cursorStringToContentValues(android.database.Cursor cursor, String field,
                                                   android.content.ContentValues values, String key) {
        if (cursor == null || values == null) return;
        int idx = cursor.getColumnIndex(field);
        values.put(key, idx < 0 ? null : cursor.getString(idx));
    }

    /** AOSP cursor field-type constants. */
    public static final int FIELD_TYPE_NULL = 0;
    public static final int FIELD_TYPE_INTEGER = 1;
    public static final int FIELD_TYPE_FLOAT = 2;
    public static final int FIELD_TYPE_STRING = 3;
    public static final int FIELD_TYPE_BLOB = 4;

    // v1.0.426 Tier-2: SQL statement classification. androidx.sqlite
    // FrameworkSQLiteDatabase / Room calls getSqlStatementType to route a query
    // as read vs write (transaction handling); a missing method aborted that path.
    public static final int STATEMENT_SELECT = 1;
    public static final int STATEMENT_UPDATE = 2;
    public static final int STATEMENT_ATTACH = 3;
    public static final int STATEMENT_BEGIN  = 4;
    public static final int STATEMENT_COMMIT = 5;
    public static final int STATEMENT_ABORT  = 6;
    public static final int STATEMENT_PRAGMA = 7;
    public static final int STATEMENT_DDL    = 8;
    public static final int STATEMENT_UNPREPARED = 9;
    public static final int STATEMENT_OTHER  = 99;

    /** AOSP getSqlStatementType (DatabaseUtils.java:1566-1608). Three details
     *  that a paraphrase loses, all of which change an EXECUTION PATH because
     *  SQLiteDatabase.execSQL routes on this value:
     *   - null is NOT defended: AOSP calls sql.trim() straight away, so a null
     *     statement is a NullPointerException at the caller, not a silent
     *     STATEMENT_OTHER that then gets handed to the prepare.
     *   - "ROLLBACK TO <savepoint>" degrades to STATEMENT_OTHER, because a
     *     savepoint rollback is not a transaction abort. Only a bare ROLLBACK
     *     is STATEMENT_ABORT.
     *   - the upper-casing is Locale.ROOT. In a Turkish locale
     *     "insert".toUpperCase() is "İNSERT", which matches no prefix and
     *     silently reclassifies every INSERT in the app as STATEMENT_OTHER. */
    public static int getSqlStatementType(String sql) {
        sql = sql.trim();
        if (sql.length() < 3) return STATEMENT_OTHER;
        String prefix = sql.substring(0, 3).toUpperCase(java.util.Locale.ROOT);
        switch (prefix) {
            case "SEL": return STATEMENT_SELECT;
            case "INS": case "UPD": case "REP": case "DEL": return STATEMENT_UPDATE;
            case "ATT": return STATEMENT_ATTACH;
            case "COM": case "END": return STATEMENT_COMMIT;
            case "ROL":
                return sql.toUpperCase(java.util.Locale.ROOT).contains(" TO ")
                        ? STATEMENT_OTHER : STATEMENT_ABORT;
            case "BEG": return STATEMENT_BEGIN;
            case "PRA": return STATEMENT_PRAGMA;
            case "CRE": case "DRO": case "ALT": return STATEMENT_DDL;
            case "ANA": case "DET": return STATEMENT_UNPREPARED;
            default:    return STATEMENT_OTHER;
        }
    }

    /** AOSP getTypeOfObject (DatabaseUtils.java:1516-1530) -- the mapping
     *  MatrixCursor.getType is defined in terms of. Note that a Boolean lands
     *  in the final else and is therefore FIELD_TYPE_STRING, not
     *  FIELD_TYPE_INTEGER: only Long/Integer/Short/Byte are integers here. */
    public static int getTypeOfObject(Object obj) {
        if (obj == null) return Cursor.FIELD_TYPE_NULL;
        if (obj instanceof byte[]) return Cursor.FIELD_TYPE_BLOB;
        if (obj instanceof Float || obj instanceof Double) return Cursor.FIELD_TYPE_FLOAT;
        if (obj instanceof Long || obj instanceof Integer
                || obj instanceof Short || obj instanceof Byte) {
            return Cursor.FIELD_TYPE_INTEGER;
        }
        return Cursor.FIELD_TYPE_STRING;
    }

    /** AOSP bindObjectToProgram: bind a Java value to a 1-based statement slot,
     *  dispatching by runtime type (SQLiteQueryBuilder / ContentProvider helpers
     *  use this). */
    public static void bindObjectToProgram(android.database.sqlite.SQLiteProgram prog,
                                           int index, Object value) {
        if (prog == null) return;
        if (value == null)              prog.bindNull(index);
        else if (value instanceof Double || value instanceof Float)
                                        prog.bindDouble(index, ((Number) value).doubleValue());
        else if (value instanceof Number)
                                        prog.bindLong(index, ((Number) value).longValue());
        else if (value instanceof byte[])
                                        prog.bindBlob(index, (byte[]) value);
        else if (value instanceof Boolean)
                                        prog.bindLong(index, ((Boolean) value) ? 1 : 0);
        else                            prog.bindString(index, value.toString());
    }

    /** AOSP cursorFillWindow: read rows from the cursor (starting at
     *  position) into the window until the window is full or the cursor
     *  is exhausted. Each cell is stored by its column type. The cursor
     *  position is left at the last row written. Used by
     *  CrossProcessCursorWrapper for cursors that are not themselves
     *  CrossProcessCursors. */
    public static void cursorFillWindow(android.database.Cursor cursor, int position,
                                        CursorWindow window) {
        if (cursor == null || window == null) return;
        final int oldPos = cursor.getPosition();
        final int numColumns = cursor.getColumnCount();
        window.clear();
        window.setStartPosition(position);
        window.setNumColumns(numColumns);
        if (cursor.moveToPosition(position)) {
            do {
                if (!window.allocRow()) break;
                int row = cursor.getPosition();
                boolean ok = true;
                for (int i = 0; i < numColumns; i++) {
                    int type = cursor.getType(i);
                    switch (type) {
                        case FIELD_TYPE_NULL:
                            ok = window.putNull(row, i); break;
                        case FIELD_TYPE_INTEGER:
                            ok = window.putLong(cursor.getLong(i), row, i); break;
                        case FIELD_TYPE_FLOAT:
                            ok = window.putDouble(cursor.getDouble(i), row, i); break;
                        case FIELD_TYPE_BLOB:
                            ok = window.putBlob(cursor.getBlob(i), row, i); break;
                        default:
                            ok = window.putString(cursor.getString(i), row, i); break;
                    }
                    if (!ok) break;
                }
                if (!ok) { window.freeLastRow(); break; }
            } while (cursor.moveToNext());
        }
        cursor.moveToPosition(oldPos);
    }

    // ---- selection / where composition ---------------------------

    /** AOSP concatenateWhere (DatabaseUtils.java:1620-1630): combine two WHERE
     *  clauses with AND, parenthesizing each. When `a` is empty it returns `b`
     *  VERBATIM -- including when b is null. Substituting "" for that null (as
     *  this did) is not a harmless tidy-up: the result is fed straight to
     *  SQLiteDatabase.query as the selection, and "" and null are handled by
     *  DIFFERENT branches there (TextUtils.isEmpty omits the WHERE for both,
     *  but a caller that null-checks the returned clause before appending its
     *  own sees a non-null empty string and appends "WHERE  AND (...)"). */
    public static String concatenateWhere(String a, String b) {
        if (a == null || a.length() == 0) return b;
        if (b == null || b.length() == 0) return a;
        return "(" + a + ") AND (" + b + ")";
    }

    /** AOSP appendSelectionArgs: concatenate two selection-arg arrays
     *  (used when concatenateWhere merges two clauses with their args).
     *  Returns the non-null array when one side is null/empty. */
    public static String[] appendSelectionArgs(String[] originalValues, String[] newValues) {
        if (originalValues == null || originalValues.length == 0) return newValues;
        if (newValues == null || newValues.length == 0) return originalValues;
        String[] result = new String[originalValues.length + newValues.length];
        System.arraycopy(originalValues, 0, result, 0, originalValues.length);
        System.arraycopy(newValues, 0, result, originalValues.length, newValues.length);
        return result;
    }

    // ---- typed cursor -> ContentValues copies --------------------
    // AOSP variants that read a single column AS a specific type and
    // put it under the (optionally renamed) key. The no-key overload
    // reuses the field name as the key.

    public static void cursorIntToContentValues(android.database.Cursor cursor, String field,
                                                android.content.ContentValues values) {
        cursorIntToContentValues(cursor, field, values, field);
    }
    public static void cursorIntToContentValues(android.database.Cursor cursor, String field,
                                                android.content.ContentValues values, String key) {
        if (cursor == null || values == null) return;
        int colIndex = cursor.getColumnIndex(field);
        if (colIndex < 0 || cursor.isNull(colIndex)) values.put(key, (Integer) null);
        else values.put(key, cursor.getInt(colIndex));
    }

    public static void cursorLongToContentValues(android.database.Cursor cursor, String field,
                                                 android.content.ContentValues values) {
        cursorLongToContentValues(cursor, field, values, field);
    }
    public static void cursorLongToContentValues(android.database.Cursor cursor, String field,
                                                 android.content.ContentValues values, String key) {
        if (cursor == null || values == null) return;
        int colIndex = cursor.getColumnIndex(field);
        if (colIndex < 0 || cursor.isNull(colIndex)) values.put(key, (Long) null);
        else values.put(key, cursor.getLong(colIndex));
    }

    public static void cursorShortToContentValues(android.database.Cursor cursor, String field,
                                                  android.content.ContentValues values) {
        cursorShortToContentValues(cursor, field, values, field);
    }
    public static void cursorShortToContentValues(android.database.Cursor cursor, String field,
                                                  android.content.ContentValues values, String key) {
        if (cursor == null || values == null) return;
        int colIndex = cursor.getColumnIndex(field);
        if (colIndex < 0 || cursor.isNull(colIndex)) values.put(key, (Short) null);
        else values.put(key, cursor.getShort(colIndex));
    }

    public static void cursorFloatToContentValues(android.database.Cursor cursor, String field,
                                                  android.content.ContentValues values) {
        cursorFloatToContentValues(cursor, field, values, field);
    }
    public static void cursorFloatToContentValues(android.database.Cursor cursor, String field,
                                                  android.content.ContentValues values, String key) {
        if (cursor == null || values == null) return;
        int colIndex = cursor.getColumnIndex(field);
        if (colIndex < 0 || cursor.isNull(colIndex)) values.put(key, (Float) null);
        else values.put(key, cursor.getFloat(colIndex));
    }

    public static void cursorDoubleToContentValues(android.database.Cursor cursor, String field,
                                                   android.content.ContentValues values) {
        cursorDoubleToContentValues(cursor, field, values, field);
    }
    public static void cursorDoubleToContentValues(android.database.Cursor cursor, String field,
                                                   android.content.ContentValues values, String key) {
        if (cursor == null || values == null) return;
        int colIndex = cursor.getColumnIndex(field);
        if (colIndex < 0 || cursor.isNull(colIndex)) values.put(key, (Double) null);
        else values.put(key, cursor.getDouble(colIndex));
    }

    /** AOSP cursorDoubleToCursorValues (DatabaseUtils.java:875-878). The name is
     *  a decade-old typo for ...ToContentValues that AOSP never corrected, so
     *  it is part of the API surface: app bytecode calls it by that exact name
     *  and a NoSuchMethodError kills the whole containing method. */
    public static void cursorDoubleToCursorValues(android.database.Cursor cursor, String field,
                                                  android.content.ContentValues values) {
        cursorDoubleToContentValues(cursor, field, values, field);
    }

    // ---- "...IfPresent" copies -----------------------------------
    // AOSP DatabaseUtils.java:1078-1173. These differ from the plain copies
    // above in BOTH directions: an absent column or a NULL value writes
    // NOTHING at all (the plain form writes an explicit null), and the key is
    // always the column name (there is no rename overload). ContentProvider
    // code uses them to build a sparse ContentValues for a partial UPDATE, so
    // writing a null would turn "leave this column alone" into "set it to
    // NULL".

    public static void cursorStringToContentValuesIfPresent(android.database.Cursor cursor,
            android.content.ContentValues values, String column) {
        final int index = cursor.getColumnIndex(column);
        if (index != -1 && !cursor.isNull(index)) {
            values.put(column, cursor.getString(index));
        }
    }
    public static void cursorLongToContentValuesIfPresent(android.database.Cursor cursor,
            android.content.ContentValues values, String column) {
        final int index = cursor.getColumnIndex(column);
        if (index != -1 && !cursor.isNull(index)) {
            values.put(column, cursor.getLong(index));
        }
    }
    public static void cursorShortToContentValuesIfPresent(android.database.Cursor cursor,
            android.content.ContentValues values, String column) {
        final int index = cursor.getColumnIndex(column);
        if (index != -1 && !cursor.isNull(index)) {
            values.put(column, cursor.getShort(index));
        }
    }
    public static void cursorIntToContentValuesIfPresent(android.database.Cursor cursor,
            android.content.ContentValues values, String column) {
        final int index = cursor.getColumnIndex(column);
        if (index != -1 && !cursor.isNull(index)) {
            values.put(column, cursor.getInt(index));
        }
    }
    public static void cursorFloatToContentValuesIfPresent(android.database.Cursor cursor,
            android.content.ContentValues values, String column) {
        final int index = cursor.getColumnIndex(column);
        if (index != -1 && !cursor.isNull(index)) {
            values.put(column, cursor.getFloat(index));
        }
    }
    public static void cursorDoubleToContentValuesIfPresent(android.database.Cursor cursor,
            android.content.ContentValues values, String column) {
        final int index = cursor.getColumnIndex(column);
        if (index != -1 && !cursor.isNull(index)) {
            values.put(column, cursor.getDouble(index));
        }
    }

    // ---- collation keys ------------------------------------------
    // AOSP derives a binary collation key from the platform Collator
    // (en_US, PRIMARY strength) so SQL can sort accent/case-insensitively.
    // We mirror that with java.text.Collator.

    // AOSP's PUBLIC getCollationKey returns a String, not the raw bytes: it is
    //   new String(getCollationKeyInBytes(name), 0, getKeyLen(arr), "ISO8859_1")
    // -- i.e. the same bytes reinterpreted latin-1 so they can go in a TEXT column.
    // Returning byte[] is a different descriptor and could not link.
    // frameworks/base/core/java/android/database/DatabaseUtils.java
    public static String getCollationKey(String name) {
        byte[] key = collationKeyBytes(name);
        int len = keyLen(key);
        return new String(key, 0, len, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private static byte[] collationKeyBytes(String name) {
        if (name == null) return new byte[0];
        java.text.Collator collator = java.text.Collator.getInstance();
        collator.setStrength(java.text.Collator.PRIMARY);
        return collator.getCollationKey(name).toByteArray();
    }

    /** AOSP's getKeyLen: drops the trailing null terminator the ICU key carries. */
    private static int keyLen(byte[] key) {
        int len = key.length;
        if (len > 0 && key[len - 1] == 0) len--;
        return len;
    }

    public static String getHexCollationKey(String name) {
        byte[] key = collationKeyBytes(name);
        // AOSP drops the trailing null terminator the ICU key carries.
        int len = key.length;
        if (len > 0 && key[len - 1] == 0) len--;
        char[] hex = new char[len * 2];
        final char[] DIGITS = "0123456789ABCDEF".toCharArray();
        for (int i = 0; i < len; i++) {
            int b = key[i] & 0xFF;
            hex[i * 2]     = DIGITS[b >>> 4];
            hex[i * 2 + 1] = DIGITS[b & 0x0F];
        }
        return new String(hex);
    }

    // ---- debug dumps ---------------------------------------------
    // AOSP dumpCursor / dumpCurrentRow family. Used by app code logging
    // a query result. We write to System.out (the no-stream overloads)
    // or the supplied StringBuilder / PrintStream, leaving the cursor
    // position restored.

    public static void dumpCursor(android.database.Cursor cursor) {
        dumpCursor(cursor, System.out);
    }
    public static void dumpCursor(android.database.Cursor cursor, java.io.PrintStream stream) {
        StringBuilder sb = new StringBuilder();
        dumpCursor(cursor, sb);
        stream.print(sb);
    }
    public static void dumpCursor(android.database.Cursor cursor, StringBuilder sb) {
        if (cursor == null) { sb.append(">>>>> Cursor is null\n"); return; }
        sb.append(">>>>> Dumping cursor ").append(cursor).append('\n');
        int startPos = cursor.getPosition();
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            dumpCurrentRow(cursor, sb);
        }
        cursor.moveToPosition(startPos);
    }

    public static void dumpCurrentRow(android.database.Cursor cursor) {
        dumpCurrentRow(cursor, System.out);
    }
    public static void dumpCurrentRow(android.database.Cursor cursor, java.io.PrintStream stream) {
        StringBuilder sb = new StringBuilder();
        dumpCurrentRow(cursor, sb);
        stream.print(sb);
    }
    public static void dumpCurrentRow(android.database.Cursor cursor, StringBuilder sb) {
        if (cursor == null) { sb.append("(null cursor)\n"); return; }
        String[] cols = cursor.getColumnNames();
        sb.append(cursor.getPosition()).append(" {\n");
        int length = cols.length;
        for (int i = 0; i < length; i++) {
            String value;
            try {
                value = cursor.getString(i);
            } catch (Exception e) {
                value = "<unprintable>";
            }
            sb.append("   ").append(cols[i]).append('=').append(value).append('\n');
        }
        sb.append("}\n");
    }

    public static String dumpCurrentRowToString(android.database.Cursor cursor) {
        StringBuilder sb = new StringBuilder();
        dumpCurrentRow(cursor, sb);
        return sb.toString();
    }
    public static String dumpCursorToString(android.database.Cursor cursor) {
        StringBuilder sb = new StringBuilder();
        dumpCursor(cursor, sb);
        return sb.toString();
    }

    /** AOSP createDbFromSqlStatements: execute a ';'-separated batch of
     *  DDL/DML statements against a fresh database at dbName. Used by
     *  test fixtures + some content providers seeding a db. */
    public static void createDbFromSqlStatements(android.content.Context context, String dbName,
                                                 int dbVersion, String sqlStatements) {
        // AOSP opens via Context.openOrCreateDatabase; our Context does
        // not expose that overload, so resolve the on-disk path through
        // getDatabasePath and open the file directly (same end result:
        // a db file under the app's databases dir).
        String path = context.getDatabasePath(dbName).getPath();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(path, null);
        if (sqlStatements != null) {
            for (String statement : sqlStatements.split(";\n")) {
                if (statement.trim().length() == 0) continue;
                db.execSQL(statement);
            }
        }
        db.setVersion(dbVersion);
        db.close();
    }

    /** android.database.DatabaseUtils.InsertHelper -- deprecated AOSP
     *  helper for repeated inserts into one table without rebuilding
     *  the SQL each time. Column order is discovered from
     *  PRAGMA table_info; bind(index, value) targets a 1-based column
     *  slot returned by getColumnIndex. prepareForInsert/execute let a
     *  caller bind then run; insert(ContentValues) is the convenience
     *  one-shot. Mirrors AOSP behavior (use SQLiteStatement directly in
     *  new code).
     *  Source: frameworks/base/.../database/DatabaseUtils.java InsertHelper */
    @Deprecated
    public static class InsertHelper {
        public static final int TABLE_INFO_PRAGMA_COLUMNNAME_INDEX = 1;
        public static final int TABLE_INFO_PRAGMA_DEFAULT_INDEX = 4;

        private final SQLiteDatabase mDb;
        private final String mTableName;
        private java.util.HashMap<String, Integer> mColumns;
        private String mInsertSQL;
        private SQLiteStatement mInsertStatement;
        private SQLiteStatement mReplaceStatement;
        private SQLiteStatement mPreparedStatement;

        public InsertHelper(SQLiteDatabase db, String tableName) {
            mDb = db;
            mTableName = tableName;
        }

        private void buildSQL() {
            StringBuilder sb = new StringBuilder(128);
            sb.append("INSERT INTO ").append(mTableName).append(" (");
            StringBuilder sbValues = new StringBuilder(") VALUES (");
            mColumns = new java.util.HashMap<>();
            android.database.Cursor cur = mDb.rawQuery("PRAGMA table_info(" + mTableName + ")", null);
            try {
                int i = 0;
                int columnNameColIndex = cur.getColumnIndex("name");
                if (columnNameColIndex < 0) columnNameColIndex = TABLE_INFO_PRAGMA_COLUMNNAME_INDEX;
                while (cur.moveToNext()) {
                    String columnName = cur.getString(columnNameColIndex);
                    i++;
                    mColumns.put(columnName, i);
                    sb.append("'").append(columnName).append("'");
                    sbValues.append('?');
                    if (!cur.isLast()) { sb.append(','); sbValues.append(','); }
                }
            } finally {
                cur.close();
            }
            sb.append(sbValues).append(')');
            mInsertSQL = sb.toString();
        }

        private SQLiteStatement getStatement(boolean allowReplace) {
            if (allowReplace) {
                if (mReplaceStatement == null) {
                    if (mInsertSQL == null) buildSQL();
                    String replaceSQL = "INSERT OR REPLACE" + mInsertSQL.substring(6);
                    mReplaceStatement = mDb.compileStatement(replaceSQL);
                }
                return mReplaceStatement;
            } else {
                if (mInsertStatement == null) {
                    if (mInsertSQL == null) buildSQL();
                    mInsertStatement = mDb.compileStatement(mInsertSQL);
                }
                return mInsertStatement;
            }
        }

        /** 1-based column index for the named column, or -1 if absent. */
        public int getColumnIndex(String key) {
            getStatement(false);
            Integer index = mColumns.get(key);
            return (index == null) ? -1 : index;
        }

        public void bind(int index, double value)  { mPreparedStatement.bindDouble(index, value); }
        public void bind(int index, float value)   { mPreparedStatement.bindDouble(index, value); }
        public void bind(int index, long value)    { mPreparedStatement.bindLong(index, value); }
        public void bind(int index, int value)     { mPreparedStatement.bindLong(index, value); }
        public void bind(int index, boolean value) { mPreparedStatement.bindLong(index, value ? 1 : 0); }
        public void bindNull(int index)            { mPreparedStatement.bindNull(index); }
        public void bind(int index, byte[] value) {
            if (value == null) mPreparedStatement.bindNull(index);
            else mPreparedStatement.bindBlob(index, value);
        }
        public void bind(int index, String value) {
            if (value == null) mPreparedStatement.bindNull(index);
            else mPreparedStatement.bindString(index, value);
        }

        /** One-shot insert of a ContentValues row. */
        public long insert(android.content.ContentValues values) {
            return insertInternal(values, false);
        }

        public long replace(android.content.ContentValues values) {
            return insertInternal(values, true);
        }

        private long insertInternal(android.content.ContentValues values, boolean allowReplace) {
            SQLiteStatement stmt = getStatement(allowReplace);
            stmt.clearBindings();
            for (java.util.Map.Entry<String, Object> e : values.valueSet()) {
                int i = getColumnIndex(e.getKey());
                if (i < 0) continue;
                bindObjectToProgram(stmt, i, e.getValue());
            }
            try { return stmt.executeInsert(); }
            catch (android.database.SQLException e) { return -1; }
        }

        /** Ready a prepared (non-replace) statement for a bind sequence. */
        public void prepareForInsert() {
            mPreparedStatement = getStatement(false);
            mPreparedStatement.clearBindings();
        }
        /** Ready a prepared replace statement for a bind sequence. */
        public void prepareForReplace() {
            mPreparedStatement = getStatement(true);
            mPreparedStatement.clearBindings();
        }
        /** Run the statement readied by prepareForInsert/Replace. */
        public long execute() {
            if (mPreparedStatement == null) {
                throw new IllegalStateException("you must prepare this inserter before calling execute");
            }
            try { return mPreparedStatement.executeInsert(); }
            catch (android.database.SQLException e) { return -1; }
            finally { mPreparedStatement = null; }
        }

        public void close() {
            if (mInsertStatement != null)  { mInsertStatement.close();  mInsertStatement = null; }
            if (mReplaceStatement != null) { mReplaceStatement.close(); mReplaceStatement = null; }
            mInsertSQL = null;
            mColumns = null;
        }
    }
    // ------------------------------------------------------------------
    // Exception marshalling across a Parcel.
    //
    // AOSP: DatabaseUtils.writeExceptionToParcel / readExceptionFromParcel and
    // its two typed variants. The wire form is a one-int exception CODE
    // followed by the message string; 0 means "no exception". The codes are
    // AOSP's and are part of the on-wire contract, so they are transcribed
    // rather than renumbered.
    //
    // ORDER IS LOAD-BEARING in writeExceptionToParcel: SQLiteAbortException,
    // SQLiteConstraintException, SQLiteDatabaseCorruptException,
    // SQLiteFullException and SQLiteDiskIOException are all SUBCLASSES of
    // SQLiteException, so the generic SQLiteException test must come last or
    // every one of them marshals as code 9 and reads back as the wrong type.
    //
    // ⚠ ONE KNOWN HOLE, and it is not in this file: the generic fallback
    // branch delegates to android.os.Parcel.writeException(Exception), which
    // in this tree is an empty method (runtime/java/android/os/Parcel.java).
    // So an exception that is none of the eleven known kinds marshals as
    // nothing and reads back as "no exception". That is Parcel's gap, not
    // DatabaseUtils' -- mirroring AOSP's structure here keeps the fix in the
    // one place it belongs. It is also close to unreachable: these methods
    // exist to cross a Binder boundary, and Mac.apk runs the app and the
    // "system" in one process.
    // ------------------------------------------------------------------

    public static final void writeExceptionToParcel(android.os.Parcel reply, Exception e) {
        int code = 0;
        boolean logException = true;
        if (e instanceof java.io.FileNotFoundException) {
            code = 1;
            logException = false;
        } else if (e instanceof IllegalArgumentException) {
            code = 2;
        } else if (e instanceof UnsupportedOperationException) {
            code = 3;
        } else if (e instanceof android.database.sqlite.SQLiteAbortException) {
            code = 4;
        } else if (e instanceof android.database.sqlite.SQLiteConstraintException) {
            code = 5;
        } else if (e instanceof android.database.sqlite.SQLiteDatabaseCorruptException) {
            code = 6;
        } else if (e instanceof android.database.sqlite.SQLiteFullException) {
            code = 7;
        } else if (e instanceof android.database.sqlite.SQLiteDiskIOException) {
            code = 8;
        } else if (e instanceof android.database.sqlite.SQLiteException) {
            code = 9;
        } else if (e instanceof android.content.OperationApplicationException) {
            code = 10;
        } else if (e instanceof android.os.OperationCanceledException) {
            code = 11;
            logException = false;
        } else {
            reply.writeException(e);
            android.util.Log.e(TAG, "Writing exception to parcel", e);
            return;
        }
        reply.writeInt(code);
        reply.writeString(e.getMessage());

        if (logException) {
            android.util.Log.e(TAG, "Writing exception to parcel", e);
        }
    }

    /**
     * Read an exception written by {@link #writeExceptionToParcel} and rethrow
     * it, or return normally when the parcel carries code 0.
     *
     * AOSP calls Parcel.readExceptionCode(), which is @hide and additionally
     * peels StrictMode / AppOps reply headers off the front. Neither header is
     * ever written in this process (there is no Binder transaction to attach
     * one to), so the plain readInt() below reads the same value.
     */
    public static final void readExceptionFromParcel(android.os.Parcel reply) {
        int code = reply.readInt();
        if (code == 0) return;
        String msg = reply.readString();
        readExceptionFromParcel(reply, msg, code);
    }

    public static void readExceptionWithFileNotFoundExceptionFromParcel(android.os.Parcel reply)
            throws java.io.FileNotFoundException {
        int code = reply.readInt();
        if (code == 0) return;
        String msg = reply.readString();
        if (code == 1) {
            throw new java.io.FileNotFoundException(msg);
        }
        readExceptionFromParcel(reply, msg, code);
    }

    public static void readExceptionWithOperationApplicationExceptionFromParcel(
            android.os.Parcel reply) throws android.content.OperationApplicationException {
        int code = reply.readInt();
        if (code == 0) return;
        String msg = reply.readString();
        if (code == 10) {
            throw new android.content.OperationApplicationException(msg);
        }
        readExceptionFromParcel(reply, msg, code);
    }

    private static void readExceptionFromParcel(android.os.Parcel reply, String msg, int code) {
        switch (code) {
            case 2:  throw new IllegalArgumentException(msg);
            case 3:  throw new UnsupportedOperationException(msg);
            case 4:  throw new android.database.sqlite.SQLiteAbortException(msg);
            case 5:  throw new android.database.sqlite.SQLiteConstraintException(msg);
            case 6:  throw new android.database.sqlite.SQLiteDatabaseCorruptException(msg);
            case 7:  throw new android.database.sqlite.SQLiteFullException(msg);
            case 8:  throw new android.database.sqlite.SQLiteDiskIOException(msg);
            case 9:  throw new android.database.sqlite.SQLiteException(msg);
            case 11: throw new android.os.OperationCanceledException(msg);
            default: reply.readException(code, msg);
        }
    }

    /**
     * AOSP: read one column of the current row as a String and bind it into an
     * InsertHelper at the given 1-based index.
     * Uses getColumnIndexOrThrow, so a missing column is an
     * IllegalArgumentException naming the column rather than a silent skip.
     */
    public static void cursorStringToInsertHelper(Cursor cursor, String field,
            InsertHelper inserter, int index) {
        inserter.bind(index, cursor.getString(cursor.getColumnIndexOrThrow(field)));
    }

    /**
     * Run a query that returns a single BLOB and hand the result back as a
     * read-only file descriptor. AOSP:
     *   SQLiteStatement prog = db.compileStatement(query);
     *   try { return blobFileDescriptorForQuery(prog, selectionArgs); }
     *   finally { prog.close(); }
     */
    public static android.os.ParcelFileDescriptor blobFileDescriptorForQuery(
            SQLiteDatabase db, String query, String[] selectionArgs) {
        SQLiteStatement prog = db.compileStatement(query);
        try {
            return blobFileDescriptorForQuery(prog, selectionArgs);
        } finally {
            prog.close();
        }
    }

    /** See {@link #blobFileDescriptorForQuery(SQLiteDatabase, String, String[])}. */
    public static android.os.ParcelFileDescriptor blobFileDescriptorForQuery(
            SQLiteStatement prog, String[] selectionArgs) {
        prog.bindAllArgsAsStrings(selectionArgs);
        return prog.simpleQueryForBlobFileDescriptor();
    }
}
