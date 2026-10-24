// android.database.sqlite.SQLiteQueryBuilder -- the canonical helper
// ContentProvider-backed apps use to assemble a SELECT (and, on newer
// API levels, INSERT/UPDATE/DELETE) against a SQLiteDatabase. It
// accumulates table names, an optional WHERE prefix, a projection map
// (alias -> column expression), a DISTINCT flag, and a CursorFactory,
// then composes the final SQL in buildQuery / query. The static
// buildQueryString does the raw assembly; query() ANDs the builder's
// accumulated WHERE with the caller's selection (each parenthesized).
//
// Mac.apk implements the assembly faithfully and runs it through our
// real SQLite-backed SQLiteDatabase.rawQueryWithFactory. We DO NOT
// implement the "strict" anti-injection re-parse (setStrict*/validate)
// because that path needs SQLiteConnection.validateSql, which our
// folded SQLiteDatabase does not expose; the strict flags are stored
// and honored as no-op validation (queries still run), which is
// observably permissive but never breaks a non-malicious caller.
//
// Source: frameworks/base/core/java/android/database/sqlite/SQLiteQueryBuilder.java
package android.database.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class SQLiteQueryBuilder {
    private Map<String, String> mProjectionMap = null;
    private Collection<Pattern> mProjectionGreylist = null;
    private String mTables = "";
    private StringBuilder mWhereClause = null; // lazily created
    private boolean mDistinct;
    private SQLiteDatabase.CursorFactory mFactory;
    private boolean mStrict;
    private boolean mStrictColumns;
    private boolean mStrictGrammar;

    public SQLiteQueryBuilder() {}

    public void setDistinct(boolean distinct) { mDistinct = distinct; }
    public boolean isDistinct() { return mDistinct; }

    public String getTables() { return mTables; }
    public void setTables(String inTables) { mTables = inTables; }

    /** Append a chunk to the WHERE clause. The chunks are ANDed
     *  together as written; callers are responsible for spacing /
     *  parenthesizing if they append multiple times. */
    public void appendWhere(CharSequence inWhere) {
        if (mWhereClause == null) {
            mWhereClause = new StringBuilder(inWhere.length() + 16);
        }
        mWhereClause.append(inWhere);
    }

    /** Append an SQL-escaped string literal to the WHERE clause. */
    public void appendWhereEscapeString(String inWhere) {
        if (mWhereClause == null) {
            mWhereClause = new StringBuilder(inWhere.length() + 16);
        }
        DatabaseUtils.appendEscapedSQLString(mWhereClause, inWhere);
    }

    /** Append a standalone WHERE expression, ANDing it with any
     *  existing clause and wrapping it in parentheses (AOSP API 30+). */
    public void appendWhereStandalone(CharSequence inWhere) {
        if (mWhereClause == null) {
            mWhereClause = new StringBuilder(inWhere.length() + 16);
        }
        if (mWhereClause.length() > 0) {
            mWhereClause.append(" AND ");
        }
        mWhereClause.append('(').append(inWhere).append(')');
    }

    public void setProjectionMap(Map<String, String> columnMap) { mProjectionMap = columnMap; }
    public Map<String, String> getProjectionMap() { return mProjectionMap; }

    public void setProjectionGreylist(Collection<Pattern> projectionGreylist) {
        mProjectionGreylist = projectionGreylist;
    }
    public Collection<Pattern> getProjectionGreylist() { return mProjectionGreylist; }

    public void setCursorFactory(SQLiteDatabase.CursorFactory factory) { mFactory = factory; }
    public SQLiteDatabase.CursorFactory getCursorFactory() { return mFactory; }

    public void setStrict(boolean strict) { mStrict = strict; }
    public boolean isStrict() { return mStrict; }

    public void setStrictColumns(boolean strictColumns) { mStrictColumns = strictColumns; }
    public boolean isStrictColumns() { return mStrictColumns; }

    public void setStrictGrammar(boolean strictGrammar) { mStrictGrammar = strictGrammar; }
    public boolean isStrictGrammar() { return mStrictGrammar; }

    // ---- static assembly -----------------------------------------

    /** Builds a raw SELECT string from its parts. Empty / null
     *  optional clauses are skipped. Mirrors AOSP buildQueryString. */
    public static String buildQueryString(boolean distinct, String tables, String[] columns,
                                          String where, String groupBy, String having,
                                          String orderBy, String limit) {
        if (isEmpty(groupBy) && !isEmpty(having)) {
            throw new IllegalArgumentException(
                "HAVING clauses are only permitted when using a groupBy clause");
        }

        StringBuilder query = new StringBuilder(120);
        query.append("SELECT ");
        if (distinct) {
            query.append("DISTINCT ");
        }
        if (columns != null && columns.length != 0) {
            appendColumns(query, columns);
        } else {
            query.append("* ");
        }
        query.append("FROM ");
        query.append(tables);
        appendClause(query, " WHERE ", where);
        appendClause(query, " GROUP BY ", groupBy);
        appendClause(query, " HAVING ", having);
        appendClause(query, " ORDER BY ", orderBy);
        appendClause(query, " LIMIT ", limit);
        return query.toString();
    }

    private static void appendClause(StringBuilder s, String name, String clause) {
        if (!isEmpty(clause)) {
            s.append(name);
            s.append(clause);
        }
    }

    /** Append projection columns to a SELECT (comma-separated, trailing
     *  space). Mirrors AOSP SQLiteQueryBuilder.appendColumns. */
    public static void appendColumns(StringBuilder s, String[] columns) {
        int n = columns.length;
        for (int i = 0; i < n; i++) {
            String column = columns[i];
            if (column != null) {
                if (i > 0) {
                    s.append(", ");
                }
                s.append(column);
            }
        }
        s.append(' ');
    }

    /** Assemble (but do not run) the SELECT. Applies the projection
     *  map to the columns and ANDs the builder WHERE with the
     *  selection. */
    public String buildQuery(String[] projectionIn, String selection, String groupBy,
                             String having, String sortOrder, String limit) {
        String[] projection = computeProjection(projectionIn);
        String where = computeWhere(selection);
        return buildQueryString(mDistinct, mTables, projection, where, groupBy, having,
                                sortOrder, limit);
    }

    /**
     * @deprecated AOSP: "This method's signature is misleading since no
     * SQL parameter substitution is carried out. The selection arguments
     * parameter does not get used at all." The body delegates to the
     * six-argument overload and DROPS selectionArgs -- transcribed as-is,
     * because a caller that relies on this overload is already passing its
     * arguments to query() separately, and "helpfully" substituting them here
     * would produce different SQL from a device.
     */
    @Deprecated
    public String buildQuery(String[] projectionIn, String selection, String[] selectionArgs,
                             String groupBy, String having, String sortOrder, String limit) {
        return buildQuery(projectionIn, selection, groupBy, having, sortOrder, limit);
    }

    /** Compose the effective WHERE: the builder's accumulated clause
     *  ANDed with the caller's selection, each parenthesized. Either
     *  may be absent. Returns null when both are absent. */
    private String computeWhere(String selection) {
        final boolean hasInternal = mWhereClause != null && mWhereClause.length() > 0;
        final boolean hasExternal = !isEmpty(selection);
        if (hasInternal || hasExternal) {
            StringBuilder where = new StringBuilder();
            if (hasInternal) {
                where.append('(').append(mWhereClause).append(')');
            }
            if (hasInternal && hasExternal) {
                where.append(" AND ");
            }
            if (hasExternal) {
                where.append('(').append(selection).append(')');
            }
            return where.toString();
        }
        return null;
    }

    /** AOSP sAggregationPattern (SQLiteQueryBuilder.java:55-56). A projection
     *  entry like "COUNT(a)" is not in the map under that spelling, so AOSP
     *  peels the aggregate off, maps the INNER column, and puts the operator
     *  back: with {a -> t.a} the caller's "COUNT(a)" becomes "COUNT(t.a)".
     *  Passing it through verbatim leaves the un-qualified "a" in a query
     *  whose FROM is a join, where it is either ambiguous or plain wrong. */
    private static final java.util.regex.Pattern sAggregationPattern =
            java.util.regex.Pattern.compile("(?i)(AVG|COUNT|MAX|MIN|SUM|TOTAL|GROUP_CONCAT)\\((.+)\\)");

    /** AOSP computeSingleProjection (SQLiteQueryBuilder.java:1117-1160),
     *  returning null for "this column is not allowed". */
    private String computeSingleProjection(String userColumn) {
        if (mProjectionMap == null) return userColumn;   // no map: anything goes

        String operator = null;
        String column = mProjectionMap.get(userColumn);
        if (column == null) {
            java.util.regex.Matcher matcher = sAggregationPattern.matcher(userColumn);
            if (matcher.matches()) {
                operator = matcher.group(1);
                userColumn = matcher.group(2);
                column = mProjectionMap.get(userColumn);
            }
        }
        if (column != null) return maybeWithOperator(operator, column);
        // An expression that already carries its own alias is allowed
        // through when strict mode is off, because the alias is what the
        // caller will read the value back by.
        if (!mStrict && !mStrictColumns && !mStrictGrammar
                && (userColumn.contains(" AS ") || userColumn.contains(" as "))) {
            return maybeWithOperator(operator, userColumn);
        }
        if (mProjectionGreylist != null) {
            for (Pattern p : mProjectionGreylist) {
                if (p.matcher(userColumn).matches()) return maybeWithOperator(operator, userColumn);
            }
        }
        return null;
    }

    private static String maybeWithOperator(String operator, String column) {
        return (operator == null) ? column : operator + "(" + column + ")";
    }

    /** Map requested columns through the projection map when one is
     *  set; otherwise pass the columns through unchanged. A null
     *  request with a projection map expands to all mapped columns. */
    private String[] computeProjection(String[] projectionIn) {
        if (projectionIn != null && projectionIn.length > 0) {
            String[] projection = new String[projectionIn.length];
            for (int i = 0; i < projectionIn.length; i++) {
                projection[i] = computeSingleProjection(projectionIn[i]);
                if (projection[i] == null) {
                    // AOSP computeSingleProjectionOrThrow (:1108-1115). A
                    // projection map is a WHITELIST -- that is the entire
                    // reason ContentProviders set one, since the column names
                    // arrive from another app. Passing an unmapped column
                    // through verbatim (which this did) defeats the whitelist
                    // and hands the caller's string straight to SQL.
                    throw new IllegalArgumentException("Invalid column " + projectionIn[i]);
                }
            }
            return projection;
        } else if (mProjectionMap != null) {
            // Null request + a projection map: expand to all entries
            // except an "_count" pseudo-column (AOSP excludes it from
            // the implicit "*" expansion).
            java.util.ArrayList<String> projection =
                new java.util.ArrayList<>(mProjectionMap.size());
            for (Map.Entry<String, String> entry : mProjectionMap.entrySet()) {
                if ("_count".equals(entry.getKey())) continue;
                projection.add(entry.getValue());
            }
            return projection.toArray(new String[0]);
        }
        return null;
    }

    // ---- query (runs against the db) -----------------------------

    public Cursor query(SQLiteDatabase db, String[] projectionIn, String selection,
                        String[] selectionArgs, String groupBy, String having, String sortOrder) {
        return query(db, projectionIn, selection, selectionArgs, groupBy, having, sortOrder, null);
    }

    public Cursor query(SQLiteDatabase db, String[] projectionIn, String selection,
                        String[] selectionArgs, String groupBy, String having, String sortOrder,
                        String limit) {
        if (mTables == null) {
            return null;
        }
        String sql = buildQuery(projectionIn, selection, groupBy, having, sortOrder, limit);
        SQLiteDatabase.CursorFactory factory = mFactory;
        return db.rawQueryWithFactory(factory, sql, selectionArgs, getEditTable(mTables));
    }

    public Cursor query(SQLiteDatabase db, String[] projectionIn, String selection,
                        String[] selectionArgs, String groupBy, String having, String sortOrder,
                        String limit, android.os.CancellationSignal cancellationSignal) {
        return query(db, projectionIn, selection, selectionArgs, groupBy, having, sortOrder, limit);
    }

    // ---- modification helpers (AOSP API 30+) ---------------------

    public long insert(SQLiteDatabase db, ContentValues values) {
        return db.insert(getEditTable(mTables), null, values);
    }

    public int update(SQLiteDatabase db, ContentValues values, String selection,
                      String[] selectionArgs) {
        String sqlWhere = computeWhere(selection);
        return db.update(getEditTable(mTables), values, sqlWhere, selectionArgs);
    }

    public int delete(SQLiteDatabase db, String selection, String[] selectionArgs) {
        String sqlWhere = computeWhere(selection);
        return db.delete(getEditTable(mTables), sqlWhere, selectionArgs);
    }

    // ---- union helpers -------------------------------------------

    /** Build a query suitable for use as a UNION sub-query against a
     *  single table, projecting the union column set and inserting a
     *  literal type-discriminator column. Mirrors AOSP. */
    public String buildUnionSubQuery(String typeDiscriminatorColumn, String[] unionColumns,
                                     Set<String> columnsPresentInTable, int computedColumnsOffset,
                                     String typeDiscriminatorValue, String selection,
                                     String groupBy, String having) {
        int unionColumnsCount = unionColumns.length;
        String[] projectionIn = new String[unionColumnsCount];
        for (int i = 0; i < unionColumnsCount; i++) {
            String unionColumn = unionColumns[i];
            if (unionColumn.equals(typeDiscriminatorColumn)) {
                projectionIn[i] = "'" + typeDiscriminatorValue + "' AS " + typeDiscriminatorColumn;
            } else if (i <= computedColumnsOffset
                       || columnsPresentInTable.contains(unionColumn)) {
                projectionIn[i] = unionColumn;
            } else {
                projectionIn[i] = "NULL AS " + unionColumn;
            }
        }
        return buildQuery(projectionIn, selection, groupBy, having, null, null);
    }

    /**
     * @deprecated AOSP: same misleading signature as the deprecated
     * buildQuery overload -- selectionArgs is accepted and ignored.
     */
    @Deprecated
    public String buildUnionSubQuery(String typeDiscriminatorColumn, String[] unionColumns,
                                     Set<String> columnsPresentInTable, int computedColumnsOffset,
                                     String typeDiscriminatorValue, String selection,
                                     String[] selectionArgs, String groupBy, String having) {
        return buildUnionSubQuery(typeDiscriminatorColumn, unionColumns, columnsPresentInTable,
                computedColumnsOffset, typeDiscriminatorValue, selection, groupBy, having);
    }

    /** Combine multiple sub-queries (typically from buildUnionSubQuery)
     *  with UNION ALL, applying a shared ORDER BY / LIMIT to the whole.
     *  Mirrors AOSP. */
    public String buildUnionQuery(String[] subQueries, String sortOrder, String limit) {
        StringBuilder query = new StringBuilder(128);
        int subQueryCount = subQueries.length;
        String unionOperator = mDistinct ? " UNION " : " UNION ALL ";
        for (int i = 0; i < subQueryCount; i++) {
            if (i > 0) {
                query.append(unionOperator);
            }
            query.append(subQueries[i]);
        }
        appendClause(query, " ORDER BY ", sortOrder);
        appendClause(query, " LIMIT ", limit);
        return query.toString();
    }

    // ---- internal helpers ----------------------------------------

    /** AOSP findEditTable: the first whitespace/comma-delimited token
     *  of the tables string is the table that insert/update/delete
     *  operate on. Returns the whole string when it is a single
     *  table. */
    private static String getEditTable(String tables) {
        if (!isEmpty(tables)) {
            int spacePos = -1;
            for (int i = 0; i < tables.length(); i++) {
                char c = tables.charAt(i);
                if (c == ' ' || c == ',') { spacePos = i; break; }
            }
            if (spacePos > 0) {
                return tables.substring(0, spacePos);
            }
            return tables;
        }
        return null;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.length() == 0;
    }
}
