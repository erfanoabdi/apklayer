// android.database.sqlite.SQLiteProgram -- AOSP base class for
// SQLiteStatement and SQLiteQuery; holds a prepared sqlite3_stmt and
// exposes the bind* methods that subclasses inherit.
//
// AOSP hierarchy (mirrored):
//   SQLiteClosable
//   SQLiteProgram extends SQLiteClosable     -- bind methods
//   SQLiteStatement extends SQLiteProgram    -- execute family
//   SQLiteQuery extends SQLiteProgram        -- step-and-read family
//
// Why this matters: Room's auto-generated code (and androidx
// FrameworkSQLiteStatement) sometimes upcasts a SQLiteStatement to
// SQLiteProgram and calls `((SQLiteProgram)stmt).bindString(...)`.
// Before v1.0.102, our SQLiteStatement did NOT extend SQLiteProgram
// and the bind methods only existed on the subclass -- Room's upcast
// call NoSuchMethodError'd and the whole INSERT chain rolled back
// silently. With this base class holding the binds, the upcast
// resolves correctly.
package android.database.sqlite;

import com.macapk.loader.NativeSqlite;

public class SQLiteProgram extends SQLiteClosable {
    /** sqlite3_stmt* handle. Subclass owns lifecycle. */
    protected long stmt;
    /** Owning database, for errmsg + last_insert_rowid + changes. */
    protected final SQLiteDatabase db;

    /** AOSP keeps the TRIMMED sql (SQLiteProgram.java:36,46) and exposes it
     *  through getSql(); SQLiteStatement.toString is built from it. */
    private final String mSql;
    /** AOSP takes this from the prepare (SQLiteProgram.java:66) via
     *  sqlite3_bind_parameter_count and rejects a bind outside 1..n. Our JNI
     *  bridge does not expose that call, so it is derived from the sql text --
     *  see countParameters, which answers "unknown" rather than a guess when
     *  it meets syntax it cannot classify, so a wrong count can never turn a
     *  legitimate bind into a thrown IllegalArgumentException. */
    private final int mNumParameters;

    protected SQLiteProgram() { this(null, 0L); }
    protected SQLiteProgram(SQLiteDatabase db, long stmt) { this(db, stmt, null); }
    protected SQLiteProgram(SQLiteDatabase db, long stmt, String sql) {
        this.db   = db;
        this.stmt = stmt;
        this.mSql = sql == null ? null : sql.trim();
        this.mNumParameters = countParameters(this.mSql);
    }

    /** AOSP's accessor (SQLiteProgram.java:90-92); package-private there too. */
    final String getSql() { return mSql; }

    /** AOSP SQLiteProgram.bind (SQLiteProgram.java:213-220): "index < 1 ||
     *  index > mNumParameters" is an IllegalArgumentException, checked BEFORE
     *  the value is stored. Silently dropping an out-of-range bind (which is
     *  all sqlite3_bind_* does on its own -- it just answers SQLITE_RANGE)
     *  turns an off-by-one in the caller into a query that runs with a NULL
     *  where a value belonged, which is far harder to find than a throw. */
    private void checkBindIndex(int index) {
        if (index < 1 || index > mNumParameters) {
            throw new IllegalArgumentException("Cannot bind argument at index "
                + index + " because the index is out of range.  "
                + "The statement has " + mNumParameters + " parameters.");
        }
    }

    /** 1-based parameter index per sqlite3_bind contract. */
    public void bindNull(int index) {
        checkBindIndex(index);
        if (stmt != 0) NativeSqlite.nativeBindNull(stmt, index);
    }
    public void bindLong(int index, long value) {
        checkBindIndex(index);
        if (stmt != 0) NativeSqlite.nativeBindLong(stmt, index, value);
    }
    public void bindDouble(int index, double value) {
        checkBindIndex(index);
        if (stmt != 0) NativeSqlite.nativeBindDouble(stmt, index, value);
    }
    public void bindString(int index, String value) {
        checkBindIndex(index);
        if (stmt != 0) NativeSqlite.nativeBindString(stmt, index, value);
    }
    public void bindBlob(int index, byte[] value) {
        checkBindIndex(index);
        if (stmt != 0) NativeSqlite.nativeBindBlob(stmt, index, value);
    }

    /** sqlite3_bind_parameter_count, computed from the sql text.
     *
     *  sqlite recognizes five parameter forms
     *  (https://sqlite.org/lang_expr.html#varparam): "?" takes the next index,
     *  "?NNN" takes index NNN, and ":AAA" / "@AAA" / "$AAA" take the next
     *  index but REUSE the index already assigned to the same name. The count
     *  is the largest index assigned. Literals and comments are skipped so a
     *  '?' inside a string is not counted.
     *
     *  Returns Integer.MAX_VALUE for an unknown sql (the legacy no-sql
     *  constructors) or when the scan meets something it will not model,
     *  which DISABLES the range check rather than risking a false throw on a
     *  statement we merely failed to parse. Undercounting here would break a
     *  working app; over-permitting only loses a diagnostic. */
    private static int countParameters(String sql) {
        if (sql == null) return Integer.MAX_VALUE;
        int next = 0;
        int max = 0;
        java.util.HashMap<String, Integer> named = null;
        final int n = sql.length();
        for (int i = 0; i < n; i++) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                char q = c;
                i++;
                while (i < n) {
                    if (sql.charAt(i) == q) {
                        if (i + 1 < n && sql.charAt(i + 1) == q) i++;   // doubled = escaped
                        else break;
                    }
                    i++;
                }
                if (i >= n) return Integer.MAX_VALUE;                   // unterminated
            } else if (c == '[') {
                while (i < n && sql.charAt(i) != ']') i++;
                if (i >= n) return Integer.MAX_VALUE;
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                while (i < n && sql.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) i++;
                i++;
            } else if (c == '?') {
                int j = i + 1;
                int digits = 0;
                while (j < n && sql.charAt(j) >= '0' && sql.charAt(j) <= '9') { j++; digits++; }
                if (digits > 0) {
                    int idx;
                    try { idx = Integer.parseInt(sql.substring(i + 1, j)); }
                    catch (NumberFormatException e) { return Integer.MAX_VALUE; }
                    if (idx > max) max = idx;
                    if (idx > next) next = idx;
                    i = j - 1;
                } else {
                    next++;
                    if (next > max) max = next;
                }
            } else if (c == ':' || c == '@' || c == '$') {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_')) j++;
                if (j == i + 1) continue;                               // a bare ':' is not a parameter
                String name = sql.substring(i, j);
                if (named == null) named = new java.util.HashMap<String, Integer>();
                if (named.get(name) == null) {
                    next++;
                    if (next > max) max = next;
                    named.put(name, Integer.valueOf(next));
                }
                i = j - 1;
            }
        }
        return max;
    }
    public void bindAllArgsAsStrings(String[] bindArgs) {
        if (stmt == 0 || bindArgs == null) return;
        for (int i = 0; i < bindArgs.length; i++) {
            String v = bindArgs[i];
            if (v == null) NativeSqlite.nativeBindNull(stmt, i + 1);
            else           NativeSqlite.nativeBindString(stmt, i + 1, v);
        }
    }
    public void clearBindings() {
        if (stmt != 0) NativeSqlite.nativeClearBindings(stmt);
    }

    /** @deprecated AOSP retained this from the pre-refactor API where
     *  it returned the compiled-statement id. Always -1 on modern AOSP
     *  (the field was removed); mirror that so any legacy caller
     *  resolves the method. */
    @Deprecated
    public final int getUniqueId() {
        return -1;
    }
}
