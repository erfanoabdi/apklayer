// android.content.SearchRecentSuggestionsProvider -- the stock ContentProvider
// an app subclasses to get "recent searches" suggestions backed by a small
// SQLite table. The app declares a subclass in its manifest, calls
// setupSuggestions(authority, mode) from its constructor, and SearchManager
// queries it through the SUGGEST_URI_PATH_QUERY path.
//
// AOSP ref:
// frameworks/base/core/java/android/content/SearchRecentSuggestionsProvider.java
// (android16-release). Ported in full: the schema, the DATABASE_VERSION<<8 |
// mode versioning trick, the LIKE-based suggestion query, and every
// IllegalArgumentException("Unknown Uri"). It is backed by the real
// SQLiteOpenHelper/SQLiteDatabase in this tree (which are libsqlite3-backed),
// so recent-search suggestions genuinely persist.
//
// ONE stated deviation: the SUGGEST_COLUMN_ICON_1 column. AOSP hardcodes
// "android.resource://system/" + com.android.internal.R.drawable
// .ic_menu_recent_history -- an internal framework drawable id that does not
// exist in this tree and has no stable public equivalent. Rather than emit a
// URI pointing at a resource that cannot resolve (which a SearchView would try
// to load and fail on), the column is emitted as NULL, which SearchView
// already handles as "this suggestion has no icon".
package android.content;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

public class SearchRecentSuggestionsProvider extends ContentProvider {
    private String mAuthority;
    private int mMode;
    private boolean mTwoLineDisplay;
    private SQLiteOpenHelper mOpenHelper;

    private static final String sDatabaseName = "suggestions.db";
    private static final String sSuggestions = "suggestions";
    private static final String ORDER_BY = "date DESC";
    private static final String NULL_COLUMN = "query";

    /** AOSP: `2 * 256`, i.e. the schema version in the high byte so the low
     *  byte can carry the mode -- a mode change forces an upgrade. */
    private static final int DATABASE_VERSION = 2 * 256;

    public static final int DATABASE_MODE_QUERIES = 1;
    public static final int DATABASE_MODE_2LINES = 2;

    private static final int URI_MATCH_SUGGEST = 1;

    private Uri mSuggestionsUri;
    private UriMatcher mUriMatcher;
    private String mSuggestSuggestionClause;
    private String[] mSuggestionProjection;

    public SearchRecentSuggestionsProvider() {
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private int mNewVersion;

        DatabaseHelper(Context context, int newVersion) {
            super(context, sDatabaseName, null, newVersion);
            mNewVersion = newVersion;
        }

        @Override public void onCreate(SQLiteDatabase db) {
            StringBuilder builder = new StringBuilder();
            builder.append("CREATE TABLE suggestions ("
                    + "_id INTEGER PRIMARY KEY"
                    + ",display1 TEXT UNIQUE ON CONFLICT REPLACE");
            if (0 != (mNewVersion & DATABASE_MODE_2LINES)) {
                builder.append(",display2 TEXT");
            }
            builder.append(",query TEXT"
                    + ",date LONG"
                    + ");");
            db.execSQL(builder.toString());
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // AOSP logs "which will destroy all old data" and does exactly
            // that -- the table is a cache, not user data.
            db.execSQL("DROP TABLE IF EXISTS suggestions");
            onCreate(db);
        }
    }

    protected void setupSuggestions(String authority, int mode) {
        if (authority == null || authority.isEmpty()
                || ((mode & DATABASE_MODE_QUERIES) == 0)) {
            throw new IllegalArgumentException();
        }
        mTwoLineDisplay = (0 != (mode & DATABASE_MODE_2LINES));
        mAuthority = new String(authority);
        mMode = mode;

        mSuggestionsUri = Uri.parse("content://" + mAuthority + "/suggestions");
        mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mUriMatcher.addURI(mAuthority, android.app.SearchManager.SUGGEST_URI_PATH_QUERY,
                           URI_MATCH_SUGGEST);

        // See the class comment for why SUGGEST_COLUMN_ICON_1 is NULL here.
        if (mTwoLineDisplay) {
            mSuggestSuggestionClause = "display1 LIKE ? OR display2 LIKE ?";
            mSuggestionProjection = new String[] {
                    "0 AS " + android.app.SearchManager.SUGGEST_COLUMN_FORMAT,
                    "NULL AS " + android.app.SearchManager.SUGGEST_COLUMN_ICON_1,
                    "display1 AS " + android.app.SearchManager.SUGGEST_COLUMN_TEXT_1,
                    "display2 AS " + android.app.SearchManager.SUGGEST_COLUMN_TEXT_2,
                    "query AS " + android.app.SearchManager.SUGGEST_COLUMN_QUERY,
                    "_id"
            };
        } else {
            mSuggestSuggestionClause = "display1 LIKE ?";
            mSuggestionProjection = new String[] {
                    "0 AS " + android.app.SearchManager.SUGGEST_COLUMN_FORMAT,
                    "NULL AS " + android.app.SearchManager.SUGGEST_COLUMN_ICON_1,
                    "display1 AS " + android.app.SearchManager.SUGGEST_COLUMN_TEXT_1,
                    "query AS " + android.app.SearchManager.SUGGEST_COLUMN_QUERY,
                    "_id"
            };
        }
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final int length = uri.getPathSegments().size();
        if (length != 1) {
            throw new IllegalArgumentException("Unknown Uri");
        }
        final String base = uri.getPathSegments().get(0);
        int count = 0;
        if (base.equals(sSuggestions)) {
            count = db.delete(sSuggestions, selection, selectionArgs);
        } else {
            throw new IllegalArgumentException("Unknown Uri");
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override public String getType(Uri uri) {
        if (mUriMatcher.match(uri) == URI_MATCH_SUGGEST) {
            return android.app.SearchManager.SUGGEST_MIME_TYPE;
        }
        int length = uri.getPathSegments().size();
        if (length >= 1) {
            String base = uri.getPathSegments().get(0);
            if (base.equals(sSuggestions)) {
                if (length == 1) {
                    return "vnd.android.cursor.dir/suggestion";
                } else if (length == 2) {
                    return "vnd.android.cursor.item/suggestion";
                }
            }
        }
        throw new IllegalArgumentException("Unknown Uri");
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int length = uri.getPathSegments().size();
        if (length < 1) {
            throw new IllegalArgumentException("Unknown Uri");
        }
        long rowID = -1;
        String base = uri.getPathSegments().get(0);
        Uri newUri = null;
        if (base.equals(sSuggestions)) {
            if (length == 1) {
                rowID = db.insert(sSuggestions, NULL_COLUMN, values);
                if (rowID > 0) {
                    newUri = Uri.withAppendedPath(mSuggestionsUri, String.valueOf(rowID));
                }
            }
        }
        if (rowID < 0) {
            throw new IllegalArgumentException("Unknown Uri");
        }
        getContext().getContentResolver().notifyChange(newUri, null);
        return newUri;
    }

    @Override public boolean onCreate() {
        if (mAuthority == null || mMode == 0) {
            throw new IllegalArgumentException("Provider not configured");
        }
        int workingDbVersion = DATABASE_VERSION + mMode;
        mOpenHelper = new DatabaseHelper(getContext(), workingDbVersion);
        return true;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();

        if (mUriMatcher.match(uri) == URI_MATCH_SUGGEST) {
            String suggestSelection;
            String[] myArgs;
            if (selectionArgs == null || selectionArgs.length == 0
                    || selectionArgs[0] == null || selectionArgs[0].isEmpty()) {
                suggestSelection = null;
                myArgs = null;
            } else {
                String like = "%" + selectionArgs[0] + "%";
                myArgs = mTwoLineDisplay ? new String[] { like, like } : new String[] { like };
                suggestSelection = mSuggestSuggestionClause;
            }
            Cursor c = db.query(sSuggestions, mSuggestionProjection,
                    suggestSelection, myArgs, null, null, ORDER_BY, null);
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        }

        int length = uri.getPathSegments().size();
        if (length != 1 && length != 2) {
            throw new IllegalArgumentException("Unknown Uri");
        }
        String base = uri.getPathSegments().get(0);
        if (!base.equals(sSuggestions)) {
            throw new IllegalArgumentException("Unknown Uri");
        }

        String[] useProjection = null;
        if (projection != null && projection.length > 0) {
            useProjection = new String[projection.length + 1];
            System.arraycopy(projection, 0, useProjection, 0, projection.length);
            useProjection[projection.length] = "_id AS _id";
        }

        StringBuilder whereClause = new StringBuilder(256);
        if (length == 2) {
            whereClause.append("(_id = ").append(uri.getPathSegments().get(1)).append(")");
        }
        if (selection != null && selection.length() > 0) {
            if (whereClause.length() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append('(');
            whereClause.append(selection);
            whereClause.append(')');
        }

        Cursor c = db.query(base, useProjection, whereClause.toString(),
                selectionArgs, null, null, sortOrder, null);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    /** AOSP: `throw new UnsupportedOperationException("Not implemented")`. */
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
