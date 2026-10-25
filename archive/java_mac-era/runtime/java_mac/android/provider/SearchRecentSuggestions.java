package android.provider;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * Client helper for an app's own SearchRecentSuggestionsProvider.
 *
 * Every method here talks to the AUTHORITY the CALLER supplied -- an authority
 * inside the app itself, declared in its own manifest -- so nothing about this
 * needs a platform service, and it is reproduced faithfully. If the app really
 * did declare its provider, this works; if it did not, the resolver finds
 * nothing, which is the same outcome as on a device.
 *
 * Source: frameworks/base/core/java/android/provider/SearchRecentSuggestions.java
 */
public class SearchRecentSuggestions {

    /** AOSP's projection for a 1-line suggestion provider. */
    public static final String[] QUERIES_PROJECTION_1LINE = new String[] {
        "_id", "date", "query", "display1",
    };
    /** AOSP's projection for a 2-line suggestion provider. */
    public static final String[] QUERIES_PROJECTION_2LINE = new String[] {
        "_id", "date", "query", "display1", "display2",
    };

    public static final int QUERIES_PROJECTION_DATE_INDEX = 1;
    public static final int QUERIES_PROJECTION_QUERY_INDEX = 2;
    public static final int QUERIES_PROJECTION_DISPLAY1_INDEX = 3;
    public static final int QUERIES_PROJECTION_DISPLAY2_INDEX = 4;

    /** AOSP's cap on stored suggestions. */
    private static final int MAX_HISTORY_COUNT = 250;

    private final Context mContext;
    private final Uri mSuggestionsUri;
    private final boolean mTwoLineDisplay;

    public SearchRecentSuggestions(Context context, String authority, int mode) {
        if (authority == null || authority.length() < 1) {
            throw new IllegalArgumentException("No authority provided");
        }
        if ((mode & 1 /* DATABASE_MODE_QUERIES */) == 0) {
            throw new IllegalArgumentException("Mode must include DATABASE_MODE_QUERIES");
        }
        mContext = context;
        mSuggestionsUri = Uri.parse("content://" + authority + "/suggestions");
        mTwoLineDisplay = (mode & 2 /* DATABASE_MODE_2LINES */) != 0;
    }

    public void saveRecentQuery(String queryString, String line2) {
        if (queryString == null || queryString.length() == 0) return;
        if (line2 != null && !mTwoLineDisplay) {
            throw new IllegalArgumentException(
                    "Suggestions database with 2-line format requires 2nd line.");
        }
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("display1", queryString);
        if (mTwoLineDisplay) {
            values.put("display2", line2);
        }
        values.put("query", queryString);
        values.put("date", now);

        ContentResolver cr = mContext.getContentResolver();
        try {
            cr.insert(mSuggestionsUri, values);
        } catch (RuntimeException e) {
            // AOSP logs and continues: a suggestion is never worth crashing over.
        }
        truncateHistory(cr, MAX_HISTORY_COUNT);
    }

    public void clearHistory() {
        ContentResolver cr = mContext.getContentResolver();
        truncateHistory(cr, 0);
    }

    /**
     * AOSP deletes every row older than the newest maxEntries. maxEntries 0
     * clears the table outright.
     */
    protected void truncateHistory(ContentResolver cr, int maxEntries) {
        if (maxEntries < 0) {
            throw new IllegalArgumentException("maxEntries must be >= 0");
        }
        try {
            String selection = null;
            if (maxEntries > 0) {
                selection = "_id IN (SELECT _id FROM suggestions ORDER BY date DESC"
                        + " LIMIT -1 OFFSET " + String.valueOf(maxEntries) + ")";
            }
            cr.delete(mSuggestionsUri, selection, null);
        } catch (RuntimeException e) {
            // AOSP logs and continues.
        }
    }
}
